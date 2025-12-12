package io.nostr.ndk.compose.reactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.nostr.ndk.models.NDKEvent

/**
 * A composable button for replying to Nostr events.
 *
 * @param event The event to reply to
 * @param onReply Callback with the event ID when clicked
 * @param modifier Modifier for the button
 * @param iconSize Size of the icon
 * @param replyCount Optional count of replies to display
 * @param icon Icon to display
 * @param color Color for the icon and text
 * @param textStyle Style for the count text
 */
@Composable
fun ReplyButton(
    event: NDKEvent,
    onReply: (String) -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 18.dp,
    replyCount: Int? = null,
    icon: ImageVector = Icons.AutoMirrored.Filled.Reply,
    color: Color = LocalContentColor.current.copy(alpha = 0.6f),
    textStyle: TextStyle = LocalTextStyle.current
) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable { onReply(event.id) }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Reply",
            modifier = Modifier.size(iconSize),
            tint = color
        )

        replyCount?.let { count ->
            if (count > 0) {
                Text(
                    text = count.toString(),
                    style = textStyle,
                    color = color
                )
            }
        }
    }
}
