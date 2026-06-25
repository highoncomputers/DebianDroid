package com.debiandroid.desktop.vnc

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import java.net.Socket

class VncConnection(
    private val host: String = "127.0.0.1",
    private val port: Int = 5901,
    private val password: String
) {
    private var protocol: RfbProtocol? = null
    private var socket: Socket? = null
    private var job: Job? = null
    private val _scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val framebufferUpdates = Channel<FramebufferUpdate>(CONFLATED)

    var isConnected: Boolean = false
        private set

    suspend fun connect() {
        socket = Socket(host, port)
        protocol = RfbProtocol(socket!!, password)
        protocol!!.connect()
        isConnected = true

        job = _scope.launch {
            try {
                protocol!!.sendFramebufferUpdateRequest(false, 0, 0, 0, 0)
                while (isActive) {
                    val update = protocol!!.readFramebufferUpdate()
                    framebufferUpdates.send(update)
                    protocol!!.sendFramebufferUpdateRequest(true, 0, 0, 0, 0)
                }
            } catch (e: Exception) {
                isConnected = false
                framebufferUpdates.close(e)
            }
        }
    }

    suspend fun requestUpdate(full: Boolean = false) {
        protocol?.sendFramebufferUpdateRequest(!full, 0, 0, 0, 0)
    }

    fun sendKey(pressed: Boolean, keysym: Int) {
        protocol?.let { _scope.launch { it.sendKeyEvent(pressed, keysym) } }
    }

    fun sendPointer(pressed: Boolean, x: Int, y: Int, buttonMask: Int) {
        protocol?.let { _scope.launch { it.sendPointerEvent(pressed, x, y, buttonMask) } }
    }

    fun sendMouseMove(x: Int, y: Int) {
        sendPointer(false, x, y, 0)
    }

    fun sendLeftClick(x: Int, y: Int) {
        _scope.launch {
            protocol?.sendPointerEvent(true, x, y, 1)
            delay(50)
            protocol?.sendPointerEvent(false, x, y, 1)
        }
    }

    fun sendRightClick(x: Int, y: Int) {
        _scope.launch {
            protocol?.sendPointerEvent(true, x, y, 4)
            delay(50)
            protocol?.sendPointerEvent(false, x, y, 4)
        }
    }

    fun sendScroll(deltaY: Int, x: Int, y: Int) {
        val btn = if (deltaY < 0) 5 else 4
        _scope.launch {
            protocol?.sendPointerEvent(true, x, y, btn)
            delay(30)
            protocol?.sendPointerEvent(false, x, y, btn)
        }
    }

    fun sendText(text: String) {
        _scope.launch {
            for (char in text) {
                val keysym = charToKeysym(char)
                protocol?.sendKeyEvent(true, keysym)
                delay(20)
                protocol?.sendKeyEvent(false, keysym)
                delay(10)
            }
        }
    }

    suspend fun disconnect() {
        job?.cancel()
        job?.join()
        protocol?.disconnect()
        socket?.close()
        isConnected = false
        framebufferUpdates.close()
    }

    companion object {
        fun charToKeysym(c: Char): Int {
            return when (c) {
                '\n', '\r' -> 0xFF0D
                '\t' -> 0xFF09
                '\b' -> 0xFF08
                ' ' -> 0x0020
                in 'a'..'z' -> 0x0061 + (c - 'a')
                in 'A'..'Z' -> 0x0041 + (c - 'A')
                in '0'..'9' -> 0x0030 + (c - '0')
                else -> c.code
            }
        }
    }
}
