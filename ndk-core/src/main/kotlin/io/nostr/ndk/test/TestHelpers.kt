package io.nostr.ndk.test

import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKFilter
import io.nostr.ndk.models.NDKTag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeout

/**
 * Test helpers and assertions for NDK testing.
 *
 * These utilities simplify common testing patterns when working with
 * NDK events, filters, and subscriptions.
 */

// ========== Event Assertions ==========

/**
 * Asserts that an event has a specific kind.
 */
fun NDKEvent.assertKind(expected: Int) {
    if (kind != expected) {
        throw AssertionError("Expected kind $expected but was $kind")
    }
}

/**
 * Asserts that an event has a specific tag.
 */
fun NDKEvent.assertHasTag(name: String, value: String? = null) {
    val matchingTags = tagsWithName(name)
    if (matchingTags.isEmpty()) {
        throw AssertionError("Expected tag '$name' but none found")
    }
    if (value != null && matchingTags.none { it.values.firstOrNull() == value }) {
        throw AssertionError("Expected tag '$name' with value '$value' but not found")
    }
}

/**
 * Asserts that an event does not have a specific tag.
 */
fun NDKEvent.assertNoTag(name: String) {
    val matchingTags = tagsWithName(name)
    if (matchingTags.isNotEmpty()) {
        throw AssertionError("Expected no tag '$name' but found ${matchingTags.size}")
    }
}

/**
 * Asserts that an event's content contains a string.
 */
fun NDKEvent.assertContentContains(substring: String) {
    if (!content.contains(substring)) {
        throw AssertionError("Expected content to contain '$substring' but was: $content")
    }
}

/**
 * Asserts that an event is a reply to another event.
 */
fun NDKEvent.assertIsReplyTo(eventId: String) {
    val replyTag = tagsWithName("e").find {
        it.values.getOrNull(2) == "reply" || it.values.firstOrNull() == eventId
    }
    if (replyTag == null || replyTag.values.firstOrNull() != eventId) {
        throw AssertionError("Expected event to be a reply to $eventId")
    }
}

/**
 * Asserts that an event references a pubkey.
 */
fun NDKEvent.assertReferencesPubkey(pubkey: String) {
    if (!referencedPubkeys().contains(pubkey)) {
        throw AssertionError("Expected event to reference pubkey $pubkey")
    }
}

// ========== Filter Assertions ==========

/**
 * Asserts that a filter matches an event.
 */
fun NDKFilter.assertMatches(event: NDKEvent) {
    if (!matches(event)) {
        throw AssertionError("Expected filter to match event ${event.id}")
    }
}

/**
 * Asserts that a filter does not match an event.
 */
fun NDKFilter.assertDoesNotMatch(event: NDKEvent) {
    if (matches(event)) {
        throw AssertionError("Expected filter NOT to match event ${event.id}")
    }
}

// ========== Flow Helpers ==========

/**
 * Collects the first N items from a flow with a timeout.
 */
suspend fun <T> Flow<T>.collectFirst(
    count: Int,
    timeoutMs: Long = 5000
): List<T> {
    return withTimeout(timeoutMs) {
        take(count).toList()
    }
}

/**
 * Collects the first item from a flow with a timeout.
 */
suspend fun <T> Flow<T>.awaitFirst(timeoutMs: Long = 5000): T {
    return withTimeout(timeoutMs) {
        first()
    }
}

// ========== Event Matchers ==========

/**
 * Finds events matching a predicate.
 */
fun List<NDKEvent>.findMatching(predicate: (NDKEvent) -> Boolean): List<NDKEvent> {
    return filter(predicate)
}

/**
 * Finds events by kind.
 */
fun List<NDKEvent>.findByKind(kind: Int): List<NDKEvent> {
    return filter { it.kind == kind }
}

/**
 * Finds events by author.
 */
fun List<NDKEvent>.findByAuthor(pubkey: String): List<NDKEvent> {
    return filter { it.pubkey == pubkey }
}

/**
 * Finds events that reference a specific event ID.
 */
fun List<NDKEvent>.findReferencingEvent(eventId: String): List<NDKEvent> {
    return filter { it.referencedEventIds().contains(eventId) }
}

// ========== Test Data Builders ==========

/**
 * Builds an NDKTag conveniently.
 */
fun tag(name: String, vararg values: String): NDKTag {
    return NDKTag(name, values.toList())
}

/**
 * Builds a filter with minimal boilerplate.
 */
fun filterOf(
    kinds: List<Int>? = null,
    authors: List<String>? = null,
    ids: List<String>? = null,
    since: Long? = null,
    until: Long? = null,
    limit: Int? = null,
    tags: Map<String, List<String>>? = null
): NDKFilter {
    return NDKFilter(
        kinds = kinds?.toSet(),
        authors = authors?.toSet(),
        ids = ids?.toSet(),
        since = since,
        until = until,
        limit = limit,
        tags = tags?.mapValues { it.value.toSet() } ?: emptyMap()
    )
}

// ========== Timing Utilities ==========

/**
 * Creates timestamps relative to now.
 */
object Timestamps {
    fun now(): Long = System.currentTimeMillis() / 1000
    fun minutesAgo(minutes: Int): Long = now() - (minutes * 60)
    fun hoursAgo(hours: Int): Long = now() - (hours * 3600)
    fun daysAgo(days: Int): Long = now() - (days * 86400)
    fun inFuture(seconds: Long): Long = now() + seconds
}

// ========== Verification Helpers ==========

/**
 * Verifies a condition with a custom message.
 */
fun verify(condition: Boolean, message: () -> String) {
    if (!condition) {
        throw AssertionError(message())
    }
}

/**
 * Verifies that a list is not empty.
 */
fun <T> List<T>.verifyNotEmpty(message: String = "Expected non-empty list") {
    verify(isNotEmpty()) { message }
}

/**
 * Verifies that a list has a specific size.
 */
fun <T> List<T>.verifySize(expected: Int, message: String = "Expected size $expected but was $size") {
    verify(size == expected) { message }
}
