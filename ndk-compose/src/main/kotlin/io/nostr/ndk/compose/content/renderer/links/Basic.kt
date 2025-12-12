package io.nostr.ndk.compose.content.renderer.links

import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import io.nostr.ndk.content.ContentSegment

/**
 * Basic renderer for HTTP/HTTPS links.
 *
 * Shows underlined clickable text.
 */
@Composable
fun BasicLinkRenderer(
    segment: ContentSegment.Link,
    onClick: ((String) -> Unit)?,
    modifier: Modifier = Modifier
) {
    Text(
        text = segment.url,
        style = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline
        ),
        modifier = modifier.then(
            if (onClick != null) {
                Modifier.clickable { onClick(segment.url) }
            } else Modifier
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
