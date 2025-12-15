package io.nostr.ndk.subscription

import io.nostr.ndk.NDK
import io.nostr.ndk.relay.NDKRelay
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NDKValidationRatioTrackerTest {
    private lateinit var tracker: NDKValidationRatioTracker
    private lateinit var ndk: NDK
    private lateinit var relay: NDKRelay
    private val okHttpClient = OkHttpClient.Builder().build()

    @Before
    fun setup() {
        tracker = NDKValidationRatioTracker()
        ndk = NDK()
        relay = NDKRelay("wss://test.relay", ndk, okHttpClient)
    }

    @Test
    fun `new relay has no stats`() {
        val stats = tracker.getStats(relay.url)
        assertNull(stats)
    }

    @Test
    fun `shouldValidate returns true for new relay`() {
        // New relay with no history should always validate
        val shouldValidate = tracker.shouldValidate(relay)
        assertTrue(shouldValidate)
    }

    @Test
    fun `shouldValidate always returns true until MIN_EVENTS_BEFORE_TRUST`() {
        // Record fewer events than MIN_EVENTS_BEFORE_TRUST
        repeat(50) {
            tracker.recordValidation(relay, true)
        }

        // Should still always validate (haven't hit threshold)
        var validationCount = 0
        repeat(100) {
            if (tracker.shouldValidate(relay)) validationCount++
        }

        // All should require validation
        assertEquals(100, validationCount)
    }

    @Test
    fun `recordValidation increments valid count`() {
        tracker.recordValidation(relay, true)
        tracker.recordValidation(relay, true)
        tracker.recordValidation(relay, true)

        val stats = tracker.getStats(relay.url)
        assertNotNull(stats)
        assertEquals(3, stats!!.validCount)
        assertEquals(0, stats.invalidCount)
    }

    @Test
    fun `recordValidation increments invalid count`() {
        tracker.recordValidation(relay, false)
        tracker.recordValidation(relay, false)

        val stats = tracker.getStats(relay.url)
        assertNotNull(stats)
        assertEquals(0, stats!!.validCount)
        assertEquals(2, stats.invalidCount)
    }

    @Test
    fun `getTrustRatio returns correct value`() {
        // 80% valid
        repeat(80) { tracker.recordValidation(relay, true) }
        repeat(20) { tracker.recordValidation(relay, false) }

        val trustRatio = tracker.getTrustRatio(relay)
        assertNotNull(trustRatio)
        assertEquals(0.8f, trustRatio!!, 0.001f)
    }

    @Test
    fun `getTrustRatio returns null for unknown relay`() {
        val unknownRelay = NDKRelay("wss://unknown.relay", ndk, okHttpClient)
        val trustRatio = tracker.getTrustRatio(unknownRelay)
        assertNull(trustRatio)
    }

    @Test
    fun `highly trusted relay has reduced validation probability`() {
        // Build high trust (100% valid, over threshold)
        repeat(150) { tracker.recordValidation(relay, true) }

        // Count how many times validation is required
        var validationCount = 0
        repeat(1000) {
            if (tracker.shouldValidate(relay)) validationCount++
        }

        // With 100% trust, probability should be ~10% (1 - 0.9 * 1.0)
        // Due to randomness, we expect somewhere around 100 validations out of 1000
        // Allow for statistical variance
        assertTrue("Expected ~100 validations, got $validationCount", validationCount in 50..200)
    }

    @Test
    fun `untrusted relay has higher validation probability`() {
        // Build low trust (50% valid, over threshold)
        repeat(75) { tracker.recordValidation(relay, true) }
        repeat(75) { tracker.recordValidation(relay, false) }

        // Count how many times validation is required
        var validationCount = 0
        repeat(1000) {
            if (tracker.shouldValidate(relay)) validationCount++
        }

        // With 50% trust, probability should be ~55% (1 - 0.9 * 0.5)
        // Expect somewhere around 550 validations out of 1000
        assertTrue("Expected ~550 validations, got $validationCount", validationCount in 400..700)
    }

    @Test
    fun `reset clears all stats`() {
        tracker.recordValidation(relay, true)
        tracker.reset()

        val stats = tracker.getStats(relay.url)
        assertNull(stats)
    }

    @Test
    fun `resetRelay clears stats for specific relay`() {
        val relay2 = NDKRelay("wss://test2.relay", ndk, okHttpClient)

        tracker.recordValidation(relay, true)
        tracker.recordValidation(relay2, true)

        tracker.resetRelay(relay.url)

        assertNull(tracker.getStats(relay.url))
        assertNotNull(tracker.getStats(relay2.url))
    }

    @Test
    fun `ValidationStatsSnapshot calculates trust ratio correctly`() {
        val snapshot = ValidationStatsSnapshot(validCount = 90, invalidCount = 10)

        assertEquals(100, snapshot.totalCount)
        assertEquals(0.9f, snapshot.trustRatio, 0.001f)
    }

    @Test
    fun `ValidationStatsSnapshot validation probability for high trust`() {
        // Over threshold with high trust
        val snapshot = ValidationStatsSnapshot(validCount = 100, invalidCount = 0)

        // With 100% trust, probability = 1.0 - (1.0 * 0.9) = 0.1
        assertEquals(0.1f, snapshot.validationProbability, 0.001f)
    }

    @Test
    fun `ValidationStatsSnapshot validation probability below threshold`() {
        // Below threshold
        val snapshot = ValidationStatsSnapshot(validCount = 50, invalidCount = 0)

        // Below threshold should always be 1.0
        assertEquals(1.0f, snapshot.validationProbability, 0.001f)
    }

    @Test
    fun `thread safety - concurrent validations`() {
        // Run multiple threads recording validations
        val threads = (1..10).map {
            Thread {
                repeat(100) {
                    tracker.recordValidation(relay, true)
                }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        val stats = tracker.getStats(relay.url)
        assertEquals(1000, stats!!.validCount)
    }
}
