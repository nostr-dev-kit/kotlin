package io.nostr.ndk.content

/**
 * Type alias for a segment renderer function.
 *
 * Renderers are registered by apps to provide custom rendering logic.
 * The actual rendering implementation is up to the app (must have Compose UI deps).
 *
 * ## Usage
 *
 * In your app module with Compose UI:
 * ```kotlin
 * typealias ActualRenderer = @Composable (ContentSegment) -> Unit
 *
 * // In your composable:
 * when (segment) {
 *     is ContentSegment.Text -> Text(segment.text)
 *     is ContentSegment.Mention -> {
 *         val renderer = registry.getRenderer(segment::class.java)
 *         renderer?.invoke(segment) ?: DefaultMention(segment)
 *     }
 *     // ... other types
 * }
 * ```
 */
typealias SegmentRenderer = (ContentSegment) -> Unit

/**
 * Registry for custom segment renderers.
 *
 * This class lives in ndk-core and provides the registration API.
 * Apps with Compose UI dependencies register their custom renderers.
 *
 * Design:
 * - Simple manual registration (no auto-registration)
 * - No priority system (single renderer per type)
 * - Thread-safe via immutable map replacement
 *
 * ## Usage
 *
 * In app module:
 * ```kotlin
 * // Create a registry
 * val registry = ContentRendererRegistry()
 *
 * // Register custom renderers
 * registry.register<ContentSegment.Mention> { segment ->
 *     // Your @Composable rendering logic
 *     UserProfileChip(segment.pubkey)
 * }
 *
 * registry.register<ContentSegment.Media> { segment ->
 *     ImageGallery(segment.urls)
 * }
 *
 * // Use in rendering
 * val renderer = registry.getRenderer(segment::class.java)
 * if (renderer != null) {
 *     renderer(segment)
 * } else {
 *     DefaultRenderer(segment)
 * }
 * ```
 */
class ContentRendererRegistry {
    @Volatile
    private var renderers: Map<Class<out ContentSegment>, SegmentRenderer> = emptyMap()

    /**
     * Registers a custom renderer for a segment type.
     *
     * If a renderer is already registered for this type, it will be replaced.
     *
     * @param T The segment type to render
     * @param renderer The rendering function
     *
     * ## Example
     *
     * ```kotlin
     * registry.register<ContentSegment.Link> { segment ->
     *     LinkPreviewCard(url = segment.url)
     * }
     * ```
     */
    inline fun <reified T : ContentSegment> register(noinline renderer: SegmentRenderer) {
        register(T::class.java, renderer)
    }

    /**
     * Registers a custom renderer for a segment type (Java class version).
     *
     * @param segmentClass The class of the segment type
     * @param renderer The rendering function
     */
    @Synchronized
    fun register(segmentClass: Class<out ContentSegment>, renderer: SegmentRenderer) {
        renderers = renderers + (segmentClass to renderer)
    }

    /**
     * Gets the registered renderer for a segment type.
     *
     * Returns null if no renderer is registered for this type.
     *
     * @param segmentClass The class of the segment type
     * @return The registered renderer, or null
     *
     * ## Example
     *
     * ```kotlin
     * val renderer = registry.getRenderer(ContentSegment.Mention::class.java)
     * if (renderer != null) {
     *     renderer(segment)
     * } else {
     *     // Use default rendering
     *     Text(segment.bech32)
     * }
     * ```
     */
    fun getRenderer(segmentClass: Class<out ContentSegment>): SegmentRenderer? {
        return renderers[segmentClass]
    }

    /**
     * Gets the registered renderer for a segment (convenience).
     *
     * @param segment The segment instance
     * @return The registered renderer, or null
     */
    fun getRenderer(segment: ContentSegment): SegmentRenderer? {
        return getRenderer(segment.javaClass)
    }

    /**
     * Unregisters a renderer for a segment type.
     *
     * @param segmentClass The class of the segment type
     *
     * ## Example
     *
     * ```kotlin
     * registry.unregister(ContentSegment.Link::class.java)
     * ```
     */
    @Synchronized
    fun unregister(segmentClass: Class<out ContentSegment>) {
        renderers = renderers - segmentClass
    }

    /**
     * Unregisters a renderer for a segment type (inline version).
     *
     * @param T The segment type
     *
     * ## Example
     *
     * ```kotlin
     * registry.unregister<ContentSegment.Link>()
     * ```
     */
    inline fun <reified T : ContentSegment> unregister() {
        unregister(T::class.java)
    }

    /**
     * Clears all registered renderers.
     *
     * ## Example
     *
     * ```kotlin
     * registry.clear()
     * ```
     */
    @Synchronized
    fun clear() {
        renderers = emptyMap()
    }

    /**
     * Returns true if a renderer is registered for this segment type.
     *
     * @param segmentClass The class of the segment type
     * @return True if a renderer is registered
     */
    fun hasRenderer(segmentClass: Class<out ContentSegment>): Boolean {
        return renderers.containsKey(segmentClass)
    }

    /**
     * Returns true if a renderer is registered for this segment type (inline version).
     *
     * @param T The segment type
     * @return True if a renderer is registered
     *
     * ## Example
     *
     * ```kotlin
     * if (registry.hasRenderer<ContentSegment.Media>()) {
     *     // Use custom renderer
     * } else {
     *     // Use default
     * }
     * ```
     */
    inline fun <reified T : ContentSegment> hasRenderer(): Boolean {
        return hasRenderer(T::class.java)
    }
}
