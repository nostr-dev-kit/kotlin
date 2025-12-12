package io.nostr.ndk.compose.content.renderer.events

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.nostr.ndk.NDK
import io.nostr.ndk.content.ContentSegment

/**
 * Compact event mention renderer - just shows event ID.
 */
@Composable
fun EventMentionCompactRenderer(
    ndk: NDK,
    segment: ContentSegment.EventMention,
    onClick: ((String) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val displayText = "Note: ${segment.eventId?.take(8) ?: segment.identifier?.take(8) ?: "unknown"}..."

    val eventId = segment.eventId
    Text(
        text = displayText,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .padding(vertical = 2.dp)
            .then(
                if (onClick != null && eventId != null) {
                    Modifier.clickable { onClick(eventId) }
                } else Modifier
            )
    )
}
