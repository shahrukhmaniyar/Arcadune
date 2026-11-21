package com.swordfish.lemuroid.lib.achievements

import android.content.SharedPreferences
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.library.SystemID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.spec.SecretKeySpec

class RetroAchievementsHardcoreIntegrationTest {
    private data class RecordedRequest(
        val method: String,
        val path: String,
        val userAgent: String,
        val formParams: Map<String, String>,
        val rawBody: String,
    )

    private lateinit var server: LocalLoopbackHttpServer
    private lateinit var endpointUrl: String
    private val recordedRequests = CopyOnWriteArrayList<RecordedRequest>()
    private val responseStatusOverride = AtomicInteger(200)

    @Volatile
    private var responseBodyHandler: (Map<String, String>) -> String = { params ->
        when (params["r"]) {
            "login2" -> {
                val u = params["u"]
                """{"Success":true,"User":"$u","Token":"mock_tok_$u","Score":1250,"SoftcoreScore":300,"Messages":0}"""
            }
            "awardachievement" -> {
                val a = params["a"] ?: "0"
                """{"Success":true,"AchievementID":$a,"Score":1275,"SoftcoreScore":300,"AchievementsRemaining":4}"""
            }
            "submitlbentry" -> {
                val s = params["s"] ?: "0"
                """{"Success":true,"Response":{"Score":$s,"BestScore":$s,"RankInfo":{"Rank":1,"NumEntries":42}}}"""
            }
            else -> """{"Success":true}"""
        }
    }

    @Before
    fun setUp() {
        recordedRequests.clear()
        responseStatusOverride.set(200)
        server =
            LocalLoopbackHttpServer { method, path, headers, rawBody ->
                val params = parseFormBody(rawBody)
                val ua = headers["user-agent"].orEmpty()
                recordedRequests.add(
                    RecordedRequest(
                        method = method,
                        path = path,
                        userAgent = ua,
                        formParams = params,
                        rawBody = rawBody,
                    ),
                )
                val status = responseStatusOverride.get()
                val body =
                    if (status in 200..299) {
                        responseBodyHandler(params)
                    } else {
                        """{"Success":false,"Error":"Simulated server outage"}"""
                    }
                status to body
            }
        server.start()
        endpointUrl = "http://127.0.0.1:${server.port}/dorequest.php"

        RetroAchievementsAuthClient.endpointUrl = endpointUrl
        RetroAchievementsAuthClient.appVersion = "1.0.9"
        RetroAchievementsTokenStore.cipherOverrideForTesting =
            AesGcmSecretKeyCipherEngine(
                SecretKeySpec(ByteArray(32) { index -> (index + 7).toByte() }, "AES"),
            )
        RetroAchievementsOfflineQueue.clockEpochMs = { 1_700_000_000_000L }
        RetroAchievementsPreferences.hardcoreApprovalOverrideForTesting = null
    }

    @After
    fun tearDown() {
        server.close()
        RetroAchievementsAuthClient.endpointUrl = RetroAchievementsAuthClient.DEFAULT_DOREQUEST_URL
        RetroAchievementsTokenStore.cipherOverrideForTesting = null
        RetroAchievementsOfflineQueue.clockEpochMs = { System.currentTimeMillis() }
        RetroAchievementsPreferences.hardcoreApprovalOverrideForTesting = null
    }

    @Test
    fun outboundUserAgent_formatsProductVersionPlatformCoreAndRcheevos_andRedactsSecrets() =
        runBlocking {
            val coreUa =
                RetroAchievementsUserAgent.build(
                    appVersion = "1.0.9",
                    osRelease = "14",
                    coreName = "Snes9x",
                    coreVersion = "1.62.3\r\n",
                    rcheevosClause = "rcheevos/12.5",
                )
            assertEquals("Arcadune/1.0.9 (Android 14) Snes9x/1.62.3 rcheevos/12.5", coreUa)
            assertFalse(coreUa.contains("\r"))
            assertFalse(coreUa.contains("\n"))

            val loginResult = RetroAchievementsAuthClient.loginWithPassword("AlicePlayer", "s3cr3tPass!")
            assertTrue(loginResult.isSuccess)
            val refreshResult =
                RetroAchievementsAuthClient.verifyAndRefreshAccount(
                    username = "AlicePlayer",
                    token = "mock_tok_AlicePlayer",
                )
            assertTrue(refreshResult.isSuccess)

            assertEquals(2, recordedRequests.size)
            for (req in recordedRequests) {
                assertTrue(
                    "Expected Arcadune/1.0.9 UA but got: ${req.userAgent}",
                    req.userAgent.startsWith("Arcadune/1.0.9 (Android") &&
                        req.userAgent.endsWith(") rcheevos/12.5"),
                )
            }

            val rawSensitive =
                "POST r=awardachievement&u=AlicePlayer&t=super_secret_token_123&p=my_pass_99 " +
                    """{"Success":true,"Token":"json_secret_tok_456"}"""
            val redacted = RetroAchievementsUserAgent.redactSecrets(rawSensitive)
            assertFalse(redacted.contains("super_secret_token_123"))
            assertFalse(redacted.contains("my_pass_99"))
            assertFalse(redacted.contains("json_secret_tok_456"))
            assertTrue(redacted.contains("t=[REDACTED]"))
            assertTrue(redacted.contains("p=[REDACTED]"))
            assertTrue(redacted.contains("\"Token\":\"[REDACTED]\""))
        }

    @Test
    fun tokenStore_encryptsTokensWithAesGcm_migratesLegacyPlaintext_andWipesOnSignOut() {
        val prefs = InMemorySharedPreferences()

        // 1. Fresh write stores AES-GCM ciphertext and never writes legacy plaintext key.
        val editor = prefs.edit()
        RetroAchievementsTokenStore.writeToken(editor, "live_ra_connect_token_abc")
        editor.apply()

        val storedEncrypted = prefs.getString(RetroAchievementsTokenStore.KEY_ENCRYPTED_TOKEN, null)
        assertNotNull(storedEncrypted)
        assertNotEquals("live_ra_connect_token_abc", storedEncrypted)
        assertFalse(storedEncrypted!!.contains("live_ra_connect_token_abc"))
        assertNull(prefs.getString(RetroAchievementsTokenStore.KEY_LEGACY_PLAINTEXT_TOKEN, null))
        assertEquals("live_ra_connect_token_abc", RetroAchievementsTokenStore.readToken(prefs))

        // 2. Legacy plaintext token is transparently migrated to encrypted form and wiped.
        val legacyPrefs = InMemorySharedPreferences()
        legacyPrefs.edit()
            .putString(RetroAchievementsTokenStore.KEY_LEGACY_PLAINTEXT_TOKEN, "legacy_plain_tok_777")
            .apply()

        val migratedRead = RetroAchievementsTokenStore.readToken(legacyPrefs)
        assertEquals("legacy_plain_tok_777", migratedRead)
        assertNull(legacyPrefs.getString(RetroAchievementsTokenStore.KEY_LEGACY_PLAINTEXT_TOKEN, null))
        val migratedCipher = legacyPrefs.getString(RetroAchievementsTokenStore.KEY_ENCRYPTED_TOKEN, null)
        assertNotNull(migratedCipher)
        assertNotEquals("legacy_plain_tok_777", migratedCipher)

        // 3. Sign-out clearToken removes all token artifacts.
        val clearEditor = legacyPrefs.edit()
        RetroAchievementsTokenStore.clearToken(clearEditor)
        clearEditor.apply()
        assertEquals("", RetroAchievementsTokenStore.readToken(legacyPrefs))
        assertNull(legacyPrefs.getString(RetroAchievementsTokenStore.KEY_ENCRYPTED_TOKEN, null))
        assertNull(legacyPrefs.getString(RetroAchievementsTokenStore.KEY_LEGACY_PLAINTEXT_TOKEN, null))
    }

    @Test
    fun offlineQueue_preservesModeAndAccount_survivesDisconnectsAndAccountSwitches_andVerifiesSignatures() =
        runBlocking {
            val prefs = InMemorySharedPreferences()
            var fakeNowMs = 1_700_000_000_000L
            RetroAchievementsOfflineQueue.clockEpochMs = { fakeNowMs }

            val hashA = "0123456789abcdef0123456789abcdef"
            val hashB = "fedcba9876543210fedcba9876543210"
            // Enqueue Alice Hardcore unlock (with active core UA) + Alice Hardcore leaderboard + Bob Casual unlock.
            RetroAchievementsOfflineQueue.enqueueFromPostData(
                sp = prefs,
                postData = "r=awardachievement&u=Alice&t=old_tok_a&a=4401&h=1&m=$hashA&v=deadbeef",
                fallbackGameHash = hashA,
                userAgent = "Arcadune/1.0.9 (Android 14) Snes9x/1.62.3 rcheevos/12.5",
            )
            // Duplicate enqueue is idempotent.
            RetroAchievementsOfflineQueue.enqueueFromPostData(
                sp = prefs,
                postData = "r=awardachievement&u=Alice&t=old_tok_a&a=4401&h=1&m=$hashA&v=deadbeef",
                fallbackGameHash = hashA,
            )
            RetroAchievementsOfflineQueue.enqueueFromPostData(
                sp = prefs,
                postData = "r=submitlbentry&u=Alice&t=old_tok_a&i=880&s=95400&m=$hashA&v=beadfeed",
                fallbackGameHash = hashA,
            )
            RetroAchievementsOfflineQueue.enqueueFromPostData(
                sp = prefs,
                postData = "r=awardachievement&u=Bob&t=old_tok_b&a=5502&h=0&m=$hashB&v=cafebabe",
                fallbackGameHash = hashB,
            )

            // Stored JSON never persists user tokens on disk!
            val rawQueueJson = prefs.getString(RetroAchievementsOfflineQueue.KEY_OFFLINE_QUEUE, "").orEmpty()
            assertFalse(rawQueueJson.contains("old_tok_a"))
            assertFalse(rawQueueJson.contains("old_tok_b"))
            assertEquals(3, RetroAchievementsOfflineQueue.listPending(prefs).size)

            val aliceAccount =
                RetroAchievementsAccount(
                    username = "Alice",
                    displayName = "Alice",
                    token = "fresh_alice_token",
                    avatarUrl = "",
                    score = 500,
                    softcoreScore = 100,
                )
            val bobAccount =
                RetroAchievementsAccount(
                    username = "Bob",
                    displayName = "Bob",
                    token = "fresh_bob_token",
                    avatarUrl = "",
                    score = 200,
                    softcoreScore = 400,
                )

            // 1. Simulate HTTP 503 outage while Alice tries to flush -> items stay queued with attempts incremented.
            fakeNowMs += 45_000L
            responseStatusOverride.set(503)
            val outageSummary =
                RetroAchievementsOfflineQueue.flushPending(
                    sp = prefs,
                    account = aliceAccount,
                    endpointUrl = endpointUrl,
                    appVersion = "1.0.9",
                    osRelease = "14",
                )
            assertEquals(2, outageSummary.attempted)
            assertEquals(0, outageSummary.delivered)
            assertEquals(2, outageSummary.remainingForAccount)
            assertEquals(1, outageSummary.preservedOtherAccounts)
            val afterOutage = RetroAchievementsOfflineQueue.listPending(prefs).filter { it.username == "Alice" }
            assertTrue(afterOutage.all { it.attempts == 1 })

            // 2. Account switch to Bob -> flushes ONLY Bob's Casual unlock, preserving Alice's 2 Hardcore items!
            recordedRequests.clear()
            responseStatusOverride.set(200)
            fakeNowMs += 15_000L // total 60s since enqueue
            val bobSummary =
                RetroAchievementsOfflineQueue.flushPending(
                    sp = prefs,
                    account = bobAccount,
                    endpointUrl = endpointUrl,
                    appVersion = "1.0.9",
                    osRelease = "14",
                )
            assertEquals(1, bobSummary.attempted)
            assertEquals(1, bobSummary.delivered)
            assertEquals(0, bobSummary.remainingForAccount)
            assertEquals(2, bobSummary.preservedOtherAccounts)
            assertEquals(1, recordedRequests.size)

            val bobReq = recordedRequests.single()
            assertEquals("Arcadune/1.0.9 (Android 14) rcheevos/12.5", bobReq.userAgent)
            assertEquals("awardachievement", bobReq.formParams["r"])
            assertEquals("Bob", bobReq.formParams["u"])
            assertEquals("fresh_bob_token", bobReq.formParams["t"])
            assertEquals("5502", bobReq.formParams["a"])
            assertEquals("0", bobReq.formParams["h"])
            assertEquals("60", bobReq.formParams["o"])
            assertEquals(md5("5502Bob0550260"), bobReq.formParams["v"])

            // 3. Sign back into Alice, with server returning "User already has..." on the unlock -> idempotent delivery!
            recordedRequests.clear()
            fakeNowMs += 30_000L // total 90s since enqueue
            responseBodyHandler = { params ->
                when (params["r"]) {
                    "awardachievement" ->
                        """{"Success":false,"Error":"User already has this achievement unlocked in hardcore mode."}"""
                    "submitlbentry" -> """{"Success":true,"Response":{"Score":95400,"BestScore":95400}}"""
                    else -> """{"Success":true}"""
                }
            }

            val aliceSummary =
                RetroAchievementsOfflineQueue.flushPending(
                    sp = prefs,
                    account = aliceAccount,
                    endpointUrl = endpointUrl,
                    appVersion = "1.0.9",
                    osRelease = "14",
                )
            assertEquals(2, aliceSummary.attempted)
            assertEquals(2, aliceSummary.delivered)
            assertEquals(0, aliceSummary.remainingForAccount)
            assertEquals(0, aliceSummary.preservedOtherAccounts)
            assertTrue(RetroAchievementsOfflineQueue.listPending(prefs).isEmpty())

            assertEquals(2, recordedRequests.size)
            val achReq = recordedRequests[0]
            assertEquals("Arcadune/1.0.9 (Android 14) Snes9x/1.62.3 rcheevos/12.5", achReq.userAgent)
            assertEquals("awardachievement", achReq.formParams["r"])
            assertEquals("Alice", achReq.formParams["u"])
            assertEquals("fresh_alice_token", achReq.formParams["t"])
            assertEquals("4401", achReq.formParams["a"])
            assertEquals("1", achReq.formParams["h"])
            assertEquals("90", achReq.formParams["o"])
            assertEquals(md5("4401Alice1440190"), achReq.formParams["v"])

            val lbReq = recordedRequests[1]
            assertEquals("submitlbentry", lbReq.formParams["r"])
            assertEquals("Alice", lbReq.formParams["u"])
            assertEquals("fresh_alice_token", lbReq.formParams["t"])
            assertEquals("880", lbReq.formParams["i"])
            assertEquals("95400", lbReq.formParams["s"])
            assertEquals("90", lbReq.formParams["o"])
            assertEquals(md5("880Alice9540090"), lbReq.formParams["v"])
        }

    @Test
    fun coreComplianceMatrix_enforcesAll24CompliantCoresAndBlocks5NonCompliantCores_andGatesDefaultApproval() {
        // Default public approval flag MUST be false until RAdmin approves Arcadune's User-Agent.
        assertFalse(RetroAchievementsPreferences.HARDCORE_SUPPORT_APPROVED)
        assertFalse(RetroAchievementsPreferences.isHardcoreApproved())

        val state =
            RetroAchievementsSettingsState(
                account =
                    RetroAchievementsAccount(
                        username = "Tester",
                        displayName = "Tester",
                        token = "tok",
                        avatarUrl = "",
                        score = 10,
                        softcoreScore = 0,
                    ),
                enabled = true,
                hardcoreEnabled = true,
            )
        assertFalse(state.isHardcoreActive)
        RetroAchievementsPreferences.hardcoreApprovalOverrideForTesting = true
        assertTrue(state.isHardcoreActive)

        val compliantPairs =
            listOf(
                CoreID.STELLA to SystemID.ATARI2600,
                CoreID.FCEUMM to SystemID.NES,
                CoreID.SNES9X to SystemID.SNES,
                CoreID.GENESIS_PLUS_GX to SystemID.SMS,
                CoreID.GENESIS_PLUS_GX to SystemID.GG,
                CoreID.GENESIS_PLUS_GX to SystemID.GENESIS,
                CoreID.GENESIS_PLUS_GX to SystemID.SEGACD,
                CoreID.GAMBATTE to SystemID.GB,
                CoreID.GAMBATTE to SystemID.GBC,
                CoreID.MGBA to SystemID.GBA,
                CoreID.MUPEN64_PLUS_NEXT to SystemID.N64,
                CoreID.PCSX_REARMED to SystemID.PSX,
                CoreID.PPSSPP to SystemID.PSP,
                CoreID.FBNEO to SystemID.FBNEO,
                CoreID.MELONDS to SystemID.NDS,
                CoreID.HANDY to SystemID.LYNX,
                CoreID.MEDNAFEN_PCE_FAST to SystemID.PC_ENGINE,
                CoreID.PROSYSTEM to SystemID.ATARI7800,
                CoreID.MEDNAFEN_NGP to SystemID.NGP,
                CoreID.MEDNAFEN_NGP to SystemID.NGC,
                CoreID.MEDNAFEN_WSWAN to SystemID.WS,
                CoreID.MEDNAFEN_WSWAN to SystemID.WSC,
                CoreID.DOSBOX_PURE to SystemID.DOS,
                CoreID.FLYCAST to SystemID.DREAMCAST,
                CoreID.OPERA to SystemID.THREEDO,
                CoreID.PICODRIVE to SystemID.SEGA32X,
                CoreID.GEARCOLECO to SystemID.COLECOVISION,
                CoreID.VICE_X64 to SystemID.C64,
                CoreID.APPLEWIN to SystemID.APPLEII,
                CoreID.FMSX to SystemID.MSX,
            )

        for ((core, system) in compliantPairs) {
            assertTrue(
                "Expected $core on $system to be Hardcore-compliant",
                RetroAchievementsConsoleMap.isCoreHardcoreCompliant(core, system),
            )
            assertNull(RetroAchievementsConsoleMap.hardcoreUnsupportedReason(core, system))
        }

        val blockedPairs =
            listOf(
                CoreID.CITRA to SystemID.NINTENDO_3DS,
                CoreID.AZAHAR to SystemID.NINTENDO_3DS,
                CoreID.ATARI800 to SystemID.ATARI800,
                CoreID.A5200 to SystemID.ATARI5200,
                CoreID.YABASANSHIRO to SystemID.SATURN,
                CoreID.MAME2003PLUS to SystemID.MAME2003PLUS,
                CoreID.BSNES to SystemID.SNES,
                CoreID.RACE to SystemID.NGP,
                CoreID.RACE to SystemID.NGC,
                CoreID.ARMGBA to SystemID.GBA,
                CoreID.GPSP to SystemID.GBA,
                CoreID.BLASTEM to SystemID.GENESIS,
                CoreID.PARALLEL_N64 to SystemID.N64,
            )

        for ((core, system) in blockedPairs) {
            assertFalse(
                "Expected $core on $system to block Hardcore",
                RetroAchievementsConsoleMap.isCoreHardcoreCompliant(core, system),
            )
            val reason = RetroAchievementsConsoleMap.hardcoreUnsupportedReason(core, system)
            assertNotNull("Missing unsupported reason for $core on $system", reason)
            assertTrue(reason!!.isNotBlank())
        }

        // Every CoreID must carry non-blank FOSS license and HTTPS upstream URL for AboutDialog compliance.
        for (core in CoreID.values()) {
            assertTrue("Blank license on $core", core.license.isNotBlank())
            assertTrue("Non-HTTPS upstreamUrl on $core", core.upstreamUrl.startsWith("https://"))
        }
    }

    private fun parseFormBody(body: String): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        for (part in body.split('&')) {
            val idx = part.indexOf('=')
            if (idx > 0) {
                val key = URLDecoder.decode(part.substring(0, idx), "UTF-8")
                val value = URLDecoder.decode(part.substring(idx + 1), "UTF-8")
                map[key] = value
            }
        }
        return map
    }

    private fun md5(text: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private class LocalLoopbackHttpServer(
        private val handler: (String, String, Map<String, String>, String) -> Pair<Int, String>,
    ) {
        private val serverSocket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        private val running = AtomicBoolean(false)
        private val executor = Executors.newCachedThreadPool()

        val port: Int
            get() = serverSocket.localPort

        fun start() {
            if (!running.compareAndSet(false, true)) return
            executor.execute {
                while (running.get()) {
                    val client = runCatching { serverSocket.accept() }.getOrNull() ?: break
                    executor.execute { handleClient(client) }
                }
            }
        }

        fun close() {
            running.set(false)
            runCatching { serverSocket.close() }
            executor.shutdownNow()
        }

        private fun handleClient(socket: Socket) {
            socket.use { sock ->
                sock.soTimeout = 5_000
                val input = BufferedInputStream(sock.getInputStream())
                val requestLine = readAsciiLine(input) ?: return
                val lineParts = requestLine.split(' ')
                val method = lineParts.getOrElse(0) { "POST" }
                val path = lineParts.getOrElse(1) { "/" }

                val headers = LinkedHashMap<String, String>()
                while (true) {
                    val headerLine = readAsciiLine(input) ?: break
                    if (headerLine.isEmpty()) break
                    val colon = headerLine.indexOf(':')
                    if (colon > 0) {
                        val k = headerLine.substring(0, colon).trim().lowercase()
                        val v = headerLine.substring(colon + 1).trim()
                        headers[k] = v
                    }
                }

                val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                val bodyBytes = ByteArray(contentLength)
                var offset = 0
                while (offset < contentLength) {
                    val read = input.read(bodyBytes, offset, contentLength - offset)
                    if (read <= 0) break
                    offset += read
                }
                val rawBody = String(bodyBytes, 0, offset, Charsets.UTF_8)

                val (statusCode, responseBody) = handler(method, path, headers, rawBody)
                val respBytes = responseBody.toByteArray(Charsets.UTF_8)
                val reason = if (statusCode in 200..299) "OK" else "Service Unavailable"
                val headerText =
                    "HTTP/1.1 $statusCode $reason\r\n" +
                        "Content-Type: application/json; charset=utf-8\r\n" +
                        "Content-Length: ${respBytes.size}\r\n" +
                        "Connection: close\r\n\r\n"
                val out = sock.getOutputStream()
                out.write(headerText.toByteArray(Charsets.US_ASCII))
                out.write(respBytes)
                out.flush()
            }
        }

        private fun readAsciiLine(input: BufferedInputStream): String? {
            val buf = ByteArrayOutputStream()
            var sawAny = false
            while (true) {
                val b = input.read()
                if (b == -1) {
                    return if (sawAny) buf.toString("US-ASCII") else null
                }
                sawAny = true
                if (b == '\n'.code) break
                if (b != '\r'.code) buf.write(b)
            }
            return buf.toString("US-ASCII")
        }
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val map = LinkedHashMap<String, Any?>()

        @Synchronized
        override fun getAll(): MutableMap<String, *> = LinkedHashMap(map)

        @Synchronized
        override fun getString(
            key: String?,
            defValue: String?,
        ): String? = (map[key] as? String) ?: defValue

        @Suppress("UNCHECKED_CAST")
        @Synchronized
        override fun getStringSet(
            key: String?,
            defValues: MutableSet<String>?,
        ): MutableSet<String>? = (map[key] as? Set<String>)?.toMutableSet() ?: defValues

        @Synchronized
        override fun getInt(
            key: String?,
            defValue: Int,
        ): Int = (map[key] as? Int) ?: defValue

        @Synchronized
        override fun getLong(
            key: String?,
            defValue: Long,
        ): Long = (map[key] as? Long) ?: defValue

        @Synchronized
        override fun getFloat(
            key: String?,
            defValue: Float,
        ): Float = (map[key] as? Float) ?: defValue

        @Synchronized
        override fun getBoolean(
            key: String?,
            defValue: Boolean,
        ): Boolean = (map[key] as? Boolean) ?: defValue

        @Synchronized
        override fun contains(key: String?): Boolean = map.containsKey(key)

        override fun edit(): SharedPreferences.Editor = EditorImpl()

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) = Unit

        private inner class EditorImpl : SharedPreferences.Editor {
            private val staged = LinkedHashMap<String, Any?>()
            private val removals = HashSet<String>()
            private var clearAll = false

            override fun putString(
                key: String?,
                value: String?,
            ): SharedPreferences.Editor =
                apply {
                    if (key != null) staged[key] = value
                }

            override fun putStringSet(
                key: String?,
                values: MutableSet<String>?,
            ): SharedPreferences.Editor =
                apply {
                    if (key != null) staged[key] = values?.toSet()
                }

            override fun putInt(
                key: String?,
                value: Int,
            ): SharedPreferences.Editor =
                apply {
                    if (key != null) staged[key] = value
                }

            override fun putLong(
                key: String?,
                value: Long,
            ): SharedPreferences.Editor =
                apply {
                    if (key != null) staged[key] = value
                }

            override fun putFloat(
                key: String?,
                value: Float,
            ): SharedPreferences.Editor =
                apply {
                    if (key != null) staged[key] = value
                }

            override fun putBoolean(
                key: String?,
                value: Boolean,
            ): SharedPreferences.Editor =
                apply {
                    if (key != null) staged[key] = value
                }

            override fun remove(key: String?): SharedPreferences.Editor =
                apply {
                    if (key != null) removals.add(key)
                }

            override fun clear(): SharedPreferences.Editor =
                apply {
                    clearAll = true
                }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                synchronized(this@InMemorySharedPreferences) {
                    if (clearAll) map.clear()
                    for (rem in removals) map.remove(rem)
                    for ((k, v) in staged) {
                        if (v == null) map.remove(k) else map[k] = v
                    }
                }
            }
        }
    }
}
