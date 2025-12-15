package com.example.chirp.features.videos.record

import java.io.File

data class VideoRecordState(
    val recordingState: RecordingState = RecordingState.Idle,
    val recordedVideo: RecordedVideo? = null,
    val caption: String = "",
    val isUploading: Boolean = false,
    val uploadProgress: Float = 0f,
    val uploaded: Boolean = false,
    val error: String? = null
)

sealed class RecordingState {
    data object Idle : RecordingState()
    data class Recording(val elapsedSeconds: Int) : RecordingState()
    data object Stopped : RecordingState()
}

data class RecordedVideo(
    val file: File,
    val duration: Int, // seconds
    val dimensions: Pair<Int, Int>, // width x height
    val mimeType: String,
    val thumbnailFile: File?
)
