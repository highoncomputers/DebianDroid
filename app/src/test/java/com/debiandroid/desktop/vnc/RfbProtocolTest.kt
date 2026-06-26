package com.debiandroid.desktop.vnc

import org.junit.Assert.*
import org.junit.Test

class RfbProtocolTest {

    @Test
    fun `reverseBits swaps bit order`() {
        // Use reflection to test private method
        val method = RfbProtocol::class.java.getDeclaredMethod("reverseBits", Byte::class.java)
        method.isAccessible = true

        assertEquals(0x00.toByte(), method.invoke(null, 0x00.toByte()))
        assertEquals(0x80.toByte(), method.invoke(null, 0x01.toByte()))
        assertEquals(0x01.toByte(), method.invoke(null, 0x80.toByte()))
        assertEquals(0xFF.toByte(), method.invoke(null, 0xFF.toByte()))
        assertEquals(0x0F.toByte(), method.invoke(null, 0xF0.toByte()))
    }

    @Test
    fun `VncProtocolException message`() {
        val ex = VncProtocolException("test error")
        assertEquals("test error", ex.message)
    }
}
