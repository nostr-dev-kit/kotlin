package io.nostr.ndk.compose.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import io.nostr.ndk.NDK
import io.nostr.ndk.content.ContentRendererRegistry
import io.nostr.ndk.content.ContentSegment
import io.nostr.ndk.content.parseContent
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.compose.content.renderer.mentions.BasicMentionRenderer
import io.nostr.ndk.compose.content.renderer.links.BasicLinkRenderer
import io.nostr.ndk.compose.content.renderer.links.LinkPreviewRenderer
import io.nostr.ndk.compose.content.renderer.hashtags.BasicHashtagRenderer
import io.nostr.ndk.compose.content.renderer.media.BasicMediaRenderer
import io.nostr.ndk.compose.content.renderer.events.*

/**
 * Main composable for rendering parsed event content.
 *
 * This is the primary entry point for displaying Nostr event content
 * with rich formatting including mentions, links, hashtags, and media.
 *
 * ## Usage
 *
 * ```kotlin
 * RenderedContent(
 *     ndk = ndk,
 *     event = note,
 *     callbacks = ContentCallbacks(
 *         onUserClick = { pubkey -> navController.navigate("profile/$pubkey") },
 *         onMediaClick = { urls, index -> openGallery(urls, index) }
 *     ),
 *     settings = ContentRendererSettings(
 *         articleStyle = RendererStyle.CARD,
 *         enableLinkPreviews = true
 *     )
 * )
 * ```
 *
 * @param ndk NDK instance for fetching related data
 * @param event The event to render
 * @param callbacks User interaction callbacks
 * @param modifier Compose modifier
 * @param textStyle Text style for text segments
 * @param registry Optional custom renderer registry
 * @param settings Renderer style and behavior settings
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RenderedContent(
    ndk: NDK,
    event: NDKEvent,
    callbacks: ContentCallbacks = ContentCallbacks(),
    modifier: Modifier = Modifier,
    textStyle: TextStyle = LocalTextStyle.current,
    registry: ContentRendererRegistry? = null,
    settings: ContentRendererSettings = ContentRendererSettings()
) {
    val segments = remember(event.id) {
        event.parseContent()
    }

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        segments.forEach { segment ->
            RenderSegment(
                ndk = ndk,
                segment = segment,
                callbacks = callbacks,
                textStyle = textStyle,
                registry = registry,
                settings = settings
            )
        }
    }
}

/**
 * Renders a single content segment using default or custom renderers.
 */
@Composable
private fun RenderSegment(
    ndk: NDK,
    segment: ContentSegment,
    callbacks: ContentCallbacks,
    textStyle: TextStyle,
    registry: ContentRendererRegistry?,
    settings: ContentRendererSettings
) {
    // Check for custom renderer first
    if (registry != null) {
        val customRenderer = registry.getRenderer(segment)
        if (customRenderer != null) {
            customRenderer(segment)
            return
        }
    }

    // Use default renderers based on settings
    when (segment) {
        is ContentSegment.Text -> {
            Text(
                text = segment.text,
                style = textStyle
            )
        }

        is ContentSegment.Mention -> {
            BasicMentionRenderer(
                ndk = ndk,
                segment = segment,
                onClick = callbacks.onUserClick,
                showAvatar = settings.showAvatarsInMentions
            )
        }

        is ContentSegment.EventMention -> {
            // Choose renderer based on kind and settings
            when {
                segment.kind == 30023 -> {
                    // Article - use article style setting
                    when (settings.articleStyle) {
                        RendererStyle.CARD -> ArticleCardRenderer(
                            ndk = ndk,
                            segment = segment,
                            onClick = callbacks.onEventClick
                        )
                        RendererStyle.COMPACT -> ArticleCompactRenderer(
                            ndk = ndk,
                            segment = segment,
                            onClick = callbacks.onEventClick
                        )
                        RendererStyle.MINIMAL -> ArticleMinimalRenderer(
                            ndk = ndk,
                            segment = segment,
                            onClick = callbacks.onEventClick
                        )
                        else -> ArticleCardRenderer(
                            ndk = ndk,
                            segment = segment,
                            onClick = callbacks.onEventClick
                        )
                    }
                }
                else -> {
                    // Regular event - use event mention style setting
                    when (settings.eventMentionStyle) {
                        RendererStyle.COMPACT -> EventMentionCompactRenderer(
                            ndk = ndk,
                            segment = segment,
                            onClick = callbacks.onEventClick
                        )
                        else -> BasicEventMentionRenderer(
                            ndk = ndk,
                            segment = segment,
                            onClick = callbacks.onEventClick
                        )
                    }
                }
            }
        }

        is ContentSegment.Hashtag -> {
            BasicHashtagRenderer(
                segment = segment,
                onClick = callbacks.onHashtagClick
            )
        }

        is ContentSegment.Link -> {
            // Choose link renderer based on settings
            when (settings.linkStyle) {
                RendererStyle.CARD -> {
                    if (settings.enableLinkPreviews) {
                        LinkPreviewRenderer(
                            segment = segment,
                            onClick = callbacks.onLinkClick,
                            enablePreview = true
                        )
                    } else {
                        BasicLinkRenderer(
                            segment = segment,
                            onClick = callbacks.onLinkClick
                        )
                    }
                }
                else -> {
                    BasicLinkRenderer(
                        segment = segment,
                        onClick = callbacks.onLinkClick
                    )
                }
            }
        }

        is ContentSegment.Media -> {
            BasicMediaRenderer(
                segment = segment,
                onClick = callbacks.onMediaClick,
                autoLoad = settings.autoLoadImages
            )
        }
    }
}
