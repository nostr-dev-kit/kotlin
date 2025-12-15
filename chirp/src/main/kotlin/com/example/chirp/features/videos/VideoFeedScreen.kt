package com.example.chirp.features.videos

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.chirp.components.VideoPlayer
import com.example.chirp.util.formatRelativeTime
import io.nostr.ndk.kinds.NDKVideo

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoFeedScreen(
    viewModel: VideoFeedViewModel = hiltViewModel(),
    onNavigateToProfile: (String) -> Unit = {},
    onNavigateToRecord: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()

    if (state.error != null && state.videos.isEmpty()) {
        ErrorState(error = state.error!!, onNavigateToRecord = onNavigateToRecord)
        return
    }

    if (state.videos.isEmpty()) {
        EmptyState(onNavigateToRecord = onNavigateToRecord)
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
            val isCurrentPage = page == pagerState.currentPage
            val shouldPreload = kotlin.math.abs(page - pagerState.currentPage) <= 1

            VideoPlayerPage(
                video = state.videos[page],
                isActive = isCurrentPage,
                shouldPreload = shouldPreload,
                isMuted = state.isMuted,
                onMuteToggle = { viewModel.toggleMute() },
                onNavigateToProfile = onNavigateToProfile
            )
        }

        // Record FAB
        FloatingActionButton(
            onClick = onNavigateToRecord,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .padding(bottom = 80.dp), // Above bottom nav
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(
                imageVector = Icons.Default.Videocam,
                contentDescription = "Record video"
            )
        }
    }
}

@Composable
private fun VideoPlayerPage(
    video: NDKVideo,
    isActive: Boolean,
    shouldPreload: Boolean,
    isMuted: Boolean,
    onMuteToggle: () -> Unit,
    onNavigateToProfile: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                var offsetX = 0f
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetX > 200f) {
                            onNavigateToProfile(video.pubkey)
                        }
                        offsetX = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        offsetX += dragAmount
                    }
                )
            }
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
            onAuthorClick = { onNavigateToProfile(video.pubkey) },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(16.dp)
                .padding(end = 72.dp)  // Make room for interaction buttons
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
    onAuthorClick: () -> Unit,
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
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.clickable(onClick = onAuthorClick)
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
            text = formatRelativeTime(video.publishedAt ?: video.createdAt),
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun EmptyState(onNavigateToRecord: () -> Unit = {}) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.VideoLibrary,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = Color.White.copy(alpha = 0.5f)
            )
            Text(
                text = "No videos yet",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Be the first to post a 6-second video!",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onNavigateToRecord) {
                Icon(Icons.Default.Videocam, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Record Video")
            }
        }

        FloatingActionButton(
            onClick = onNavigateToRecord,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .padding(bottom = 80.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(
                imageVector = Icons.Default.Videocam,
                contentDescription = "Record video"
            )
        }
    }
}

@Composable
private fun ErrorState(error: String, onNavigateToRecord: () -> Unit = {}) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = "Oops!",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }

        FloatingActionButton(
            onClick = onNavigateToRecord,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .padding(bottom = 80.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(
                imageVector = Icons.Default.Videocam,
                contentDescription = "Record video"
            )
        }
    }
}

