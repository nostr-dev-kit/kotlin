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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.nostr.ndk.NDK
import io.nostr.ndk.content.ContentSegment
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKFilter
import io.nostr.ndk.compose.user.UserDisplayName
import kotlinx.coroutines.flow.firstOrNull

/**
 * Basic renderer for event mentions (note, nevent).
 * Shows a preview card of the mentioned event.
 */
@Composable
fun BasicEventMentionRenderer(
    ndk: NDK,
    segment: ContentSegment.EventMention,
    onClick: ((String) -> Unit)?,
    modifier: Modifier = Modifier
) {
    var event by remember { mutableStateOf<NDKEvent?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(segment.eventId, segment.identifier) {
        try {
            // Try to fetch the event
            val subscription = segment.eventId?.let { eventId ->
                val filter = NDKFilter(ids = setOf(eventId))
                ndk.subscribe(filter)
            } ?: run {
                val id = segment.identifier
                val auth = segment.author
                val k = segment.kind
                if (id != null && auth != null && k != null) {
                    val filter = NDKFilter(
                        kinds = setOf(k),
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
            // Failed to fetch event
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
        Column(modifier = Modifier.padding(12.dp)) {
            when {
                isLoading -> {
                    CircularProgressIndicator()
                }
                event != null -> {
                    // Show event preview
                    UserDisplayName(
                        pubkey = event!!.pubkey,
                        ndk = ndk,
                        style = MaterialTheme.typography.labelMedium
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = event!!.content,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                else -> {
                    Text(
                        text = "Note: ${segment.eventId?.take(8) ?: segment.identifier?.take(8)}...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
