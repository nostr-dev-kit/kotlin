package io.nostr.ndk.compose.content.renderer.events

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.nostr.ndk.NDK
import io.nostr.ndk.content.ContentSegment
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKFilter
import io.nostr.ndk.compose.user.UserDisplayName
import kotlinx.coroutines.flow.firstOrNull

/**
 * Article card renderer for kind 30023 (long-form content).
 * Shows article with title, summary, author, and optional image.
 */
@Composable
fun ArticleCardRenderer(
    ndk: NDK,
    segment: ContentSegment.EventMention,
    onClick: ((String) -> Unit)?,
    modifier: Modifier = Modifier
) {
    var event by remember { mutableStateOf<NDKEvent?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(segment.eventId, segment.identifier) {
        try {
            // Fetch the article
            val subscription = segment.eventId?.let { eventId ->
                val filter = NDKFilter(ids = setOf(eventId))
                ndk.subscribe(filter)
            } ?: run {
                val id = segment.identifier
                val auth = segment.author
                if (id != null && auth != null) {
                    val filter = NDKFilter(
                        kinds = setOf(30023),
                        authors = setOf(auth),
                        tags = mapOf("d" to setOf(id))
                    )
                    ndk.subscribe(filter)
                } else {
                    null
                }
            }

            if (subscription != null) {
                event = subscription.events.firstOrNull()
                subscription.stop()
            }
        } catch (e: Exception) {
            // Failed to fetch
        } finally {
            isLoading = false
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null && event != null) {
                    Modifier.clickable { onClick(event!!.id) }
                } else Modifier
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        when {
            isLoading -> {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            }
            event != null -> {
                ArticleCardContent(event!!, ndk)
            }
            else -> {
                Text(
                    text = "Article not found",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun ArticleCardContent(event: NDKEvent, ndk: NDK) {
    // Extract article metadata from tags
    val title = event.tags.find { it.name == "title" }?.values?.firstOrNull() ?: "Untitled Article"
    val summary = event.tags.find { it.name == "summary" }?.values?.firstOrNull()
    val imageUrl = event.tags.find { it.name == "image" }?.values?.firstOrNull()
    val publishedAt = event.tags.find { it.name == "published_at" }?.values?.firstOrNull()?.toLongOrNull()

    Row(modifier = Modifier.fillMaxWidth()) {
        // Article info
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp)
        ) {
            // Title
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Author
            UserDisplayName(
                pubkey = event.pubkey,
                ndk = ndk,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
            )

            if (summary != null) {
                Spacer(modifier = Modifier.height(8.dp))

                // Summary
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Image or icon
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "Article image",
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Default.Article,
                contentDescription = "Article",
                modifier = Modifier
                    .padding(16.dp)
                    .size(60.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
