package com.swordfish.lemuroid.lib.cheats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ChtParserTest {
    @Test
    fun readsDescriptionsCodesAndKeepsFileOrder() {
        val text =
            """
            cheats = 3

            cheat0_desc = "Totally Invincible P1"
            cheat0_code = "00AE:41"
            cheat0_enable = false

            cheat1_desc = "Infinite Lives P2"
            cheat1_code = "0033:09"
            cheat1_enable = true

            cheat10_desc = "Last one"
            cheat10_code = "0024:01"
            """.trimIndent()

        val cheats = ChtParser.parse(text)

        assertEquals(listOf("Totally Invincible P1", "Infinite Lives P2", "Last one"), cheats.map { it.description })
        assertEquals(listOf("00AE:41", "0033:09", "0024:01"), cheats.map { it.code })
    }

    @Test
    fun importedCheatsAlwaysStartSwitchedOff() {
        val cheats = ChtParser.parse("cheat0_desc = \"A\"\ncheat0_code = \"1234\"\ncheat0_enable = true")
        assertFalse(cheats.single().enabled)
    }

    @Test
    fun cheatWithoutCodeIsSkippedAndMissingDescriptionGetsAName() {
        val cheats = ChtParser.parse("cheat0_desc = \"No code\"\ncheat1_code = \"ABCD\"")
        assertEquals(1, cheats.size)
        assertEquals("Cheat 2", cheats.single().description)
    }

    @Test
    fun garbageAndEmptyInputGiveNoCheats() {
        assertTrue(ChtParser.parse("").isEmpty())
        assertTrue(ChtParser.parse("<html>404: Not Found</html>").isEmpty())
    }

    @Test
    fun codesWithSpacesAndPlusSignsAreKeptAsIs() {
        val cheats = ChtParser.parse("cheat0_desc = \"X\"\ncheat0_code = \"82003210 0063+82003212 0001\"")
        assertEquals("82003210 0063+82003212 0001", cheats.single().code)
    }
}

class LibretroTitleMatcherTest {
    @Test
    fun matchesAcrossRegionPublisherAndExtraTags() {
        val files = listOf("Wario Land 4 (USA, Europe) (Code Breaker)", "Other Game (USA)")
        assertEquals("Wario Land 4 (USA, Europe) (Code Breaker)", LibretroTitleMatcher.findBest(files, "Wario Land 4 (USA, Europe)"))
        assertEquals("Contra (USA)", LibretroTitleMatcher.findBest(listOf("Contra (USA)"), "Contra (Konami) (USA)"))
    }

    @Test
    fun romanNumeralsCaseAndPunctuationDoNotMatter() {
        val files = listOf("Takahashi Meijin no Bouken Jima IV (1994-06-24)(Hudson)(JP)")
        assertEquals(files.single(), LibretroTitleMatcher.findBest(files, "takahashi meijin no bouken jima 4"))
    }

    @Test
    fun prefersUsaThenCleanDumpsAndNeverGuessesADifferentGame() {
        val files = listOf("Game (Europe)", "Game (USA) [b]", "Game (USA)", "Game 2 (USA)")
        assertEquals("Game (USA)", LibretroTitleMatcher.findBest(files, "Game"))
        assertNull(LibretroTitleMatcher.findBest(files, "Another Game"))
    }

    @Test
    fun emptyTitleFindsNothing() {
        assertNull(LibretroTitleMatcher.findBest(listOf("Game (USA)"), "(USA)"))
    }
}

class CheatsStoreTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun savedCheatsComeBackIdentically() {
        val store = CheatsStore(temp.newFolder("cheats"))
        val cheats = listOf(Cheat("Infinite Lives", "0033:09", true), Cheat("Invincible", "00AE:41"))

        store.save("nes__Contra.nes", cheats)

        assertEquals(cheats, store.load("nes__Contra.nes"))
    }

    @Test
    fun differentGamesDoNotShareCheats() {
        val store = CheatsStore(temp.newFolder("cheats"))
        store.save("nes__A.nes", listOf(Cheat("a", "1")))
        store.save("nes__B.nes", listOf(Cheat("b", "2")))

        assertEquals("a", store.load("nes__A.nes").single().description)
        assertEquals("b", store.load("nes__B.nes").single().description)
        assertTrue(store.load("nes__C.nes").isEmpty())
    }

    @Test
    fun brokenFileGivesNoCheatsInsteadOfCrashing() {
        val directory = temp.newFolder("cheats")
        val store = CheatsStore(directory)
        store.save("nes__A.nes", listOf(Cheat("a", "1")))
        directory.listFiles()!!.single { it.name.endsWith(".json") }.writeText("{ not json")

        assertTrue(store.load("nes__A.nes").isEmpty())
    }

    @Test
    fun savingAnEmptyListRemovesTheFileAndLeavesNoTemporaryFiles() {
        val directory = temp.newFolder("cheats")
        val store = CheatsStore(directory)
        store.save("nes__A.nes", listOf(Cheat("a", "1")))
        store.save("nes__A.nes", emptyList())

        assertTrue(directory.listFiles()!!.isEmpty())
        assertTrue(File(directory, "x.tmp").let { !it.exists() })
    }

    @Test
    fun fileNamesWithOddCharactersAreSafe() {
        val directory = temp.newFolder("cheats")
        val store = CheatsStore(directory)
        store.save("gba__../../Evil Game (USA).gba", listOf(Cheat("a", "1")))

        assertEquals(1, directory.listFiles()!!.size)
        assertEquals(directory.canonicalPath, directory.listFiles()!!.single().canonicalFile.parentFile.path)
    }
}

class CheatApplierTest {
    /** Records what is sent to the emulator, and behaves like the NES core: it keeps every cheat it is given. */
    private class FakeCore(private val canClear: Boolean = true) : CheatTarget {
        val calls = mutableListOf<String>()
        val activeCodes = mutableListOf<String>()

        override fun clear(): Boolean {
            calls += "clear"
            if (canClear) activeCodes.clear()
            return canClear
        }

        override fun enable(
            index: Int,
            code: String,
        ) {
            calls += "enable $index $code"
            activeCodes += code
        }
    }

    private val list =
        listOf(
            Cheat("30 lives option", "0024:01", enabled = false),
            Cheat("Invincible", "00AE:41", enabled = true),
            Cheat("Boss", "G".repeat(300), enabled = true),
            Cheat("Extra life", "NYAIUZ", enabled = true),
        )

    @Test
    fun clearsFirstThenAddsOnlyTheSwitchedOnCheatsThatTheCoreCanTake() {
        val core = FakeCore()

        val active = CheatApplier(core, "nes").apply(list)

        assertEquals(2, active)
        assertEquals(listOf("clear", "enable 0 00AE:41", "enable 1 NYAIUZ"), core.calls)
    }

    @Test
    fun aLongNesCheatIsSentInPiecesAndCountsAsOneCheat() {
        val core = FakeCore()
        val weapons = Cheat("Press Select To Change Weapons", (1..32).joinToString("+") { "GGKIZAKX" }, enabled = true)

        val active = CheatApplier(core, "nes").apply(listOf(weapons))

        assertEquals(1, active)
        assertEquals(2, core.activeCodes.size)
        assertTrue(core.activeCodes.all { it.length <= Cheat.MAX_CODE_LENGTH })
        assertEquals(weapons.code, core.activeCodes.joinToString("+"))
    }

    private val kirby =
        listOf(
            Cheat("Enable Code (Must Be On)", "0000F3FD+000A+100011E6+0007", enabled = false),
            Cheat("Infinite Health", "32005588+0030", enabled = true),
            Cheat("Infinite Lives", "32007D48+0009", enabled = false),
        )

    @Test
    fun aGbaMasterCodeIsNeverSentEvenWhenSwitchedOn() {
        val core = FakeCore()

        val active = CheatApplier(core, "gba").apply(kirby.map { it.copy(enabled = true) })

        assertEquals(2, active)
        assertEquals(listOf("32005588+0030", "32007D48+0009"), core.activeCodes)
        assertTrue(core.activeCodes.none { it.contains("100011E6") })
    }

    @Test
    fun aGbaMasterCodeAloneSendsNothing() {
        val core = FakeCore()

        assertEquals(0, CheatApplier(core, "gba").apply(kirby.mapIndexed { i, c -> c.copy(enabled = i == 0) }))
        assertTrue(core.activeCodes.isEmpty())
    }

    @Test
    fun hookAndGameIdLinesAreRemovedFromCodeBreakerCodesOnTheGbaOnly() {
        val mixed = "0000F3FD+000A+100011E6+0007+32005588+0030"

        assertEquals("32005588+0030", CheatPolicy.withoutHookLines(mixed, "gba"))
        assertEquals(mixed, CheatPolicy.withoutHookLines(mixed, "nes"))
    }

    @Test
    fun otherGbaCodesAreLeftAlone() {
        // Ordinary Code Breaker lines (types 2 to F) and 8+8 digit GameShark lines are not touched.
        assertEquals("32005588+0030+82003210+0063", CheatPolicy.withoutHookLines("32005588+0030+82003210+0063", "gba"))
        assertEquals("92225990 00000000", CheatPolicy.withoutHookLines("92225990 00000000", "gba"))
        assertEquals("ABC", CheatPolicy.withoutHookLines("ABC", "gba"))
    }

    @Test
    fun aGbaCheatThatIsOnlyHookLinesSendsNothing() {
        val core = FakeCore()

        val active = CheatApplier(core, "gba").apply(listOf(Cheat("Setup", "0000F3FD+000A+100011E6+0007", enabled = true)))

        assertEquals(0, active)
        assertTrue(core.activeCodes.isEmpty())
    }

    @Test
    fun masterCodesAreStillSentOnOtherSystems() {
        val core = FakeCore()

        CheatApplier(core, "snes").apply(listOf(Cheat("Master Code", "7E0DBE05", enabled = true)))

        assertEquals(listOf("7E0DBE05"), core.activeCodes)
    }

    @Test
    fun masterCodesAreRecognisedByName() {
        assertTrue(CheatPolicy.isMasterCode("Enable Code (Must Be On)"))
        assertTrue(CheatPolicy.isMasterCode("Master Code"))
        assertFalse(CheatPolicy.isMasterCode("Infinite Health"))
        assertFalse(CheatPolicy.isMasterCode("Enable 30 Lives Option"))
    }

    @Test
    fun theSameLongCheatIsNotSentAtAllOnOtherSystems() {
        val core = FakeCore()
        val long = Cheat("Long", (1..32).joinToString("+") { "GGKIZAKX" }, enabled = true)

        assertEquals(0, CheatApplier(core, "gba").apply(listOf(long)))
        assertEquals(listOf("clear"), core.calls)
    }

    @Test
    fun aSwitchedOffCheatIsNeverSentAndEndsUpOff() {
        val core = FakeCore()
        CheatApplier(core, "nes").apply(list)
        // The player switches "Invincible" off and closes the menu: the list is applied again.
        CheatApplier(core, "nes").apply(list.map { if (it.description == "Invincible") it.copy(enabled = false) else it })

        assertEquals(listOf("NYAIUZ"), core.activeCodes)
        assertTrue(core.calls.none { it.contains("0024:01") })
    }

    @Test
    fun applyingTheSameListTwiceDoesNotStackCheats() {
        val core = FakeCore()
        CheatApplier(core, "nes").apply(list)
        CheatApplier(core, "nes").apply(list)

        assertEquals(listOf("00AE:41", "NYAIUZ"), core.activeCodes)
    }

    @Test
    fun ifTheCheatsCannotBeClearedNothingIsAdded() {
        val core = FakeCore(canClear = false)

        assertNull(CheatApplier(core, "nes").apply(list))
        assertEquals(listOf("clear"), core.calls)
    }

    @Test
    fun emptyListJustClears() {
        val core = FakeCore()
        assertEquals(0, CheatApplier(core, "nes").apply(emptyList()))
        assertEquals(listOf("clear"), core.calls)
    }
}

class CheatSafetyTest {
    @get:Rule
    val temp = TemporaryFolder()

    /** The shape of the code that made the NES core abort: 32 parts of 8 characters joined with "+", 287 characters. */
    private val crashingCode = (1..32).joinToString("+") { "GGKIZAKX" }

    @Test
    fun shortCodesAreSentAsTheyAreOnEverySystem() {
        assertEquals(listOf("NYAIUZ"), CheatPolicy.chunks("NYAIUZ", "nes"))
        assertEquals(listOf("A".repeat(255)), CheatPolicy.chunks("A".repeat(255), "gba"))
    }

    @Test
    fun aLongNesCodeIsCutAtPlusSignsIntoPiecesThatFit() {
        assertEquals(287, crashingCode.length)

        val chunks = CheatPolicy.chunks(crashingCode, "nes")!!

        assertEquals(2, chunks.size)
        assertTrue(chunks.all { it.length <= Cheat.MAX_CODE_LENGTH })
        assertTrue(chunks.all { piece -> piece.split('+').all { it == "GGKIZAKX" } })
        assertEquals(crashingCode, chunks.joinToString("+"))
    }

    @Test
    fun longCodesAreRefusedOnSystemsWherePartsMayDependOnEachOther() {
        for (system in listOf("gba", "n64", "psx", "psp", "gb", "gbc", "nds", "ngp", "atari2600", "atari7800", "lynx", "mame2003plus")) {
            assertNull("$system must not split", CheatPolicy.chunks(crashingCode, system))
            assertFalse(CheatPolicy.isSupported(crashingCode, system))
        }
    }

    @Test
    fun longCodesAreSplitOnTheSystemsWhoseCoresReadPartsIndependently() {
        for (system in listOf("nes", "snes", "md", "sms", "gg", "scd")) {
            val chunks = CheatPolicy.chunks(crashingCode, system)!!
            assertTrue("$system chunks must fit", chunks.all { it.length <= Cheat.MAX_CODE_LENGTH })
            assertEquals(crashingCode, chunks.joinToString("+"))
        }
    }

    @Test
    fun aSinglePartThatIsTooLongIsRefusedEvenOnTheNes() {
        assertNull(CheatPolicy.chunks("A".repeat(Cheat.MAX_CODE_LENGTH + 1), "nes"))
        assertNull(CheatPolicy.chunks("OK+" + "B".repeat(400), "nes"))
    }

    @Test
    fun crashMarkerSwitchesOffEveryCheatOfThatGameOnly() {
        val store = CheatsStore(temp.newFolder("cheats"))
        store.save("nes__Contra.nes", listOf(Cheat("a", "1", true), Cheat("b", "2", true)))
        store.save("gba__Wario.gba", listOf(Cheat("c", "3", true)))

        store.startApplying("nes__Contra.nes")
        // ... the game dies here, finishApplying() is never called ...
        val recovered = CheatsStore(store.let { temp.root.resolve("cheats") }).switchOffAfterCrash()

        assertEquals("nes__Contra.nes", recovered)
        assertTrue(store.load("nes__Contra.nes").none { it.enabled })
        assertEquals(2, store.load("nes__Contra.nes").size)
        assertTrue(store.load("gba__Wario.gba").single().enabled)
    }

    @Test
    fun noMarkerAfterANormalApplyMeansNothingIsSwitchedOff() {
        val store = CheatsStore(temp.newFolder("cheats"))
        store.save("nes__Contra.nes", listOf(Cheat("a", "1", true)))

        store.startApplying("nes__Contra.nes")
        store.finishApplying()

        assertNull(store.switchOffAfterCrash())
        assertTrue(store.load("nes__Contra.nes").single().enabled)
    }

    @Test
    fun recoveryIsOnlyDoneOnce() {
        val store = CheatsStore(temp.newFolder("cheats"))
        store.save("nes__Contra.nes", listOf(Cheat("a", "1", true)))
        store.startApplying("nes__Contra.nes")

        assertEquals("nes__Contra.nes", store.switchOffAfterCrash())
        assertNull(store.switchOffAfterCrash())
    }

    @Test
    fun aDamagedMarkerDoesNotCrashAndIsCleanedUp() {
        val directory = temp.newFolder("cheats")
        val store = CheatsStore(directory)
        File(directory, "applying.marker").writeText("")

        assertNull(store.switchOffAfterCrash())
        assertFalse(File(directory, "applying.marker").exists())
    }
}

class CheatsDownloaderParsingTest {
    @Test
    fun readsOnlyChtFileNamesFromAGithubTree() {
        val json =
            """{"sha":"x","tree":[{"path":"Contra (USA).cht","type":"blob"},{"path":"readme.txt","type":"blob"},{"path":"Super C (USA).cht","type":"blob"}],"truncated":false}"""
        assertEquals(listOf("Contra (USA)", "Super C (USA)"), CheatsDownloader.parseTreeNames(json))
    }

    @Test
    fun answerWithoutATreeGivesNoNames() {
        assertTrue(CheatsDownloader.parseTreeNames("""{"message":"Not Found"}""").isEmpty())
    }
}
