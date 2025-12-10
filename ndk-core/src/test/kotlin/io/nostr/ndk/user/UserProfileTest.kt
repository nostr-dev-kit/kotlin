package io.nostr.ndk.user

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserProfileTest {

    @Test
    fun `bestName returns displayName when available`() {
        val profile = createProfile(
            displayName = "Display Name",
            name = "username"
        )
        assertEquals("Display Name", profile.bestName)
    }

    @Test
    fun `bestName returns name when displayName is blank`() {
        val profile = createProfile(
            displayName = "",
            name = "username"
        )
        assertEquals("username", profile.bestName)
    }

    @Test
    fun `bestName returns name when displayName is null`() {
        val profile = createProfile(
            displayName = null,
            name = "username"
        )
        assertEquals("username", profile.bestName)
    }

    @Test
    fun `bestName returns truncated pubkey when both are null`() {
        val profile = createProfile(
            pubkey = "abcd1234567890",
            displayName = null,
            name = null
        )
        assertEquals("abcd1234...", profile.bestName)
    }

    @Test
    fun `lightningAddress returns lud16 when available`() {
        val profile = createProfile(
            lud16 = "user@getalby.com",
            lud06 = "lnurl..."
        )
        assertEquals("user@getalby.com", profile.lightningAddress)
    }

    @Test
    fun `lightningAddress returns lud06 when lud16 is blank`() {
        val profile = createProfile(
            lud16 = "",
            lud06 = "lnurl..."
        )
        assertEquals("lnurl...", profile.lightningAddress)
    }

    @Test
    fun `lightningAddress returns null when both are null`() {
        val profile = createProfile(
            lud16 = null,
            lud06 = null
        )
        assertNull(profile.lightningAddress)
    }

    private fun createProfile(
        pubkey: String = "testpubkey1234567890",
        name: String? = null,
        displayName: String? = null,
        about: String? = null,
        picture: String? = null,
        banner: String? = null,
        nip05: String? = null,
        lud16: String? = null,
        lud06: String? = null,
        website: String? = null,
        createdAt: Long = System.currentTimeMillis() / 1000
    ): UserProfile {
        return UserProfile(
            pubkey = pubkey,
            name = name,
            displayName = displayName,
            about = about,
            picture = picture,
            banner = banner,
            nip05 = nip05,
            lud16 = lud16,
            lud06 = lud06,
            website = website,
            createdAt = createdAt
        )
    }
}
