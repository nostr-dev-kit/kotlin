package io.nostr.ndk.relay.messages

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.nostr.ndk.models.EventId
import io.nostr.ndk.models.NDKEvent

/**
 * Messages sent from relay to client.
 *
 * Per Nostr protocol, relay messages are JSON arrays where the first element
 * is the message type string.
 */
sealed class RelayMessage {
    /**
     * EVENT message - relay sends an event matching a subscription.
     * Format: ["EVENT", <subscription_id>, <event JSON>]
     */
    data class Event(
        val subscriptionId: String,
        val event: NDKEvent
    ) : RelayMessage()

    /**
     * EOSE message - End Of Stored Events.
     * Relay has sent all cached events for a subscription.
     * Format: ["EOSE", <subscription_id>]
     */
    data class Eose(
        val subscriptionId: String
    ) : RelayMessage()

    /**
     * OK message - relay's response to an EVENT message.
     * Format: ["OK", <event_id>, <true|false>, <message>]
     */
    data class Ok(
        val eventId: EventId,
        val success: Boolean,
        val message: String
    ) : RelayMessage()

    /**
     * NOTICE message - relay sends a human-readable message.
     * Format: ["NOTICE", <message>]
     */
    data class Notice(
        val message: String
    ) : RelayMessage()

    /**
     * AUTH message - relay requires authentication (NIP-42).
     * Format: ["AUTH", <challenge>]
     */
    data class Auth(
        val challenge: String
    ) : RelayMessage()

    /**
     * CLOSED message - relay closed a subscription.
     * Format: ["CLOSED", <subscription_id>, <message>]
     */
    data class Closed(
        val subscriptionId: String,
        val message: String
    ) : RelayMessage()

    /**
     * COUNT message - relay sends event count for a subscription (NIP-45).
     * Format: ["COUNT", <subscription_id>, {"count": <number>}]
     */
    data class Count(
        val subscriptionId: String,
        val count: Int
    ) : RelayMessage()

    companion object {
        private val objectMapper: ObjectMapper = jacksonObjectMapper()

        /**
         * Parses a JSON string into a RelayMessage.
         *
         * @param json JSON string from relay
         * @return Parsed RelayMessage
         * @throws IllegalArgumentException if JSON is invalid or message type is unknown
         */
        fun parse(json: String): RelayMessage {
            val array: List<Any> = try {
                objectMapper.readValue(json)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid JSON: ${e.message}", e)
            }

            if (array.isEmpty()) {
                throw IllegalArgumentException("Empty message array")
            }

            val type = array[0] as? String
                ?: throw IllegalArgumentException("Message type must be a string")

            return when (type) {
                "EVENT" -> parseEvent(array)
                "EOSE" -> parseEose(array)
                "OK" -> parseOk(array)
                "NOTICE" -> parseNotice(array)
                "AUTH" -> parseAuth(array)
                "CLOSED" -> parseClosed(array)
                "COUNT" -> parseCount(array)
                else -> throw IllegalArgumentException("Unknown message type: $type")
            }
        }

        private fun parseEvent(array: List<Any>): Event {
            if (array.size < 3) {
                throw IllegalArgumentException("EVENT message requires 3 elements")
            }

            val subscriptionId = array[1] as? String
                ?: throw IllegalArgumentException("Subscription ID must be a string")

            val eventJson = objectMapper.writeValueAsString(array[2])
            val event = NDKEvent.fromJson(eventJson)

            return Event(subscriptionId, event)
        }

        private fun parseEose(array: List<Any>): Eose {
            if (array.size < 2) {
                throw IllegalArgumentException("EOSE message requires 2 elements")
            }

            val subscriptionId = array[1] as? String
                ?: throw IllegalArgumentException("Subscription ID must be a string")

            return Eose(subscriptionId)
        }

        private fun parseOk(array: List<Any>): Ok {
            if (array.size < 4) {
                throw IllegalArgumentException("OK message requires 4 elements")
            }

            val eventId = array[1] as? String
                ?: throw IllegalArgumentException("Event ID must be a string")

            val success = array[2] as? Boolean
                ?: throw IllegalArgumentException("Success flag must be a boolean")

            val message = array[3] as? String
                ?: throw IllegalArgumentException("Message must be a string")

            return Ok(eventId, success, message)
        }

        private fun parseNotice(array: List<Any>): Notice {
            if (array.size < 2) {
                throw IllegalArgumentException("NOTICE message requires 2 elements")
            }

            val message = array[1] as? String
                ?: throw IllegalArgumentException("Message must be a string")

            return Notice(message)
        }

        private fun parseAuth(array: List<Any>): Auth {
            if (array.size < 2) {
                throw IllegalArgumentException("AUTH message requires 2 elements")
            }

            val challenge = array[1] as? String
                ?: throw IllegalArgumentException("Challenge must be a string")

            return Auth(challenge)
        }

        private fun parseClosed(array: List<Any>): Closed {
            if (array.size < 3) {
                throw IllegalArgumentException("CLOSED message requires 3 elements")
            }

            val subscriptionId = array[1] as? String
                ?: throw IllegalArgumentException("Subscription ID must be a string")

            val message = array[2] as? String
                ?: throw IllegalArgumentException("Message must be a string")

            return Closed(subscriptionId, message)
        }

        private fun parseCount(array: List<Any>): Count {
            if (array.size < 3) {
                throw IllegalArgumentException("COUNT message requires 3 elements")
            }

            val subscriptionId = array[1] as? String
                ?: throw IllegalArgumentException("Subscription ID must be a string")

            @Suppress("UNCHECKED_CAST")
            val countMap = array[2] as? Map<String, Any>
                ?: throw IllegalArgumentException("Count data must be an object")

            val count = (countMap["count"] as? Number)?.toInt()
                ?: throw IllegalArgumentException("Count must be a number")

            return Count(subscriptionId, count)
        }
    }
}
