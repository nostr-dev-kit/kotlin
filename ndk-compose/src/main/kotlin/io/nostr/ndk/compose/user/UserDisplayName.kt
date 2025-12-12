package io.nostr.ndk.compose.user

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
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
