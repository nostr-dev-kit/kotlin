package io.nostr.ndk.compose.relay

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

/**
 * Displays relay name from NIP-11 or falls back to URL.
 *
 * Must be used within [RelayRoot].
 *
 * @param modifier Modifier for the text
 * @param maxLines Maximum number of lines (default: 1)
 */
@Composable
fun RelayName(
    modifier: Modifier = Modifier,
    maxLines: Int = 1
) {
    val context = LocalRelayContext.current
        ?: error("RelayName must be used within RelayRoot")

    val name = context.nip11Data?.name ?: context.relay.url.removePrefix("wss://").removePrefix("ws://")

    Text(
        text = name,
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}
