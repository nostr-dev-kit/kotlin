package com.example.chirp.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.example.chirp.features.home.components.NoteCard
import io.nostr.ndk.relay.NDKRelay
import io.nostr.ndk.relay.nip11.Nip11RelayInformation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToCompose: (String?) -> Unit,
    onNavigateToThread: (String) -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToSearch: (String?) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val relayFilterState by viewModel.relayFilterState.collectAsState()
    val connectedRelays by viewModel.ndk.pool.connectedRelays.collectAsState()
    var showRelayDropdown by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.clickable { showRelayDropdown = true }
                        ) {
                            when (val mode = relayFilterState.mode) {
                                is RelayFilterMode.AllRelays -> {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                    )
                                    Text(
                                        "Feed",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                is RelayFilterMode.SingleRelay -> {
                                    RelayTitleContent(relay = mode.relay)
                                }
                            }
                        }

                        DropdownMenu(
                            expanded = showRelayDropdown,
                            onDismissRequest = { showRelayDropdown = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Feed") },
                                onClick = {
                                    viewModel.selectRelayFilter(RelayFilterMode.AllRelays)
                                    showRelayDropdown = false
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
                                            viewModel.selectRelayFilter(RelayFilterMode.SingleRelay(relay))
                                            showRelayDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { /* TODO: Notifications */ }) {
                        Icon(Icons.Default.Notifications, "Notifications")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigateToCompose(null) },
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Default.Add, "Compose")
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                NavigationBarItem(
                    selected = state.selectedTab == FeedTab.NOTES,
                    onClick = { viewModel.onIntent(HomeIntent.SwitchTab(FeedTab.NOTES)) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = "Notes"
                        )
                    },
                    label = { Text("Notes") }
                )
                NavigationBarItem(
                    selected = state.selectedTab == FeedTab.IMAGES,
                    onClick = { viewModel.onIntent(HomeIntent.SwitchTab(FeedTab.IMAGES)) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = "Images"
                        )
                    },
                    label = { Text("Images") }
                )
                NavigationBarItem(
                    selected = state.selectedTab == FeedTab.VIDEOS,
                    onClick = { viewModel.onIntent(HomeIntent.SwitchTab(FeedTab.VIDEOS)) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = "Videos"
                        )
                    },
                    label = { Text("Videos") }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Feed with bgSecondary background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (state.notes.isEmpty() && !state.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No notes yet",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn {
                        items(state.notes, key = { it.id }) { note ->
                            NoteCard(
                                note = note,
                                ndk = viewModel.ndk,
                                onReply = { onNavigateToCompose(it) },
                                onReact = { eventId, emoji ->
                                    viewModel.onIntent(HomeIntent.ReactToNote(eventId, emoji))
                                },
                                onNoteClick = onNavigateToThread,
                                onProfileClick = onNavigateToProfile,
                                onHashtagClick = { tag -> onNavigateToSearch(tag) }
                            )
                        }
                    }
                }

                if (state.isLoading && state.notes.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            state.error?.let { error ->
                Snackbar(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(error)
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
