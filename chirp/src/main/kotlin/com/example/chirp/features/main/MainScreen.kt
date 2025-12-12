package com.example.chirp.features.main

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.chirp.features.home.HomeScreen
import com.example.chirp.features.images.ImageFeedScreen
import com.example.chirp.features.videos.VideoFeedScreen
import com.example.chirp.models.ContentType
import com.example.chirp.navigation.Routes

@Composable
fun MainScreen(
    navController: NavHostController,
    viewModel: MainViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        bottomBar = {
            // Hide bottom navigation for Videos tab (full-screen experience like TikTok/Reels)
            if (state.selectedContent != ContentType.Videos) {
                NavigationBar {
                    NavigationBarItem(
                        selected = state.selectedContent == ContentType.TextNotes,
                        onClick = { viewModel.selectContentType(ContentType.TextNotes) },
                        icon = { Icon(Icons.Default.Home, "Text Notes") },
                        label = { Text("Notes") }
                    )
                    NavigationBarItem(
                        selected = state.selectedContent == ContentType.Images,
                        onClick = { viewModel.selectContentType(ContentType.Images) },
                        icon = { Icon(Icons.Default.Collections, "Images") },
                        label = { Text("Images") }
                    )
                    NavigationBarItem(
                        selected = state.selectedContent == ContentType.Videos,
                        onClick = { viewModel.selectContentType(ContentType.Videos) },
                        icon = { Icon(Icons.Default.PlayCircle, "Videos") },
                        label = { Text("Videos") }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = if (state.selectedContent == ContentType.Videos) {
                // Full screen for videos (no padding)
                Modifier.fillMaxSize()
            } else {
                // Normal padding for other tabs
                Modifier.padding(paddingValues)
            }
        ) {
            when (state.selectedContent) {
                ContentType.TextNotes -> {
                    HomeScreen(
                        onNavigateToCompose = { replyTo ->
                            navController.navigate("compose?replyTo=${replyTo ?: ""}")
                        },
                        onNavigateToThread = { eventId ->
                            navController.navigate("thread/$eventId")
                        },
                        onNavigateToProfile = { pubkey ->
                            navController.navigate("profile/$pubkey")
                        },
                        onNavigateToSearch = { hashtag ->
                            navController.navigate(Routes.Search.createRoute(hashtag))
                        },
                        onNavigateToSettings = {
                            navController.navigate("settings")
                        }
                    )
                }
                ContentType.Images -> {
                    ImageFeedScreen(
                        onImageClick = { gallery ->
                            navController.navigate(Routes.ImageDetail.createRoute(gallery.id))
                        },
                        onUploadClick = {
                            navController.navigate(Routes.ImageUpload.route)
                        }
                    )
                }
                ContentType.Videos -> {
                    VideoFeedScreen(
                        onNavigateToProfile = { pubkey ->
                            navController.navigate(Routes.Profile.createRoute(pubkey))
                        }
                    )
                }
            }
        }
    }
}
