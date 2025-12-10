package io.nostr.ndk.compose

import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.subscription.NDKSubscription
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Flow extensions for NDK subscriptions.
 *
 * These extensions provide convenient Flow transformations for working with
 * subscription events in any reactive context (Compose, coroutines, etc).
 *
 * For Compose, use these flows with collectAsState():
 * ```kotlin
 * @Composable
 * fun FeedScreen(ndk: NDK) {
 *     val subscription = remember { ndk.subscribe(filter) }
 *     val events by subscription.eventsAccumulated().collectAsState(initial = emptyList())
 * }
 * ```
 */

/**
 * Creates a flow that accumulates events into a list, sorted by createdAt descending.
 *
 * Usage:
 * ```kotlin
 * val subscription = ndk.subscribe(filter)
 * subscription.eventsAccumulated().collect { events ->
 *     // events is sorted newest-first
 * }
 * ```
 */
fun NDKSubscription.eventsAccumulated(): Flow<List<NDKEvent>> {
    val accumulated = mutableListOf<NDKEvent>()
    val seenIds = mutableSetOf<String>()

    return events.map { event ->
        if (seenIds.add(event.id)) {
            accumulated.add(event)
            accumulated.sortByDescending { it.createdAt }
        }
        accumulated.toList()
    }
}

/**
 * Creates a flow that emits only the latest event by createdAt.
 *
 * Useful for fetching profiles (kind 0) or other replaceable events.
 *
 * Usage:
 * ```kotlin
 * val subscription = ndk.subscribe(NDKFilter(kinds = setOf(0), authors = setOf(pubkey)))
 * subscription.latestEvent().collect { profile ->
 *     profile?.let { displayProfile(it) }
 * }
 * ```
 */
fun NDKSubscription.latestEvent(): Flow<NDKEvent?> {
    var latest: NDKEvent? = null

    return events.map { event ->
        if (latest == null || event.createdAt > latest!!.createdAt) {
            latest = event
        }
        latest
    }
}

/**
 * Creates a flow that counts unique events.
 *
 * Usage:
 * ```kotlin
 * val filter = NDKFilter(kinds = setOf(7), tags = mapOf("e" to setOf(eventId)))
 * ndk.subscribe(filter).eventCount().collect { count ->
 *     println("$count reactions")
 * }
 * ```
 */
fun NDKSubscription.eventCount(): Flow<Int> {
    val seenIds = mutableSetOf<String>()

    return events.map { event ->
        seenIds.add(event.id)
        seenIds.size
    }
}

/**
 * Creates a flow that groups events by a key function.
 *
 * Usage:
 * ```kotlin
 * subscription.eventsGroupedBy { it.pubkey }.collect { byAuthor ->
 *     byAuthor.forEach { (pubkey, events) ->
 *         println("Author $pubkey has ${events.size} events")
 *     }
 * }
 * ```
 */
fun <K> NDKSubscription.eventsGroupedBy(
    keySelector: (NDKEvent) -> K
): Flow<Map<K, List<NDKEvent>>> {
    val groups = mutableMapOf<K, MutableList<NDKEvent>>()
    val seenIds = mutableSetOf<String>()

    return events.map { event ->
        if (seenIds.add(event.id)) {
            val key = keySelector(event)
            groups.getOrPut(key) { mutableListOf() }.add(event)
        }
        groups.mapValues { it.value.toList() }
    }
}

/**
 * Creates a flow that emits distinct events, filtering duplicates.
 *
 * Useful when combining multiple subscriptions or when relays may
 * send duplicate events.
 */
fun NDKSubscription.distinctEvents(): Flow<NDKEvent> {
    val seenIds = mutableSetOf<String>()

    return events.map { event ->
        if (seenIds.add(event.id)) event else null
    }.map { it!! } // Filter nulls - this is intentional as we want to skip duplicates
}
