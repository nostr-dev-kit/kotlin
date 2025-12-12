# ndk-compose

Compose UI library for NDK (Nostr Development Kit). Provides reusable, customizable composables for rendering Nostr content with rich formatting.

## Overview

`ndk-compose` is a UI layer for NDK that handles:
- User profile display (avatars, names)
- Rich content rendering (mentions, links, hashtags, media, events)
- OpenGraph link previews
- Multiple renderer variants for different UI styles
- Customizable callbacks and settings

## Installation

Add to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":ndk-compose"))
}
```

## Quick Start

### Basic Content Rendering

```kotlin
import io.nostr.ndk.compose.content.RenderedContent
import io.nostr.ndk.compose.content.ContentCallbacks

@Composable
fun NoteContent(note: NDKEvent, ndk: NDK) {
    RenderedContent(
        ndk = ndk,
        event = note,
        callbacks = ContentCallbacks(
            onUserClick = { pubkey -> /* Navigate to profile */ },
            onEventClick = { eventId -> /* Navigate to thread */ },
            onHashtagClick = { tag -> /* Search hashtag */ },
            onLinkClick = { url -> /* Open browser */ },
            onMediaClick = { urls, index -> /* Open gallery */ }
        )
    )
}
```

### User Components

```kotlin
import io.nostr.ndk.compose.user.*

// Display name only
UserDisplayName(
    pubkey = user.pubkey,
    ndk = ndk,
    style = MaterialTheme.typography.titleMedium
)

// Avatar only
UserAvatar(
    pubkey = user.pubkey,
    ndk = ndk,
    size = 40.dp
)

// Avatar + name together
UserInfo(
    pubkey = user.pubkey,
    ndk = ndk,
    avatarSize = 32.dp,
    nameStyle = MaterialTheme.typography.bodyMedium
)
```

## Renderer Variants

### Event Mentions

| Style | Description | Use Case |
|-------|-------------|----------|
| `DEFAULT` | Card with author and content preview | General timeline |
| `COMPACT` | Just the event ID | Minimal UI |

### Articles (Kind 30023)

| Style | Description | Use Case |
|-------|-------------|----------|
| `CARD` | Full card with image, title, summary, author | Featured articles |
| `COMPACT` | Title and author only | Article lists |
| `MINIMAL` | Just the title with emoji | Inline references |

### Links

| Style | Description | Use Case |
|-------|-------------|----------|
| `DEFAULT` | Basic underlined link | Simple text display |
| `CARD` (with previews) | OpenGraph preview card | Rich social feed |

## Customization

### Renderer Settings

```kotlin
import io.nostr.ndk.compose.content.ContentRendererSettings
import io.nostr.ndk.compose.content.RendererStyle

val settings = ContentRendererSettings(
    // Event rendering
    eventMentionStyle = RendererStyle.DEFAULT,
    articleStyle = RendererStyle.CARD,

    // Link rendering
    linkStyle = RendererStyle.CARD,
    enableLinkPreviews = true,

    // User mentions
    mentionStyle = RendererStyle.DEFAULT,
    showAvatarsInMentions = true,

    // Media
    mediaStyle = RendererStyle.DEFAULT,
    autoLoadImages = true,
    maxImagesInGallery = 4
)

RenderedContent(
    ndk = ndk,
    event = note,
    settings = settings
)
```

### Custom Renderers

Register custom renderers for specific content types:

```kotlin
import io.nostr.ndk.content.ContentRendererRegistry

val customRegistry = ContentRendererRegistry().apply {
    register<ContentSegment.Link> { segment ->
        // Your custom link renderer
        MyCustomLinkRenderer(segment)
    }
}

RenderedContent(
    ndk = ndk,
    event = note,
    registry = customRegistry
)
```

## Features

### OpenGraph Link Previews

Automatically fetches and displays rich previews for links:

```kotlin
// Enable link previews
val settings = ContentRendererSettings(
    linkStyle = RendererStyle.CARD,
    enableLinkPreviews = true
)
```

Features:
- Async fetching with OkHttp
- In-memory caching (prevents duplicate requests)
- Automatic fallback to basic link renderer
- Extracts: title, description, image, site name

### Media Rendering

Handles images and videos with:
- Single image: Full-width preview
- Multiple images: 2-column grid layout
- Loading states
- Error states
- Video play indicator
- Click to open gallery

### Profile Resolution

User components automatically:
- Fetch profiles from relays
- Show loading states (truncated pubkey)
- Cache profile data
- Update reactively when profiles load

## Architecture

### Directory Structure

```
ndk-compose/
├── user/
│   ├── UserDisplayName.kt    - Display name component
│   ├── UserAvatar.kt          - Avatar image component
│   └── UserInfo.kt            - Combined avatar + name
└── content/
    ├── RenderedContent.kt     - Main orchestrator
    ├── ContentCallbacks.kt    - Click handlers
    ├── ContentRendererSettings.kt - Configuration
    └── renderer/
        ├── mentions/Basic.kt
        ├── links/
        │   ├── Basic.kt       - Simple underlined link
        │   ├── Preview.kt     - OpenGraph preview
        │   ├── OpenGraphFetcher.kt
        │   └── OpenGraphData.kt
        ├── hashtags/Basic.kt
        ├── media/Basic.kt
        └── events/
            ├── Basic.kt       - Default event preview
            ├── Compact.kt     - Minimal event display
            ├── Article.kt     - Full article card
            ├── ArticleCompact.kt
            └── ArticleMinimal.kt
```

### How It Works

1. **Content Parsing**: NDK core parses event content into `ContentSegment` types
2. **Orchestration**: `RenderedContent` routes each segment to appropriate renderer
3. **Customization**: Settings control which renderer variant is used
4. **Callbacks**: User interactions bubble up through callbacks
5. **Profile Loading**: User components fetch and cache profile data

## Dependencies

- `ndk-core` - Core NDK functionality
- Jetpack Compose (Material3)
- Coil 3.x - Image loading
- OkHttp 5.x - OpenGraph fetching

## Design Patterns

- **Immutable data**: All settings and data classes are immutable
- **Flow-based**: Profile data exposed as StateFlow
- **Composable**: Built on Jetpack Compose
- **Extensible**: Custom renderer registration
- **Async**: Background profile fetching and OpenGraph loading

## Examples

### Minimal Note Card

```kotlin
@Composable
fun MinimalNoteCard(note: NDKEvent, ndk: NDK) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            UserInfo(pubkey = note.pubkey, ndk = ndk)
            Spacer(modifier = Modifier.height(8.dp))
            RenderedContent(
                ndk = ndk,
                event = note,
                textStyle = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
```

### Article Feed

```kotlin
@Composable
fun ArticleFeed(articles: List<NDKEvent>, ndk: NDK) {
    LazyColumn {
        items(articles) { article ->
            RenderedContent(
                ndk = ndk,
                event = article,
                settings = ContentRendererSettings(
                    articleStyle = RendererStyle.CARD
                ),
                callbacks = ContentCallbacks(
                    onEventClick = { id -> /* Navigate */ }
                )
            )
        }
    }
}
```

### Compact Timeline

```kotlin
@Composable
fun CompactTimeline(events: List<NDKEvent>, ndk: NDK) {
    val settings = ContentRendererSettings(
        eventMentionStyle = RendererStyle.COMPACT,
        linkStyle = RendererStyle.DEFAULT,
        enableLinkPreviews = false,
        showAvatarsInMentions = false
    )

    LazyColumn {
        items(events) { event ->
            RenderedContent(ndk = ndk, event = event, settings = settings)
        }
    }
}
```

## Performance Considerations

- **Profile caching**: User profiles cached in NDK core's StateFlow
- **OpenGraph caching**: In-memory ConcurrentHashMap cache
- **Lazy loading**: Images loaded on-demand with Coil
- **Recomposition**: Uses `remember` for stable references

## License

Same as NDK parent project.
