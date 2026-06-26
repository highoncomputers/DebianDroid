package com.debiandroid.desktop.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SessionManagerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var sessionManager: SessionManager

    @Before
    fun setUp() = runBlocking {
        sessionManager = SessionManager(context)
        sessionManager.resetAll()
        sessionManager = SessionManager(context)
    }

    @Test
    fun `default setup complete is false`() = runBlocking {
        val isComplete = sessionManager.isSetupComplete.first()
        assertFalse(isComplete)
    }

    @Test
    fun `set setup complete persists`() = runBlocking {
        sessionManager.setSetupComplete(true)
        val isComplete = sessionManager.isSetupComplete.first()
        assertTrue(isComplete)
    }

    @Test
    fun `default resolution is 1280x720`() = runBlocking {
        val resolution = sessionManager.desktopResolution.first()
        assertEquals("1280x720", resolution)
    }

    @Test
    fun `set resolution persists`() = runBlocking {
        sessionManager.setDesktopResolution("1920x1080")
        val resolution = sessionManager.desktopResolution.first()
        assertEquals("1920x1080", resolution)
    }

    @Test
    fun `default vnc password is debian`() = runBlocking {
        val password = sessionManager.vncPassword.first()
        assertEquals("debian", password)
    }

    @Test
    fun `set vnc password persists`() = runBlocking {
        sessionManager.setVncPassword("newpass123")
        val password = sessionManager.vncPassword.first()
        assertEquals("newpass123", password)
    }

    @Test
    fun `reset all clears preferences`() = runBlocking {
        sessionManager.setSetupComplete(true)
        sessionManager.setVncPassword("testpass")
        sessionManager.resetAll()

        assertFalse(sessionManager.isSetupComplete.first())
        assertEquals("debian", sessionManager.vncPassword.first())
    }
}
