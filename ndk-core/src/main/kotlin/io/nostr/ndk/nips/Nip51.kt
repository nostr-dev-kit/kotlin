package io.nostr.ndk.nips

import io.nostr.ndk.models.EventId
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.PublicKey

/**
 * NIP-51: Lists
 *
 * Lists are parameterized replaceable events that store collections of items.
 * Items can be pubkeys, events, relays, hashtags, etc.
 *
 * Common list kinds:
 * - 10000: Mute list
 * - 10001: Pin list
 * - 30000: Categorized people list
 * - 30001: Categorized bookmark list
 * - 30002: Relay sets
 */

/**
 * Kind constants for list events.
 */
const val KIND_MUTE_LIST = 10000
const val KIND_PIN_LIST = 10001
const val KIND_PEOPLE_LIST = 30000
const val KIND_BOOKMARK_LIST = 30001
const val KIND_RELAY_SET = 30002

/**
 * Represents an item in a list.
 */
sealed class ListItem {
    data class Pubkey(val pubkey: PublicKey, val relay: String?, val petname: String?) : ListItem()
    data class Event(val eventId: EventId, val relay: String?) : ListItem()
    data class Hashtag(val tag: String) : ListItem()
    data class Relay(val url: String) : ListItem()
    data class Word(val word: String) : ListItem()
    data class Url(val url: String) : ListItem()
}

/**
 * Returns true if this event is a mute list (kind 10000).
 */
val NDKEvent.isMuteList: Boolean
    get() = kind == KIND_MUTE_LIST

/**
 * Returns true if this event is a pin list (kind 10001).
 */
val NDKEvent.isPinList: Boolean
    get() = kind == KIND_PIN_LIST

/**
 * Returns true if this event is a categorized people list (kind 30000).
 */
val NDKEvent.isPeopleList: Boolean
    get() = kind == KIND_PEOPLE_LIST

/**
 * Returns true if this event is a categorized bookmark list (kind 30001).
 */
val NDKEvent.isBookmarkList: Boolean
    get() = kind == KIND_BOOKMARK_LIST

/**
 * Returns true if this event is a relay set (kind 30002).
 */
val NDKEvent.isRelaySet: Boolean
    get() = kind == KIND_RELAY_SET

/**
 * Returns true if this event is any kind of list.
 */
val NDKEvent.isList: Boolean
    get() = isMuteList || isPinList || isPeopleList || isBookmarkList || isRelaySet

/**
 * Gets the list name/identifier from the d tag.
 * For replaceable lists (10000, 10001), this is always empty.
 * For parameterized replaceable lists (30000, 30001), this is the list name.
 */
val NDKEvent.listName: String?
    get() = tagValue("d")

/**
 * Gets the list title from the title tag (if present).
 */
val NDKEvent.listTitle: String?
    get() = tagValue("title")

/**
 * Gets the list description from the description tag (if present).
 */
val NDKEvent.listDescription: String?
    get() = tagValue("description")

/**
 * Gets the list image URL from the image tag (if present).
 */
val NDKEvent.listImage: String?
    get() = tagValue("image")

/**
 * Gets all items from a list event.
 */
val NDKEvent.listItems: List<ListItem>
    get() {
        if (!isList) return emptyList()

        val items = mutableListOf<ListItem>()

        // Parse p tags (pubkeys)
        tagsWithName("p").forEach { tag ->
            val pubkey = tag.values.getOrNull(0) ?: return@forEach
            items.add(
                ListItem.Pubkey(
                    pubkey = pubkey,
                    relay = tag.values.getOrNull(1)?.takeIf { it.isNotBlank() },
                    petname = tag.values.getOrNull(2)?.takeIf { it.isNotBlank() }
                )
            )
        }

        // Parse e tags (events)
        tagsWithName("e").forEach { tag ->
            val eventId = tag.values.getOrNull(0) ?: return@forEach
            items.add(
                ListItem.Event(
                    eventId = eventId,
                    relay = tag.values.getOrNull(1)?.takeIf { it.isNotBlank() }
                )
            )
        }

        // Parse t tags (hashtags)
        tagsWithName("t").forEach { tag ->
            val hashtag = tag.values.getOrNull(0) ?: return@forEach
            items.add(ListItem.Hashtag(hashtag))
        }

        // Parse relay tags
        tagsWithName("relay").forEach { tag ->
            val url = tag.values.getOrNull(0) ?: return@forEach
            items.add(ListItem.Relay(url))
        }

        // Parse word tags (for mute lists)
        tagsWithName("word").forEach { tag ->
            val word = tag.values.getOrNull(0) ?: return@forEach
            items.add(ListItem.Word(word))
        }

        // Parse r tags (URLs, used in bookmarks)
        tagsWithName("r").forEach { tag ->
            val url = tag.values.getOrNull(0) ?: return@forEach
            // r tags can be relay URLs or regular URLs
            // Check if it looks like a relay URL
            if (url.startsWith("wss://") || url.startsWith("ws://")) {
                items.add(ListItem.Relay(url))
            } else {
                items.add(ListItem.Url(url))
            }
        }

        return items
    }

/**
 * Gets only pubkeys from a list.
 */
val NDKEvent.listPubkeys: List<PublicKey>
    get() = listItems.filterIsInstance<ListItem.Pubkey>().map { it.pubkey }

/**
 * Gets only event IDs from a list.
 */
val NDKEvent.listEventIds: List<EventId>
    get() = listItems.filterIsInstance<ListItem.Event>().map { it.eventId }

/**
 * Checks if a pubkey is in this list.
 */
fun NDKEvent.listContainsPubkey(pubkey: PublicKey): Boolean {
    return listItems.any { it is ListItem.Pubkey && it.pubkey == pubkey }
}

/**
 * Checks if an event ID is in this list.
 */
fun NDKEvent.listContainsEvent(eventId: EventId): Boolean {
    return listItems.any { it is ListItem.Event && it.eventId == eventId }
}

/**
 * Gets relay URLs from a relay set (kind 30002).
 * Returns empty list if not a relay set.
 */
val NDKEvent.relaySetRelays: List<String>
    get() {
        if (!isRelaySet) return emptyList()
        return tagsWithName("relay").mapNotNull { it.values.getOrNull(0) }
    }
