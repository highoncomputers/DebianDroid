package com.debiandroid.desktop.vnc

import org.junit.Assert.*
import org.junit.Test

class EncodingsTest {

    @Test
    fun `decodeTight fill encoding`() {
        // Fill subencoding: control byte 0x10 (subencoding=1, no reset)
        val data = byteArrayOf(
            0x10, // control: subencoding=1 (fill)
            0xFF.toByte(), // R
            0x00, // G
            0x00  // B
        )
        val pixels = Encodings.decodeTight(data, 4, 1)
        assertEquals(4, pixels.size)
        for (pixel in pixels) {
            assertEquals(0xFFFF0000.toInt(), pixel)
        }
    }

    @Test
    fun `decodeTight basic zlib encoding`() {
        // This tests zlib-compressed tight data
        // Build a minimal valid zlib + RGB triplet
        val uncompressed = byteArrayOf(
            0xFF.toByte(), 0x00, 0x00, // red
            0x00, 0xFF.toByte(), 0x00  // green
        )

        val deflater = java.util.zip.Deflater()
        deflater.setInput(uncompressed)
        deflater.finish()
        val compressed = java.io.ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!deflater.finished()) {
            val count = deflater.deflate(buf)
            compressed.write(buf, 0, count)
        }
        deflater.end()
        val compressedBytes = compressed.toByteArray()

        // Build tight data: control + compact size + compressed bytes
        val tightData = java.io.ByteArrayOutputStream()
        tightData.write(0x00) // control: subencoding=0 (basic), no reset
        // Write compact size
        var size = uncompressed.size / 3 * 3 // round to RGB triplets
        var s = size
        val sizeBytes = java.io.ByteArrayOutputStream()
        while (s >= 128) {
            sizeBytes.write((s and 0x7F) or 0x80)
            s = s shr 7
        }
        sizeBytes.write(s)
        tightData.write(sizeBytes.toByteArray())
        tightData.write(compressedBytes)

        val pixels = Encodings.decodeTight(tightData.toByteArray(), 2, 1)
        assertEquals(2, pixels.size)
        assertEquals(0xFFFF0000.toInt(), pixels[0])
        assertEquals(0xFF00FF00.toInt(), pixels[1])
    }

    @Test
    fun `encodings constants are correct`() {
        assertEquals(0, Encodings.RAW)
        assertEquals(1, Encodings.COPY_RECT)
        assertEquals(7, Encodings.TIGHT)
        assertEquals(-223, Encodings.DESKTOP_SIZE)
    }
}
