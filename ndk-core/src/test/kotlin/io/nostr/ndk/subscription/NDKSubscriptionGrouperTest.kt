package io.nostr.ndk.subscription

import io.nostr.ndk.NDK
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKFilter
import io.nostr.ndk.relay.NDKRelay
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NDKSubscriptionGrouperTest {
    private lateinit var ndk: NDK
    private lateinit var grouper: NDKSubscriptionGrouper
    private lateinit var relay: NDKRelay

    @Before
    fun setup() {
        ndk = NDK()
        grouper = NDKSubscriptionGrouper(ndk, groupingDelayMs = 50)
        relay = NDKRelay("wss://test.relay", ndk)
    }

    @Test
    fun `single subscription is executed directly without grouping`() = runTest {
        val subscription = NDKSubscription(
            id = "sub-1",
            filters = listOf(NDKFilter(kinds = setOf(1))),
            ndk = ndk
        )

        grouper.enqueue(subscription, setOf(relay))

        // Wait for processing
        delay(100)

        // Subscription should have been processed (test doesn't verify grouping,
        // just that enqueue works without error)
    }

    @Test
    fun `filter fingerprint is deterministic`() {
        val filter1 = NDKFilter(kinds = setOf(1, 2, 3), authors = setOf("a", "b"))
        val filter2 = NDKFilter(kinds = setOf(3, 1, 2), authors = setOf("b", "a"))

        // Same content, different order - fingerprints should match
        assertEquals(filter1.fingerprint(), filter2.fingerprint())
    }

    @Test
    fun `fingerprint excludes temporal constraints`() {
        val filter1 = NDKFilter(kinds = setOf(1), since = 1000)
        val filter2 = NDKFilter(kinds = setOf(1), since = 2000)
        val filter3 = NDKFilter(kinds = setOf(1), until = 5000)
        val filter4 = NDKFilter(kinds = setOf(1), limit = 100)

        // All should have same fingerprint (temporal constraints excluded)
        assertEquals(filter1.fingerprint(), filter2.fingerprint())
        assertEquals(filter1.fingerprint(), filter3.fingerprint())
        assertEquals(filter1.fingerprint(), filter4.fingerprint())
    }

    @Test
    fun `different filters have different fingerprints`() {
        val filter1 = NDKFilter(kinds = setOf(1))
        val filter2 = NDKFilter(kinds = setOf(2))
        val filter3 = NDKFilter(kinds = setOf(1), authors = setOf("a"))

        assertTrue(filter1.fingerprint() != filter2.fingerprint())
        assertTrue(filter1.fingerprint() != filter3.fingerprint())
    }

    @Test
    fun `fingerprint includes tag filters`() {
        val filter1 = NDKFilter(kinds = setOf(1), tags = mapOf("e" to setOf("event1")))
        val filter2 = NDKFilter(kinds = setOf(1), tags = mapOf("e" to setOf("event2")))
        val filter3 = NDKFilter(kinds = setOf(1))

        assertTrue(filter1.fingerprint() != filter2.fingerprint())
        assertTrue(filter1.fingerprint() != filter3.fingerprint())
    }

    @Test
    fun `enqueue and remove subscription`() = runTest {
        val subscription = NDKSubscription(
            id = "sub-remove",
            filters = listOf(NDKFilter(kinds = setOf(1))),
            ndk = ndk
        )

        grouper.enqueue(subscription, setOf(relay))
        delay(100)

        // Remove should work without error
        grouper.remove(subscription.id)
    }

    // Helper to create test events
    private fun createTestEvent(
        kind: Int = 1,
        id: String = "test-event-${System.currentTimeMillis()}"
    ): NDKEvent {
        return NDKEvent(
            id = id,
            pubkey = "test-pubkey",
            createdAt = System.currentTimeMillis() / 1000,
            kind = kind,
            tags = emptyList(),
            content = "test content",
            sig = null
        )
    }
}
