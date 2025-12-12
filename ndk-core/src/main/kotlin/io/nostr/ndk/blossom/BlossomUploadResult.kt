package io.nostr.ndk.blossom

import kotlinx.serialization.Serializable

/**
 * Result of a successful Blossom upload.
 */
@Serializable
data class BlossomUploadResult(
    val url: String,
    val sha256: String,
    val size: Long,
    val type: String
) {
    val mimeType: String get() = type
}
