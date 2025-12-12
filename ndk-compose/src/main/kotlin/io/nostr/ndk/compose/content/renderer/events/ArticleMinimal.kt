package io.nostr.ndk.compose.content.renderer.events

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.nostr.ndk.NDK
import io.nostr.ndk.content.ContentSegment
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKFilter
import kotlinx.coroutines.flow.firstOrNull

/**
 * Minimal article renderer for kind 30023 - just the title.
 */
@Composable
fun ArticleMinimalRenderer(
    ndk: NDK,
    segment: ContentSegment.EventMention,
    onClick: ((String) -> Unit)?,
    modifier: Modifier = Modifier
) {
    var event by remember { mutableStateOf<NDKEvent?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(segment.eventId, segment.identifier) {
        try {
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

    when {
        isLoading -> {
            CircularProgressIndicator(modifier = modifier.padding(8.dp))
        }
        event != null -> {
            val title = event!!.tags.find { it.name == "title" }?.values?.firstOrNull() ?: "Untitled Article"

            Text(
                text = "📄 $title",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = modifier
                    .then(
                        if (onClick != null) {
                            Modifier.clickable { onClick(event!!.id) }
                        } else Modifier
                    )
            )
        }
        else -> {
            Text(
                text = "Article not found",
                modifier = modifier,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
