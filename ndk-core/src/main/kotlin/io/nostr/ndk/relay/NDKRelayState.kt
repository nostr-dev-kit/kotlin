package io.nostr.ndk.relay

/**
 * Represents the connection state of a Nostr relay.
 */
enum class NDKRelayState {
    /**
     * Relay is not connected and not attempting to connect.
     */
    DISCONNECTED,

    /**
     * Relay is attempting to establish a connection.
     */
    CONNECTING,

    /**
     * Relay is connected and ready to send/receive messages.
     */
    CONNECTED,

    /**
     * Relay requires NIP-42 authentication.
     */
    AUTH_REQUIRED,

    /**
     * Relay is currently authenticating using NIP-42.
     */
    AUTHENTICATING,

    /**
     * Relay is connected and authenticated.
     */
    AUTHENTICATED,

    /**
     * Relay is attempting to reconnect after a disconnection.
     */
    RECONNECTING,

    /**
     * Relay is flapping (rapid connect/disconnect cycles detected).
     */
    FLAPPING
}
