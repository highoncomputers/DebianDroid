package com.debiandroid.desktop.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.debiandroid.desktop.terminal.TerminalSession
import com.debiandroid.desktop.terminal.TerminalLine
import com.debiandroid.desktop.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    onBack: () -> Unit,
    session: TerminalSession = remember { TerminalSession() }
) {
    var input by remember { mutableStateOf(TextFieldValue("")) }
    val scrollState = rememberScrollState()
    val terminalState by session.state.collectAsState()

    LaunchedEffect(Unit) {
        session.start(
            command = listOf("/system/bin/sh"),
            env = mapOf("TERM" to "xterm-256color")
        )
    }

    DisposableEffect(Unit) {
        onDispose { session.stop() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Terminal") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { session.sendCtrlC() }) {
                        Icon(Icons.Default.Cancel, "Ctrl+C")
                    }
                    IconButton(onClick = { session.stop(); /* restart logic */ }) {
                        Icon(Icons.Default.Refresh, "Restart")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(TerminalBackground)
        ) {
            // Terminal output area
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(8.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                for (line in terminalState.lines) {
                    Text(
                        text = line.text,
                        color = if (line.isCommand) TerminalGreen else TerminalText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            // Input area
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF252526),
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "\$ ",
                        color = TerminalGreen,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp
                    )
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(
                            color = TerminalText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        ),
                        cursorBrush = SolidColor(TerminalGreen),
                        singleLine = true
                    )
                    IconButton(onClick = {
                        session.sendCommand(input.text)
                        input = TextFieldValue("")
                    }) {
                        Icon(Icons.Default.Send, "Send", tint = TerminalGreen)
                    }
                }
            }
        }
    }
}
