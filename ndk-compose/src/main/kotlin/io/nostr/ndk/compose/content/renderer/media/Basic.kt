package io.nostr.ndk.compose.content.renderer.media

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import io.nostr.ndk.content.ContentSegment
import io.nostr.ndk.content.MediaType

/**
 * Basic renderer for media (images/videos).
 *
 * Features:
 * - Single image: Full width preview
 * - Multiple images: Grid layout (2 column max)
 * - Loading states with progress indicator
 * - Error states with icon
 * - Video indicator overlay
 * - Click to open gallery
 */
@Composable
fun BasicMediaRenderer(
    segment: ContentSegment.Media,
    onClick: ((urls: List<String>, index: Int) -> Unit)?,
    modifier: Modifier = Modifier
) {
    when {
        segment.urls.isEmpty() -> {
            // No media
        }
        segment.urls.size == 1 -> {
            SingleMediaItem(
                url = segment.urls[0],
                mediaType = segment.mediaType,
                onClick = { onClick?.invoke(segment.urls, 0) },
                modifier = modifier
            )
        }
        else -> {
            MediaGrid(
                urls = segment.urls,
                mediaType = segment.mediaType,
                onClick = onClick,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun SingleMediaItem(
    url: String,
    mediaType: MediaType,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val isVideo = mediaType == MediaType.VIDEO

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (onClick != null) {
                    Modifier.clickable { onClick() }
                } else Modifier
            )
    ) {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = if (isVideo) "Video" else "Image",
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            contentScale = ContentScale.Crop
        ) {
            when (painter.state) {
                is coil3.compose.AsyncImagePainter.State.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is coil3.compose.AsyncImagePainter.State.Error -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BrokenImage,
                            contentDescription = "Failed to load",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
                else -> {
                    SubcomposeAsyncImageContent()
                }
            }
        }

        // Video play indicator
        if (isVideo) {
            Icon(
                imageVector = Icons.Default.PlayCircle,
                contentDescription = "Video",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp)
            )
        }
    }
}

@Composable
private fun MediaGrid(
    urls: List<String>,
    mediaType: MediaType,
    onClick: ((urls: List<String>, index: Int) -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        urls.chunked(2).forEachIndexed { rowIndex, rowUrls ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowUrls.forEachIndexed { colIndex, url ->
                    val index = rowIndex * 2 + colIndex
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                    ) {
                        SingleMediaItem(
                            url = url,
                            mediaType = mediaType,
                            onClick = { onClick?.invoke(urls, index) }
                        )
                    }
                }
                // Fill empty space if odd number
                if (rowUrls.size == 1) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
