package io.nostr.ndk.content

import io.nostr.ndk.models.NDKEvent

/**
 * Extension functions for content parsing on NDKEvent.
 */

/**
 * Parses this event's content into typed segments.
 *
 * Returns a list of segments that can be rendered with custom components.
 * Segments include Text, Mention, Link, Hashtag, and Media.
 *
 * ## Usage
 *
 * ```kotlin
 * val segments = event.parseContent()
 * segments.forEach { segment ->
 *     when (segment) {
 *         is ContentSegment.Text -> Text(segment.text)
 *         is ContentSegment.Mention -> UserMention(segment.pubkey)
 *         is ContentSegment.Link -> Hyperlink(segment.url)
 *         is ContentSegment.Hashtag -> HashtagChip(segment.tag)
 *         is ContentSegment.Media -> MediaGallery(segment.urls)
 *     }
 * }
 * ```
 */
fun NDKEvent.parseContent(): List<ContentSegment> {
    return ContentParser.parse(content)
}

/**
 * Returns all user mentions found in this event's content.
 *
 * This only includes inline mentions (npub/nprofile in content),
 * NOT p-tags. Use `mentionedPubkeys` for p-tags (from Nip01.kt).
 *
 * ## Usage
 *
 * ```kotlin
 * val mentions = event.inlineMentions()
 * mentions.forEach { mention ->
 *     prefetchProfile(mention.pubkey)
 * }
 * ```
 */
fun NDKEvent.inlineMentions(): List<ContentSegment.Mention> {
    return parseContent().filterIsInstance<ContentSegment.Mention>()
}

/**
 * Returns all hashtags found in this event's content.
 *
 * This only includes inline hashtags (#tag in content),
 * NOT t-tags. Use `hashtags` property for t-tags (from Nip01.kt).
 *
 * ## Usage
 *
 * ```kotlin
 * val tags = event.inlineHashtags()
 * tags.forEach { hashtag ->
 *     println("#${hashtag.tag}")
 * }
 * ```
 */
fun NDKEvent.inlineHashtags(): List<ContentSegment.Hashtag> {
    return parseContent().filterIsInstance<ContentSegment.Hashtag>()
}

/**
 * Returns all media URLs found in this event's content.
 *
 * ## Usage
 *
 * ```kotlin
 * val media = event.inlineMedia()
 * if (media.isNotEmpty()) {
 *     val firstGallery = media[0]
 *     displayGallery(firstGallery.urls, firstGallery.mediaType)
 * }
 * ```
 */
fun NDKEvent.inlineMedia(): List<ContentSegment.Media> {
    return parseContent().filterIsInstance<ContentSegment.Media>()
}

/**
 * Returns all links found in this event's content.
 *
 * ## Usage
 *
 * ```kotlin
 * val links = event.inlineLinks()
 * links.forEach { link ->
 *     prefetchLinkMetadata(link.url)
 * }
 * ```
 */
fun NDKEvent.inlineLinks(): List<ContentSegment.Link> {
    return parseContent().filterIsInstance<ContentSegment.Link>()
}

/**
 * Returns all event mentions found in this event's content.
 *
 * This includes inline event mentions (note, nevent, naddr in content).
 *
 * ## Usage
 *
 * ```kotlin
 * val eventMentions = event.inlineEventMentions()
 * eventMentions.forEach { mention ->
 *     prefetchEvent(mention.eventId ?: mention.identifier)
 * }
 * ```
 */
fun NDKEvent.inlineEventMentions(): List<ContentSegment.EventMention> {
    return parseContent().filterIsInstance<ContentSegment.EventMention>()
}
