package io.nostr.ndk.compose.content.renderer.links

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.nostr.ndk.content.ContentSegment

/**
 * Link preview renderer with OpenGraph metadata.
 *
 * Fetches and displays:
 * - Preview image
 * - Site name
 * - Title
 * - Description
 * - URL
 *
 * Falls back to BasicLinkRenderer if preview fetch fails or is disabled.
 */
@Composable
fun LinkPreviewRenderer(
    segment: ContentSegment.Link,
    onClick: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
    enablePreview: Boolean = true
) {
    var openGraphData by remember(segment.url) { mutableStateOf<OpenGraphData?>(null) }
    var isLoading by remember(segment.url) { mutableStateOf(enablePreview) }
    var hasFailed by remember(segment.url) { mutableStateOf(false) }

    LaunchedEffect(segment.url) {
        if (enablePreview) {
            try {
                val data = OpenGraphFetcher.fetch(segment.url)
                openGraphData = data
                hasFailed = data == null
            } catch (e: Exception) {
                hasFailed = true
            } finally {
                isLoading = false
            }
        }
    }

    when {
        // Show loading state
        isLoading -> {
            CircularProgressIndicator(modifier = modifier.padding(8.dp))
        }

        // Show preview card if we have OpenGraph data
        openGraphData != null && !hasFailed -> {
            LinkPreviewCard(
                data = openGraphData!!,
                url = segment.url,
                onClick = onClick,
                modifier = modifier
            )
        }

        // Fall back to basic link renderer
        else -> {
            BasicLinkRenderer(
                segment = segment,
                onClick = onClick,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun LinkPreviewCard(
    data: OpenGraphData,
    url: String,
    onClick: ((String) -> Unit)?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (onClick != null) {
                    Modifier.clickable { onClick(url) }
                } else Modifier
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Preview image
            if (data.image != null) {
                AsyncImage(
                    model = data.image,
                    contentDescription = "Preview image for ${data.title ?: url}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentScale = ContentScale.Crop
                )
            }

            // Content
            Column(modifier = Modifier.padding(12.dp)) {
                // Site name
                if (data.siteName != null) {
                    Text(
                        text = data.siteName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Title
                if (data.title != null) {
                    Text(
                        text = data.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Description
                if (data.description != null) {
                    Text(
                        text = data.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // URL
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
