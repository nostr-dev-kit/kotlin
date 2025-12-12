package com.example.chirp.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.example.chirp.features.onboarding.OnboardingScreen
import com.example.chirp.features.home.HomeScreen
import com.example.chirp.features.compose.ComposeScreen
import com.example.chirp.features.thread.ThreadScreen
import com.example.chirp.features.profile.ProfileScreen
import com.example.chirp.features.search.SearchScreen
import com.example.chirp.features.settings.SettingsScreen
import com.example.chirp.features.debug.DebugScreen

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
            HomeScreen(
                onNavigateToCompose = { replyTo ->
                    navController.navigate(Routes.Compose.createRoute(replyTo))
                },
                onNavigateToThread = { eventId ->
                    navController.navigate(Routes.Thread.createRoute(eventId))
                },
                onNavigateToProfile = { pubkey ->
                    navController.navigate(Routes.Profile.createRoute(pubkey))
                },
                onNavigateToSearch = {
                    navController.navigate(Routes.Search.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.Settings.route)
                }
            )
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

        composable(Routes.Search.route) {
            SearchScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToThread = { eventId ->
                    navController.navigate(Routes.Thread.createRoute(eventId))
                },
                onNavigateToProfile = { pubkey ->
                    navController.navigate(Routes.Profile.createRoute(pubkey))
                }
            )
        }

        composable(Routes.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToDebug = {
                    navController.navigate(Routes.Debug.route)
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
    }
}
