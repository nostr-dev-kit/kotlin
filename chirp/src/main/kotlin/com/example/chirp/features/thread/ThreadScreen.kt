package com.example.chirp.features.thread

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.chirp.features.home.components.NoteCard
import com.example.chirp.ui.theme.Spacing
import io.nostr.ndk.models.NDKEvent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(
    viewModel: ThreadViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToProfile: (String) -> Unit = {},
    onNavigateToThread: (String) -> Unit = {},
    onNavigateToSearch: (String?) -> Unit = {},
    onNavigateToCompose: (String?) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Thread") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                state.error != null -> {
                    Text(
                        text = state.error ?: "Unknown error",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(Spacing.lg)
                    )
                }

                state.mainEvent != null -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
                    ) {
                        // Main event
                        item {
                            NoteCard(
                                note = state.mainEvent!!,
                                ndk = viewModel.ndk,
                                onReply = { eventId -> onNavigateToCompose(eventId) },
                                onNoteClick = { },
                                onProfileClick = onNavigateToProfile,
                                onHashtagClick = { tag -> onNavigateToSearch(tag) }
                            )
                        }

                        // Replies header
                        if (state.replies.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Replies (${state.replies.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(vertical = Spacing.sm)
                                )
                            }
                        }

                        // Replies
                        items(
                            items = state.replies,
                            key = { it.id }
                        ) { reply ->
                            NoteCard(
                                note = reply,
                                ndk = viewModel.ndk,
                                onReply = { eventId -> onNavigateToCompose(eventId) },
                                onNoteClick = { eventId -> onNavigateToThread(eventId) },
                                onProfileClick = onNavigateToProfile,
                                onHashtagClick = { tag -> onNavigateToSearch(tag) }
                            )
                        }
                    }
                }

                else -> {
                    Text(
                        text = "Event not found",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(Spacing.lg)
                    )
                }
            }
        }
    }
}
