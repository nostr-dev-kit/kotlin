package com.example.chirp.di.shadows

import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/**
 * Shadow for NostrDB to prevent native library loading and database initialization in unit tests.
 * This allows testing NDK module configuration without requiring actual JNI libraries.
 */
@Implements(className = "io.nostr.ndk.cache.nostrdb.NostrDB")
class ShadowNostrDB {

    companion object {
        private var mockPtr = 12345L

        @JvmStatic
        @Implementation
        @Suppress("unused")
        fun nativeInit(dbPath: String, mapSize: Long, ingesterThreads: Int): Long {
            // Return a mock pointer instead of calling native code
            return mockPtr++
        }

        @JvmStatic
        @Implementation
        @Suppress("unused")
        fun nativeDestroy(ndbPtr: Long) {
            // No-op: Prevent actual native cleanup in tests
        }

        @JvmStatic
        @Implementation
        @Suppress("unused")
        fun nativeBeginQuery(ndbPtr: Long): Long {
            // Return a mock transaction pointer
            return mockPtr++
        }

        @JvmStatic
        @Implementation
        @Suppress("unused")
        fun nativeEndQuery(txnPtr: Long) {
            // No-op: Prevent actual native query cleanup
        }
    }
}
