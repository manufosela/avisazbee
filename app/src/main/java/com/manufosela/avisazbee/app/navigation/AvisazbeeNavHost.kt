package com.manufosela.avisazbee.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.manufosela.avisazbee.features.auth.presentation.SignInScreen
import com.manufosela.avisazbee.features.channels.presentation.channels.ChannelsScreen
import com.manufosela.avisazbee.app.splash.SplashScreen

@Composable
fun AvisazbeeNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onAuthenticated = {
                    navController.navigate(Routes.CHANNELS) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
                onUnauthenticated = {
                    navController.navigate(Routes.SIGN_IN) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.SIGN_IN) {
            SignInScreen(
                onSignedIn = {
                    navController.navigate(Routes.CHANNELS) {
                        popUpTo(Routes.SIGN_IN) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.CHANNELS) {
            ChannelsScreen()
        }
    }
}
