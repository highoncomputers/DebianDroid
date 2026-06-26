package com.debiandroid.desktop.proot

import android.content.Context
import android.os.StatFs
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.*
import java.util.zip.GZIPInputStream

enum class SetupPhase { EXTRACTING, CONFIGURING, COMPLETE, ERROR }

data class SetupProgress(
    val phase: SetupPhase = SetupPhase.EXTRACTING,
    val progress: Float = 0f,
    val error: String? = null
)

class RootfsManager(private val context: Context) {
    private val _progress = MutableStateFlow(SetupProgress())
    val progress: StateFlow<SetupProgress> = _progress

    private val filesDir get() = context.filesDir
    val rootfsDir get() = File(filesDir, "rootfs")
    val isSetupComplete get() = File(rootfsDir, "etc/debian_version").exists()

    suspend fun setup() = withContext(Dispatchers.IO) {
        doSetup { extractFromAssets() }
    }

    suspend fun setupFromFile(filePath: String) = withContext(Dispatchers.IO) {
        doSetup {
            val srcFile = File(filePath)
            if (!srcFile.exists()) throw FileNotFoundException("Rootfs file not found: $filePath")
            val localFile = File(filesDir, "custom-rootfs.tar.gz")
            srcFile.copyTo(localFile, overwrite = true)
            extractFromFile(localFile)
        }
    }

    private suspend fun doSetup(extractAction: suspend () -> Unit) {
        try {
            if (isSetupComplete) {
                _progress.value = SetupProgress(phase = SetupPhase.COMPLETE, progress = 1f)
                return
            }

            checkStorageSpace()

            rootfsDir.deleteRecursively()
            rootfsDir.mkdirs()

            extractAction()
            configure()

            _progress.value = SetupProgress(phase = SetupPhase.COMPLETE, progress = 1f)
        } catch (e: Exception) {
            _progress.value = SetupProgress(
                phase = SetupPhase.ERROR,
                error = e.message ?: "Unknown error"
            )
        }
    }

    private suspend fun extractFromAssets() {
        _progress.value = SetupProgress(phase = SetupPhase.EXTRACTING, progress = 0f)

        val assetFd = context.assets.openFd("rootfs.tar.gz")
        val totalCompressed = assetFd.length
        assetFd.close()

        context.assets.open("rootfs.tar.gz").use { assetStream ->
            CountingInputStream(assetStream).use { countingStream ->
                GZIPInputStream(countingStream).use { gzis ->
                    extractTarEntries(gzis) {
                        if (totalCompressed > 0) countingStream.bytesRead.toFloat() / totalCompressed else 0f
                    }
                }
            }
        }
    }

    private suspend fun extractFromFile(file: File) {
        _progress.value = SetupProgress(phase = SetupPhase.EXTRACTING, progress = 0f)
        val totalBytes = file.length()

        FileInputStream(file).use { fis ->
            CountingInputStream(fis).use { countingStream ->
                GZIPInputStream(countingStream).use { gzis ->
                    extractTarEntries(gzis) {
                        if (totalBytes > 0) countingStream.bytesRead.toFloat() / totalBytes else 0f
                    }
                }
            }
        }
    }

    private suspend fun extractTarEntries(input: InputStream, progressFn: () -> Float) {
        val tarInput = TarInputStream(input)
        val rootfsCanonical = rootfsDir.canonicalPath
        var entry = tarInput.nextEntry()
        while (entry != null) {
            val outputFile = File(rootfsDir, entry.name)
            val outputCanonical = outputFile.canonicalPath
            if (!outputCanonical.startsWith(rootfsCanonical + File.separator) && outputCanonical != rootfsCanonical) {
                throw SecurityException("Path traversal blocked: ${entry.name}")
            }
            if (entry.isDirectory) {
                outputFile.mkdirs()
            } else {
                outputFile.parentFile?.mkdirs()
                FileOutputStream(outputFile).use { out ->
                    tarInput.readAll(out)
                }
                outputFile.setExecutable(entry.isExecutable, false)
            }
            entry = tarInput.nextEntry()
            _progress.value = SetupProgress(phase = SetupPhase.EXTRACTING, progress = progressFn())
        }
    }

    private suspend fun configure() {
        _progress.value = SetupProgress(phase = SetupPhase.CONFIGURING, progress = 0f)
        File(filesDir, "shared").mkdirs()
        File(filesDir, "tmp").mkdirs()

        val resolv = File(rootfsDir, "etc/resolv.conf")
        resolv.parentFile?.mkdirs()
        resolv.writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")

        val passwd = File(rootfsDir, "etc/passwd")
        if (passwd.exists() && !passwd.readText().contains("debian:")) {
            passwd.appendText("debian:x:1000:1000:Debian User,,,:/home/debian:/bin/bash\n")
        }
        val shadow = File(rootfsDir, "etc/shadow")
        if (shadow.exists() && !shadow.readText().contains("debian:")) {
            shadow.appendText("debian:*:19000:0:99999:7:::\n")
        }

        File(rootfsDir, "home/debian/.vnc").mkdirs()
        File(rootfsDir, "home/debian").setReadable(true, false)

        _progress.value = SetupProgress(phase = SetupPhase.CONFIGURING, progress = 1f)
    }

    private fun checkStorageSpace() {
        val stat = StatFs(filesDir.absolutePath)
        val availableBytes = stat.availableBytes
        if (availableBytes < 2L * 1024 * 1024 * 1024) {
            throw IOException(
                "Insufficient storage: ${availableBytes / 1_000_000}MB free, ~2GB needed"
            )
        }
    }

    class TarInputStream(private val input: InputStream) {
        data class Entry(val name: String, val size: Long, val isDirectory: Boolean, val isExecutable: Boolean)

        private val buffer = ByteArray(512)
        private var remainingBytes: Long = 0

        fun nextEntry(): Entry? {
            skipPadding()
            try {
                readBlock(buffer)
            } catch (_: IOException) {
                return null
            }
            if (buffer.all { it == 0.toByte() }) return null

            val name = String(buffer, 0, 100).trimEnd('\u0000').trimEnd('/')
            if (name.isEmpty()) return null

            val sizeStr = String(buffer, 124, 12).trimEnd('\u0000')
            val size = sizeStr.toLongOrNull() ?: 0L
            val type = buffer[156].toInt()
            val modeStr = String(buffer, 100, 8).trimEnd('\u0000')
            val mode = modeStr.toIntOrNull(8) ?: 0

            remainingBytes = size
            return Entry(name, size, type == 53, mode and 64 != 0)
        }

        fun readAll(out: OutputStream) {
            val buf = ByteArray(8192)
            while (remainingBytes > 0) {
                val toRead = minOf(buf.size.toLong(), remainingBytes).toInt()
                val read = input.read(buf, 0, toRead)
                if (read == -1) {
                    remainingBytes = 0
                    break
                }
                out.write(buf, 0, read)
                remainingBytes -= read
            }
        }

        private fun readBlock(buffer: ByteArray) {
            var total = 0
            while (total < buffer.size) {
                val read = input.read(buffer, total, buffer.size - total)
                if (read == -1) throw IOException("Unexpected end of tar stream")
                total += read
            }
        }

        private fun skipPadding() {
            val skip = (512 - (remainingBytes % 512)) % 512
            if (skip > 0) {
                var toSkip = skip
                while (toSkip > 0) {
                    val skipped = input.skip(toSkip)
                    if (skipped <= 0) break
                    toSkip -= skipped
                }
            }
        }
    }

    private class CountingInputStream(private val input: InputStream) : InputStream() {
        var bytesRead: Long = 0
            private set

        override fun read(): Int {
            val b = input.read()
            if (b != -1) bytesRead++
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val read = input.read(b, off, len)
            if (read > 0) bytesRead += read
            return read
        }

        override fun close() = input.close()
    }
}
