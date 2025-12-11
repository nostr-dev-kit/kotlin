package com.example.chirp.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.NavType

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
            // Placeholder - will implement later
        }

        composable(Routes.Home.route) {
            // Placeholder - will implement later
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
            // Placeholder - will implement later
        }

        composable(
            route = Routes.Thread.route,
            arguments = listOf(
                navArgument("eventId") {
                    type = NavType.StringType
                }
            )
        ) {
            // Placeholder - will implement later
        }

        composable(
            route = Routes.Profile.route,
            arguments = listOf(
                navArgument("pubkey") {
                    type = NavType.StringType
                }
            )
        ) {
            // Placeholder - will implement later
        }

        composable(Routes.Search.route) {
            // Placeholder - will implement later
        }

        composable(Routes.Settings.route) {
            // Placeholder - will implement later
        }
    }
}
