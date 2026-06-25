package com.debiandroid.desktop.vnc

import java.util.zip.Inflater

object Encodings {
    const val RAW = 0
    const val COPY_RECT = 1
    const val TIGHT = 7
    const val DESKTOP_SIZE = -223

    fun decodeTight(data: ByteArray, width: Int, height: Int): IntArray {
        val pixels = IntArray(width * height)
        val inflater = Inflater()
        var dataOffset = 0
        var pixelOffset = 0

        while (pixelOffset < width * height && dataOffset < data.size) {
            val control = data[dataOffset++].toInt() and 0xFF
            val subEncoding = (control shr 4) and 0x0F

            when (subEncoding) {
                0 -> { // Basic (zlib compressed)
                    val resetStream = (control and 0x0F) > 0
                    if (resetStream) inflater.reset()
                    
                    val rawLen = readCompactSize(data, dataOffset)
                    dataOffset += compactSizeBytes(data, dataOffset)
                    
                    val compressed = data.copyOfRange(dataOffset, dataOffset + rawLen)
                    dataOffset += rawLen

                    val decompressed = ByteArray(width * height * 3)
                    inflater.setInput(compressed)
                    val decoded = inflater.inflate(decompressed)
                    
                    for (i in 0 until minOf(decoded / 3, width * height - pixelOffset)) {
                        val off = i * 3
                        val r = decompressed[off].toInt() and 0xFF
                        val g = decompressed[off + 1].toInt() and 0xFF
                        val b = decompressed[off + 2].toInt() and 0xFF
                        pixels[pixelOffset++] = 0xFF000000 or (r shl 16) or (g shl 8) or b
                    }
                }
                1 -> { // Fill (solid color)
                    val r = data[dataOffset++].toInt() and 0xFF
                    val g = data[dataOffset++].toInt() and 0xFF
                    val b = data[dataOffset++].toInt() and 0xFF
                    val color = 0xFF000000 or (r shl 16) or (g shl 8) or b
                    val count = minOf(width * height - pixelOffset, width * height)
                    pixels.fill(color, pixelOffset, pixelOffset + count)
                    pixelOffset += count
                }
                else -> { // Fallback: skip remaining data
                    break
                }
            }
        }
        inflater.end()
        return pixels
    }

    private fun readCompactSize(data: ByteArray, offset: Int): Int {
        var result = 0
        var pos = offset
        while (pos < data.size) {
            val b = data[pos++].toInt() and 0xFF
            result += b and 0x7F
            if (b and 0x80 == 0) break
            result = result shl 7
        }
        return result
    }

    private fun compactSizeBytes(data: ByteArray, offset: Int): Int {
        var count = 0
        var pos = offset
        while (pos < data.size) {
            count++
            if (data[pos++].toInt() and 0x80 == 0) break
        }
        return count
    }

    private fun minOf(a: Int, b: Int) = if (a < b) a else b
}
