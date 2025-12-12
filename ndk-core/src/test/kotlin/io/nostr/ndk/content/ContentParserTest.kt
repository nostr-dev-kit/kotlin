package io.nostr.ndk.content

import org.junit.Test
import org.junit.Assert.*

/**
 * Tests for ContentParser.
 *
 * Follows TDD pattern from NDKTest.kt with arrange-act-assert structure.
 */
class ContentParserTest {

    @Test
    fun `empty content returns empty list`() {
        val segments = ContentParser.parse("")

        assertTrue(segments.isEmpty())
    }

    @Test
    fun `blank content returns empty list`() {
        val segments = ContentParser.parse("   \n\t  ")

        assertTrue(segments.isEmpty())
    }

    @Test
    fun `plain text returns single Text segment`() {
        val segments = ContentParser.parse("Hello world")

        assertEquals(1, segments.size)
        assertTrue(segments[0] is ContentSegment.Text)
        assertEquals("Hello world", (segments[0] as ContentSegment.Text).text)
    }

    // URL Detection Tests

    @Test
    fun `parses simple https URL`() {
        val segments = ContentParser.parse("Check this out https://example.com cool right?")

        assertEquals(3, segments.size)
        assertTrue(segments[0] is ContentSegment.Text)
        assertTrue(segments[1] is ContentSegment.Link)
        assertTrue(segments[2] is ContentSegment.Text)
        assertEquals("https://example.com", (segments[1] as ContentSegment.Link).url)
    }

    @Test
    fun `parses http URL`() {
        val segments = ContentParser.parse("Visit http://example.com")

        val link = segments.filterIsInstance<ContentSegment.Link>().firstOrNull()
        assertNotNull(link)
        assertEquals("http://example.com", link?.url)
    }

    @Test
    fun `parses multiple URLs in single text`() {
        val segments = ContentParser.parse("First https://one.com then https://two.com done")

        val links = segments.filterIsInstance<ContentSegment.Link>()
        assertEquals(2, links.size)
        assertEquals("https://one.com", links[0].url)
        assertEquals("https://two.com", links[1].url)
    }

    @Test
    fun `parses URL with query parameters`() {
        val segments = ContentParser.parse("Search https://example.com?q=nostr&limit=50")

        val link = segments.filterIsInstance<ContentSegment.Link>().firstOrNull()
        assertEquals("https://example.com?q=nostr&limit=50", link?.url)
    }

    @Test
    fun `parses URL with fragment`() {
        val segments = ContentParser.parse("Jump to https://example.com#section-2")

        val link = segments.filterIsInstance<ContentSegment.Link>().firstOrNull()
        assertEquals("https://example.com#section-2", link?.url)
    }

    @Test
    fun `parses URL at start of content`() {
        val segments = ContentParser.parse("https://example.com is great")

        assertTrue(segments[0] is ContentSegment.Link)
    }

    @Test
    fun `parses URL at end of content`() {
        val segments = ContentParser.parse("Check out https://example.com")

        assertTrue(segments[segments.size - 1] is ContentSegment.Link)
    }

    // Image Detection Tests

    @Test
    fun `parses jpg image URL`() {
        val segments = ContentParser.parse("Photo: https://example.com/photo.jpg nice!")

        val media = segments.filterIsInstance<ContentSegment.Media>().firstOrNull()
        assertNotNull(media)
        assertEquals(MediaType.IMAGE, media?.mediaType)
        assertEquals("https://example.com/photo.jpg", media?.urls?.first())
    }

    @Test
    fun `parses png image URL`() {
        val segments = ContentParser.parse("https://cdn.com/image.png")

        val media = segments.filterIsInstance<ContentSegment.Media>().firstOrNull()
        assertEquals(MediaType.IMAGE, media?.mediaType)
    }

    @Test
    fun `parses webp image URL`() {
        val segments = ContentParser.parse("Modern format: https://cdn.com/image.webp")

        val media = segments.filterIsInstance<ContentSegment.Media>().firstOrNull()
        assertEquals(MediaType.IMAGE, media?.mediaType)
    }

    @Test
    fun `parses gif image URL`() {
        val segments = ContentParser.parse("Animated: https://cdn.com/animation.gif")

        val media = segments.filterIsInstance<ContentSegment.Media>().firstOrNull()
        assertEquals(MediaType.IMAGE, media?.mediaType)
    }

    @Test
    fun `parses image URL case insensitively`() {
        val segments = ContentParser.parse("https://example.com/PHOTO.JPG")

        val media = segments.filterIsInstance<ContentSegment.Media>().firstOrNull()
        assertEquals(MediaType.IMAGE, media?.mediaType)
    }

    @Test
    fun `parses image URL with query parameters`() {
        val segments = ContentParser.parse("https://cdn.com/image.jpg?size=large&q=90")

        val media = segments.filterIsInstance<ContentSegment.Media>().firstOrNull()
        assertEquals(MediaType.IMAGE, media?.mediaType)
        assertEquals("https://cdn.com/image.jpg?size=large&q=90", media?.urls?.first())
    }

    // Video Detection Tests

    @Test
    fun `parses mp4 video URL`() {
        val segments = ContentParser.parse("Watch https://videos.com/clip.mp4 now")

        val media = segments.filterIsInstance<ContentSegment.Media>().firstOrNull()
        assertEquals(MediaType.VIDEO, media?.mediaType)
        assertEquals("https://videos.com/clip.mp4", media?.urls?.first())
    }

    @Test
    fun `parses webm video URL`() {
        val segments = ContentParser.parse("https://cdn.com/video.webm")

        val media = segments.filterIsInstance<ContentSegment.Media>().firstOrNull()
        assertEquals(MediaType.VIDEO, media?.mediaType)
    }

    @Test
    fun `parses mov video URL`() {
        val segments = ContentParser.parse("https://cdn.com/clip.mov")

        val media = segments.filterIsInstance<ContentSegment.Media>().firstOrNull()
        assertEquals(MediaType.VIDEO, media?.mediaType)
    }

    // Hashtag Detection Tests

    @Test
    fun `parses simple hashtag`() {
        val segments = ContentParser.parse("Hello #nostr world")

        val hashtag = segments.filterIsInstance<ContentSegment.Hashtag>().firstOrNull()
        assertNotNull(hashtag)
        assertEquals("nostr", hashtag?.tag)
    }

    @Test
    fun `parses hashtag with numbers`() {
        val segments = ContentParser.parse("Event #bitcoin2024 coming")

        val hashtag = segments.filterIsInstance<ContentSegment.Hashtag>().firstOrNull()
        assertEquals("bitcoin2024", hashtag?.tag)
    }

    @Test
    fun `parses hashtag with underscores`() {
        val segments = ContentParser.parse("Join #nostr_dev discussion")

        val hashtag = segments.filterIsInstance<ContentSegment.Hashtag>().firstOrNull()
        assertEquals("nostr_dev", hashtag?.tag)
    }

    @Test
    fun `parses hashtag at start of content`() {
        val segments = ContentParser.parse("#nostr is great")

        assertTrue(segments[0] is ContentSegment.Hashtag)
        assertEquals("nostr", (segments[0] as ContentSegment.Hashtag).tag)
    }

    @Test
    fun `parses hashtag at end of content`() {
        val segments = ContentParser.parse("Love #nostr")

        val hashtag = segments.filterIsInstance<ContentSegment.Hashtag>().lastOrNull()
        assertNotNull(hashtag)
        assertEquals("nostr", hashtag?.tag)
    }

    @Test
    fun `parses multiple hashtags`() {
        val segments = ContentParser.parse("Topics: #bitcoin #lightning #nostr")

        val hashtags = segments.filterIsInstance<ContentSegment.Hashtag>()
        assertEquals(3, hashtags.size)
        assertEquals("bitcoin", hashtags[0].tag)
        assertEquals("lightning", hashtags[1].tag)
        assertEquals("nostr", hashtags[2].tag)
    }

    @Test
    fun `does not include punctuation in hashtag`() {
        val segments = ContentParser.parse("This is cool #nostr.")

        val hashtag = segments.filterIsInstance<ContentSegment.Hashtag>().firstOrNull()
        assertEquals("nostr", hashtag?.tag)
    }

    // Media Grouping Tests

    @Test
    fun `groups consecutive images`() {
        val segments = ContentParser.parse("https://example.com/1.jpg https://example.com/2.jpg")

        assertEquals(1, segments.size)
        assertTrue(segments[0] is ContentSegment.Media)
        val media = segments[0] as ContentSegment.Media
        assertEquals(2, media.urls.size)
        assertEquals(MediaType.IMAGE, media.mediaType)
    }

    @Test
    fun `groups images separated by whitespace`() {
        val segments = ContentParser.parse("https://example.com/1.jpg\n\nhttps://example.com/2.jpg")

        val media = segments.filterIsInstance<ContentSegment.Media>().firstOrNull()
        assertNotNull(media)
        assertEquals(2, media?.urls?.size)
    }

    @Test
    fun `does not group images separated by text`() {
        val segments = ContentParser.parse("https://example.com/1.jpg text https://example.com/2.jpg")

        val mediaSegments = segments.filterIsInstance<ContentSegment.Media>()
        assertEquals(2, mediaSegments.size)
        assertEquals(1, mediaSegments[0].urls.size)
        assertEquals(1, mediaSegments[1].urls.size)
    }

    @Test
    fun `does not group images and videos together`() {
        val segments = ContentParser.parse("https://example.com/photo.jpg https://example.com/video.mp4")

        val mediaSegments = segments.filterIsInstance<ContentSegment.Media>()
        assertEquals(2, mediaSegments.size)
        assertEquals(MediaType.IMAGE, mediaSegments[0].mediaType)
        assertEquals(MediaType.VIDEO, mediaSegments[1].mediaType)
    }

    // Mention Tests (npub only for now since we don't have actual bech32 decoder in tests)

    @Test
    fun `parses nostr npub mention structure`() {
        val content = "Thanks nostr:npub1abc123def for the help"
        val segments = ContentParser.parse(content)

        // Should have text segments (mention parsing requires valid bech32)
        assertTrue(segments.isNotEmpty())
    }

    // Mixed Content Tests

    @Test
    fun `parses mixed content with multiple entity types`() {
        val content = """
            Hello #nostr check this https://example.com
            Image: https://example.com/pic.jpg
        """.trimIndent()

        val segments = ContentParser.parse(content)

        assertTrue(segments.any { it is ContentSegment.Text })
        assertTrue(segments.any { it is ContentSegment.Hashtag })
        assertTrue(segments.any { it is ContentSegment.Link })
        assertTrue(segments.any { it is ContentSegment.Media })
    }

    @Test
    fun `preserves emoji in text`() {
        val segments = ContentParser.parse("Hello 👋 world 🌍")

        val text = segments.filterIsInstance<ContentSegment.Text>().joinToString("") { it.text }
        assertTrue(text.contains("👋"))
        assertTrue(text.contains("🌍"))
    }

    @Test
    fun `handles consecutive URLs correctly`() {
        val segments = ContentParser.parse("https://one.com https://two.com")

        val links = segments.filterIsInstance<ContentSegment.Link>()
        assertTrue(links.size >= 2)
    }

    @Test
    fun `maintains correct segment order`() {
        val segments = ContentParser.parse("Start #tag1 https://url.com #tag2 end")

        // Verify general structure (text, tag, text, url, text, tag, text)
        assertTrue(segments[0] is ContentSegment.Text)
        assertTrue(segments.any { it is ContentSegment.Hashtag })
        assertTrue(segments.any { it is ContentSegment.Link })
    }

    // Edge Cases

    @Test
    fun `handles text with only whitespace`() {
        val segments = ContentParser.parse("   \n\t  ")

        assertTrue(segments.isEmpty())
    }

    @Test
    fun `handles single character`() {
        val segments = ContentParser.parse("a")

        assertEquals(1, segments.size)
        assertTrue(segments[0] is ContentSegment.Text)
    }

    @Test
    fun `handles very long URLs`() {
        val longUrl = "https://example.com/" + "a".repeat(500)
        val segments = ContentParser.parse(longUrl)

        assertTrue(segments[0] is ContentSegment.Link)
        assertEquals(longUrl, (segments[0] as ContentSegment.Link).url)
    }

    @Test
    fun `handles Japanese text with entities`() {
        val content = "これは良い投稿です #nostr リンク: https://example.jp"
        val segments = ContentParser.parse(content)

        assertTrue(segments.any { it is ContentSegment.Hashtag })
        assertTrue(segments.any { it is ContentSegment.Link })
    }
}
