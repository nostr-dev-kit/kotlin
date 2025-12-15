package io.nostr.ndk.builders

import io.nostr.ndk.blossom.BlobDescriptor
import io.nostr.ndk.crypto.NDKSigner
import io.nostr.ndk.crypto.UnsignedEvent
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKTag
import io.nostr.ndk.nips.KIND_VIDEO

/**
 * Builder for creating video events (kind 34236) per NIP-71.
 *
 * Example usage:
 * ```
 * val videoEvent = VideoBuilder()
 *     .video(blobDescriptor, duration = 6, dimensions = 1080 to 1920)
 *     .thumbnail(thumbnailUrl, blurhash = "LKO2...")
 *     .caption("6 second masterpiece!")
 *     .build(signer)
 * ```
 */
class VideoBuilder {
    private var content: String = ""
    private var title: String? = null
    private var videoMetadata: VideoMetadata? = null
    private var thumbnailUrl: String? = null
    private var thumbnailBlurhash: String? = null
    private var dTag: String? = null

    private data class VideoMetadata(
        val url: String,
        val blurhash: String?,
        val dimensions: Pair<Int, Int>?,
        val mimeType: String,
        val sha256: String,
        val size: Long,
        val duration: Int?,
        val alt: String?
    )

    /**
     * Set the video's d-tag identifier for replaceable events.
     * If not set, a random identifier will be generated.
     */
    fun dTag(tag: String) = apply { dTag = tag }

    /**
     * Set the caption/description for the video.
     */
    fun caption(text: String) = apply { content = text }

    /**
     * Set the video title.
     */
    fun title(text: String) = apply { title = text }

    /**
     * Set the video from a BlobDescriptor (NDKBlossom upload result).
     *
     * @param blob BlobDescriptor from NDKBlossom upload
     * @param duration Video duration in seconds
     * @param blurhash Blurhash string for placeholder
     * @param dimensions Video dimensions (width x height)
     * @param alt Alt text for accessibility
     */
    fun video(
        blob: BlobDescriptor,
        duration: Int? = null,
        blurhash: String? = null,
        dimensions: Pair<Int, Int>? = null,
        alt: String? = null
    ) = apply {
        videoMetadata = VideoMetadata(
            url = blob.url,
            blurhash = blurhash ?: blob.blurhash,
            dimensions = dimensions ?: blob.width?.let { w -> blob.height?.let { h -> w to h } },
            mimeType = blob.mimeType,
            sha256 = blob.sha256,
            size = blob.size,
            duration = duration,
            alt = alt ?: blob.alt
        )
    }

    /**
     * Set thumbnail image for the video.
     */
    fun thumbnail(url: String, blurhash: String? = null) = apply {
        thumbnailUrl = url
        thumbnailBlurhash = blurhash
    }

    /**
     * Build and sign the video event.
     * @return Signed NDKEvent ready for publishing
     */
    suspend fun build(signer: NDKSigner): NDKEvent {
        val video = requireNotNull(videoMetadata) { "Video is required" }

        val tags = mutableListOf<NDKTag>()

        // d-tag for replaceable event (NIP-71 uses parameterized replaceable events)
        val identifier = dTag ?: generateDTag(video.sha256)
        tags.add(NDKTag("d", listOf(identifier)))

        // imeta tag with video metadata (NIP-92 format)
        val imetaParts = buildList {
            add("url ${video.url}")
            video.blurhash?.let { add("blurhash $it") }
            video.dimensions?.let { (w, h) -> add("dim ${w}x${h}") }
            add("m ${video.mimeType}")
            add("x ${video.sha256}")
            add("size ${video.size}")
            video.duration?.let { add("duration $it") }
            video.alt?.let { add("alt $it") }
            thumbnailUrl?.let { add("image $it") }
        }
        tags.add(NDKTag("imeta", listOf(imetaParts.joinToString(" "))))

        // Optional tags
        title?.let { tags.add(NDKTag("title", listOf(it))) }
        video.duration?.let { tags.add(NDKTag("duration", listOf(it.toString()))) }
        tags.add(NDKTag("published_at", listOf((System.currentTimeMillis() / 1000).toString())))

        val unsigned = UnsignedEvent(
            pubkey = signer.pubkey,
            createdAt = System.currentTimeMillis() / 1000,
            kind = KIND_VIDEO,
            tags = tags,
            content = content
        )

        return signer.sign(unsigned)
    }

    private fun generateDTag(sha256: String): String {
        // Use first 16 chars of sha256 as d-tag identifier
        return sha256.take(16)
    }
}
