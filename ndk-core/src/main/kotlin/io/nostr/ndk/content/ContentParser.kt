package io.nostr.ndk.content

import io.nostr.ndk.utils.Nip19

/**
 * Regex patterns for content entity detection.
 */
internal object ContentPatterns {
    /** Matches nostr: URIs for user mentions (npub, nprofile) */
    val NOSTR_USER_URI = Regex(
        "nostr:(npub1[a-z0-9]{58}|nprofile1[a-z0-9]+)",
        RegexOption.IGNORE_CASE
    )

    /** Matches nostr: URIs for event mentions (note, nevent, naddr) */
    val NOSTR_EVENT_URI = Regex(
        "nostr:(note1[a-z0-9]{58}|nevent1[a-z0-9]+|naddr1[a-z0-9]+)",
        RegexOption.IGNORE_CASE
    )

    /** Matches hashtags (word boundary aware) */
    val HASHTAG = Regex(
        "(^|\\s)#([a-zA-Z0-9_\\u0080-\\uFFFF]+)(?=\\s|\$|[^\\w])"
    )

    /** Matches image URLs */
    val IMAGE_URL = Regex(
        "https?://[^\\s<>\"]+\\.(jpg|jpeg|png|gif|webp|svg)(\\?[^\\s<>\"]*)?",
        RegexOption.IGNORE_CASE
    )

    /** Matches video URLs */
    val VIDEO_URL = Regex(
        "https?://[^\\s<>\"]+\\.(mp4|webm|mov)(\\?[^\\s<>\"]*)?",
        RegexOption.IGNORE_CASE
    )

    /** Matches any HTTP(S) URL */
    val URL = Regex("https?://[^\\s<>\"]+", RegexOption.IGNORE_CASE)

    /** All patterns in priority order (most specific first) */
    val ALL = listOf(NOSTR_USER_URI, NOSTR_EVENT_URI, HASHTAG, IMAGE_URL, VIDEO_URL, URL)
}

/**
 * Parses event content into typed segments.
 *
 * This is a pure function that takes content and produces a list of segments.
 * It handles:
 * - User mentions (npub/nprofile)
 * - Hashtags
 * - Media URLs (images and videos)
 * - Regular links
 * - Plain text
 *
 * Consecutive media URLs separated only by whitespace are automatically grouped
 * for gallery-style rendering.
 *
 * ## Usage
 *
 * ```kotlin
 * val segments = ContentParser.parse("Hello #nostr https://example.com/photo.jpg")
 * segments.forEach { segment ->
 *     when (segment) {
 *         is ContentSegment.Text -> println("Text: ${segment.text}")
 *         is ContentSegment.Hashtag -> println("Tag: ${segment.tag}")
 *         is ContentSegment.Media -> println("Image: ${segment.urls}")
 *         else -> {}
 *     }
 * }
 * ```
 */
object ContentParser {
    /**
     * Parses content string into segments.
     *
     * @param content The event content to parse
     * @return List of parsed segments
     */
    fun parse(content: String): List<ContentSegment> {
        if (content.isBlank()) return emptyList()

        val matches = collectMatches(content)
        val segments = buildSegments(content, matches)
        return groupConsecutiveMedia(segments)
    }

    /**
     * Collects all pattern matches from content, sorted by position.
     */
    private fun collectMatches(content: String): List<MatchData> {
        val matches = mutableListOf<MatchData>()

        for (pattern in ContentPatterns.ALL) {
            pattern.findAll(content).forEach { match ->
                matches.add(MatchData(match, match.range.first))
            }
        }

        return matches.sortedBy { it.index }
    }

    /**
     * Builds initial segments from matches.
     */
    private fun buildSegments(
        content: String,
        matches: List<MatchData>
    ): List<ContentSegment> {
        val segments = mutableListOf<ContentSegment>()
        var lastIndex = 0

        for (matchData in matches) {
            val match = matchData.match
            val index = matchData.index

            // Skip overlapping matches
            if (index < lastIndex) continue

            // Add text before match
            if (index > lastIndex) {
                segments.add(ContentSegment.Text(content.substring(lastIndex, index)))
            }

            // Add classified match
            val matchText = match.value

            // Special handling for hashtags (preserve/skip leading whitespace)
            if (matchText.matches(ContentPatterns.HASHTAG)) {
                val groups = ContentPatterns.HASHTAG.find(matchText)!!.groupValues
                val whitespace = groups.getOrNull(1) ?: ""
                val tag = groups.getOrNull(2) ?: ""

                if (whitespace.isNotEmpty()) {
                    segments.add(ContentSegment.Text(whitespace))
                }
                if (tag.isNotEmpty()) {
                    segments.add(ContentSegment.Hashtag(tag))
                }
            } else {
                segments.add(classifyMatch(matchText))
            }

            lastIndex = index + matchText.length
        }

        // Add remaining text
        if (lastIndex < content.length) {
            segments.add(ContentSegment.Text(content.substring(lastIndex)))
        }

        return segments
    }

    /**
     * Classifies a matched string into the appropriate segment type.
     */
    private fun classifyMatch(text: String): ContentSegment {
        // Nostr URI (mention only for now)
        if (text.startsWith("nostr:", ignoreCase = true)) {
            val bech32 = text.substring(6) // Remove "nostr:" prefix
            return parseNostrUri(bech32) ?: ContentSegment.Text(text)
        }

        // Media (image or video)
        if (text.matches(ContentPatterns.IMAGE_URL)) {
            return ContentSegment.Media(text, MediaType.IMAGE)
        }
        if (text.matches(ContentPatterns.VIDEO_URL)) {
            return ContentSegment.Media(text, MediaType.VIDEO)
        }

        // Regular link
        if (text.startsWith("http", ignoreCase = true)) {
            return ContentSegment.Link(text)
        }

        return ContentSegment.Text(text)
    }

    /**
     * Parses a nostr: URI into a Mention or EventMention segment.
     */
    private fun parseNostrUri(bech32: String): ContentSegment? {
        return try {
            when (val decoded = Nip19.decode(bech32)) {
                is Nip19.Decoded.Npub -> {
                    ContentSegment.Mention(
                        bech32 = bech32,
                        pubkey = decoded.pubkey,
                        relays = emptyList()
                    )
                }
                is Nip19.Decoded.Nprofile -> {
                    ContentSegment.Mention(
                        bech32 = bech32,
                        pubkey = decoded.pubkey,
                        relays = decoded.relays
                    )
                }
                is Nip19.Decoded.Note -> {
                    ContentSegment.EventMention(
                        bech32 = bech32,
                        eventId = decoded.eventId,
                        relays = emptyList()
                    )
                }
                is Nip19.Decoded.Nevent -> {
                    ContentSegment.EventMention(
                        bech32 = bech32,
                        eventId = decoded.eventId,
                        kind = decoded.kind,
                        author = decoded.author,
                        relays = decoded.relays
                    )
                }
                is Nip19.Decoded.Naddr -> {
                    ContentSegment.EventMention(
                        bech32 = bech32,
                        identifier = decoded.identifier,
                        kind = decoded.kind,
                        author = decoded.pubkey,
                        relays = decoded.relays
                    )
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Groups consecutive media segments separated only by whitespace.
     *
     * This enables gallery-style rendering for multiple images/videos.
     */
    private fun groupConsecutiveMedia(segments: List<ContentSegment>): List<ContentSegment> {
        val result = mutableListOf<ContentSegment>()
        val mediaBuffer = mutableListOf<String>()
        val whitespaceBuffer = mutableListOf<ContentSegment>()
        var currentMediaType: MediaType? = null

        fun flushMedia() {
            if (mediaBuffer.isEmpty()) return

            result.add(
                ContentSegment.Media(
                    urls = mediaBuffer.toList(),
                    mediaType = currentMediaType!!
                )
            )
            mediaBuffer.clear()
            whitespaceBuffer.clear()
            currentMediaType = null
        }

        fun flushWhitespace() {
            result.addAll(whitespaceBuffer)
            whitespaceBuffer.clear()
        }

        for (segment in segments) {
            when {
                segment is ContentSegment.Media -> {
                    // Only group media of the same type
                    if (currentMediaType == null) {
                        currentMediaType = segment.mediaType
                    } else if (currentMediaType != segment.mediaType) {
                        flushMedia()
                        currentMediaType = segment.mediaType
                    }
                    mediaBuffer.addAll(segment.urls)
                }
                segment is ContentSegment.Text &&
                    segment.text.isBlank() &&
                    mediaBuffer.isNotEmpty() -> {
                    // Buffer whitespace between media
                    whitespaceBuffer.add(segment)
                }
                else -> {
                    // Non-media breaks grouping
                    flushMedia()
                    flushWhitespace()
                    result.add(segment)
                }
            }
        }

        flushMedia()
        return result
    }
}

/**
 * Internal data class for tracking matches.
 */
private data class MatchData(
    val match: MatchResult,
    val index: Int
)
