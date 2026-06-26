package com.debiandroid.desktop.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    onBack: () -> Unit,
    sharedDirPath: String = ""
) {
    val context = LocalContext.current
    val sharedDir = remember { File(context.filesDir, "shared") }
    var currentPath by remember { mutableStateOf(sharedDirPath.ifEmpty { sharedDir.absolutePath }) }
    var files by remember { mutableStateOf(listOf<File>()) }

    val androidFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            currentPath = uri.path ?: currentPath
        }
    }

    LaunchedEffect(currentPath) {
        withContext(Dispatchers.IO) {
            val dir = File(currentPath)
            if (dir.exists()) {
                files = dir.listFiles()?.sortedWith(
                    compareByDescending<File> { it.isDirectory }.thenBy { it.name }
                ) ?: emptyList()
            } else {
                files = emptyList()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("File Manager") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { androidFilesLauncher.launch(null) }) {
                        Icon(Icons.Default.PhoneAndroid, "Android Files")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            // Breadcrumb / path bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(currentPath, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${files.size} items",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (files.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("Empty directory", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Parent directory
                    if (currentPath != "/") {
                        item {
                            DirectoryEntry(
                                name = "..",
                                onClick = {
                                    val parent = File(currentPath).parent
                                    if (parent != null) currentPath = parent
                                }
                            )
                        }
                    }
                    items(files) { file ->
                        if (file.isDirectory) {
                            DirectoryEntry(
                                name = file.name,
                                onClick = { currentPath = file.absolutePath }
                            )
                        } else {
                            FileEntry(name = file.name, size = file.length())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectoryEntry(name: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(name, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun FileEntry(name: String, size: Long) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyMedium)
                Text(formatFileSize(size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes > 1_000_000_000 -> "${bytes / 1_000_000_000} GB"
    bytes > 1_000_000 -> "${bytes / 1_000_000} MB"
    bytes > 1_000 -> "${bytes / 1_000} KB"
    else -> "$bytes B"
}
