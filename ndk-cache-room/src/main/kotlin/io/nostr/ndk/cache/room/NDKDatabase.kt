package io.nostr.ndk.cache.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for NDK event cache.
 *
 * Usage:
 * ```
 * val db = NDKDatabase.getInstance(context)
 * val cacheAdapter = RoomCacheAdapter(db)
 * val ndk = NDK(cacheAdapter = cacheAdapter)
 * ```
 */
@Database(
    entities = [EventEntity::class],
    version = 1,
    exportSchema = false
)
abstract class NDKDatabase : RoomDatabase() {

    abstract fun eventDao(): EventDao

    companion object {
        private const val DATABASE_NAME = "ndk_events.db"

        @Volatile
        private var instance: NDKDatabase? = null

        /**
         * Get the singleton database instance.
         *
         * @param context Application context
         * @return NDKDatabase instance
         */
        fun getInstance(context: Context): NDKDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }
        }

        /**
         * Create a new database instance with custom name.
         * Useful for testing or multiple database instances.
         *
         * @param context Application context
         * @param name Database file name
         * @return New NDKDatabase instance
         */
        fun create(context: Context, name: String = DATABASE_NAME): NDKDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                NDKDatabase::class.java,
                name
            )
                .fallbackToDestructiveMigration()
                .build()
        }

        /**
         * Create an in-memory database for testing.
         *
         * @param context Application context
         * @return In-memory NDKDatabase instance
         */
        fun createInMemory(context: Context): NDKDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                NDKDatabase::class.java
            )
                .allowMainThreadQueries()
                .build()
        }

        private fun buildDatabase(context: Context): NDKDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                NDKDatabase::class.java,
                DATABASE_NAME
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
