package com.example.chirp.features.videos

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.chirp.components.VideoPlayer
import io.nostr.ndk.kinds.NDKVideo

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoFeedScreen(
    viewModel: VideoFeedViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    if (state.isLoading && state.videos.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    if (state.error != null && state.videos.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = state.error!!,
                color = MaterialTheme.colorScheme.error
            )
        }
        return
    }

    if (state.videos.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("No videos available")
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { state.videos.size })

    // Track current index for preloading
    LaunchedEffect(pagerState.currentPage) {
        viewModel.setCurrentIndex(pagerState.currentPage)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            VideoPlayerPage(
                video = state.videos[page],
                isActive = page == pagerState.currentPage,
                isMuted = state.isMuted,
                onMuteToggle = { viewModel.toggleMute() }
            )
        }
    }
}

@Composable
private fun VideoPlayerPage(
    video: NDKVideo,
    isActive: Boolean,
    isMuted: Boolean,
    onMuteToggle: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Video player (full screen)
        VideoPlayer(
            videoUrl = video.videoUrl ?: return,
            thumbnailUrl = video.thumbnailUrl,
            blurhash = video.blurhash,
            isActive = isActive,
            isMuted = isMuted,
            onMuteToggle = onMuteToggle,
            modifier = Modifier.fillMaxSize()
        )

        // Video info overlay (bottom-left)
        VideoInfoOverlay(
            video = video,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(16.dp)
        )

        // Mute button (top-right)
        IconButton(
            onClick = onMuteToggle,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                contentDescription = if (isMuted) "Unmute" else "Mute",
                tint = Color.White
            )
        }

        // Duration indicator (top-left)
        video.duration?.let { duration ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                color = Color.Black.copy(alpha = 0.6f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "${duration}s",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun VideoInfoOverlay(
    video: NDKVideo,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Author
        Text(
            text = "@${video.pubkey.take(16)}...",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge
        )

        // Title
        video.title?.let { title ->
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Description
        if (video.description.isNotBlank()) {
            var expanded by remember { mutableStateOf(false) }
            Text(
                text = video.description,
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures { expanded = !expanded }
                }
            )
        }

        // Timestamp
        Text(
            text = formatTimestamp(video.publishedAt ?: video.createdAt),
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

private fun formatTimestamp(unixTimestamp: Long): String {
    val now = System.currentTimeMillis() / 1000
    val diff = now - unixTimestamp

    return when {
        diff < 60 -> "just now"
        diff < 3600 -> "${diff / 60}m ago"
        diff < 86400 -> "${diff / 3600}h ago"
        diff < 604800 -> "${diff / 86400}d ago"
        else -> "${diff / 604800}w ago"
    }
}
