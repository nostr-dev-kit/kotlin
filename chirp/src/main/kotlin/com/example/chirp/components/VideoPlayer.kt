package com.example.chirp.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView

/**
 * Composable video player with lifecycle management.
 *
 * Features:
 * - Auto-play muted
 * - Looping playback
 * - Pauses on lifecycle stop
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

    // Create player instance with optimized buffering
    val player = remember(videoUrl) {
        ExoPlayer.Builder(context)
            .build()
            .apply {
                // Configure for auto-play looping
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 0f  // Start muted

                // Prepare media with preloading
                val mediaItem = MediaItem.Builder()
                    .setUri(videoUrl)
                    .build()

                setMediaItem(mediaItem)
                // Start preparing immediately for faster playback
                prepare()
                // Preload by setting playWhenReady but pausing if not active
                playWhenReady = false
            }
    }

    // Update mute state
    LaunchedEffect(isMuted) {
        player.volume = if (isMuted) 0f else 1f
    }

    // Play/pause based on visibility
    LaunchedEffect(isActive) {
        if (isActive) {
            player.play()
        } else {
            player.pause()
        }
    }

    // Lifecycle management
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, videoUrl) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> player.pause()
                Lifecycle.Event.ON_RESUME -> if (isActive) player.play()
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

    Box(modifier = modifier) {
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

        // Video player
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false  // We'll provide custom controls
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
