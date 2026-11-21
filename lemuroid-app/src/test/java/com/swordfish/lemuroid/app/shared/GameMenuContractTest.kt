package com.swordfish.lemuroid.app.shared

import org.junit.Assert.assertEquals
import org.junit.Test

class GameMenuContractTest {
    @Test
    fun nextFrameSpeedCyclesThroughSupportedStepsAndWrapsToNormal() {
        // 8x systems (GBA, SNES, NES, DS, PSX, etc.) -> 4 fast-forward steps: 2x, 4x, 6x, 8x
        val steps8x = GameMenuContract.speedsUpTo(8).filter { it > 1 }
        assertEquals(listOf(2, 4, 6, 8), steps8x)
        assertEquals(2, GameMenuContract.nextFrameSpeed(1, max = 8))
        assertEquals(4, GameMenuContract.nextFrameSpeed(2, max = 8))
        assertEquals(6, GameMenuContract.nextFrameSpeed(4, max = 8))
        assertEquals(8, GameMenuContract.nextFrameSpeed(6, max = 8))
        assertEquals(1, GameMenuContract.nextFrameSpeed(8, max = 8))

        // 6x systems (N64) -> 3 fast-forward steps: 2x, 4x, 6x
        val steps6x = GameMenuContract.speedsUpTo(6).filter { it > 1 }
        assertEquals(listOf(2, 4, 6), steps6x)
        assertEquals(2, GameMenuContract.nextFrameSpeed(1, max = 6))
        assertEquals(4, GameMenuContract.nextFrameSpeed(2, max = 6))
        assertEquals(6, GameMenuContract.nextFrameSpeed(4, max = 6))
        assertEquals(1, GameMenuContract.nextFrameSpeed(6, max = 6))

        // 4x systems (PSP, Dreamcast, Saturn) -> 2 fast-forward steps: 2x, 4x
        val steps4x = GameMenuContract.speedsUpTo(4).filter { it > 1 }
        assertEquals(listOf(2, 4), steps4x)
        assertEquals(2, GameMenuContract.nextFrameSpeed(1, max = 4))
        assertEquals(4, GameMenuContract.nextFrameSpeed(2, max = 4))
        assertEquals(1, GameMenuContract.nextFrameSpeed(4, max = 4))
    }

    @Test
    fun progressiveDotCountMatchesCurrentSpeedWithinCap() {
        fun activeDots(
            speed: Int,
            max: Int,
        ): Int {
            if (speed <= 1) return 0
            val steps = GameMenuContract.speedsUpTo(max).filter { it > 1 }
            val idx = steps.indexOfLast { it <= speed }
            return if (idx >= 0) idx + 1 else 0
        }

        // 6x cap (3 total dots): 1x -> 0, 2x -> 1, 4x -> 2, 6x -> 3
        assertEquals(0, activeDots(1, max = 6))
        assertEquals(1, activeDots(2, max = 6))
        assertEquals(2, activeDots(4, max = 6))
        assertEquals(3, activeDots(6, max = 6))

        // 8x cap (4 total dots): 1x -> 0, 2x -> 1, 4x -> 2, 6x -> 3, 8x -> 4
        assertEquals(0, activeDots(1, max = 8))
        assertEquals(1, activeDots(2, max = 8))
        assertEquals(2, activeDots(4, max = 8))
        assertEquals(3, activeDots(6, max = 8))
        assertEquals(4, activeDots(8, max = 8))
    }
}
