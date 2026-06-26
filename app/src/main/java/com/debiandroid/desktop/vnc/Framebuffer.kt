package com.debiandroid.desktop.vnc

data class Framebuffer(
    val width: Int,
    val height: Int,
    val pixels: IntArray
) {
    init {
        require(pixels.size == width * height) { "pixels array size mismatch" }
    }

    fun updateRegion(x: Int, y: Int, w: Int, h: Int, data: IntArray) {
        for (row in 0 until h) {
            val srcOffset = row * w
            val destOffset = (y + row) * width + x
            data.copyInto(pixels, destOffset, srcOffset, srcOffset + w)
        }
    }

    fun copyRect(srcX: Int, srcY: Int, destX: Int, destY: Int, w: Int, h: Int) {
        val srcRow = srcY * width + srcX
        val destRow = destY * width + destX
        if (destY < srcY) {
            for (row in 0 until h) {
                val srcOff = srcRow + row * width
                val dstOff = destRow + row * width
                pixels.copyInto(pixels, dstOff, srcOff, srcOff + w)
            }
        } else {
            for (row in h - 1 downTo 0) {
                val srcOff = srcRow + row * width
                val dstOff = destRow + row * width
                pixels.copyInto(pixels, dstOff, srcOff, srcOff + w)
            }
        }
    }

    fun resize(newWidth: Int, newHeight: Int): Framebuffer {
        val newPixels = IntArray(newWidth * newHeight)
        for (row in 0 until minOf(height, newHeight)) {
            val copyLen = minOf(width, newWidth)
            pixels.copyInto(newPixels, row * newWidth, row * width, row * width + copyLen)
        }
        return Framebuffer(newWidth, newHeight, newPixels)
    }

    fun updateFromRaw(x: Int, y: Int, w: Int, h: Int, rawData: ByteArray, bpp: Int) {
        val data = IntArray(w * h)
        when (bpp) {
            4 -> { // RGB888 (32-bit)
                for (i in 0 until w * h) {
                    val offset = i * 4
                    val r = rawData[offset + 2].toInt() and 0xFF
                    val g = rawData[offset + 1].toInt() and 0xFF
                    val b = rawData[offset].toInt() and 0xFF
                    data[i] = (0xFF000000).toInt() or (r shl 16) or (g shl 8) or b
                }
            }
            2 -> { // RGB565 (16-bit)
                for (i in 0 until w * h) {
                    val offset = i * 2
                    val pixel = ((rawData[offset + 1].toInt() and 0xFF) shl 8) or (rawData[offset].toInt() and 0xFF)
                    val r = (pixel shr 11) and 0x1F
                    val g = (pixel shr 5) and 0x3F
                    val b = pixel and 0x1F
                    data[i] = (0xFF000000).toInt() or (r shl 19) or (g shl 10) or (b shl 3)
                }
            }
        }
        updateRegion(x, y, w, h, data)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Framebuffer) return false
        if (width != other.width || height != other.height) return false
        return pixels.contentEquals(other.pixels)
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + pixels.contentHashCode()
        return result
    }
}
