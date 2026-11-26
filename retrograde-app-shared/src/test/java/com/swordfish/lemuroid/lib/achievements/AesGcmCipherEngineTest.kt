package com.swordfish.lemuroid.lib.achievements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import javax.crypto.KeyGenerator

class AesGcmCipherEngineTest {
    private fun engine() =
        AesGcmSecretKeyCipherEngine(
            KeyGenerator.getInstance("AES").apply { init(256) }.generateKey(),
        )

    @Test
    fun roundTripsAToken() {
        val engine = engine()
        assertEquals("live_token_abc", engine.decrypt(engine.encrypt("live_token_abc")))
    }

    // The cipher picks a fresh IV each time (the Android Keystore refuses an IV chosen by the
    // caller, which is what made every sign-in fail to be stored on real phones).
    @Test
    fun usesAFreshIvForEveryEncryption() {
        val engine = engine()
        assertNotEquals(engine.encrypt("same"), engine.encrypt("same"))
    }

    @Test
    fun refusesATamperedOrEmptyValue() {
        val engine = engine()
        assertNull(engine.decrypt(""))
        assertNull(engine.decrypt("not-base64-!!"))
        assertNull(engine.decrypt(engine.encrypt("x").dropLast(4) + "AAAA"))
    }
}
