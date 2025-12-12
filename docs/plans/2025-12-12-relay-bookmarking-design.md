# Relay Bookmarking Design (Issue #6)

**Date:** 2025-12-12
**Feature:** NIP-51 Relay Sets (kind 30002) for content browsing

## Overview

Add support for relay bookmarking using NIP-51 kind 30002 events. Relay bookmarks are collections of relays that act as content filters, allowing users to browse content from specific relay sets like "magazines" or "topics."

## Two Separate Concerns

### 1. App Relays (Existing - No Changes)
- `RelayPreferencesRepository` with DataStore remains unchanged
- These are the relays the app connects to for normal operations
- Used for publishing, fetching user's feed, general connectivity

### 2. Relay Bookmarks (New Feature)
- Kind 30002 events (NIP-51 Relay sets)
- Collections of relays that work as content filters
- Examples: "Tech relays", "Art relays", "Privacy relays"
- When selected, app fetches content exclusively from those relays

## Core Mechanism

- Relay sets are Nostr events (kind 30002) stored via NDK
- No additional DataStore - NDK's event cache handles persistence
- When a relay set is selected, app creates REQ subscriptions only to those relays
- Default mode ("All relays") uses normal app relays from DataStore

## Content Scope

- All content types: kind 1 posts, kind 30023 articles, media, etc.
- Filtered only by relay source

## Discovery

- User's own relay sets (kind 30002 events they authored)
- Relay sets from people they follow (via social graph)
- No "following" mechanism - just pick from dropdown and view

## Data Layer

### NIP-51 Kind 30002 Structure

```
kind: 30002
tags:
  - ["d", "<set-identifier>"]           // unique ID
  - ["relay", "<wss://relay.url>"]      // one per relay
  - ["title", "<display-name>"]         // optional
  - ["description", "<text>"]           // optional
  - ["image", "<url>"]                  // optional
content: ""
```

### Code Additions

**Nip51.kt:**
```kotlin
const val KIND_RELAY_SET = 30002

val NDKEvent.isRelaySet: Boolean
    get() = kind == KIND_RELAY_SET

val NDKEvent.relaySetRelays: List<String>
    get() = tagsWithName("relay").mapNotNull { it.values.getOrNull(0) }
```

Update `isList` to include relay sets.

**RelaySet.kt:**
```kotlin
data class RelaySet(
    val identifier: String,
    val title: String?,
    val description: String?,
    val image: String?,
    val relays: List<String>,
    val author: PublicKey,
    val createdAt: Timestamp
) {
    companion object {
        fun fromEvent(event: NDKEvent): RelaySet
    }
}
```

## Business Logic Layer

**RelaySetRepository:**
```kotlin
@Singleton
class RelaySetRepository @Inject constructor(
    private val ndk: NDK
) {
    // Fetch user's own relay sets
    fun fetchOwnRelaySets(pubkey: PublicKey): Flow<List<RelaySet>>

    // Fetch relay sets from follows
    fun fetchRelaySetsFromFollows(follows: List<PublicKey>): Flow<List<RelaySet>>

    // Create/update a relay set
    suspend fun publishRelaySet(
        identifier: String,
        title: String?,
        description: String?,
        image: String?,
        relays: List<String>
    ): Result<NDKEvent>

    // Delete a relay set
    suspend fun deleteRelaySet(identifier: String): Result<Unit>
}
```

Implementation:
- Use NDK subscription API to fetch kind 30002 events
- Filter by author(s) for own vs follows' sets
- Publish using NDK event creation and signing
- NDK handles caching automatically
- Return Flow for reactive updates

## UI Layer

### 1. Dropdown Selector (Toolbar)

Dropdown/spinner in app toolbar showing:
- "All relays" (default - uses app relays)
- User's relay sets
- Followed users' relay sets (e.g., "Bitcoin news by @alice")

Triggers content resubscription when changed.

### 2. RelaySetEditorScreen

Full-screen editor:
- Text field: Set name (title tag)
- Text field: Description (optional)
- Text field: Image URL (optional)
- Relay list with add/remove:
  - Text input for new relay URL
  - List of current relays with delete buttons
- Save button: Publishes kind 30002 event
- Delete button: For existing sets

### 3. State Management

```kotlin
data class RelayFilterState(
    val mode: RelayFilterMode,
    val activeRelaySet: RelaySet?
)

sealed class RelayFilterMode {
    object AllRelays
    data class RelaySetFilter(val relaySet: RelaySet)
}
```

### 4. Feed Integration

When `RelayFilterState` changes:
- Close existing subscriptions
- Create new REQ to appropriate relays
- Update content stream

## Error Handling

### Error Scenarios

1. **No relay sets:** Empty state, prompt to create or browse follows' sets
2. **Publishing fails:** Show error toast, NDK queues for retry
3. **Invalid relay URLs:** Validate before adding, show validation error
4. **Selected relay set deleted:** Fallback to "All relays", show toast
5. **Empty relay set:** Allow creation, show empty feed state
6. **Unavailable relays:** Show loading/timeout states

### Validation Rules

- Relay set identifier: non-empty, unique per user
- Relay URLs: valid websocket format (wss:// or ws://)
- Title: optional, max 100 chars

## Testing Strategy

**Unit tests:**
- `RelaySet.fromEvent()` parsing
- NIP-51 extensions for kind 30002
- `RelaySetRepository` methods (mocked NDK)
- Relay URL validation

**Integration tests:**
- Publishing relay set events
- Fetching own and follows' relay sets
- Feed subscription changes

**UI tests:**
- Creating/editing relay sets
- Switching relay sets in dropdown
- Empty states and error handling

## Implementation Steps

1. **Data layer:**
   - Add `KIND_RELAY_SET` to `Nip51.kt`
   - Add extensions: `isRelaySet`, `relaySetRelays`
   - Create `RelaySet.kt` with `fromEvent()`
   - Write unit tests

2. **Repository:**
   - Create `RelaySetRepository`
   - Implement fetch methods with NDK
   - Implement publish/delete methods
   - Add tests

3. **UI - Editor:**
   - Create `RelaySetEditorScreen.kt`
   - Create `RelaySetEditorViewModel.kt`
   - Add navigation route
   - Implement validation

4. **UI - Dropdown & Feed:**
   - Add `RelayFilterState` to main ViewModel
   - Add dropdown to toolbar
   - Update feed subscription logic
   - Handle state transitions

5. **Polish:**
   - Error handling and empty states
   - Loading indicators
   - Validation messages
