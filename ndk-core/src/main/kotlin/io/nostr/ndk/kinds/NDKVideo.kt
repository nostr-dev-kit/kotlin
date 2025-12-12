package io.nostr.ndk.kinds

import io.nostr.ndk.models.EventId
import io.nostr.ndk.models.ImetaTag
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKTag
import io.nostr.ndk.models.PublicKey
import io.nostr.ndk.models.Signature
import io.nostr.ndk.models.Timestamp
import io.nostr.ndk.nips.KIND_VIDEO

/**
 * Represents a video event (kind 34236).
 *
 * Uses delegation to extend NDKEvent functionality while maintaining
 * type safety and avoiding wrapper overhead.
 *
 * @kind 34236
 */
class NDKVideo private constructor(
    private val delegate: NDKEvent
) {

    companion object {
        const val KIND = KIND_VIDEO

        /**
         * Create NDKVideo from an existing NDKEvent.
         *
         * @param event Event to wrap (must be kind 34236)
         * @return NDKVideo instance or null if event is not kind 34236
         */
        fun from(event: NDKEvent): NDKVideo? {
            if (event.kind != KIND) return null
            return NDKVideo(event)
        }
    }

    // Delegate all NDKEvent properties
    val id: EventId get() = delegate.id
    val pubkey: PublicKey get() = delegate.pubkey
    val createdAt: Timestamp get() = delegate.createdAt
    val kind: Int get() = delegate.kind
    val tags: List<NDKTag> get() = delegate.tags
    val content: String get() = delegate.content
    val sig: Signature? get() = delegate.sig

    /**
     * Parsed video metadata from imeta tag.
     * Lazy evaluation - only parses once on first access.
     */
    val video: ImetaTag? by lazy {
        tags.filter { it.name == "imeta" }
            .mapNotNull { ImetaTag.parse(it) }
            .firstOrNull()  // Videos have only one media file
    }

    /**
     * Video title from "title" tag.
     */
    val title: String? by lazy {
        tags.find { it.name == "title" }?.values?.firstOrNull()
    }

    /**
     * Video summary from "summary" tag.
     */
    val summary: String? by lazy {
        tags.find { it.name == "summary" }?.values?.firstOrNull()
    }

    /**
     * Video description (from content field).
     */
    val description: String get() = content

    /**
     * Published timestamp from "published_at" tag.
     */
    val publishedAt: Long? by lazy {
        tags.find { it.name == "published_at" }?.values?.firstOrNull()?.toLongOrNull()
    }

    /**
     * Duration in seconds from "duration" tag or imeta metadata.
     */
    val duration: Int? by lazy {
        tags.find { it.name == "duration" }?.values?.firstOrNull()?.toIntOrNull()
            ?: video?.duration  // Fallback to imeta duration
    }

    /**
     * Alt text from "alt" tag.
     */
    val altText: String? by lazy {
        tags.find { it.name == "alt" }?.values?.firstOrNull()
    }

    /**
     * d-tag identifier.
     */
    val dTag: String? by lazy {
        tags.find { it.name == "d" }?.values?.firstOrNull()
    }

    /**
     * Video URL for playback.
     */
    val videoUrl: String? get() = video?.url

    /**
     * Thumbnail image URL from imeta "image" metadata.
     */
    val thumbnailUrl: String? by lazy {
        // Parse thumbnail from imeta tag's "image" metadata
        tags.filter { it.name == "imeta" }
            .firstNotNullOfOrNull { tag ->
                val parts = tag.values.firstOrNull()?.split(" ") ?: emptyList()
                var i = 0
                while (i < parts.size - 1) {
                    if (parts[i] == "image") {
                        return@firstNotNullOfOrNull parts[i + 1]
                    }
                    i += 2
                }
                null
            }
    }

    /**
     * Blurhash placeholder for video thumbnail.
     */
    val blurhash: String? get() = video?.blurhash

    /**
     * Whether this video event is valid (has video URL).
     */
    val isValid: Boolean get() = videoUrl != null

    /**
     * Whether duration is exactly 6 seconds (Vine-style).
     */
    val isVineLength: Boolean get() = duration == 6
}
