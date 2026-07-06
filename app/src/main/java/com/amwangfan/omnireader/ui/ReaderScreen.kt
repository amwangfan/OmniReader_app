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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NavigateBefore
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.amwangfan.omnireader.AppUiState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

@OptIn(FlowPreview::class)
@Composable
fun ReaderScreen(
    state: AppUiState,
    padding: PaddingValues,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCheckpoint: (Int, Int) -> Unit,
    onActiveChanged: (Boolean) -> Unit,
) {
    val reader = state.reader
    if (reader == null) {
        Text("No book is open.", modifier = Modifier.padding(padding).padding(18.dp))
        return
    }
    val listState = rememberLazyListState(reader.initialBlockIndex, reader.initialScrollOffset)
    LifecycleResumeEffect(reader.localBookId) {
        onActiveChanged(true)
        onPauseOrDispose { onActiveChanged(false) }
    }
    LaunchedEffect(reader.positionVersion, reader.currentChapterIndex, listState) {
        listState.scrollToItem(reader.initialBlockIndex, reader.initialScrollOffset)
        snapshotFlow {
            Triple(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, listState.isScrollInProgress)
        }.filter { it.third }
            .map { it.first to it.second }
            .distinctUntilChanged()
            .debounce(650)
            .collect { (block, offset) -> onCheckpoint(block, offset) }
    }

    Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp)) {
        Text(reader.title, style = MaterialTheme.typography.titleLarge)
        Text(
            "${reader.currentChapterIndex + 1} / ${reader.chapters.size}: ${reader.currentChapter.title}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
            itemsIndexed(
                reader.currentChapter.blocks,
                key = { index, block -> "${reader.currentChapter.href}:${block.textHash}:$index" },
            ) { _, block ->
                Text(
                    block.text,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                    style = when (block.kind) {
                        "h1", "h2", "h3" -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.bodyLarge
                    },
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = onPrevious, enabled = reader.currentChapterIndex > 0 && !state.isBusy) {
                Icon(Icons.AutoMirrored.Outlined.NavigateBefore, contentDescription = null); Text("Previous")
            }
            Button(onClick = onNext, enabled = reader.currentChapterIndex < reader.chapters.lastIndex && !state.isBusy) {
                Text("Next"); Icon(Icons.AutoMirrored.Outlined.NavigateNext, contentDescription = null)
            }
        }
    }
}
