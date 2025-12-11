package com.example.chirp.features.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.chirp.features.home.components.NoteCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToCompose: (String?) -> Unit,
    onNavigateToThread: (String) -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chirp") },
                actions = {
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(Icons.Default.Search, "Search")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigateToCompose(null) }) {
                Icon(Icons.Default.Add, "Compose")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tabs
            TabRow(selectedTabIndex = state.selectedTab.ordinal) {
                Tab(
                    selected = state.selectedTab == FeedTab.FOLLOWING,
                    onClick = { viewModel.onIntent(HomeIntent.SwitchTab(FeedTab.FOLLOWING)) },
                    text = { Text("Following") }
                )
                Tab(
                    selected = state.selectedTab == FeedTab.GLOBAL,
                    onClick = { viewModel.onIntent(HomeIntent.SwitchTab(FeedTab.GLOBAL)) },
                    text = { Text("Global") }
                )
                Tab(
                    selected = state.selectedTab == FeedTab.NOTIFICATIONS,
                    onClick = { viewModel.onIntent(HomeIntent.SwitchTab(FeedTab.NOTIFICATIONS)) },
                    text = { Text("Notifications") }
                )
            }

            // Feed
            Box(modifier = Modifier.fillMaxSize()) {
                if (state.notes.isEmpty() && !state.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No notes yet")
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
                                onProfileClick = onNavigateToProfile
                            )
                        }
                    }
                }

                if (state.isLoading && state.notes.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
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
