package io.nostr.ndk.compose.reactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.nostr.ndk.NDK
import io.nostr.ndk.builders.ReactionBuilder
import io.nostr.ndk.models.NDKEvent
import io.nostr.ndk.models.NDKFilter
import io.nostr.ndk.nips.KIND_REACTION
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * A composable button for reacting to Nostr events.
 *
 * Automatically fetches the current user's reaction status and reaction count.
 * Handles publishing reactions when clicked.
 *
 * @param ndk The NDK instance
 * @param event The event to react to
 * @param emoji The reaction emoji (default "+")
 * @param modifier Modifier for the button
 * @param iconSize Size of the icon
 * @param showCount Whether to show the reaction count
 * @param activeIcon Icon when user has reacted
 * @param inactiveIcon Icon when user hasn't reacted
 * @param activeColor Color when user has reacted
 * @param inactiveColor Color when user hasn't reacted
 * @param textStyle Style for the count text
 */
@Composable
fun ReactionButton(
    ndk: NDK,
    event: NDKEvent,
    emoji: String = "+",
    modifier: Modifier = Modifier,
    iconSize: Dp = 18.dp,
    showCount: Boolean = false,
    activeIcon: ImageVector = Icons.Filled.Favorite,
    inactiveIcon: ImageVector = Icons.Filled.FavoriteBorder,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = LocalContentColor.current.copy(alpha = 0.6f),
    textStyle: TextStyle = LocalTextStyle.current
) {
    val scope = rememberCoroutineScope()
    val signer = ndk.signer
    val userPubkey = signer?.pubkey

    var hasReacted by remember { mutableStateOf(false) }
    var reactionCount by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }

    // Fetch reactions for this event
    val reactions by remember(event.id) {
        val filter = NDKFilter(
            kinds = setOf(KIND_REACTION),
            tags = mapOf("e" to setOf(event.id))
        )
        ndk.subscribe(filter).events
    }.collectAsState(initial = null)

    // Update state when reactions change
    reactions?.let { reaction ->
        if (reaction.pubkey == userPubkey) {
            hasReacted = true
        }
        reactionCount++
    }

    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = signer != null && !isLoading) {
                if (hasReacted) return@clickable

                scope.launch {
                    isLoading = true
                    try {
                        signer?.let { s ->
                            val reaction = ReactionBuilder()
                                .target(event.id, event.pubkey, event.kind)
                                .emoji(emoji)
                                .build(s)
                            ndk.publish(reaction)
                            hasReacted = true
                            reactionCount++
                        }
                    } finally {
                        isLoading = false
                    }
                }
            }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (hasReacted) activeIcon else inactiveIcon,
            contentDescription = if (hasReacted) "Liked" else "Like",
            modifier = Modifier.size(iconSize),
            tint = if (hasReacted) activeColor else inactiveColor
        )

        if (showCount && reactionCount > 0) {
            Text(
                text = reactionCount.toString(),
                style = textStyle,
                color = if (hasReacted) activeColor else inactiveColor
            )
        }
    }
}
