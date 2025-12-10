package io.nostr.ndk.signer.amber

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.nostr.ndk.crypto.NDKSigner
import io.nostr.ndk.crypto.UnsignedEvent
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKTag
import io.nostr.ndk.models.PublicKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * NIP-55 Android Signer Application (Amber) integration for NDK.
 *
 * This signer delegates event signing to the Amber app using Android Intents.
 * Amber is a key management app that securely stores private keys and signs
 * events without exposing the key to the requesting application.
 *
 * Usage:
 * ```kotlin
 * val signer = AmberSigner(context)
 *
 * // In your Activity:
 * private val amberLauncher = registerForActivityResult(
 *     ActivityResultContracts.StartActivityForResult()
 * ) { result ->
 *     signer.handleAmberResult(result.resultCode, result.data)
 * }
 *
 * // Set the launcher
 * signer.setActivityLauncher(amberLauncher)
 *
 * // Sign an event
 * val signedEvent = signer.sign(unsignedEvent)
 * ```
 *
 * @property context Android context for launching intents
 * @property permissions List of permissions to request from Amber
 * @property packageName Package name of the Amber app (default: com.greenart7c3.nostrsigner)
 */
class AmberSigner(
    private val context: Context,
    private val permissions: List<Permission> = listOf(Permission.SIGN_EVENT, Permission.GET_PUBLIC_KEY),
    private val packageName: String = DEFAULT_AMBER_PACKAGE
) : NDKSigner {

    private var activityLauncher: ActivityResultLauncher<Intent>? = null
    private var pendingSignRequest: CompletableDeferred<NDKEvent>? = null
    private var pendingPubkeyRequest: CompletableDeferred<String>? = null
    private var cachedPubkey: String? = null

    /**
     * The public key associated with this signer.
     * This is fetched from Amber on first access.
     */
    override val pubkey: PublicKey
        get() = cachedPubkey ?: throw IllegalStateException(
            "Public key not available. Call initialize() first or use sign() which fetches it automatically."
        )

    /**
     * Sets the ActivityResultLauncher to use for launching Amber intents.
     * This must be called before using the signer.
     */
    fun setActivityLauncher(launcher: ActivityResultLauncher<Intent>) {
        this.activityLauncher = launcher
    }

    /**
     * Initializes the signer by fetching the public key from Amber.
     * This should be called once during app initialization.
     *
     * @throws IllegalStateException if Amber is not installed or launcher not set
     */
    suspend fun initialize() {
        if (!isAmberInstalled(context, packageName)) {
            throw IllegalStateException("Amber app is not installed. Package: $packageName")
        }

        if (cachedPubkey == null) {
            cachedPubkey = getPublicKeyFromAmber()
        }
    }

    /**
     * Signs an unsigned event using the Amber app.
     *
     * @param event The unsigned event to sign
     * @return The signed NDKEvent
     * @throws IllegalStateException if signing fails or Amber is not available
     */
    override suspend fun sign(event: UnsignedEvent): NDKEvent {
        val launcher = activityLauncher
            ?: throw IllegalStateException("ActivityResultLauncher not set. Call setActivityLauncher() first.")

        if (!isAmberInstalled(context, packageName)) {
            throw IllegalStateException("Amber app is not installed. Package: $packageName")
        }

        // Ensure we have the public key
        if (cachedPubkey == null) {
            cachedPubkey = getPublicKeyFromAmber()
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

    /**
     * Handles the result from Amber after signing.
     * This should be called from your Activity's result handler.
     *
     * @param resultCode The result code from the activity
     * @param data The intent data containing the result
     */
    fun handleAmberResult(resultCode: Int, data: Intent?) {
        if (resultCode != android.app.Activity.RESULT_OK || data == null) {
            pendingSignRequest?.completeExceptionally(
                IllegalStateException("Amber signing cancelled or failed")
            )
            pendingPubkeyRequest?.completeExceptionally(
                IllegalStateException("Amber public key request cancelled or failed")
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
                    IllegalStateException("Invalid response from Amber: no signature or event")
                )
                pendingPubkeyRequest?.completeExceptionally(
                    IllegalStateException("Invalid response from Amber: no pubkey")
                )
                pendingSignRequest = null
                pendingPubkeyRequest = null
            }
        }
    }

    /**
     * Fetches the public key from Amber.
     */
    private suspend fun getPublicKeyFromAmber(): String {
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
     * Creates an intent to sign an event via Amber.
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
     * Creates an intent to get the public key from Amber.
     */
    private fun createGetPublicKeyIntent(): Intent {
        val uri = Uri.parse("nostrsigner:")

        return Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(packageName)
            putExtra("type", "get_public_key")
            putExtra("id", generateRequestId())
            putExtra("permission", Permission.GET_PUBLIC_KEY.value)
        }
    }

    /**
     * Serializes an unsigned event to JSON format expected by Amber.
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
     * Parses a signed event JSON from Amber into an NDKEvent.
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
     * Generates a unique request ID for Amber intents.
     */
    private fun generateRequestId(): String {
        return System.currentTimeMillis().toString()
    }

    companion object {
        private val objectMapper: ObjectMapper = jacksonObjectMapper()
        private const val DEFAULT_AMBER_PACKAGE = "com.greenart7c3.nostrsigner"

        /**
         * Checks if the Amber app is installed on the device.
         *
         * @param context Android context
         * @param packageName Package name of the Amber app
         * @return true if Amber is installed, false otherwise
         */
        fun isAmberInstalled(
            context: Context,
            packageName: String = DEFAULT_AMBER_PACKAGE
        ): Boolean {
            return try {
                context.packageManager.getPackageInfo(packageName, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }

        /**
         * Opens the Play Store page for the Amber app.
         *
         * @param context Android context
         * @param packageName Package name of the Amber app
         */
        fun openAmberInPlayStore(
            context: Context,
            packageName: String = DEFAULT_AMBER_PACKAGE
        ) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                setPackage("com.android.vending")
            }
            context.startActivity(intent)
        }
    }
}
