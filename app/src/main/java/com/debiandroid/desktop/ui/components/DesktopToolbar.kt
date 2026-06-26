package com.debiandroid.desktop.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DesktopToolbar(
    isConnected: Boolean,
    onBack: () -> Unit,
    onKeyboard: () -> Unit,
    onFullscreen: () -> Unit,
    onSettings: () -> Unit,
    showKeyboard: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isConnected) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            " Connected ",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Row {
                IconButton(onClick = onKeyboard) {
                    Icon(
                        if (showKeyboard) Icons.Default.KeyboardHide else Icons.Default.Keyboard,
                        "Keyboard"
                    )
                }
                IconButton(onClick = onFullscreen) {
                    Icon(Icons.Default.Fullscreen, "Fullscreen")
                }
            }
        }
    }
}
