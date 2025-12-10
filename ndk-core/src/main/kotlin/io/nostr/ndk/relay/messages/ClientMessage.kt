package io.nostr.ndk.relay.messages

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKFilter

/**
 * Messages sent from client to relay.
 *
 * Per Nostr protocol, client messages are JSON arrays where the first element
 * is the message type string.
 */
sealed class ClientMessage {
    /**
     * REQ message - subscribe to events matching filters.
     * Format: ["REQ", <subscription_id>, <filter JSON>, <filter JSON>, ...]
     */
    data class Req(
        val subscriptionId: String,
        val filters: List<NDKFilter>
    ) : ClientMessage() {
        override fun toJson(): String {
            val array = mutableListOf<Any>("REQ", subscriptionId)
            filters.forEach { filter ->
                array.add(parseJsonToMap(filter.toJson()))
            }
            return objectMapper.writeValueAsString(array)
        }
    }

    /**
     * CLOSE message - close a subscription.
     * Format: ["CLOSE", <subscription_id>]
     */
    data class Close(
        val subscriptionId: String
    ) : ClientMessage() {
        override fun toJson(): String {
            return objectMapper.writeValueAsString(listOf("CLOSE", subscriptionId))
        }
    }

    /**
     * EVENT message - publish an event.
     * Format: ["EVENT", <event JSON>]
     */
    data class Event(
        val event: NDKEvent
    ) : ClientMessage() {
        override fun toJson(): String {
            return objectMapper.writeValueAsString(
                listOf("EVENT", parseJsonToMap(event.toJson()))
            )
        }
    }

    /**
     * AUTH message - authenticate with relay (NIP-42).
     * Format: ["AUTH", <signed event JSON>]
     */
    data class Auth(
        val event: NDKEvent
    ) : ClientMessage() {
        override fun toJson(): String {
            return objectMapper.writeValueAsString(
                listOf("AUTH", parseJsonToMap(event.toJson()))
            )
        }
    }

    /**
     * Serializes this message to JSON string.
     *
     * @return JSON string representation
     */
    abstract fun toJson(): String

    companion object {
        private val objectMapper: ObjectMapper = jacksonObjectMapper()

        /**
         * Helper to parse JSON string to Map for embedding in arrays.
         */
        private fun parseJsonToMap(json: String): Map<String, Any?> {
            return objectMapper.readValue(json, Map::class.java) as Map<String, Any?>
        }
    }
}
