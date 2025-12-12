package io.nostr.ndk.compose.content.renderer.links

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Utility for fetching OpenGraph metadata from URLs.
 *
 * Features:
 * - In-memory caching to prevent duplicate fetches
 * - Coroutine-based async fetching
 * - HTML meta tag parsing
 * - Timeout handling
 *
 * ## Usage
 *
 * ```kotlin
 * val ogData = OpenGraphFetcher.fetch("https://example.com")
 * if (ogData != null) {
 *     // Use the OpenGraph data
 *     println("Title: ${ogData.title}")
 *     println("Description: ${ogData.description}")
 * }
 * ```
 */
object OpenGraphFetcher {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // Simple in-memory cache (URL -> OpenGraphData)
    private val cache = ConcurrentHashMap<String, OpenGraphData>()

    /**
     * Fetch OpenGraph metadata for a URL.
     * Returns cached data if available.
     *
     * @param url The URL to fetch OpenGraph data from
     * @return OpenGraphData if successfully parsed, null otherwise
     */
    suspend fun fetch(url: String): OpenGraphData? = withContext(Dispatchers.IO) {
        // Check cache first
        cache[url]?.let { return@withContext it }

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (compatible; NDK/1.0)")
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext null
            }

            val html = response.body?.string() ?: return@withContext null

            val ogData = parseOpenGraph(html, url)

            // Cache the result
            if (ogData != null) {
                cache[url] = ogData
            }

            ogData
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse OpenGraph meta tags from HTML.
     *
     * @param html The HTML content to parse
     * @param fallbackUrl The URL to use as fallback for the url field
     * @return OpenGraphData if tags found, null otherwise
     */
    private fun parseOpenGraph(html: String, fallbackUrl: String): OpenGraphData? {
        val ogTags = mutableMapOf<String, String>()

        // Simple regex-based extraction of og: meta tags
        val metaTagRegex = """<meta\s+property=["']og:([^"']+)["']\s+content=["']([^"']+)["']""".toRegex()
        val altMetaTagRegex = """<meta\s+content=["']([^"']+)["']\s+property=["']og:([^"']+)["']""".toRegex()

        metaTagRegex.findAll(html).forEach { match ->
            val property = match.groupValues[1]
            val content = match.groupValues[2]
            ogTags[property] = content
        }

        altMetaTagRegex.findAll(html).forEach { match ->
            val content = match.groupValues[1]
            val property = match.groupValues[2]
            ogTags[property] = content
        }

        // If no og tags found, return null
        if (ogTags.isEmpty()) {
            return null
        }

        return OpenGraphData(
            title = ogTags["title"],
            description = ogTags["description"],
            image = ogTags["image"],
            siteName = ogTags["site_name"],
            url = ogTags["url"] ?: fallbackUrl,
            type = ogTags["type"]
        )
    }

    /**
     * Clear the cache (useful for testing or memory management).
     */
    fun clearCache() {
        cache.clear()
    }
}
