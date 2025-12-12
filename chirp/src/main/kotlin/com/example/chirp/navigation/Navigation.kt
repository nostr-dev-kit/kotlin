package com.example.chirp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.example.chirp.features.onboarding.OnboardingScreen
import com.example.chirp.features.main.MainScreen
import com.example.chirp.features.compose.ComposeScreen
import com.example.chirp.features.thread.ThreadScreen
import com.example.chirp.features.profile.ProfileScreen
import com.example.chirp.features.search.SearchScreen
import com.example.chirp.features.settings.SettingsScreen
import com.example.chirp.features.debug.DebugScreen
import com.example.chirp.features.debug.DeveloperToolsScreen
import com.example.chirp.features.debug.RelayMonitorScreen
import com.example.chirp.features.debug.NostrDBStatsScreen
import com.example.chirp.features.debug.SubscriptionsScreen
import com.example.chirp.features.settings.ContentRenderSettingsScreen
import com.example.chirp.features.images.ImageDetailScreen
import com.example.chirp.features.images.ImageFeedViewModel
import com.example.chirp.features.images.upload.ImageUploadScreen
import com.example.chirp.features.videos.record.VideoRecordScreen
import com.example.chirp.features.settings.blossom.BlossomSettingsScreen

@Composable
fun ChirpNavigation(
    navController: NavHostController,
    startDestination: String = Routes.Onboarding.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.Onboarding.route) {
            OnboardingScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.Home.route) {
                        popUpTo(Routes.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.Home.route) {
            MainScreen(navController = navController)
        }

        composable(
            route = Routes.Compose.route,
            arguments = listOf(
                navArgument("replyTo") {
                    type = NavType.StringType
                    nullable = true
                }
            )
        ) {
            ComposeScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Routes.Thread.route,
            arguments = listOf(
                navArgument("eventId") {
                    type = NavType.StringType
                }
            )
        ) {
            ThreadScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToProfile = { pubkey ->
                    navController.navigate(Routes.Profile.createRoute(pubkey))
                },
                onNavigateToThread = { eventId ->
                    navController.navigate(Routes.Thread.createRoute(eventId))
                },
                onNavigateToSearch = { hashtag ->
                    navController.navigate(Routes.Search.createRoute(hashtag))
                },
                onNavigateToCompose = { replyTo ->
                    navController.navigate(Routes.Compose.createRoute(replyTo))
                }
            )
        }

        composable(
            route = Routes.Profile.route,
            arguments = listOf(
                navArgument("pubkey") {
                    type = NavType.StringType
                }
            )
        ) {
            ProfileScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToThread = { eventId ->
                    navController.navigate(Routes.Thread.createRoute(eventId))
                }
            )
        }

        composable(
            route = Routes.Search.route,
            arguments = listOf(
                navArgument("hashtag") {
                    type = NavType.StringType
                    nullable = true
                }
            )
        ) {
            SearchScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToThread = { eventId ->
                    navController.navigate(Routes.Thread.createRoute(eventId))
                },
                onNavigateToProfile = { pubkey ->
                    navController.navigate(Routes.Profile.createRoute(pubkey))
                },
                onNavigateToSearch = { hashtag ->
                    navController.navigate(Routes.Search.createRoute(hashtag))
                },
                onNavigateToCompose = { replyTo ->
                    navController.navigate(Routes.Compose.createRoute(replyTo))
                }
            )
        }

        composable(Routes.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToDeveloperTools = {
                    navController.navigate(Routes.DeveloperTools.route)
                },
                onNavigateToContentRendererSettings = {
                    navController.navigate(Routes.ContentRendererSettings.route)
                },
                onNavigateToRelaySettings = {
                    navController.navigate(Routes.RelaySettings.route)
                },
                onNavigateToBlossomSettings = {
                    navController.navigate(Routes.BlossomSettings.route)
                },
                onLogout = {
                    navController.navigate(Routes.Onboarding.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.Debug.route) {
            DebugScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.DeveloperTools.route) {
            DeveloperToolsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToRelayMonitor = {
                    navController.navigate(Routes.RelayMonitor.route)
                },
                onNavigateToNostrDBStats = {
                    navController.navigate(Routes.NostrDBStats.route)
                },
                onNavigateToSubscriptions = {
                    navController.navigate(Routes.Subscriptions.route)
                },
                onNavigateToOutboxMetrics = {
                    navController.navigate(Routes.Debug.route)
                }
            )
        }

        composable(Routes.RelayMonitor.route) {
            RelayMonitorScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.NostrDBStats.route) {
            NostrDBStatsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.Subscriptions.route) {
            SubscriptionsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.ContentRendererSettings.route) {
            val viewModel: com.example.chirp.features.settings.ContentRenderSettingsViewModel = hiltViewModel()
            val settings by viewModel.settings.collectAsState()

            ContentRenderSettingsScreen(
                settings = settings,
                onSettingsChanged = { newSettings ->
                    viewModel.updateSettings(newSettings)
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.RelaySettings.route) {
            com.example.chirp.features.settings.relay.RelaySettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Routes.ImageDetail.route,
            arguments = listOf(
                navArgument("galleryId") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val galleryId = backStackEntry.arguments?.getString("galleryId") ?: return@composable

            // Get the shared ViewModel from the Home route's back stack entry
            val viewModel: ImageFeedViewModel = hiltViewModel(
                viewModelStoreOwner = remember(backStackEntry) {
                    navController.getBackStackEntry(Routes.Home.route)
                }
            )

            ImageDetailScreen(
                galleryId = galleryId,
                viewModel = viewModel,
                onDismiss = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.ImageUpload.route) {
            ImageUploadScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.VideoRecord.route) {
            VideoRecordScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.BlossomSettings.route) {
            BlossomSettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
