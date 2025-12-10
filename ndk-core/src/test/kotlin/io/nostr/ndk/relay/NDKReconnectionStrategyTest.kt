package io.nostr.ndk.relay

import org.junit.Test
import org.junit.Assert.*

class NDKReconnectionStrategyTest {

    @Test
    fun `nextDelay returns initial delay for first attempt`() {
        val strategy = NDKReconnectionStrategy()
        assertEquals(1_000L, strategy.nextDelay(0))
    }

    @Test
    fun `nextDelay uses exponential backoff`() {
        val strategy = NDKReconnectionStrategy()
        assertEquals(1_000L, strategy.nextDelay(0))
        assertEquals(2_000L, strategy.nextDelay(1))
        assertEquals(4_000L, strategy.nextDelay(2))
        assertEquals(8_000L, strategy.nextDelay(3))
        assertEquals(16_000L, strategy.nextDelay(4))
        assertEquals(32_000L, strategy.nextDelay(5))
    }

    @Test
    fun `nextDelay caps at max delay`() {
        val strategy = NDKReconnectionStrategy()
        assertEquals(60_000L, strategy.nextDelay(10))
        assertEquals(60_000L, strategy.nextDelay(20))
        assertEquals(60_000L, strategy.nextDelay(100))
    }

    @Test
    fun `isFlapping returns true for connections shorter than threshold`() {
        val strategy = NDKReconnectionStrategy()
        assertTrue(strategy.isFlapping(500L))
        assertTrue(strategy.isFlapping(999L))
        assertTrue(strategy.isFlapping(1L))
    }

    @Test
    fun `isFlapping returns false for connections at or above threshold`() {
        val strategy = NDKReconnectionStrategy()
        assertFalse(strategy.isFlapping(1_000L))
        assertFalse(strategy.isFlapping(1_001L))
        assertFalse(strategy.isFlapping(10_000L))
    }
}
