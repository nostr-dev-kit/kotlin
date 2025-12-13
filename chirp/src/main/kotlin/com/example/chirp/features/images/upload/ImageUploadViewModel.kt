package com.example.chirp.features.images.upload

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.nostr.ndk.NDK
import io.nostr.ndk.blossom.BlossomClient
import io.nostr.ndk.builders.ImageBuilder
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject

@HiltViewModel
class ImageUploadViewModel @Inject constructor(
    private val ndk: NDK,
    private val blossomClient: BlossomClient,
    private val imageLoader: ImageLoader
) : ViewModel() {

    private val _state = MutableStateFlow(ImageUploadState())
    val state: StateFlow<ImageUploadState> = _state.asStateFlow()

    fun onImagesSelected(uris: List<Uri>) {
        viewModelScope.launch {
            _state.update { it.copy(isProcessing = true, error = null) }

            try {
                val processed = supervisorScope {
                    uris.map { uri ->
                        async { imageLoader.loadAndResizeImage(uri) }
                    }.awaitAll()
                }

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


    fun onCaptionChanged(caption: String) {
        _state.update { it.copy(caption = caption) }
    }

    fun onPublish() {
        viewModelScope.launch {
            _state.update { it.copy(isUploading = true, uploadProgress = 0f, error = null) }

            try {
                val signer = ndk.currentUser.value?.signer
                    ?: throw Exception("No active user")

                val builder = ImageBuilder().caption(state.value.caption)

                // Upload each image with progress tracking
                state.value.selectedImages.forEachIndexed { index, img ->
                    val uploadResult = blossomClient.upload(
                        file = img.file,
                        signer = signer,
                        mimeType = img.mimeType
                    ).getOrThrow()

                    builder.addImage(
                        uploadResult = uploadResult,
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
                // NDKImage is a wrapper around NDKEvent, so we can cast it
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
