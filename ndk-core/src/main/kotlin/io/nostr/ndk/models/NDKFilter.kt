package io.nostr.ndk.models

import androidx.compose.runtime.Immutable
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

/**
 * Immutable representation of a Nostr filter per NIP-01.
 *
 * Filters are used to subscribe to events from relays. A filter matches an event
 * if ALL specified criteria match (AND logic).
 *
 * @property ids Set of event IDs to match (prefix matching supported by relays)
 * @property authors Set of author public keys to match (prefix matching supported by relays)
 * @property kinds Set of event kinds to match
 * @property since Unix timestamp (seconds) - match events created at or after this time
 * @property until Unix timestamp (seconds) - match events created at or before this time
 * @property limit Maximum number of events to return (hint to relay, not enforced client-side)
 * @property search Full-text search query (relay-side only, not enforced client-side)
 * @property tags Map of tag names to sets of values (e.g., {"e": ["event_id1", "event_id2"]})
 */
@Immutable
data class NDKFilter(
    val ids: Set<EventId>? = null,
    val authors: Set<PublicKey>? = null,
    val kinds: Set<Int>? = null,
    val since: Timestamp? = null,
    val until: Timestamp? = null,
    val limit: Int? = null,
    val search: String? = null,
    val tags: Map<String, Set<String>> = emptyMap()
) {
    /**
     * Checks if an event matches this filter.
     *
     * An event matches if ALL specified criteria match:
     * - If ids is set, event.id must be in the set
     * - If authors is set, event.pubkey must be in the set
     * - If kinds is set, event.kind must be in the set
     * - If since is set, event.createdAt must be >= since
     * - If until is set, event.createdAt must be <= until
     * - If tags are set, event must have matching tags for ALL specified tag names
     *
     * Note: limit and search are relay-side hints and don't affect client-side matching
     *
     * @param event The event to check
     * @return True if the event matches this filter
     */
    fun matches(event: NDKEvent): Boolean {
        // Check ids
        if (ids != null && event.id !in ids) {
            return false
        }

        // Check authors
        if (authors != null && event.pubkey !in authors) {
            return false
        }

        // Check kinds
        if (kinds != null && event.kind !in kinds) {
            return false
        }

        // Check since
        if (since != null && event.createdAt < since) {
            return false
        }

        // Check until
        if (until != null && event.createdAt > until) {
            return false
        }

        // Check tags
        for ((tagName, tagValues) in tags) {
            val eventTagValues = event.tagsWithName(tagName)
                .mapNotNull { it.values.firstOrNull() }
                .toSet()

            // Check if any of the filter's tag values match the event's tag values
            val hasMatch = tagValues.any { it in eventTagValues }
            if (!hasMatch) {
                return false
            }
        }

        // Note: limit and search are relay-side only, don't affect matching
        return true
    }

    /**
     * Serializes the filter to JSON string per NIP-01.
     *
     * Only non-null fields are included in the output.
     * Tags are serialized with "#" prefix (e.g., "#e", "#p").
     *
     * @return JSON representation of the filter
     */
    fun toJson(): String {
        val map = mutableMapOf<String, Any>()

        ids?.let { map["ids"] = it.toList() }
        authors?.let { map["authors"] = it.toList() }
        kinds?.let { map["kinds"] = it.toList() }
        since?.let { map["since"] = it }
        until?.let { map["until"] = it }
        limit?.let { map["limit"] = it }
        search?.let { map["search"] = it }

        // Add tag filters with "#" prefix
        tags.forEach { (name, values) ->
            map["#$name"] = values.toList()
        }

        return objectMapper.writeValueAsString(map)
    }

    /**
     * Generates a deterministic fingerprint for this filter.
     *
     * The fingerprint is used for subscription grouping - filters with the same
     * fingerprint can be combined into a single relay subscription.
     *
     * Temporal constraints (since, until, limit) are excluded from the fingerprint
     * because they vary frequently but don't affect the core filter criteria.
     *
     * @return Deterministic fingerprint string
     */
    fun fingerprint(): String {
        val parts = mutableListOf<String>()

        // Sort all sets to ensure deterministic output
        ids?.sorted()?.let { parts.add("i:${it.joinToString(",")}") }
        authors?.sorted()?.let { parts.add("a:${it.joinToString(",")}") }
        kinds?.sorted()?.let { parts.add("k:${it.joinToString(",")}") }

        // Add tags in sorted order
        tags.entries.sortedBy { it.key }.forEach { (name, values) ->
            parts.add("#$name:${values.sorted().joinToString(",")}")
        }

        // Don't include since/until/limit/search - those vary
        return parts.joinToString("|")
    }

    /**
     * Creates a copy of this filter without temporal constraints.
     *
     * This is useful for cache queries where temporal constraints
     * should not limit the cached results.
     *
     * @return A new filter with since, until, and limit removed
     */
    fun withoutTemporalConstraints(): NDKFilter {
        return copy(since = null, until = null, limit = null)
    }

    companion object {
        private val objectMapper: ObjectMapper = jacksonObjectMapper()

        /**
         * Deserializes a filter from JSON string.
         *
         * Tags with "#" prefix (e.g., "#e", "#p") are parsed into the tags map.
         *
         * @param json JSON representation of the filter
         * @return Deserialized NDKFilter
         */
        fun fromJson(json: String): NDKFilter {
            val map: Map<String, Any?> = objectMapper.readValue(json)

            // Parse arrays to sets
            @Suppress("UNCHECKED_CAST")
            val ids = (map["ids"] as? List<String>)?.toSet()

            @Suppress("UNCHECKED_CAST")
            val authors = (map["authors"] as? List<String>)?.toSet()

            @Suppress("UNCHECKED_CAST")
            val kindsRaw = map["kinds"] as? List<Number>
            val kinds = kindsRaw?.map { it.toInt() }?.toSet()

            val since = (map["since"] as? Number)?.toLong()
            val until = (map["until"] as? Number)?.toLong()
            val limit = (map["limit"] as? Number)?.toInt()
            val search = map["search"] as? String

            // Parse tag filters (keys starting with "#")
            val tags = mutableMapOf<String, Set<String>>()
            map.forEach { (key, value) ->
                if (key.startsWith("#")) {
                    val tagName = key.substring(1)
                    @Suppress("UNCHECKED_CAST")
                    val tagValues = (value as? List<String>)?.toSet() ?: emptySet()
                    tags[tagName] = tagValues
                }
            }

            return NDKFilter(
                ids = ids,
                authors = authors,
                kinds = kinds,
                since = since,
                until = until,
                limit = limit,
                search = search,
                tags = tags
            )
        }
    }
}
