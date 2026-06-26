package com.debiandroid.desktop.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.debiandroid.desktop.permission.PermissionManager
import com.debiandroid.desktop.proot.RootfsManager
import com.debiandroid.desktop.proot.SetupPhase
import com.debiandroid.desktop.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SetupScreen(
    rootfsManager: RootfsManager,
    onComplete: () -> Unit,
    onError: (String) -> Unit
) {
    val progress by rootfsManager.progress.collectAsState()
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) {
        if (rootfsManager.isSetupComplete) {
            onComplete()
        }
    }

    LaunchedEffect(progress.phase) {
        if (progress.phase == SetupPhase.COMPLETE) {
            delay(1000)
            onComplete()
        }
        if (progress.phase == SetupPhase.ERROR) {
            onError(progress.error ?: "Setup failed")
        }
    }

    LaunchedEffect(Unit) {
        if (!rootfsManager.isSetupComplete && PermissionManager.areAllGranted(context)) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { rootfsManager.setup() }
                } catch (_: Exception) { }
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = when (progress.phase) {
                    SetupPhase.EXTRACTING -> Icons.Default.Unarchive
                    SetupPhase.CONFIGURING -> Icons.Default.Tune
                    SetupPhase.COMPLETE -> Icons.Default.CheckCircle
                    SetupPhase.ERROR -> Icons.Default.Error
                },
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = when (progress.phase) {
                    SetupPhase.ERROR -> StatusError
                    SetupPhase.COMPLETE -> StatusRunning
                    else -> MaterialTheme.colorScheme.primary
                }
            )

            Text(
                text = "Setting Up DebianDroid",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = when (progress.phase) {
                    SetupPhase.EXTRACTING -> "Extracting filesystem..."
                    SetupPhase.CONFIGURING -> "Configuring desktop environment..."
                    SetupPhase.COMPLETE -> "Setup complete!"
                    SetupPhase.ERROR -> "Setup failed"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (progress.phase == SetupPhase.ERROR && progress.error != null) {
                Text(
                    text = progress.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            if (progress.phase == SetupPhase.EXTRACTING) {
                LinearProgressIndicator(
                    progress = { progress.progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (progress.phase == SetupPhase.EXTRACTING) {
                Text(
                    "${(progress.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (!PermissionManager.areAllGranted(context)) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Notifications permission required",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Grant notification permission in Settings to see setup progress.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            if (progress.phase == SetupPhase.ERROR) {
                Button(onClick = {
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { rootfsManager.setup() }
                        } catch (_: Exception) { }
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = StatusError)) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Retry")
                }
            }
        }
    }
}
