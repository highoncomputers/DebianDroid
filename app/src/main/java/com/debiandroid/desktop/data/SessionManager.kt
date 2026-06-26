package com.debiandroid.desktop.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "session_prefs")

class SessionManager(private val context: Context) {
    private val encryptedPrefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "encrypted_session_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.w("SessionManager", "Falling back to plaintext SharedPreferences", e)
            context.getSharedPreferences("fallback_session_prefs", Context.MODE_PRIVATE)
        }
    }

    private val _vncPassword = MutableStateFlow(
        encryptedPrefs.getString(KEY_VNC_PASSWORD, "debian") ?: "debian"
    )
    val vncPassword: Flow<String> = _vncPassword

    companion object {
        private val SETUP_COMPLETE = booleanPreferencesKey("setup_complete")
        private val DESKTOP_RESOLUTION = stringPreferencesKey("desktop_resolution")
        private val DARK_MODE = booleanPreferencesKey("dark_mode")
        private const val KEY_VNC_PASSWORD = "vnc_password"
    }

    val isSetupComplete: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SETUP_COMPLETE] ?: false
    }

    val desktopResolution: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[DESKTOP_RESOLUTION] ?: "1280x720"
    }

    val darkMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[DARK_MODE] ?: true
    }

    suspend fun setSetupComplete(complete: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[SETUP_COMPLETE] = complete
        }
    }

    suspend fun setVncPassword(password: String) {
        encryptedPrefs.edit().putString(KEY_VNC_PASSWORD, password).apply()
        _vncPassword.value = password
    }

    suspend fun setDesktopResolution(resolution: String) {
        context.dataStore.edit { prefs ->
            prefs[DESKTOP_RESOLUTION] = resolution
        }
    }

    suspend fun setDarkMode(dark: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[DARK_MODE] = dark
        }
    }

    suspend fun resetAll() {
        context.dataStore.edit { it.clear() }
        encryptedPrefs.edit().clear().apply()
        _vncPassword.value = "debian"
    }
}
