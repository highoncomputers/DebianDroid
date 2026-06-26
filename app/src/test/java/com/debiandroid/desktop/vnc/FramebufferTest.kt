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
        val data = IntArray(4) { 0xFFFF0000.toInt() }
        fb.updateRegion(2, 2, 2, 2, data)

        assertEquals(0xFFFF0000.toInt(), fb.pixels[22])
        assertEquals(0xFFFF0000.toInt(), fb.pixels[23])
        assertEquals(0xFFFF0000.toInt(), fb.pixels[32])
        assertEquals(0xFFFF0000.toInt(), fb.pixels[33])

        assertEquals(0, fb.pixels[0])
        assertEquals(0, fb.pixels[11])
    }

    @Test
    fun `copyRect copies region correctly`() {
        val pixels = IntArray(100)
        pixels[12] = 0xFF00FF00.toInt()
        pixels[13] = 0xFF0000FF.toInt()
        val fb = Framebuffer(10, 10, pixels)

        fb.copyRect(2, 1, 5, 5, 2, 1)

        assertEquals(0xFF00FF00.toInt(), fb.pixels[55])
        assertEquals(0xFF0000FF.toInt(), fb.pixels[56])
    }

    @Test
    fun `resize creates new framebuffer and copies old pixels`() {
        val pixels = IntArray(100)
        pixels[0] = 0xFFFF0000.toInt()
        pixels[99] = 0xFF00FF00.toInt()
        val fb = Framebuffer(10, 10, pixels)

        val resized = fb.resize(20, 20)

        assertEquals(20, resized.width)
        assertEquals(20, resized.height)
        assertEquals(400, resized.pixels.size)
        assertEquals(0xFFFF0000.toInt(), resized.pixels[0])
    }

    @Test
    fun `resize to smaller dimensions clips pixels`() {
        val pixels = IntArray(100) { it }
        val fb = Framebuffer(10, 10, pixels)

        val resized = fb.resize(5, 5)

        assertEquals(5, resized.width)
        assertEquals(5, resized.height)
        assertEquals(25, resized.pixels.size)
        assertEquals(0, resized.pixels[0])
    }

    @Test
    fun `equals and hashCode work correctly`() {
        val fb1 = Framebuffer(2, 2, intArrayOf(1, 2, 3, 4))
        val fb2 = Framebuffer(2, 2, intArrayOf(1, 2, 3, 4))
        val fb3 = Framebuffer(2, 2, intArrayOf(5, 6, 7, 8))

        assertEquals(fb1, fb2)
        assertEquals(fb1.hashCode(), fb2.hashCode())
        assertNotEquals(fb1, fb3)
    }
}
