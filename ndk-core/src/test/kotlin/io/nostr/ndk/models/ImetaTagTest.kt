package io.nostr.ndk.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ImetaTagTest {

    @Test
    fun `parse imeta tag with all fields`() {
        val tag = NDKTag(
            "imeta",
            listOf("url https://example.com/image.jpg blurhash LKO2?U%2Tw=w]~RBVZRi};RPxuwH dim 1920x1080 m image/jpeg x abc123 size 204800 alt Sunset")
        )

        val result = ImetaTag.parse(tag)

        assertNotNull(result)
        assertEquals("https://example.com/image.jpg", result?.url)
        assertEquals("LKO2?U%2Tw=w]~RBVZRi};RPxuwH", result?.blurhash)
        assertEquals(1920 to 1080, result?.dimensions)
        assertEquals("image/jpeg", result?.mimeType)
        assertEquals("abc123", result?.sha256)
        assertEquals(204800L, result?.size)
        assertEquals("Sunset", result?.alt)
    }

    @Test
    fun `parse imeta tag with only required url`() {
        val tag = NDKTag("imeta", listOf("url https://example.com/image.jpg"))

        val result = ImetaTag.parse(tag)

        assertNotNull(result)
        assertEquals("https://example.com/image.jpg", result?.url)
        assertEquals(null, result?.blurhash)
        assertEquals(null, result?.dimensions)
    }

    @Test
    fun `parse imeta tag with multiple fallback URLs`() {
        val tag = NDKTag(
            "imeta",
            listOf("url https://cdn1.com/img.jpg fallback https://cdn2.com/img.jpg fallback https://cdn3.com/img.jpg")
        )

        val result = ImetaTag.parse(tag)

        assertNotNull(result)
        assertEquals("https://cdn1.com/img.jpg", result?.url)
        assertEquals(2, result?.fallback?.size)
        assertEquals("https://cdn2.com/img.jpg", result?.fallback?.get(0))
        assertEquals("https://cdn3.com/img.jpg", result?.fallback?.get(1))
    }

    @Test
    fun `return null for non-imeta tags`() {
        val tag = NDKTag("e", listOf("event123"))

        val result = ImetaTag.parse(tag)

        assertEquals(null, result)
    }

    @Test
    fun `return null for imeta tag without url`() {
        val tag = NDKTag("imeta", listOf("blurhash LKO2?U%2Tw=w"))

        val result = ImetaTag.parse(tag)

        assertEquals(null, result)
    }
}
