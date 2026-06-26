package com.debiandroid.desktop.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "session_prefs")

class SessionManager(private val context: Context) {
    companion object {
        private val SETUP_COMPLETE = booleanPreferencesKey("setup_complete")
        private val VNC_PASSWORD = stringPreferencesKey("vnc_password")
        private val DESKTOP_RESOLUTION = stringPreferencesKey("desktop_resolution")
        private val DARK_MODE = booleanPreferencesKey("dark_mode")
    }

    val isSetupComplete: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SETUP_COMPLETE] ?: false
    }

    val vncPassword: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[VNC_PASSWORD] ?: "debian"
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
        context.dataStore.edit { prefs ->
            prefs[VNC_PASSWORD] = password
        }
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
    }
}
