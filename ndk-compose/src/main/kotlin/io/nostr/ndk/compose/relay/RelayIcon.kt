package io.nostr.ndk.compose.relay

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * Displays relay icon from NIP-11 or fallback icon.
 *
 * Must be used within [RelayRoot].
 *
 * @param modifier Modifier for the icon
 * @param size Size of the icon (default: 48.dp)
 */
@Composable
fun RelayIcon(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    val context = LocalRelayContext.current
        ?: error("RelayIcon must be used within RelayRoot")

    val iconUrl = context.nip11Data?.icon

    if (iconUrl != null) {
        AsyncImage(
            model = iconUrl,
            contentDescription = "Relay icon",
            modifier = modifier
                .size(size)
                .clip(CircleShape)
        )
    } else {
        Icon(
            imageVector = Icons.Default.Cloud,
            contentDescription = "Relay",
            modifier = modifier.size(size)
        )
    }
}
