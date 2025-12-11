package com.example.chirp.features.images.upload

import java.io.File

data class ImageUploadState(
    val selectedImages: List<ProcessedImage> = emptyList(),
    val caption: String = "",
    val isProcessing: Boolean = false,
    val isUploading: Boolean = false,
    val uploadProgress: Float = 0f,
    val uploaded: Boolean = false,
    val error: String? = null
)

data class ProcessedImage(
    val file: File,
    val blurhash: String,
    val dimensions: Pair<Int, Int>,
    val mimeType: String,
    val alt: String? = null
)
