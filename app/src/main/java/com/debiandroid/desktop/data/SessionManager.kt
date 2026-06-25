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
        private val SCREEN_WIDTH = intPreferencesKey("screen_width")
        private val SCREEN_HEIGHT = intPreferencesKey("screen_height")
        private val COLOR_DEPTH = intPreferencesKey("color_depth")
        private val LAST_SESSION_ACTIVE = booleanPreferencesKey("last_session_active")
        private val DESKTOP_RESOLUTION = stringPreferencesKey("desktop_resolution")
        private val DARK_MODE = booleanPreferencesKey("dark_mode")
        private val ONBOARDING_SHOWN = booleanPreferencesKey("onboarding_shown")
    }

    val isSetupComplete: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SETUP_COMPLETE] ?: false
    }

    val vncPassword: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[VNC_PASSWORD] ?: "debian"
    }

    val screenWidth: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[SCREEN_WIDTH] ?: 1280
    }

    val screenHeight: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[SCREEN_HEIGHT] ?: 720
    }

    val desktopResolution: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[DESKTOP_RESOLUTION] ?: "1280x720"
    }

    val wasLastSessionActive: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[LAST_SESSION_ACTIVE] ?: false
    }

    val darkMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[DARK_MODE] ?: true
    }

    val onboardingShown: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[ONBOARDING_SHOWN] ?: false
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
            val parts = resolution.split("x")
            if (parts.size == 2) {
                prefs[SCREEN_WIDTH] = parts[0].toIntOrNull() ?: 1280
                prefs[SCREEN_HEIGHT] = parts[1].toIntOrNull() ?: 720
            }
        }
    }

    suspend fun setLastSessionActive(active: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[LAST_SESSION_ACTIVE] = active
        }
    }

    suspend fun setDarkMode(dark: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[DARK_MODE] = dark
        }
    }

    suspend fun setOnboardingShown(shown: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[ONBOARDING_SHOWN] = shown
        }
    }

    suspend fun resetAll() {
        context.dataStore.edit { it.clear() }
    }
}
