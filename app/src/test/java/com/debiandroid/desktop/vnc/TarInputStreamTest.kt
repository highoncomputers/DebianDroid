package com.debiandroid.desktop.proot

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class TarInputStreamTest {

    @Test
    fun `empty stream returns null`() {
        val stream = ByteArrayInputStream(ByteArray(0))
        val tar = RootfsManager.TarInputStream(stream)
        assertNull(tar.nextEntry())
    }

    @Test
    fun `all-zero buffer returns null`() {
        val stream = ByteArrayInputStream(ByteArray(1024))
        val tar = RootfsManager.TarInputStream(stream)
        assertNull(tar.nextEntry())
    }

    @Test
    fun `parse a minimal tar header`() {
        val header = ByteArray(512)
        // Name (offset 0, len 100)
        val name = "test.txt".toByteArray()
        name.copyInto(header, 0, 0, name.size)
        // Size (offset 124, len 12) - octal "       12"
        val sizeStr = "%011o".format(12L).toByteArray()
        sizeStr.copyInto(header, 124, 0, sizeStr.size)
        // Type (offset 156) - '0' for regular file
        header[156] = '0'.code.toByte()
        // Mode (offset 100, len 8) - octal "0000644"
        val modeStr = "0000644".toByteArray()
        modeStr.copyInto(header, 100, 0, modeStr.size)

        val stream = ByteArrayInputStream(header)
        val tar = RootfsManager.TarInputStream(stream)
        val entry = tar.nextEntry()

        assertNotNull(entry)
        assertEquals("test.txt", entry!!.name)
        assertEquals(12L, entry.size)
        assertFalse(entry.isDirectory)
        assertFalse(entry.isExecutable)
    }

    @Test
    fun `readAll collects entry data`() {
        val data = "Hello World!".toByteArray()
        val header = ByteArray(512)
        val name = "hello.txt".toByteArray()
        name.copyInto(header, 0, 0, name.size)
        val sizeStr = "%011o".format(data.size.toLong()).toByteArray()
        sizeStr.copyInto(header, 124, 0, sizeStr.size)
        header[156] = '0'.code.toByte()
        val modeStr = "0000644".toByteArray()
        modeStr.copyInto(header, 100, 0, modeStr.size)

        val fullInput = header + data + ByteArray(512 - data.size % 512)
        val stream = ByteArrayInputStream(fullInput.toByteArray())
        val tar = RootfsManager.TarInputStream(stream)
        val entry = tar.nextEntry()

        assertNotNull(entry)
        assertEquals(11L, entry!!.size)

        val out = java.io.ByteArrayOutputStream()
        tar.readAll(out)
        assertEquals("Hello World!", out.toString())
    }
}
