package io.nostr.ndk.compose.content

/**
 * Renderer style options for different content types.
 */
enum class RendererStyle {
    DEFAULT,
    COMPACT,
    CARD,
    MINIMAL
}

/**
 * Settings for content renderers.
 *
 * Controls which renderer variant is used for different content types
 * and other rendering behavior.
 *
 * ## Usage
 *
 * ```kotlin
 * val settings = ContentRendererSettings(
 *     articleStyle = RendererStyle.CARD,
 *     linkStyle = RendererStyle.CARD,
 *     enableLinkPreviews = true,
 *     showAvatarsInMentions = true
 * )
 *
 * RenderedContent(
 *     ndk = ndk,
 *     event = note,
 *     settings = settings
 * )
 * ```
 */
data class ContentRendererSettings(
    val eventMentionStyle: RendererStyle = RendererStyle.DEFAULT,
    val articleStyle: RendererStyle = RendererStyle.CARD,
    val mentionStyle: RendererStyle = RendererStyle.DEFAULT,
    val linkStyle: RendererStyle = RendererStyle.DEFAULT,
    val mediaStyle: RendererStyle = RendererStyle.DEFAULT,
    val showAvatarsInMentions: Boolean = true,
    val enableLinkPreviews: Boolean = true
)
