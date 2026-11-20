package com.cuotiben.notebook.ui

import androidx.navigation.NavHostController

object Routes {
    const val HOME = "home"
    const val REVIEW = "review"
    const val STATS = "stats"
    const val CAMERA = "camera"
    const val VOICE = "voice"
    const val SELECT = "select/{name}"
    const val DETAIL = "detail/{id}"

    fun select(name: String): String = "select/$name"
    fun detail(id: Long): String = "detail/$id"
}

fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
