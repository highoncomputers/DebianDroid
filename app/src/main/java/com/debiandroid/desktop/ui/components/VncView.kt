package com.debiandroid.desktop.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalContext
import com.debiandroid.desktop.vnc.*
import kotlinx.coroutines.*

@Composable
fun VncView(
    connection: VncConnection,
    modifier: Modifier = Modifier
) {
    var framebuffer by remember { mutableStateOf<Framebuffer?>(null) }
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val scope = rememberCoroutineScope()
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Collect framebuffer updates
    LaunchedEffect(connection) {
        connection.framebufferUpdates.consumeAsFlow().collect { update ->
            val currentFb = framebuffer
            if (currentFb == null) {
                // Find the first raw/desktop-size rect to get dimensions
                for (rect in update.rectangles) {
                    if (rect.encoding == Encodings.DESKTOP_SIZE || (rect.w > 0 && rect.h > 0)) {
                        val fb = Framebuffer(rect.w, rect.h, IntArray(rect.w * rect.h))
                        // Request a full update
                        connection.requestUpdate(full = true)
                        framebuffer = fb
                        return@collect
                    }
                }
                return@collect
            }

            var fb = currentFb
            for (rect in update.rectangles) {
                when (rect.encoding) {
                    Encodings.RAW -> {
                        val rawData = rect.pixelData ?: continue
                        val bytes = ByteArray(rawData.size)
                        for (i in rawData.indices) bytes[i] = rawData[i].toByte()
                        fb = Framebuffer(fb.width, fb.height, fb.pixels.copyOf())
                        fb.updateFromRaw(rect.x, rect.y, rect.w, rect.h, bytes, 4)
                    }
                    Encodings.COPY_RECT -> {
                        fb = Framebuffer(fb.width, fb.height, fb.pixels.copyOf())
                        fb.copyRect(rect.srcX, rect.srcY, rect.x, rect.y, rect.w, rect.h)
                    }
                    Encodings.TIGHT -> {
                        val data = rect.pixelData ?: continue
                        fb = Framebuffer(fb.width, fb.height, fb.pixels.copyOf())
                        val rawPixels = data
                        fb.updateRegion(rect.x, rect.y, rect.w, rect.h, rawPixels)
                    }
                    Encodings.DESKTOP_SIZE -> {
                        fb = fb.resize(rect.w, rect.h)
                    }
                }
            }

            framebuffer = fb
            bitmap = fb.pixels.toImageBitmap(fb.width, fb.height)
        }
    }

    // Touch-to-mouse mapping
    val pointerState = remember { mutableStateOf(PointerState()) }

    Canvas(
        modifier = modifier
            .pointerInput(connection) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.25f, 4f)
                    offset = Offset.Zero
                }
            }
            .pointerInput(connection) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull() ?: continue
                        if (pointer.pressed) {
                            val fb = framebuffer ?: continue
                            val vncX = (pointer.position.x / scale).toInt().coerceIn(0, fb.width - 1)
                            val vncY = (pointer.position.y / scale).toInt().coerceIn(0, fb.height - 1)
                            connection.sendMouseMove(vncX, vncY)
                        }
                        pointer.consume()
                    }
                }
            }
            .pointerInput(connection) {
                detectTapGestures(
                    onTap = {
                        val fb = framebuffer ?: return@detectTapGestures
                        val vncX = (it.x / scale).toInt().coerceIn(0, fb.width - 1)
                        val vncY = (it.y / scale).toInt().coerceIn(0, fb.height - 1)
                        connection.sendLeftClick(vncX, vncY)
                    },
                    onLongPress = {
                        val fb = framebuffer ?: return@detectLongPress
                        val vncX = (it.x / scale).toInt().coerceIn(0, fb.width - 1)
                        val vncY = (it.y / scale).toInt().coerceIn(0, fb.height - 1)
                        connection.sendRightClick(vncX, vncY)
                    }
                )
            }
    ) {
        bitmap?.let {
            val scaledWidth = size.width
            val scaledHeight = size.height
            drawImage(
                image = it,
                dstSize = androidx.compose.ui.geometry.Size(scaledWidth, scaledHeight)
            )
        }
    }
}

private data class PointerState(
    var x: Float = 0f,
    var y: Float = 0f,
    var pressed: Boolean = false,
    var button: Int = 0
)

private fun IntArray.toImageBitmap(width: Int, height: Int): ImageBitmap {
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bmp.setPixels(this, 0, width, 0, 0, width, height)
    return bmp.asImageBitmap()
}

private fun <T> kotlinx.coroutines.channels.ReceiveChannel<T>.consumeAsFlow() = kotlinx.coroutines.flow.callbackFlow {
    launch {
        for (item in this@consumeAsFlow) {
            send(item)
            if (!isActive) break
        }
    }
}
