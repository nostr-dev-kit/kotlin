package io.nostr.ndk.subscription

import io.nostr.ndk.NDK
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKFilter
import io.nostr.ndk.relay.NDKRelay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Groups similar subscriptions to reduce relay load.
 *
 * When multiple subscriptions have the same filter fingerprint (same kinds, authors, tags
 * but possibly different temporal constraints), they can share a single relay subscription.
 * Events from the grouped relay subscription are then fan-out to all individual subscriptions.
 *
 * The grouper uses a delay window to batch subscriptions created within a short time period,
 * improving the chances of finding groupable subscriptions.
 *
 * @property ndk Reference to the parent NDK instance
 * @property groupingDelayMs Delay in milliseconds to wait before processing pending subscriptions
 */
internal class NDKSubscriptionGrouper(
    private val ndk: NDK,
    private val groupingDelayMs: Long = 100
) {
    // Coroutine scope for grouper operations
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Channel for pending subscriptions
    private val pendingChannel = Channel<PendingSubscription>(Channel.UNLIMITED)

    // Active grouped subscriptions: fingerprint -> GroupedSubscription
    private val groupedSubscriptions = ConcurrentHashMap<String, GroupedSubscription>()

    // Maps individual subscription IDs to their group fingerprint
    private val subscriptionToGroup = ConcurrentHashMap<String, String>()

    // Tracks whether the grouper processing loop is running
    @Volatile
    private var processing = false

    /**
     * Enqueues a subscription for grouping.
     *
     * The subscription will be held briefly (groupingDelayMs) to allow other
     * similar subscriptions to be batched together.
     *
     * @param subscription The subscription to enqueue
     * @param relays The relays to subscribe on
     */
    fun enqueue(subscription: NDKSubscription, relays: Set<NDKRelay>) {
        pendingChannel.trySend(PendingSubscription(subscription, relays))
        ensureProcessing()
    }

    /**
     * Removes a subscription from its group.
     *
     * If the group becomes empty, the grouped relay subscription is closed.
     *
     * @param subscriptionId The subscription ID to remove
     */
    fun remove(subscriptionId: String) {
        val fingerprint = subscriptionToGroup.remove(subscriptionId) ?: return
        val group = groupedSubscriptions[fingerprint] ?: return

        group.removeSubscription(subscriptionId)

        // If no more subscribers, close the relay subscription
        if (group.isEmpty()) {
            groupedSubscriptions.remove(fingerprint)
            group.close()
        }
    }

    /**
     * Dispatches an event to all subscriptions in the matching group.
     *
     * @param event The event to dispatch
     * @param relay The relay that sent the event
     * @param groupSubscriptionId The ID of the grouped relay subscription
     */
    internal fun dispatchToGroup(event: NDKEvent, relay: NDKRelay, groupSubscriptionId: String) {
        // Find the group that owns this relay subscription
        groupedSubscriptions.values.find { it.relaySubscriptionId == groupSubscriptionId }
            ?.dispatch(event, relay)
    }

    /**
     * Ensures the processing loop is running.
     */
    private fun ensureProcessing() {
        if (processing) return

        synchronized(this) {
            if (processing) return
            processing = true
        }

        scope.launch {
            processPendingSubscriptions()
        }
    }

    /**
     * Main processing loop that batches and groups subscriptions.
     */
    private suspend fun processPendingSubscriptions() {
        try {
            pendingChannel.receiveAsFlow().collect { first ->
                // Wait for more subscriptions to arrive
                delay(groupingDelayMs)

                // Collect all pending subscriptions
                val pending = mutableListOf(first)
                while (true) {
                    val next = pendingChannel.tryReceive().getOrNull() ?: break
                    pending.add(next)
                }

                // Process the batch
                processBatch(pending)
            }
        } finally {
            processing = false
        }
    }

    /**
     * Processes a batch of pending subscriptions, grouping them by fingerprint.
     */
    private fun processBatch(pending: List<PendingSubscription>) {
        // Group by combined fingerprint of all filters
        val byFingerprint = pending.groupBy { pendingSub ->
            pendingSub.subscription.filters.map { it.fingerprint() }.sorted().joinToString(";")
        }

        byFingerprint.forEach { (fingerprint, group) ->
            if (group.size == 1 && !groupedSubscriptions.containsKey(fingerprint)) {
                // Single subscription with no existing group - execute directly
                executeDirect(group.first())
            } else {
                // Multiple subscriptions or existing group - create/join group
                createOrJoinGroup(fingerprint, group)
            }
        }
    }

    /**
     * Executes a subscription directly without grouping.
     */
    private fun executeDirect(pending: PendingSubscription) {
        pending.subscription.start(pending.relays)
    }

    /**
     * Creates a new group or adds subscriptions to an existing group.
     */
    private fun createOrJoinGroup(fingerprint: String, pending: List<PendingSubscription>) {
        val group = groupedSubscriptions.getOrPut(fingerprint) {
            // Create new grouped subscription
            val mergedFilters = mergeFilters(pending.flatMap { it.subscription.filters })
            val relays = pending.flatMap { it.relays }.toSet()

            GroupedSubscription(
                fingerprint = fingerprint,
                filters = mergedFilters,
                relays = relays,
                ndk = ndk
            ).also { it.start() }
        }

        // Add all pending subscriptions to the group
        pending.forEach { pendingSub ->
            group.addSubscription(pendingSub.subscription)
            subscriptionToGroup[pendingSub.subscription.id] = fingerprint
        }
    }

    /**
     * Merges multiple filters with the same fingerprint.
     *
     * Since fingerprints exclude temporal constraints, we need to find the widest
     * temporal range to ensure all events are captured.
     */
    private fun mergeFilters(filters: List<NDKFilter>): List<NDKFilter> {
        // Group filters by their fingerprint
        val byFingerprint = filters.groupBy { it.fingerprint() }

        return byFingerprint.map { (_, sameFingerprint) ->
            // Find the widest temporal range
            val minSince = sameFingerprint.mapNotNull { it.since }.minOrNull()
            val maxUntil = sameFingerprint.mapNotNull { it.until }.maxOrNull()
            val maxLimit = sameFingerprint.mapNotNull { it.limit }.maxOrNull()

            // Use the first filter as base and adjust temporal constraints
            sameFingerprint.first().copy(
                since = minSince,
                until = maxUntil,
                limit = maxLimit
            )
        }
    }
}

/**
 * Represents a subscription waiting to be processed for grouping.
 */
internal data class PendingSubscription(
    val subscription: NDKSubscription,
    val relays: Set<NDKRelay>
)

/**
 * Represents a group of subscriptions sharing a single relay subscription.
 */
internal class GroupedSubscription(
    val fingerprint: String,
    private val filters: List<NDKFilter>,
    private val relays: Set<NDKRelay>,
    private val ndk: NDK
) {
    // ID for the shared relay subscription
    val relaySubscriptionId: String = "group-${UUID.randomUUID()}"

    // Individual subscriptions in this group
    private val members = ConcurrentHashMap<String, NDKSubscription>()

    /**
     * Starts the grouped relay subscription.
     */
    fun start() {
        relays.forEach { relay ->
            relay.subscribe(relaySubscriptionId, filters)
        }
    }

    /**
     * Closes the grouped relay subscription.
     */
    fun close() {
        relays.forEach { relay ->
            relay.unsubscribe(relaySubscriptionId)
        }
    }

    /**
     * Adds a subscription to this group.
     */
    fun addSubscription(subscription: NDKSubscription) {
        members[subscription.id] = subscription
    }

    /**
     * Removes a subscription from this group.
     */
    fun removeSubscription(subscriptionId: String) {
        members.remove(subscriptionId)
    }

    /**
     * Checks if the group has no members.
     */
    fun isEmpty(): Boolean = members.isEmpty()

    /**
     * Dispatches an event to all member subscriptions.
     *
     * Each subscription performs its own filter matching since grouped filters
     * may have wider temporal constraints.
     */
    fun dispatch(event: NDKEvent, relay: NDKRelay) {
        members.values.forEach { subscription ->
            if (subscription.filters.any { it.matches(event) }) {
                subscription.emit(event, relay)
            }
        }
    }
}
