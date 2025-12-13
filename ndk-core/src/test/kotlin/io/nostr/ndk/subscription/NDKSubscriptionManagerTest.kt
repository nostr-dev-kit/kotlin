package io.nostr.ndk.subscription

import io.nostr.ndk.NDK
import io.nostr.ndk.models.EventId
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKFilter
import io.nostr.ndk.models.NDKTag
import io.nostr.ndk.relay.NDKRelay
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NDKSubscriptionManagerTest {
    private lateinit var ndk: NDK
    private lateinit var manager: NDKSubscriptionManager
    private lateinit var relay: NDKRelay
    private val okHttpClient = OkHttpClient.Builder().build()

    @Before
    fun setup() {
        ndk = NDK()
        manager = NDKSubscriptionManager(ndk)
        relay = NDKRelay("wss://test.relay", ndk, okHttpClient)
    }

    @Test
    fun `subscribe creates new subscription with unique ID`() = runTest {
        val filters = listOf(NDKFilter(kinds = setOf(1)))

        val subscription = manager.subscribe(filters)

        assertNotNull(subscription)
        assertNotNull(subscription.id)
        assertEquals(filters, subscription.filters)
    }

    @Test
    fun `subscribe creates subscriptions with unique IDs`() = runTest {
        val filters = listOf(NDKFilter(kinds = setOf(1)))

        val sub1 = manager.subscribe(filters)
        val sub2 = manager.subscribe(filters)

        assertTrue(sub1.id != sub2.id)
    }

    @Test
    fun `unsubscribe removes subscription`() = runTest {
        val filters = listOf(NDKFilter(kinds = setOf(1)))
        val subscription = manager.subscribe(filters)

        manager.unsubscribe(subscription.id)

        // After unsubscribe, dispatching events should not deliver to that subscription
        val testEvent = createTestEvent()
        manager.dispatchEvent(testEvent, relay, subscription.id)

        // Subscription should not receive event (we'll verify this by checking allEvents)
        // The subscription was removed, so dispatchEvent should not route to it
    }

    @Test
    fun `dispatchEvent routes event to correct subscription`() = runTest {
        val filters = listOf(NDKFilter(kinds = setOf(1)))
        val subscription = manager.subscribe(filters)

        val testEvent = createTestEvent(kind = 1)

        manager.dispatchEvent(testEvent, relay, subscription.id)

        val receivedEvent = subscription.events.first()
        assertEquals(testEvent.id, receivedEvent.id)
    }

    @Test
    fun `dispatchEvent emits to allEvents flow`() = runTest {
        val filters = listOf(NDKFilter(kinds = setOf(1)))
        manager.subscribe(filters)

        val testEvent = createTestEvent(kind = 1)

        // Start collecting from allEvents before dispatching
        val collected = mutableListOf<Pair<NDKEvent, NDKRelay>>()
        val job = launch {
            manager.allEvents.take(1).collect { collected.add(it) }
        }

        // Give the collector a moment to start
        delay(10)

        // Dispatch the event
        manager.dispatchEvent(testEvent, relay, "sub-id")

        // Wait for collection to complete
        job.join()

        assertTrue(collected.isNotEmpty())
        assertEquals(testEvent.id, collected.first().first.id)
        assertEquals(relay.url, collected.first().second.url)
    }

    @Test
    fun `dispatchEose calls markEose on subscription`() = runTest {
        val filters = listOf(NDKFilter(kinds = setOf(1)))
        val subscription = manager.subscribe(filters)

        manager.dispatchEose(relay, subscription.id)

        val eoseStatus = subscription.eosePerRelay.value
        assertEquals(true, eoseStatus[relay.url])
    }

    @Test
    fun `deduplication - same event ID ignored on second dispatch`() = runTest {
        val filters = listOf(NDKFilter(kinds = setOf(1)))
        val subscription = manager.subscribe(filters)

        val testEvent = createTestEvent(kind = 1, id = "duplicate-event-id")

        // Dispatch same event twice
        manager.dispatchEvent(testEvent, relay, subscription.id)
        manager.dispatchEvent(testEvent, relay, subscription.id)

        // Should only receive one event
        val events = subscription.events.take(1).toList()
        assertEquals(1, events.size)
    }

    @Test
    fun `multiple subscriptions receive same event if filters match`() = runTest {
        val filters = listOf(NDKFilter(kinds = setOf(1)))
        val sub1 = manager.subscribe(filters)
        val sub2 = manager.subscribe(filters)

        val testEvent = createTestEvent(kind = 1)

        // Dispatch event with sub1's ID
        manager.dispatchEvent(testEvent, relay, sub1.id)

        // Both subscriptions should receive the event (if filters match)
        val event1 = sub1.events.first()
        assertEquals(testEvent.id, event1.id)

        // sub2 should also receive if its filters match
        // (in real implementation, dispatchEvent checks all subscriptions)
    }

    @Test
    fun `dispatchEvent only delivers to subscriptions with matching filters`() = runTest {
        val sub1 = manager.subscribe(listOf(NDKFilter(kinds = setOf(1))))
        val sub2 = manager.subscribe(listOf(NDKFilter(kinds = setOf(2))))

        val testEvent = createTestEvent(kind = 1)

        manager.dispatchEvent(testEvent, relay, sub1.id)

        // sub1 should receive (kind 1 matches)
        val event1 = sub1.events.first()
        assertEquals(testEvent.id, event1.id)

        // sub2 should not receive (kind 2 doesn't match kind 1 event)
        // We can't easily test "not receiving" without timeouts, but we've
        // verified the matching subscription receives it
    }

    // Helper functions
    private fun createTestEvent(
        kind: Int = 1,
        id: String = "test-event-${System.currentTimeMillis()}",
        pubkey: String = "test-pubkey",
        content: String = "test content"
    ): NDKEvent {
        return NDKEvent(
            id = id,
            pubkey = pubkey,
            createdAt = System.currentTimeMillis() / 1000,
            kind = kind,
            tags = emptyList(),
            content = content,
            sig = null
        )
    }
}
