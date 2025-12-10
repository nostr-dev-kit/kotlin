package io.nostr.ndk.subscription

import io.nostr.ndk.relay.NDKRelay
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/**
 * Tracks signature validation statistics per relay for trust-based sampling.
 *
 * As relays prove themselves trustworthy (by consistently sending valid events),
 * we can reduce the frequency of signature verification, improving performance.
 * This implements a "trust but verify" model where higher trust leads to lower
 * verification rates, but never zero.
 *
 * The validation ratio is calculated as:
 *   ratio = validCount / (validCount + invalidCount + 1)
 *
 * The probability of validation is:
 *   probability = 1.0 - (ratio * maxTrustReduction)
 *
 * This ensures that even highly trusted relays still have a baseline verification rate.
 */
class NDKValidationRatioTracker {
    companion object {
        /**
         * Maximum reduction in validation probability for trusted relays.
         * A value of 0.9 means that the most trusted relays will still be
         * validated 10% of the time (1.0 - 0.9 = 0.1).
         */
        const val MAX_TRUST_REDUCTION = 0.9f

        /**
         * Minimum number of events that must be validated before trust kicks in.
         * This prevents trusting relays too quickly.
         */
        const val MIN_EVENTS_BEFORE_TRUST = 100L
    }

    // Per-relay validation statistics
    private val relayStats = ConcurrentHashMap<String, RelayValidationStats>()

    /**
     * Determines whether an event from this relay should be validated.
     *
     * Events are always validated if:
     * - The relay has not yet processed MIN_EVENTS_BEFORE_TRUST events
     * - Random sampling based on trust ratio determines it should be validated
     *
     * @param relay The relay to check
     * @return True if the event should be validated, false to skip validation
     */
    fun shouldValidate(relay: NDKRelay): Boolean {
        val stats = relayStats.getOrPut(relay.url) { RelayValidationStats() }

        val totalEvents = stats.validCount.get() + stats.invalidCount.get()

        // Always validate until we have enough history
        if (totalEvents < MIN_EVENTS_BEFORE_TRUST) {
            return true
        }

        // Calculate trust ratio (0.0 = no trust, 1.0 = full trust)
        val trustRatio = stats.validCount.get().toFloat() / (totalEvents + 1)

        // Higher trust = lower validation rate
        // validationProbability ranges from 1.0 (no trust) to 0.1 (full trust)
        val validationProbability = 1.0f - (trustRatio * MAX_TRUST_REDUCTION)

        return Random.nextFloat() < validationProbability
    }

    /**
     * Records the result of a signature validation for a relay.
     *
     * @param relay The relay that sent the event
     * @param valid True if the signature was valid, false otherwise
     */
    fun recordValidation(relay: NDKRelay, valid: Boolean) {
        val stats = relayStats.getOrPut(relay.url) { RelayValidationStats() }

        if (valid) {
            stats.validCount.incrementAndGet()
        } else {
            stats.invalidCount.incrementAndGet()
        }
    }

    /**
     * Gets the current trust ratio for a relay.
     *
     * @param relay The relay to check
     * @return Trust ratio from 0.0 to 1.0, or null if no events have been validated
     */
    fun getTrustRatio(relay: NDKRelay): Float? {
        val stats = relayStats[relay.url] ?: return null
        val total = stats.validCount.get() + stats.invalidCount.get()
        if (total == 0L) return null
        return stats.validCount.get().toFloat() / total
    }

    /**
     * Gets the validation statistics for a relay.
     *
     * @param relayUrl The relay URL to check
     * @return Stats object or null if no events have been validated
     */
    fun getStats(relayUrl: String): ValidationStatsSnapshot? {
        val stats = relayStats[relayUrl] ?: return null
        return ValidationStatsSnapshot(
            validCount = stats.validCount.get(),
            invalidCount = stats.invalidCount.get()
        )
    }

    /**
     * Resets all statistics. Useful for testing.
     */
    fun reset() {
        relayStats.clear()
    }

    /**
     * Resets statistics for a specific relay.
     *
     * @param relayUrl The relay URL to reset
     */
    fun resetRelay(relayUrl: String) {
        relayStats.remove(relayUrl)
    }
}

/**
 * Thread-safe validation statistics for a relay.
 */
internal class RelayValidationStats {
    val validCount = AtomicLong(0)
    val invalidCount = AtomicLong(0)
}

/**
 * Immutable snapshot of validation statistics.
 */
data class ValidationStatsSnapshot(
    val validCount: Long,
    val invalidCount: Long
) {
    /**
     * Total number of validations performed.
     */
    val totalCount: Long get() = validCount + invalidCount

    /**
     * Trust ratio from 0.0 to 1.0.
     */
    val trustRatio: Float get() = if (totalCount == 0L) 0f else validCount.toFloat() / totalCount

    /**
     * Current validation probability (1.0 = always validate).
     */
    val validationProbability: Float get() {
        if (totalCount < NDKValidationRatioTracker.MIN_EVENTS_BEFORE_TRUST) {
            return 1.0f
        }
        return 1.0f - (trustRatio * NDKValidationRatioTracker.MAX_TRUST_REDUCTION)
    }
}
