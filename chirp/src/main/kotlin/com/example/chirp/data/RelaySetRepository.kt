package com.example.chirp.data

import io.nostr.ndk.NDK
import io.nostr.ndk.crypto.UnsignedEvent
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKFilter
import io.nostr.ndk.models.NDKTag
import io.nostr.ndk.models.PublicKey
import io.nostr.ndk.nips.KIND_RELAY_SET
import io.nostr.ndk.nips.RelaySet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing relay sets (NIP-51 kind 30002).
 *
 * Provides methods to fetch, publish, and delete relay sets.
 * Relay sets are collections of relay URLs that can be used to filter
 * content browsing to specific relay sources.
 */
@Singleton
class RelaySetRepository @Inject constructor(
    private val ndk: NDK
) {
    /**
     * Fetches the user's own relay sets.
     *
     * @param pubkey The public key of the user
     * @return Flow of relay sets authored by the user
     */
    fun fetchOwnRelaySets(pubkey: PublicKey): Flow<List<RelaySet>> {
        val filter = NDKFilter(
            authors = setOf(pubkey),
            kinds = setOf(KIND_RELAY_SET)
        )

        return ndk.subscribe(filter).events.map { event ->
            // Convert each event to a RelaySet
            listOfNotNull(RelaySet.fromEvent(event))
        }
    }

    /**
     * Fetches relay sets from a list of users (e.g., follows).
     *
     * @param follows List of public keys to fetch relay sets from
     * @return Flow of relay sets from the specified users
     */
    fun fetchRelaySetsFromFollows(follows: List<PublicKey>): Flow<List<RelaySet>> {
        if (follows.isEmpty()) {
            return kotlinx.coroutines.flow.flowOf(emptyList())
        }

        val filter = NDKFilter(
            authors = follows.toSet(),
            kinds = setOf(KIND_RELAY_SET)
        )

        return ndk.subscribe(filter).events.map { event ->
            // Convert each event to a RelaySet
            listOfNotNull(RelaySet.fromEvent(event))
        }
    }

    /**
     * Publishes a new or updated relay set.
     *
     * @param identifier Unique identifier for this relay set (d tag)
     * @param title Optional display name for the relay set
     * @param description Optional description
     * @param image Optional image URL
     * @param relays List of relay URLs in this set
     * @return Result containing the published event or error
     */
    suspend fun publishRelaySet(
        identifier: String,
        title: String?,
        description: String?,
        image: String?,
        relays: List<String>
    ): Result<NDKEvent> {
        return try {
            val currentUser = ndk.currentUser.value
                ?: return Result.failure(Exception("No active user"))

            val tags = buildList {
                add(NDKTag("d", listOf(identifier)))
                title?.let { add(NDKTag("title", listOf(it))) }
                description?.let { add(NDKTag("description", listOf(it))) }
                image?.let { add(NDKTag("image", listOf(it))) }
                relays.forEach { add(NDKTag("relay", listOf(it))) }
            }

            val unsigned = UnsignedEvent(
                pubkey = currentUser.pubkey,
                createdAt = System.currentTimeMillis() / 1000,
                kind = KIND_RELAY_SET,
                tags = tags,
                content = ""
            )

            val event = currentUser.signer.sign(unsigned)
            ndk.publish(event)
            Result.success(event)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Deletes a relay set by publishing an event with empty content and no relay tags.
     *
     * @param identifier The identifier (d tag) of the relay set to delete
     * @return Result indicating success or failure
     */
    suspend fun deleteRelaySet(identifier: String): Result<Unit> {
        return try {
            val currentUser = ndk.currentUser.value
                ?: return Result.failure(Exception("No active user"))

            val tags = listOf(NDKTag("d", listOf(identifier)))

            val unsigned = UnsignedEvent(
                pubkey = currentUser.pubkey,
                createdAt = System.currentTimeMillis() / 1000,
                kind = KIND_RELAY_SET,
                tags = tags,
                content = ""
            )

            val event = currentUser.signer.sign(unsigned)
            ndk.publish(event)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
