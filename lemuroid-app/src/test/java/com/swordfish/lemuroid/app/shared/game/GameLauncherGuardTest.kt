package com.swordfish.lemuroid.app.shared.game

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameLauncherGuardTest {
    @After
    fun reset() = GameLauncher.onLaunchEnded()

    @Test
    fun aSecondTapWhileTheFirstGameIsStartingIsIgnored() {
        assertTrue(GameLauncher.tryStartLaunch(nowMs = 1_000))
        assertFalse(GameLauncher.tryStartLaunch(nowMs = 1_150))
        assertFalse(GameLauncher.tryStartLaunch(nowMs = 1_000 + GameLauncher.LAUNCH_GUARD_MS - 1))
    }

    @Test
    fun theNextGameCanStartOnceTheFirstHasReturned() {
        assertTrue(GameLauncher.tryStartLaunch(nowMs = 1_000))
        GameLauncher.onLaunchEnded()
        assertTrue(GameLauncher.tryStartLaunch(nowMs = 1_100))
    }

    @Test
    fun aLaunchThatNeverReportsBackStopsBlockingAfterTheGuardTime() {
        assertTrue(GameLauncher.tryStartLaunch(nowMs = 1_000))
        assertTrue(GameLauncher.tryStartLaunch(nowMs = 1_000 + GameLauncher.LAUNCH_GUARD_MS))
    }
}
