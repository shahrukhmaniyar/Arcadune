package com.swordfish.lemuroid.lib.saves

import com.swordfish.lemuroid.lib.library.LemuroidLibrary
import com.swordfish.lemuroid.lib.library.db.entity.Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SaveFileLocatorTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun game(
        id: Int,
        fileName: String,
        systemId: String,
        lastPlayedAt: Long? = null,
    ) = Game(
        id = id,
        fileName = fileName,
        fileUri = "content://roms/$fileName",
        title = fileName,
        systemId = systemId,
        developer = null,
        coverFrontUrl = null,
        lastIndexedAt = 0L,
        lastPlayedAt = lastPlayedAt,
    )

    private fun File.write(text: String) =
        apply {
            parentFile?.mkdirs()
            writeText(text)
        }

    @Test
    fun aGameWithAUniqueNameKeepsTheRetroArchName() {
        val saves = temp.newFolder("saves")
        val sonic = game(1, "Sonic.md", "md")
        val location = SaveFileLocator.locate(saves, sonic, emptyList())
        assertEquals(File(saves, "Sonic.srm"), location.write)
        assertNull(location.read)

        File(saves, "Sonic.srm").write("md save")
        assertEquals(File(saves, "Sonic.srm"), SaveFileLocator.locate(saves, sonic, emptyList()).read)
    }

    @Test
    fun twoConsolesWithTheSameNameGetTheirOwnFiles() {
        val saves = temp.newFolder("saves")
        val md = game(1, "Sonic.md", "md", lastPlayedAt = 200)
        val gg = game(2, "Sonic.gg", "gg", lastPlayedAt = 100)
        File(saves, "Sonic.srm").write("written last by the Mega Drive game")

        val mdLocation = SaveFileLocator.locate(saves, md, listOf(gg))
        val ggLocation = SaveFileLocator.locate(saves, gg, listOf(md))

        assertEquals(File(saves, "by-console/md/Sonic.srm"), mdLocation.write)
        assertEquals(File(saves, "by-console/gg/Sonic.srm"), ggLocation.write)
        // The shared file belongs to the game played last; the other one starts without a save.
        assertEquals(File(saves, "Sonic.srm"), mdLocation.read)
        assertNull(ggLocation.read)
    }

    @Test
    fun theSharedFileStaysWithItsOwnerAfterTheOtherGameSavesOnItsOwn() {
        val saves = temp.newFolder("saves")
        val md = game(1, "Sonic.md", "md", lastPlayedAt = 100)
        // The Game Gear game was just played, so it is the most recent one, but it already has its own file.
        val gg = game(2, "Sonic.gg", "gg", lastPlayedAt = 500)
        File(saves, "Sonic.srm").write("the Mega Drive save")
        File(saves, "by-console/gg/Sonic.srm").write("the Game Gear save")

        assertEquals(File(saves, "Sonic.srm"), SaveFileLocator.locate(saves, md, listOf(gg)).read)
        assertEquals(File(saves, "by-console/gg/Sonic.srm"), SaveFileLocator.locate(saves, gg, listOf(md)).read)
    }

    @Test
    fun sameNameOnTheSameConsoleUsesTheGameId() {
        val saves = temp.newFolder("saves")
        val a = game(7, "Tetris.gb", "gb")
        val b = game(9, "Tetris.gb", "gb")
        assertEquals(File(saves, "by-console/gb/Tetris [7].srm"), SaveFileLocator.locate(saves, a, listOf(b)).write)
        assertEquals(File(saves, "by-console/gb/Tetris [9].srm"), SaveFileLocator.locate(saves, b, listOf(a)).write)
    }

    @Test
    fun namesAreComparedLikeAndroidStorageDoesIgnoringCase() {
        val sonic = game(1, "Sonic.md", "md")
        val others = listOf(game(2, "SONIC.gg", "gg"), game(3, "Sonic 2.md", "md"))
        assertEquals(listOf(2), SaveFileLocator.collidingGames(sonic, others).map { it.id })
    }

    @Test
    fun likePrefixEscapesWildcards() {
        assertEquals("100\\% Mario\\_v2%", SaveFileLocator.likePrefix("100% Mario_v2"))
    }

    @Test
    fun aScanThatSawNothingNeverEmptiesALibrary() {
        assertFalse(LemuroidLibrary.shouldRunCleanUp(filesSeen = 0, gamesInLibrary = 120))
        assertTrue(LemuroidLibrary.shouldRunCleanUp(filesSeen = 0, gamesInLibrary = 0))
        assertTrue(LemuroidLibrary.shouldRunCleanUp(filesSeen = 3, gamesInLibrary = 120))
    }
}
