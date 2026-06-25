package com.debiandroid.desktop.proot

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

enum class SetupPhase { DOWNLOADING, EXTRACTING, CONFIGURING, COMPLETE, ERROR }

data class SetupProgress(
    val phase: SetupPhase = SetupPhase.DOWNLOADING,
    val progress: Float = 0f,
    val speed: String = "",
    val eta: String = "",
    val error: String? = null
)

class RootfsManager(private val context: Context) {
    private val _progress = MutableStateFlow(SetupProgress())
    val progress: StateFlow<SetupProgress> = _progress

    private val filesDir get() = context.filesDir
    val rootfsDir get() = File(filesDir, "rootfs")
    val isSetupComplete get() = File(rootfsDir, "etc/debian_version").exists()

    companion object {
        const val ROOTFS_URL = "https://github.com/debiandroid/rootfs/releases/latest/download/debian-trixie-arm64.tar.gz"
        const val CHECKSUM_URL = "https://github.com/debiandroid/rootfs/releases/latest/download/debian-trixie-arm64.tar.gz.sha256"
    }

    suspend fun setup() = withContext(Dispatchers.IO) {
        try {
            if (isSetupComplete) {
                _progress.value = SetupProgress(phase = SetupPhase.COMPLETE, progress = 1f)
                return@withContext
            }

            rootfsDir.mkdirs()
            val tarFile = File(filesDir, "rootfs.tar.gz")

            download(tarFile)
            extract(tarFile)
            configure()
            tarFile.delete()

            _progress.value = SetupProgress(phase = SetupPhase.COMPLETE, progress = 1f)
        } catch (e: Exception) {
            _progress.value = SetupProgress(
                phase = SetupPhase.ERROR,
                error = e.message ?: "Unknown error"
            )
            throw e
        }
    }

    private suspend fun download(file: File) {
        _progress.value = SetupProgress(phase = SetupPhase.DOWNLOADING, progress = 0f)
        val url = URL(ROOTFS_URL)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 30000
        conn.readTimeout = 30000
        conn.connect()

        val totalSize = conn.contentLengthLong
        val input = BufferedInputStream(conn.inputStream)
        val output = FileOutputStream(file)
        val buffer = ByteArray(8192)
        var downloaded = 0L
        var lastUpdate = System.currentTimeMillis()
        var lastBytes = 0L

        input.use { inp ->
            output.use { out ->
                while (true) {
                    val bytes = inp.read(buffer)
                    if (bytes == -1) break
                    out.write(buffer, 0, bytes)
                    downloaded += bytes

                    val now = System.currentTimeMillis()
                    if (now - lastUpdate > 500) {
                        val elapsed = (now - lastUpdate) / 1000f
                        val bytesSince = downloaded - lastBytes
                        val speed = if (elapsed > 0) (bytesSince / elapsed).toLong() else 0L
                        val progress = if (totalSize > 0) downloaded.toFloat() / totalSize else 0f
                        val remaining = if (speed > 0) (totalSize - downloaded) / speed else 0L

                        _progress.value = SetupProgress(
                            phase = SetupPhase.DOWNLOADING,
                            progress = progress,
                            speed = formatSpeed(speed),
                            eta = formatTime(remaining)
                        )
                        lastUpdate = now
                        lastBytes = downloaded
                    }
                }
            }
        }
        conn.disconnect()
    }

    private suspend fun extract(file: File) {
        _progress.value = SetupProgress(phase = SetupPhase.EXTRACTING, progress = 0f)
        val totalBytes = file.length()
        var extractedBytes = 0L

        FileInputStream(file).use { fis ->
            BufferedInputStream(fis).use { bis ->
                GZIPInputStream(bis).use { gzis ->
                    val tarInput = TarInputStream(gzis)
                    var entry = tarInput.nextEntry()
                    while (entry != null) {
                        val outputFile = File(rootfsDir, entry.name)
                        if (entry.isDirectory) {
                            outputFile.mkdirs()
                        } else {
                            outputFile.parentFile?.mkdirs()
                            FileOutputStream(outputFile).use { out ->
                                tarInput.readAll(out)
                            }
                            outputFile.setExecutable(entry.isExecutable, false)
                        }
                        extractedBytes += entry.size
                        entry = tarInput.nextEntry()
                        val progress = if (totalBytes > 0) extractedBytes.toFloat() / totalBytes else 0f
                        _progress.value = SetupProgress(phase = SetupPhase.EXTRACTING, progress = progress)
                    }
                }
            }
        }
    }

    private suspend fun configure() {
        _progress.value = SetupProgress(phase = SetupPhase.CONFIGURING, progress = 0f)
        // Create shared directory
        File(filesDir, "shared").mkdirs()

        // Create temporary directory
        File(filesDir, "tmp").mkdirs()

        // Ensure resolv.conf
        val resolv = File(rootfsDir, "etc/resolv.conf")
        resolv.parentFile?.mkdirs()
        resolv.writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")

        // Create debian user
        val passwd = File(rootfsDir, "etc/passwd")
        if (!passwd.readText().contains("debian:")) {
            passwd.appendText("debian:x:1000:1000:Debian User,,,:/home/debian:/bin/bash\n")
        }
        val shadow = File(rootfsDir, "etc/shadow")
        if (!shadow.readText().contains("debian:")) {
            shadow.appendText("debian:*:19000:0:99999:7:::\n")
        }

        // Create home directory
        File(rootfsDir, "home/debian/.vnc").mkdirs()

        // Set permissions
        File(rootfsDir, "home/debian").setReadable(true, false)

        _progress.value = SetupProgress(phase = SetupPhase.CONFIGURING, progress = 1f)
    }

    class TarInputStream(private val input: InputStream) {
        data class Entry(val name: String, val size: Long, val isDirectory: Boolean, val isExecutable: Boolean)

        private val buffer = ByteArray(512)
        private var currentEntry: Entry? = null
        private var remainingBytes: Long = 0

        fun nextEntry(): Entry? {
            skipPadding()
            if (readBlock(buffer) == -1) return null
            if (buffer.all { it == 0.toByte() }) return null

            val name = String(buffer, 0, 100).trimEnd('\u0000').trimEnd('/')
            if (name.isEmpty()) return null

            val sizeStr = String(buffer, 124, 12).trimEnd('\u0000')
            val size = sizeStr.toLongOrNull() ?: 0L
            val type = buffer[156].toInt()
            val modeStr = String(buffer, 100, 8).trimEnd('\u0000')
            val mode = modeStr.toIntOrNull(8) ?: 0

            remainingBytes = size
            currentEntry = Entry(name, size, type == 53, mode and 64 != 0)
            return currentEntry
        }

        fun readAll(out: OutputStream) {
            val buf = ByteArray(8192)
            while (remainingBytes > 0) {
                val toRead = minOf(buf.size.toLong(), remainingBytes).toInt()
                val read = input.read(buf, 0, toRead)
                if (read == -1) break
                out.write(buf, 0, read)
                remainingBytes -= read
            }
        }

        private fun readBlock(buffer: ByteArray): Int {
            return input.read(buffer)
        }

        private fun skipPadding() {
            val skip = (512 - (remainingBytes % 512)) % 512
            if (skip > 0) input.skip(skip)
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec > 1_000_000 -> "${bytesPerSec / 1_000_000} MB/s"
            bytesPerSec > 1_000 -> "${bytesPerSec / 1_000} KB/s"
            else -> "$bytesPerSec B/s"
        }
    }

    private fun formatTime(seconds: Long): String {
        return when {
            seconds > 3600 -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
            seconds > 60 -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
    }
}
