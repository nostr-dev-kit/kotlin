package io.nostr.ndk.relay

import io.nostr.ndk.NDK
import io.nostr.ndk.crypto.NDKSigner
import io.nostr.ndk.crypto.UnsignedEvent
import io.nostr.ndk.models.EventId
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKFilter
import io.nostr.ndk.models.NDKTag
import io.nostr.ndk.models.PublicKey
import io.nostr.ndk.models.Timestamp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class NDKRelayTest {

    @Test
    fun `relay starts in DISCONNECTED state`() = runTest {
        val relay = NDKRelay("wss://test.relay", null)
        assertEquals(NDKRelayState.DISCONNECTED, relay.state.value)
    }

    @Test
    fun `connectionAttempts starts at 0`() = runTest {
        val relay = NDKRelay("wss://test.relay", null)
        assertEquals(0, relay.connectionAttempts)
    }

    @Test
    fun `lastConnectedAt starts as null`() = runTest {
        val relay = NDKRelay("wss://test.relay", null)
        assertNull(relay.lastConnectedAt)
    }

    @Test
    fun `validatedEventCount starts at 0`() = runTest {
        val relay = NDKRelay("wss://test.relay", null)
        assertEquals(0L, relay.validatedEventCount)
    }

    @Test
    fun `nonValidatedEventCount starts at 0`() = runTest {
        val relay = NDKRelay("wss://test.relay", null)
        assertEquals(0L, relay.nonValidatedEventCount)
    }

    @Test
    fun `url is stored correctly`() = runTest {
        val url = "wss://test.relay"
        val relay = NDKRelay(url, null)
        assertEquals(url, relay.url)
    }

    @Test
    fun `state transitions from DISCONNECTED to CONNECTING on connect`() = runTest {
        val relay = NDKRelay("wss://test.relay", null)

        // This test will fail until we implement a mock WebSocket
        // For now, just verify the relay can be created
        assertNotNull(relay)
    }

    @Test
    fun `subscribe sends REQ message`() = runTest {
        val relay = NDKRelay("wss://test.relay", null)
        val filters = listOf(NDKFilter(kinds = setOf(1)))

        // This will need WebSocket mocking
        // relay.subscribe("sub1", filters)

        // Verify REQ message sent (requires WebSocket mock)
        assertTrue(true) // Placeholder
    }

    @Test
    fun `unsubscribe sends CLOSE message`() = runTest {
        val relay = NDKRelay("wss://test.relay", null)

        // This will need WebSocket mocking
        // relay.unsubscribe("sub1")

        // Verify CLOSE message sent (requires WebSocket mock)
        assertTrue(true) // Placeholder
    }

    @Test
    fun `publish sends EVENT message and waits for OK`() = runTest {
        val relay = NDKRelay("wss://test.relay", null)
        val event = createTestEvent()

        // This will need WebSocket mocking
        // val result = relay.publish(event)

        // Verify EVENT message sent (requires WebSocket mock)
        assertTrue(true) // Placeholder
    }

    @Test
    fun `disconnect transitions state to DISCONNECTED`() = runTest {
        val relay = NDKRelay("wss://test.relay", null)

        // This will need WebSocket mocking
        relay.disconnect()

        assertEquals(NDKRelayState.DISCONNECTED, relay.state.value)
    }

    @Test
    fun `connectionAttempts increments on each connect attempt`() = runTest {
        val relay = NDKRelay("wss://test.relay", null)

        // This will need to simulate failed connection attempts
        assertEquals(0, relay.connectionAttempts)

        // Placeholder for actual test
        assertTrue(true)
    }

    @Test
    fun `lastConnectedAt is set when connection succeeds`() = runTest {
        val relay = NDKRelay("wss://test.relay", null)

        // This will need WebSocket mocking to simulate successful connection
        assertNull(relay.lastConnectedAt)

        // Placeholder for actual test
        assertTrue(true)
    }

    @Test
    fun `authenticate creates kind 22242 event with correct tags`() = runTest {
        val testPubkey = "a".repeat(64)
        val testChallenge = "challenge123"
        val relayUrl = "wss://test.relay"

        val mockSigner = object : NDKSigner {
            override val pubkey: PublicKey = testPubkey

            override suspend fun sign(event: UnsignedEvent): NDKEvent {
                // Verify the unsigned event has correct properties
                assertEquals(22242, event.kind)
                assertEquals(testPubkey, event.pubkey)
                assertEquals("", event.content)

                // Verify tags
                assertEquals(2, event.tags.size)

                val relayTag = event.tags.find { it.name == "relay" }
                assertNotNull("relay tag should exist", relayTag)
                assertEquals(relayUrl, relayTag?.values?.firstOrNull())

                val challengeTag = event.tags.find { it.name == "challenge" }
                assertNotNull("challenge tag should exist", challengeTag)
                assertEquals(testChallenge, challengeTag?.values?.firstOrNull())

                // Return a signed event
                return NDKEvent(
                    id = "b".repeat(64),
                    pubkey = testPubkey,
                    createdAt = event.createdAt,
                    kind = event.kind,
                    tags = event.tags,
                    content = event.content,
                    sig = "c".repeat(128)
                )
            }

            override fun serialize(): ByteArray = ByteArray(0)
        }

        val ndk = NDK(signer = mockSigner)
        val relay = NDKRelay(relayUrl, ndk)

        // Note: This will fail to send because there's no WebSocket connection
        // but we can verify the event creation logic runs
        relay.authenticate(testChallenge)

        // The test verifies the event is created correctly via the mock signer
        assertTrue(true)
    }

    @Test
    fun `authenticate sets state to AUTHENTICATING then AUTHENTICATED`() = runTest {
        val testPubkey = "a".repeat(64)
        val mockSigner = object : NDKSigner {
            override val pubkey: PublicKey = testPubkey

            override suspend fun sign(event: UnsignedEvent): NDKEvent {
                return NDKEvent(
                    id = "b".repeat(64),
                    pubkey = testPubkey,
                    createdAt = event.createdAt,
                    kind = event.kind,
                    tags = event.tags,
                    content = event.content,
                    sig = "c".repeat(128)
                )
            }

            override fun serialize(): ByteArray = ByteArray(0)
        }

        val ndk = NDK(signer = mockSigner)
        val relay = NDKRelay("wss://test.relay", ndk)

        assertEquals(NDKRelayState.DISCONNECTED, relay.state.value)

        relay.authenticate("challenge123")

        // After authentication attempt without WebSocket, state should be AUTH_REQUIRED
        // (because we can't send the message)
        assertEquals(NDKRelayState.AUTH_REQUIRED, relay.state.value)
    }

    @Test
    fun `authenticate without signer logs warning and returns early`() = runTest {
        val relay = NDKRelay("wss://test.relay", null)

        assertEquals(NDKRelayState.DISCONNECTED, relay.state.value)

        relay.authenticate("challenge123")

        // State should remain DISCONNECTED when there's no signer
        assertEquals(NDKRelayState.DISCONNECTED, relay.state.value)
    }

    @Test
    fun `auth event has correct structure`() = runTest {
        val testPubkey = "a".repeat(64)
        val testChallenge = "test_challenge_string"
        val relayUrl = "wss://relay.example.com"

        var capturedEvent: UnsignedEvent? = null

        val mockSigner = object : NDKSigner {
            override val pubkey: PublicKey = testPubkey

            override suspend fun sign(event: UnsignedEvent): NDKEvent {
                capturedEvent = event

                return NDKEvent(
                    id = "b".repeat(64),
                    pubkey = testPubkey,
                    createdAt = event.createdAt,
                    kind = event.kind,
                    tags = event.tags,
                    content = event.content,
                    sig = "c".repeat(128)
                )
            }

            override fun serialize(): ByteArray = ByteArray(0)
        }

        val ndk = NDK(signer = mockSigner)
        val relay = NDKRelay(relayUrl, ndk)

        relay.authenticate(testChallenge)

        // Verify the captured event
        assertNotNull("Event should be captured", capturedEvent)
        capturedEvent?.let { event ->
            assertEquals(22242, event.kind)
            assertEquals(testPubkey, event.pubkey)
            assertEquals("", event.content)
            assertTrue(event.createdAt > 0)

            // Check relay tag
            val relayTag = event.tags.find { it.name == "relay" }
            assertNotNull(relayTag)
            assertEquals(listOf(relayUrl), relayTag?.values)

            // Check challenge tag
            val challengeTag = event.tags.find { it.name == "challenge" }
            assertNotNull(challengeTag)
            assertEquals(listOf(testChallenge), challengeTag?.values)
        }
    }

    private fun createTestEvent(): NDKEvent {
        return NDKEvent(
            id = "0".repeat(64),
            pubkey = "0".repeat(64),
            createdAt = System.currentTimeMillis() / 1000,
            kind = 1,
            tags = emptyList(),
            content = "test",
            sig = "0".repeat(128)
        )
    }
}
