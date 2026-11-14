package com.swordfish.lemuroid.lib.achievements

import com.swordfish.lemuroid.lib.library.SystemID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RetroAchievementsTest {
    @Test
    fun `console map maps all 24 supported systems to positive rcheevos console IDs and excludes 3DS`() {
        val allSystems = SystemID.values()
        assertEquals(25, allSystems.size)

        allSystems.forEach { systemId ->
            val consoleId = RetroAchievementsConsoleMap.getConsoleId(systemId)
            if (systemId == SystemID.NINTENDO_3DS) {
                assertEquals(RetroAchievementsConsoleMap.RC_CONSOLE_UNKNOWN, consoleId)
                assertFalse(RetroAchievementsConsoleMap.isSupported(systemId))
            } else {
                assertTrue("Expected positive RC_CONSOLE_* ID for $systemId", consoleId > 0)
                assertTrue(RetroAchievementsConsoleMap.isSupported(systemId))
            }
        }

        assertEquals(7, RetroAchievementsConsoleMap.getConsoleId(SystemID.NES))
        assertEquals(3, RetroAchievementsConsoleMap.getConsoleId(SystemID.SNES))
        assertEquals(5, RetroAchievementsConsoleMap.getConsoleId(SystemID.GBA))
        assertEquals(12, RetroAchievementsConsoleMap.getConsoleId(SystemID.PSX))
        assertEquals(18, RetroAchievementsConsoleMap.getConsoleId(SystemID.NDS))
        assertEquals(41, RetroAchievementsConsoleMap.getConsoleId(SystemID.PSP))
        assertEquals(27, RetroAchievementsConsoleMap.getConsoleId(SystemID.FBNEO))
    }

    @Test
    fun `parseLoginPayload decodes valid Connect login2 response`() {
        val rawJson =
            """
            {
              "Success": true,
              "User": "ArcaduneHero",
              "Token": "tok_abc123xyz",
              "Score": 4250,
              "SoftcoreScore": 850,
              "Messages": 2
            }
            """.trimIndent()

        val result = RetroAchievementsAuthClient.parseLoginPayload("arcadunehero", rawJson)
        assertTrue(result.isSuccess)
        val account = result.getOrNull()
        assertNotNull(account)
        assertEquals("ArcaduneHero", account!!.username)
        assertEquals("ArcaduneHero", account.displayName)
        assertEquals("tok_abc123xyz", account.token)
        assertEquals(4250, account.score)
        assertEquals(850, account.softcoreScore)
        assertEquals("https://media.retroachievements.org/UserPic/ArcaduneHero.png", account.avatarUrl)
    }

    @Test
    fun `parseLoginPayload returns failure with server error message when credentials invalid`() {
        val rawJson = """{"Success":false,"Error":"Invalid User/Password combination. Please try again."}"""
        val result = RetroAchievementsAuthClient.parseLoginPayload("player1", rawJson)
        assertTrue(result.isFailure)
        assertEquals(
            "Invalid User/Password combination. Please try again.",
            result.exceptionOrNull()?.message,
        )
    }

    @Test
    fun `parseLoginPayload fails when token is blank or JSON is malformed`() {
        val missingToken = RetroAchievementsAuthClient.parseLoginPayload("player1", """{"Success":true,"Token":""}""")
        assertTrue(missingToken.isFailure)

        val malformed = RetroAchievementsAuthClient.parseLoginPayload("player1", "<html>502 Bad Gateway</html>")
        assertTrue(malformed.isFailure)
    }

    @Test
    fun `settings state computes active and hardcore flags accurately`() {
        val signedOut = RetroAchievementsSettingsState(account = null, enabled = true, hardcoreEnabled = true)
        assertFalse(signedOut.isSignedIn)
        assertFalse(signedOut.isActive)
        assertFalse(signedOut.isHardcoreActive)

        val account =
            RetroAchievementsAccount(
                username = "PlayerOne",
                displayName = "PlayerOne",
                token = "secret_token",
                avatarUrl = RetroAchievementsPreferences.defaultAvatarUrl("PlayerOne"),
                score = 100,
                softcoreScore = 50,
            )

        val softcore = RetroAchievementsSettingsState(account = account, enabled = true, hardcoreEnabled = false)
        assertTrue(softcore.isSignedIn)
        assertTrue(softcore.isActive)
        assertFalse(softcore.isHardcoreActive)
        assertTrue(softcore.soundEffectsEnabled)

        val muted = softcore.copy(soundEffectsEnabled = false)
        assertFalse(muted.soundEffectsEnabled)

        val hardcore = softcore.copy(hardcoreEnabled = true)
        assertFalse(hardcore.isHardcoreActive)
        assertTrue(hardcore.isHardcoreActive(hardcoreApproved = true))
        assertEquals(150, account.totalPoints)

        val paused = hardcore.copy(enabled = false)
        assertTrue(paused.isSignedIn)
        assertFalse(paused.isActive)
        assertFalse(paused.isHardcoreActive(hardcoreApproved = true))
    }
}
