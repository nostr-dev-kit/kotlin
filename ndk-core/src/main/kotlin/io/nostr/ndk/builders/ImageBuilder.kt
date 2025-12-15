package io.nostr.ndk.builders

import io.nostr.ndk.blossom.BlobDescriptor
import io.nostr.ndk.crypto.NDKSigner
import io.nostr.ndk.crypto.UnsignedEvent
import io.nostr.ndk.kinds.NDKImage
import io.nostr.ndk.models.NDKTag
import io.nostr.ndk.nips.KIND_IMAGE

/**
 * Builder for creating image gallery events (kind 20).
 *
 * Example usage:
 * ```
 * val imageEvent = ImageBuilder()
 *     .caption("Beautiful sunset!")
 *     .addImage(blobDescriptor, blurhash = "LKO2...", dimensions = 1920 to 1080)
 *     .build(signer)
 * ```
 */
class ImageBuilder {
    private var content: String = ""
    private val images = mutableListOf<ImageMetadata>()

    private data class ImageMetadata(
        val url: String,
        val blurhash: String?,
        val dimensions: Pair<Int, Int>?,
        val mimeType: String,
        val sha256: String,
        val size: Long,
        val alt: String?
    )

    /**
     * Set the caption text for the gallery.
     */
    fun caption(text: String) = apply { content = text }

    /**
     * Add an image to the gallery.
     *
     * @param blob BlobDescriptor from NDKBlossom upload
     * @param blurhash Blurhash string for placeholder (uses blob.blurhash if not provided)
     * @param dimensions Image dimensions (width x height) (uses blob dimensions if not provided)
     * @param alt Alt text for accessibility (uses blob.alt if not provided)
     */
    fun addImage(
        blob: BlobDescriptor,
        blurhash: String? = null,
        dimensions: Pair<Int, Int>? = null,
        alt: String? = null
    ) = apply {
        images.add(
            ImageMetadata(
                url = blob.url,
                blurhash = blurhash ?: blob.blurhash,
                dimensions = dimensions ?: blob.width?.let { w -> blob.height?.let { h -> w to h } },
                mimeType = blob.mimeType,
                sha256 = blob.sha256,
                size = blob.size,
                alt = alt ?: blob.alt
            )
        )
    }

    /**
     * Build and sign the image gallery event.
     */
    suspend fun build(signer: NDKSigner): NDKImage {
        require(images.isNotEmpty()) { "At least one image is required" }

        val tags = images.map { img ->
            val imetaParts = buildList {
                // Required fields
                add("url ${img.url}")

                // Optional fields
                img.blurhash?.let { add("blurhash $it") }
                img.dimensions?.let { (w, h) -> add("dim ${w}x${h}") }
                add("m ${img.mimeType}")
                add("x ${img.sha256}")
                add("size ${img.size}")
                img.alt?.let { add("alt $it") }
            }

            NDKTag("imeta", listOf(imetaParts.joinToString(" ")))
        }

        val unsigned = UnsignedEvent(
            pubkey = signer.pubkey,
            createdAt = System.currentTimeMillis() / 1000,
            kind = KIND_IMAGE,
            tags = tags,
            content = content
        )

        val signedEvent = signer.sign(unsigned)

        return NDKImage.from(signedEvent)!!
    }
}
