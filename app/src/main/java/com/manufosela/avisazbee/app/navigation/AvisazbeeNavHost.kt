package com.manufosela.avisazbee.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.manufosela.avisazbee.app.splash.SplashScreen
import com.manufosela.avisazbee.features.auth.presentation.SignInScreen
import com.manufosela.avisazbee.features.buttons.presentation.create.CreateButtonScreen
import com.manufosela.avisazbee.features.buttons.presentation.list.ButtonsScreen
import com.manufosela.avisazbee.features.channels.presentation.channels.ChannelsScreen
import com.manufosela.avisazbee.features.channels.presentation.create.CreateChannelScreen
import com.manufosela.avisazbee.features.channels.presentation.detail.ChannelDetailScreen
import com.manufosela.avisazbee.features.channels.presentation.dialers.DialersScreen
import com.manufosela.avisazbee.features.channels.presentation.escalation.EscalationPolicyScreen
import com.manufosela.avisazbee.features.channels.presentation.join.JoinChannelScreen
import com.manufosela.avisazbee.features.users.presentation.NotificationPreferencesScreen

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
            ChannelsScreen(
                onOpenChannel = { channelId ->
                    navController.navigate(Routes.channelDetail(channelId))
                },
                onCreateChannel = { navController.navigate(Routes.CREATE_CHANNEL) },
                onJoinChannel = { navController.navigate(Routes.JOIN_CHANNEL) },
                onOpenPreferences = { navController.navigate(Routes.NOTIFICATION_PREFERENCES) },
            )
        }
        composable(Routes.NOTIFICATION_PREFERENCES) {
            NotificationPreferencesScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.CREATE_CHANNEL) {
            CreateChannelScreen(
                onBack = { navController.popBackStack() },
                onCreated = { channelId ->
                    navController.navigate(Routes.channelDetail(channelId)) {
                        popUpTo(Routes.CHANNELS) { inclusive = false }
                    }
                },
            )
        }
        composable(Routes.JOIN_CHANNEL) {
            JoinChannelScreen(
                onBack = { navController.popBackStack() },
                onJoined = { channelId ->
                    navController.navigate(Routes.channelDetail(channelId)) {
                        popUpTo(Routes.CHANNELS) { inclusive = false }
                    }
                },
            )
        }
        composable(
            route = Routes.CHANNEL_DETAIL_PATTERN,
            arguments = listOf(navArgument(Routes.ARG_CHANNEL_ID) { type = NavType.StringType }),
        ) { backStackEntry ->
            val channelId = backStackEntry.arguments
                ?.getString(Routes.ARG_CHANNEL_ID)
                .orEmpty()
            ChannelDetailScreen(
                onBack = { navController.popBackStack() },
                onManageButtons = { navController.navigate(Routes.buttons(channelId)) },
                onOpenEscalation = { navController.navigate(Routes.escalation(channelId)) },
                onOpenDialers = { navController.navigate(Routes.dialers(channelId)) },
            )
        }
        composable(
            route = Routes.ESCALATION_PATTERN,
            arguments = listOf(navArgument(Routes.ARG_CHANNEL_ID) { type = NavType.StringType }),
        ) {
            EscalationPolicyScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.DIALERS_PATTERN,
            arguments = listOf(navArgument(Routes.ARG_CHANNEL_ID) { type = NavType.StringType }),
        ) {
            DialersScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.BUTTONS_PATTERN,
            arguments = listOf(navArgument(Routes.ARG_CHANNEL_ID) { type = NavType.StringType }),
        ) {
            ButtonsScreen(
                onBack = { navController.popBackStack() },
                onCreate = { channelId ->
                    navController.navigate(Routes.createButton(channelId))
                },
            )
        }
        composable(
            route = Routes.CREATE_BUTTON_PATTERN,
            arguments = listOf(navArgument(Routes.ARG_CHANNEL_ID) { type = NavType.StringType }),
        ) {
            CreateButtonScreen(
                onBack = { navController.popBackStack() },
                onConfirmed = { navController.popBackStack() },
            )
        }
    }
}
