package io.nostr.ndk.account.android

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.nostr.ndk.account.NDKAccountStorage
import io.nostr.ndk.models.PublicKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android implementation of NDKAccountStorage using Android Keystore.
 *
 * Uses:
 * - MasterKey (AES256_GCM) from Android Keystore for encryption
 * - EncryptedFile for storing signer data
 * - EncryptedSharedPreferences for tracking account list
 *
 * Thread-safe with Mutex protection.
 */
class AndroidAccountStorage private constructor(
    private val context: Context
) : NDKAccountStorage {

    private val mutex = Mutex()
    private val storageDir = File(context.filesDir, "ndk_accounts")

    private val masterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val accountsPrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            "ndk_accounts_list",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    init {
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
    }

    override suspend fun saveSigner(pubkey: PublicKey, signerPayload: ByteArray) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val file = File(storageDir, pubkey)

            // EncryptedFile requires delete before overwrite
            if (file.exists()) {
                file.delete()
            }

            val encryptedFile = EncryptedFile.Builder(
                context,
                file,
                masterKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()

            encryptedFile.openFileOutput().use { output ->
                output.write(signerPayload)
            }

            // Track this account in the list
            accountsPrefs.edit().putBoolean(pubkey, true).apply()
        }
    }

    override suspend fun loadSigner(pubkey: PublicKey): ByteArray? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val file = File(storageDir, pubkey)

            if (!file.exists()) {
                return@withContext null
            }

            val encryptedFile = EncryptedFile.Builder(
                context,
                file,
                masterKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()

            encryptedFile.openFileInput().use { input ->
                input.readBytes()
            }
        }
    }

    override suspend fun listAccounts(): List<PublicKey> = withContext(Dispatchers.IO) {
        mutex.withLock {
            accountsPrefs.all.keys.toList()
        }
    }

    override suspend fun deleteAccount(pubkey: PublicKey) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val file = File(storageDir, pubkey)

            if (file.exists()) {
                file.delete()
            }

            accountsPrefs.edit().remove(pubkey).apply()
        }
    }

    companion object {
        @Volatile
        private var instance: AndroidAccountStorage? = null

        /**
         * Gets the singleton instance of AndroidAccountStorage.
         *
         * @param context Application or Activity context
         * @return The singleton instance
         */
        fun getInstance(context: Context): AndroidAccountStorage {
            return instance ?: synchronized(this) {
                instance ?: AndroidAccountStorage(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
