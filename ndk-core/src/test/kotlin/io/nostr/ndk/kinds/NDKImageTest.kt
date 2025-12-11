package io.nostr.ndk.kinds

import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKTag
import io.nostr.ndk.nips.KIND_IMAGE
import org.junit.Assert.*
import org.junit.Test

class NDKImageTest {

    @Test
    fun `from creates NDKImage from kind 20 event`() {
        val event = NDKEvent(
            id = "img123",
            pubkey = "author123",
            createdAt = 1234567890,
            kind = KIND_IMAGE,
            tags = listOf(
                NDKTag("imeta", listOf("url https://example.com/img1.jpg blurhash LKO2 dim 1920x1080 m image/jpeg x abc123 size 204800")),
                NDKTag("imeta", listOf("url https://example.com/img2.jpg blurhash LPO3 dim 1080x1920 m image/jpeg x def456 size 187392"))
            ),
            content = "Beautiful sunset!",
            sig = "sig123"
        )

        val image = NDKImage.from(event)

        assertNotNull(image)
        assertEquals("img123", image?.id)
        assertEquals("author123", image?.pubkey)
        assertEquals("Beautiful sunset!", image?.caption)
        assertEquals(2, image?.images?.size)
        assertTrue(image?.isValid == true)
    }

    @Test
    fun `from returns null for non-kind-20 events`() {
        val event = NDKEvent(
            id = "note123",
            pubkey = "author123",
            createdAt = 1234567890,
            kind = 1,
            tags = emptyList(),
            content = "Just a note",
            sig = "sig123"
        )

        val image = NDKImage.from(event)

        assertNull(image)
    }

    @Test
    fun `coverImage returns first image`() {
        val event = NDKEvent(
            id = "img123",
            pubkey = "author123",
            createdAt = 1234567890,
            kind = KIND_IMAGE,
            tags = listOf(
                NDKTag("imeta", listOf("url https://example.com/img1.jpg")),
                NDKTag("imeta", listOf("url https://example.com/img2.jpg"))
            ),
            content = "Test",
            sig = "sig123"
        )

        val image = NDKImage.from(event)!!

        assertEquals("https://example.com/img1.jpg", image.coverImage?.url)
    }

    @Test
    fun `isValid returns false when no valid images`() {
        val event = NDKEvent(
            id = "img123",
            pubkey = "author123",
            createdAt = 1234567890,
            kind = KIND_IMAGE,
            tags = emptyList(),
            content = "No images",
            sig = "sig123"
        )

        val image = NDKImage.from(event)!!

        assertFalse(image.isValid)
    }

    @Test
    fun `lazy images property caches result`() {
        val event = NDKEvent(
            id = "img123",
            pubkey = "author123",
            createdAt = 1234567890,
            kind = KIND_IMAGE,
            tags = listOf(
                NDKTag("imeta", listOf("url https://example.com/img1.jpg"))
            ),
            content = "Test",
            sig = "sig123"
        )

        val image = NDKImage.from(event)!!

        val first = image.images
        val second = image.images

        assertSame(first, second)
    }

    @Test
    fun `delegates all NDKEvent properties`() {
        val event = NDKEvent(
            id = "img123",
            pubkey = "author123",
            createdAt = 1234567890,
            kind = KIND_IMAGE,
            tags = listOf(
                NDKTag("imeta", listOf("url https://example.com/img1.jpg"))
            ),
            content = "Test",
            sig = "sig123"
        )

        val image = NDKImage.from(event)!!

        // Verify delegation works by checking delegated properties
        assertEquals(event.id, image.id)
        assertEquals(event.pubkey, image.pubkey)
        assertEquals(event.createdAt, image.createdAt)
        assertEquals(event.kind, image.kind)
        assertEquals(event.tags, image.tags)
        assertEquals(event.content, image.content)
        assertEquals(event.sig, image.sig)
    }
}
