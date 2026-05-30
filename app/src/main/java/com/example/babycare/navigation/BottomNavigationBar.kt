package com.example.babycare.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.babycare.ui.theme.PrimaryBlue
import com.example.babycare.ui.theme.SecondaryBlue
import com.example.babycare.ui.theme.TextSecondary

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem(Screen.Home.route, "Trang chủ", Icons.Filled.Home),
    BottomNavItem(Screen.MyChildren.route, "Trẻ em", Icons.Filled.People),
    BottomNavItem(Screen.Chatbot.route, "Trợ lý AI", Icons.Filled.SmartToy),
    BottomNavItem(Screen.Booking.route, "Lịch hẹn", Icons.Filled.DateRange),
    BottomNavItem(Screen.Profile.route, "Hồ sơ", Icons.Filled.Person),
)

@Composable
fun BabyBottomNavigation(navController: NavHostController) {
    val currentBackStackEntry = navController.currentBackStackEntryAsState().value
    val currentRoute = currentBackStackEntry?.destination?.route

    NavigationBar(
        containerColor = Color(0xFFF1F5F9),
        tonalElevation = 6.dp,
    ) {
        bottomNavItems.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = PrimaryBlue,
                    selectedTextColor = PrimaryBlue,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary,
                    indicatorColor = Color(0xFFE2ECFF)
                )
            )
        }
    }
}

