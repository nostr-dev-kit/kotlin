package io.nostr.ndk.compose.user

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.nostr.ndk.NDK

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
    avatarSize: Dp = 32.dp,
    nameStyle: TextStyle = MaterialTheme.typography.bodyMedium
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
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
