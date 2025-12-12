# Developer Tools & Internal Visibility

NDK-Android provides deep visibility into its internals for debugging, monitoring, and building developer tools. This document covers the APIs for inspecting NostrDB, relay connections, subscriptions, and validation statistics.

## NostrDB Statistics

NostrDB (the embedded LMDB-based cache) exposes detailed statistics about stored events and database utilization.

### Accessing Stats

```kotlin
val cache = ndk.cacheAdapter
if (cache is NostrDBCacheAdapter) {
    val stats = cache.getStats()
    val dbSize = cache.getDatabaseSize()
    val dbPath = cache.getDatabasePath()
}
```

### NdbStats Structure

```kotlin
data class NdbStats(
    val databases: List<NdbDatabaseStats>,  // Per-database stats
    val commonKinds: List<NdbKindStats>,    // Per-kind event counts
    val otherKinds: NdbStatCounts           // Events of uncommon kinds
) {
    val totalEvents: Long      // Sum of all events
    val totalStorageSize: Long // Total bytes used
}

data class NdbDatabaseStats(
    val name: String,          // e.g., "note", "profile", "id"
    val counts: NdbStatCounts
)

data class NdbKindStats(
    val name: String,          // e.g., "Profiles", "Text Notes", "Follows"
    val counts: NdbStatCounts
)

data class NdbStatCounts(
    val count: Long,           // Number of entries
    val keySize: Long,         // Total key bytes
    val valueSize: Long        // Total value bytes
)
```

### Database Types

NostrDB maintains these internal databases:

| Database | Description |
|----------|-------------|
| `note` | Raw event data |
| `meta` | Event metadata |
| `profile` | Parsed profile content |
| `id` | Event ID index |
| `pubkey` | Author pubkey index |
| `pubkey_kind` | Author+kind compound index |
| `tag` | Tag index for queries |
| `tag8` | 8-byte tag prefix index |
| `tag_value` | Tag value index |
| `text` | Full-text search index |
| `word` | Word index for search |
| `ndb_meta` | Internal metadata |
| `profile_pk` | Profile by pubkey index |
| `profile_search` | Profile search index |
| `kind` | Kind index |
| `timestamp` | Created_at index |

### Common Event Kinds Tracked

Stats are collected for these frequently-used kinds:

- Profiles (kind 0)
- Text Notes (kind 1)
- Follows (kind 3)
- DMs (kind 4)
- Deletion (kind 5)
- Repost (kind 6)
- Reaction (kind 7)
- Zap (kind 9735)
- Zap Request (kind 9734)
- Mute List (kind 10000)
- Relay List (kind 10002)
- Bookmarks (kind 10003)
- Long-form (kind 30023)
- Status (kind 30315)

Events of other kinds are aggregated into `otherKinds`.

### JNI Implementation

The stats are retrieved via JNI calling nostrdb's `ndb_stat()` function:

```c
// nostrdb.h
int ndb_stat(struct ndb *ndb, struct ndb_stat *stat);

struct ndb_stat {
    struct ndb_stat_counts dbs[NDB_DBS];
    struct ndb_stat_counts common_kinds[NDB_CKIND_COUNT];
    struct ndb_stat_counts other_kinds;
};
```

---

## Relay Statistics

Every relay connection tracks detailed statistics about messages, events, and performance.

### Per-Relay Statistics

```kotlin
val relay = ndk.pool.getRelay("wss://relay.example.com")
val stats: NDKRelayStatisticsSnapshot = relay.getStatistics()

// Message counts
stats.messagesReceived      // Total WebSocket messages
stats.messagesSent          // Messages sent to relay
stats.eventsReceived        // EVENT messages received
stats.eosesReceived         // EOSE (end of stored events) count

// Data transfer
stats.bytesReceived         // Total bytes downloaded
stats.bytesSent             // Total bytes uploaded

// Subscriptions
stats.activeSubscriptions   // Currently open subscriptions

// Deduplication
stats.duplicateEvents       // Events already seen from other relays
stats.uniqueEvents          // First-time events from this relay
stats.uniqueMessageRate     // Ratio of unique to total (0.0-1.0)

// Validation (see Trust-Based Validation below)
stats.validatedEvents       // Events with verified signatures
stats.nonValidatedEvents    // Events trusted without verification
stats.validationRate        // Current verification frequency (0.1-1.0)
```

### Aggregated Pool Statistics

```kotlin
val poolStats: AggregatedRelayStatistics = ndk.pool.getAggregatedStatistics()

poolStats.totalMessagesReceived
poolStats.totalEventsSent
poolStats.totalActiveSubscriptions
poolStats.totalValidatedEvents
poolStats.relayCount
```

### NIP-11 Relay Information

Relays can publish metadata about themselves per NIP-11:

```kotlin
val relay = ndk.pool.getRelay("wss://relay.example.com")

// Fetch NIP-11 info (async)
relay.fetchNip11Info()

// Access cached info
val nip11: Nip11RelayInformation? = relay.nip11Info

nip11?.name              // Relay name
nip11?.description       // Description
nip11?.pubkey            // Operator's pubkey
nip11?.contact           // Contact info
nip11?.supportedNips     // List of supported NIPs
nip11?.software          // Software name
nip11?.version           // Software version

// Limitations
nip11?.limitation?.maxMessageLength
nip11?.limitation?.maxSubscriptions
nip11?.limitation?.maxFilters
nip11?.limitation?.maxEventTags
nip11?.limitation?.maxContentLength
nip11?.limitation?.authRequired
nip11?.limitation?.paymentRequired

// Retention policies
nip11?.retention         // List of retention rules per kind

// Fees
nip11?.fees?.admission   // One-time fees
nip11?.fees?.subscription // Recurring fees
nip11?.fees?.publication  // Per-event fees
```

---

## Trust-Based Signature Validation

NDK uses an adaptive trust system to optimize signature verification. Instead of verifying every event (CPU intensive), it samples based on relay trustworthiness.

### How It Works

1. **Initial state**: All events from a relay are verified (100% validation rate)
2. **Building trust**: As valid signatures are confirmed, verification frequency decreases
3. **Minimum floor**: Verification never drops below 10% (always spot-checking)
4. **Trust reset**: Invalid signatures reset the relay to 100% verification

### Accessing Validation Stats

```kotlin
val stats = relay.getStatistics()

stats.validatedEvents    // Events with cryptographic verification
stats.nonValidatedEvents // Events trusted based on relay reputation
stats.validationRate     // Current sampling rate (0.1 to 1.0)
```

### Per-Relay Trust Levels

```kotlin
// Get validation info for all relays
val allRelays = ndk.pool.availableRelays.value + ndk.outboxPool.availableRelays.value

allRelays.forEach { relay ->
    val stats = relay.getStatistics()
    val total = stats.validatedEvents + stats.nonValidatedEvents

    if (total > 0) {
        val trustLevel = 1.0f - stats.validationRate  // Higher = more trusted
        println("${relay.url}: ${(trustLevel * 100).toInt()}% trusted")
    }
}
```

### Why This Matters

- **Performance**: Signature verification is expensive (secp256k1 operations)
- **Security**: Bad actors can't exploit trust - invalid sigs reset to full verification
- **Transparency**: Apps can display trust levels to users

---

## Relay Connection States

```kotlin
enum class NDKRelayState {
    DISCONNECTED,    // Not connected
    CONNECTING,      // WebSocket handshake in progress
    CONNECTED,       // Connected, ready for messages
    AUTHENTICATING,  // NIP-42 auth in progress
    AUTHENTICATED,   // Auth completed successfully
    RECONNECTING,    // Temporary disconnect, will retry
    FLAPPING         // Unstable connection (repeated failures)
}

// Check state
val relay = ndk.pool.getRelay("wss://relay.example.com")
val state: NDKRelayState = relay.state.value

// Observe state changes
relay.state.collect { newState ->
    println("Relay state: $newState")
}
```

---

## Building Debug UIs

### Example: Relay Monitor

```kotlin
@Composable
fun RelayMonitor(ndk: NDK) {
    val relays = ndk.pool.availableRelays.collectAsState()

    LazyColumn {
        items(relays.value) { relay ->
            val stats = relay.getStatistics()

            ListItem(
                headlineContent = { Text(relay.url) },
                supportingContent = {
                    Text("${stats.messagesReceived} msgs, ${stats.uniqueMessageRate * 100}% unique")
                },
                trailingContent = {
                    ConnectionBadge(relay.state.value)
                }
            )
        }
    }
}
```

### Example: NostrDB Stats Dashboard

```kotlin
@Composable
fun NostrDBDashboard(ndk: NDK) {
    val cache = ndk.cacheAdapter as? NostrDBCacheAdapter ?: return
    var stats by remember { mutableStateOf<NdbStats?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            stats = cache.getStats()
            delay(3000)
        }
    }

    stats?.let { s ->
        Text("Total events: ${s.totalEvents}")
        Text("Storage: ${s.totalStorageSize / 1024 / 1024} MB")

        s.commonKinds.forEach { kind ->
            Text("${kind.name}: ${kind.counts.count}")
        }
    }
}
```

---

## Reference Implementation

The Chirp sample app includes a full Developer Tools implementation:

- `DeveloperToolsScreen.kt` - Hub navigation
- `RelayMonitorScreen.kt` - Relay list with detail sheet
- `NostrDBStatsScreen.kt` - Database statistics
- `SubscriptionsScreen.kt` - Subscription and validation stats

Navigate to: **Settings → Developer Tools**
