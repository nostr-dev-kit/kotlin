package io.nostr.ndk.models

/**
 * Parsed NIP-92 media metadata from imeta tags.
 *
 * Format: ["imeta", "url <url> blurhash <hash> dim <w>x<h> m <mime> x <sha256> size <bytes> alt <text>"]
 */
data class ImetaTag(
    val url: String,
    val blurhash: String? = null,
    val dimensions: Pair<Int, Int>? = null,
    val mimeType: String? = null,
    val sha256: String? = null,
    val size: Long? = null,
    val alt: String? = null,
    val fallback: List<String> = emptyList()
) {
    companion object {
        /**
         * Parse an imeta tag into structured metadata.
         *
         * @param tag NDKTag with name "imeta"
         * @return Parsed ImetaTag or null if invalid
         */
        fun parse(tag: NDKTag): ImetaTag? {
            if (tag.name != "imeta") return null

            val metadata = mutableMapOf<String, String>()
            val fallbacks = mutableListOf<String>()

            // Parse space-separated key-value pairs
            tag.values.forEach { value ->
                val parts = value.split(" ")
                var i = 0
                while (i < parts.size - 1) {
                    val key = parts[i]
                    val v = parts[i + 1]

                    if (key == "fallback") {
                        fallbacks.add(v)
                    } else {
                        metadata[key] = v
                    }
                    i += 2
                }
            }

            // URL is required
            val url = metadata["url"] ?: return null

            return ImetaTag(
                url = url,
                blurhash = metadata["blurhash"],
                dimensions = metadata["dim"]?.let { parseDimensions(it) },
                mimeType = metadata["m"],
                sha256 = metadata["x"],
                size = metadata["size"]?.toLongOrNull(),
                alt = metadata["alt"],
                fallback = fallbacks
            )
        }

        private fun parseDimensions(dim: String): Pair<Int, Int>? {
            val parts = dim.split("x")
            if (parts.size != 2) return null
            val width = parts[0].toIntOrNull() ?: return null
            val height = parts[1].toIntOrNull() ?: return null
            return width to height
        }
    }
}
