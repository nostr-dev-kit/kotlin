package com.example.chirp.features.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.nostr.ndk.compose.user.UserDisplayName
import io.nostr.ndk.compose.content.ContentCallbacks
import io.nostr.ndk.compose.content.RenderedContent
import io.nostr.ndk.NDK
import io.nostr.ndk.models.NDKEvent
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun NoteCard(
    note: NDKEvent,
    ndk: NDK,
    onReply: (String) -> Unit,
    onReact: (String, String) -> Unit,
    onNoteClick: (String) -> Unit,
    onProfileClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onNoteClick(note.id) }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Author info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                UserDisplayName(
                    pubkey = note.pubkey,
                    ndk = ndk,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.clickable { onProfileClick(note.pubkey) }
                )

                Text(
                    text = formatTimestamp(note.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Content with rich rendering
            RenderedContent(
                ndk = ndk,
                event = note,
                callbacks = ContentCallbacks(
                    onUserClick = onProfileClick,
                    onHashtagClick = { tag -> /* TODO: Navigate to hashtag feed */ },
                    onLinkClick = { url -> /* TODO: Open browser */ },
                    onMediaClick = { urls, index -> /* TODO: Open gallery */ }
                ),
                textStyle = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                IconButton(onClick = { onReply(note.id) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Reply,
                        contentDescription = "Reply"
                    )
                }

                IconButton(onClick = { onReact(note.id, "+") }) {
                    Icon(
                        imageVector = Icons.Default.FavoriteBorder,
                        contentDescription = "Like"
                    )
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis() / 1000
    val diff = now - timestamp

    return when {
        diff < 60 -> "${diff}s"
        diff < 3600 -> "${diff / 60}m"
        diff < 86400 -> "${diff / 3600}h"
        else -> {
            val date = Date(timestamp * 1000)
            SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
        }
    }
}
