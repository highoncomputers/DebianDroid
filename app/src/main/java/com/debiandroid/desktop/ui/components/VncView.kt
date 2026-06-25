package com.debiandroid.desktop.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import com.debiandroid.desktop.vnc.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

@Composable
fun VncView(
    connection: VncConnection,
    modifier: Modifier = Modifier
) {
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var fbWidth by remember { mutableIntStateOf(1280) }
    var fbHeight by remember { mutableIntStateOf(720) }
    var scale by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(connection) {
        while (isActive) {
            try {
                val update = connection.framebufferUpdates.receive()
                for (rect in update.rectangles) {
                    when (rect.encoding) {
                        Encodings.DESKTOP_SIZE -> {
                            fbWidth = rect.w
                            fbHeight = rect.h
                        }
                    }
                }
                if (bitmap == null) {
                    val bmp = Bitmap.createBitmap(fbWidth, fbHeight, Bitmap.Config.ARGB_8888)
                    bitmap = bmp.asImageBitmap()
                }
            } catch (_: ClosedReceiveChannelException) {
                break
            } catch (_: Exception) {
                break
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(connection) {
                detectTapGestures(
                    onTap = { pos ->
                        val x = (pos.x / scale).toInt().coerceIn(0, fbWidth - 1)
                        val y = (pos.y / scale).toInt().coerceIn(0, fbHeight - 1)
                        connection.sendLeftClick(x, y)
                    },
                    onLongPress = { pos ->
                        val x = (pos.x / scale).toInt().coerceIn(0, fbWidth - 1)
                        val y = (pos.y / scale).toInt().coerceIn(0, fbHeight - 1)
                        connection.sendRightClick(x, y)
                    }
                )
            }
    ) {
        bitmap?.let {
            drawImage(
                image = it,
                dstSize = IntSize(size.width.toInt(), size.height.toInt())
            )
        }
    }
}
