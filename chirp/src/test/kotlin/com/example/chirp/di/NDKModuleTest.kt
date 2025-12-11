package com.example.chirp.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.chirp.di.shadows.ShadowNostrDB
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Functional tests for NDKModule that verify provideNDK correctly configures and provides
 * an NDK instance with the expected relay URLs and dependencies.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowNostrDB::class])
class NDKModuleTest {

    @Test
    fun `provideNDK returns configured NDK instance`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val module = NDKModule
        val ndk = module.provideNDK(context)

        assertNotNull(ndk, "provideNDK should return a non-null NDK instance")
    }

    @Test
    fun `provideNDK configures relay URLs correctly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val module = NDKModule
        val ndk = module.provideNDK(context)

        val expectedRelays = setOf(
            "wss://relay.damus.io",
            "wss://nos.lol",
            "wss://relay.nostr.band"
        )

        assertEquals(expectedRelays, ndk.explicitRelayUrls,
            "NDK should be configured with the correct relay URLs")
    }

    @Test
    fun `provideNDK configures cache adapter`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val module = NDKModule
        val ndk = module.provideNDK(context)

        assertNotNull(ndk.cacheAdapter, "NDK should have a cache adapter configured")
    }

    @Test
    fun `provideNDK configures account storage`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val module = NDKModule
        val ndk = module.provideNDK(context)

        assertNotNull(ndk.accountStorage, "NDK should have account storage configured")
    }

    @Test
    fun `provideNDK pool is accessible`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val module = NDKModule
        val ndk = module.provideNDK(context)

        val pool = ndk.pool
        assertNotNull(pool, "NDK pool should be accessible")
        assertEquals(pool, ndk.pool, "Pool should return same instance on subsequent access")
    }
}
