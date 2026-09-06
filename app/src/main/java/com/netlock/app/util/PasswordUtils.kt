package com.netlock.app.util

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Simple PBKDF2-based password hashing. Nothing exotic, but the password is
 * never stored (or compared) in plain text.
 */
object PasswordUtils {

    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH = 256

    fun generateSalt(): String {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    fun hash(password: String, saltB64: String): String {
        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    fun verify(password: String, saltB64: String, expectedHashB64: String): Boolean {
        val actual = hash(password, saltB64)
        // constant-time-ish comparison
        if (actual.length != expectedHashB64.length) return false
        var result = 0
        for (i in actual.indices) result = result or (actual[i].code xor expectedHashB64[i].code)
        return result == 0
    }
}
