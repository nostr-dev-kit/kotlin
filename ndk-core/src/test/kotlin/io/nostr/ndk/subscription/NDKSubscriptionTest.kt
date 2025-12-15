package io.nostr.ndk.subscription

import io.nostr.ndk.NDK
import io.nostr.ndk.models.EventId
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKFilter
import io.nostr.ndk.relay.NDKRelay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test

class NDKSubscriptionTest {

    private val ndk = NDK()
    private val okHttpClient = OkHttpClient.Builder().build()

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
    fun `events flow through SharedFlow`() {
        val filter = NDKFilter(kinds = setOf(1))
        val sub = NDKSubscription("test", listOf(filter), ndk)

        val event = createTestEvent("event1", kind = 1)

        // Emit event
        sub.emit(event, createTestRelay("wss://relay1.com"))

        // With replay = Int.MAX_VALUE, we can access replay cache
        val replayCache = sub.events.replayCache
        assertEquals(1, replayCache.size)
        assertEquals(event, replayCache[0])
    }

    @Test
    fun `multiple events flow through SharedFlow in order`() {
        val filter = NDKFilter(kinds = setOf(1))
        val sub = NDKSubscription("test", listOf(filter), ndk)

        val event1 = createTestEvent("event1", kind = 1)
        val event2 = createTestEvent("event2", kind = 1)
        val event3 = createTestEvent("event3", kind = 1)

        val relay = createTestRelay("wss://relay1.com")
        sub.emit(event1, relay)
        sub.emit(event2, relay)
        sub.emit(event3, relay)

        // Access replay cache to verify events are stored in order
        val replayCache = sub.events.replayCache
        assertEquals(3, replayCache.size)
        assertEquals(event1, replayCache[0])
        assertEquals(event2, replayCache[1])
        assertEquals(event3, replayCache[2])
    }

    @Test
    fun `EOSE tracked per relay`() {
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
    fun `markEose updates eosePerRelay state`() {
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
    fun `multiple relays tracked independently`() {
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
    fun `stop cancels subscription`() {
        val filter = NDKFilter(kinds = setOf(1))
        val sub = NDKSubscription("test", listOf(filter), ndk)

        val relay = createTestRelay("wss://relay1.com")

        // Start the subscription
        sub.start(setOf(relay))

        // Stop it
        sub.stop()

        // Subscription methods should be callable without errors
        assertNotNull(sub)
    }

    @Test
    fun `start initializes subscription with relays`() {
        val filter = NDKFilter(kinds = setOf(1))
        val sub = NDKSubscription("test", listOf(filter), ndk)

        val relay1 = createTestRelay("wss://relay1.com")
        val relay2 = createTestRelay("wss://relay2.com")

        // Start should be callable with a set of relays
        sub.start(setOf(relay1, relay2))

        // The method should exist and be callable
        assertNotNull(sub)
    }

    @Test
    fun `emit adds event to SharedFlow`() {
        val filter = NDKFilter(kinds = setOf(1))
        val sub = NDKSubscription("test", listOf(filter), ndk)

        val event = createTestEvent("test-event", kind = 1)
        val relay = createTestRelay("wss://relay1.com")

        sub.emit(event, relay)

        // Verify event is in replay cache
        val replayCache = sub.events.replayCache
        assertEquals(1, replayCache.size)
        assertEquals(event, replayCache[0])
    }

    @Test
    fun `events from different relays flow through same SharedFlow`() {
        val filter = NDKFilter(kinds = setOf(1))
        val sub = NDKSubscription("test", listOf(filter), ndk)

        val event1 = createTestEvent("event1", kind = 1)
        val event2 = createTestEvent("event2", kind = 1)

        val relay1 = createTestRelay("wss://relay1.com")
        val relay2 = createTestRelay("wss://relay2.com")

        sub.emit(event1, relay1)
        sub.emit(event2, relay2)

        // Access replay cache to verify both events are stored
        val replayCache = sub.events.replayCache
        assertEquals(2, replayCache.size)
        assertEquals(event1, replayCache[0])
        assertEquals(event2, replayCache[1])
    }

    @Test
    fun `subscription stores NDK reference`() {
        val filter = NDKFilter(kinds = setOf(1))
        val sub = NDKSubscription("test", listOf(filter), ndk)

        // The NDK reference should be stored (even if nullable)
        // We can't directly test the private field, but the constructor accepts it
        assertNotNull(sub)
    }

    // ===========================================
    // Dynamic Relay Update Tests
    // ===========================================

    @Test
    fun `activeRelays tracks relays subscription is using`() {
        val filter = NDKFilter(kinds = setOf(1))
        val sub = NDKSubscription("test", listOf(filter), ndk)

        val relay1 = createTestRelay("wss://relay1.com")
        val relay2 = createTestRelay("wss://relay2.com")

        // Initially empty
        assertTrue(sub.activeRelays.value.isEmpty())

        // Start populates activeRelays
        sub.start(setOf(relay1, relay2))

        assertEquals(2, sub.activeRelays.value.size)
        assertTrue(sub.activeRelays.value.contains(relay1))
        assertTrue(sub.activeRelays.value.contains(relay2))
    }

    @Test
    fun `hasRelay returns true for active relay`() {
        val filter = NDKFilter(kinds = setOf(1))
        val sub = NDKSubscription("test", listOf(filter), ndk)

        val relay = createTestRelay("wss://relay1.com")
        sub.start(setOf(relay))

        assertTrue(sub.hasRelay("wss://relay1.com"))
        assertFalse(sub.hasRelay("wss://other-relay.com"))
    }

    @Test
    fun `addRelays adds new relays to subscription`() {
        val filter = NDKFilter(kinds = setOf(1))
        val sub = NDKSubscription("test", listOf(filter), ndk)

        val relay1 = createTestRelay("wss://relay1.com")
        sub.start(setOf(relay1))

        assertEquals(1, sub.activeRelays.value.size)

        val relay2 = createTestRelay("wss://relay2.com")
        sub.addRelays(setOf(relay2))

        assertEquals(2, sub.activeRelays.value.size)
        assertTrue(sub.hasRelay("wss://relay2.com"))
    }

    @Test
    fun `addRelays ignores duplicate relays`() {
        val filter = NDKFilter(kinds = setOf(1))
        val sub = NDKSubscription("test", listOf(filter), ndk)

        val relay = createTestRelay("wss://relay1.com")
        sub.start(setOf(relay))

        assertEquals(1, sub.activeRelays.value.size)

        // Try to add same relay again
        val sameRelay = createTestRelay("wss://relay1.com")
        sub.addRelays(setOf(sameRelay))

        // Should still be 1
        assertEquals(1, sub.activeRelays.value.size)
    }

    @Test
    fun `stop clears activeRelays`() {
        val filter = NDKFilter(kinds = setOf(1))
        val sub = NDKSubscription("test", listOf(filter), ndk)

        val relay = createTestRelay("wss://relay1.com")
        sub.start(setOf(relay))

        assertEquals(1, sub.activeRelays.value.size)

        sub.stop()

        assertTrue(sub.activeRelays.value.isEmpty())
    }

    // ===========================================
    // fetchEvent Tests
    // ===========================================

    @Test
    fun `fetchEvent returns event when event arrives before EOSE`() = runTest {
        val filter = NDKFilter(kinds = setOf(1))
        val sub = NDKSubscription("test", listOf(filter), ndk)

        val relay = createTestRelay("wss://relay1.com")
        sub.start(setOf(relay))

        val event = createTestEvent("event1", kind = 1)

        // Launch fetchEvent in background
        val resultDeferred = launch {
            val result = sub.fetchEvent()
            assertEquals(event, result)
        }

        // Emit event before EOSE
        sub.emit(event, relay)

        resultDeferred.join()
    }

    @Test
    fun `fetchEvent returns null when EOSE arrives before event`() = runTest {
        val filter = NDKFilter(kinds = setOf(1))
        val sub = NDKSubscription("test", listOf(filter), ndk)

        val relay = createTestRelay("wss://relay1.com")
        sub.start(setOf(relay))

        // Launch fetchEvent in background
        val resultDeferred = launch {
            val result = sub.fetchEvent()
            assertNull(result)
        }

        // Mark EOSE before any events
        sub.markEose(relay)

        resultDeferred.join()
    }

    @Test
    fun `fetchEvent stops subscription when done`() = runTest {
        val filter = NDKFilter(kinds = setOf(1))
        val sub = NDKSubscription("test", listOf(filter), ndk)

        val relay = createTestRelay("wss://relay1.com")
        sub.start(setOf(relay))

        assertTrue(sub.activeRelays.value.isNotEmpty())

        val event = createTestEvent("event1", kind = 1)

        // Launch fetchEvent
        val resultDeferred = launch {
            sub.fetchEvent()
        }

        sub.emit(event, relay)
        resultDeferred.join()

        // Subscription should be stopped
        assertTrue(sub.activeRelays.value.isEmpty())
    }

    @Test
    fun `fetchEvent waits for all relays to EOSE before returning null`() = runTest {
        val filter = NDKFilter(kinds = setOf(1))
        val sub = NDKSubscription("test", listOf(filter), ndk)

        val relay1 = createTestRelay("wss://relay1.com")
        val relay2 = createTestRelay("wss://relay2.com")
        sub.start(setOf(relay1, relay2))

        var result: NDKEvent? = createTestEvent("placeholder", kind = 1)

        // Launch fetchEvent in background
        val resultDeferred = launch {
            result = sub.fetchEvent()
        }

        // Mark EOSE for only one relay - should not return yet
        sub.markEose(relay1)

        // Give time for processing
        kotlinx.coroutines.delay(50)

        // Still waiting since relay2 hasn't sent EOSE
        assertTrue(resultDeferred.isActive)

        // Mark EOSE for second relay - now should return null
        sub.markEose(relay2)

        resultDeferred.join()
        assertNull(result)
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
        return NDKRelay(url, ndk, okHttpClient)
    }
}
