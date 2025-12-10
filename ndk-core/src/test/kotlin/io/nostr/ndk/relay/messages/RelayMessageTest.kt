package io.nostr.ndk.relay.messages

import io.nostr.ndk.models.NDKEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class RelayMessageTest {

    @Test
    fun `parse EVENT message from relay`() {
        val json = """["EVENT","sub1",{"id":"abc123","pubkey":"def456","created_at":1234567890,"kind":1,"tags":[],"content":"Hello","sig":"sig123"}]"""

        val message = RelayMessage.parse(json)

        assertTrue(message is RelayMessage.Event)
        val eventMsg = message as RelayMessage.Event
        assertEquals("sub1", eventMsg.subscriptionId)
        assertEquals("abc123", eventMsg.event.id)
        assertEquals("def456", eventMsg.event.pubkey)
        assertEquals(1234567890L, eventMsg.event.createdAt)
        assertEquals(1, eventMsg.event.kind)
        assertEquals("Hello", eventMsg.event.content)
        assertEquals("sig123", eventMsg.event.sig)
    }

    @Test
    fun `parse EOSE message`() {
        val json = """["EOSE","sub1"]"""

        val message = RelayMessage.parse(json)

        assertTrue(message is RelayMessage.Eose)
        val eoseMsg = message as RelayMessage.Eose
        assertEquals("sub1", eoseMsg.subscriptionId)
    }

    @Test
    fun `parse OK message with success`() {
        val json = """["OK","event123",true,""]"""

        val message = RelayMessage.parse(json)

        assertTrue(message is RelayMessage.Ok)
        val okMsg = message as RelayMessage.Ok
        assertEquals("event123", okMsg.eventId)
        assertTrue(okMsg.success)
        assertEquals("", okMsg.message)
    }

    @Test
    fun `parse OK message with failure`() {
        val json = """["OK","event123",false,"duplicate: already have this event"]"""

        val message = RelayMessage.parse(json)

        assertTrue(message is RelayMessage.Ok)
        val okMsg = message as RelayMessage.Ok
        assertEquals("event123", okMsg.eventId)
        assertFalse(okMsg.success)
        assertEquals("duplicate: already have this event", okMsg.message)
    }

    @Test
    fun `parse NOTICE message`() {
        val json = """["NOTICE","Restricted: authentication required"]"""

        val message = RelayMessage.parse(json)

        assertTrue(message is RelayMessage.Notice)
        val noticeMsg = message as RelayMessage.Notice
        assertEquals("Restricted: authentication required", noticeMsg.message)
    }

    @Test
    fun `parse AUTH message`() {
        val json = """["AUTH","challenge123"]"""

        val message = RelayMessage.parse(json)

        assertTrue(message is RelayMessage.Auth)
        val authMsg = message as RelayMessage.Auth
        assertEquals("challenge123", authMsg.challenge)
    }

    @Test
    fun `parse CLOSED message`() {
        val json = """["CLOSED","sub1","auth-required: please authenticate"]"""

        val message = RelayMessage.parse(json)

        assertTrue(message is RelayMessage.Closed)
        val closedMsg = message as RelayMessage.Closed
        assertEquals("sub1", closedMsg.subscriptionId)
        assertEquals("auth-required: please authenticate", closedMsg.message)
    }

    @Test
    fun `parse COUNT message`() {
        val json = """["COUNT","sub1",{"count":42}]"""

        val message = RelayMessage.parse(json)

        assertTrue(message is RelayMessage.Count)
        val countMsg = message as RelayMessage.Count
        assertEquals("sub1", countMsg.subscriptionId)
        assertEquals(42, countMsg.count)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parse empty array throws exception`() {
        val json = """[]"""
        RelayMessage.parse(json)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parse unknown message type throws exception`() {
        val json = """["UNKNOWN","data"]"""
        RelayMessage.parse(json)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parse invalid JSON throws exception`() {
        val json = """not valid json"""
        RelayMessage.parse(json)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parse non-array JSON throws exception`() {
        val json = """{"type":"EVENT"}"""
        RelayMessage.parse(json)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parse EVENT with wrong number of elements throws exception`() {
        val json = """["EVENT"]"""
        RelayMessage.parse(json)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parse EOSE with wrong number of elements throws exception`() {
        val json = """["EOSE"]"""
        RelayMessage.parse(json)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parse OK with wrong number of elements throws exception`() {
        val json = """["OK","event123"]"""
        RelayMessage.parse(json)
    }

    @Test
    fun `parse EVENT with tags`() {
        val json = """["EVENT","sub1",{"id":"abc","pubkey":"def","created_at":1234567890,"kind":1,"tags":[["e","event1"],["p","pubkey1","relay1"]],"content":"Hello","sig":"sig"}]"""

        val message = RelayMessage.parse(json)

        assertTrue(message is RelayMessage.Event)
        val eventMsg = message as RelayMessage.Event
        assertEquals(2, eventMsg.event.tags.size)
        assertEquals("e", eventMsg.event.tags[0].name)
        assertEquals("event1", eventMsg.event.tags[0].values[0])
        assertEquals("p", eventMsg.event.tags[1].name)
        assertEquals("pubkey1", eventMsg.event.tags[1].values[0])
        assertEquals("relay1", eventMsg.event.tags[1].values[1])
    }
}
