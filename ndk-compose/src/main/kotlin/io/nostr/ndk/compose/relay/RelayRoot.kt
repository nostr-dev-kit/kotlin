package io.nostr.ndk.compose.relay

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.nostr.ndk.relay.NDKRelay
import io.nostr.ndk.relay.nip11.Nip11RelayInformation

/**
 * Root component that provides relay context to children.
 *
 * Automatically fetches NIP-11 metadata in the background and makes it available
 * to child components via [LocalRelayContext].
 *
 * Usage:
 * ```kotlin
 * RelayRoot(relay = relay) {
 *     RelayIcon()
 *     RelayName()
 *     RelayConnectionStatus()
 * }
 * ```
 *
 * @param relay The relay instance to provide context for
 * @param onClick Optional click handler
 * @param modifier Modifier for the root container
 * @param content The child composables that will consume the relay context
 */
@Composable
fun RelayRoot(
    relay: NDKRelay,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // Fetch NIP-11 data asynchronously
    val nip11Data by produceState<Nip11RelayInformation?>(
        initialValue = relay.nip11Info, // Use cached value if available
        key1 = relay.url
    ) {
        // Only fetch if not already cached
        if (relay.nip11Info == null) {
            val result = relay.fetchNip11Info()
            value = result.getOrNull()
        }
    }

    // Create context
    val context = remember(relay, nip11Data, onClick) {
        RelayContext(
            relay = relay,
            nip11Data = nip11Data,
            onClick = onClick
        )
    }

    // Provide context to children
    CompositionLocalProvider(LocalRelayContext provides context) {
        Box(modifier = modifier) {
            content()
        }
    }
}
