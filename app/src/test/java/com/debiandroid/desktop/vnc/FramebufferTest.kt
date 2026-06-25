package com.debiandroid.desktop.vnc

import org.junit.Assert.*
import org.junit.Test

class FramebufferTest {

    @Test
    fun `create framebuffer with correct dimensions`() {
        val fb = Framebuffer(100, 50, IntArray(5000))
        assertEquals(100, fb.width)
        assertEquals(50, fb.height)
        assertEquals(5000, fb.pixels.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `throws on size mismatch`() {
        Framebuffer(100, 50, IntArray(10))
    }

    @Test
    fun `updateRegion modifies correct region`() {
        val fb = Framebuffer(10, 10, IntArray(100))
        val data = IntArray(4) { 0xFFFF0000.toInt() } // 2x2 red pixels
        fb.updateRegion(2, 2, 2, 2, data)

        // Check the 4 pixels at (2,2), (3,2), (2,3), (3,3)
        assertEquals(0xFFFF0000.toInt(), fb.pixels[22]) // (2,2) = row 2 * 10 + 2
        assertEquals(0xFFFF0000.toInt(), fb.pixels[23]) // (3,2)
        assertEquals(0xFFFF0000.toInt(), fb.pixels[32]) // (2,3)
        assertEquals(0xFFFF0000.toInt(), fb.pixels[33]) // (3,3)

        // Check surrounding pixel unchanged
        assertEquals(0, fb.pixels[0])
        assertEquals(0, fb.pixels[11])
    }

    @Test
    fun `copyRect copies region correctly`() {
        val pixels = IntArray(100)
        pixels[12] = 0xFF00FF00.toInt() // (2,1)
        pixels[13] = 0xFF0000FF.toInt() // (3,1)
        val fb = Framebuffer(10, 10, pixels)

        fb.copyRect(2, 1, 5, 5, 2, 1) // copy 2x1 from (2,1) to (5,5)

        assertEquals(0xFF00FF00.toInt(), fb.pixels[55]) // (5,5)
        assertEquals(0xFF0000FF.toInt(), fb.pixels[56]) // (6,5)
    }

    @Test
    fun `resize creates new framebuffer with different dimensions`() {
        val fb = Framebuffer(10, 10, IntArray(100))
        val resized = fb.resize(20, 20)
        assertEquals(20, resized.width)
        assertEquals(20, resized.height)
        assertEquals(400, resized.pixels.size)
    }

    @Test
    fun `updateFromRaw converts RGB888 correctly`() {
        val fb = Framebuffer(2, 1, IntArray(2))
        // 2 pixels in RGB888: red (FF0000) and green (00FF00) - stored as BGR in raw RFB
        val rawData = byteArrayOf(
            0x00, 0x00, 0xFF.toByte(), 0x00.toByte(), // pixel 1: B=0, G=0, R=255
            0x00, 0xFF.toByte(), 0x00, 0x00.toByte()   // pixel 2: B=0, G=255, R=0
        )
        fb.updateFromRaw(0, 0, 2, 1, rawData, 4)

        assertEquals(0xFFFF0000.toInt(), fb.pixels[0])
        assertEquals(0xFF00FF00.toInt(), fb.pixels[1])
    }

    @Test
    fun `updateFromRaw converts RGB565 correctly`() {
        val fb = Framebuffer(2, 1, IntArray(2))
        // RGB565: RRRRR GGGGGG BBBBB
        // Red:   11111 000000 00000 = 0xF800
        // Green: 00000 111111 00000 = 0x07E0
        val rawData = byteArrayOf(
            0x00, 0xF8.toByte(), // pixel 1: red (little-endian)
            0xE0.toByte(), 0x07  // pixel 2: green (little-endian)
        )
        fb.updateFromRaw(0, 0, 2, 1, rawData, 2)

        assertTrue("Pixel 0 should be reddish", fb.pixels[0] shr 16 > 200)
        assertTrue("Pixel 0 should have low green", (fb.pixels[0] shr 8 and 0xFF) < 50)
    }
}
