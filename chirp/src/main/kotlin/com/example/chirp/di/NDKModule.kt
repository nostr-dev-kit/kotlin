package com.example.chirp.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.nostr.ndk.NDK
import io.nostr.ndk.account.android.AndroidAccountStorage
import io.nostr.ndk.cache.nostrdb.NostrDBCacheAdapter
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NDKModule {

    @Provides
    @Singleton
    fun provideNDK(
        @ApplicationContext context: Context
    ): NDK = NDK(
        explicitRelayUrls = setOf(
            "wss://relay.damus.io",
            "wss://nos.lol",
            "wss://relay.nostr.band"
        ),
        cacheAdapter = NostrDBCacheAdapter.create(context),
        accountStorage = AndroidAccountStorage.getInstance(context)
    )
}
