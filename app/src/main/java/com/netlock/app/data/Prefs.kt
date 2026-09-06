package com.netlock.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

enum class BlockMode { WHITELIST, BLACKLIST }

/**
 * Stores app settings (blocking mode, password hash/salt, which app packages are filtered)
 * in an encrypted SharedPreferences file so the password hash isn't sitting in plain text.
 */
class Prefs private constructor(context: Context) {

    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        prefs = EncryptedSharedPreferences.create(
            context,
            "netlock_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var mode: BlockMode
        get() = BlockMode.valueOf(prefs.getString(KEY_MODE, BlockMode.WHITELIST.name)!!)
        set(value) = prefs.edit().putString(KEY_MODE, value.name).apply()

    var passwordSalt: String?
        get() = prefs.getString(KEY_SALT, null)
        set(value) = prefs.edit().putString(KEY_SALT, value).apply()

    var passwordHash: String?
        get() = prefs.getString(KEY_HASH, null)
        set(value) = prefs.edit().putString(KEY_HASH, value).apply()

    fun hasPassword(): Boolean = passwordHash != null

    fun clearPassword() {
        prefs.edit().remove(KEY_HASH).remove(KEY_SALT).apply()
    }

    var selectedPackages: Set<String>
        get() = prefs.getStringSet(KEY_PACKAGES, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_PACKAGES, value).apply()

    var vpnRunning: Boolean
        get() = prefs.getBoolean(KEY_RUNNING, false)
        set(value) = prefs.edit().putBoolean(KEY_RUNNING, value).apply()

    companion object {
        private const val KEY_MODE = "mode"
        private const val KEY_SALT = "pw_salt"
        private const val KEY_HASH = "pw_hash"
        private const val KEY_PACKAGES = "packages"
        private const val KEY_RUNNING = "running"

        @Volatile private var INSTANCE: Prefs? = null

        fun getInstance(context: Context): Prefs =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Prefs(context.applicationContext).also { INSTANCE = it }
            }
    }
}
