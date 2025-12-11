# Outbox Model & Auto-Connect to User Relays

**Date:** 2025-12-12
**Status:** Approved
**Branch:** feature/outbox-auto-connect

## Overview

Implement outbox model support in ndk-android to match NDK TypeScript functionality:

1. **Auto-connect to user relays** - When signer assigned, fetch user's relay list and add to pool
2. **Outbox-based subscription routing** - When subscribing with `authors` filter, query each author's write relays instead of broadcasting to all relays
3. **Dynamic relay updates** - Add relays to active subscriptions as relay lists are discovered

## Configuration

Add to `NDK` class:

```kotlin
var enableOutboxModel: Boolean = true
var autoConnectUserRelays: Boolean = true
var relayGoalPerAuthor: Int = 2

val outboxRelayUrls: MutableSet<String> = mutableSetOf(
    "wss://purplepag.es",
    "wss://relay.nos.social"
)
```

## Architecture

### Outbox Pool

Separate pool dedicated to relay list discovery queries:

```kotlin
// In NDK.kt
val outboxPool: NDKPool = NDKPool(this)

init {
    outboxRelayUrls.forEach { url ->
        outboxPool.addRelay(url, connect = false)
    }
}
```

### Connection Flow

On `ndk.connect()`:

```kotlin
suspend fun connect(timeoutMs: Long = 5000) {
    // Connect both pools in parallel
    coroutineScope {
        launch { outboxPool.connect(timeoutMs) }
        launch { pool.connect(timeoutMs) }
    }

    // Async user relay connection (doesn't block)
    if (signer != null && autoConnectUserRelays) {
        scope.launch { connectToUserRelays(signer!!) }
    }
}

private suspend fun connectToUserRelays(signer: NDKSigner) {
    val pubkey = signer.publicKey() ?: return
    outboxPool.connectedRelays.first { it.isNotEmpty() }

    val relayList = outboxTracker.fetchRelayList(pubkey) ?: return
    relayList.allRelays.forEach { url ->
        pool.addRelay(url)
    }
}
```

Signer assignment also triggers auto-connect:

```kotlin
var signer: NDKSigner? = null
    set(value) {
        field = value
        if (value != null && autoConnectUserRelays && pool.connectedRelays.value.isNotEmpty()) {
            scope.launch { connectToUserRelays(value) }
        }
    }
```

## Relay List Discovery

### NDKOutboxTracker Enhancements

```kotlin
class NDKOutboxTracker(private val ndk: NDK) {

    private val _relayListUpdates = MutableSharedFlow<Pair<PublicKey, RelayList>>()
    val onRelayListDiscovered: SharedFlow<Pair<PublicKey, RelayList>> = _relayListUpdates.asSharedFlow()

    // Existing: cache-only lookup
    suspend fun getRelayList(pubkey: PublicKey): RelayList? { ... }

    // New: fetch with fallback chain
    suspend fun fetchRelayList(pubkey: PublicKey): RelayList? {
        // 1. Check cache first
        getRelayList(pubkey)?.let { return it }

        // 2. Fetch kind 10002 from outbox pool
        var event = fetchKind10002(pubkey, ndk.outboxPool)

        // 3. Fallback to main pool
        if (event == null) {
            event = fetchKind10002(pubkey, ndk.pool)
        }

        // 4. Fallback to kind 3 contact list
        if (event == null) {
            return extractRelaysFromContactList(pubkey)
        }

        trackRelayList(event)
        return RelayList.fromEvent(event)
    }

    private suspend fun fetchKind10002(pubkey: PublicKey, pool: NDKPool): NDKEvent? {
        val filter = NDKFilter(kinds = listOf(10002), authors = setOf(pubkey))
        return ndk.fetchEventFromPool(filter, pool)
    }

    private suspend fun extractRelaysFromContactList(pubkey: PublicKey): RelayList? {
        val filter = NDKFilter(kinds = listOf(3), authors = setOf(pubkey))
        val event = ndk.fetchEvent(filter) ?: return null

        // Kind 3 content contains relay hints as JSON
        return RelayList.fromContactListContent(event.content)
    }

    suspend fun trackRelayList(event: NDKEvent) {
        require(event.kind == 10002)
        ndk.cacheAdapter?.store(event)

        val pubkey = event.pubkey
        val relayList = RelayList.fromEvent(event)
        _relayListUpdates.emit(pubkey to relayList)
    }
}
```

### Auto-Track Kind 10002 Events

In NDK's event handling, auto-track any relay list events:

```kotlin
internal fun onEventReceived(event: NDKEvent, relay: NDKRelay) {
    if (event.kind == 10002 && enableOutboxModel) {
        scope.launch { outboxTracker.trackRelayList(event) }
    }
    // ... dispatch to subscription
}
```

## Subscription Relay Selection

### NDKRelaySetCalculator (New Class)

```kotlin
class NDKRelaySetCalculator(private val ndk: NDK) {

    suspend fun calculateRelaysForFilters(
        filters: List<NDKFilter>
    ): Set<NDKRelay> {
        if (!ndk.enableOutboxModel) {
            return ndk.pool.connectedRelays.value
        }

        val authors = filters.flatMap { it.authors ?: emptySet() }.toSet()

        if (authors.isEmpty()) {
            return ndk.pool.connectedRelays.value
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

        // Phase 2: Trigger async fetch for uncovered authors
        if (uncoveredAuthors.isNotEmpty()) {
            ndk.scope.launch {
                uncoveredAuthors.forEach { pubkey ->
                    ndk.outboxTracker.fetchRelayList(pubkey)
                }
            }
        }

        return selectOptimalRelays(authorRelays, ndk.relayGoalPerAuthor)
    }

    private suspend fun selectOptimalRelays(
        authorRelays: Map<PublicKey, Set<String>>,
        goalPerAuthor: Int
    ): Set<NDKRelay> {
        val connectedUrls = ndk.pool.connectedRelays.value.map { it.url }.toSet()
        val selectedUrls = mutableSetOf<String>()
        val authorCoverage = mutableMapOf<PublicKey, Int>()

        // First pass: prefer already-connected relays
        for ((pubkey, relays) in authorRelays) {
            val connected = relays.intersect(connectedUrls)
            connected.take(goalPerAuthor).forEach { url ->
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
                    .forEach { selectedUrls.add(it) }
            }
        }

        // Fallback: authors with no known relays use connected relays
        val authorsWithNoRelays = authorRelays.filter { it.value.isEmpty() }.keys
        if (authorsWithNoRelays.isNotEmpty() && selectedUrls.isEmpty()) {
            selectedUrls.addAll(connectedUrls)
        }

        // Return relay instances, adding temporary relays as needed
        return selectedUrls.map { url ->
            ndk.pool.getRelay(url) ?: ndk.pool.addTemporaryRelay(url)
        }.toSet()
    }
}
```

### Subscribe Integration

```kotlin
// In NDK.kt
private val relaySetCalculator = NDKRelaySetCalculator(this)

suspend fun subscribe(
    filter: NDKFilter,
    relays: Set<NDKRelay>? = null
): NDKSubscription {
    val subscription = NDKSubscription(generateSubId(), listOf(filter), this)

    val targetRelays = relays ?: relaySetCalculator.calculateRelaysForFilters(listOf(filter))

    subscription.start(targetRelays)
    subscription.loadFromCache()

    if (enableOutboxModel && relays == null) {
        trackSubscriptionForUpdates(subscription, filter)
    }

    return subscription
}

private fun trackSubscriptionForUpdates(subscription: NDKSubscription, filter: NDKFilter) {
    val authors = filter.authors ?: return

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
```

## NDKSubscription Changes

```kotlin
class NDKSubscription(...) {

    private val _activeRelays = MutableStateFlow<Set<NDKRelay>>(emptySet())
    val activeRelays: StateFlow<Set<NDKRelay>> = _activeRelays.asStateFlow()

    private var relayUpdateJob: Job? = null

    internal fun start(relays: Set<NDKRelay>) {
        _activeRelays.value = relays
        relays.forEach { relay ->
            relay.subscribe(id, filters)
        }
    }

    fun hasRelay(url: String): Boolean {
        return _activeRelays.value.any { it.url == url }
    }

    fun addRelays(relays: Set<NDKRelay>) {
        val newRelays = relays.filter { !hasRelay(it.url) }.toSet()
        if (newRelays.isEmpty()) return

        _activeRelays.value = _activeRelays.value + newRelays
        newRelays.forEach { relay ->
            relay.subscribe(id, filters)
        }
    }

    internal fun setRelayUpdateJob(job: Job) {
        relayUpdateJob = job
    }

    fun stop() {
        relayUpdateJob?.cancel()
        relayUpdateJob = null

        _activeRelays.value.forEach { relay ->
            relay.unsubscribe(id)
        }
        _activeRelays.value = emptySet()
    }
}
```

## Files to Modify

| File | Action |
|------|--------|
| `NDK.kt` | Add config props, outboxPool, connect(), subscribe(), onEventReceived() |
| `NDKOutboxTracker.kt` | Add fetchRelayList, fallback chain, SharedFlow emissions |
| `NDKSubscription.kt` | Add activeRelays, hasRelay(), addRelays(), relayUpdateJob |
| `NDKRelaySetCalculator.kt` | **New** - relay selection algorithm |
| `RelayList.kt` | Add fromContactListContent() for kind 3 fallback |

## Testing Strategy

1. **Unit tests for NDKRelaySetCalculator**
   - Empty authors → returns connected relays
   - Authors with cached relay lists → returns write relays
   - Mixed cached/uncached → returns cached, triggers async fetch
   - relayGoalPerAuthor coverage verification

2. **Unit tests for NDKOutboxTracker**
   - Cache hit returns immediately
   - Outbox pool fetch works
   - Fallback to main pool
   - Fallback to kind 3

3. **Integration tests**
   - Auto-connect on signer assignment
   - Subscription routing uses author relays
   - Dynamic relay addition mid-subscription

## Edge Cases

1. **Outbox pool unavailable**: Falls back to main pool, then kind 3
2. **Author has no known relays**: Falls back to connected relays
3. **Author has fewer relays than goal**: Uses all available
4. **Subscription stopped during update**: relayUpdateJob cancellation prevents issues
5. **Kind 10002 received through regular subscription**: Auto-tracked via onEventReceived
