package com.amwangfan.omnireader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.amwangfan.omnireader.AppUiState
import com.amwangfan.omnireader.data.LocalBook

@Composable
fun ShelfScreen(
    state: AppUiState,
    padding: PaddingValues,
    onImport: () -> Unit,
    onOpen: (LocalBook) -> Unit,
    onDelete: (LocalBook) -> Unit,
) {
    if (state.localBooks.isEmpty()) {
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Button(onClick = onImport, enabled = !state.isBusy) {
                Icon(Icons.Outlined.FileOpen, contentDescription = null)
                Text("Import EPUB", modifier = Modifier.padding(start = 8.dp))
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
        items(state.localBooks, key = LocalBook::id) { book ->
            BookCard(
                title = book.title,
                author = book.author,
                fileSize = book.fileSize,
                status = bookSyncLabel(book),
                actions = {
                    Button(onClick = { onOpen(book) }, enabled = !state.isBusy) {
                        Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null)
                        Text("Read", modifier = Modifier.padding(start = 6.dp))
                    }
                    IconButton(onClick = { onDelete(book) }, enabled = !state.isBusy) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete " + book.title)
                    }
                },
            )
        }
    }
}
