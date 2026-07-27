package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SoundScapeShapes
import com.example.ui.theme.heavyShadow
import com.example.ui.viewmodel.LyricsUiState
import com.example.ui.viewmodel.MusicViewModel

@Composable
fun LyricsOverlay(
    viewModel: MusicViewModel,
    onDismiss: () -> Unit
) {
    val lyricsState = viewModel.lyricsState
    val activeLineIndex = viewModel.activeLyricLineIndex
    val listState = rememberLazyListState()

    LaunchedEffect(activeLineIndex, lyricsState) {
        if (lyricsState is LyricsUiState.Synced && activeLineIndex >= 0) {
            val viewportHeight = listState.layoutInfo.viewportSize.height
            listState.animateScrollToItem(
                index = activeLineIndex,
                scrollOffset = -(viewportHeight / 2).coerceAtLeast(0)
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .heavyShadow(SoundScapeShapes.comfortable)
            .background(SpotifyBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close", tint = ThemeWhite)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Lyrics",
                color = ThemeWhite,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (val state = lyricsState) {
            LyricsUiState.Loading -> LyricsCenteredMessage { CircularProgressIndicator(color = SpotifyGreen) }

            LyricsUiState.NotFoundState -> LyricsCenteredMessage {
                Text(
                    text = "No lyrics found for this song",
                    color = SpotifyTextSecondary,
                    fontSize = 14.sp
                )
            }

            LyricsUiState.OfflineUnavailable -> LyricsCenteredMessage {
                Text(
                    text = "Lyrics need an internet connection the first time",
                    color = SpotifyTextSecondary,
                    fontSize = 14.sp
                )
            }

            LyricsUiState.Idle -> LyricsCenteredMessage {
                Text(text = "Nothing playing right now", color = SpotifyTextSecondary)
            }

            is LyricsUiState.PlainOnly -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Unsynced lyrics",
                    color = SpotifyTextSecondary,
                    fontSize = 11.sp,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Text(
                    text = state.text,
                    color = ThemeWhite,
                    fontSize = 17.sp,
                    lineHeight = 26.sp
                )
            }

            is LyricsUiState.Synced -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(state.lines, key = { index, _ -> index }) { index, line ->
                    val isActive = index == activeLineIndex
                    Text(
                        text = line.text.ifBlank { "…" },
                        color = if (isActive) SpotifyGreen else SpotifyTextSecondary,
                        fontSize = if (isActive) 22.sp else 18.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.seekTo(line.timestampMs) }
                            .padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricsCenteredMessage(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
