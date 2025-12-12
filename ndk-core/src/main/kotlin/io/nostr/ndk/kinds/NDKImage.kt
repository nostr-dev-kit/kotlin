package io.nostr.ndk.kinds

import io.nostr.ndk.models.EventId
import io.nostr.ndk.models.ImetaTag
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKTag
import io.nostr.ndk.models.PublicKey
import io.nostr.ndk.models.Signature
import io.nostr.ndk.models.Timestamp
import io.nostr.ndk.nips.KIND_IMAGE

/**
 * Represents an image gallery event (NIP-68/NIP-92).
 *
 * Uses delegation to extend NDKEvent functionality while maintaining
 * type safety and avoiding wrapper overhead.
 *
 * @kind 20
 */
class NDKImage private constructor(
    private val delegate: NDKEvent
) {

    companion object {
        const val KIND = KIND_IMAGE

        /**
         * Create NDKImage from an existing NDKEvent.
         *
         * @param event Event to wrap (must be kind 20)
         * @return NDKImage instance or null if event is not kind 20
         */
        fun from(event: NDKEvent): NDKImage? {
            if (event.kind != KIND) return null
            return NDKImage(event)
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
     * Parsed image metadata from imeta tags.
     * Lazy evaluation - only parses once on first access.
     */
    val images: List<ImetaTag> by lazy {
        tags.filter { it.name == "imeta" }
            .mapNotNull { ImetaTag.parse(it) }
    }

    /**
     * Caption text for the gallery.
     */
    val caption: String get() = content

    /**
     * Whether this gallery has at least one valid image.
     */
    val isValid: Boolean get() = images.isNotEmpty()

    /**
     * First image for use as cover/thumbnail.
     */
    val coverImage: ImetaTag? get() = images.firstOrNull()
}
