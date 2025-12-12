package com.example.chirp.features.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chirp.ui.theme.ChirpColors
import com.example.chirp.util.formatRelativeTime
import io.nostr.ndk.compose.user.UserAvatar
import io.nostr.ndk.compose.user.UserDisplayName
import io.nostr.ndk.compose.content.ContentCallbacks
import io.nostr.ndk.compose.content.RenderedContent
import io.nostr.ndk.compose.reactions.ReactionButton
import io.nostr.ndk.compose.reactions.ReplyButton
import io.nostr.ndk.NDK
import io.nostr.ndk.models.NDKEvent

@Composable
fun NoteCard(
    note: NDKEvent,
    ndk: NDK,
    onReply: (String) -> Unit,
    onNoteClick: (String) -> Unit,
    onProfileClick: (String) -> Unit,
    onHashtagClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onNoteClick(note.id) }
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        // Avatar + Name row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatar(
                pubkey = note.pubkey,
                ndk = ndk,
                size = 40.dp,
                modifier = Modifier.clickable { onProfileClick(note.pubkey) }
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                UserDisplayName(
                    pubkey = note.pubkey,
                    ndk = ndk,
                    style = LocalTextStyle.current.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    modifier = Modifier.clickable { onProfileClick(note.pubkey) }
                )

                Text(
                    text = "·",
                    style = LocalTextStyle.current.copy(
                        fontSize = 13.sp,
                        color = ChirpColors.TertiaryText
                    )
                )

                Text(
                    text = formatRelativeTime(note.createdAt),
                    style = LocalTextStyle.current.copy(
                        fontSize = 13.sp,
                        color = ChirpColors.TertiaryText
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Content - full width
        RenderedContent(
            ndk = ndk,
            event = note,
            callbacks = ContentCallbacks(
                onUserClick = onProfileClick,
                onHashtagClick = onHashtagClick,
                onLinkClick = { url -> },
                onMediaClick = { urls, index -> }
            ),
            textStyle = LocalTextStyle.current.copy(
                fontSize = 15.sp,
                lineHeight = 22.5.sp
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Action buttons - full width
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReplyButton(
                event = note,
                onReply = onReply,
                color = ChirpColors.TertiaryText
            )

            ReactionButton(
                ndk = ndk,
                event = note,
                inactiveColor = ChirpColors.TertiaryText
            )
        }
    }

    // Bottom border
    HorizontalDivider(
        thickness = 1.dp,
        color = ChirpColors.Border
    )
}

