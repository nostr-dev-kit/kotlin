package io.nostr.ndk.cache.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKTag

/**
 * Room entity for storing Nostr events.
 *
 * Indexes are optimized for common query patterns:
 * - kind: For filtering by event type
 * - pubkey: For user-specific queries (profiles, contacts)
 * - createdAt: For time-based ordering
 * - dedupKey: For replaceable event lookups
 */
@Entity(
    tableName = "events",
    indices = [
        Index(value = ["kind"]),
        Index(value = ["pubkey"]),
        Index(value = ["createdAt"]),
        Index(value = ["dedupKey"], unique = true)
    ]
)
data class EventEntity(
    @PrimaryKey
    val id: String,
    val pubkey: String,
    val createdAt: Long,
    val kind: Int,
    val content: String,
    val sig: String?,
    val tagsJson: String,
    val dedupKey: String
) {
    companion object {
        private val objectMapper = jacksonObjectMapper()

        fun fromNDKEvent(event: NDKEvent): EventEntity {
            val tagsArray = event.tags.map { listOf(it.name) + it.values }
            return EventEntity(
                id = event.id,
                pubkey = event.pubkey,
                createdAt = event.createdAt,
                kind = event.kind,
                content = event.content,
                sig = event.sig,
                tagsJson = objectMapper.writeValueAsString(tagsArray),
                dedupKey = event.deduplicationKey()
            )
        }
    }

    fun toNDKEvent(): NDKEvent {
        val tagsArray: List<List<String>> = objectMapper.readValue(tagsJson)
        val tags = tagsArray.map { tagArray ->
            if (tagArray.isEmpty()) {
                NDKTag("", emptyList())
            } else {
                NDKTag(tagArray[0], tagArray.drop(1))
            }
        }

        return NDKEvent(
            id = id,
            pubkey = pubkey,
            createdAt = createdAt,
            kind = kind,
            tags = tags,
            content = content,
            sig = sig
        )
    }
}
