package io.nostr.ndk.outbox

import io.nostr.ndk.NDK
import io.nostr.ndk.models.NDKFilter
import io.nostr.ndk.models.PublicKey
import io.nostr.ndk.relay.NDKRelay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Calculates the optimal set of relays for a subscription based on the outbox model.
 *
 * When outbox model is enabled, subscriptions with author filters will:
 * 1. Look up each author's write relays (their outbox)
 * 2. Select an optimal subset that covers all authors
 * 3. Prefer already-connected relays to minimize new connections
 * 4. Add temporary relays as needed for coverage
 */
class NDKRelaySetCalculator(private val ndk: NDK) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Calculates the optimal relays to subscribe to for the given filters.
     *
     * @param filters The subscription filters
     * @return Set of relays to subscribe on
     */
    suspend fun calculateRelaysForFilters(filters: List<NDKFilter>): Set<NDKRelay> {
        // If outbox model disabled, use all available relays
        if (!ndk.enableOutboxModel) {
            return ndk.pool.availableRelays.value
        }

        // Extract all authors from all filters
        val authors = filters.flatMap { it.authors ?: emptySet() }.toSet()

        // No authors = use all available relays
        if (authors.isEmpty()) {
            return ndk.pool.availableRelays.value
        }

        // Phase 1: Use cached relay lists only (non-blocking)
        val authorRelays = mutableMapOf<PublicKey, Set<String>>()
        val uncoveredAuthors = mutableSetOf<PublicKey>()

        for (pubkey in authors) {
            val cached = ndk.outboxTracker.getRelayList(pubkey)
            if (cached != null) {
                authorRelays[pubkey] = cached.writeRelays
            } else {
                authorRelays[pubkey] = emptySet()
                uncoveredAuthors.add(pubkey)
            }
        }

        // Phase 2: Trigger async fetch for uncovered authors (don't block)
        if (uncoveredAuthors.isNotEmpty()) {
            scope.launch {
                uncoveredAuthors.forEach { pubkey ->
                    ndk.outboxTracker.fetchRelayList(pubkey)
                }
            }
        }

        return selectOptimalRelays(authorRelays)
    }

    /**
     * Selects the optimal set of relays that covers all authors with the goal per author.
     * Prioritizes already-connected relays to minimize new connections.
     */
    private fun selectOptimalRelays(
        authorRelays: Map<PublicKey, Set<String>>
    ): Set<NDKRelay> {
        val availableUrls = ndk.pool.availableRelays.value.map { it.url }.toSet()
        val selectedUrls = mutableSetOf<String>()
        val authorCoverage = mutableMapOf<PublicKey, Int>()

        val goalPerAuthor = ndk.relayGoalPerAuthor

        // First pass: prefer already-available relays
        for ((pubkey, relays) in authorRelays) {
            val available = relays.intersect(availableUrls)
            val toAdd = available.take(goalPerAuthor)
            toAdd.forEach { url ->
                selectedUrls.add(url)
                authorCoverage[pubkey] = (authorCoverage[pubkey] ?: 0) + 1
            }
        }

        // Second pass: add new relays for under-covered authors
        for ((pubkey, relays) in authorRelays) {
            val coverage = authorCoverage[pubkey] ?: 0
            if (coverage < goalPerAuthor) {
                val needed = goalPerAuthor - coverage
                relays.filter { it !in selectedUrls }
                    .take(needed)
                    .forEach { url ->
                        selectedUrls.add(url)
                        authorCoverage[pubkey] = (authorCoverage[pubkey] ?: 0) + 1
                    }
            }
        }

        // Fallback: authors with no known relays use available relays
        val authorsWithNoRelays = authorRelays.filter { it.value.isEmpty() }.keys
        if (authorsWithNoRelays.isNotEmpty() && selectedUrls.isEmpty()) {
            selectedUrls.addAll(availableUrls)
        }

        // Return relay instances, adding temporary relays as needed
        return selectedUrls.map { url ->
            ndk.pool.getRelay(url) ?: ndk.pool.addTemporaryRelay(url)
        }.toSet()
    }
}
