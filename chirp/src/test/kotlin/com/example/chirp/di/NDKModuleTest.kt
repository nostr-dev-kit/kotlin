package com.example.chirp.di

import org.junit.Test
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class NDKModuleTest {

    @Test
    fun `NDKModule object exists`() {
        assertNotNull(NDKModule, "NDKModule should be accessible")
    }

    @Test
    fun `provideNDK method exists`() {
        val method = NDKModule::class.java.declaredMethods.find { it.name == "provideNDK" }
        assertNotNull(method, "provideNDK method should exist")
    }

    @Test
    fun `provideNDK method returns NDK type`() {
        val method = NDKModule::class.java.declaredMethods.find { it.name == "provideNDK" }
        assertTrue(
            method?.returnType == io.nostr.ndk.NDK::class.java,
            "provideNDK should return NDK instance"
        )
    }

    @Test
    fun `provideNDK method takes Context parameter`() {
        val method = NDKModule::class.java.declaredMethods.find { it.name == "provideNDK" }
        val parameters = method?.parameterTypes
        assertTrue(
            parameters?.size == 1 && parameters[0] == android.content.Context::class.java,
            "provideNDK should take a Context parameter"
        )
    }
}
