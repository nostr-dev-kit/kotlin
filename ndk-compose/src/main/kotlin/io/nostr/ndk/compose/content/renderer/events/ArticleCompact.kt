package io.nostr.ndk.compose.content.renderer.events

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.nostr.ndk.NDK
import io.nostr.ndk.content.ContentSegment
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKFilter
import io.nostr.ndk.compose.user.UserDisplayName
import kotlinx.coroutines.flow.firstOrNull

/**
 * Compact article renderer for kind 30023 - title and author only.
 */
@Composable
fun ArticleCompactRenderer(
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

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null && event != null) {
                    Modifier.clickable { onClick(event!!.id) }
                } else Modifier
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        when {
            isLoading -> {
                CircularProgressIndicator(modifier = Modifier.padding(12.dp))
            }
            event != null -> {
                CompactArticleContent(event!!, ndk)
            }
            else -> {
                Text(
                    text = "Article not found",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun CompactArticleContent(event: NDKEvent, ndk: NDK) {
    val title = event.tags.find { it.name == "title" }?.values?.firstOrNull() ?: "Untitled Article"

    Column(modifier = Modifier.padding(12.dp)) {
        // Title
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
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
    }
}
