package io.nostr.ndk.content

import androidx.compose.runtime.Immutable

/**
 * Represents a parsed segment of event content.
 *
 * Content is broken down into typed segments that can be rendered differently:
 * - [Text]: Plain text content
 * - [Mention]: User reference (npub/nprofile)
 * - [Link]: HTTP(S) URL
 * - [Hashtag]: #tag reference
 * - [Media]: Image or video URL
 *
 * ## Usage
 *
 * ```kotlin
 * val segments = event.parseContent()
 * segments.forEach { segment ->
 *     when (segment) {
 *         is ContentSegment.Text -> println(segment.text)
 *         is ContentSegment.Mention -> fetchProfile(segment.pubkey)
 *         is ContentSegment.Link -> openBrowser(segment.url)
 *         is ContentSegment.Hashtag -> searchTag(segment.tag)
 *         is ContentSegment.Media -> displayMedia(segment.urls)
 *     }
 * }
 * ```
 */
@Immutable
sealed class ContentSegment {

    /**
     * Plain text content.
     *
     * @property text The text content
     */
    @Immutable
    data class Text(val text: String) : ContentSegment()

    /**
     * User mention from nostr: URI (npub/nprofile).
     *
     * @property bech32 The original bech32 identifier (npub1... or nprofile1...)
     * @property pubkey The decoded public key (hex)
     * @property relays Optional relay hints from nprofile
     */
    @Immutable
    data class Mention(
        val bech32: String,
        val pubkey: String,
        val relays: List<String> = emptyList()
    ) : ContentSegment()

    /**
     * HTTP(S) link that is not media.
     *
     * @property url The URL string
     */
    @Immutable
    data class Link(val url: String) : ContentSegment()

    /**
     * Hashtag reference.
     *
     * @property tag The tag text (without # symbol)
     */
    @Immutable
    data class Hashtag(val tag: String) : ContentSegment()

    /**
     * Event mention from nostr: URI (note/nevent/naddr).
     *
     * @property bech32 The original bech32 identifier
     * @property eventId The event ID (for note/nevent)
     * @property identifier The identifier (for naddr)
     * @property kind The event kind (for nevent/naddr)
     * @property author The author pubkey (for nevent/naddr)
     * @property relays Optional relay hints
     */
    @Immutable
    data class EventMention(
        val bech32: String,
        val eventId: String? = null,
        val identifier: String? = null,
        val kind: Int? = null,
        val author: String? = null,
        val relays: List<String> = emptyList()
    ) : ContentSegment()

    /**
     * Media content (images or videos).
     *
     * Consecutive media URLs separated only by whitespace are grouped together
     * for gallery-style rendering.
     *
     * @property urls List of media URLs (can be single or multiple)
     * @property mediaType The type of media content
     */
    @Immutable
    data class Media(
        val urls: List<String>,
        val mediaType: MediaType
    ) : ContentSegment() {
        /**
         * Convenience constructor for single media URL.
         */
        constructor(url: String, mediaType: MediaType) : this(listOf(url), mediaType)
    }
}

/**
 * Type of media content.
 */
@Immutable
enum class MediaType {
    /** Static image (jpg, png, gif, webp, svg) */
    IMAGE,

    /** Video file (mp4, webm, mov) */
    VIDEO
}
