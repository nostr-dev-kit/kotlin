# Active Follows Feed Filtering

## Overview

Content feeds (Home, Images, Videos) now filter content by followed users. When exploring a specific relay, content is shown without author filtering.

## Architecture

### NDK.activeFollows

A reactive `StateFlow<Set<PublicKey>>` that provides the appropriate set of authors for feed filtering:

- **Logged in**: Returns `currentUser.follows`
- **Not logged in**: Fetches and returns follows of fallback npub (`npub1l2vyh47mk2p0qlsku7hg0vn29faehy9hy34ygaclpn66ukqp3afqutajft`)

Implementation in `NDK.kt`:
```kotlin
val activeFollows: StateFlow<Set<PublicKey>> = _currentUser
    .flatMapLatest { user ->
        user?.follows ?: _fallbackFollows
    }
    .stateIn(scope, SharingStarted.Eagerly, emptySet())
```

### Feed Filtering Logic

Each feed ViewModel applies authors filtering based on relay mode:

```kotlin
val authors = when (relayFilterMode) {
    is RelayFilterMode.SingleRelay -> null  // No filter when exploring relay
    is RelayFilterMode.AllRelays -> ndk.activeFollows.value.ifEmpty { null }
}

val filter = NDKFilter(
    kinds = setOf(...),
    authors = authors,
    limit = ...
)
```

### RelayFilterState

Moved to shared location: `com.example.chirp.models.RelayFilterState`

```kotlin
sealed class RelayFilterMode {
    data object AllRelays : RelayFilterMode()
    data class SingleRelay(val relay: NDKRelay) : RelayFilterMode()
}
```

## Files Modified

- `ndk-core/src/main/kotlin/io/nostr/ndk/NDK.kt` - Added `activeFollows`, fallback follows fetching
- `chirp/.../models/RelayFilterState.kt` - New shared location
- `chirp/.../home/HomeViewModel.kt` - Uses activeFollows
- `chirp/.../home/HomeScreen.kt` - Updated imports
- `chirp/.../images/ImageFeedViewModel.kt` - Added relay filter and activeFollows
- `chirp/.../images/ImageFeedScreen.kt` - Added relay selector UI
- `chirp/.../videos/VideoFeedViewModel.kt` - Added relay filter and activeFollows

## Lifecycle

1. `NDK.connect()` - If no user logged in, fetches fallback pubkey's follows
2. `NDK.login()` - Cancels fallback fetch, uses user's follows
3. `NDK.logout()` - If no users remain, fetches fallback follows again

## Usage

```kotlin
// In ViewModel
val authors = if (exploringRelay) null else ndk.activeFollows.value.ifEmpty { null }
val filter = NDKFilter(kinds = setOf(1), authors = authors, limit = 100)
val subscription = ndk.subscribe(filter)
```
