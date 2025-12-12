package io.nostr.ndk.compose.content.renderer.mentions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.nostr.ndk.NDK
import io.nostr.ndk.content.ContentSegment
import io.nostr.ndk.user.user

/**
 * Basic renderer for user mentions.
 * Shows avatar + display name, loads profile data.
 *
 * Design:
 * - Small avatar (20dp) + name
 * - Primary color for mention styling
 * - Clickable with ripple effect
 * - Loading state shows pubkey truncated
 */
@Composable
fun BasicMentionRenderer(
    ndk: NDK,
    segment: ContentSegment.Mention,
    onClick: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
    showAvatar: Boolean = true
) {
    val user = remember(segment.pubkey) { ndk.user(segment.pubkey) }
    val profile by user.profile.collectAsState()

    LaunchedEffect(segment.pubkey) {
        user.fetchProfile()
    }

    val displayName = profile?.name ?: "${segment.pubkey.take(8)}..."

    Surface(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .then(
                if (onClick != null) {
                    Modifier.clickable { onClick(segment.pubkey) }
                } else Modifier
            ),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (showAvatar) {
                AsyncImage(
                    model = profile?.picture,
                    contentDescription = "Avatar for $displayName",
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                )
            }

            Text(
                text = "@$displayName",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
