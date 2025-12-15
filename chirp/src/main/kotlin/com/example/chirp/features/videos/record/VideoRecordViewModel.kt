package com.example.chirp.features.videos.record

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.nostr.ndk.NDK
import io.nostr.ndk.blossom.BlossomUploadOptions
import io.nostr.ndk.blossom.NDKBlossom
import io.nostr.ndk.builders.VideoBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

const val MAX_VIDEO_DURATION_SECONDS = 6
private const val DEFAULT_FALLBACK_SERVER = "https://blossom.primal.net"

@HiltViewModel
class VideoRecordViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ndk: NDK
) : ViewModel() {

    private val _state = MutableStateFlow(VideoRecordState())
    val state: StateFlow<VideoRecordState> = _state.asStateFlow()

    private var recordingTimer: Job? = null

    fun onRecordingStarted() {
        _state.update { it.copy(recordingState = RecordingState.Recording(0), error = null) }
        startRecordingTimer()
    }

    private fun startRecordingTimer() {
        recordingTimer?.cancel()
        recordingTimer = viewModelScope.launch {
            var seconds = 0
            while (seconds < MAX_VIDEO_DURATION_SECONDS) {
                delay(1000)
                seconds++
                _state.update {
                    if (it.recordingState is RecordingState.Recording) {
                        it.copy(recordingState = RecordingState.Recording(seconds))
                    } else it
                }
            }
            // Auto-stop at 6 seconds
            _state.update { it.copy(recordingState = RecordingState.Stopped) }
        }
    }

    fun onRecordingComplete(videoUri: Uri) {
        recordingTimer?.cancel()
        viewModelScope.launch {
            _state.update { it.copy(recordingState = RecordingState.Stopped) }
            processVideo(videoUri)
        }
    }

    fun onRecordingStopped() {
        recordingTimer?.cancel()
        _state.update { it.copy(recordingState = RecordingState.Stopped) }
    }

    private suspend fun processVideo(uri: Uri) = withContext(Dispatchers.IO) {
        try {
            // Copy to local file
            val videoFile = File(context.cacheDir, "video_${System.currentTimeMillis()}.mp4")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(videoFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw Exception("Failed to read video")

            // Extract metadata
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(videoFile.path)

            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            val duration = (durationMs / 1000).toInt().coerceAtMost(MAX_VIDEO_DURATION_SECONDS)

            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1080
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1920

            // Extract thumbnail
            val thumbnailFile = File(context.cacheDir, "thumb_${System.currentTimeMillis()}.jpg")
            val thumbnail = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            thumbnail?.let { bmp ->
                FileOutputStream(thumbnailFile).use { output ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 85, output)
                }
                bmp.recycle()
            }

            retriever.release()

            _state.update {
                it.copy(
                    recordedVideo = RecordedVideo(
                        file = videoFile,
                        duration = duration,
                        dimensions = width to height,
                        mimeType = "video/mp4",
                        thumbnailFile = if (thumbnailFile.exists()) thumbnailFile else null
                    )
                )
            }
        } catch (e: Exception) {
            _state.update { it.copy(error = "Failed to process video: ${e.message}") }
        }
    }

    fun onCaptionChanged(caption: String) {
        _state.update { it.copy(caption = caption) }
    }

    fun onPublish() {
        val video = state.value.recordedVideo ?: return

        viewModelScope.launch {
            _state.update { it.copy(isUploading = true, uploadProgress = 0f, error = null) }

            try {
                val signer = ndk.currentUser.value?.signer
                    ?: throw Exception("No active user")

                val blossom = NDKBlossom(ndk, signer)
                val uploadOptions = BlossomUploadOptions(fallbackServer = DEFAULT_FALLBACK_SERVER)

                // Upload video
                _state.update { it.copy(uploadProgress = 0.2f) }
                val videoBlob = blossom.upload(
                    file = video.file,
                    mimeType = video.mimeType,
                    options = uploadOptions
                )

                _state.update { it.copy(uploadProgress = 0.6f) }

                // Upload thumbnail if available
                val thumbnailUrl = video.thumbnailFile?.let { thumbFile ->
                    try {
                        blossom.upload(
                            file = thumbFile,
                            mimeType = "image/jpeg",
                            options = uploadOptions
                        ).url
                    } catch (e: Exception) {
                        null // Thumbnail upload is optional
                    }
                }

                _state.update { it.copy(uploadProgress = 0.8f) }

                // Build and publish event
                val builder = VideoBuilder()
                    .video(
                        blob = videoBlob,
                        duration = video.duration,
                        dimensions = video.dimensions
                    )
                    .caption(state.value.caption)

                thumbnailUrl?.let { builder.thumbnail(it) }

                val videoEvent = builder.build(signer)
                ndk.publish(videoEvent)

                // Cleanup
                video.file.delete()
                video.thumbnailFile?.delete()

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

    fun onRetry() {
        _state.update { it.copy(recordedVideo = null, recordingState = RecordingState.Idle, error = null) }
    }

    fun reset() {
        recordingTimer?.cancel()
        state.value.recordedVideo?.file?.delete()
        state.value.recordedVideo?.thumbnailFile?.delete()
        _state.value = VideoRecordState()
    }

    override fun onCleared() {
        super.onCleared()
        recordingTimer?.cancel()
    }
}
