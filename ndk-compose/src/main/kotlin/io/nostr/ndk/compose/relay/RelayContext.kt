package io.nostr.ndk.compose.relay

import androidx.compose.runtime.compositionLocalOf
import io.nostr.ndk.relay.NDKRelay
import io.nostr.ndk.relay.nip11.Nip11RelayInformation

/**
 * Context shared between relay components.
 *
 * This context is provided by [RelayRoot] and consumed by child primitives
 * like RelayIcon, RelayName, RelayConnectionStatus, etc.
 */
data class RelayContext(
    /** The relay instance */
    val relay: NDKRelay,

    /** NIP-11 relay information (fetched asynchronously) */
    val nip11Data: Nip11RelayInformation? = null,

    /** Optional click handler */
    val onClick: (() -> Unit)? = null
)

/**
 * CompositionLocal for relay context.
 *
 * This allows relay primitives to access relay data without explicit prop drilling.
 */
val LocalRelayContext = compositionLocalOf<RelayContext?> { null }
