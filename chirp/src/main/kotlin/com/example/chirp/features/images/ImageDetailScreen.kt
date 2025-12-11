package com.example.chirp.features.images

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.chirp.components.UserDisplayName
import io.nostr.ndk.NDK
import io.nostr.ndk.kinds.NDKImage
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableState

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ImageDetailScreen(
    gallery: NDKImage,
    ndk: NDK,
    onDismiss: () -> Unit
) {
    var currentPage by remember { mutableStateOf(0) }
    val pagerState = rememberPagerState(pageCount = { gallery.images.size })

    LaunchedEffect(pagerState.currentPage) {
        currentPage = pagerState.currentPage
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Horizontal pager for swiping between images
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val image = gallery.images[page]

            // Zoomable image with pinch-to-zoom
            ZoomableAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(image.url)
                    .crossfade(true)
                    .apply {
                        image.sha256?.let {
                            memoryCacheKey(it)
                            diskCacheKey(it)
                        }
                    }
                    .build(),
                contentDescription = image.alt,
                modifier = Modifier.fillMaxSize(),
                state = rememberZoomableState()
            )
        }

        // Top bar with close button and page indicator
        TopAppBar(
            title = {
                Text(
                    "${currentPage + 1} / ${gallery.images.size}",
                    color = Color.White
                )
            },
            navigationIcon = {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Black.copy(alpha = 0.5f)
            ),
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Page indicators (dots)
        if (gallery.images.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 72.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(gallery.images.size) { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index == currentPage) 8.dp else 6.dp)
                            .background(
                                color = if (index == currentPage)
                                    Color.White
                                else
                                    Color.White.copy(alpha = 0.5f),
                                shape = CircleShape
                            )
                    )
                }
            }
        }

        // Bottom caption overlay
        if (gallery.caption.isNotBlank()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                color = Color.Black.copy(alpha = 0.7f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Author info
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UserDisplayName(
                            pubkey = gallery.pubkey,
                            ndk = ndk,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = "•",
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Text(
                            text = formatTimestamp(gallery.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Caption text
                    Text(
                        text = gallery.caption,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
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
