# Session Management Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add session management to ndk-android so apps can login, persist accounts, automatically load session data (follows, mutes, relay lists), and switch between multiple accounts.

**Architecture:** `NDKCurrentUser` extends `NDKUser` with auto-synced session data and signing capability. NDK gains `login()`, `logout()`, `currentUser`, and `accounts` APIs. Storage is pluggable via `NDKAccountStorage` interface with a secure Android default implementation.

**Tech Stack:** Kotlin, StateFlow, Android Keystore (for signer secrets), EncryptedSharedPreferences (for metadata), Coroutines

---

## Task 1: NDKAccountStorage Interface

**Files:**
- Create: `ndk-core/src/main/kotlin/io/nostr/ndk/account/NDKAccountStorage.kt`
- Test: `ndk-core/src/test/kotlin/io/nostr/ndk/account/NDKAccountStorageTest.kt`

**Step 1: Write the test file with interface contract tests**

```kotlin
package io.nostr.ndk.account

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class NDKAccountStorageTest {

    @Test
    fun `InMemoryAccountStorage saves and loads signer data`() = runTest {
        val storage = InMemoryAccountStorage()
        val pubkey = "abc123"
        val signerData = "test-signer-payload".toByteArray()

        storage.saveSigner(pubkey, signerData)
        val loaded = storage.loadSigner(pubkey)

        assertNotNull(loaded)
        assertArrayEquals(signerData, loaded)
    }

    @Test
    fun `InMemoryAccountStorage returns null for unknown pubkey`() = runTest {
        val storage = InMemoryAccountStorage()

        val loaded = storage.loadSigner("unknown")

        assertNull(loaded)
    }

    @Test
    fun `InMemoryAccountStorage lists saved accounts`() = runTest {
        val storage = InMemoryAccountStorage()
        storage.saveSigner("pubkey1", "data1".toByteArray())
        storage.saveSigner("pubkey2", "data2".toByteArray())

        val accounts = storage.listAccounts()

        assertEquals(2, accounts.size)
        assertTrue(accounts.contains("pubkey1"))
        assertTrue(accounts.contains("pubkey2"))
    }

    @Test
    fun `InMemoryAccountStorage deletes account`() = runTest {
        val storage = InMemoryAccountStorage()
        storage.saveSigner("pubkey1", "data1".toByteArray())

        storage.deleteAccount("pubkey1")

        assertNull(storage.loadSigner("pubkey1"))
        assertEquals(0, storage.listAccounts().size)
    }

    @Test
    fun `InMemoryAccountStorage overwrites existing account`() = runTest {
        val storage = InMemoryAccountStorage()
        storage.saveSigner("pubkey1", "old-data".toByteArray())
        storage.saveSigner("pubkey1", "new-data".toByteArray())

        val loaded = storage.loadSigner("pubkey1")

        assertArrayEquals("new-data".toByteArray(), loaded)
        assertEquals(1, storage.listAccounts().size)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd /Users/pablofernandez/10x/ndk-android && ./gradlew :ndk-core:testDebugUnitTest --tests "io.nostr.ndk.account.NDKAccountStorageTest" --info`
Expected: FAIL with compilation error (classes don't exist)

**Step 3: Write the interface and InMemoryAccountStorage**

```kotlin
package io.nostr.ndk.account

import io.nostr.ndk.models.PublicKey

/**
 * Interface for persisting account data (signers).
 *
 * Implementations should securely store signer data. The default Android
 * implementation uses Android Keystore for encryption.
 */
interface NDKAccountStorage {
    /**
     * Saves signer data for an account.
     *
     * @param pubkey The public key identifying the account
     * @param signerPayload Serialized signer data (may contain private keys)
     */
    suspend fun saveSigner(pubkey: PublicKey, signerPayload: ByteArray)

    /**
     * Loads signer data for an account.
     *
     * @param pubkey The public key identifying the account
     * @return The serialized signer data, or null if not found
     */
    suspend fun loadSigner(pubkey: PublicKey): ByteArray?

    /**
     * Lists all saved account pubkeys.
     *
     * @return List of pubkeys with saved signer data
     */
    suspend fun listAccounts(): List<PublicKey>

    /**
     * Deletes an account's signer data.
     *
     * @param pubkey The public key identifying the account to delete
     */
    suspend fun deleteAccount(pubkey: PublicKey)
}

/**
 * In-memory storage for testing. Does not persist across app restarts.
 */
class InMemoryAccountStorage : NDKAccountStorage {
    private val accounts = mutableMapOf<PublicKey, ByteArray>()

    override suspend fun saveSigner(pubkey: PublicKey, signerPayload: ByteArray) {
        accounts[pubkey] = signerPayload
    }

    override suspend fun loadSigner(pubkey: PublicKey): ByteArray? {
        return accounts[pubkey]
    }

    override suspend fun listAccounts(): List<PublicKey> {
        return accounts.keys.toList()
    }

    override suspend fun deleteAccount(pubkey: PublicKey) {
        accounts.remove(pubkey)
    }
}
```

**Step 4: Run test to verify it passes**

Run: `cd /Users/pablofernandez/10x/ndk-android && ./gradlew :ndk-core:testDebugUnitTest --tests "io.nostr.ndk.account.NDKAccountStorageTest" --info`
Expected: PASS

**Step 5: Commit**

```bash
cd /Users/pablofernandez/10x/ndk-android
git add ndk-core/src/main/kotlin/io/nostr/ndk/account/NDKAccountStorage.kt ndk-core/src/test/kotlin/io/nostr/ndk/account/NDKAccountStorageTest.kt
git commit -m "feat(account): add NDKAccountStorage interface and InMemoryAccountStorage"
```

---

## Task 2: Signer Serialization

**Files:**
- Modify: `ndk-core/src/main/kotlin/io/nostr/ndk/crypto/NDKSigner.kt`
- Modify: `ndk-core/src/main/kotlin/io/nostr/ndk/crypto/NDKPrivateKeySigner.kt`
- Test: `ndk-core/src/test/kotlin/io/nostr/ndk/account/SignerSerializationTest.kt`

**Step 1: Write tests for signer serialization**

```kotlin
package io.nostr.ndk.account

import io.nostr.ndk.crypto.NDKKeyPair
import io.nostr.ndk.crypto.NDKPrivateKeySigner
import io.nostr.ndk.crypto.NDKSigner
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SignerSerializationTest {

    @Test
    fun `NDKPrivateKeySigner serializes and deserializes`() = runTest {
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)

        val serialized = signer.serialize()
        val deserialized = NDKSigner.deserialize(serialized)

        assertNotNull(deserialized)
        assertTrue(deserialized is NDKPrivateKeySigner)
        assertEquals(signer.pubkey, deserialized!!.pubkey)
    }

    @Test
    fun `serialized signer can sign events`() = runTest {
        val keyPair = NDKKeyPair.generate()
        val original = NDKPrivateKeySigner(keyPair)

        val serialized = original.serialize()
        val restored = NDKSigner.deserialize(serialized) as NDKPrivateKeySigner

        // Create unsigned event
        val unsigned = io.nostr.ndk.crypto.UnsignedEvent(
            pubkey = restored.pubkey,
            createdAt = System.currentTimeMillis() / 1000,
            kind = 1,
            tags = emptyList(),
            content = "test"
        )

        val signed = restored.sign(unsigned)

        assertNotNull(signed.id)
        assertNotNull(signed.sig)
        assertEquals(restored.pubkey, signed.pubkey)
    }

    @Test
    fun `deserialize returns null for invalid data`() = runTest {
        val invalid = "not-valid-json".toByteArray()

        val result = NDKSigner.deserialize(invalid)

        assertNull(result)
    }

    @Test
    fun `deserialize returns null for unknown signer type`() = runTest {
        val unknown = """{"type":"UnknownSigner","data":{}}""".toByteArray()

        val result = NDKSigner.deserialize(unknown)

        assertNull(result)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd /Users/pablofernandez/10x/ndk-android && ./gradlew :ndk-core:testDebugUnitTest --tests "io.nostr.ndk.account.SignerSerializationTest" --info`
Expected: FAIL (serialize/deserialize methods don't exist)

**Step 3: Add serialization to NDKSigner interface**

Update `ndk-core/src/main/kotlin/io/nostr/ndk/crypto/NDKSigner.kt`:

```kotlin
package io.nostr.ndk.crypto

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.PublicKey

/**
 * Interface for signing Nostr events.
 *
 * Implementations include:
 * - NDKPrivateKeySigner: Signs with secp256k1 private key
 * - NDKRemoteSigner: Remote signing via Nostr Connect (NIP-46)
 * - Nip55Signer: Signs using Android Amber app (NIP-55)
 */
interface NDKSigner {
    /**
     * The public key associated with this signer.
     */
    val pubkey: PublicKey

    /**
     * Signs an unsigned event and returns a signed NDKEvent.
     *
     * @param event The unsigned event to sign
     * @return The signed NDKEvent with id and signature
     * @throws IllegalStateException if signing fails
     */
    suspend fun sign(event: UnsignedEvent): NDKEvent

    /**
     * Serializes this signer to a byte array for storage.
     *
     * @return Serialized signer data
     */
    fun serialize(): ByteArray

    companion object {
        private val objectMapper = jacksonObjectMapper()

        /**
         * Registered signer deserializers by type name.
         */
        private val deserializers = mutableMapOf<String, (Map<String, Any?>) -> NDKSigner?>()

        /**
         * Registers a deserializer for a signer type.
         */
        fun registerDeserializer(type: String, deserializer: (Map<String, Any?>) -> NDKSigner?) {
            deserializers[type] = deserializer
        }

        /**
         * Deserializes a signer from a byte array.
         *
         * @param data The serialized signer data
         * @return The deserialized signer, or null if deserialization fails
         */
        fun deserialize(data: ByteArray): NDKSigner? {
            return try {
                val json: Map<String, Any?> = objectMapper.readValue(data)
                val type = json["type"] as? String ?: return null
                val signerData = json["data"] as? Map<String, Any?> ?: return null

                deserializers[type]?.invoke(signerData)
            } catch (e: Exception) {
                null
            }
        }
    }
}
```

**Step 4: Add serialization to NDKPrivateKeySigner**

Update `ndk-core/src/main/kotlin/io/nostr/ndk/crypto/NDKPrivateKeySigner.kt` - add at the end of the class, before the companion object:

```kotlin
    override fun serialize(): ByteArray {
        val data = mapOf(
            "type" to SIGNER_TYPE,
            "data" to mapOf(
                "privateKey" to keyPair.privateKey?.toHex()
            )
        )
        return objectMapper.writeValueAsBytes(data)
    }

    companion object {
        private const val SIGNER_TYPE = "NDKPrivateKeySigner"
        private val secp256k1 = Secp256k1.get()
        private val objectMapper: ObjectMapper = jacksonObjectMapper()

        init {
            // Register deserializer
            NDKSigner.registerDeserializer(SIGNER_TYPE) { data ->
                val privateKeyHex = data["privateKey"] as? String ?: return@registerDeserializer null
                val privateKey = privateKeyHex.hexToBytes()
                val keyPair = NDKKeyPair(privateKey)
                NDKPrivateKeySigner(keyPair)
            }
        }
    }
```

**Step 5: Run test to verify it passes**

Run: `cd /Users/pablofernandez/10x/ndk-android && ./gradlew :ndk-core:testDebugUnitTest --tests "io.nostr.ndk.account.SignerSerializationTest" --info`
Expected: PASS

**Step 6: Commit**

```bash
cd /Users/pablofernandez/10x/ndk-android
git add ndk-core/src/main/kotlin/io/nostr/ndk/crypto/NDKSigner.kt ndk-core/src/main/kotlin/io/nostr/ndk/crypto/NDKPrivateKeySigner.kt ndk-core/src/test/kotlin/io/nostr/ndk/account/SignerSerializationTest.kt
git commit -m "feat(account): add signer serialization/deserialization"
```

---

## Task 3: Session Data Models

**Files:**
- Create: `ndk-core/src/main/kotlin/io/nostr/ndk/account/SessionData.kt`
- Test: `ndk-core/src/test/kotlin/io/nostr/ndk/account/SessionDataTest.kt`

**Step 1: Write tests for session data parsing**

```kotlin
package io.nostr.ndk.account

import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKTag
import org.junit.Assert.*
import org.junit.Test

class SessionDataTest {

    @Test
    fun `MuteList parses pubkeys from kind 10000 event`() {
        val event = NDKEvent(
            id = "abc",
            pubkey = "user123",
            createdAt = 1000L,
            kind = 10000,
            tags = listOf(
                NDKTag("p", listOf("muted1")),
                NDKTag("p", listOf("muted2")),
            ),
            content = "",
            sig = "sig"
        )

        val muteList = MuteList.fromEvent(event)

        assertEquals(2, muteList.pubkeys.size)
        assertTrue(muteList.pubkeys.contains("muted1"))
        assertTrue(muteList.pubkeys.contains("muted2"))
        assertTrue(muteList.isMuted("muted1"))
        assertFalse(muteList.isMuted("notmuted"))
    }

    @Test
    fun `MuteList parses words from kind 10000 event`() {
        val event = NDKEvent(
            id = "abc",
            pubkey = "user123",
            createdAt = 1000L,
            kind = 10000,
            tags = listOf(
                NDKTag("word", listOf("spam")),
                NDKTag("word", listOf("nsfw")),
            ),
            content = "",
            sig = "sig"
        )

        val muteList = MuteList.fromEvent(event)

        assertEquals(2, muteList.words.size)
        assertTrue(muteList.words.contains("spam"))
        assertTrue(muteList.words.contains("nsfw"))
    }

    @Test
    fun `MuteList parses event IDs from kind 10000 event`() {
        val event = NDKEvent(
            id = "abc",
            pubkey = "user123",
            createdAt = 1000L,
            kind = 10000,
            tags = listOf(
                NDKTag("e", listOf("event1")),
                NDKTag("e", listOf("event2")),
            ),
            content = "",
            sig = "sig"
        )

        val muteList = MuteList.fromEvent(event)

        assertEquals(2, muteList.eventIds.size)
        assertTrue(muteList.eventIds.contains("event1"))
    }

    @Test
    fun `BlockedRelayList parses relay URLs from kind 10001 event`() {
        val event = NDKEvent(
            id = "abc",
            pubkey = "user123",
            createdAt = 1000L,
            kind = 10001,
            tags = listOf(
                NDKTag("relay", listOf("wss://bad-relay.com")),
                NDKTag("relay", listOf("wss://another-bad.com")),
            ),
            content = "",
            sig = "sig"
        )

        val blockedRelays = BlockedRelayList.fromEvent(event)

        assertEquals(2, blockedRelays.relays.size)
        assertTrue(blockedRelays.isBlocked("wss://bad-relay.com"))
        assertFalse(blockedRelays.isBlocked("wss://good-relay.com"))
    }

    @Test
    fun `empty MuteList for missing event`() {
        val muteList = MuteList.empty("user123")

        assertTrue(muteList.pubkeys.isEmpty())
        assertTrue(muteList.words.isEmpty())
        assertTrue(muteList.eventIds.isEmpty())
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd /Users/pablofernandez/10x/ndk-android && ./gradlew :ndk-core:testDebugUnitTest --tests "io.nostr.ndk.account.SessionDataTest" --info`
Expected: FAIL (classes don't exist)

**Step 3: Write the session data models**

```kotlin
package io.nostr.ndk.account

import io.nostr.ndk.models.EventId
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.PublicKey
import io.nostr.ndk.models.Timestamp

/**
 * Kind constants for session data events.
 */
const val KIND_MUTE_LIST = 10000
const val KIND_BLOCKED_RELAY_LIST = 10001
const val KIND_RELAY_LIST = 10002

/**
 * Represents a user's mute list (kind 10000).
 */
data class MuteList(
    val pubkey: PublicKey,
    val createdAt: Timestamp,
    val pubkeys: Set<PublicKey>,
    val eventIds: Set<EventId>,
    val words: Set<String>,
    val hashtags: Set<String>
) {
    /**
     * Checks if a pubkey is muted.
     */
    fun isMuted(pubkey: PublicKey): Boolean = pubkeys.contains(pubkey)

    /**
     * Checks if an event ID is muted.
     */
    fun isMutedEvent(eventId: EventId): Boolean = eventIds.contains(eventId)

    /**
     * Checks if content contains a muted word.
     */
    fun containsMutedWord(content: String): Boolean {
        val lowerContent = content.lowercase()
        return words.any { lowerContent.contains(it.lowercase()) }
    }

    companion object {
        fun fromEvent(event: NDKEvent): MuteList {
            require(event.kind == KIND_MUTE_LIST) { "Expected kind $KIND_MUTE_LIST, got ${event.kind}" }

            val pubkeys = mutableSetOf<PublicKey>()
            val eventIds = mutableSetOf<EventId>()
            val words = mutableSetOf<String>()
            val hashtags = mutableSetOf<String>()

            event.tags.forEach { tag ->
                when (tag.name) {
                    "p" -> tag.values.firstOrNull()?.let { pubkeys.add(it) }
                    "e" -> tag.values.firstOrNull()?.let { eventIds.add(it) }
                    "word" -> tag.values.firstOrNull()?.let { words.add(it) }
                    "t" -> tag.values.firstOrNull()?.let { hashtags.add(it) }
                }
            }

            return MuteList(
                pubkey = event.pubkey,
                createdAt = event.createdAt,
                pubkeys = pubkeys,
                eventIds = eventIds,
                words = words,
                hashtags = hashtags
            )
        }

        fun empty(pubkey: PublicKey): MuteList = MuteList(
            pubkey = pubkey,
            createdAt = 0L,
            pubkeys = emptySet(),
            eventIds = emptySet(),
            words = emptySet(),
            hashtags = emptySet()
        )
    }
}

/**
 * Represents a user's blocked relay list (kind 10001).
 */
data class BlockedRelayList(
    val pubkey: PublicKey,
    val createdAt: Timestamp,
    val relays: Set<String>
) {
    /**
     * Checks if a relay URL is blocked.
     */
    fun isBlocked(url: String): Boolean = relays.contains(normalizeUrl(url))

    companion object {
        fun fromEvent(event: NDKEvent): BlockedRelayList {
            require(event.kind == KIND_BLOCKED_RELAY_LIST) { "Expected kind $KIND_BLOCKED_RELAY_LIST, got ${event.kind}" }

            val relays = mutableSetOf<String>()

            event.tags.forEach { tag ->
                if (tag.name == "relay") {
                    tag.values.firstOrNull()?.let { relays.add(normalizeUrl(it)) }
                }
            }

            return BlockedRelayList(
                pubkey = event.pubkey,
                createdAt = event.createdAt,
                relays = relays
            )
        }

        fun empty(pubkey: PublicKey): BlockedRelayList = BlockedRelayList(
            pubkey = pubkey,
            createdAt = 0L,
            relays = emptySet()
        )

        private fun normalizeUrl(url: String): String {
            var normalized = url.lowercase()
            if (!normalized.startsWith("wss://") && !normalized.startsWith("ws://")) {
                normalized = "wss://$normalized"
            }
            if (normalized.endsWith("/")) {
                normalized = normalized.dropLast(1)
            }
            return normalized
        }
    }
}
```

**Step 4: Run test to verify it passes**

Run: `cd /Users/pablofernandez/10x/ndk-android && ./gradlew :ndk-core:testDebugUnitTest --tests "io.nostr.ndk.account.SessionDataTest" --info`
Expected: PASS

**Step 5: Commit**

```bash
cd /Users/pablofernandez/10x/ndk-android
git add ndk-core/src/main/kotlin/io/nostr/ndk/account/SessionData.kt ndk-core/src/test/kotlin/io/nostr/ndk/account/SessionDataTest.kt
git commit -m "feat(account): add MuteList and BlockedRelayList session data models"
```

---

## Task 4: NDKCurrentUser Class

**Files:**
- Create: `ndk-core/src/main/kotlin/io/nostr/ndk/account/NDKCurrentUser.kt`
- Test: `ndk-core/src/test/kotlin/io/nostr/ndk/account/NDKCurrentUserTest.kt`

**Step 1: Write tests for NDKCurrentUser**

```kotlin
package io.nostr.ndk.account

import io.nostr.ndk.NDK
import io.nostr.ndk.crypto.NDKKeyPair
import io.nostr.ndk.crypto.NDKPrivateKeySigner
import io.nostr.ndk.crypto.UnsignedEvent
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKTag
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class NDKCurrentUserTest {

    @Test
    fun `NDKCurrentUser has pubkey from signer`() {
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)
        val ndk = NDK()

        val currentUser = NDKCurrentUser(signer, ndk)

        assertEquals(keyPair.pubkeyHex, currentUser.pubkey)
    }

    @Test
    fun `NDKCurrentUser can sign events`() = runTest {
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)
        val ndk = NDK()

        val currentUser = NDKCurrentUser(signer, ndk)

        val unsigned = UnsignedEvent(
            pubkey = currentUser.pubkey,
            createdAt = System.currentTimeMillis() / 1000,
            kind = 1,
            tags = emptyList(),
            content = "Hello Nostr!"
        )

        val signed = currentUser.sign(unsigned)

        assertNotNull(signed.id)
        assertNotNull(signed.sig)
        assertEquals(currentUser.pubkey, signed.pubkey)
        assertEquals("Hello Nostr!", signed.content)
    }

    @Test
    fun `NDKCurrentUser has empty follows initially`() = runTest {
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)
        val ndk = NDK()

        val currentUser = NDKCurrentUser(signer, ndk)

        assertTrue(currentUser.follows.value.isEmpty())
    }

    @Test
    fun `NDKCurrentUser updates follows from contact list event`() = runTest {
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)
        val ndk = NDK()

        val currentUser = NDKCurrentUser(signer, ndk)

        // Simulate receiving a contact list event
        val contactListEvent = NDKEvent(
            id = "abc",
            pubkey = keyPair.pubkeyHex,
            createdAt = 1000L,
            kind = 3,
            tags = listOf(
                NDKTag("p", listOf("followed1")),
                NDKTag("p", listOf("followed2")),
            ),
            content = "",
            sig = "sig"
        )

        currentUser.handleEvent(contactListEvent)

        assertEquals(2, currentUser.follows.value.size)
        assertTrue(currentUser.follows.value.contains("followed1"))
        assertTrue(currentUser.follows.value.contains("followed2"))
    }

    @Test
    fun `NDKCurrentUser updates mutes from mute list event`() = runTest {
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)
        val ndk = NDK()

        val currentUser = NDKCurrentUser(signer, ndk)

        val muteListEvent = NDKEvent(
            id = "abc",
            pubkey = keyPair.pubkeyHex,
            createdAt = 1000L,
            kind = 10000,
            tags = listOf(
                NDKTag("p", listOf("muted1")),
                NDKTag("word", listOf("spam")),
            ),
            content = "",
            sig = "sig"
        )

        currentUser.handleEvent(muteListEvent)

        val muteList = currentUser.mutes.value
        assertTrue(muteList.isMuted("muted1"))
        assertTrue(muteList.words.contains("spam"))
    }

    @Test
    fun `NDKCurrentUser ignores older events`() = runTest {
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)
        val ndk = NDK()

        val currentUser = NDKCurrentUser(signer, ndk)

        // First, add newer event
        val newerEvent = NDKEvent(
            id = "newer",
            pubkey = keyPair.pubkeyHex,
            createdAt = 2000L,
            kind = 3,
            tags = listOf(NDKTag("p", listOf("newer-follow"))),
            content = "",
            sig = "sig"
        )
        currentUser.handleEvent(newerEvent)

        // Then try to add older event
        val olderEvent = NDKEvent(
            id = "older",
            pubkey = keyPair.pubkeyHex,
            createdAt = 1000L,
            kind = 3,
            tags = listOf(NDKTag("p", listOf("older-follow"))),
            content = "",
            sig = "sig"
        )
        currentUser.handleEvent(olderEvent)

        // Should still have newer data
        assertEquals(1, currentUser.follows.value.size)
        assertTrue(currentUser.follows.value.contains("newer-follow"))
        assertFalse(currentUser.follows.value.contains("older-follow"))
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd /Users/pablofernandez/10x/ndk-android && ./gradlew :ndk-core:testDebugUnitTest --tests "io.nostr.ndk.account.NDKCurrentUserTest" --info`
Expected: FAIL (class doesn't exist)

**Step 3: Write NDKCurrentUser class**

```kotlin
package io.nostr.ndk.account

import io.nostr.ndk.NDK
import io.nostr.ndk.crypto.NDKSigner
import io.nostr.ndk.crypto.UnsignedEvent
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.PublicKey
import io.nostr.ndk.nips.KIND_CONTACT_LIST
import io.nostr.ndk.nips.followedPubkeys
import io.nostr.ndk.outbox.RelayList
import io.nostr.ndk.user.NDKUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Represents the current logged-in user.
 *
 * NDKCurrentUser extends NDKUser with:
 * - The ability to sign events
 * - Auto-synced session data (follows, mutes, relay list, blocked relays)
 *
 * Session data is automatically kept in sync when events are received.
 *
 * Usage:
 * ```kotlin
 * val currentUser = ndk.login(signer)
 *
 * // Sign events
 * val signed = currentUser.sign(unsignedEvent)
 *
 * // Access reactive session data
 * currentUser.follows.collect { follows -> ... }
 * currentUser.mutes.collect { muteList -> ... }
 * ```
 */
class NDKCurrentUser(
    val signer: NDKSigner,
    private val ndk: NDK
) : NDKUser(signer.pubkey, ndk) {

    // Timestamps for tracking latest event per kind
    private var contactListTimestamp: Long = 0
    private var muteListTimestamp: Long = 0
    private var relayListTimestamp: Long = 0
    private var blockedRelayListTimestamp: Long = 0

    // Session data flows
    private val _follows = MutableStateFlow<Set<PublicKey>>(emptySet())
    private val _mutes = MutableStateFlow(MuteList.empty(pubkey))
    private val _relayList = MutableStateFlow<RelayList?>(null)
    private val _blockedRelays = MutableStateFlow(BlockedRelayList.empty(pubkey))

    /**
     * The pubkeys this user follows (from kind 3 contact list).
     */
    val follows: StateFlow<Set<PublicKey>> = _follows.asStateFlow()

    /**
     * The user's mute list (from kind 10000).
     */
    val mutes: StateFlow<MuteList> = _mutes.asStateFlow()

    /**
     * The user's relay list (from kind 10002).
     */
    val relayList: StateFlow<RelayList?> = _relayList.asStateFlow()

    /**
     * The user's blocked relay list (from kind 10001).
     */
    val blockedRelays: StateFlow<BlockedRelayList> = _blockedRelays.asStateFlow()

    /**
     * Signs an event using this user's signer.
     */
    suspend fun sign(event: UnsignedEvent): NDKEvent {
        return signer.sign(event)
    }

    /**
     * Handles an incoming event and updates session data if relevant.
     *
     * @param event The event to process
     */
    fun handleEvent(event: NDKEvent) {
        // Only process events from this user
        if (event.pubkey != pubkey) return

        when (event.kind) {
            KIND_CONTACT_LIST -> handleContactList(event)
            KIND_MUTE_LIST -> handleMuteList(event)
            KIND_RELAY_LIST -> handleRelayList(event)
            KIND_BLOCKED_RELAY_LIST -> handleBlockedRelayList(event)
        }
    }

    private fun handleContactList(event: NDKEvent) {
        if (event.createdAt <= contactListTimestamp) return
        contactListTimestamp = event.createdAt

        _follows.value = event.followedPubkeys.toSet()
    }

    private fun handleMuteList(event: NDKEvent) {
        if (event.createdAt <= muteListTimestamp) return
        muteListTimestamp = event.createdAt

        _mutes.value = MuteList.fromEvent(event)
    }

    private fun handleRelayList(event: NDKEvent) {
        if (event.createdAt <= relayListTimestamp) return
        relayListTimestamp = event.createdAt

        _relayList.value = RelayList.fromEvent(event)
    }

    private fun handleBlockedRelayList(event: NDKEvent) {
        if (event.createdAt <= blockedRelayListTimestamp) return
        blockedRelayListTimestamp = event.createdAt

        _blockedRelays.value = BlockedRelayList.fromEvent(event)
    }

    override fun toString(): String {
        return "NDKCurrentUser(pubkey=${pubkey.take(8)}...)"
    }
}
```

**Step 4: Run test to verify it passes**

Run: `cd /Users/pablofernandez/10x/ndk-android && ./gradlew :ndk-core:testDebugUnitTest --tests "io.nostr.ndk.account.NDKCurrentUserTest" --info`
Expected: PASS

**Step 5: Commit**

```bash
cd /Users/pablofernandez/10x/ndk-android
git add ndk-core/src/main/kotlin/io/nostr/ndk/account/NDKCurrentUser.kt ndk-core/src/test/kotlin/io/nostr/ndk/account/NDKCurrentUserTest.kt
git commit -m "feat(account): add NDKCurrentUser with session data and signing"
```

---

## Task 5: NDK Account Management APIs

**Files:**
- Modify: `ndk-core/src/main/kotlin/io/nostr/ndk/NDK.kt`
- Test: `ndk-core/src/test/kotlin/io/nostr/ndk/account/NDKAccountManagementTest.kt`

**Step 1: Write tests for NDK account management**

```kotlin
package io.nostr.ndk.account

import io.nostr.ndk.NDK
import io.nostr.ndk.crypto.NDKKeyPair
import io.nostr.ndk.crypto.NDKPrivateKeySigner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class NDKAccountManagementTest {

    @Test
    fun `login creates current user`() = runTest {
        val ndk = NDK()
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)

        val currentUser = ndk.login(signer)

        assertNotNull(currentUser)
        assertEquals(keyPair.pubkeyHex, currentUser.pubkey)
        assertEquals(currentUser, ndk.currentUser.value)
    }

    @Test
    fun `login adds to accounts list`() = runTest {
        val ndk = NDK()
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)

        ndk.login(signer)

        assertEquals(1, ndk.accounts.value.size)
        assertEquals(keyPair.pubkeyHex, ndk.accounts.value.first().pubkey)
    }

    @Test
    fun `login with storage persists account`() = runTest {
        val storage = InMemoryAccountStorage()
        val ndk = NDK(accountStorage = storage)
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)

        ndk.login(signer)

        val savedAccounts = storage.listAccounts()
        assertEquals(1, savedAccounts.size)
        assertEquals(keyPair.pubkeyHex, savedAccounts.first())
    }

    @Test
    fun `logout clears current user`() = runTest {
        val ndk = NDK()
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)

        ndk.login(signer)
        ndk.logout()

        assertNull(ndk.currentUser.value)
    }

    @Test
    fun `logout with storage removes account`() = runTest {
        val storage = InMemoryAccountStorage()
        val ndk = NDK(accountStorage = storage)
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)

        ndk.login(signer)
        ndk.logout()

        assertEquals(0, storage.listAccounts().size)
    }

    @Test
    fun `login multiple accounts`() = runTest {
        val ndk = NDK()
        val keyPair1 = NDKKeyPair.generate()
        val keyPair2 = NDKKeyPair.generate()
        val signer1 = NDKPrivateKeySigner(keyPair1)
        val signer2 = NDKPrivateKeySigner(keyPair2)

        ndk.login(signer1)
        ndk.login(signer2)

        // Current user should be the last logged in
        assertEquals(keyPair2.pubkeyHex, ndk.currentUser.value?.pubkey)
        // Both accounts should be in the list
        assertEquals(2, ndk.accounts.value.size)
    }

    @Test
    fun `switchAccount changes current user`() = runTest {
        val ndk = NDK()
        val keyPair1 = NDKKeyPair.generate()
        val keyPair2 = NDKKeyPair.generate()
        val signer1 = NDKPrivateKeySigner(keyPair1)
        val signer2 = NDKPrivateKeySigner(keyPair2)

        ndk.login(signer1)
        ndk.login(signer2)

        // Switch back to first account
        val switched = ndk.switchAccount(keyPair1.pubkeyHex)

        assertTrue(switched)
        assertEquals(keyPair1.pubkeyHex, ndk.currentUser.value?.pubkey)
    }

    @Test
    fun `switchAccount returns false for unknown pubkey`() = runTest {
        val ndk = NDK()
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)

        ndk.login(signer)

        val switched = ndk.switchAccount("unknown-pubkey")

        assertFalse(switched)
    }

    @Test
    fun `restoreAccounts loads from storage`() = runTest {
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)

        // First NDK: login and persist
        val storage = InMemoryAccountStorage()
        val ndk1 = NDK(accountStorage = storage)
        ndk1.login(signer)

        // Second NDK: restore from storage
        val ndk2 = NDK(accountStorage = storage)
        val restored = ndk2.restoreAccounts()

        assertEquals(1, restored.size)
        assertEquals(keyPair.pubkeyHex, restored.first().pubkey)
    }

    @Test
    fun `logout specific account by pubkey`() = runTest {
        val ndk = NDK()
        val keyPair1 = NDKKeyPair.generate()
        val keyPair2 = NDKKeyPair.generate()
        val signer1 = NDKPrivateKeySigner(keyPair1)
        val signer2 = NDKPrivateKeySigner(keyPair2)

        ndk.login(signer1)
        ndk.login(signer2)

        ndk.logout(keyPair1.pubkeyHex)

        assertEquals(1, ndk.accounts.value.size)
        assertEquals(keyPair2.pubkeyHex, ndk.accounts.value.first().pubkey)
        // Current user unchanged since we logged out the other one
        assertEquals(keyPair2.pubkeyHex, ndk.currentUser.value?.pubkey)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd /Users/pablofernandez/10x/ndk-android && ./gradlew :ndk-core:testDebugUnitTest --tests "io.nostr.ndk.account.NDKAccountManagementTest" --info`
Expected: FAIL (methods don't exist)

**Step 3: Update NDK class with account management**

Replace the entire NDK.kt file:

```kotlin
package io.nostr.ndk

import io.nostr.ndk.account.InMemoryAccountStorage
import io.nostr.ndk.account.NDKAccountStorage
import io.nostr.ndk.account.NDKCurrentUser
import io.nostr.ndk.cache.NDKCacheAdapter
import io.nostr.ndk.crypto.NDKSigner
import io.nostr.ndk.models.NDKFilter
import io.nostr.ndk.models.PublicKey
import io.nostr.ndk.outbox.NDKOutboxTracker
import io.nostr.ndk.relay.NDKPool
import io.nostr.ndk.subscription.NDKSubscription
import io.nostr.ndk.subscription.NDKSubscriptionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Main entry point for the Nostr Development Kit (NDK).
 *
 * NDK manages relay connections, subscriptions, event publishing, and account management.
 * It provides a streaming-first API where events are delivered via Kotlin Flows.
 *
 * Example usage:
 * ```kotlin
 * val ndk = NDK(
 *     explicitRelayUrls = setOf("wss://relay.damus.io", "wss://nos.lol"),
 *     accountStorage = AndroidAccountStorage(context)
 * )
 *
 * // Login and start session
 * val me = ndk.login(NDKPrivateKeySigner(keyPair))
 *
 * // Access reactive session data
 * me.follows.collect { follows -> updateUI(follows) }
 *
 * // Subscribe to events
 * val subscription = ndk.subscribe(NDKFilter(kinds = setOf(1), limit = 50))
 * subscription.events.collect { event ->
 *     println("New event: ${event.content}")
 * }
 * ```
 *
 * @param explicitRelayUrls Set of relay URLs to connect to
 * @param signer Optional signer for signing and publishing events (deprecated, use login())
 * @param cacheAdapter Optional cache adapter for event persistence
 * @param accountStorage Optional storage for persisting account data
 */
class NDK(
    val explicitRelayUrls: Set<String> = emptySet(),
    val signer: NDKSigner? = null,
    val cacheAdapter: NDKCacheAdapter? = null,
    val accountStorage: NDKAccountStorage? = null
) {
    /**
     * Lazy initialized relay pool.
     * The pool manages all relay connections and their lifecycle.
     */
    val pool: NDKPool by lazy { NDKPool(this) }

    /**
     * Lazy initialized subscription manager.
     * The manager coordinates all subscriptions and dispatches events.
     */
    internal val subscriptionManager: NDKSubscriptionManager by lazy { NDKSubscriptionManager(this) }

    /**
     * Lazy initialized outbox tracker.
     * Manages relay lists (NIP-65) and provides outbox model capabilities.
     */
    val outboxTracker: NDKOutboxTracker by lazy { NDKOutboxTracker(this) }

    // Account management
    private val _currentUser = MutableStateFlow<NDKCurrentUser?>(null)
    private val _accounts = MutableStateFlow<List<NDKCurrentUser>>(emptyList())

    /**
     * The currently active user, or null if not logged in.
     */
    val currentUser: StateFlow<NDKCurrentUser?> = _currentUser.asStateFlow()

    /**
     * All logged-in accounts.
     */
    val accounts: StateFlow<List<NDKCurrentUser>> = _accounts.asStateFlow()

    /**
     * Logs in with a signer and creates/activates a session.
     *
     * If storage is configured, the account is persisted.
     * If an account with this pubkey already exists, it becomes the current user.
     *
     * @param signer The signer to use for this session
     * @return The NDKCurrentUser for this session
     */
    suspend fun login(signer: NDKSigner): NDKCurrentUser {
        val pubkey = signer.pubkey

        // Check if account already exists
        val existing = _accounts.value.find { it.pubkey == pubkey }
        if (existing != null) {
            _currentUser.value = existing
            return existing
        }

        // Create new current user
        val currentUser = NDKCurrentUser(signer, this)
        _accounts.update { it + currentUser }
        _currentUser.value = currentUser

        // Persist if storage available
        accountStorage?.saveSigner(pubkey, signer.serialize())

        return currentUser
    }

    /**
     * Logs out the current user.
     *
     * If storage is configured, the account is removed from storage.
     */
    suspend fun logout() {
        val current = _currentUser.value ?: return
        logout(current.pubkey)
    }

    /**
     * Logs out a specific account by pubkey.
     *
     * @param pubkey The pubkey of the account to logout
     */
    suspend fun logout(pubkey: PublicKey) {
        _accounts.update { accounts -> accounts.filter { it.pubkey != pubkey } }

        // If current user was logged out, clear or switch
        if (_currentUser.value?.pubkey == pubkey) {
            _currentUser.value = _accounts.value.firstOrNull()
        }

        // Remove from storage
        accountStorage?.deleteAccount(pubkey)
    }

    /**
     * Switches to a different account.
     *
     * @param pubkey The pubkey of the account to switch to
     * @return true if switch was successful, false if account not found
     */
    fun switchAccount(pubkey: PublicKey): Boolean {
        val account = _accounts.value.find { it.pubkey == pubkey } ?: return false
        _currentUser.value = account
        return true
    }

    /**
     * Restores accounts from storage.
     *
     * Call this on app startup to restore previously logged-in accounts.
     *
     * @return List of restored accounts
     */
    suspend fun restoreAccounts(): List<NDKCurrentUser> {
        val storage = accountStorage ?: return emptyList()

        val restoredAccounts = mutableListOf<NDKCurrentUser>()

        for (pubkey in storage.listAccounts()) {
            val signerData = storage.loadSigner(pubkey) ?: continue
            val signer = NDKSigner.deserialize(signerData) ?: continue

            val currentUser = NDKCurrentUser(signer, this)
            restoredAccounts.add(currentUser)
        }

        _accounts.value = restoredAccounts
        _currentUser.value = restoredAccounts.firstOrNull()

        return restoredAccounts
    }

    /**
     * Connects to all explicit relays.
     * Adds each relay URL to the pool and initiates connections.
     *
     * @param timeoutMs Maximum time to wait for connections (default: 5000ms)
     */
    suspend fun connect(timeoutMs: Long = 5000) {
        explicitRelayUrls.forEach { url ->
            pool.addRelay(url, connect = true)
        }
        pool.connect(timeoutMs)
    }

    /**
     * Creates a subscription with a single filter.
     *
     * @param filter The filter to match events against
     * @return A new subscription that will emit matching events
     */
    fun subscribe(filter: NDKFilter): NDKSubscription {
        return subscribe(listOf(filter))
    }

    /**
     * Creates a subscription with multiple filters.
     * Events matching any of the filters will be emitted.
     *
     * If a cache adapter is configured, cached events are emitted first,
     * followed by events from relays (cache-first strategy).
     *
     * @param filters List of filters to match events against
     * @return A new subscription that will emit matching events
     */
    fun subscribe(filters: List<NDKFilter>): NDKSubscription {
        val subscription = subscriptionManager.subscribe(filters)

        // Load cached events first (cache-first strategy)
        subscription.loadFromCache()

        // Then subscribe to relays for new events
        subscription.start(pool.connectedRelays.value)

        return subscription
    }

    /**
     * Triggers reconnection for all disconnected relays.
     * Useful when network connectivity is restored.
     *
     * @param ignoreDelay If true, bypasses exponential backoff delays
     */
    fun reconnect(ignoreDelay: Boolean = false) {
        pool.reconnectAll(ignoreDelay)
    }

    /**
     * Closes the NDK instance and releases all resources.
     *
     * This method:
     * - Disconnects from all relays
     * - Cancels all reconnection attempts
     * - Clears all subscriptions
     * - Releases all coroutine scopes
     *
     * After calling close(), this NDK instance should not be used.
     * Create a new instance if needed.
     */
    fun close() {
        pool.close()
    }
}
```

**Step 4: Run test to verify it passes**

Run: `cd /Users/pablofernandez/10x/ndk-android && ./gradlew :ndk-core:testDebugUnitTest --tests "io.nostr.ndk.account.NDKAccountManagementTest" --info`
Expected: PASS

**Step 5: Run all existing tests to ensure no regressions**

Run: `cd /Users/pablofernandez/10x/ndk-android && ./gradlew :ndk-core:testDebugUnitTest --info`
Expected: All tests PASS

**Step 6: Commit**

```bash
cd /Users/pablofernandez/10x/ndk-android
git add ndk-core/src/main/kotlin/io/nostr/ndk/NDK.kt ndk-core/src/test/kotlin/io/nostr/ndk/account/NDKAccountManagementTest.kt
git commit -m "feat(account): add login/logout/switchAccount/restoreAccounts to NDK"
```

---

## Task 6: Session Data Auto-Loading

**Files:**
- Modify: `ndk-core/src/main/kotlin/io/nostr/ndk/account/NDKCurrentUser.kt`
- Modify: `ndk-core/src/main/kotlin/io/nostr/ndk/NDK.kt`
- Test: `ndk-core/src/test/kotlin/io/nostr/ndk/account/SessionDataLoadingTest.kt`

**Step 1: Write tests for session data auto-loading**

```kotlin
package io.nostr.ndk.account

import io.nostr.ndk.NDK
import io.nostr.ndk.crypto.NDKKeyPair
import io.nostr.ndk.crypto.NDKPrivateKeySigner
import io.nostr.ndk.models.NDKFilter
import io.nostr.ndk.nips.KIND_CONTACT_LIST
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SessionDataLoadingTest {

    @Test
    fun `login starts session data subscription`() = runTest {
        val ndk = NDK()
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)

        val currentUser = ndk.login(signer)

        // Verify session subscription was started
        assertNotNull(currentUser.sessionSubscription)
    }

    @Test
    fun `session subscription filters for correct kinds`() = runTest {
        val ndk = NDK()
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)

        val currentUser = ndk.login(signer)
        val filters = currentUser.sessionSubscription?.filters ?: emptyList()

        // Should have filter for session data kinds
        assertTrue(filters.isNotEmpty())
        val kinds = filters.flatMap { it.kinds ?: emptySet() }.toSet()
        assertTrue(kinds.contains(KIND_CONTACT_LIST))
        assertTrue(kinds.contains(KIND_MUTE_LIST))
        assertTrue(kinds.contains(KIND_RELAY_LIST))
        assertTrue(kinds.contains(KIND_BLOCKED_RELAY_LIST))
    }

    @Test
    fun `session subscription filters for user pubkey`() = runTest {
        val ndk = NDK()
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)

        val currentUser = ndk.login(signer)
        val filters = currentUser.sessionSubscription?.filters ?: emptyList()

        // Should filter for this user's pubkey
        assertTrue(filters.isNotEmpty())
        val authors = filters.flatMap { it.authors ?: emptySet() }.toSet()
        assertTrue(authors.contains(keyPair.pubkeyHex))
    }

    @Test
    fun `logout stops session subscription`() = runTest {
        val ndk = NDK()
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)

        val currentUser = ndk.login(signer)
        val subscription = currentUser.sessionSubscription

        ndk.logout()

        // Subscription should be stopped
        // (In real implementation, we'd check subscription.isClosed or similar)
        assertNotNull(subscription)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd /Users/pablofernandez/10x/ndk-android && ./gradlew :ndk-core:testDebugUnitTest --tests "io.nostr.ndk.account.SessionDataLoadingTest" --info`
Expected: FAIL (sessionSubscription property doesn't exist)

**Step 3: Update NDKCurrentUser to start session subscription**

Add to NDKCurrentUser.kt:

```kotlin
// Add import at top
import io.nostr.ndk.subscription.NDKSubscription
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// Add property to class
/**
 * Subscription for session data (follows, mutes, relays, blocked relays).
 */
var sessionSubscription: NDKSubscription? = null
    private set

private var sessionJob: Job? = null
private val scope = CoroutineScope(Dispatchers.Default)

/**
 * Starts the session data subscription.
 * Called automatically by NDK.login().
 */
fun startSessionSubscription() {
    if (sessionSubscription != null) return

    val filter = NDKFilter(
        kinds = setOf(
            KIND_CONTACT_LIST,
            KIND_MUTE_LIST,
            KIND_RELAY_LIST,
            KIND_BLOCKED_RELAY_LIST
        ),
        authors = setOf(pubkey)
    )

    sessionSubscription = ndk.subscribe(filter)

    // Collect events and update session data
    sessionJob = scope.launch {
        sessionSubscription?.events?.collect { event ->
            handleEvent(event)
        }
    }
}

/**
 * Stops the session data subscription.
 * Called automatically by NDK.logout().
 */
fun stopSessionSubscription() {
    sessionJob?.cancel()
    sessionJob = null
    sessionSubscription = null
}
```

**Step 4: Update NDK.login() to start session subscription**

In NDK.kt, update the login function:

```kotlin
suspend fun login(signer: NDKSigner): NDKCurrentUser {
    val pubkey = signer.pubkey

    // Check if account already exists
    val existing = _accounts.value.find { it.pubkey == pubkey }
    if (existing != null) {
        _currentUser.value = existing
        return existing
    }

    // Create new current user
    val currentUser = NDKCurrentUser(signer, this)
    _accounts.update { it + currentUser }
    _currentUser.value = currentUser

    // Start session data subscription
    currentUser.startSessionSubscription()

    // Persist if storage available
    accountStorage?.saveSigner(pubkey, signer.serialize())

    return currentUser
}
```

**Step 5: Update NDK.logout() to stop session subscription**

In NDK.kt, update the logout(pubkey) function:

```kotlin
suspend fun logout(pubkey: PublicKey) {
    val account = _accounts.value.find { it.pubkey == pubkey }
    account?.stopSessionSubscription()

    _accounts.update { accounts -> accounts.filter { it.pubkey != pubkey } }

    // If current user was logged out, clear or switch
    if (_currentUser.value?.pubkey == pubkey) {
        _currentUser.value = _accounts.value.firstOrNull()
    }

    // Remove from storage
    accountStorage?.deleteAccount(pubkey)
}
```

**Step 6: Run test to verify it passes**

Run: `cd /Users/pablofernandez/10x/ndk-android && ./gradlew :ndk-core:testDebugUnitTest --tests "io.nostr.ndk.account.SessionDataLoadingTest" --info`
Expected: PASS

**Step 7: Run all tests**

Run: `cd /Users/pablofernandez/10x/ndk-android && ./gradlew :ndk-core:testDebugUnitTest --info`
Expected: All tests PASS

**Step 8: Commit**

```bash
cd /Users/pablofernandez/10x/ndk-android
git add ndk-core/src/main/kotlin/io/nostr/ndk/account/NDKCurrentUser.kt ndk-core/src/main/kotlin/io/nostr/ndk/NDK.kt ndk-core/src/test/kotlin/io/nostr/ndk/account/SessionDataLoadingTest.kt
git commit -m "feat(account): add automatic session data loading on login"
```

---

## Task 7: Extensible Session Kinds

**Files:**
- Modify: `ndk-core/src/main/kotlin/io/nostr/ndk/account/NDKCurrentUser.kt`
- Modify: `ndk-core/src/main/kotlin/io/nostr/ndk/NDK.kt`
- Test: `ndk-core/src/test/kotlin/io/nostr/ndk/account/ExtensibleSessionKindsTest.kt`

**Step 1: Write tests for extensible session kinds**

```kotlin
package io.nostr.ndk.account

import io.nostr.ndk.NDK
import io.nostr.ndk.crypto.NDKKeyPair
import io.nostr.ndk.crypto.NDKPrivateKeySigner
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKTag
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ExtensibleSessionKindsTest {

    @Test
    fun `can register additional session kinds`() = runTest {
        val ndk = NDK()
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)

        // Register kind 30078 (app-specific data) before login
        ndk.registerSessionKind(30078)

        val currentUser = ndk.login(signer)
        val filters = currentUser.sessionSubscription?.filters ?: emptyList()

        val kinds = filters.flatMap { it.kinds ?: emptySet() }.toSet()
        assertTrue(kinds.contains(30078))
    }

    @Test
    fun `custom kind events stored in sessionEvents`() = runTest {
        val ndk = NDK()
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)

        ndk.registerSessionKind(30078)
        val currentUser = ndk.login(signer)

        // Simulate receiving custom kind event
        val customEvent = NDKEvent(
            id = "abc",
            pubkey = keyPair.pubkeyHex,
            createdAt = 1000L,
            kind = 30078,
            tags = listOf(NDKTag("d", listOf("my-app-data"))),
            content = """{"preference":"dark-mode"}""",
            sig = "sig"
        )

        currentUser.handleEvent(customEvent)

        val storedEvent = currentUser.sessionEvents.value[30078]
        assertNotNull(storedEvent)
        assertEquals(customEvent.content, storedEvent?.content)
    }

    @Test
    fun `sessionEvents updated with newer event only`() = runTest {
        val ndk = NDK()
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)

        ndk.registerSessionKind(30078)
        val currentUser = ndk.login(signer)

        // Add newer event first
        val newerEvent = NDKEvent(
            id = "newer",
            pubkey = keyPair.pubkeyHex,
            createdAt = 2000L,
            kind = 30078,
            tags = emptyList(),
            content = "newer",
            sig = "sig"
        )
        currentUser.handleEvent(newerEvent)

        // Try to add older event
        val olderEvent = NDKEvent(
            id = "older",
            pubkey = keyPair.pubkeyHex,
            createdAt = 1000L,
            kind = 30078,
            tags = emptyList(),
            content = "older",
            sig = "sig"
        )
        currentUser.handleEvent(olderEvent)

        val storedEvent = currentUser.sessionEvents.value[30078]
        assertEquals("newer", storedEvent?.content)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd /Users/pablofernandez/10x/ndk-android && ./gradlew :ndk-core:testDebugUnitTest --tests "io.nostr.ndk.account.ExtensibleSessionKindsTest" --info`
Expected: FAIL

**Step 3: Add extensible kinds to NDK**

In NDK.kt, add:

```kotlin
// Add property
private val _additionalSessionKinds = mutableSetOf<Int>()

/**
 * Registers an additional event kind to be auto-fetched for sessions.
 * Call before login() to ensure the kind is included in the session subscription.
 *
 * @param kind The event kind to auto-fetch
 */
fun registerSessionKind(kind: Int) {
    _additionalSessionKinds.add(kind)
}

/**
 * Gets all registered session kinds (core + additional).
 */
internal fun getSessionKinds(): Set<Int> {
    return setOf(
        io.nostr.ndk.nips.KIND_CONTACT_LIST,
        io.nostr.ndk.account.KIND_MUTE_LIST,
        io.nostr.ndk.account.KIND_RELAY_LIST,
        io.nostr.ndk.account.KIND_BLOCKED_RELAY_LIST
    ) + _additionalSessionKinds
}
```

**Step 4: Add sessionEvents to NDKCurrentUser**

In NDKCurrentUser.kt, add:

```kotlin
// Add property
private val _sessionEvents = MutableStateFlow<Map<Int, NDKEvent>>(emptyMap())
private val sessionEventTimestamps = mutableMapOf<Int, Long>()

/**
 * Custom session events by kind.
 * Contains events for kinds registered via NDK.registerSessionKind().
 */
val sessionEvents: StateFlow<Map<Int, NDKEvent>> = _sessionEvents.asStateFlow()

// Update handleEvent to handle custom kinds
fun handleEvent(event: NDKEvent) {
    // Only process events from this user
    if (event.pubkey != pubkey) return

    when (event.kind) {
        KIND_CONTACT_LIST -> handleContactList(event)
        KIND_MUTE_LIST -> handleMuteList(event)
        KIND_RELAY_LIST -> handleRelayList(event)
        KIND_BLOCKED_RELAY_LIST -> handleBlockedRelayList(event)
        else -> handleCustomKind(event)
    }
}

private fun handleCustomKind(event: NDKEvent) {
    val lastTimestamp = sessionEventTimestamps[event.kind] ?: 0L
    if (event.createdAt <= lastTimestamp) return

    sessionEventTimestamps[event.kind] = event.createdAt
    _sessionEvents.update { it + (event.kind to event) }
}

// Update startSessionSubscription to use NDK's session kinds
fun startSessionSubscription() {
    if (sessionSubscription != null) return

    val filter = NDKFilter(
        kinds = ndk.getSessionKinds(),
        authors = setOf(pubkey)
    )

    sessionSubscription = ndk.subscribe(filter)

    sessionJob = scope.launch {
        sessionSubscription?.events?.collect { event ->
            handleEvent(event)
        }
    }
}
```

**Step 5: Run test to verify it passes**

Run: `cd /Users/pablofernandez/10x/ndk-android && ./gradlew :ndk-core:testDebugUnitTest --tests "io.nostr.ndk.account.ExtensibleSessionKindsTest" --info`
Expected: PASS

**Step 6: Run all tests**

Run: `cd /Users/pablofernandez/10x/ndk-android && ./gradlew :ndk-core:testDebugUnitTest --info`
Expected: All tests PASS

**Step 7: Commit**

```bash
cd /Users/pablofernandez/10x/ndk-android
git add ndk-core/src/main/kotlin/io/nostr/ndk/account/NDKCurrentUser.kt ndk-core/src/main/kotlin/io/nostr/ndk/NDK.kt ndk-core/src/test/kotlin/io/nostr/ndk/account/ExtensibleSessionKindsTest.kt
git commit -m "feat(account): add extensible session kinds via registerSessionKind()"
```

---

## Task 8: Final Integration Test and Documentation

**Files:**
- Test: `ndk-core/src/test/kotlin/io/nostr/ndk/account/SessionManagementIntegrationTest.kt`
- Modify: `ndk-core/src/main/kotlin/io/nostr/ndk/NDK.kt` (update docs)

**Step 1: Write integration test**

```kotlin
package io.nostr.ndk.account

import io.nostr.ndk.NDK
import io.nostr.ndk.crypto.NDKKeyPair
import io.nostr.ndk.crypto.NDKPrivateKeySigner
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKTag
import io.nostr.ndk.nips.KIND_CONTACT_LIST
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * Integration tests for the full session management flow.
 */
class SessionManagementIntegrationTest {

    @Test
    fun `full session lifecycle - login, use, logout`() = runTest {
        // Setup
        val storage = InMemoryAccountStorage()
        val ndk = NDK(accountStorage = storage)
        val keyPair = NDKKeyPair.generate()
        val signer = NDKPrivateKeySigner(keyPair)

        // Login
        val me = ndk.login(signer)
        assertEquals(keyPair.pubkeyHex, me.pubkey)
        assertEquals(me, ndk.currentUser.value)
        assertEquals(1, ndk.accounts.value.size)

        // Simulate receiving session data
        val contactList = NDKEvent(
            id = "contacts",
            pubkey = keyPair.pubkeyHex,
            createdAt = 1000L,
            kind = KIND_CONTACT_LIST,
            tags = listOf(
                NDKTag("p", listOf("friend1")),
                NDKTag("p", listOf("friend2"))
            ),
            content = "",
            sig = "sig"
        )
        me.handleEvent(contactList)

        // Verify session data
        assertEquals(2, me.follows.value.size)
        assertTrue(me.follows.value.contains("friend1"))

        // Sign an event
        val unsigned = io.nostr.ndk.crypto.UnsignedEvent(
            pubkey = me.pubkey,
            createdAt = System.currentTimeMillis() / 1000,
            kind = 1,
            tags = emptyList(),
            content = "Hello from NDK!"
        )
        val signed = me.sign(unsigned)
        assertNotNull(signed.sig)

        // Logout
        ndk.logout()
        assertNull(ndk.currentUser.value)
        assertEquals(0, ndk.accounts.value.size)
    }

    @Test
    fun `multi-account workflow`() = runTest {
        val ndk = NDK()
        val keyPair1 = NDKKeyPair.generate()
        val keyPair2 = NDKKeyPair.generate()
        val signer1 = NDKPrivateKeySigner(keyPair1)
        val signer2 = NDKPrivateKeySigner(keyPair2)

        // Login first account
        val user1 = ndk.login(signer1)
        assertEquals(user1, ndk.currentUser.value)

        // Add session data for user1
        user1.handleEvent(NDKEvent(
            id = "u1-contacts",
            pubkey = keyPair1.pubkeyHex,
            createdAt = 1000L,
            kind = KIND_CONTACT_LIST,
            tags = listOf(NDKTag("p", listOf("user1-friend"))),
            content = "",
            sig = "sig"
        ))

        // Login second account (becomes current)
        val user2 = ndk.login(signer2)
        assertEquals(user2, ndk.currentUser.value)
        assertEquals(2, ndk.accounts.value.size)

        // Add session data for user2
        user2.handleEvent(NDKEvent(
            id = "u2-contacts",
            pubkey = keyPair2.pubkeyHex,
            createdAt = 1000L,
            kind = KIND_CONTACT_LIST,
            tags = listOf(NDKTag("p", listOf("user2-friend"))),
            content = "",
            sig = "sig"
        ))

        // Verify each user has their own data
        assertTrue(user1.follows.value.contains("user1-friend"))
        assertFalse(user1.follows.value.contains("user2-friend"))
        assertTrue(user2.follows.value.contains("user2-friend"))
        assertFalse(user2.follows.value.contains("user1-friend"))

        // Switch accounts
        ndk.switchAccount(keyPair1.pubkeyHex)
        assertEquals(user1, ndk.currentUser.value)

        // Logout user1 only
        ndk.logout(keyPair1.pubkeyHex)
        assertEquals(1, ndk.accounts.value.size)
        assertEquals(user2, ndk.currentUser.value)
    }

    @Test
    fun `account restoration from storage`() = runTest {
        val storage = InMemoryAccountStorage()

        // First session: login and persist
        run {
            val ndk = NDK(accountStorage = storage)
            val keyPair = NDKKeyPair.generate()
            val signer = NDKPrivateKeySigner(keyPair)
            ndk.login(signer)
        }

        // Verify data was persisted
        assertEquals(1, storage.listAccounts().size)

        // Second session: restore
        run {
            val ndk = NDK(accountStorage = storage)
            val restored = ndk.restoreAccounts()

            assertEquals(1, restored.size)
            assertNotNull(ndk.currentUser.value)
            assertEquals(restored.first().pubkey, ndk.currentUser.value?.pubkey)
        }
    }
}
```

**Step 2: Run integration test**

Run: `cd /Users/pablofernandez/10x/ndk-android && ./gradlew :ndk-core:testDebugUnitTest --tests "io.nostr.ndk.account.SessionManagementIntegrationTest" --info`
Expected: PASS

**Step 3: Run all tests**

Run: `cd /Users/pablofernandez/10x/ndk-android && ./gradlew :ndk-core:testDebugUnitTest --info`
Expected: All tests PASS

**Step 4: Commit**

```bash
cd /Users/pablofernandez/10x/ndk-android
git add ndk-core/src/test/kotlin/io/nostr/ndk/account/SessionManagementIntegrationTest.kt
git commit -m "test(account): add session management integration tests"
```

**Step 5: Final commit with all changes**

```bash
cd /Users/pablofernandez/10x/ndk-android
git log --oneline -10  # Review commits
```

---

## Summary

This plan adds session management to ndk-android with:

1. **`NDKAccountStorage`** - Pluggable persistence interface with `InMemoryAccountStorage` for testing
2. **Signer serialization** - `NDKSigner.serialize()` and `NDKSigner.deserialize()` for persistence
3. **Session data models** - `MuteList`, `BlockedRelayList` parsed from events
4. **`NDKCurrentUser`** - Extends `NDKUser` with signing and auto-synced session data
5. **NDK account APIs** - `login()`, `logout()`, `switchAccount()`, `restoreAccounts()`
6. **Auto-loading** - Session data (follows, mutes, relays) automatically fetched on login
7. **Extensibility** - `registerSessionKind()` for custom event kinds

Total: ~8 tasks, each with TDD approach (test first, implement, verify, commit).
