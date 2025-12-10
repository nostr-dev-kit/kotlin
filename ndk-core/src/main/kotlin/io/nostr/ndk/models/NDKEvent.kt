package io.nostr.ndk.models

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.security.MessageDigest

/**
 * Immutable representation of a Nostr event per NIP-01.
 *
 * @property id Event ID (32-byte hex SHA256 hash)
 * @property pubkey Public key of the event creator (32-byte hex)
 * @property createdAt Unix timestamp in seconds
 * @property kind Event kind (determines event type)
 * @property tags List of type-safe tags
 * @property content Event content (text or JSON)
 * @property sig Schnorr signature (64-byte hex), null for unsigned events
 */
data class NDKEvent(
    val id: EventId,
    val pubkey: PublicKey,
    @JsonProperty("created_at")
    val createdAt: Timestamp,
    val kind: Int,
    val tags: List<NDKTag>,
    val content: String,
    val sig: Signature?
) {
    /**
     * Returns true if this is an ephemeral event (kind 20000-29999).
     * Ephemeral events are not stored by relays.
     */
    val isEphemeral: Boolean
        get() = kind in 20000..29999

    /**
     * Returns true if this is a replaceable event (kind 0, 3, or 10000-19999).
     * Only the latest replaceable event per kind+pubkey is stored.
     */
    val isReplaceable: Boolean
        get() = kind == 0 || kind == 3 || kind in 10000..19999

    /**
     * Returns true if this is a parameterized replaceable event (kind 30000-39999).
     * Only the latest event per kind+pubkey+d-tag is stored.
     */
    val isParameterizedReplaceable: Boolean
        get() = kind in 30000..39999

    /**
     * Generates a deduplication key for this event.
     *
     * For replaceable events, the key is "kind:pubkey".
     * For parameterized replaceable events, the key is "kind:pubkey:d-tag".
     * For regular events, the key is the event ID.
     */
    fun deduplicationKey(): String = when {
        isParameterizedReplaceable -> "$kind:$pubkey:${tagValue("d") ?: ""}"
        isReplaceable -> "$kind:$pubkey"
        else -> id
    }

    /**
     * Returns all tags with the specified name.
     *
     * @param name The tag name to filter by
     * @return List of matching tags
     */
    fun tagsWithName(name: String): List<NDKTag> {
        return tags.filter { it.name == name }
    }

    /**
     * Returns the first value of the first tag with the specified name.
     *
     * @param name The tag name to search for
     * @return The tag value or null if not found
     */
    fun tagValue(name: String): String? {
        return tags.firstOrNull { it.name == name }?.values?.firstOrNull()
    }

    /**
     * Returns all event IDs referenced in e tags.
     *
     * @return List of event IDs
     */
    fun referencedEventIds(): List<EventId> {
        return tagsWithName("e").mapNotNull { it.values.firstOrNull() }
    }

    /**
     * Returns all public keys referenced in p tags.
     *
     * @return List of public keys
     */
    fun referencedPubkeys(): List<PublicKey> {
        return tagsWithName("p").mapNotNull { it.values.firstOrNull() }
    }

    /**
     * Calculates the event ID per NIP-01 specification.
     *
     * The ID is the SHA256 hash of the serialized event:
     * [0, pubkey, created_at, kind, tags, content]
     *
     * @return The calculated event ID as 64-character hex string
     */
    fun calculateId(): EventId {
        // Serialize event per NIP-01: [0, pubkey, created_at, kind, tags, content]
        val serialized = serializeForId()

        // Calculate SHA256 hash
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(serialized.toByteArray(Charsets.UTF_8))

        // Convert to hex string
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * Validates that the event ID matches the calculated ID.
     *
     * @return True if the ID is valid
     */
    fun isIdValid(): Boolean {
        return id == calculateId()
    }

    /**
     * Serializes the event to JSON string.
     *
     * @return JSON representation of the event
     */
    fun toJson(): String {
        return objectMapper.writeValueAsString(toJsonMap())
    }

    /**
     * Serializes event for ID calculation per NIP-01.
     */
    private fun serializeForId(): String {
        val array = listOf(
            0,
            pubkey,
            createdAt,
            kind,
            tags.map { listOf(it.name) + it.values },
            content
        )
        return objectMapper.writeValueAsString(array)
    }

    /**
     * Converts event to JSON map for serialization.
     */
    private fun toJsonMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "pubkey" to pubkey,
            "created_at" to createdAt,
            "kind" to kind,
            "tags" to tags.map { listOf(it.name) + it.values },
            "content" to content,
            "sig" to sig
        )
    }

    companion object {
        private val objectMapper: ObjectMapper = jacksonObjectMapper()

        /**
         * Deserializes an event from JSON string.
         *
         * @param json JSON representation of the event
         * @return Deserialized NDKEvent
         */
        fun fromJson(json: String): NDKEvent {
            val map: Map<String, Any?> = objectMapper.readValue(json)

            @Suppress("UNCHECKED_CAST")
            val tagsArray = map["tags"] as? List<List<String>> ?: emptyList()
            val tags = tagsArray.map { tagArray ->
                if (tagArray.isEmpty()) {
                    throw IllegalArgumentException("Tag array cannot be empty")
                }
                NDKTag(tagArray[0], tagArray.drop(1))
            }

            return NDKEvent(
                id = map["id"] as? String ?: "",
                pubkey = map["pubkey"] as? String ?: "",
                createdAt = (map["created_at"] as? Number)?.toLong() ?: 0L,
                kind = (map["kind"] as? Number)?.toInt() ?: 0,
                tags = tags,
                content = map["content"] as? String ?: "",
                sig = map["sig"] as? String
            )
        }
    }
}
