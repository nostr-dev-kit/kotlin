package io.nostr.ndk

import io.nostr.ndk.account.NDKAccountStorage
import io.nostr.ndk.account.NDKCurrentUser
import io.nostr.ndk.cache.NDKCacheAdapter
import io.nostr.ndk.crypto.NDKSigner
import io.nostr.ndk.models.NDKFilter
import io.nostr.ndk.models.PublicKey
import io.nostr.ndk.outbox.NDKOutboxTracker
import io.nostr.ndk.outbox.NDKRelaySetCalculator
import io.nostr.ndk.relay.NDKPool
import io.nostr.ndk.subscription.NDKSubscription
import io.nostr.ndk.subscription.NDKSubscriptionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Main entry point for the Nostr Development Kit (NDK).
 *
 * NDK manages relay connections, subscriptions, event publishing, and account management.
 * It provides a streaming-first API where events are delivered via Kotlin Flows.
 *
 * ## Basic Usage
 * ```kotlin
 * val ndk = NDK(
 *     explicitRelayUrls = setOf("wss://relay.damus.io", "wss://nos.lol"),
 *     accountStorage = AndroidAccountStorage(context)
 * )
 *
 * // Login and start session
 * val me = ndk.login(NDKPrivateKeySigner(keyPair))
 *
 * // Access reactive session data
 * me.follows.collect { follows -> updateUI(follows) }
 *
 * // Subscribe to events
 * val subscription = ndk.subscribe(NDKFilter(kinds = setOf(1), limit = 50))
 * subscription.events.collect { event ->
 *     println("New event: ${event.content}")
 * }
 * ```
 *
 * ## Outbox Model (NIP-65)
 *
 * NDK implements the outbox model for efficient relay selection. When enabled:
 * - Subscriptions with author filters query each author's write relays
 * - Relay lists (kind 10002) are auto-tracked and cached
 * - Subscriptions dynamically add relays as relay lists are discovered
 *
 * ```kotlin
 * // Configure outbox model (enabled by default)
 * ndk.enableOutboxModel = true         // Enable/disable outbox relay selection
 * ndk.relayGoalPerAuthor = 2           // Target relays per author (default: 2)
 * ndk.autoConnectUserRelays = true     // Auto-connect to user's relays on login
 *
 * // Default outbox relays for relay list discovery
 * ndk.outboxRelayUrls.add("wss://purplepag.es")
 *
 * // Subscribe with outbox model - automatically queries author's write relays
 * val filter = NDKFilter(authors = setOf("alice", "bob"), kinds = setOf(1))
 * val sub = ndk.subscribe(filter)
 *
 * // As relay lists are discovered, subscriptions update automatically
 * ```
 *
 * @param explicitRelayUrls Set of relay URLs to connect to
 * @param signer Optional signer for signing and publishing events (deprecated, use login())
 * @param cacheAdapter Optional cache adapter for event persistence (required for outbox model caching)
 * @param accountStorage Optional storage for persisting account data
 */
class NDK(
    val explicitRelayUrls: Set<String> = emptySet(),
    val signer: NDKSigner? = null,
    val cacheAdapter: NDKCacheAdapter? = null,
    val accountStorage: NDKAccountStorage? = null
) {
    /**
     * Whether to use the outbox model for relay selection.
     * When enabled, subscriptions with author filters will query each author's write relays.
     */
    var enableOutboxModel: Boolean = true

    /**
     * Whether to automatically connect to the user's relays when a signer is set.
     * When enabled, fetches the user's relay list and adds those relays to the pool.
     */
    var autoConnectUserRelays: Boolean = true

    /**
     * Target number of relays to query per author when using outbox model.
     * Higher values increase redundancy but may slow down queries.
     */
    var relayGoalPerAuthor: Int = 2

    /**
     * URLs of relays dedicated to fetching relay lists (NIP-65).
     * These relays are queried when looking up a user's relay list.
     */
    val outboxRelayUrls: MutableSet<String> = mutableSetOf(
        "wss://purplepag.es",
        "wss://relay.nos.social"
    )
    /**
     * Lazy initialized relay pool.
     * The pool manages all relay connections and their lifecycle.
     */
    val pool: NDKPool by lazy { NDKPool(this) }

    /**
     * Lazy delegate for outbox pool, stored for lifecycle management.
     */
    private val _outboxPool = lazy {
        val pool = NDKPool(this)
        outboxRelayUrls.forEach { url ->
            pool.addRelay(url, connect = false)
        }
        pool
    }

    /**
     * Lazy initialized outbox relay pool.
     * This pool is dedicated to fetching relay lists (NIP-65) for the outbox model.
     */
    val outboxPool: NDKPool by _outboxPool

    /**
     * Lazy initialized subscription manager.
     * The manager coordinates all subscriptions and dispatches events.
     */
    internal val subscriptionManager: NDKSubscriptionManager by lazy { NDKSubscriptionManager(this) }

    /**
     * Lazy initialized outbox tracker.
     * Manages relay lists (NIP-65) and provides outbox model capabilities.
     */
    val outboxTracker: NDKOutboxTracker by lazy { NDKOutboxTracker(this) }

    /**
     * Lazy initialized relay set calculator.
     * Calculates optimal relays for subscriptions based on outbox model.
     */
    internal val relaySetCalculator: NDKRelaySetCalculator by lazy { NDKRelaySetCalculator(this) }

    /**
     * Coroutine scope for NDK operations.
     * Internal to allow components like relaySetCalculator to use it.
     */
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Account management
    private val _currentUser = MutableStateFlow<NDKCurrentUser?>(null)
    private val _accounts = MutableStateFlow<List<NDKCurrentUser>>(emptyList())
    private val _additionalSessionKinds = mutableSetOf<Int>()

    /**
     * The currently active user, or null if not logged in.
     */
    val currentUser: StateFlow<NDKCurrentUser?> = _currentUser.asStateFlow()

    /**
     * All logged-in accounts.
     */
    val accounts: StateFlow<List<NDKCurrentUser>> = _accounts.asStateFlow()

    /**
     * Registers an additional event kind to be auto-fetched for sessions.
     * Call before login() to ensure the kind is included in the session subscription.
     *
     * @param kind The event kind to auto-fetch
     */
    fun registerSessionKind(kind: Int) {
        _additionalSessionKinds.add(kind)
    }

    /**
     * Gets all registered session kinds (core + additional).
     */
    internal fun getSessionKinds(): Set<Int> {
        return setOf(
            io.nostr.ndk.nips.KIND_CONTACT_LIST,
            io.nostr.ndk.account.KIND_SESSION_MUTE_LIST,
            io.nostr.ndk.account.KIND_SESSION_RELAY_LIST,
            io.nostr.ndk.account.KIND_SESSION_BLOCKED_RELAY_LIST
        ) + _additionalSessionKinds
    }

    /**
     * Logs in with a signer and creates/activates a session.
     *
     * If storage is configured, the account is persisted.
     * If an account with this pubkey already exists, it becomes the current user.
     *
     * @param signer The signer to use for this session
     * @return The NDKCurrentUser for this session
     */
    suspend fun login(signer: NDKSigner): NDKCurrentUser {
        val pubkey = signer.pubkey

        // Check if account already exists
        val existing = _accounts.value.find { it.pubkey == pubkey }
        if (existing != null) {
            _currentUser.value = existing
            return existing
        }

        // Create new current user
        val currentUser = NDKCurrentUser(signer, this)
        _accounts.update { it + currentUser }
        _currentUser.value = currentUser

        // Start session data subscription
        currentUser.startSessionSubscription()

        // Persist if storage available
        accountStorage?.saveSigner(pubkey, signer.serialize())

        return currentUser
    }

    /**
     * Logs out the current user.
     *
     * If storage is configured, the account is removed from storage.
     */
    suspend fun logout() {
        val current = _currentUser.value ?: return
        logout(current.pubkey)
    }

    /**
     * Logs out a specific account by pubkey.
     *
     * @param pubkey The pubkey of the account to logout
     */
    suspend fun logout(pubkey: PublicKey) {
        val account = _accounts.value.find { it.pubkey == pubkey }
        account?.stopSessionSubscription()

        _accounts.update { accounts -> accounts.filter { it.pubkey != pubkey } }

        // If current user was logged out, clear or switch
        if (_currentUser.value?.pubkey == pubkey) {
            _currentUser.value = _accounts.value.firstOrNull()
        }

        // Remove from storage
        accountStorage?.deleteAccount(pubkey)
    }

    /**
     * Switches to a different account.
     *
     * @param pubkey The pubkey of the account to switch to
     * @return true if switch was successful, false if account not found
     */
    fun switchAccount(pubkey: PublicKey): Boolean {
        val account = _accounts.value.find { it.pubkey == pubkey } ?: return false
        _currentUser.value = account
        return true
    }

    /**
     * Restores accounts from storage.
     *
     * Call this on app startup to restore previously logged-in accounts.
     *
     * @return List of restored accounts
     */
    suspend fun restoreAccounts(): List<NDKCurrentUser> {
        val storage = accountStorage ?: return emptyList()

        val restoredAccounts = mutableListOf<NDKCurrentUser>()

        for (pubkey in storage.listAccounts()) {
            val signerData = storage.loadSigner(pubkey) ?: continue
            var signer = NDKSigner.deserialize(signerData) ?: continue

            // Handle deferred signers that need NDK instance
            if (signer is io.nostr.ndk.crypto.NDKDeferredRemoteSigner) {
                signer = signer.initialize(this)
            }

            val currentUser = NDKCurrentUser(signer, this)
            restoredAccounts.add(currentUser)
        }

        _accounts.value = restoredAccounts
        _currentUser.value = restoredAccounts.firstOrNull()

        // Start session subscription for the current user
        _currentUser.value?.startSessionSubscription()

        return restoredAccounts
    }

    /**
     * Connects to all explicit relays and outbox relays.
     * Both pools are connected in parallel for faster startup.
     *
     * @param timeoutMs Maximum time to wait for connections (default: 5000ms)
     */
    suspend fun connect(timeoutMs: Long = 5000) {
        // Add explicit relays to main pool
        explicitRelayUrls.forEach { url ->
            pool.addRelay(url, connect = true)
        }

        // Connect both pools in parallel
        coroutineScope {
            launch { pool.connect(timeoutMs) }
            launch { outboxPool.connect(timeoutMs) }
        }
    }

    /**
     * Creates a subscription with a single filter.
     *
     * @param filter The filter to match events against
     * @return A new subscription that will emit matching events
     */
    fun subscribe(filter: NDKFilter): NDKSubscription {
        return subscribe(listOf(filter))
    }

    /**
     * Creates a subscription with multiple filters.
     * Events matching any of the filters will be emitted.
     *
     * If a cache adapter is configured, cached events are emitted first,
     * followed by events from relays (cache-first strategy).
     *
     * When outbox model is enabled and filters contain authors, the subscription
     * will use the authors' write relays instead of all connected relays.
     *
     * @param filters List of filters to match events against
     * @return A new subscription that will emit matching events
     */
    fun subscribe(filters: List<NDKFilter>): NDKSubscription {
        val subscription = subscriptionManager.subscribe(filters)

        // Load cached events first (cache-first strategy)
        subscription.loadFromCache()

        // Calculate optimal relays based on outbox model (uses runBlocking for cache lookup)
        val relays = runBlocking {
            relaySetCalculator.calculateRelaysForFilters(filters)
        }

        // Start subscription on calculated relays
        subscription.start(relays)

        // Track subscription for dynamic relay updates if outbox model enabled
        if (enableOutboxModel) {
            trackSubscriptionForRelayUpdates(subscription, filters)
        }

        return subscription
    }

    /**
     * Tracks a subscription for dynamic relay updates.
     * When relay lists are discovered for authors in the filter,
     * the subscription is updated to include those relays.
     */
    private fun trackSubscriptionForRelayUpdates(subscription: NDKSubscription, filters: List<NDKFilter>) {
        val authors = filters.flatMap { it.authors ?: emptySet() }.toSet()
        if (authors.isEmpty()) return

        val job = scope.launch {
            outboxTracker.onRelayListDiscovered.collect { (pubkey, relayList) ->
                if (pubkey in authors) {
                    val newRelays = relayList.writeRelays.mapNotNull { url ->
                        if (subscription.hasRelay(url)) null
                        else pool.getRelay(url) ?: pool.addTemporaryRelay(url)
                    }
                    if (newRelays.isNotEmpty()) {
                        subscription.addRelays(newRelays.toSet())
                    }
                }
            }
        }
        subscription.setRelayUpdateJob(job)
    }

    /**
     * Publishes an event to all connected relays.
     *
     * @param event The event to publish
     * @return List of results from each relay (success or failure)
     */
    suspend fun publish(event: io.nostr.ndk.models.NDKEvent): List<Result<Unit>> {
        return pool.connectedRelays.value.map { relay ->
            relay.publish(event)
        }
    }

    /**
     * Triggers reconnection for all disconnected relays.
     * Useful when network connectivity is restored.
     *
     * @param ignoreDelay If true, bypasses exponential backoff delays
     */
    fun reconnect(ignoreDelay: Boolean = false) {
        pool.reconnectAll(ignoreDelay)
    }

    /**
     * Closes the NDK instance and releases all resources.
     *
     * This method:
     * - Disconnects from all relays (both main pool and outbox pool)
     * - Cancels all reconnection attempts
     * - Clears all subscriptions
     * - Cancels all pending async operations (relay list fetches, etc.)
     *
     * After calling close(), this NDK instance should not be used.
     * Create a new instance if needed.
     */
    fun close() {
        // Cancel all async operations
        scope.cancel()

        // Close main pool
        pool.close()

        // Close outbox pool if it was initialized
        if (_outboxPool.isInitialized()) {
            outboxPool.close()
        }
    }
}
