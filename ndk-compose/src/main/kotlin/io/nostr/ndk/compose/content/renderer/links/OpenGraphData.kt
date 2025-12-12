package io.nostr.ndk.compose.content.renderer.links

import androidx.compose.runtime.Immutable

/**
 * OpenGraph metadata extracted from a webpage.
 *
 * Used by the link preview renderer to display rich previews of links.
 *
 * ## Usage
 *
 * ```kotlin
 * val ogData = OpenGraphFetcher.fetch("https://example.com")
 * if (ogData != null) {
 *     // Display preview card
 * }
 * ```
 */
@Immutable
data class OpenGraphData(
    val title: String? = null,
    val description: String? = null,
    val image: String? = null,
    val siteName: String? = null,
    val url: String? = null,
    val type: String? = null
)
