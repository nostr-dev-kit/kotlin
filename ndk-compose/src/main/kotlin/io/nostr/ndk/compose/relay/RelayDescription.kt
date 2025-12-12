package io.nostr.ndk.compose.relay

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

/**
 * Displays relay description from NIP-11.
 *
 * Must be used within [RelayRoot]. Only displays if NIP-11 data is available.
 *
 * @param modifier Modifier for the text
 * @param maxLines Maximum number of lines (default: 2)
 */
@Composable
fun RelayDescription(
    modifier: Modifier = Modifier,
    maxLines: Int = 2
) {
    val context = LocalRelayContext.current
        ?: error("RelayDescription must be used within RelayRoot")

    val description = context.nip11Data?.description

    if (description != null) {
        Text(
            text = description,
            modifier = modifier,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}
