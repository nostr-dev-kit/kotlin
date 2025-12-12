package io.nostr.ndk

import io.nostr.ndk.crypto.NDKKeyPair
import io.nostr.ndk.crypto.NDKPrivateKeySigner
import io.nostr.ndk.models.NDKFilter
import io.nostr.ndk.relay.NDKRelay
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*

/**
 * Tests for the main NDK class.
 *
 * Following TDD: These tests are written first and should fail until implementation is complete.
 */
class NDKTest {

    @Test
    fun `NDK initializes with empty relay URLs by default`() {
        val ndk = NDK()

        assertEquals(emptySet<String>(), ndk.explicitRelayUrls)
    }

    @Test
    fun `NDK initializes with provided relay URLs`() {
        val relayUrls = setOf("wss://relay.damus.io", "wss://nos.lol")
        val ndk = NDK(explicitRelayUrls = relayUrls)

        assertEquals(relayUrls, ndk.explicitRelayUrls)
    }

    @Test
    fun `NDK initializes without signer by default`() {
        val ndk = NDK()

        assertNull(ndk.signer)
    }

    @Test
    fun `NDK initializes with provided signer`() {
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)
        val ndk = NDK(signer = signer)

        assertNotNull(ndk.signer)
        assertEquals(signer, ndk.signer)
    }

    @Test
    fun `pool is lazy initialized`() {
        val ndk = NDK()

        // Access pool - should be created on first access
        val pool = ndk.pool

        assertNotNull(pool)
        // Second access should return same instance
        assertEquals(pool, ndk.pool)
    }

    @Test
    fun `subscriptionManager is lazy initialized`() {
        val ndk = NDK()

        // Create subscriptions to trigger subscriptionManager initialization
        val sub1 = ndk.subscribe(NDKFilter(kinds = setOf(1)))
        val sub2 = ndk.subscribe(NDKFilter(kinds = setOf(3)))

        // Both subscriptions should work, meaning subscriptionManager was initialized
        assertNotNull(sub1)
        assertNotNull(sub2)

        // They should have different IDs since they're different subscriptions
        assertTrue(sub1.id != sub2.id)
    }

    @Test
    fun `subscribe creates subscription with single filter`() = runTest {
        val ndk = NDK()

        val filter = NDKFilter(kinds = setOf(1))
        val subscription = ndk.subscribe(filter)

        assertNotNull(subscription)
        assertEquals(1, subscription.filters.size)
        assertEquals(filter, subscription.filters.first())
    }

    @Test
    fun `subscribe creates subscription with multiple filters`() = runTest {
        val ndk = NDK()

        val filters = listOf(
            NDKFilter(kinds = setOf(1)),
            NDKFilter(kinds = setOf(3))
        )
        val subscription = ndk.subscribe(filters)

        assertNotNull(subscription)
        assertEquals(2, subscription.filters.size)
        assertEquals(filters, subscription.filters)
    }

    @Test
    fun `connect adds explicit relays to pool and connects`() = runTest {
        val relayUrls = setOf("wss://relay.damus.io", "wss://nos.lol")
        val ndk = NDK(explicitRelayUrls = relayUrls)

        // Before connect, pool should have no relays
        assertEquals(emptySet<NDKRelay>(), ndk.pool.availableRelays.value)

        // Connect should add relays to pool
        ndk.connect(timeoutMs = 1000)

        // After connect, pool should have the relays
        val availableRelays = ndk.pool.availableRelays.value
        assertEquals(2, availableRelays.size)

        val relayUrlsInPool = availableRelays.map { it.url }.toSet()
        assertTrue(relayUrlsInPool.contains("wss://relay.damus.io"))
        assertTrue(relayUrlsInPool.contains("wss://nos.lol"))
    }

    @Test
    fun `connect with empty relay URLs does not fail`() = runTest {
        val ndk = NDK()

        // Should not throw
        ndk.connect(timeoutMs = 1000)

        assertEquals(emptySet<NDKRelay>(), ndk.pool.availableRelays.value)
    }

    @Test
    fun `signer property accessible`() {
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)
        val ndk = NDK(signer = signer)

        assertEquals(signer, ndk.signer)
        assertEquals(keyPair.pubkeyHex, ndk.signer?.pubkey)
    }

    @Test
    fun `multiple NDK instances are independent`() {
        val ndk1 = NDK(explicitRelayUrls = setOf("wss://relay1.com"))
        val ndk2 = NDK(explicitRelayUrls = setOf("wss://relay2.com"))

        // Different relay URLs
        assertEquals(setOf("wss://relay1.com"), ndk1.explicitRelayUrls)
        assertEquals(setOf("wss://relay2.com"), ndk2.explicitRelayUrls)

        // Different pool instances
        assertTrue(ndk1.pool !== ndk2.pool)
    }

    // ===========================================
    // Outbox Model Tests
    // ===========================================

    @Test
    fun `enableOutboxModel defaults to true`() {
        val ndk = NDK()

        assertTrue(ndk.enableOutboxModel)
    }

    @Test
    fun `enableOutboxModel can be disabled`() {
        val ndk = NDK()
        ndk.enableOutboxModel = false

        assertFalse(ndk.enableOutboxModel)
    }

    @Test
    fun `autoConnectUserRelays defaults to true`() {
        val ndk = NDK()

        assertTrue(ndk.autoConnectUserRelays)
    }

    @Test
    fun `relayGoalPerAuthor defaults to 2`() {
        val ndk = NDK()

        assertEquals(2, ndk.relayGoalPerAuthor)
    }

    @Test
    fun `outboxRelayUrls has default relays`() {
        val ndk = NDK()

        assertTrue(ndk.outboxRelayUrls.isNotEmpty())
        assertTrue(ndk.outboxRelayUrls.contains("wss://purplepag.es"))
    }

    @Test
    fun `outboxPool is lazy initialized`() {
        val ndk = NDK()

        val outboxPool = ndk.outboxPool
        assertNotNull(outboxPool)

        // Second access returns same instance
        assertEquals(outboxPool, ndk.outboxPool)
    }

    @Test
    fun `outboxPool is separate from main pool`() {
        val ndk = NDK()

        assertTrue(ndk.pool !== ndk.outboxPool)
    }

    @Test
    fun `subscribe with authors uses relay calculator when outbox enabled`() = runTest {
        val ndk = NDK()
        ndk.enableOutboxModel = true

        // Add a relay to pool
        ndk.pool.addRelay("wss://relay1.com", connect = false)

        // Subscribe with authors filter
        val filter = NDKFilter(authors = setOf("author1"), kinds = setOf(1))
        val subscription = ndk.subscribe(filter)

        // Subscription should be created
        assertNotNull(subscription)
    }

    @Test
    fun `subscribe without authors uses all connected relays`() = runTest {
        val ndk = NDK()

        ndk.pool.addRelay("wss://relay1.com", connect = false)
        ndk.pool.addRelay("wss://relay2.com", connect = false)

        // Subscribe without authors
        val filter = NDKFilter(kinds = setOf(1), limit = 10)
        val subscription = ndk.subscribe(filter)

        // Should use all available relays
        assertEquals(2, subscription.activeRelays.value.size)
    }
}
