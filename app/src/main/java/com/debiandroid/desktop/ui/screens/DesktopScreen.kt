package com.debiandroid.desktop.ui.screens

import android.view.KeyEvent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import com.debiandroid.desktop.ui.components.VncView
import com.debiandroid.desktop.vnc.VncConnection
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesktopScreen(
    onBack: () -> Unit,
    vncPassword: String = "debian",
    resolution: String = "1280x720"
) {
    val scope = rememberCoroutineScope()
    var showToolbar by remember { mutableStateOf(true) }
    var showKeyboard by remember { mutableStateOf(false) }
    var isConnected by remember { mutableStateOf(false) }
    var connection by remember { mutableStateOf<VncConnection?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(vncPassword) {
        try {
            val conn = VncConnection(password = vncPassword)
            conn.connect()
            connection = conn
            isConnected = true
        } catch (e: Exception) {
            error = e.message
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            scope.launch { connection?.disconnect() }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (connection != null) {
            VncView(
                connection = connection!!,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
                        )
                    }
            )
        } else if (error != null) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                Text("Connection failed", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text(error ?: "", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                Button(onClick = onBack) { Text("Go Back") }
            }
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(color = Color.White)
                Text("Connecting to desktop...", color = Color.White)
            }
        }

        // Floating toolbar toggle area - tap top edge
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .pointerInput(Unit) { detectTapGestures { showToolbar = !showToolbar } }
        )

        // Toolbar
        AnimatedVisibility(
            visible = showToolbar,
            enter = slideInVertically { -it },
            exit = slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xCC1C1B1F),
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                    }
                    Text("Debian Desktop", color = Color.White, style = MaterialTheme.typography.titleSmall)
                    Row {
                        IconButton(onClick = { showKeyboard = !showKeyboard }) {
                            Icon(
                                if (showKeyboard) Icons.Default.KeyboardHide else Icons.Default.Keyboard,
                                "Keyboard",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = { connection?.sendLeftClick(640, 360) }) {
                            Icon(Icons.Default.TouchApp, "Tap", tint = Color.White)
                        }
                    }
                }
            }
        }

        if (showKeyboard) {
            VirtualKeyboardView(
                onKeyPress = { key -> connection?.sendText(key.toString()) },
                onBackspace = { connection?.sendText("\b") },
                onEnter = { connection?.sendText("\n") },
                onDismiss = { showKeyboard = false },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun VirtualKeyboardView(
    onKeyPress: (Char) -> Unit,
    onBackspace: () -> Unit,
    onEnter: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(((0xFF2D2D2D).toInt()).toInt()),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                KeyboardKey("Esc", onClick = { onKeyPress('\u001B') })
                KeyboardKey("Tab", onClick = { onKeyPress('\t') })
                KeyboardKey("Ctrl", onClick = { /* modifier state */ })
                KeyboardKey("Alt", onClick = { /* modifier state */ })
                KeyboardKey("Del", onClick = { })
            }
            val rows = listOf(
                "qwertyuiop",
                "asdfghjkl",
                "zxcvbnm"
            )
            for (row in rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (char in row) {
                        KeyboardKey(char.toString(), onClick = { onKeyPress(char) })
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                KeyboardKey("Space", onClick = { onKeyPress(' ') }, modifier = Modifier.weight(3f))
                KeyboardKey("Bksp", onClick = onBackspace)
                KeyboardKey("Enter", onClick = onEnter)
                KeyboardKey("Hide", onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun KeyboardKey(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(40.dp).padding(horizontal = 2.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(((0xFF555555).toInt()).toInt())),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}
