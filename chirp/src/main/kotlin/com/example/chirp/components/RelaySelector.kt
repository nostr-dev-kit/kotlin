package com.example.chirp.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.chirp.models.RelayFilterMode
import io.nostr.ndk.relay.NDKRelay
import io.nostr.ndk.relay.nip11.Nip11RelayInformation

/**
 * Relay selector dropdown for feed filtering.
 *
 * @param currentMode The currently selected relay filter mode
 * @param connectedRelays List of connected relays to show in dropdown
 * @param defaultLabel Label to show when in AllRelays mode (e.g., "Feed", "Images")
 * @param onModeSelected Callback when a relay filter mode is selected
 */
@Composable
fun RelaySelector(
    currentMode: RelayFilterMode,
    connectedRelays: Set<NDKRelay>,
    defaultLabel: String,
    onModeSelected: (RelayFilterMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDropdown by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.clickable { showDropdown = true }
        ) {
            when (currentMode) {
                is RelayFilterMode.AllRelays -> {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    )
                    Text(
                        defaultLabel,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                is RelayFilterMode.SingleRelay -> {
                    RelayTitleContent(relay = currentMode.relay)
                }
            }
        }

        DropdownMenu(
            expanded = showDropdown,
            onDismissRequest = { showDropdown = false }
        ) {
            DropdownMenuItem(
                text = { Text(defaultLabel) },
                onClick = {
                    onModeSelected(RelayFilterMode.AllRelays)
                    showDropdown = false
                },
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    )
                }
            )

            if (connectedRelays.isNotEmpty()) {
                HorizontalDivider()

                connectedRelays.forEach { relay ->
                    RelayDropdownItem(
                        relay = relay,
                        onClick = {
                            onModeSelected(RelayFilterMode.SingleRelay(relay))
                            showDropdown = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RelayTitleContent(relay: NDKRelay) {
    val nip11Data by produceState<Nip11RelayInformation?>(
        initialValue = relay.nip11Info,
        key1 = relay.url
    ) {
        if (relay.nip11Info == null) {
            val result = relay.fetchNip11Info()
            value = result.getOrNull()
        }
    }

    val iconUrl = nip11Data?.icon
    val name = nip11Data?.name ?: relay.url.removePrefix("wss://").removePrefix("ws://")

    if (iconUrl != null) {
        AsyncImage(
            model = iconUrl,
            contentDescription = "Relay icon",
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
        )
    } else {
        Icon(
            imageVector = Icons.Default.Cloud,
            contentDescription = "Relay",
            modifier = Modifier.size(32.dp)
        )
    }

    Text(
        text = name,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun RelayDropdownItem(
    relay: NDKRelay,
    onClick: () -> Unit
) {
    val nip11Data by produceState<Nip11RelayInformation?>(
        initialValue = relay.nip11Info,
        key1 = relay.url
    ) {
        if (relay.nip11Info == null) {
            val result = relay.fetchNip11Info()
            value = result.getOrNull()
        }
    }

    val iconUrl = nip11Data?.icon
    val name = nip11Data?.name ?: relay.url.removePrefix("wss://").removePrefix("ws://")

    DropdownMenuItem(
        text = { Text(name) },
        onClick = onClick,
        leadingIcon = {
            if (iconUrl != null) {
                AsyncImage(
                    model = iconUrl,
                    contentDescription = "Relay icon",
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = "Relay",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    )
}
