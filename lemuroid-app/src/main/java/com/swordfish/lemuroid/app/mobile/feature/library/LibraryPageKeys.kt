package com.swordfish.lemuroid.app.mobile.feature.library

import android.view.KeyEvent

/**
 * The shoulder buttons of a gamepad turn the library's pages: L1 or L2 goes to the previous page
 * (console), R1 or R2 to the next. The activity hands every key here first, so this works even
 * before the cursor is on a game. Only while the library is on screen and not searching.
 */
object LibraryPageKeys {
    @Volatile
    private var turnPage: ((Int) -> Unit)? = null

    fun attach(listener: (Int) -> Unit) {
        turnPage = listener
    }

    fun detach(listener: (Int) -> Unit) {
        if (turnPage === listener) turnPage = null
    }

    /** True when [event] was a page turn (its press and its release are both used up). */
    fun handle(event: KeyEvent): Boolean {
        val listener = turnPage ?: return false
        val direction = directionOf(event.keyCode) ?: return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            listener(direction)
        }
        return true
    }

    internal fun directionOf(keyCode: Int): Int? =
        when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_L2 -> -1
            KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_R2 -> 1
            else -> null
        }
}
