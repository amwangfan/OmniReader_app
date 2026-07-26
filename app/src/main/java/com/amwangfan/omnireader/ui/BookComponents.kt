package com.amwangfan.omnireader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.amwangfan.omnireader.data.BookDto
import com.amwangfan.omnireader.data.BookSyncState
import com.amwangfan.omnireader.data.LocalBook

fun bookSyncLabel(book: LocalBook): String = when (book.syncState) {
    BookSyncState.PENDING_UPLOAD -> "Pending upload"
    BookSyncState.SYNCED -> "Synced"
    BookSyncState.LOCAL_ONLY -> "Local only"
}

fun localBookForRemote(remote: BookDto, localBooks: List<LocalBook>): LocalBook? =
    localBooks.firstOrNull { it.remoteBookId == remote.id }

fun isUpdateAvailable(remote: BookDto, local: LocalBook): Boolean =
    if (remote.contentRevision.isNotBlank()) {
        remote.contentRevision != local.contentRevision
    } else {
        remote.checksum.isNotBlank() && remote.checksum != local.checksum
    }

@Composable
fun BookCard(
    title: String,
    author: String,
    fileSize: Long,
    status: String? = null,
    actions: @Composable RowScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            val metadata = bookMetadata(author, fileSize)
            if (metadata.isNotBlank()) {
                Text(metadata, style = MaterialTheme.typography.bodyMedium)
            }
            if (!status.isNullOrBlank()) {
                Text(
                    status,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                content = actions,
            )
        }
    }
}

private fun bookMetadata(author: String, fileSize: Long): String {
    val size = when {
        fileSize < 1024 -> "1 KB"
        fileSize < 1024 * 1024 -> ((fileSize + 1023) / 1024).toString() + " KB"
        else -> String.format("%.1f MB", fileSize / (1024.0 * 1024.0))
    }
    return listOf(author, size).filter(String::isNotBlank).joinToString(" · ")
}
