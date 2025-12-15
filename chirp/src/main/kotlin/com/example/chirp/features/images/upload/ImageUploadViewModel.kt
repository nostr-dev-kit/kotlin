package com.example.chirp.features.images.upload

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.nostr.ndk.NDK
import io.nostr.ndk.blossom.BlossomUploadOptions
import io.nostr.ndk.blossom.NDKBlossom
import io.nostr.ndk.builders.ImageBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

private const val DEFAULT_FALLBACK_SERVER = "https://blossom.primal.net"

@HiltViewModel
class ImageUploadViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ndk: NDK
) : ViewModel() {

    private val _state = MutableStateFlow(ImageUploadState())
    val state: StateFlow<ImageUploadState> = _state.asStateFlow()

    fun onImagesSelected(uris: List<Uri>) {
        viewModelScope.launch {
            _state.update { it.copy(isProcessing = true, error = null) }

            try {
                val processed = uris.map { uri ->
                    async { processImage(uri) }
                }.awaitAll()

                _state.update {
                    it.copy(
                        selectedImages = processed,
                        isProcessing = false
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isProcessing = false,
                        error = "Failed to process images: ${e.message}"
                    )
                }
            }
        }
    }

    private suspend fun processImage(uri: Uri): ProcessedImage = withContext(Dispatchers.IO) {
        // Load bitmap
        val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input)
        } ?: throw IOException("Failed to load image")

        // Resize if too large (max 2048x2048)
        val resized = if (bitmap.width > 2048 || bitmap.height > 2048) {
            val scale = minOf(2048f / bitmap.width, 2048f / bitmap.height)
            val newWidth = (bitmap.width * scale).toInt()
            val newHeight = (bitmap.height * scale).toInt()
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true).also {
                bitmap.recycle()
            }
        } else {
            bitmap
        }

        // Generate blurhash (placeholder until blurhash library is available)
        val blurhash = "L12345"

        // Create temp file
        val file = File.createTempFile("upload_", ".jpg", context.cacheDir)
        file.outputStream().use { output ->
            resized.compress(Bitmap.CompressFormat.JPEG, 90, output)
        }

        ProcessedImage(
            file = file,
            blurhash = blurhash,
            dimensions = resized.width to resized.height,
            mimeType = "image/jpeg"
        )
    }

    fun onCaptionChanged(caption: String) {
        _state.update { it.copy(caption = caption) }
    }

    fun onPublish() {
        viewModelScope.launch {
            _state.update { it.copy(isUploading = true, uploadProgress = 0f, error = null) }

            try {
                val signer = ndk.currentUser.value?.signer
                    ?: throw Exception("No active user")

                val blossom = NDKBlossom(ndk, signer)
                val builder = ImageBuilder().caption(state.value.caption)

                // Upload each image with progress tracking
                state.value.selectedImages.forEachIndexed { index, img ->
                    val blob = blossom.upload(
                        file = img.file,
                        mimeType = img.mimeType,
                        options = BlossomUploadOptions(fallbackServer = DEFAULT_FALLBACK_SERVER)
                    )

                    builder.addImage(
                        blob = blob,
                        blurhash = img.blurhash,
                        dimensions = img.dimensions,
                        alt = img.alt
                    )

                    // Update progress
                    val progress = (index + 1).toFloat() / state.value.selectedImages.size
                    _state.update { it.copy(uploadProgress = progress) }
                }

                // Build and publish event
                val imageEvent = builder.build(signer)
                ndk.publish(imageEvent as io.nostr.ndk.models.NDKEvent)

                // Clean up temp files
                state.value.selectedImages.forEach { it.file.delete() }

                _state.update {
                    it.copy(
                        isUploading = false,
                        uploaded = true,
                        uploadProgress = 1f
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isUploading = false,
                        error = e.message ?: "Upload failed"
                    )
                }
            }
        }
    }

    fun reset() {
        _state.value = ImageUploadState()
    }
}
