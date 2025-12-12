package com.example.chirp.features.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.chirp.features.home.components.NoteCard
import com.example.chirp.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToThread: (String) -> Unit = {},
    onNavigateToProfile: (String) -> Unit = {},
    onNavigateToSearch: (String?) -> Unit = {},
    onNavigateToCompose: (String?) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = { viewModel.onQueryChange(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search notes...") },
                        singleLine = true,
                        trailingIcon = {
                            if (state.query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onQueryChange("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear"
                                    )
                                }
                            }
                        }
                    )
                },
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
                state.query.isBlank() -> {
                    Text(
                        text = "Enter a search query to find notes",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(Spacing.lg),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                state.error != null -> {
                    Text(
                        text = state.error ?: "Unknown error",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(Spacing.lg)
                    )
                }

                state.results.isEmpty() && !state.isSearching -> {
                    Text(
                        text = "No results found for \"${state.query}\"",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(Spacing.lg),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
                    ) {
                        item {
                            Text(
                                text = "${state.results.size} results",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        items(
                            items = state.results,
                            key = { it.id }
                        ) { note ->
                            NoteCard(
                                note = note,
                                ndk = viewModel.ndk,
                                onReply = { eventId -> onNavigateToCompose(eventId) },
                                onNoteClick = { eventId -> onNavigateToThread(eventId) },
                                onProfileClick = onNavigateToProfile,
                                onHashtagClick = { tag -> onNavigateToSearch(tag) }
                            )
                        }
                    }
                }
            }
        }
    }
}
