package io.nostr.ndk.compose.content

/**
 * Callbacks for user interactions with content segments.
 *
 * Used by RenderedContent to handle clicks on various content types.
 *
 * ## Usage
 *
 * ```kotlin
 * RenderedContent(
 *     ndk = ndk,
 *     event = note,
 *     callbacks = ContentCallbacks(
 *         onUserClick = { pubkey -> navController.navigate("profile/$pubkey") },
 *         onEventClick = { eventId -> navController.navigate("thread/$eventId") },
 *         onHashtagClick = { tag -> navController.navigate("search?q=%23$tag") },
 *         onLinkClick = { url -> openBrowser(url) },
 *         onMediaClick = { urls, index -> openGallery(urls, index) }
 *     )
 * )
 * ```
 */
data class ContentCallbacks(
    val onUserClick: ((pubkey: String) -> Unit)? = null,
    val onEventClick: ((eventId: String) -> Unit)? = null,
    val onHashtagClick: ((tag: String) -> Unit)? = null,
    val onLinkClick: ((url: String) -> Unit)? = null,
    val onMediaClick: ((urls: List<String>, index: Int) -> Unit)? = null
)
