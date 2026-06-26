package com.debiandroid.desktop.vnc

import org.junit.Assert.*
import org.junit.Test

class RfbProtocolTest {

    @Test
    fun `reverseBits swaps bit order`() {
        assertEquals(0x00.toByte(), RfbProtocol.reverseBits(0x00.toByte()))
        assertEquals(0x80.toByte(), RfbProtocol.reverseBits(0x01.toByte()))
        assertEquals(0x01.toByte(), RfbProtocol.reverseBits(0x80.toByte()))
        assertEquals(0xFF.toByte(), RfbProtocol.reverseBits(0xFF.toByte()))
        assertEquals(0x0F.toByte(), RfbProtocol.reverseBits(0xF0.toByte()))
    }

    @Test
    fun `VncProtocolException message`() {
        val ex = VncProtocolException("test error")
        assertEquals("test error", ex.message)
    }
}
