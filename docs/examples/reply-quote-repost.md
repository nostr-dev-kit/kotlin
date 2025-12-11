# Reply, Quote, and Repost Examples

This document demonstrates how to use the reply, quote, and repost builders in ndk-android.

## Overview

ndk-android provides three high-level builders for interacting with events:

- **ReplyBuilder**: Reply to any event (NIP-10 for kind 1, NIP-22 for other kinds)
- **QuoteBuilder**: Quote an event with your own commentary (NIP-18)
- **RepostBuilder**: Repost/share an event (NIP-18)

All builders follow NIP-10 and NIP-18 conventions, automatically handling proper tag construction.

## Reply to Events

### Reply to a Text Note (Kind 1)

```kotlin
// Fetch an event you want to reply to
val originalNote = ndk.fetchEvent("note1...")

// Create a reply using the extension function
val reply = originalNote.reply()
    .content("Great point! I totally agree.")
    .build(currentUser.signer)

// Publish the reply
ndk.publish(reply)
```

The reply builder automatically:
- Uses kind 1 for replies to kind 1 events
- Adds proper NIP-10 root/reply markers
- Copies thread tags from parent events
- Tags all mentioned users

### Reply to an Article (NIP-22)

```kotlin
// Fetch an article (kind 30023)
val article = ndk.fetchEvent("naddr1...")

// Create a comment/reply
val comment = article.reply()
    .content("Excellent article! Very insightful.")
    .build(currentUser.signer)

// Publish the comment
ndk.publish(comment)
```

The reply builder automatically:
- Uses kind 1111 (generic reply) for non-kind-1 events
- Creates uppercase tags (A, E, K, P) for the root event
- Creates lowercase tags (a, e, k, p) for the direct parent
- Handles nested comment threads correctly

### Reply to a Comment (Nested Replies)

```kotlin
// Fetch a comment on an article
val comment = ndk.fetchEvent("note1...")  // This is a kind 1111 comment

// Reply to the comment
val nestedReply = comment.reply()
    .content("I agree with your comment!")
    .build(currentUser.signer)

// Publish the nested reply
ndk.publish(nestedReply)
```

The builder automatically:
- Copies uppercase tags from the parent comment (preserving root reference)
- Adds lowercase tags for the direct parent comment
- Maintains proper thread structure

## Quote Events

### Quote a Text Note

```kotlin
// Fetch an event to quote
val originalNote = ndk.fetchEvent("note1...")

// Create a quote with your commentary
val quote = originalNote.quote()
    .content("This is an important take. nostr:${originalNote.encode()}")
    .build(currentUser.signer)

// Publish the quote
ndk.publish(quote)
```

The quote builder:
- Always creates kind 1 events
- Adds a "q" tag referencing the quoted event
- Allows you to add your own commentary

## Repost Events

### Repost a Text Note

```kotlin
// Fetch a note to repost
val note = ndk.fetchEvent("note1...")

// Create a repost
val repost = note.repost()
    .build(currentUser.signer)

// Publish the repost
ndk.publish(repost)
```

The repost builder:
- Uses kind 6 for kind 1 events
- Includes the original event JSON in the content
- Adds proper "e" and "p" tags

### Repost an Article

```kotlin
// Fetch an article to repost
val article = ndk.fetchEvent("naddr1...")

// Create a generic repost
val repost = article.repost()
    .build(currentUser.signer)

// Publish the repost
ndk.publish(repost)
```

The repost builder:
- Uses kind 16 (generic repost) for non-kind-1 events
- Uses "a" tag for replaceable events
- Adds a "k" tag indicating the original event kind
- Includes the original event JSON in the content

## Using Builders Directly

You can also use the builders directly without the extension functions:

```kotlin
// Direct builder usage
val reply = ReplyBuilder(parentEvent)
    .content("My reply")
    .tag("custom", "value")  // Add custom tags if needed
    .build(signer)

val quote = QuoteBuilder(quotedEvent)
    .content("Check this out!")
    .build(signer)

val repost = RepostBuilder(originalEvent)
    .build(signer)
```

## Complete Example

Here's a complete example showing all three interactions:

```kotlin
class EventInteractionsExample(
    private val ndk: NDK,
    private val currentUser: NDKCurrentUser
) {
    suspend fun demonstrateInteractions() {
        // Fetch an original event
        val originalEvent = ndk.fetchEvent("note1...")
            ?: return

        // 1. Reply to it
        val reply = originalEvent.reply()
            .content("Great post! Here's my take...")
            .build(currentUser.signer)
        ndk.publish(reply)
        println("Published reply: ${reply.id}")

        // 2. Quote it
        val quote = originalEvent.quote()
            .content("This is worth reading. nostr:${originalEvent.encode()}")
            .build(currentUser.signer)
        ndk.publish(quote)
        println("Published quote: ${quote.id}")

        // 3. Repost it
        val repost = originalEvent.repost()
            .build(currentUser.signer)
        ndk.publish(repost)
        println("Published repost: ${repost.id}")
    }
}
```

## Thread Structure Visualization

Here's how the tag structure works for replies:

### Simple Reply Chain (Kind 1)
```
Root Post (no e tags)
  └─ Reply 1 (e tag with "root" marker)
      └─ Reply 2 (e tag with "root" + e tag with "reply" marker)
          └─ Reply 3 (e tag with "root" + e tag with "reply" marker)
```

### Article Comment Chain (NIP-22)
```
Article (kind 30023)
  └─ Comment 1 (A/E/K/P uppercase + a/e/k/p lowercase)
      └─ Comment 2 (copies uppercase tags + new lowercase tags for parent)
          └─ Comment 3 (copies uppercase tags + new lowercase tags for parent)
```

## Important Notes

1. **No Manual Tag Construction**: Never manually construct "e" and "p" tags. Always use the builders.

2. **Automatic Thread Handling**: The builders automatically detect whether you're replying to a root event or a reply in a thread.

3. **NIP Compliance**: All builders follow NIP-10 (replies) and NIP-18 (reposts/quotes) conventions.

4. **Event Kind Detection**: The reply builder automatically chooses kind 1 for text note replies and kind 1111 for all other event kinds.

5. **Relay Hints**: The builders currently use empty strings for relay hints. Future versions may integrate with NDK's relay selection logic.

## Related NIPs

- [NIP-10: Conventions for clients' use of `e` and `p` tags in text events](https://github.com/nostr-protocol/nips/blob/master/10.md)
- [NIP-18: Reposts](https://github.com/nostr-protocol/nips/blob/master/18.md)
- [NIP-22: Comment](https://github.com/nostr-protocol/nips/blob/master/22.md)
