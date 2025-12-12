package com.example.chirp.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * Composable for loading images with optional blurhash placeholder support.
 *
 * Note: Blurhash placeholder generation requires the blurhash-kotlin library.
 * Currently placeholders are disabled until the library is available from Maven Central.
 *
 * @param url URL of the image to load
 * @param blurhash Blurhash string for placeholder (currently unused due to library availability)
 * @param contentDescription Accessibility description
 * @param modifier Compose modifier
 * @param contentScale How to scale the image
 * @param cacheKey Optional cache key for Coil
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
        modifier = modifier,
        contentScale = contentScale
    )
}
