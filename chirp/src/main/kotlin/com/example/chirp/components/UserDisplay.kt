package com.example.chirp.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.nostr.ndk.NDK
import io.nostr.ndk.user.user

/**
 * Displays a user's display name with automatic profile resolution.
 * Shows displayName > name > truncated pubkey as fallback.
 *
 * Usage:
 * ```
 * UserDisplayName(
 *     pubkey = note.pubkey,
 *     ndk = ndk,
 *     style = MaterialTheme.typography.titleSmall
 * )
 * ```
 */
@Composable
fun UserDisplayName(
    pubkey: String,
    ndk: NDK,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleSmall
) {
    val user = remember(pubkey) { ndk.user(pubkey) }
    val profile by user.profile.collectAsState()

    LaunchedEffect(pubkey) {
        user.fetchProfile()
    }

    Text(
        text = profile?.bestName ?: "${pubkey.take(8)}...",
        style = style,
        modifier = modifier
    )
}

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
    size: androidx.compose.ui.unit.Dp = 40.dp
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

/**
 * Displays user avatar and display name together (common pattern).
 *
 * Usage:
 * ```
 * UserInfo(
 *     pubkey = note.pubkey,
 *     ndk = ndk,
 *     avatarSize = 32.dp,
 *     nameStyle = MaterialTheme.typography.bodyMedium
 * )
 * ```
 */
@Composable
fun UserInfo(
    pubkey: String,
    ndk: NDK,
    modifier: Modifier = Modifier,
    avatarSize: androidx.compose.ui.unit.Dp = 32.dp,
    nameStyle: TextStyle = MaterialTheme.typography.bodyMedium
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        UserAvatar(
            pubkey = pubkey,
            ndk = ndk,
            size = avatarSize
        )
        UserDisplayName(
            pubkey = pubkey,
            ndk = ndk,
            style = nameStyle
        )
    }
}
