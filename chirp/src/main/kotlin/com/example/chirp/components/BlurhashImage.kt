package com.example.chirp.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.vanniktech.blurhash.BlurHash

/**
 * Composable for loading images with blurhash placeholder support.
 *
 * Shows a decoded blurhash as placeholder while the actual image loads,
 * providing a smooth visual experience.
 */
@Composable
fun BlurhashImage(
    url: String?,
    blurhash: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    cacheKey: String? = null
) {
    val blurhashBitmap = remember(blurhash) {
        blurhash?.let { hash ->
            try {
                BlurHash.decode(hash, 32, 32)
            } catch (e: Exception) {
                null
            }
        }
    }

    Box(modifier = modifier) {
        // Show blurhash placeholder first
        blurhashBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = contentScale
            )
        }

        // Load actual image on top
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(url)
                .crossfade(true)
                .apply {
                    cacheKey?.let {
                        memoryCacheKey(it)
                        diskCacheKey(it)
                    }
                }
                .build(),
            contentDescription = contentDescription,
            modifier = Modifier.matchParentSize(),
            contentScale = contentScale
        )
    }
}
