package io.nostr.ndk.compose.user

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.nostr.ndk.NDK
import io.nostr.ndk.user.user

/**
 * Displays a user's avatar image with automatic profile resolution.
 * Shows profile picture or placeholder if not available.
 *
 * Usage:
 * ```
 * UserAvatar(
 *     pubkey = note.pubkey,
 *     ndk = ndk,
 *     size = 40.dp
 * )
 * ```
 */
@Composable
fun UserAvatar(
    pubkey: String,
    ndk: NDK,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    val user = remember(pubkey) { ndk.user(pubkey) }
    val profile by user.profile.collectAsState()

    LaunchedEffect(pubkey) {
        user.fetchProfile()
    }

    AsyncImage(
        model = profile?.picture,
        contentDescription = "Avatar for ${profile?.bestName ?: pubkey.take(8)}",
        modifier = modifier.size(size)
    )
}
