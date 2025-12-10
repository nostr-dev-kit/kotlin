package io.nostr.ndk.relay

import kotlin.math.min

/**
 * Implements reconnection logic with exponential backoff and flapping detection.
 */
internal class NDKReconnectionStrategy {
    companion object {
        const val INITIAL_DELAY_MS = 1_000L
        const val MAX_DELAY_MS = 60_000L
        const val MAX_ATTEMPTS = 10
        const val FLAPPING_THRESHOLD_MS = 1_000L
    }

    /**
     * Calculates the next reconnection delay using exponential backoff.
     *
     * @param attempt The attempt number (0-indexed)
     * @return The delay in milliseconds before the next reconnection attempt
     */
    fun nextDelay(attempt: Int): Long {
        // Cap attempt to prevent overflow
        val cappedAttempt = min(attempt, 6) // 2^6 * 1000 = 64,000 which is > MAX_DELAY_MS
        val delay = INITIAL_DELAY_MS * (1 shl cappedAttempt)
        return min(delay, MAX_DELAY_MS)
    }

    /**
     * Determines if a connection is flapping (rapidly connecting and disconnecting).
     *
     * @param connectionDuration How long the connection lasted in milliseconds
     * @return true if the connection is considered flapping
     */
    fun isFlapping(connectionDuration: Long): Boolean {
        return connectionDuration < FLAPPING_THRESHOLD_MS
    }
}
