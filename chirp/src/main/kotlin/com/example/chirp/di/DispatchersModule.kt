package com.example.chirp.di

import com.example.chirp.core.DefaultDispatchersProvider
import com.example.chirp.core.DispatchersProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DispatchersModule {

    @Binds
    @Singleton
    abstract fun bindDispatchersProvider(
        impl: DefaultDispatchersProvider
    ): DispatchersProvider
}
