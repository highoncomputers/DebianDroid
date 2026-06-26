package com.debiandroid.desktop.vnc

import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlin.math.min

data class FramebufferUpdate(
    val rectangles: List<RectangleData>
)

data class RectangleData(
    val x: Int, val y: Int, val w: Int, val h: Int,
    val encoding: Int,
    val pixelData: IntArray? = null,
    val srcX: Int = 0,
    val srcY: Int = 0
)

class RfbProtocol(
    private val socket: Socket,
    private val password: String
) {
    private val input: InputStream = socket.getInputStream()
    private val output: OutputStream = socket.getOutputStream()
    private var bpp: Int = 4
    var fbWidth: Int = 0
        private set
    var fbHeight: Int = 0
        private set

    fun getBpp(): Int = bpp

    suspend fun connect() = withContext(Dispatchers.IO) {
        doHandshake()
        doSecurity()
        doClientInit()
        doServerInit()
    }

    private fun readFully(len: Int): ByteArray {
        val buf = ByteArray(len)
        var total = 0
        while (total < len) {
            val read = input.read(buf, total, len - total)
            if (read == -1) throw VncProtocolException("Connection closed during read")
            total += read
        }
        return buf
    }

    private fun write(data: ByteArray) {
        output.write(data)
        output.flush()
    }

    private fun doHandshake() {
        val protocol = String(readFully(12))
        if (protocol.startsWith("RFB 003.")) {
            write("RFB 003.008\n".toByteArray())
        } else if (protocol.startsWith("RFB 003.008")) {
            // already matching
        } else {
            throw VncProtocolException("Unsupported protocol: $protocol")
        }
    }

    private fun doSecurity() {
        val numSecTypes = readFully(1)[0].toInt() and 0xFF
        if (numSecTypes == 0) {
            val reasonLen = readFully(4)
            val reason = String(readFully(ByteBuffer.wrap(reasonLen).order(ByteOrder.BIG_ENDIAN).int))
            throw VncProtocolException("Server refused connection: $reason")
        }
        val secTypes = readFully(numSecTypes)
        if (secTypes.contains(1.toByte())) {
            // VNC authentication
            write(byteArrayOf(1))
            doVncAuth()
        } else if (secTypes.contains(2.toByte())) {
            // None
            write(byteArrayOf(2))
            readFully(4) // security result
        } else {
            throw VncProtocolException("No supported security type")
        }
    }

    private fun doVncAuth() {
        val challenge = readFully(16)
        val key = ByteArray(8)
        val passBytes = password.toByteArray()
        for (i in 0 until 8) {
            key[i] = if (i < passBytes.size) passBytes[i] else 0
        }
        // Reverse bits in each key byte (VNC authentication quirk)
        for (i in key.indices) {
            key[i] = reverseBits(key[i])
        }
        val response = desEncrypt(challenge, key)
        write(response)
        val result = readFully(4)
        val resultVal = ByteBuffer.wrap(result).order(ByteOrder.BIG_ENDIAN).int
        if (resultVal != 0) {
            throw VncProtocolException("VNC authentication failed")
        }
    }

    private fun reverseBits(b: Byte): Byte {
        var x = b.toInt() and 0xFF
        x = (x and 0x55 shl 1) or (x and 0xAA shr 1)
        x = (x and 0x33 shl 2) or (x and 0xCC shr 2)
        x = (x shl 4) or (x shr 4)
        return (x and 0xFF).toByte()
    }

    private fun desEncrypt(data: ByteArray, key: ByteArray): ByteArray {
        // Simplified DES for VNC auth
        val cipher = javax.crypto.Cipher.getInstance("DES/ECB/NoPadding")
        val keySpec = javax.crypto.spec.SecretKeySpec(key, "DES")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, keySpec)
        return cipher.doFinal(data)
    }

    private fun doClientInit() {
        write(byteArrayOf(1)) // share desktop
    }

    private fun doServerInit() {
        fbWidth = ByteBuffer.wrap(readFully(2)).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
        fbHeight = ByteBuffer.wrap(readFully(2)).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
        
        val bppVal = readFully(1)[0].toInt() and 0xFF
        bpp = bppVal / 8
        readFully(2) // depth
        readFully(1) // big-endian flag
        readFully(1) // true-color flag
        readFully(2) // red-max
        readFully(2) // green-max
        readFully(2) // blue-max
        readFully(1) // red-shift
        readFully(1) // green-shift
        readFully(1) // blue-shift
        readFully(3) // padding

        val nameLen = ByteBuffer.wrap(readFully(4)).order(ByteOrder.BIG_ENDIAN).int
        val name = String(readFully(nameLen))

        // Set pixel format and encodings
        setPixelFormat(32)
        setEncodings()
    }

    private fun setPixelFormat(depth: Int) {
        val buf = ByteBuffer.allocate(20).order(ByteOrder.BIG_ENDIAN)
        buf.put(0x00) // message type
        buf.put(0x00) // padding
        buf.put(0x00) // padding
        buf.put(0x00) // padding
        buf.putShort(depth.toShort()) // bits-per-pixel
        buf.putShort(depth.toShort()) // depth
        buf.put(0x00) // big-endian
        buf.put(0x01) // true-color
        if (depth == 32) {
            buf.putShort(0xFF.toShort()) // red-max
            buf.putShort(0xFF.toShort()) // green-max
            buf.putShort(0xFF.toShort()) // blue-max
            buf.put(16) // red-shift
            buf.put(8)  // green-shift
            buf.put(0)  // blue-shift
        } else {
            buf.putShort(0x1F.toShort())
            buf.putShort(0x3F.toShort())
            buf.putShort(0x1F.toShort())
            buf.put(11)
            buf.put(5)
            buf.put(0)
        }
        buf.put(0x00) // padding
        write(buf.array())
    }

    private fun setEncodings() {
        val encodings = listOf(
            Encodings.TIGHT,
            Encodings.COPY_RECT,
            Encodings.RAW,
            Encodings.DESKTOP_SIZE
        )
        val buf = ByteBuffer.allocate(4 + encodings.size * 4).order(ByteOrder.BIG_ENDIAN)
        buf.put(0x02.toByte()) // set-encodings
        buf.put(0x00) // padding
        buf.putShort(encodings.size.toShort())
        for (enc in encodings) {
            buf.putInt(enc)
        }
        write(buf.array())
    }

    suspend fun sendFramebufferUpdateRequest(incremental: Boolean, x: Int, y: Int, w: Int, h: Int) = withContext(Dispatchers.IO) {
        val buf = ByteBuffer.allocate(10).order(ByteOrder.BIG_ENDIAN)
        buf.put(0x03.toByte()) // framebuffer-update-request
        buf.put(if (incremental) 1 else 0)
        buf.putShort(x.toShort())
        buf.putShort(y.toShort())
        buf.putShort(w.toShort())
        buf.putShort(h.toShort())
        write(buf.array())
    }

    suspend fun sendKeyEvent(pressed: Boolean, keysym: Int) = withContext(Dispatchers.IO) {
        val buf = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
        buf.put(0x04.toByte()) // key-event
        buf.put(if (pressed) 1 else 0)
        buf.putShort(0) // padding
        buf.putInt(keysym)
        write(buf.array())
    }

    suspend fun sendPointerEvent(pressed: Boolean, x: Int, y: Int, buttonMask: Int) = withContext(Dispatchers.IO) {
        val mask = if (pressed) buttonMask else 0
        val buf = ByteBuffer.allocate(6).order(ByteOrder.BIG_ENDIAN)
        buf.put(0x05.toByte()) // pointer-event
        buf.put(mask.toByte())
        buf.putShort(x.toShort())
        buf.putShort(y.toShort())
        write(buf.array())
    }

    suspend fun readFramebufferUpdate(): FramebufferUpdate = withContext(Dispatchers.IO) {
        val msgType = readFully(1)[0].toInt() and 0xFF
        if (msgType != 0) {
            throw VncProtocolException("Expected framebuffer update, got type: $msgType")
        }
        readFully(1) // padding
        val numRects = ByteBuffer.wrap(readFully(2)).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
        val rects = mutableListOf<RectangleData>()

        for (i in 0 until numRects) {
            val x = ByteBuffer.wrap(readFully(2)).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
            val y = ByteBuffer.wrap(readFully(2)).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
            val w = ByteBuffer.wrap(readFully(2)).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
            val h = ByteBuffer.wrap(readFully(2)).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
            val encoding = ByteBuffer.wrap(readFully(4)).order(ByteOrder.BIG_ENDIAN).int

            when (encoding) {
                Encodings.RAW -> {
                    val rawSize = w.toLong() * h * bpp
                    if (rawSize > Int.MAX_VALUE) throw VncProtocolException("RAW rect too large: ${rawSize}bytes")
                    val rawData = readFully(rawSize.toInt())
                    rects.add(RectangleData(x, y, w, h, Encodings.RAW, pixelData = rawData.toIntArray()))
                }
                Encodings.COPY_RECT -> {
                    val srcX = ByteBuffer.wrap(readFully(2)).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
                    val srcY = ByteBuffer.wrap(readFully(2)).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
                    rects.add(RectangleData(x, y, w, h, Encodings.COPY_RECT, srcX = srcX, srcY = srcY))
                }
                Encodings.TIGHT -> {
                    val tightData = readTightData(w, h)
                    rects.add(RectangleData(x, y, w, h, Encodings.TIGHT, pixelData = tightData))
                }
                Encodings.DESKTOP_SIZE -> {
                    rects.add(RectangleData(x, y, w, h, Encodings.DESKTOP_SIZE))
                }
                else -> {
                    rects.add(RectangleData(x, y, w, h, encoding))
                }
            }
        }
        FramebufferUpdate(rects)
    }

    private fun readTightData(width: Int, height: Int): IntArray {
        val pixelCount = width * height
        val pixels = IntArray(pixelCount)
        var pixelOffset = 0

        while (pixelOffset < pixelCount) {
            val control = readFully(1)[0].toInt() and 0xFF
            val subEncoding = (control shr 4) and 0x0F
            val resetStream = (control and 0x0F) > 0

            when (subEncoding) {
                0 -> { // Basic zlib
                    val rawLen = readCompactSize()
                    val compressed = readFully(rawLen)
                    val decompLen = width.toLong() * height * 3
                    if (decompLen > Int.MAX_VALUE) throw VncProtocolException("Tight rect too large: ${decompLen}bytes")
                    val decompressed = ByteArray(decompLen.toInt())
                    val inflater = java.util.zip.Inflater()
                    inflater.setInput(compressed)
                    val decoded = inflater.inflate(decompressed)
                    inflater.end()

                    for (i in 0 until minOf(decoded / 3, pixelCount - pixelOffset)) {
                        val off = i * 3
                        val r = decompressed[off].toInt() and 0xFF
                        val g = decompressed[off + 1].toInt() and 0xFF
                        val b = decompressed[off + 2].toInt() and 0xFF
                        pixels[pixelOffset++] = (0xFF000000).toInt() or (r shl 16) or (g shl 8) or b
                    }
                }
                1 -> { // Fill
                    val r = readFully(1)[0].toInt() and 0xFF
                    val g = readFully(1)[0].toInt() and 0xFF
                    val b = readFully(1)[0].toInt() and 0xFF
                    val color = (0xFF000000).toInt() or (r shl 16) or (g shl 8) or b
                    val remaining = pixelCount - pixelOffset
                    pixels.fill(color, pixelOffset, pixelOffset + remaining)
                    pixelOffset = pixelCount
                }
            }
        }
        return pixels
    }

    private fun readCompactSize(): Int {
        var result = 0
        while (true) {
            val b = readFully(1)[0].toInt() and 0xFF
            result += b and 0x7F
            if (b and 0x80 == 0) break
            result = result shl 7
        }
        return result
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            socket.close()
        } catch (_: Exception) {}
    }

    private fun ByteArray.toIntArray(): IntArray {
        val result = IntArray(this.size)
        for (i in this.indices) {
            result[i] = this[i].toInt() and 0xFF
        }
        return result
    }
}

class VncProtocolException(message: String) : Exception(message)
