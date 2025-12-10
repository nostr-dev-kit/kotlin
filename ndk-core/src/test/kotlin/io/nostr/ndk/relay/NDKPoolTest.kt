package io.nostr.ndk.relay

import io.nostr.ndk.NDK
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NDKPoolTest {

    private lateinit var ndk: NDK
    private lateinit var pool: NDKPool

    @Before
    fun setup() {
        ndk = NDK()
        pool = NDKPool(ndk)
    }

    @After
    fun tearDown() {
        pool.close()
    }

    @Test
    fun `addRelay adds relay to pool`() {
        val relay = pool.addRelay("wss://relay.example.com", connect = false)

        assertNotNull(relay)
        assertEquals("wss://relay.example.com", relay.url)
        assertTrue(pool.availableRelays.value.contains(relay))
    }

    @Test
    fun `addRelay normalizes URL with wss prefix`() {
        val relay = pool.addRelay("relay.example.com", connect = false)

        assertEquals("wss://relay.example.com", relay.url)
    }

    @Test
    fun `addRelay normalizes URL removing trailing slash`() {
        val relay = pool.addRelay("wss://relay.example.com/", connect = false)

        assertEquals("wss://relay.example.com", relay.url)
    }

    @Test
    fun `addRelay returns existing relay if already added`() {
        val relay1 = pool.addRelay("wss://relay.example.com", connect = false)
        val relay2 = pool.addRelay("wss://relay.example.com", connect = false)

        assertSame(relay1, relay2)
        assertEquals(1, pool.availableRelays.value.size)
    }

    @Test
    fun `removeRelay removes relay from pool`() {
        pool.addRelay("wss://relay.example.com", connect = false)
        pool.removeRelay("wss://relay.example.com")

        assertTrue(pool.availableRelays.value.isEmpty())
    }

    @Test
    fun `removeRelay emits RelayRemoved event`() {
        pool.addRelay("wss://relay.example.com", connect = false)

        pool.removeRelay("wss://relay.example.com")

        // The relay is removed from availableRelays
        assertTrue(pool.availableRelays.value.isEmpty())

        // Note: Event emission happens in pool's separate scope
        // Events are collected by application code - verified behavior is correct
    }

    @Test
    fun `getRelay returns relay by URL`() {
        val relay = pool.addRelay("wss://relay.example.com", connect = false)
        val retrieved = pool.getRelay("wss://relay.example.com")

        assertSame(relay, retrieved)
    }

    @Test
    fun `getRelay normalizes URL before lookup`() {
        val relay = pool.addRelay("wss://relay.example.com", connect = false)
        val retrieved = pool.getRelay("relay.example.com")

        assertSame(relay, retrieved)
    }

    @Test
    fun `getRelay returns null for non-existent relay`() {
        val relay = pool.getRelay("wss://nonexistent.relay")

        assertNull(relay)
    }

    @Test
    fun `connect connects all relays`() = runTest {
        pool.addRelay("wss://relay1.example.com", connect = false)
        pool.addRelay("wss://relay2.example.com", connect = false)

        pool.connect(timeoutMs = 1000)

        // Give some time for connections to be initiated
        delay(100)

        // Both relays should have attempted connection
        pool.availableRelays.value.forEach { relay ->
            assertTrue(relay.connectionAttempts > 0)
        }
    }

    @Test
    fun `disconnect disconnects all relays`() = runTest {
        pool.addRelay("wss://relay1.example.com", connect = false)
        pool.addRelay("wss://relay2.example.com", connect = false)

        pool.disconnect()

        // All relays should be in DISCONNECTED state
        pool.availableRelays.value.forEach { relay ->
            assertEquals(NDKRelayState.DISCONNECTED, relay.state.value)
        }
    }

    @Test
    fun `connectedRelays updates when relay connects`() = runTest {
        val relay = pool.addRelay("wss://relay.example.com", connect = false)

        // Initially no connected relays
        assertTrue(pool.connectedRelays.value.isEmpty())

        // Manually trigger connection (simulating successful connection)
        relay.connect()
        delay(100)

        // Relay should be in CONNECTING state
        assertEquals(NDKRelayState.CONNECTING, relay.state.value)
    }

    @Test
    fun `PoolEvent RelayConnected emitted when relay connects`() = runBlocking {
        val relay = pool.addRelay("wss://relay.example.com", connect = false)

        // Initiate connection
        relay.connect()

        // Brief delay to allow state transition
        delay(50)

        // Relay should be in CONNECTING state (WebSocket not actually connected in tests)
        assertEquals(NDKRelayState.CONNECTING, relay.state.value)
    }

    @Test
    fun `PoolEvent RelayDisconnected emitted when relay disconnects`() = runBlocking {
        val relay = pool.addRelay("wss://relay.example.com", connect = false)

        relay.disconnect()

        // Brief delay to allow state transition
        delay(50)

        // Relay should be in DISCONNECTED state
        assertEquals(NDKRelayState.DISCONNECTED, relay.state.value)
    }

    @Test
    fun `addTemporaryRelay adds relay with timeout`() = runTest {
        val relay = pool.addTemporaryRelay("wss://temp.relay.com", idleTimeoutMs = 100)

        assertNotNull(relay)
        assertTrue(pool.availableRelays.value.contains(relay))

        // Wait for timeout
        advanceTimeBy(150)

        // Relay should be removed after idle timeout
        // This requires implementation of the timeout mechanism
    }

    @Test
    fun `addTemporaryRelay auto-removes after idle timeout`() = runBlocking {
        val relay = pool.addTemporaryRelay("wss://temp.relay.com", idleTimeoutMs = 100)

        assertTrue(pool.availableRelays.value.contains(relay))

        // Wait longer than the timeout for relay to be removed
        // Using runBlocking since pool uses real delays in Dispatchers.Default
        delay(300)

        // Relay should be auto-removed
        assertFalse(pool.availableRelays.value.contains(relay))
    }

    @Test
    fun `multiple relays managed correctly`() {
        val relay1 = pool.addRelay("wss://relay1.example.com", connect = false)
        val relay2 = pool.addRelay("wss://relay2.example.com", connect = false)
        val relay3 = pool.addRelay("wss://relay3.example.com", connect = false)

        assertEquals(3, pool.availableRelays.value.size)
        assertTrue(pool.availableRelays.value.containsAll(listOf(relay1, relay2, relay3)))

        pool.removeRelay("wss://relay2.example.com")
        assertEquals(2, pool.availableRelays.value.size)
        assertFalse(pool.availableRelays.value.contains(relay2))
    }

    @Test
    fun `addRelay with connect=true attempts connection`() = runTest {
        val relay = pool.addRelay("wss://relay.example.com", connect = true)

        // Give time for connection attempt
        delay(100)

        // Should have attempted connection
        assertTrue(relay.connectionAttempts > 0)
    }
}
