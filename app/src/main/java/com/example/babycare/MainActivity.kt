package com.example.babycare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.babycare.navigation.BabyBottomNavigation
import com.example.babycare.navigation.Screen
import com.example.babycare.navigation.SetupNavGraph
import com.example.babycare.ui.theme.BackgroundLight
import com.example.babycare.ui.theme.BabyCareTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            BabyCareTheme {
                val navController = rememberNavController()
                val backStackEntry = navController.currentBackStackEntryAsState().value
                val currentRoute = backStackEntry?.destination?.route

                val showBottomBar = currentRoute in listOf(
                    Screen.Home.route,
                    Screen.MyChildren.route,
                    Screen.Vaccination.route,
                    Screen.Chatbot.route,
                    Screen.Booking.route,
                    Screen.Notifications.route,
                    Screen.Profile.route
                )

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = BackgroundLight,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        if (showBottomBar) {
                            BabyBottomNavigation(navController = navController)
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(BackgroundLight)
                            .padding(innerPadding)
                    ) {
                        SetupNavGraph(navController = navController)
                    }
                }
            }
        }
    }
}
