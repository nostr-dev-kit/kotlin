package io.nostr.ndk.relay

/**
 * Events emitted by the relay pool to track relay lifecycle changes.
 */
sealed class PoolEvent {
    /**
     * Emitted when a relay successfully connects.
     *
     * @param relay The relay that connected
     */
    data class RelayConnected(val relay: NDKRelay) : PoolEvent()

    /**
     * Emitted when a relay disconnects.
     *
     * @param relay The relay that disconnected
     * @param reason Optional reason for disconnection
     */
    data class RelayDisconnected(val relay: NDKRelay, val reason: String?) : PoolEvent()

    /**
     * Emitted when a relay requires authentication (NIP-42).
     *
     * @param relay The relay requiring authentication
     * @param challenge The authentication challenge from the relay
     */
    data class RelayAuthRequired(val relay: NDKRelay, val challenge: String) : PoolEvent()

    /**
     * Emitted when a relay is removed from the pool.
     *
     * @param url The URL of the removed relay
     */
    data class RelayRemoved(val url: String) : PoolEvent()
}
