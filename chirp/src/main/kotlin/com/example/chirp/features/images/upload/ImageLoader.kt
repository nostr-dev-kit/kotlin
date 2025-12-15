package com.example.chirp.features.images.upload

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.chirp.core.DispatchersProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

interface ImageLoader {
    suspend fun loadAndResizeImage(uri: Uri): ProcessedImage
}

class AndroidImageLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatchersProvider
) : ImageLoader {

    override suspend fun loadAndResizeImage(uri: Uri): ProcessedImage = withContext(dispatchers.io) {
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
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ImageLoaderModule {
    @Binds
    @Singleton
    abstract fun bindImageLoader(impl: AndroidImageLoader): ImageLoader
}
