package com.example.babycare.navigation

sealed class Screen(val route: String) {
    object Welcome : Screen("welcome")
    object Login : Screen("login")
    object Register : Screen("register")
    object BabyInfoNew : Screen("baby_info_new")
    object BabyInfoEdit : Screen("baby_info_edit/{childId}") {
        const val ARG_CHILD_ID = "childId"

        fun createRoute(childId: String) = "baby_info_edit/$childId"
    }
    object Home : Screen("home")
    object MyChildren : Screen("my_children")
    object Vaccination : Screen("vaccination")
    object Chatbot : Screen("chatbot")
    object Booking : Screen("booking")
    object Notifications : Screen("notifications")
    object Profile : Screen("profile")
}
