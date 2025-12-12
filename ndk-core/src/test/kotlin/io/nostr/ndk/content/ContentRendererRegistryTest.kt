package io.nostr.ndk.content

import org.junit.Test
import org.junit.Assert.*
import org.junit.Before

/**
 * Tests for ContentRendererRegistry.
 *
 * Tests cover renderer registration, lookup, manual override,
 * and default renderer fallback behavior.
 */
class ContentRendererRegistryTest {

    private lateinit var registry: ContentRendererRegistry

    @Before
    fun setup() {
        registry = ContentRendererRegistry()
    }

    @Test
    fun `register adds renderer for segment type`() {
        var invoked = false
        val renderer: SegmentRenderer = { invoked = true }

        registry.register<ContentSegment.Link>(renderer)

        val retrieved = registry.getRenderer(ContentSegment.Link::class.java)
        assertNotNull(retrieved)
        retrieved?.invoke(ContentSegment.Link("test"))
        assertTrue(invoked)
    }

    @Test
    fun `getRenderer returns null for unregistered type`() {
        val renderer = registry.getRenderer(ContentSegment.Link::class.java)

        assertNull(renderer)
    }

    @Test
    fun `register overwrites existing renderer for same type`() {
        var callCount = 0

        val renderer1: SegmentRenderer = { callCount = 1 }
        val renderer2: SegmentRenderer = { callCount = 2 }

        registry.register<ContentSegment.Link>(renderer1)
        registry.register<ContentSegment.Link>(renderer2)

        val retrieved = registry.getRenderer(ContentSegment.Link::class.java)
        retrieved?.invoke(ContentSegment.Link("test"))

        assertEquals(2, callCount)
    }

    @Test
    fun `getRenderer works with segment instance`() {
        var invoked = false
        val renderer: SegmentRenderer = { invoked = true }

        registry.register<ContentSegment.Text>(renderer)

        val segment = ContentSegment.Text("hello")
        val retrieved = registry.getRenderer(segment)

        assertNotNull(retrieved)
        retrieved?.invoke(segment)
        assertTrue(invoked)
    }

    @Test
    fun `unregister removes renderer`() {
        val renderer: SegmentRenderer = { }

        registry.register<ContentSegment.Text>(renderer)
        assertTrue(registry.hasRenderer<ContentSegment.Text>())

        registry.unregister<ContentSegment.Text>()
        assertFalse(registry.hasRenderer<ContentSegment.Text>())
    }

    @Test
    fun `unregister with class works`() {
        val renderer: SegmentRenderer = { }

        registry.register<ContentSegment.Link>(renderer)
        registry.unregister(ContentSegment.Link::class.java)

        assertNull(registry.getRenderer(ContentSegment.Link::class.java))
    }

    @Test
    fun `clear removes all registered renderers`() {
        registry.register<ContentSegment.Text> { }
        registry.register<ContentSegment.Link> { }
        registry.register<ContentSegment.Hashtag> { }

        assertTrue(registry.hasRenderer<ContentSegment.Text>())
        assertTrue(registry.hasRenderer<ContentSegment.Link>())

        registry.clear()

        assertFalse(registry.hasRenderer<ContentSegment.Text>())
        assertFalse(registry.hasRenderer<ContentSegment.Link>())
        assertFalse(registry.hasRenderer<ContentSegment.Hashtag>())
    }

    @Test
    fun `hasRenderer returns correct value`() {
        assertFalse(registry.hasRenderer<ContentSegment.Text>())

        registry.register<ContentSegment.Text> { }

        assertTrue(registry.hasRenderer<ContentSegment.Text>())
    }

    @Test
    fun `hasRenderer with class works`() {
        assertFalse(registry.hasRenderer(ContentSegment.Link::class.java))

        registry.register<ContentSegment.Link> { }

        assertTrue(registry.hasRenderer(ContentSegment.Link::class.java))
    }

    @Test
    fun `can register multiple different types`() {
        var textCalled = false
        var linkCalled = false
        var hashtagCalled = false

        registry.register<ContentSegment.Text> { textCalled = true }
        registry.register<ContentSegment.Link> { linkCalled = true }
        registry.register<ContentSegment.Hashtag> { hashtagCalled = true }

        registry.getRenderer(ContentSegment.Text::class.java)?.invoke(ContentSegment.Text("test"))
        registry.getRenderer(ContentSegment.Link::class.java)?.invoke(ContentSegment.Link("url"))
        registry.getRenderer(ContentSegment.Hashtag::class.java)?.invoke(ContentSegment.Hashtag("tag"))

        assertTrue(textCalled)
        assertTrue(linkCalled)
        assertTrue(hashtagCalled)
    }

    @Test
    fun `registering does not affect other types`() {
        registry.register<ContentSegment.Text> { }

        assertTrue(registry.hasRenderer<ContentSegment.Text>())
        assertFalse(registry.hasRenderer<ContentSegment.Link>())
        assertFalse(registry.hasRenderer<ContentSegment.Hashtag>())
    }

    @Test
    fun `can register all segment types`() {
        registry.register<ContentSegment.Text> { }
        registry.register<ContentSegment.Link> { }
        registry.register<ContentSegment.Hashtag> { }
        registry.register<ContentSegment.Mention> { }
        registry.register<ContentSegment.Media> { }

        assertTrue(registry.hasRenderer<ContentSegment.Text>())
        assertTrue(registry.hasRenderer<ContentSegment.Link>())
        assertTrue(registry.hasRenderer<ContentSegment.Hashtag>())
        assertTrue(registry.hasRenderer<ContentSegment.Mention>())
        assertTrue(registry.hasRenderer<ContentSegment.Media>())
    }

    @Test
    fun `registry is independent between instances`() {
        val registry1 = ContentRendererRegistry()
        val registry2 = ContentRendererRegistry()

        registry1.register<ContentSegment.Text> { }

        assertTrue(registry1.hasRenderer<ContentSegment.Text>())
        assertFalse(registry2.hasRenderer<ContentSegment.Text>())
    }

    @Test
    fun `can unregister and re-register`() {
        var callCount = 0

        registry.register<ContentSegment.Link> { callCount = 1 }
        registry.unregister<ContentSegment.Link>()
        registry.register<ContentSegment.Link> { callCount = 2 }

        registry.getRenderer(ContentSegment.Link::class.java)?.invoke(ContentSegment.Link("test"))

        assertEquals(2, callCount)
    }
}
