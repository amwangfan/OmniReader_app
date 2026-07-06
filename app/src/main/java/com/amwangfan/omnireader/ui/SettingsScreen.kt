package com.amwangfan.omnireader.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.amwangfan.omnireader.AppUiState

@Composable
fun SettingsScreen(
    state: AppUiState,
    padding: PaddingValues,
    onServerChanged: (String) -> Unit,
    onSaveServer: () -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onChooseFolder: () -> Unit,
    onResetFolder: () -> Unit,
    onAutoUploadChanged: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionTitle("Server")
        OutlinedTextField(
            value = state.serverUrl,
            onValueChange = onServerChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Server address") },
            placeholder = { Text("http://100.114.93.90:18080") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        Button(onClick = onSaveServer, enabled = !state.isBusy) {
            Icon(Icons.Outlined.Save, contentDescription = null)
            Text("Save server", modifier = Modifier.padding(start = 8.dp))
        }

        HorizontalDivider()
        SectionTitle("Account")
        if (state.accessToken.isNotBlank()) {
            Text("Signed in as " + state.username)
            OutlinedButton(onClick = onLogout, enabled = !state.isBusy) {
                Icon(Icons.Outlined.Logout, contentDescription = null)
                Text("Sign out", modifier = Modifier.padding(start = 8.dp))
            }
        } else {
            OutlinedTextField(
                value = state.username,
                onValueChange = onUsernameChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Username") },
                singleLine = true,
            )
            OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
            )
            Button(onClick = onLogin, enabled = !state.isBusy) {
                Icon(Icons.Outlined.Login, contentDescription = null)
                Text("Sign in", modifier = Modifier.padding(start = 8.dp))
            }
        }

        HorizontalDivider()
        SectionTitle("Storage and sync")
        Text(
            "Download location: " + folderLabel(state.defaultDownloadTreeUri),
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onChooseFolder, enabled = !state.isBusy) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                Text("Choose folder", modifier = Modifier.padding(start = 8.dp))
            }
            if (state.defaultDownloadTreeUri.isNotBlank()) {
                OutlinedButton(onClick = onResetFolder, enabled = !state.isBusy) {
                    Icon(Icons.Outlined.DeleteSweep, contentDescription = null)
                    Text("Reset", modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Automatically upload imported books")
                Text(
                    "Failed uploads retry on the next sync.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = state.autoUploadImports,
                onCheckedChange = onAutoUploadChanged,
                enabled = !state.isBusy,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

private fun folderLabel(uri: String): String {
    if (uri.isBlank()) return "App storage"
    val segment = Uri.parse(uri).lastPathSegment?.let(Uri::decode).orEmpty()
    return segment.ifBlank { "Selected folder" }
}
