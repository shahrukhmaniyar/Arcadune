package com.swordfish.lemuroid.lib.achievements

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface TokenCipherEngine {
    fun encrypt(plaintext: String): String

    fun decrypt(encodedCiphertext: String): String?
}

class AesGcmSecretKeyCipherEngine(
    private val secretKey: SecretKey,
) : TokenCipherEngine {
    private val random = SecureRandom()

    override fun encrypt(plaintext: String): String {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        random.nextBytes(iv)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
        return combined.toByteString().base64()
    }

    override fun decrypt(encodedCiphertext: String): String? {
        if (encodedCiphertext.isBlank()) return null
        return runCatching {
            val combined = encodedCiphertext.decodeBase64()?.toByteArray() ?: return null
            if (combined.size <= GCM_IV_LENGTH_BYTES) return null
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH_BYTES)
            val payload = combined.copyOfRange(GCM_IV_LENGTH_BYTES, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(payload), Charsets.UTF_8)
        }.getOrNull()
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_BITS = 128
    }
}

object RetroAchievementsTokenStore {
    internal const val KEY_ENCRYPTED_TOKEN = "ra_token_enc_v1"
    internal const val KEY_LEGACY_PLAINTEXT_TOKEN = "ra_token"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "arcadune_ra_token_key_v1"

    @Volatile
    var cipherOverrideForTesting: TokenCipherEngine? = null

    private val defaultCipherEngine: TokenCipherEngine by lazy {
        createAndroidKeystoreCipherOrFallback()
    }

    private fun engine(): TokenCipherEngine = cipherOverrideForTesting ?: defaultCipherEngine

    fun readToken(prefs: SharedPreferences): String {
        val encrypted = prefs.getString(KEY_ENCRYPTED_TOKEN, null)
        if (!encrypted.isNullOrBlank()) {
            val decrypted = engine().decrypt(encrypted)
            if (!decrypted.isNullOrBlank()) {
                if (prefs.contains(KEY_LEGACY_PLAINTEXT_TOKEN)) {
                    prefs.edit().remove(KEY_LEGACY_PLAINTEXT_TOKEN).apply()
                }
                return decrypted
            }
        }

        val legacyPlain = prefs.getString(KEY_LEGACY_PLAINTEXT_TOKEN, null).orEmpty()
        if (legacyPlain.isNotBlank()) {
            val migratedCiphertext = runCatching { engine().encrypt(legacyPlain) }.getOrNull()
            if (!migratedCiphertext.isNullOrBlank()) {
                prefs.edit()
                    .putString(KEY_ENCRYPTED_TOKEN, migratedCiphertext)
                    .remove(KEY_LEGACY_PLAINTEXT_TOKEN)
                    .apply()
            }
            return legacyPlain
        }

        return ""
    }

    fun writeToken(
        editor: SharedPreferences.Editor,
        token: String,
    ) {
        editor.remove(KEY_LEGACY_PLAINTEXT_TOKEN)
        if (token.isBlank()) {
            editor.remove(KEY_ENCRYPTED_TOKEN)
        } else {
            editor.putString(KEY_ENCRYPTED_TOKEN, engine().encrypt(token))
        }
    }

    fun clearToken(editor: SharedPreferences.Editor) {
        editor.remove(KEY_LEGACY_PLAINTEXT_TOKEN)
        editor.remove(KEY_ENCRYPTED_TOKEN)
    }

    private fun createAndroidKeystoreCipherOrFallback(): TokenCipherEngine {
        return runCatching {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            val secretKey =
                existing ?: run {
                    val keyGen =
                        KeyGenerator.getInstance(
                            KeyProperties.KEY_ALGORITHM_AES,
                            ANDROID_KEYSTORE,
                        )
                    val spec =
                        KeyGenParameterSpec.Builder(
                            KEY_ALIAS,
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                        )
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .setKeySize(256)
                            .build()
                    keyGen.init(spec)
                    keyGen.generateKey()
                }
            AesGcmSecretKeyCipherEngine(secretKey)
        }.getOrElse {
            // A throwaway key would "work" until the next start and then silently sign the player
            // out every time. Refuse instead, so sign-in shows a clear error. (Tests set
            // [cipherOverrideForTesting].)
            UnavailableCipherEngine
        }
    }
}

/** The Android Keystore could not give Arcadune a key: the sign-in cannot be stored securely. */
class TokenStorageUnavailableException : IllegalStateException("Secure storage is not available on this device")

private object UnavailableCipherEngine : TokenCipherEngine {
    override fun encrypt(plaintext: String): String = throw TokenStorageUnavailableException()

    override fun decrypt(encodedCiphertext: String): String? = null
}
