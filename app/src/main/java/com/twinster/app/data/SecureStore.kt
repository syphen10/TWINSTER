package com.twinster.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "twinster_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var byokApiKey: String?
        get() = prefs.getString(KEY_BYOK, null)
        set(value) = prefs.edit().putString(KEY_BYOK, value).apply()

    var byokProvider: String?
        get() = prefs.getString(KEY_BYOK_PROVIDER, null)
        set(value) = prefs.edit().putString(KEY_BYOK_PROVIDER, value).apply()

    companion object {
        private const val KEY_BYOK = "byok_api_key"
        private const val KEY_BYOK_PROVIDER = "byok_provider"
    }
}
