package io.nostr.ndk.crypto

import android.content.Context
import kotlinx.coroutines.ExperimentalCoroutinesApi
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKTag
import io.nostr.ndk.models.PublicKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * NIP-55 Android Signer Application integration for NDK.
 *
 * This signer delegates event signing to an external NIP-55 compliant signer app
 * (such as Amber) using Android Intents. The signer app securely stores private
 * keys and signs events without exposing the key to the requesting application.
 *
 * @see <a href="https://github.com/nostr-protocol/nips/blob/master/55.md">NIP-55</a>
 *
 * Usage:
 * ```kotlin
 * val signer = Nip55Signer(context)
 *
 * // In your Activity:
 * private val signerLauncher = registerForActivityResult(
 *     ActivityResultContracts.StartActivityForResult()
 * ) { result ->
 *     signer.handleResult(result.resultCode, result.data)
 * }
 *
 * // Set the launcher
 * signer.setActivityLauncher(signerLauncher)
 *
 * // Sign an event
 * val signedEvent = signer.sign(unsignedEvent)
 * ```
 *
 * @property context Android context for launching intents
 * @property permissions List of permissions to request from the signer
 * @property packageName Package name of the signer app (default: Amber)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Nip55Signer(
    private val context: Context,
    private val permissions: List<Nip55Permission> = listOf(Nip55Permission.SIGN_EVENT, Nip55Permission.GET_PUBLIC_KEY),
    private val packageName: String = DEFAULT_SIGNER_PACKAGE
) : NDKSigner {

    private var activityLauncher: ActivityResultLauncher<Intent>? = null
    private var pendingSignRequest: CompletableDeferred<NDKEvent>? = null
    private var pendingPubkeyRequest: CompletableDeferred<String>? = null
    private var cachedPubkey: String? = null

    /**
     * The public key associated with this signer.
     * This is fetched from the signer app on first access.
     */
    override val pubkey: PublicKey
        get() = cachedPubkey ?: throw IllegalStateException(
            "Public key not available. Call initialize() first or use sign() which fetches it automatically."
        )

    /**
     * Sets the ActivityResultLauncher to use for launching signer intents.
     * This must be called before using the signer.
     */
    fun setActivityLauncher(launcher: ActivityResultLauncher<Intent>) {
        this.activityLauncher = launcher
    }

    /**
     * Initializes the signer by fetching the public key from the signer app.
     * This should be called once during app initialization.
     *
     * @throws IllegalStateException if signer app is not installed or launcher not set
     */
    suspend fun initialize() {
        if (!isSignerInstalled(context, packageName)) {
            throw IllegalStateException("NIP-55 signer app is not installed. Package: $packageName")
        }

        if (cachedPubkey == null) {
            cachedPubkey = getPublicKeyFromSigner()
        }
    }

    /**
     * Signs an unsigned event using the NIP-55 signer app.
     *
     * @param event The unsigned event to sign
     * @return The signed NDKEvent
     * @throws IllegalStateException if signing fails or signer is not available
     */
    override suspend fun sign(event: UnsignedEvent): NDKEvent {
        val launcher = activityLauncher
            ?: throw IllegalStateException("ActivityResultLauncher not set. Call setActivityLauncher() first.")

        if (!isSignerInstalled(context, packageName)) {
            throw IllegalStateException("NIP-55 signer app is not installed. Package: $packageName")
        }

        // Ensure we have the public key
        if (cachedPubkey == null) {
            cachedPubkey = getPublicKeyFromSigner()
        }

        return suspendCancellableCoroutine { continuation ->
            val deferred = CompletableDeferred<NDKEvent>()
            pendingSignRequest = deferred

            val intent = createSignEventIntent(event)
            launcher.launch(intent)

            deferred.invokeOnCompletion { error ->
                if (error != null) {
                    continuation.resumeWithException(error)
                } else {
                    continuation.resume(deferred.getCompleted())
                }
            }
        }
    }

    override fun serialize(): ByteArray {
        throw UnsupportedOperationException(
            "Nip55Signer cannot be serialized - it requires Android Context. " +
            "Store the public key and recreate the signer on app restart."
        )
    }

    /**
     * Handles the result from the signer app after signing.
     * This should be called from your Activity's result handler.
     *
     * @param resultCode The result code from the activity
     * @param data The intent data containing the result
     */
    fun handleResult(resultCode: Int, data: Intent?) {
        if (resultCode != android.app.Activity.RESULT_OK || data == null) {
            pendingSignRequest?.completeExceptionally(
                IllegalStateException("Signing cancelled or failed")
            )
            pendingPubkeyRequest?.completeExceptionally(
                IllegalStateException("Public key request cancelled or failed")
            )
            pendingSignRequest = null
            pendingPubkeyRequest = null
            return
        }

        val signature = data.getStringExtra("signature")
        val eventJson = data.getStringExtra("event")
        val pubkeyResult = data.getStringExtra("pubkey")

        when {
            pubkeyResult != null -> {
                pendingPubkeyRequest?.complete(pubkeyResult)
                pendingPubkeyRequest = null
            }
            signature != null && eventJson != null -> {
                try {
                    val signedEvent = parseSignedEvent(eventJson)
                    pendingSignRequest?.complete(signedEvent)
                } catch (e: Exception) {
                    pendingSignRequest?.completeExceptionally(e)
                }
                pendingSignRequest = null
            }
            else -> {
                pendingSignRequest?.completeExceptionally(
                    IllegalStateException("Invalid response from signer: no signature or event")
                )
                pendingPubkeyRequest?.completeExceptionally(
                    IllegalStateException("Invalid response from signer: no pubkey")
                )
                pendingSignRequest = null
                pendingPubkeyRequest = null
            }
        }
    }

    /**
     * Fetches the public key from the signer app.
     */
    private suspend fun getPublicKeyFromSigner(): String {
        val launcher = activityLauncher
            ?: throw IllegalStateException("ActivityResultLauncher not set. Call setActivityLauncher() first.")

        return suspendCancellableCoroutine { continuation ->
            val deferred = CompletableDeferred<String>()
            pendingPubkeyRequest = deferred

            val intent = createGetPublicKeyIntent()
            launcher.launch(intent)

            deferred.invokeOnCompletion { error ->
                if (error != null) {
                    continuation.resumeWithException(error)
                } else {
                    continuation.resume(deferred.getCompleted())
                }
            }
        }
    }

    /**
     * Creates an intent to sign an event via the signer app.
     */
    private fun createSignEventIntent(event: UnsignedEvent): Intent {
        val eventJson = serializeEvent(event)
        val uri = Uri.parse("nostrsigner:$eventJson")

        return Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(packageName)
            putExtra("type", "sign_event")
            putExtra("id", generateRequestId())
            permissions.forEach { permission ->
                putExtra("permission", permission.value)
            }
        }
    }

    /**
     * Creates an intent to get the public key from the signer app.
     */
    private fun createGetPublicKeyIntent(): Intent {
        val uri = Uri.parse("nostrsigner:")

        return Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(packageName)
            putExtra("type", "get_public_key")
            putExtra("id", generateRequestId())
            putExtra("permission", Nip55Permission.GET_PUBLIC_KEY.value)
        }
    }

    /**
     * Serializes an unsigned event to JSON format expected by NIP-55 signers.
     */
    private fun serializeEvent(event: UnsignedEvent): String {
        val eventMap = mapOf(
            "pubkey" to event.pubkey,
            "created_at" to event.createdAt,
            "kind" to event.kind,
            "tags" to event.tags.map { listOf(it.name) + it.values },
            "content" to event.content
        )
        return objectMapper.writeValueAsString(eventMap)
    }

    /**
     * Parses a signed event JSON from the signer app into an NDKEvent.
     */
    private fun parseSignedEvent(eventJson: String): NDKEvent {
        val eventMap: Map<String, Any> = objectMapper.readValue(eventJson)

        return NDKEvent(
            id = eventMap["id"] as String,
            pubkey = eventMap["pubkey"] as String,
            createdAt = (eventMap["created_at"] as Number).toLong(),
            kind = (eventMap["kind"] as Number).toInt(),
            tags = parseTags(eventMap["tags"] as List<*>),
            content = eventMap["content"] as String,
            sig = eventMap["sig"] as String?
        )
    }

    /**
     * Parses tags from JSON array format.
     */
    private fun parseTags(tagsJson: List<*>): List<NDKTag> {
        return tagsJson.mapNotNull { tagArray ->
            when (tagArray) {
                is List<*> -> {
                    if (tagArray.isEmpty()) return@mapNotNull null
                    val name = tagArray[0] as? String ?: return@mapNotNull null
                    val values = tagArray.drop(1).mapNotNull { it as? String }
                    NDKTag(name, values)
                }
                else -> null
            }
        }
    }

    /**
     * Generates a unique request ID for signer intents.
     */
    private fun generateRequestId(): String {
        return System.currentTimeMillis().toString()
    }

    companion object {
        private val objectMapper: ObjectMapper = jacksonObjectMapper()

        /** Default NIP-55 signer package (Amber) */
        const val DEFAULT_SIGNER_PACKAGE = "com.greenart7c3.nostrsigner"

        /**
         * Checks if a NIP-55 signer app is installed on the device.
         *
         * @param context Android context
         * @param packageName Package name of the signer app
         * @return true if the signer is installed, false otherwise
         */
        fun isSignerInstalled(
            context: Context,
            packageName: String = DEFAULT_SIGNER_PACKAGE
        ): Boolean {
            return try {
                context.packageManager.getPackageInfo(packageName, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }

        /**
         * Returns a list of known NIP-55 signer packages.
         */
        fun knownSignerPackages(): List<String> = listOf(
            "com.greenart7c3.nostrsigner"  // Amber
        )

        /**
         * Finds all installed NIP-55 signers on the device.
         *
         * @param context Android context
         * @return List of package names for installed signers
         */
        fun findInstalledSigners(context: Context): List<String> {
            return knownSignerPackages().filter { isSignerInstalled(context, it) }
        }

        /**
         * Opens the Play Store page for a signer app.
         *
         * @param context Android context
         * @param packageName Package name of the signer app
         */
        fun openInPlayStore(
            context: Context,
            packageName: String = DEFAULT_SIGNER_PACKAGE
        ) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                setPackage("com.android.vending")
            }
            context.startActivity(intent)
        }
    }
}

/**
 * Permissions that can be requested from a NIP-55 signer app.
 * These permissions control what operations the app can perform.
 */
enum class Nip55Permission(val value: String) {
    /** Permission to sign Nostr events */
    SIGN_EVENT("sign_event"),

    /** Permission to perform NIP-04 encryption */
    NIP04_ENCRYPT("nip04_encrypt"),

    /** Permission to perform NIP-04 decryption */
    NIP04_DECRYPT("nip04_decrypt"),

    /** Permission to perform NIP-44 encryption */
    NIP44_ENCRYPT("nip44_encrypt"),

    /** Permission to perform NIP-44 decryption */
    NIP44_DECRYPT("nip44_decrypt"),

    /** Permission to get the public key */
    GET_PUBLIC_KEY("get_public_key"),

    /** Permission to decrypt zap events (NIP-57) */
    DECRYPT_ZAP_EVENT("decrypt_zap_event");

    companion object {
        fun fromValue(value: String): Nip55Permission? {
            return entries.find { it.value == value }
        }
    }
}
