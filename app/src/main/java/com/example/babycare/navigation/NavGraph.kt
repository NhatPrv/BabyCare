package com.example.babycare.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.babycare.ui.screens.*
import com.example.babycare.viewmodel.BabyViewModel

@Composable
fun SetupNavGraph(navController: NavHostController) {
    val babyViewModel: BabyViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = Screen.Login.route
    ) {
        composable(route = Screen.Welcome.route) {
            WelcomeScreen(onNavigateToInfo = {
                navController.navigate(Screen.BabyInfoNew.route)
            })
        }
        composable(route = Screen.Login.route) {
            LoginScreen(
                viewModel = babyViewModel,
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate(Screen.Register.route)
                }
            )
        }
        composable(route = Screen.Register.route) {
            RegisterScreen(
                viewModel = babyViewModel,
                onRegisterSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.popBackStack()
                }
            )
        }
        composable(route = Screen.BabyInfoNew.route) {
            BabyInfoScreen(
                viewModel = babyViewModel,
                childId = null,
                onNavigateAfterSave = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        composable(
            route = Screen.BabyInfoEdit.route,
            arguments = listOf(navArgument(Screen.BabyInfoEdit.ARG_CHILD_ID) { type = NavType.StringType })
        ) { backStackEntry ->
            val childId = backStackEntry.arguments?.getString(Screen.BabyInfoEdit.ARG_CHILD_ID)
            BabyInfoScreen(
                viewModel = babyViewModel,
                childId = childId,
                onNavigateAfterSave = {
                    navController.popBackStack()
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        composable(route = Screen.Home.route) {
            HomeScreen(
                viewModel = babyViewModel,
                onNavigateToVaccination = { navController.navigate(Screen.Vaccination.route) },
                onNavigateToChatbot = { navController.navigate(Screen.Chatbot.route) },
                onNavigateToBooking = { navController.navigate(Screen.Booking.route) },
                onNavigateToNotifications = { navController.navigate(Screen.Notifications.route) },
                onNavigateToProfile = { navController.navigate(Screen.Profile.route) },
                onNavigateToGeneralVaccines = { navController.navigate(Screen.GeneralVaccines.route) },
                onNavigateToUpcomingVaccines = { navController.navigate(Screen.UpcomingVaccines.route) },
                onAddChild = { navController.navigate(Screen.BabyInfoNew.route) }
            )
        }
        composable(route = Screen.UpcomingVaccines.route) {
            UpcomingVaccinesScreen(
                viewModel = babyViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToBooking = { navController.navigate(Screen.Booking.route) }
            )
        }
        composable(route = Screen.MyChildren.route) {
            MyChildrenScreen(
                viewModel = babyViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToHome = { navController.navigate(Screen.Home.route) },
                onNavigateToBooking = { navController.navigate(Screen.Booking.route) },
                onNavigateToVaccinations = { filter ->
                    babyViewModel.setInitialVaccineFilter(filter)
                    navController.navigate(Screen.Vaccination.route)
                },
                onNavigateToAppointments = {
                    navController.navigate(Screen.UpcomingVaccines.route)
                },
                onAddChild = { navController.navigate(Screen.BabyInfoNew.route) }
            )
        }
        composable(route = Screen.Vaccination.route) {
            VaccinationScreen(
                viewModel = babyViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(route = Screen.GeneralVaccines.route) {
            GeneralVaccinesScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(route = Screen.Chatbot.route) {
            ChatbotScreen(
                viewModel = babyViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(route = Screen.Notifications.route) {
            NotificationsScreen(
                viewModel = babyViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(route = Screen.Profile.route) {
            ProfileScreen(
                viewModel = babyViewModel,
                onNavigateBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onNavigateToChildren = {
                    navController.navigate(Screen.MyChildren.route)
                },
                onNavigateToAppointments = {
                    navController.navigate(Screen.UpcomingVaccines.route)
                },
                onNavigateToVaccinations = {
                    navController.navigate(Screen.Vaccination.route)
                },
                onNavigateToChatbot = {
                    navController.navigate(Screen.Chatbot.route)
                }
            )
        }
        composable(route = Screen.Booking.route) {
            BookingScreen(
                viewModel = babyViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
