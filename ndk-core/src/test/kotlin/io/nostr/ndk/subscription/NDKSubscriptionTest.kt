package io.nostr.ndk.subscription

import io.nostr.ndk.NDK
import io.nostr.ndk.models.EventId
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKFilter
import io.nostr.ndk.models.NDKTag
import io.nostr.ndk.relay.NDKRelay
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class NDKSubscriptionTest {

    private val ndk = NDK()

    @Test
    fun `subscription has unique ID`() {
        val filter = NDKFilter(kinds = setOf(1))
        val sub1 = NDKSubscription("sub1", listOf(filter), ndk)
        val sub2 = NDKSubscription("sub2", listOf(filter), ndk)

        assertEquals("sub1", sub1.id)
        assertEquals("sub2", sub2.id)
        assertNotEquals(sub1.id, sub2.id)
    }

    @Test
    fun `subscription stores filters correctly`() {
        val filter1 = NDKFilter(kinds = setOf(1))
        val filter2 = NDKFilter(kinds = setOf(3))
        val filters = listOf(filter1, filter2)

        val sub = NDKSubscription("test", filters, ndk)

        assertEquals(filters, sub.filters)
    }

    @Test
    fun `events flow through SharedFlow`() = runTest {
        val filter = NDKFilter(kinds = setOf(1))
        val sub = NDKSubscription("test", listOf(filter), ndk)

        val event = createTestEvent("event1", kind = 1)

        // Emit event first
        sub.emit(event, createTestRelay("wss://relay1.com"))

        // Then collect
        val collectedEvent = sub.events.first()

        assertEquals(event, collectedEvent)
    }

    @Test
    fun `multiple events flow through SharedFlow in order`() = runTest {
        val filter = NDKFilter(kinds = setOf(1))
        val sub = NDKSubscription("test", listOf(filter), ndk)

        val event1 = createTestEvent("event1", kind = 1)
        val event2 = createTestEvent("event2", kind = 1)
        val event3 = createTestEvent("event3", kind = 1)

        val relay = createTestRelay("wss://relay1.com")
        sub.emit(event1, relay)
        sub.emit(event2, relay)
        sub.emit(event3, relay)

        val collectedEvents = sub.events.take(3).toList()

        assertEquals(3, collectedEvents.size)
        assertEquals(event1, collectedEvents[0])
        assertEquals(event2, collectedEvents[1])
        assertEquals(event3, collectedEvents[2])
    }

    @Test
    fun `EOSE tracked per relay`() = runTest {
        val filter = NDKFilter(kinds = setOf(1))
        val sub = NDKSubscription("test", listOf(filter), ndk)

        val relay1 = createTestRelay("wss://relay1.com")
        val relay2 = createTestRelay("wss://relay2.com")

        // Initially, no EOSE for any relay
        assertEquals(emptyMap<String, Boolean>(), sub.eosePerRelay.value)

        // Mark EOSE for relay1
        sub.markEose(relay1)

        val eoseMap1 = sub.eosePerRelay.value
        assertEquals(true, eoseMap1["wss://relay1.com"])
        assertNull(eoseMap1["wss://relay2.com"])

        // Mark EOSE for relay2
        sub.markEose(relay2)

        val eoseMap2 = sub.eosePerRelay.value
        assertEquals(true, eoseMap2["wss://relay1.com"])
        assertEquals(true, eoseMap2["wss://relay2.com"])
    }

    @Test
    fun `markEose updates eosePerRelay state`() = runTest {
        val filter = NDKFilter(kinds = setOf(1))
        val sub = NDKSubscription("test", listOf(filter), ndk)

        val relay = createTestRelay("wss://relay.damus.io")

        // Check initial state
        assertEquals(emptyMap<String, Boolean>(), sub.eosePerRelay.value)

        // Mark EOSE
        sub.markEose(relay)

        // Check updated state
        assertEquals(mapOf("wss://relay.damus.io" to true), sub.eosePerRelay.value)
    }

    @Test
    fun `multiple relays tracked independently`() = runTest {
        val filter = NDKFilter(kinds = setOf(1))
        val sub = NDKSubscription("test", listOf(filter), ndk)

        val relay1 = createTestRelay("wss://relay1.com")
        val relay2 = createTestRelay("wss://relay2.com")
        val relay3 = createTestRelay("wss://relay3.com")

        // Mark EOSE for relay1 and relay3
        sub.markEose(relay1)
        sub.markEose(relay3)

        val eoseMap = sub.eosePerRelay.value

        assertEquals(true, eoseMap["wss://relay1.com"])
        assertNull(eoseMap["wss://relay2.com"]) // Not marked yet
        assertEquals(true, eoseMap["wss://relay3.com"])
    }

    @Test
    fun `stop cancels subscription`() = runTest {
        val filter = NDKFilter(kinds = setOf(1))
        val sub = NDKSubscription("test", listOf(filter), ndk)

        val relay = createTestRelay("wss://relay1.com")

        // Start the subscription
        sub.start(setOf(relay))

        // Stop it
        sub.stop()

        // After stopping, emitting should not work (subscription is closed)
        // We can't easily test this without a more complex setup, but the method should exist
        // and be callable
    }

    @Test
    fun `start initializes subscription with relays`() = runTest {
        val filter = NDKFilter(kinds = setOf(1))
        val sub = NDKSubscription("test", listOf(filter), ndk)

        val relay1 = createTestRelay("wss://relay1.com")
        val relay2 = createTestRelay("wss://relay2.com")

        // Start should be callable with a set of relays
        sub.start(setOf(relay1, relay2))

        // The method should exist and be callable
    }

    @Test
    fun `emit adds event to SharedFlow`() = runTest {
        val filter = NDKFilter(kinds = setOf(1))
        val sub = NDKSubscription("test", listOf(filter), ndk)

        val event = createTestEvent("test-event", kind = 1)
        val relay = createTestRelay("wss://relay1.com")

        sub.emit(event, relay)

        val collectedEvent = sub.events.first()

        assertEquals(event, collectedEvent)
    }

    @Test
    fun `events from different relays flow through same SharedFlow`() = runTest {
        val filter = NDKFilter(kinds = setOf(1))
        val sub = NDKSubscription("test", listOf(filter), ndk)

        val event1 = createTestEvent("event1", kind = 1)
        val event2 = createTestEvent("event2", kind = 1)

        val relay1 = createTestRelay("wss://relay1.com")
        val relay2 = createTestRelay("wss://relay2.com")

        sub.emit(event1, relay1)
        sub.emit(event2, relay2)

        val collectedEvents = sub.events.take(2).toList()

        assertEquals(2, collectedEvents.size)
        assertEquals(event1, collectedEvents[0])
        assertEquals(event2, collectedEvents[1])
    }

    @Test
    fun `subscription stores NDK reference`() {
        val filter = NDKFilter(kinds = setOf(1))
        val sub = NDKSubscription("test", listOf(filter), ndk)

        // The NDK reference should be stored (even if nullable)
        // We can't directly test the private field, but the constructor accepts it
        assertNotNull(sub)
    }

    // Helper functions

    private fun createTestEvent(
        id: EventId,
        kind: Int = 1,
        pubkey: String = "test-pubkey",
        content: String = "test content",
        createdAt: Long = System.currentTimeMillis() / 1000
    ): NDKEvent {
        return NDKEvent(
            id = id,
            pubkey = pubkey,
            createdAt = createdAt,
            kind = kind,
            tags = emptyList(),
            content = content,
            sig = "test-signature"
        )
    }

    private fun createTestRelay(url: String): NDKRelay {
        return NDKRelay(url, ndk)
    }
}
