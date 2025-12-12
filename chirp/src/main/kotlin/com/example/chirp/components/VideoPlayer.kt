package com.example.chirp.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

/**
 * Composable video player with lifecycle management.
 *
 * Features:
 * - Auto-play muted
 * - Looping playback
 * - Pauses on lifecycle stop
 * - Error state display
 * - Play/pause tap control with visual feedback
 * - Proper cleanup
 *
 * @param videoUrl URL of video to play
 * @param thumbnailUrl Optional thumbnail for loading state
 * @param blurhash Optional blurhash placeholder
 * @param isActive Whether this player should be active (for pager visibility)
 * @param isMuted Whether audio is muted
 * @param onMuteToggle Callback when user toggles mute
 */
@Composable
fun VideoPlayer(
    videoUrl: String,
    thumbnailUrl: String? = null,
    blurhash: String? = null,
    isActive: Boolean,
    isMuted: Boolean,
    onMuteToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showPlayPauseIcon by remember { mutableStateOf(false) }

    // Create player instance
    val player = remember(videoUrl) {
        ExoPlayer.Builder(context)
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 0f

                val mediaItem = MediaItem.Builder()
                    .setUri(videoUrl)
                    .build()

                setMediaItem(mediaItem)
                prepare()
                playWhenReady = false
            }
    }

    // Listen for player state changes
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                hasError = true
                errorMessage = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                        "Network error"
                    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
                    PlaybackException.ERROR_CODE_IO_NO_PERMISSION ->
                        "Video unavailable"
                    else -> "Playback error"
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    // Update mute state
    LaunchedEffect(isMuted) {
        player.volume = if (isMuted) 0f else 1f
    }

    // Play/pause based on visibility
    LaunchedEffect(isActive) {
        if (isActive && !hasError) {
            player.play()
        } else {
            player.pause()
        }
    }

    // Auto-hide play/pause icon
    LaunchedEffect(showPlayPauseIcon) {
        if (showPlayPauseIcon) {
            delay(800)
            showPlayPauseIcon = false
        }
    }

    // Lifecycle management
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, videoUrl) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> player.pause()
                Lifecycle.Event.ON_RESUME -> if (isActive && !hasError) player.play()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)

        onDispose {
            lifecycle.removeObserver(observer)
            player.stop()
            player.release()
        }
    }

    Box(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (!hasError) {
                    if (player.isPlaying) {
                        player.pause()
                    } else {
                        player.play()
                    }
                    showPlayPauseIcon = true
                }
            }
    ) {
        // Thumbnail background (shown while video loads)
        if (thumbnailUrl != null) {
            BlurhashImage(
                url = thumbnailUrl,
                blurhash = blurhash,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            )
        }

        // Video player (only if no error)
        if (!hasError) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Play/Pause indicator
        AnimatedVisibility(
            visible = showPlayPauseIcon,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = androidx.compose.foundation.shape.CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        // Error state
        if (hasError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "Error",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = errorMessage ?: "Playback error",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
