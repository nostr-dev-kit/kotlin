package io.nostr.ndk.compose.relay.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.nostr.ndk.compose.relay.*
import io.nostr.ndk.relay.NDKRelay
import io.nostr.ndk.relay.NDKRelayState
import androidx.compose.runtime.collectAsState

/**
 * List-style relay card showing icon, name, URL, status, and stats.
 *
 * Ideal for settings/management screens. Displays comprehensive relay information
 * and includes an optional toggle switch for connect/disconnect.
 *
 * @param relay The relay to display
 * @param onClick Optional click handler for the entire card
 * @param onToggleConnection Optional handler for connection toggle (receives desired state)
 * @param modifier Modifier for the card
 * @param showStats Whether to show statistics (default: true)
 */
@Composable
fun RelayCardList(
    relay: NDKRelay,
    onClick: (() -> Unit)? = null,
    onToggleConnection: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
    showStats: Boolean = true
) {
    RelayRoot(relay = relay, onClick = onClick) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) Modifier.clickable(onClick = onClick)
                    else Modifier
                )
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Relay icon
                RelayIcon(size = 48.dp)

                // Relay info
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Name
                    RelayName()

                    // URL
                    RelayUrl()

                    // Connection status
                    RelayConnectionStatus(showLabel = true)

                    // Statistics (optional)
                    if (showStats) {
                        RelayStats(
                            modifier = Modifier.padding(top = 4.dp),
                            showMessages = true,
                            showTraffic = true,
                            showValidation = false,
                            showLastConnected = false
                        )
                    }
                }

                // Connection toggle switch (optional)
                if (onToggleConnection != null) {
                    val state by relay.state.collectAsState()
                    val isConnected = state == NDKRelayState.CONNECTED ||
                            state == NDKRelayState.AUTHENTICATED

                    Switch(
                        checked = isConnected,
                        onCheckedChange = onToggleConnection
                    )
                }
            }
        }
    }
}
