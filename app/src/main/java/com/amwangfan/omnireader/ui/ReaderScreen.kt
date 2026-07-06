package com.amwangfan.omnireader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NavigateBefore
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.amwangfan.omnireader.AppUiState

@Composable
fun ReaderScreen(
    state: AppUiState,
    padding: PaddingValues,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val reader = state.reader
    if (reader == null) {
        Text("No book is open.", modifier = Modifier.padding(padding).padding(18.dp))
        return
    }

    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Text(reader.title, style = MaterialTheme.typography.titleLarge)
        Text(
            (reader.currentChapterIndex + 1).toString() + " / " + reader.chapters.size +
                ": " + reader.currentChapter.title,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            reader.currentChapter.text,
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            OutlinedButton(
                onClick = onPrevious,
                enabled = reader.currentChapterIndex > 0 && !state.isBusy,
            ) {
                Icon(Icons.AutoMirrored.Outlined.NavigateBefore, contentDescription = null)
                Text("Previous")
            }
            Button(
                onClick = onNext,
                enabled = reader.currentChapterIndex < reader.chapters.lastIndex && !state.isBusy,
            ) {
                Text("Next")
                Icon(Icons.AutoMirrored.Outlined.NavigateNext, contentDescription = null)
            }
        }
    }
}
