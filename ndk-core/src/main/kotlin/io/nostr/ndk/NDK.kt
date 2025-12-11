package io.nostr.ndk

import io.nostr.ndk.account.NDKAccountStorage
import io.nostr.ndk.account.NDKCurrentUser
import io.nostr.ndk.cache.NDKCacheAdapter
import io.nostr.ndk.crypto.NDKSigner
import io.nostr.ndk.models.NDKFilter
import io.nostr.ndk.models.PublicKey
import io.nostr.ndk.outbox.NDKOutboxTracker
import io.nostr.ndk.relay.NDKPool
import io.nostr.ndk.subscription.NDKSubscription
import io.nostr.ndk.subscription.NDKSubscriptionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Main entry point for the Nostr Development Kit (NDK).
 *
 * NDK manages relay connections, subscriptions, event publishing, and account management.
 * It provides a streaming-first API where events are delivered via Kotlin Flows.
 *
 * Example usage:
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
 * @param explicitRelayUrls Set of relay URLs to connect to
 * @param signer Optional signer for signing and publishing events (deprecated, use login())
 * @param cacheAdapter Optional cache adapter for event persistence
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
     * Lazy initialized outbox relay pool.
     * This pool is dedicated to fetching relay lists (NIP-65) for the outbox model.
     */
    val outboxPool: NDKPool by lazy {
        val pool = NDKPool(this)
        outboxRelayUrls.forEach { url ->
            pool.addRelay(url, connect = false)
        }
        pool
    }

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
     * Connects to all explicit relays.
     * Adds each relay URL to the pool and initiates connections.
     *
     * @param timeoutMs Maximum time to wait for connections (default: 5000ms)
     */
    suspend fun connect(timeoutMs: Long = 5000) {
        explicitRelayUrls.forEach { url ->
            pool.addRelay(url, connect = true)
        }
        pool.connect(timeoutMs)
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
     * @param filters List of filters to match events against
     * @return A new subscription that will emit matching events
     */
    fun subscribe(filters: List<NDKFilter>): NDKSubscription {
        val subscription = subscriptionManager.subscribe(filters)

        // Load cached events first (cache-first strategy)
        subscription.loadFromCache()

        // Then subscribe to relays for new events
        subscription.start(pool.connectedRelays.value)

        return subscription
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
     * - Disconnects from all relays
     * - Cancels all reconnection attempts
     * - Clears all subscriptions
     * - Releases all coroutine scopes
     *
     * After calling close(), this NDK instance should not be used.
     * Create a new instance if needed.
     */
    fun close() {
        pool.close()
    }
}
