package com.debiandroid.desktop.proot

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

data class ProotState(
    val isRunning: Boolean = false,
    val pid: Int = -1,
    val error: String? = null
)

class ProotRunner(private val context: Context) {
    private var process: Process? = null
    private var monitorJob: Job? = null
    private val _state = MutableStateFlow(ProotState())
    val state: StateFlow<ProotState> = _state
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val filesDir: File get() = context.filesDir
    private val rootfsDir: File get() = File(filesDir, "rootfs")
    private val prootBin: File get() = File(filesDir, "proot/proot")

    fun startVncServer(vncPassword: String, resolution: String = "1280x720") {
        val passFile = File(filesDir, "tmp/.vnc_pass")
        passFile.parentFile?.mkdirs()
        passFile.writeText(vncPassword)
        passFile.setReadable(true, true)

        val cmd = buildProotCommand(
            "/bin/bash", "-c", """
                set -e
                export HOME=/home/debian
                export USER=debian
                if ! pgrep -x Xvnc > /dev/null; then
                    mkdir -p /home/debian/.vnc
                    cat /tmp/.vnc_pass | vncpasswd -f > /home/debian/.vnc/passwd
                    rm -f /tmp/.vnc_pass
                    chmod 600 /home/debian/.vnc/passwd
                    cat > /home/debian/.vnc/xstartup << 'EOF'
#!/bin/bash
export DISPLAY=:1
export HOME=/home/debian
export USER=debian
unset SESSION_MANAGER
unset DBUS_SESSION_BUS_ADDRESS
startxfce4 &
EOF
                    chmod +x /home/debian/.vnc/xstartup
                    vncserver :1 -geometry $resolution -depth 24 -localhost -SecurityTypes VncAuth
                fi
            """.trimIndent()
        )
        startProcess(cmd)

        scope.launch {
            delay(5000)
            if (passFile.exists()) passFile.delete()
        }
    }

    fun startDesktop() {
        val cmd = buildProotCommand(
            "/bin/bash", "-c",
            "export DISPLAY=:1 && export HOME=/home/debian && " +
            "if ! pgrep -x Xvnc > /dev/null; then " +
            "vncserver :1 -geometry 1280x720 -depth 24 -localhost -SecurityTypes VncAuth; " +
            "fi"
        )
        startProcess(cmd)
    }

    fun executeShellCommand(command: String): Process? {
        val cmd = buildProotCommand("/bin/bash", "-c", command)
        return try {
            val pb = ProcessBuilder(cmd).directory(rootfsDir)
            pb.environment()["LD_LIBRARY_PATH"] = "${filesDir.absolutePath}/proot"
            val proc = pb.start()
            proc
        } catch (e: Exception) {
            null
        }
    }

    fun createShellProcess(): Process? {
        val cmd = buildProotCommand("/bin/bash", "--login")
        return try {
            val pb = ProcessBuilder(cmd).directory(rootfsDir)
            pb.environment()["LD_LIBRARY_PATH"] = "${filesDir.absolutePath}/proot"
            pb.environment()["HOME"] = "/home/debian"
            pb.environment()["USER"] = "debian"
            pb.environment()["TERM"] = "xterm-256color"
            val proc = pb.start()
            proc
        } catch (e: Exception) {
            null
        }
    }

    private fun buildProotCommand(vararg args: String): List<String> {
        val cmd = mutableListOf(
            prootBin.absolutePath,
            "-0",
            "-r", rootfsDir.absolutePath,
            "-w", "/",
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "-b", "${filesDir.absolutePath}/shared:/shared",
            "-b", "${filesDir.absolutePath}/tmp:/tmp"
        )
        cmd.addAll(args)
        return cmd
    }

    private fun startProcess(cmd: List<String>) {
        if (process?.isAlive == true) return
        try {
            val pb = ProcessBuilder(cmd)
            pb.environment()["LD_LIBRARY_PATH"] = "${filesDir.absolutePath}/proot"
            pb.environment()["DISPLAY"] = ":1"
            pb.environment()["HOME"] = "/home/debian"
            pb.environment()["USER"] = "debian"
            pb.environment()["PULSE_SERVER"] = "127.0.0.1"
            process = pb.start()
            val pid = try {
                (process!!::class.java.getMethod("pid").invoke(process) as Number).toInt()
            } catch (_: Exception) {
                -1
            }
            _state.value = ProotState(isRunning = true, pid = pid)
            monitorProcess()
        } catch (e: Exception) {
            _state.value = ProotState(isRunning = false, error = e.message)
        }
    }

    private fun monitorProcess() {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            process?.waitFor()
            _state.value = ProotState(isRunning = false, pid = -1)
        }
    }

    fun stop() {
        monitorJob?.cancel()
        process?.destroy()
        process?.waitFor()
        process = null
        _state.value = ProotState(isRunning = false)
    }

    fun isAlive(): Boolean = process?.isAlive == true

    fun cleanup() {
        stop()
        scope.cancel()
    }
}
