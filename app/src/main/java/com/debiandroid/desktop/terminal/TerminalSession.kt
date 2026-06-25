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

    private val buffer = StringBuilder()
    private var currentLine = StringBuilder()

    fun start(command: List<String>, env: Map<String, String> = emptyMap()) {
        try {
            val pb = ProcessBuilder(command)
            pb.environment().putAll(env)
            pb.redirectErrorStream(true)
            process = pb.start()
            writer = process!!.outputStream

            _state.value = _state.value.copy(isRunning = true)

            readerJob = scope.launch {
                try {
                    val reader = BufferedReader(InputStreamReader(process!!.inputStream))
                    var char: Int
                    while (reader.read().also { char = it } != -1) {
                        processChar(char.toChar())
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

    private fun processChar(c: Char) {
        when (c) {
            '\n' -> {
                val line = currentLine.toString()
                currentLine = StringBuilder()
                val current = _state.value.lines.toMutableList()
                val isCmd = line.startsWith("\$ ")
                current.add(TerminalLine(if (isCmd) line else line, isCmd))
                current.add(TerminalLine("\$ "))
                _state.value = _state.value.copy(lines = current.takeLast(500))
            }
            '\r' -> {}
            '\b', 0x7F -> {
                if (currentLine.isNotEmpty()) {
                    currentLine.deleteCharAt(currentLine.length - 1)
                }
            }
            0x1B -> { /* ANSI escape sequences - simplified */ }
            else -> {
                if (c.code in 0x20..0x7E || c.code > 0xA0) {
                    currentLine.append(c)
                }
            }
        }
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
