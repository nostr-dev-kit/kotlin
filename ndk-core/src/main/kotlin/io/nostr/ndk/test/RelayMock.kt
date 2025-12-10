package io.nostr.ndk.test

import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKFilter
import io.nostr.ndk.relay.messages.ClientMessage
import io.nostr.ndk.relay.messages.RelayMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Mock relay implementation for testing NDK interactions.
 *
 * RelayMock simulates a Nostr relay without network connections, allowing
 * you to test event publishing, subscription handling, and error scenarios.
 *
 * Usage:
 * ```kotlin
 * @Test
 * fun `test event publishing`() = runTest {
 *     val relay = RelayMock("wss://mock.relay")
 *
 *     // Simulate successful publish
 *     relay.acceptNextPublish()
 *
 *     // Or simulate failure
 *     relay.rejectNextPublish("error: rate limited")
 *
 *     // Verify received messages
 *     val messages = relay.receivedMessages
 *     assertTrue(messages.any { it is ClientMessage.Event })
 * }
 * ```
 */
class RelayMock(
    val url: String = "wss://mock.relay"
) {
    private val _receivedMessages = mutableListOf<ClientMessage>()
    private val _sentMessages = MutableSharedFlow<RelayMessage>(replay = 100)
    private val subscriptions = mutableMapOf<String, NDKFilter>()

    /**
     * All client messages received by this mock relay.
     */
    val receivedMessages: List<ClientMessage>
        get() = _receivedMessages.toList()

    /**
     * Flow of messages sent from this relay to clients.
     */
    val sentMessages: Flow<RelayMessage> = _sentMessages.asSharedFlow()

    /**
     * Active subscription IDs.
     */
    val activeSubscriptions: Set<String>
        get() = subscriptions.keys.toSet()

    /**
     * Simulates receiving a client message (EVENT, REQ, CLOSE).
     */
    suspend fun receiveClientMessage(message: ClientMessage) {
        _receivedMessages.add(message)

        when (message) {
            is ClientMessage.Event -> {
                // Default behavior: accept the event
            }
            is ClientMessage.Req -> {
                subscriptions[message.subscriptionId] = message.filters.first()
            }
            is ClientMessage.Close -> {
                subscriptions.remove(message.subscriptionId)
            }
            else -> {}
        }
    }

    /**
     * Simulates the relay accepting an event publish.
     * Sends OK message with success=true.
     */
    suspend fun acceptEvent(eventId: String, message: String = "") {
        _sentMessages.emit(RelayMessage.Ok(eventId, true, message))
    }

    /**
     * Simulates the relay rejecting an event publish.
     * Sends OK message with success=false.
     */
    suspend fun rejectEvent(eventId: String, reason: String) {
        _sentMessages.emit(RelayMessage.Ok(eventId, false, reason))
    }

    /**
     * Sends an event to subscribers as if it came from the relay.
     */
    suspend fun sendEvent(subscriptionId: String, event: NDKEvent) {
        _sentMessages.emit(RelayMessage.Event(subscriptionId, event))
    }

    /**
     * Sends EOSE (End of Stored Events) for a subscription.
     */
    suspend fun sendEose(subscriptionId: String) {
        _sentMessages.emit(RelayMessage.Eose(subscriptionId))
    }

    /**
     * Sends a NOTICE message.
     */
    suspend fun sendNotice(message: String) {
        _sentMessages.emit(RelayMessage.Notice(message))
    }

    /**
     * Sends a CLOSED message for a subscription.
     */
    suspend fun sendClosed(subscriptionId: String, reason: String) {
        _sentMessages.emit(RelayMessage.Closed(subscriptionId, reason))
    }

    /**
     * Sends an AUTH challenge.
     */
    suspend fun sendAuthChallenge(challenge: String) {
        _sentMessages.emit(RelayMessage.Auth(challenge))
    }

    /**
     * Clears all received messages and subscriptions.
     */
    fun reset() {
        _receivedMessages.clear()
        subscriptions.clear()
    }

    /**
     * Gets the filter for a specific subscription.
     */
    fun getSubscriptionFilter(subscriptionId: String): NDKFilter? {
        return subscriptions[subscriptionId]
    }

    /**
     * Checks if a subscription is active.
     */
    fun isSubscriptionActive(subscriptionId: String): Boolean {
        return subscriptions.containsKey(subscriptionId)
    }

    /**
     * Returns all EVENT messages received by this relay.
     */
    fun getPublishedEvents(): List<NDKEvent> {
        return _receivedMessages
            .filterIsInstance<ClientMessage.Event>()
            .map { it.event }
    }

    /**
     * Returns all REQ messages received by this relay.
     */
    fun getSubscriptionRequests(): List<ClientMessage.Req> {
        return _receivedMessages.filterIsInstance<ClientMessage.Req>()
    }

    /**
     * Returns the last client message of a specific type.
     */
    fun <T : ClientMessage> getLastMessageOfType(clazz: Class<T>): T? {
        return _receivedMessages.filterIsInstance(clazz).lastOrNull()
    }
}

/**
 * Builder for creating mock relay scenarios.
 */
class RelayMockScenario(private val relay: RelayMock) {

    /**
     * Simulates a successful subscription with events.
     */
    suspend fun subscriptionWithEvents(
        subscriptionId: String,
        events: List<NDKEvent>,
        sendEose: Boolean = true
    ) {
        events.forEach { event ->
            relay.sendEvent(subscriptionId, event)
        }
        if (sendEose) {
            relay.sendEose(subscriptionId)
        }
    }

    /**
     * Simulates a rate-limited scenario.
     */
    suspend fun rateLimited(eventId: String) {
        relay.rejectEvent(eventId, "rate-limited: slow down")
    }

    /**
     * Simulates an auth required scenario.
     */
    suspend fun authRequired(challenge: String) {
        relay.sendAuthChallenge(challenge)
    }

    /**
     * Simulates subscription closed by relay.
     */
    suspend fun subscriptionClosed(subscriptionId: String, reason: String = "subscription closed") {
        relay.sendClosed(subscriptionId, reason)
    }
}

/**
 * Extension to create a scenario builder.
 */
fun RelayMock.scenario(): RelayMockScenario = RelayMockScenario(this)
