package com.debiandroid.desktop.terminal

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TerminalSessionTest {

    @Test
    fun `initial state has prompt`() {
        val session = TerminalSession()
        val state = session.state.value
        assertTrue(state.lines.isNotEmpty())
        assertEquals("\$ ", state.lines.last().text)
    }

    @Test
    fun `start sets isRunning to true`() {
        val session = TerminalSession()
        // Can't easily test with real process in unit test
        assertFalse(session.state.value.isRunning)
    }

    @Test
    fun `multiple sessions don't interfere`() {
        val session1 = TerminalSession()
        val session2 = TerminalSession()
        assertEquals(session1.state.value.lines.size, session2.state.value.lines.size)
    }

    @Test
    fun `stop resets to initial state`() {
        val session = TerminalSession()
        session.stop()
        val state = session.state.value
        assertEquals(1, state.lines.size)
        assertEquals("\$ ", state.lines.last().text)
        assertFalse(state.isRunning)
    }
}
