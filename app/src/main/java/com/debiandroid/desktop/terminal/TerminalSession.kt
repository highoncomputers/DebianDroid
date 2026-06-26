package com.debiandroid.desktop.terminal

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.*

data class TerminalLine(
    val text: String,
    val isCommand: Boolean = false
)

data class TerminalState(
    val lines: List<TerminalLine> = listOf(TerminalLine("\$ ")),
    val cursorVisible: Boolean = true,
    val isRunning: Boolean = false
)

class TerminalSession {
    private var process: Process? = null
    private var readerJob: Job? = null
    private var writer: OutputStream? = null
    private val _state = MutableStateFlow(TerminalState())
    val state: StateFlow<TerminalState> = _state
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var pendingLine = StringBuilder()
    private var ansiBuffer = StringBuilder()

    fun start(command: List<String>, env: Map<String, String> = emptyMap()) {
        stop()
        try {
            val pb = ProcessBuilder(command)
            pb.environment().putAll(env)
            pb.redirectErrorStream(true)
            process = pb.start()
            writer = process!!.outputStream

            _state.value = TerminalState(isRunning = true)

            readerJob = scope.launch {
                try {
                    val reader = BufferedReader(InputStreamReader(process!!.inputStream))
                    val cbuf = CharArray(4096)
                    while (isActive) {
                        val count = reader.read(cbuf, 0, cbuf.size)
                        if (count == -1) break
                        for (i in 0 until count) {
                            processChar(cbuf[i])
                        }
                    }
                } catch (_: IOException) {
                } finally {
                    _state.value = _state.value.copy(isRunning = false)
                }
            }
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                lines = listOf(TerminalLine("Failed to start: ${e.message}")),
                isRunning = false
            )
        }
    }

    fun startWithProcess(process: Process) {
        stop()
        this.process = process
        writer = process.outputStream
        _state.value = _state.value.copy(isRunning = true)
        readerJob = scope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val cbuf = CharArray(4096)
                while (isActive) {
                    val count = reader.read(cbuf, 0, cbuf.size)
                    if (count == -1) break
                    for (i in 0 until count) {
                        processChar(cbuf[i])
                    }
                }
            } catch (_: IOException) {
            } finally {
                _state.value = _state.value.copy(isRunning = false)
            }
        }
    }

    private fun processChar(c: Char) {
        if (c == '\u001b') {
            ansiBuffer = StringBuilder().append(c)
            return
        }
        if (ansiBuffer.isNotEmpty()) {
            ansiBuffer.append(c)
            if (c == '~' || (c in 'A'..'Z') || (c in 'a'..'z')) {
                handleAnsiSequence(ansiBuffer.toString())
                ansiBuffer = StringBuilder()
            }
            return
        }
        when (c) {
            '\n' -> commitLine()
            '\r' -> {}
            '\b', '\u007f' -> {
                if (pendingLine.isNotEmpty()) {
                    pendingLine.deleteCharAt(pendingLine.length - 1)
                }
            }
            else -> {
                if (c.code >= 0x20) {
                    pendingLine.append(c)
                }
            }
        }
    }

    private fun handleAnsiSequence(seq: String) {
        when {
            seq == "\u001b[2J" || seq == "\u001b[H\u001b[2J" || seq == "\u001bc" -> {
                _state.value = _state.value.copy(lines = emptyList())
            }
            seq == "\u001b[A" -> {} // cursor up - ignore
            seq == "\u001b[B" -> {} // cursor down - ignore
            seq == "\u001b[C" -> {} // cursor forward - ignore
            seq == "\u001b[D" -> {} // cursor back - ignore
            seq.startsWith("\u001b[") && seq.endsWith("K") -> {} // erase line - ignore
            seq.startsWith("\u001b[") && seq.endsWith("m") -> {} // SGR color - ignore
            else -> {} // unknown escape - ignore
        }
    }

    private fun commitLine() {
        val line = pendingLine.toString()
        pendingLine = StringBuilder()
        val current = _state.value.lines.toMutableList()
        val isCmd = line.startsWith("\$ ")
        current.add(TerminalLine(if (isCmd) line else line, isCmd))
        current.add(TerminalLine("\$ "))
        _state.value = _state.value.copy(lines = current.takeLast(500))
    }

    fun writeInput(text: String) {
        try {
            writer?.write(text.toByteArray())
            writer?.flush()
        } catch (_: IOException) {}
    }

    fun sendEnter() = writeInput("\n")
    fun sendBackspace() = writeInput("\b")
    fun sendTab() = writeInput("\t")
    fun sendCtrlC() = writeInput("\u0003")
    fun sendCtrlD() = writeInput("\u0004")

    fun sendCommand(cmd: String) {
        writeInput("$cmd\n")
    }

    fun stop() {
        readerJob?.cancel()
        process?.destroy()
        process = null
        writer = null
        _state.value = TerminalState()
    }
}
