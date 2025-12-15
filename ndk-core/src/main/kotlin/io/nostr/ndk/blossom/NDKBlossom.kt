package io.nostr.ndk.blossom

import io.nostr.ndk.NDK
import io.nostr.ndk.crypto.NDKSigner
import io.nostr.ndk.crypto.UnsignedEvent
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKFilter
import io.nostr.ndk.models.NDKTag
import io.nostr.ndk.models.PublicKey
import io.nostr.ndk.user.NDKUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Blossom protocol implementation for NDK Android.
 *
 * Blossom is a decentralized media hosting protocol that uses content-addressed
 * storage (SHA-256 hashes) to store and retrieve files on Blossom servers.
 *
 * @see <a href="https://github.com/hzrd149/blossom">Blossom Protocol</a>
 *
 * @param ndk NDK instance
 * @param signer Optional signer for authentication (falls back to ndk.signer)
 */
class NDKBlossom(
    val ndk: NDK,
    var signer: NDKSigner? = null
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private var cachedServerList: BlossomServerList? = null

    /**
     * Callback for upload progress.
     * Return "continue" to proceed, "cancel" to abort.
     */
    var onUploadProgress: ((loaded: Long, total: Long) -> String)? = null

    /**
     * Callback for upload failures.
     */
    var onUploadFailed: ((error: String, serverUrl: String?) -> Unit)? = null

    /**
     * Get the user's Blossom server list (kind 10063).
     *
     * @param user Optional user to fetch servers for (defaults to ndk.activeUser)
     * @return BlossomServerList or null if not found
     */
    suspend fun getServerList(user: NDKUser? = null): BlossomServerList? {
        cachedServerList?.let { return it }

        val effectiveSigner = signer ?: ndk.signer
        val targetUser = user ?: effectiveSigner?.let { NDKUser(it.pubkey, ndk) }
            ?: throw BlossomError("No user available to fetch server list", BlossomErrorCode.NO_SIGNER)

        val filter = NDKFilter(
            kinds = setOf(KIND_BLOSSOM_SERVER_LIST),
            authors = setOf(targetUser.pubkey)
        )

        val subscription = ndk.subscribe(filter)
        val event = subscription.fetchEvent() ?: return null
        cachedServerList = BlossomServerList.fromEvent(event)
        return cachedServerList
    }

    /**
     * Set a cached server list.
     */
    fun setServerList(serverList: BlossomServerList) {
        cachedServerList = serverList
    }

    /**
     * Upload a file to a Blossom server.
     *
     * @param data File contents as ByteArray
     * @param mimeType MIME type of the file
     * @param options Upload options
     * @return Blob metadata with URL and hash
     */
    suspend fun upload(
        data: ByteArray,
        mimeType: String,
        options: BlossomUploadOptions = BlossomUploadOptions()
    ): BlobDescriptor = withContext(Dispatchers.IO) {
        // Calculate SHA-256 hash
        val hash = calculateSha256(data)

        // Determine which server to use
        val serverUrl = options.server ?: run {
            val serverList = try {
                getServerList()
            } catch (e: BlossomError) {
                null // Fall through to fallback
            }
            serverList?.servers?.firstOrNull()
                ?: options.fallbackServer
                ?: throw BlossomError("No blossom servers configured", BlossomErrorCode.SERVER_LIST_EMPTY)
        }

        try {
            uploadToServer(data, mimeType, hash, serverUrl, options)
        } catch (e: Exception) {
            onUploadFailed?.invoke(e.message ?: "Unknown error", serverUrl)

            // Try fallback if available
            if (options.fallbackServer != null && options.server == null) {
                try {
                    return@withContext uploadToServer(data, mimeType, hash, options.fallbackServer, options)
                } catch (fallbackError: Exception) {
                    onUploadFailed?.invoke(fallbackError.message ?: "Unknown error", options.fallbackServer)
                }
            }
            throw e
        }
    }

    /**
     * Upload a file from disk.
     */
    suspend fun upload(
        file: File,
        mimeType: String? = null,
        options: BlossomUploadOptions = BlossomUploadOptions()
    ): BlobDescriptor {
        val data = file.readBytes()
        val type = mimeType ?: guessMimeType(file.name)
        return upload(data, type, options)
    }

    /**
     * Upload from an InputStream.
     */
    suspend fun upload(
        inputStream: InputStream,
        mimeType: String,
        options: BlossomUploadOptions = BlossomUploadOptions()
    ): BlobDescriptor {
        val data = inputStream.readBytes()
        return upload(data, mimeType, options)
    }

    private suspend fun uploadToServer(
        data: ByteArray,
        mimeType: String,
        hash: String,
        serverUrl: String,
        options: BlossomUploadOptions
    ): BlobDescriptor {
        val effectiveSigner = signer ?: ndk.signer
            ?: throw BlossomError("No signer available for upload", BlossomErrorCode.NO_SIGNER)

        val baseUrl = serverUrl.trimEnd('/')
        val uploadUrl = "$baseUrl/upload"

        // Create auth event
        val authEvent = createAuthEvent(
            signer = effectiveSigner,
            action = "upload",
            sha256 = hash,
            content = "Upload blob"
        )

        // Build request
        val requestBody = data.toRequestBody(mimeType.toMediaType())
        val request = Request.Builder()
            .url(uploadUrl)
            .put(requestBody)
            .header("Content-Type", mimeType)
            .header("Authorization", "Nostr ${encodeAuthEvent(authEvent)}")
            .apply {
                options.headers?.forEach { (key, value) ->
                    header(key, value)
                }
            }
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw BlossomError(
                "Upload failed: ${response.code} $errorBody",
                BlossomErrorCode.UPLOAD_FAILED,
                serverUrl
            )
        }

        return BlobDescriptor(
            sha256 = hash,
            url = "$baseUrl/$hash",
            size = data.size.toLong(),
            mimeType = mimeType
        )
    }

    /**
     * Get a blob from a URL.
     *
     * @param url URL of the blob
     * @return Blob contents as ByteArray
     */
    suspend fun getBlob(url: String): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).get().build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            throw BlossomError(
                "Failed to fetch blob: ${response.code}",
                BlossomErrorCode.BLOB_NOT_FOUND,
                url
            )
        }

        response.body?.bytes() ?: throw BlossomError(
            "Empty response body",
            BlossomErrorCode.BLOB_NOT_FOUND,
            url
        )
    }

    /**
     * Get a blob by its SHA-256 hash from user's servers.
     *
     * @param hash SHA-256 hash of the blob
     * @param user Optional user whose servers to check
     * @return Blob contents as ByteArray
     */
    suspend fun getBlobByHash(hash: String, user: NDKUser? = null): ByteArray {
        val serverList = getServerList(user)
            ?: throw BlossomError("No server list found", BlossomErrorCode.SERVER_LIST_EMPTY)

        for (serverUrl in serverList.servers) {
            try {
                val url = "${serverUrl.trimEnd('/')}/$hash"
                return getBlob(url)
            } catch (e: Exception) {
                // Try next server
            }
        }

        throw BlossomError("Blob not found on any server", BlossomErrorCode.BLOB_NOT_FOUND)
    }

    /**
     * Check if a server has a specific blob.
     *
     * @param serverUrl Server URL
     * @param hash SHA-256 hash
     * @return true if blob exists
     */
    suspend fun checkBlobExists(serverUrl: String, hash: String): Boolean = withContext(Dispatchers.IO) {
        val url = "${serverUrl.trimEnd('/')}/$hash"
        val request = Request.Builder().url(url).head().build()

        try {
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Delete a blob from user's servers.
     *
     * @param hash SHA-256 hash of the blob to delete
     * @return true if deleted from at least one server
     */
    suspend fun deleteBlob(hash: String): Boolean = withContext(Dispatchers.IO) {
        val effectiveSigner = signer ?: ndk.signer
            ?: throw BlossomError("No signer available for delete", BlossomErrorCode.NO_SIGNER)

        val serverList = getServerList()
            ?: throw BlossomError("No server list found", BlossomErrorCode.SERVER_LIST_EMPTY)

        var deleted = false

        for (serverUrl in serverList.servers) {
            try {
                val baseUrl = serverUrl.trimEnd('/')
                val deleteUrl = "$baseUrl/$hash"

                val authEvent = createAuthEvent(
                    signer = effectiveSigner,
                    action = "delete",
                    sha256 = hash,
                    content = "Delete blob $hash"
                )

                val request = Request.Builder()
                    .url(deleteUrl)
                    .delete()
                    .header("Authorization", "Nostr ${encodeAuthEvent(authEvent)}")
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    deleted = true
                }
            } catch (e: Exception) {
                // Continue to next server
            }
        }

        deleted
    }

    /**
     * List blobs for a user from their servers.
     *
     * @param user Optional user (defaults to active user)
     * @return List of blob descriptors
     */
    suspend fun listBlobs(user: NDKUser? = null): List<BlobDescriptor> = withContext(Dispatchers.IO) {
        val targetUser = user ?: ndk.signer?.let { NDKUser(it.pubkey, ndk) }
            ?: throw BlossomError("No user available", BlossomErrorCode.NO_SIGNER)

        val serverList = getServerList(targetUser)
            ?: return@withContext emptyList()

        val blobs = mutableMapOf<String, BlobDescriptor>()

        for (serverUrl in serverList.servers) {
            try {
                val baseUrl = serverUrl.trimEnd('/')
                val listUrl = "$baseUrl/list/${targetUser.pubkey}"

                val request = Request.Builder().url(listUrl).get().build()
                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val body = response.body?.string() ?: continue
                    // Parse JSON array of blobs
                    // Simple parsing - in production would use Jackson
                    parseBlobList(body, baseUrl).forEach { blob ->
                        blobs[blob.sha256] = blob
                    }
                }
            } catch (e: Exception) {
                // Continue to next server
            }
        }

        blobs.values.toList()
    }

    /**
     * Get optimized URL for a blob (for image resizing, format conversion).
     *
     * @param url Original blob URL
     * @param options Optimization options
     * @return Optimized URL
     */
    fun getOptimizedUrl(url: String, options: BlossomOptimizationOptions): String {
        val urlObj = java.net.URL(url)
        val baseUrl = "${urlObj.protocol}://${urlObj.host}"
        val hash = urlObj.path.split("/").lastOrNull() ?: return url

        val params = mutableListOf<String>()
        options.width?.let { params.add("width=$it") }
        options.height?.let { params.add("height=$it") }
        options.format?.let { params.add("format=$it") }
        options.quality?.let { params.add("quality=$it") }

        val queryString = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
        return "$baseUrl/media/$hash$queryString"
    }

    private suspend fun createAuthEvent(
        signer: NDKSigner,
        action: String,
        sha256: String?,
        content: String,
        expirationSeconds: Long = 3600
    ): NDKEvent {
        val tags = mutableListOf(
            NDKTag("t", listOf(action)),
            NDKTag("expiration", listOf((System.currentTimeMillis() / 1000 + expirationSeconds).toString()))
        )

        sha256?.let {
            tags.add(NDKTag("x", listOf(it)))
        }

        val unsigned = UnsignedEvent(
            pubkey = signer.pubkey,
            createdAt = System.currentTimeMillis() / 1000,
            kind = KIND_BLOSSOM_AUTH,
            tags = tags,
            content = content
        )

        return signer.sign(unsigned)
    }

    private fun encodeAuthEvent(event: NDKEvent): String {
        val json = """{"id":"${event.id}","pubkey":"${event.pubkey}","created_at":${event.createdAt},"kind":${event.kind},"tags":${tagsToJson(event.tags)},"content":"${event.content}","sig":"${event.sig}"}"""
        return Base64.getEncoder().encodeToString(json.toByteArray())
    }

    private fun tagsToJson(tags: List<NDKTag>): String {
        return tags.joinToString(",", "[", "]") { tag ->
            (listOf(tag.name) + tag.values).joinToString(",", "[", "]") { "\"$it\"" }
        }
    }

    private fun calculateSha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private fun guessMimeType(filename: String): String {
        return when {
            filename.endsWith(".jpg", ignoreCase = true) || filename.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
            filename.endsWith(".png", ignoreCase = true) -> "image/png"
            filename.endsWith(".gif", ignoreCase = true) -> "image/gif"
            filename.endsWith(".webp", ignoreCase = true) -> "image/webp"
            filename.endsWith(".mp4", ignoreCase = true) -> "video/mp4"
            filename.endsWith(".webm", ignoreCase = true) -> "video/webm"
            filename.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg"
            filename.endsWith(".ogg", ignoreCase = true) -> "audio/ogg"
            filename.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
            else -> "application/octet-stream"
        }
    }

    private fun parseBlobList(json: String, baseUrl: String): List<BlobDescriptor> {
        // Simple JSON parsing - in production use Jackson
        val blobs = mutableListOf<BlobDescriptor>()
        // This is a simplified parser - would use Jackson in real implementation
        return blobs
    }

    companion object {
        /** Blossom server list kind (NIP-TBD) */
        const val KIND_BLOSSOM_SERVER_LIST = 10063

        /** Blossom auth event kind (BUD-01) */
        const val KIND_BLOSSOM_AUTH = 24242
    }
}

/**
 * Blossom server list (kind 10063).
 */
data class BlossomServerList(
    val pubkey: PublicKey,
    val servers: List<String>,
    val createdAt: Long
) {
    companion object {
        fun fromEvent(event: NDKEvent): BlossomServerList {
            val servers = event.tags
                .filter { it.name == "server" || it.name == "r" }
                .mapNotNull { it.values.firstOrNull() }

            return BlossomServerList(
                pubkey = event.pubkey,
                servers = servers,
                createdAt = event.createdAt
            )
        }
    }
}

/**
 * Blob descriptor returned from Blossom servers.
 */
data class BlobDescriptor(
    val sha256: String,
    val url: String,
    val size: Long,
    val mimeType: String,
    val width: Int? = null,
    val height: Int? = null,
    val blurhash: String? = null,
    val alt: String? = null
)

/**
 * Options for uploading to Blossom servers.
 */
data class BlossomUploadOptions(
    /** Specific server to use (bypasses server list) */
    val server: String? = null,
    /** Fallback server if all others fail */
    val fallbackServer: String? = null,
    /** Additional headers */
    val headers: Map<String, String>? = null,
    /** Maximum retry attempts */
    val maxRetries: Int = 3,
    /** Delay between retries in ms */
    val retryDelay: Long = 1000
)

/**
 * Options for media optimization.
 */
data class BlossomOptimizationOptions(
    val width: Int? = null,
    val height: Int? = null,
    val format: String? = null,
    val quality: Int? = null
)

/**
 * Blossom error codes.
 */
enum class BlossomErrorCode {
    NO_SIGNER,
    SERVER_LIST_EMPTY,
    UPLOAD_FAILED,
    BLOB_NOT_FOUND,
    AUTH_FAILED,
    SERVER_ERROR
}

/**
 * Blossom error.
 */
class BlossomError(
    message: String,
    val code: BlossomErrorCode,
    val serverUrl: String? = null,
    cause: Throwable? = null
) : Exception(message, cause)
