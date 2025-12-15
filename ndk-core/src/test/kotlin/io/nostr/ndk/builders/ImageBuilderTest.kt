package io.nostr.ndk.builders

import io.nostr.ndk.blossom.BlobDescriptor
import io.nostr.ndk.crypto.NDKKeyPair
import io.nostr.ndk.crypto.NDKPrivateKeySigner
import io.nostr.ndk.kinds.NDKImage
import io.nostr.ndk.nips.KIND_IMAGE
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ImageBuilderTest {

    private fun createBlob(
        url: String,
        sha256: String,
        size: Long,
        mimeType: String
    ) = BlobDescriptor(
        url = url,
        sha256 = sha256,
        size = size,
        mimeType = mimeType
    )

    @Test
    fun `build creates kind 20 event with imeta tags`() = runTest {
        val signer = NDKPrivateKeySigner(NDKKeyPair.generate())
        val blob = createBlob(
            url = "https://cdn.example.com/image.jpg",
            sha256 = "abc123",
            size = 204800,
            mimeType = "image/jpeg"
        )

        val image = ImageBuilder()
            .caption("Beautiful sunset!")
            .addImage(
                blob = blob,
                blurhash = "LKO2?U%2Tw=w",
                dimensions = 1920 to 1080,
                alt = "Sunset"
            )
            .build(signer)

        assertEquals(KIND_IMAGE, image.kind)
        assertEquals("Beautiful sunset!", image.content)
        assertEquals(1, image.images.size)

        val img = image.images[0]
        assertEquals("https://cdn.example.com/image.jpg", img.url)
        assertEquals("LKO2?U%2Tw=w", img.blurhash)
        assertEquals(1920 to 1080, img.dimensions)
        assertEquals("image/jpeg", img.mimeType)
        assertEquals("abc123", img.sha256)
        assertEquals(204800L, img.size)
        assertEquals("Sunset", img.alt)
    }

    @Test
    fun `build with multiple images creates multiple imeta tags`() = runTest {
        val signer = NDKPrivateKeySigner(NDKKeyPair.generate())

        val image = ImageBuilder()
            .caption("Gallery")
            .addImage(
                createBlob("https://cdn.example.com/img1.jpg", "hash1", 100000, "image/jpeg"),
                blurhash = "LBLUE"
            )
            .addImage(
                createBlob("https://cdn.example.com/img2.jpg", "hash2", 200000, "image/png"),
                blurhash = "LRED"
            )
            .build(signer)

        assertEquals(2, image.images.size)
        assertEquals("https://cdn.example.com/img1.jpg", image.images[0].url)
        assertEquals("https://cdn.example.com/img2.jpg", image.images[1].url)
    }

    @Test
    fun `build without caption creates empty content`() = runTest {
        val signer = NDKPrivateKeySigner(NDKKeyPair.generate())

        val image = ImageBuilder()
            .addImage(
                createBlob("https://cdn.example.com/img.jpg", "hash", 100000, "image/jpeg")
            )
            .build(signer)

        assertEquals("", image.content)
    }

    @Test
    fun `build requires at least one image`() = runTest {
        val signer = NDKPrivateKeySigner(NDKKeyPair.generate())

        try {
            ImageBuilder()
                .caption("No images")
                .build(signer)
            fail("Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("At least one image") == true)
        }
    }

    @Test
    fun `imeta tag format is space-separated key-value pairs`() = runTest {
        val signer = NDKPrivateKeySigner(NDKKeyPair.generate())

        val image = ImageBuilder()
            .addImage(
                createBlob("https://cdn.example.com/img.jpg", "abc123", 204800, "image/jpeg"),
                blurhash = "LBLUE",
                dimensions = 1920 to 1080,
                alt = "TestImage"
            )
            .build(signer)

        val imetaTag = image.tags.find { it.name == "imeta" }
        assertNotNull(imetaTag)

        val tagValue = imetaTag!!.values.first()
        assertTrue(tagValue.contains("url https://cdn.example.com/img.jpg"))
        assertTrue(tagValue.contains("blurhash LBLUE"))
        assertTrue(tagValue.contains("dim 1920x1080"))
        assertTrue(tagValue.contains("m image/jpeg"))
        assertTrue(tagValue.contains("x abc123"))
        assertTrue(tagValue.contains("size 204800"))
        assertTrue(tagValue.contains("alt TestImage"))
    }

    @Test
    fun `addImage with minimal parameters creates valid imeta tag`() = runTest {
        val signer = NDKPrivateKeySigner(NDKKeyPair.generate())

        val image = ImageBuilder()
            .addImage(
                createBlob("https://cdn.example.com/img.jpg", "abc123", 204800, "image/jpeg")
            )
            .build(signer)

        val imetaTag = image.tags.find { it.name == "imeta" }
        assertNotNull(imetaTag)

        val tagValue = imetaTag!!.values.first()
        assertTrue(tagValue.contains("url https://cdn.example.com/img.jpg"))
        assertTrue(tagValue.contains("m image/jpeg"))
        assertTrue(tagValue.contains("x abc123"))
        assertTrue(tagValue.contains("size 204800"))
        assertFalse(tagValue.contains("blurhash"))
        assertFalse(tagValue.contains("dim"))
        assertFalse(tagValue.contains("alt"))
    }
}
