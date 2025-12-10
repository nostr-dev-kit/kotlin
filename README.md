# NDK for Android

Production-quality Nostr Development Kit for Android, matching the quality and API design of NDKSwift and NDK TypeScript.

## Features

- **Streaming-First Architecture**: Events flow as `Flow<NDKEvent>`, displaying events as they arrive
- **Subscription-Centric**: All data access through reactive subscriptions
- **Pluggable Architecture**: Cache adapters, signers, relay policies as interfaces
- **Single Dispatch Point**: All events route through one place for cross-subscription reactivity
- **Extended NIP Support**: NIP-01, 02, 05, 10, 23, 25, 51, 57, 65

## Installation

Add to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.nostr:ndk-core:1.0.0")
}
```

## Quick Start

### Initialize NDK

```kotlin
val ndk = NDK(
    explicitRelays = setOf(
        "wss://relay.damus.io",
        "wss://nos.lol",
        "wss://relay.nostr.band"
    )
)
```

### Connect and Subscribe

```kotlin
// Connect to relays
ndk.connect()

// Create a subscription for text notes
val filter = NDKFilter(
    kinds = setOf(1),
    limit = 50
)

val subscription = ndk.subscribe(filter)

// Collect events as they arrive
subscription.events.collect { event ->
    println("New note: ${event.content}")
}
```

### With Jetpack Compose

```kotlin
@Composable
fun NoteFeed() {
    val ndk = remember { NDK(explicitRelays = setOf("wss://relay.damus.io")) }
    val subscription = remember {
        ndk.subscribe(NDKFilter(kinds = setOf(1), limit = 50))
    }

    // Use Flow extensions for Compose
    val events by subscription.events
        .eventsAccumulated()
        .collectAsState(initial = emptyList())

    LazyColumn {
        items(events) { event ->
            NoteCard(event)
        }
    }
}
```

### Publish Events

```kotlin
// Create a signer from private key
val keyPair = NDKKeyPair.fromNsec("nsec1...")
val signer = NDKPrivateKeySigner(keyPair)

// Build and sign a text note
val event = ndk.textNote()
    .content("Hello Nostr!")
    .hashtag("nostr")
    .build(signer)

// Publish to connected relays
ndk.publish(event)
```

### User Profiles

```kotlin
val user = ndk.user("pubkey_hex")

// Fetch and observe profile
user.fetchProfile()
user.profile.collect { profile ->
    println("Name: ${profile?.bestName}")
    println("NIP-05: ${profile?.nip05}")
}

// Get user's notes
val notesSubscription = user.notes()
notesSubscription.events.collect { note ->
    println(note.content)
}
```

### Working with Threads (NIP-10)

```kotlin
import io.nostr.ndk.nips.*

val event: NDKEvent = // ... received event

// Get threading information
val threadInfo = event.threadInfo

// Access root event (with marker or positional fallback)
threadInfo?.root?.eventId?.let { rootId ->
    println("Root: $rootId")
}

// Access direct reply parent
threadInfo?.replyTo?.eventId?.let { parentId ->
    println("Reply to: $parentId")
}
```

### Reactions (NIP-25)

```kotlin
// Check reaction type
if (event.isLike) {
    println("Liked!")
} else if (event.isDislike) {
    println("Disliked")
} else if (event.isCustomReaction) {
    println("Reacted with: ${event.content}")
}

// Create a reaction
val reaction = ndk.reaction()
    .target(targetEvent)
    .like()
    .build(signer)
```

### Long-Form Articles (NIP-23)

```kotlin
// Access article properties
println("Title: ${event.articleTitle}")
println("Summary: ${event.articleSummary}")
println("Topics: ${event.articleTopics}")

// Create an article
val article = ndk.article()
    .identifier("my-first-article")
    .title("Getting Started with Nostr")
    .content("# Introduction\n\nNostr is...")
    .summary("A beginner's guide to Nostr")
    .topic("nostr")
    .topic("tutorial")
    .build(signer)
```

### Contact Lists (NIP-02)

```kotlin
// Read contacts
val contacts = event.contacts
contacts.forEach { contact ->
    println("Following: ${contact.pubkey}")
    println("Petname: ${contact.petname}")
}

// Check if following
if (event.isFollowing(somePubkey)) {
    println("Following!")
}

// Create contact list
val contactList = ndk.contactList()
    .follow("pubkey1", "wss://relay.com", "alice")
    .follow("pubkey2")
    .build(signer)
```

### NIP-05 Verification

```kotlin
// Verify a NIP-05 identifier
val result = Nip05Verifier.verify("user@example.com", expectedPubkey)
if (result) {
    println("Verified!")
}

// Lookup pubkey from NIP-05
val pubkey = Nip05Verifier.lookup("user@example.com")
```

### Lists (NIP-51)

```kotlin
// Parse list items
val items = event.listItems
items.forEach { item ->
    when (item) {
        is ListItem.Pubkey -> println("Person: ${item.pubkey}")
        is ListItem.Event -> println("Event: ${item.eventId}")
        is ListItem.Word -> println("Word: ${item.word}")
        is ListItem.Hashtag -> println("Tag: ${item.hashtag}")
    }
}
```

## Testing

NDK includes testing utilities for easier unit testing:

```kotlin
// Create test events easily
val generator = EventGenerator()
val note = generator.textNote("Test content")
val reply = generator.reply("Reply", note)
val feed = generator.feed(count = 50)

// Mock relay for testing without network
val relay = RelayMock("wss://mock.relay")
relay.scenario().subscriptionWithEvents("sub-1", events)

// Test helpers
note.assertKind(1)
note.assertContentContains("Test")
filter.assertMatches(note)
```

## Project Structure

```
ndk-android/
├── ndk-core/                    # Core NDK library
│   └── src/main/kotlin/io/nostr/ndk/
│       ├── NDK.kt               # Main entry point
│       ├── models/              # NDKEvent, NDKFilter, NDKTag
│       ├── crypto/              # NDKKeyPair, NDKSigner, encryption
│       ├── relay/               # NDKRelay, NDKPool, WebSocket
│       ├── subscription/        # NDKSubscription, grouping
│       ├── cache/               # Cache adapters
│       ├── outbox/              # NIP-65 outbox model
│       ├── nips/                # NIP-01 through NIP-57 extensions
│       ├── user/                # NDKUser, UserProfile
│       ├── builders/            # Event builders
│       ├── compose/             # Compose Flow extensions
│       └── test/                # Testing utilities
│
└── sample-app/                  # Sample Android application
```

## Architecture

```
Application Layer (Compose UI)
        ↓
NDK Public API (NDK, NDKSubscription, NDKUser, NDKEvent)
        ↓
Subscription Management (grouping, deduplication, dispatch)
        ↓
Relay Pool & Outbox (NDKPool, NDKRelay, NDKOutboxTracker)
        ↓
Network & Connection (WebSocket, keepalive, reconnection)
        ↓
Cache Adapter (InMemoryCacheAdapter, Room)
        ↓
Cryptography (secp256k1-kmp, Schnorr, NIP-04/44)
```

## Supported NIPs

| NIP | Description | Status |
|-----|-------------|--------|
| 01 | Basic protocol | Implemented |
| 02 | Contact List | Implemented |
| 04 | Encrypted DMs | Implemented |
| 05 | DNS Identifier | Implemented |
| 10 | Thread markers | Implemented |
| 19 | bech32 encoding | Implemented |
| 23 | Long-form content | Implemented |
| 25 | Reactions | Implemented |
| 44 | Versioned encryption | Implemented |
| 51 | Lists | Implemented |
| 57 | Zaps | Implemented |
| 65 | Relay List Metadata | Implemented |

## Build Configuration

- **Kotlin**: 2.1.0
- **Android Gradle Plugin**: 8.7.2
- **Compile SDK**: 35
- **Min SDK**: 26
- **Java Target**: 17

## Dependencies

- **secp256k1-kmp** (0.21+): BIP-340 Schnorr signatures
- **OkHttp 5.x**: WebSocket with coroutine support
- **Jackson**: JSON serialization
- **LazySodium**: NIP-44 ChaCha20-Poly1305 encryption
- **Kotlin Coroutines**: Flow/StateFlow/SharedFlow

## Building

```bash
# Build all modules
./gradlew build

# Build only ndk-core library
./gradlew :ndk-core:build

# Run tests
./gradlew :ndk-core:test

# Build sample app
./gradlew :sample-app:assembleDebug
```

## License

MIT License
