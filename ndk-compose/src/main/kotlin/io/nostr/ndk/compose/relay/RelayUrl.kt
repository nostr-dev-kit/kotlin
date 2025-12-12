package io.nostr.ndk.compose.relay

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

/**
 * Displays relay WebSocket URL.
 *
 * Must be used within [RelayRoot].
 *
 * @param modifier Modifier for the text
 * @param maxLines Maximum number of lines (default: 1)
 */
@Composable
fun RelayUrl(
    modifier: Modifier = Modifier,
    maxLines: Int = 1
) {
    val context = LocalRelayContext.current
        ?: error("RelayUrl must be used within RelayRoot")

    Text(
        text = context.relay.url,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}
