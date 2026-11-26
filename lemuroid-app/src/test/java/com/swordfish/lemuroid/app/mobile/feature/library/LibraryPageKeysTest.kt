package com.swordfish.lemuroid.app.mobile.feature.library

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryPageKeysTest {
    @Test
    fun leftShouldersGoBackAndRightShouldersGoForward() {
        assertEquals(-1, LibraryPageKeys.directionOf(KeyEvent.KEYCODE_BUTTON_L1))
        assertEquals(-1, LibraryPageKeys.directionOf(KeyEvent.KEYCODE_BUTTON_L2))
        assertEquals(1, LibraryPageKeys.directionOf(KeyEvent.KEYCODE_BUTTON_R1))
        assertEquals(1, LibraryPageKeys.directionOf(KeyEvent.KEYCODE_BUTTON_R2))
    }

    @Test
    fun otherKeysAreLeftAlone() {
        assertNull(LibraryPageKeys.directionOf(KeyEvent.KEYCODE_BUTTON_A))
        assertNull(LibraryPageKeys.directionOf(KeyEvent.KEYCODE_DPAD_LEFT))
        assertNull(LibraryPageKeys.directionOf(KeyEvent.KEYCODE_BACK))
    }
}
