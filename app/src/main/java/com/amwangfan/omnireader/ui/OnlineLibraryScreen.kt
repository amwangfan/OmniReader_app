package com.amwangfan.omnireader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.amwangfan.omnireader.AppUiState
import com.amwangfan.omnireader.data.BookDto
import com.amwangfan.omnireader.data.LocalBook

@Composable
fun OnlineLibraryScreen(
    state: AppUiState,
    padding: PaddingValues,
    onSettings: () -> Unit,
    onSync: () -> Unit,
    onRead: (LocalBook) -> Unit,
    onUpdate: (BookDto) -> Unit,
    onChooseUpdateFolder: (BookDto) -> Unit,
    onDownloadDefault: (BookDto) -> Unit,
    onChooseFolder: (BookDto) -> Unit,
) {
    if (state.serverUrl.isBlank() || state.accessToken.isBlank()) {
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Connect to your OmniReader server to browse online books.")
                Button(onClick = onSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = null)
                    Text("Open settings", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.remoteBooks.isEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("No server books yet.")
                    Button(onClick = onSync, enabled = !state.isBusy) { Text("Refresh") }
                }
            }
        } else {
            items(state.remoteBooks, key = BookDto::id) { remote ->
                val local = localBookForRemote(remote, state.localBooks)
                BookCard(
                    title = remote.title,
                    author = remote.author,
                    fileSize = remote.fileSize,
                    status = when {
                        local == null -> null
                        isUpdateAvailable(remote, local) -> "Update available"
                        else -> "On device"
                    },
                    actions = {
                        if (local != null) {
                            Button(onClick = { onRead(local) }, enabled = !state.isBusy) {
                                Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null)
                                Text("Read", modifier = Modifier.padding(start = 6.dp))
                            }
                            if (isUpdateAvailable(remote, local)) {
                                UpdateMenu(
                                    enabled = !state.isBusy,
                                    onUpdate = { onUpdate(remote) },
                                    onChooseFolder = { onChooseUpdateFolder(remote) },
                                )
                            }
                        } else {
                            DownloadMenu(
                                enabled = !state.isBusy,
                                onDefault = { onDownloadDefault(remote) },
                                onChooseFolder = { onChooseFolder(remote) },
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun UpdateMenu(enabled: Boolean, onUpdate: () -> Unit, onChooseFolder: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Button(onClick = { expanded = true }, enabled = enabled) {
            Icon(Icons.Outlined.Download, contentDescription = null)
            Text("Update", modifier = Modifier.padding(start = 6.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Current location") },
                onClick = { expanded = false; onUpdate() },
            )
            DropdownMenuItem(
                text = { Text("Choose folder") },
                leadingIcon = { Icon(Icons.Outlined.FolderOpen, contentDescription = null) },
                onClick = { expanded = false; onChooseFolder() },
            )
        }
    }
}

@Composable
private fun DownloadMenu(
    enabled: Boolean,
    onDefault: () -> Unit,
    onChooseFolder: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Button(onClick = { expanded = true }, enabled = enabled) {
            Icon(Icons.Outlined.Download, contentDescription = null)
            Text("Download", modifier = Modifier.padding(start = 6.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Default location") },
                leadingIcon = { Icon(Icons.Outlined.SaveAlt, contentDescription = null) },
                onClick = {
                    expanded = false
                    onDefault()
                },
            )
            DropdownMenuItem(
                text = { Text("Choose folder") },
                leadingIcon = { Icon(Icons.Outlined.FolderOpen, contentDescription = null) },
                onClick = {
                    expanded = false
                    onChooseFolder()
                },
            )
        }
    }
}
