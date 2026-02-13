package com.chattingapp.utils

import android.util.Base64
import com.chattingapp.BuildConfig
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {
    private const val PREFIX = "enc:v1:"
    private const val NONCE_LEN = 12
    private const val TAG_LEN_BITS = 128
    private const val KEY_LEN = 32

    private fun getKeyBytesOrNull(): ByteArray? {
        val keyB64 = try {
            BuildConfig.MESSAGE_ENCRYPTION_KEY_B64
        } catch (_: Exception) {
            ""
        }

        if (keyB64.isNullOrBlank()) return null

        return try {
            val key = Base64.decode(keyB64.trim(), Base64.DEFAULT)
            if (key.size != KEY_LEN) null else key
        } catch (_: Exception) {
            null
        }
    }

    fun decryptIfNeeded(value: String?): String? {
        if (value.isNullOrBlank()) return value
        if (!value.startsWith(PREFIX)) return value

        val keyBytes = getKeyBytesOrNull() ?: return value

        return try {
            val token = value.substring(PREFIX.length)
            val raw = Base64.decode(token, Base64.URL_SAFE or Base64.NO_WRAP)
            if (raw.size <= NONCE_LEN) return value

            val nonce = raw.copyOfRange(0, NONCE_LEN)
            val cipherBytes = raw.copyOfRange(NONCE_LEN, raw.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(keyBytes, "AES")
            val gcmSpec = GCMParameterSpec(TAG_LEN_BITS, nonce)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)

            val plainBytes = cipher.doFinal(cipherBytes)
            String(plainBytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            value
        }
    }

    fun isEncrypted(value: String?): Boolean {
        return !value.isNullOrBlank() && value.startsWith(PREFIX)
    }
}
