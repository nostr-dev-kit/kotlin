package io.nostr.ndk.test

import io.nostr.ndk.models.NDKFilter
import org.junit.Assert.*
import org.junit.Test

class TestHelpersTest {

    // ========== Event Assertions Tests ==========

    @Test
    fun `assertKind passes for correct kind`() {
        val generator = EventGenerator()
        val note = generator.textNote("Test")

        note.assertKind(1) // Should not throw
    }

    @Test(expected = AssertionError::class)
    fun `assertKind fails for wrong kind`() {
        val generator = EventGenerator()
        val note = generator.textNote("Test")

        note.assertKind(7) // Should throw
    }

    @Test
    fun `assertHasTag passes when tag exists`() {
        val generator = EventGenerator()
        val note = generator.textNote("Test", tags = listOf(tag("t", "hashtag")))

        note.assertHasTag("t") // Should not throw
        note.assertHasTag("t", "hashtag") // Should not throw
    }

    @Test(expected = AssertionError::class)
    fun `assertHasTag fails when tag missing`() {
        val generator = EventGenerator()
        val note = generator.textNote("Test")

        note.assertHasTag("nonexistent")
    }

    @Test(expected = AssertionError::class)
    fun `assertHasTag fails when value wrong`() {
        val generator = EventGenerator()
        val note = generator.textNote("Test", tags = listOf(tag("t", "actual")))

        note.assertHasTag("t", "expected")
    }

    @Test
    fun `assertNoTag passes when tag absent`() {
        val generator = EventGenerator()
        val note = generator.textNote("Test")

        note.assertNoTag("nonexistent") // Should not throw
    }

    @Test(expected = AssertionError::class)
    fun `assertNoTag fails when tag exists`() {
        val generator = EventGenerator()
        val note = generator.textNote("Test", tags = listOf(tag("t", "value")))

        note.assertNoTag("t")
    }

    @Test
    fun `assertContentContains passes when substring present`() {
        val generator = EventGenerator()
        val note = generator.textNote("Hello world!")

        note.assertContentContains("world") // Should not throw
    }

    @Test(expected = AssertionError::class)
    fun `assertContentContains fails when substring absent`() {
        val generator = EventGenerator()
        val note = generator.textNote("Hello world!")

        note.assertContentContains("missing")
    }

    @Test
    fun `assertIsReplyTo passes for correct reply`() {
        val generator = EventGenerator()
        val root = generator.textNote("Root")
        val reply = generator.reply("Reply", root)

        reply.assertIsReplyTo(root.id) // Should not throw
    }

    @Test(expected = AssertionError::class)
    fun `assertIsReplyTo fails for wrong parent`() {
        val generator = EventGenerator()
        val root = generator.textNote("Root")
        val reply = generator.reply("Reply", root)

        reply.assertIsReplyTo("wrong-event-id")
    }

    @Test
    fun `assertReferencesPubkey passes when pubkey referenced`() {
        val generator = EventGenerator()
        val root = generator.textNote("Root")
        val reply = generator.reply("Reply", root)

        reply.assertReferencesPubkey(root.pubkey) // Should not throw
    }

    @Test(expected = AssertionError::class)
    fun `assertReferencesPubkey fails when pubkey not referenced`() {
        val generator = EventGenerator()
        val note = generator.textNote("Test")

        note.assertReferencesPubkey("nonexistent-pubkey")
    }

    // ========== Filter Assertions Tests ==========

    @Test
    fun `assertMatches passes when filter matches`() {
        val generator = EventGenerator()
        val note = generator.textNote("Test")
        val filter = NDKFilter(kinds = setOf(1))

        filter.assertMatches(note) // Should not throw
    }

    @Test(expected = AssertionError::class)
    fun `assertMatches fails when filter does not match`() {
        val generator = EventGenerator()
        val note = generator.textNote("Test")
        val filter = NDKFilter(kinds = setOf(7))

        filter.assertMatches(note)
    }

    @Test
    fun `assertDoesNotMatch passes when filter does not match`() {
        val generator = EventGenerator()
        val note = generator.textNote("Test")
        val filter = NDKFilter(kinds = setOf(7))

        filter.assertDoesNotMatch(note) // Should not throw
    }

    @Test(expected = AssertionError::class)
    fun `assertDoesNotMatch fails when filter matches`() {
        val generator = EventGenerator()
        val note = generator.textNote("Test")
        val filter = NDKFilter(kinds = setOf(1))

        filter.assertDoesNotMatch(note)
    }

    // ========== Event Matchers Tests ==========

    @Test
    fun `findByKind filters events by kind`() {
        val generator = EventGenerator()
        val events = listOf(
            generator.textNote("Note 1"),
            generator.profile("User1"),
            generator.textNote("Note 2")
        )

        val notes = events.findByKind(1)
        assertEquals(2, notes.size)
        assertTrue(notes.all { it.kind == 1 })
    }

    @Test
    fun `findByAuthor filters events by author`() {
        val generator = EventGenerator()
        val pubkey1 = EventGenerator.generateTestPubkey(1)
        val pubkey2 = EventGenerator.generateTestPubkey(2)
        val events = listOf(
            generator.textNote("Note 1", pubkey = pubkey1),
            generator.textNote("Note 2", pubkey = pubkey2),
            generator.textNote("Note 3", pubkey = pubkey1)
        )

        val fromPubkey1 = events.findByAuthor(pubkey1)
        assertEquals(2, fromPubkey1.size)
        assertTrue(fromPubkey1.all { it.pubkey == pubkey1 })
    }

    @Test
    fun `findReferencingEvent finds events with e tag`() {
        val generator = EventGenerator()
        val root = generator.textNote("Root")
        val reply1 = generator.reply("Reply 1", root)
        val reply2 = generator.reply("Reply 2", root)
        val other = generator.textNote("Other")

        val events = listOf(root, reply1, reply2, other)
        val references = events.findReferencingEvent(root.id)

        assertEquals(2, references.size)
        assertTrue(references.contains(reply1))
        assertTrue(references.contains(reply2))
    }

    // ========== Test Data Builders Tests ==========

    @Test
    fun `tag helper creates NDKTag`() {
        val t = tag("e", "eventid", "wss://relay", "root")

        assertEquals("e", t.name)
        assertEquals(listOf("eventid", "wss://relay", "root"), t.values)
    }

    @Test
    fun `filterOf creates NDKFilter`() {
        val filter = filterOf(
            kinds = listOf(1, 7),
            authors = listOf("pubkey1", "pubkey2"),
            since = 1000L,
            limit = 100
        )

        assertEquals(setOf(1, 7), filter.kinds)
        assertEquals(setOf("pubkey1", "pubkey2"), filter.authors)
        assertEquals(1000L, filter.since)
        assertEquals(100, filter.limit)
    }

    // ========== Timestamps Tests ==========

    @Test
    fun `Timestamps now returns current timestamp`() {
        val now = Timestamps.now()
        val expected = System.currentTimeMillis() / 1000

        assertTrue(kotlin.math.abs(now - expected) < 2) // Within 2 seconds
    }

    @Test
    fun `Timestamps minutesAgo returns past timestamp`() {
        val now = Timestamps.now()
        val fiveMinutesAgo = Timestamps.minutesAgo(5)

        assertTrue(kotlin.math.abs((now - 300) - fiveMinutesAgo) < 2)
    }

    @Test
    fun `Timestamps hoursAgo returns past timestamp`() {
        val now = Timestamps.now()
        val oneHourAgo = Timestamps.hoursAgo(1)

        assertTrue(kotlin.math.abs((now - 3600) - oneHourAgo) < 2)
    }

    @Test
    fun `Timestamps daysAgo returns past timestamp`() {
        val now = Timestamps.now()
        val oneDayAgo = Timestamps.daysAgo(1)

        assertTrue(kotlin.math.abs((now - 86400) - oneDayAgo) < 2)
    }

    @Test
    fun `Timestamps inFuture returns future timestamp`() {
        val now = Timestamps.now()
        val future = Timestamps.inFuture(100)

        assertTrue(kotlin.math.abs((now + 100) - future) < 2)
    }

    // ========== Verification Helpers Tests ==========

    @Test
    fun `verify passes for true condition`() {
        verify(true) { "Should not see this" }
    }

    @Test(expected = AssertionError::class)
    fun `verify fails for false condition`() {
        verify(false) { "Expected this error" }
    }

    @Test
    fun `verifyNotEmpty passes for non-empty list`() {
        listOf(1, 2, 3).verifyNotEmpty()
    }

    @Test(expected = AssertionError::class)
    fun `verifyNotEmpty fails for empty list`() {
        emptyList<Int>().verifyNotEmpty()
    }

    @Test
    fun `verifySize passes for correct size`() {
        listOf(1, 2, 3).verifySize(3)
    }

    @Test(expected = AssertionError::class)
    fun `verifySize fails for wrong size`() {
        listOf(1, 2, 3).verifySize(5)
    }
}
