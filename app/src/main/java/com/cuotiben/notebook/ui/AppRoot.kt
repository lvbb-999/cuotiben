package com.cuotiben.notebook.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cuotiben.notebook.ui.capture.CameraScreen
import com.cuotiben.notebook.ui.capture.SelectQuestionsScreen
import com.cuotiben.notebook.ui.detail.DetailScreen
import com.cuotiben.notebook.ui.home.HomeScreen
import com.cuotiben.notebook.ui.review.ReviewScreen
import com.cuotiben.notebook.ui.stats.StatsScreen
import com.cuotiben.notebook.ui.voice.VoiceScreen

@Composable
fun AppRoot() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = Modifier.fillMaxSize(),
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onAdd = {
                    navController.navigate(Routes.CAMERA) { launchSingleTop = true }
                },
                onVoice = {
                    navController.navigate(Routes.VOICE) { launchSingleTop = true }
                },
                onOpenDetail = { id ->
                    navController.navigate(Routes.detail(id))
                },
                onNavigate = { route -> navController.navigateTab(route) },
            )
        }
        composable(Routes.REVIEW) {
            ReviewScreen(
                onOpenDetail = { id -> navController.navigate(Routes.detail(id)) },
                onNavigate = { route -> navController.navigateTab(route) },
                onGoHome = { navController.navigateTab(Routes.HOME) },
            )
        }
        composable(Routes.STATS) {
            StatsScreen(
                onNavigate = { route -> navController.navigateTab(route) },
            )
        }
        composable(Routes.CAMERA) {
            CameraScreen(
                onBack = { navController.popBackStack() },
                onPhotoReady = { name ->
                    navController.navigate(Routes.select(name)) {
                        popUpTo(Routes.CAMERA) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.VOICE) {
            VoiceScreen(
                onBack = { navController.popBackStack() },
                onSaved = { id ->
                    navController.navigate(Routes.detail(id)) {
                        popUpTo(Routes.HOME)
                    }
                },
            )
        }
        composable(
            route = Routes.SELECT,
            arguments = listOf(navArgument("name") { type = NavType.StringType }),
        ) { entry ->
            SelectQuestionsScreen(
                fileName = entry.arguments?.getString("name").orEmpty(),
                onBack = { navController.popBackStack() },
                onDone = { navController.popBackStack(Routes.HOME, inclusive = false) },
            )
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            DetailScreen(
                mistakeId = entry.arguments?.getLong("id") ?: 0L,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
