package io.nostr.ndk.test

import io.nostr.ndk.models.NDKFilter
import io.nostr.ndk.relay.messages.ClientMessage
import io.nostr.ndk.relay.messages.RelayMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RelayMockTest {

    @Test
    fun `initial state is empty`() {
        val relay = RelayMock("wss://test.relay")

        assertTrue(relay.receivedMessages.isEmpty())
        assertTrue(relay.activeSubscriptions.isEmpty())
        assertEquals("wss://test.relay", relay.url)
    }

    @Test
    fun `receiveClientMessage stores messages`() = runBlocking {
        val relay = RelayMock()
        val generator = EventGenerator()
        val event = generator.textNote("Test")

        relay.receiveClientMessage(ClientMessage.Event(event))

        assertEquals(1, relay.receivedMessages.size)
        assertTrue(relay.receivedMessages[0] is ClientMessage.Event)
    }

    @Test
    fun `REQ message creates subscription`() = runBlocking {
        val relay = RelayMock()
        val filter = NDKFilter(kinds = setOf(1))

        relay.receiveClientMessage(ClientMessage.Req("sub-1", listOf(filter)))

        assertTrue(relay.isSubscriptionActive("sub-1"))
        assertEquals(filter, relay.getSubscriptionFilter("sub-1"))
        assertEquals(setOf("sub-1"), relay.activeSubscriptions)
    }

    @Test
    fun `CLOSE message removes subscription`() = runBlocking {
        val relay = RelayMock()
        val filter = NDKFilter(kinds = setOf(1))

        relay.receiveClientMessage(ClientMessage.Req("sub-1", listOf(filter)))
        assertTrue(relay.isSubscriptionActive("sub-1"))

        relay.receiveClientMessage(ClientMessage.Close("sub-1"))
        assertFalse(relay.isSubscriptionActive("sub-1"))
    }

    @Test
    fun `acceptEvent sends OK with success`() = runBlocking {
        val relay = RelayMock()

        relay.acceptEvent("event-123", "saved")

        val message = relay.sentMessages.first()
        assertTrue(message is RelayMessage.Ok)
        val ok = message as RelayMessage.Ok
        assertEquals("event-123", ok.eventId)
        assertTrue(ok.success)
        assertEquals("saved", ok.message)
    }

    @Test
    fun `rejectEvent sends OK with failure`() = runBlocking {
        val relay = RelayMock()

        relay.rejectEvent("event-123", "rate-limited")

        val message = relay.sentMessages.first()
        assertTrue(message is RelayMessage.Ok)
        val ok = message as RelayMessage.Ok
        assertEquals("event-123", ok.eventId)
        assertFalse(ok.success)
        assertEquals("rate-limited", ok.message)
    }

    @Test
    fun `sendEvent emits EVENT message`() = runBlocking {
        val relay = RelayMock()
        val generator = EventGenerator()
        val event = generator.textNote("Test content")

        relay.sendEvent("sub-1", event)

        val message = relay.sentMessages.first()
        assertTrue(message is RelayMessage.Event)
        val eventMsg = message as RelayMessage.Event
        assertEquals("sub-1", eventMsg.subscriptionId)
        assertEquals(event.id, eventMsg.event.id)
    }

    @Test
    fun `sendEose emits EOSE message`() = runBlocking {
        val relay = RelayMock()

        relay.sendEose("sub-1")

        val message = relay.sentMessages.first()
        assertTrue(message is RelayMessage.Eose)
        assertEquals("sub-1", (message as RelayMessage.Eose).subscriptionId)
    }

    @Test
    fun `sendNotice emits NOTICE message`() = runBlocking {
        val relay = RelayMock()

        relay.sendNotice("relay message")

        val message = relay.sentMessages.first()
        assertTrue(message is RelayMessage.Notice)
        assertEquals("relay message", (message as RelayMessage.Notice).message)
    }

    @Test
    fun `sendClosed emits CLOSED message`() = runBlocking {
        val relay = RelayMock()

        relay.sendClosed("sub-1", "subscription closed")

        val message = relay.sentMessages.first()
        assertTrue(message is RelayMessage.Closed)
        val closed = message as RelayMessage.Closed
        assertEquals("sub-1", closed.subscriptionId)
        assertEquals("subscription closed", closed.message)
    }

    @Test
    fun `sendAuthChallenge emits AUTH message`() = runBlocking {
        val relay = RelayMock()

        relay.sendAuthChallenge("challenge-123")

        val message = relay.sentMessages.first()
        assertTrue(message is RelayMessage.Auth)
        assertEquals("challenge-123", (message as RelayMessage.Auth).challenge)
    }

    @Test
    fun `reset clears messages and subscriptions`() = runBlocking {
        val relay = RelayMock()
        val generator = EventGenerator()
        val filter = NDKFilter(kinds = setOf(1))

        relay.receiveClientMessage(ClientMessage.Event(generator.textNote("Test")))
        relay.receiveClientMessage(ClientMessage.Req("sub-1", listOf(filter)))

        assertEquals(2, relay.receivedMessages.size)
        assertEquals(1, relay.activeSubscriptions.size)

        relay.reset()

        assertTrue(relay.receivedMessages.isEmpty())
        assertTrue(relay.activeSubscriptions.isEmpty())
    }

    @Test
    fun `getPublishedEvents returns only EVENT messages`() = runBlocking {
        val relay = RelayMock()
        val generator = EventGenerator()
        val event1 = generator.textNote("Test 1")
        val event2 = generator.textNote("Test 2")
        val filter = NDKFilter(kinds = setOf(1))

        relay.receiveClientMessage(ClientMessage.Event(event1))
        relay.receiveClientMessage(ClientMessage.Req("sub-1", listOf(filter)))
        relay.receiveClientMessage(ClientMessage.Event(event2))

        val published = relay.getPublishedEvents()
        assertEquals(2, published.size)
        assertEquals(event1.id, published[0].id)
        assertEquals(event2.id, published[1].id)
    }

    @Test
    fun `getSubscriptionRequests returns only REQ messages`() = runBlocking {
        val relay = RelayMock()
        val generator = EventGenerator()
        val filter1 = NDKFilter(kinds = setOf(1))
        val filter2 = NDKFilter(kinds = setOf(0))

        relay.receiveClientMessage(ClientMessage.Event(generator.textNote("Test")))
        relay.receiveClientMessage(ClientMessage.Req("sub-1", listOf(filter1)))
        relay.receiveClientMessage(ClientMessage.Req("sub-2", listOf(filter2)))

        val requests = relay.getSubscriptionRequests()
        assertEquals(2, requests.size)
        assertEquals("sub-1", requests[0].subscriptionId)
        assertEquals("sub-2", requests[1].subscriptionId)
    }

    @Test
    fun `scenario subscriptionWithEvents sends events and EOSE`() = runBlocking {
        val relay = RelayMock()
        val generator = EventGenerator()
        val events = listOf(
            generator.textNote("Event 1"),
            generator.textNote("Event 2"),
            generator.textNote("Event 3")
        )

        relay.scenario().subscriptionWithEvents("sub-1", events)

        val messages = relay.sentMessages.take(4).toList()
        assertEquals(4, messages.size)
        assertTrue(messages[0] is RelayMessage.Event)
        assertTrue(messages[1] is RelayMessage.Event)
        assertTrue(messages[2] is RelayMessage.Event)
        assertTrue(messages[3] is RelayMessage.Eose)
    }

    @Test
    fun `scenario rateLimited rejects event`() = runBlocking {
        val relay = RelayMock()

        relay.scenario().rateLimited("event-123")

        val message = relay.sentMessages.first()
        assertTrue(message is RelayMessage.Ok)
        val ok = message as RelayMessage.Ok
        assertFalse(ok.success)
        assertTrue(ok.message.contains("rate-limited"))
    }

    @Test
    fun `scenario authRequired sends challenge`() = runBlocking {
        val relay = RelayMock()

        relay.scenario().authRequired("challenge-xyz")

        val message = relay.sentMessages.first()
        assertTrue(message is RelayMessage.Auth)
        assertEquals("challenge-xyz", (message as RelayMessage.Auth).challenge)
    }

    @Test
    fun `scenario subscriptionClosed sends CLOSED`() = runBlocking {
        val relay = RelayMock()

        relay.scenario().subscriptionClosed("sub-1", "too many subscriptions")

        val message = relay.sentMessages.first()
        assertTrue(message is RelayMessage.Closed)
        val closed = message as RelayMessage.Closed
        assertEquals("sub-1", closed.subscriptionId)
        assertEquals("too many subscriptions", closed.message)
    }
}
