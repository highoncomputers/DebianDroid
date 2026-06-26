package com.debiandroid.desktop.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.debiandroid.desktop.proot.RootfsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vncPassword: String = "debian",
    resolution: String = "1280x720",
    onPasswordChange: (String) -> Unit = {},
    onResolutionChange: (String) -> Unit = {},
    onReinstall: () -> Unit = {},
    rootfsManager: RootfsManager? = null,
    onPatchStatus: (String) -> Unit = {}
) {
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showPatchDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf(vncPassword) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val patchFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null && rootfsManager != null) {
            showPatchDialog = false
            scope.launch {
                try {
                    val tempFile = File(context.cacheDir, "patch-rootfs.tar.gz")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    onPatchStatus("Patching rootfs...")
                    withContext(Dispatchers.IO) {
                        rootfsManager.setupFromFile(tempFile.absolutePath)
                    }
                    onPatchStatus("Rootfs updated successfully!")
                } catch (e: Exception) {
                    onPatchStatus("Patch failed: ${e.message}")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Display section
            SectionHeader("Display")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ResolutionSelector(
                        current = resolution,
                        onSelect = onResolutionChange
                    )
                }
            }

            // VNC section
            SectionHeader("VNC Server")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("VNC Password", style = MaterialTheme.typography.titleSmall)
                            Text("Used to connect to the desktop", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(onClick = { passwordInput = vncPassword; showPasswordDialog = true }) {
                            Text("Change")
                        }
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("VNC Port", style = MaterialTheme.typography.titleSmall)
                            Text("5901", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Advanced section
            SectionHeader("Advanced")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Reinstall Debian", style = MaterialTheme.typography.titleSmall)
                            Text("Remove and re-extract rootfs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(
                            onClick = { showResetDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Reinstall")
                        }
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Update Rootfs", style = MaterialTheme.typography.titleSmall)
                            Text("Select a new rootfs.tar.gz to replace the current one", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(
                            onClick = { showPatchDialog = true }
                        ) {
                            Text("Patch")
                        }
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("About", style = MaterialTheme.typography.titleSmall)
                            Text("DebianDroid v1.0.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text("VNC Password") },
            text = {
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
            },
            confirmButton = {
                Button(onClick = {
                    onPasswordChange(passwordInput)
                    showPasswordDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showPatchDialog) {
        AlertDialog(
            onDismissRequest = { showPatchDialog = false },
            title = { Text("Update Rootfs?") },
            text = { Text("Select a rootfs.tar.gz file to replace the current Debian rootfs. This will delete the existing rootfs and extract the new one.") },
            confirmButton = {
                Button(onClick = {
                    patchFilePicker.launch(arrayOf("application/gzip", "*/*"))
                }) { Text("Select File") }
            },
            dismissButton = {
                TextButton(onClick = { showPatchDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reinstall Debian?") },
            text = { Text("This will delete the current rootfs and re-extract it. Your settings will be preserved.") },
            confirmButton = {
                Button(
                    onClick = { showResetDialog = false; onReinstall() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Reinstall") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun ResolutionSelector(current: String, onSelect: (String) -> Unit) {
    val resolutions = listOf("1024x600", "1280x720", "1920x1080", "2560x1440")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Desktop Resolution", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            resolutions.forEach { res ->
                FilterChip(
                    selected = current == res,
                    onClick = { onSelect(res) },
                    label = { Text(res, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
    }
}
