package com.kolpona.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kolpona.app.KolponaApp
import com.kolpona.app.R
import com.kolpona.app.ui.KolponaViewModelFactory
import com.kolpona.app.ui.history.HistoryScreen
import com.kolpona.app.ui.history.HistoryViewModel
import com.kolpona.app.ui.history.ImageViewerScreen
import com.kolpona.app.ui.home.HomeScreen
import com.kolpona.app.ui.home.HomeViewModel
import com.kolpona.app.ui.legal.AboutScreen
import com.kolpona.app.ui.legal.PrivacyPolicyScreen
import com.kolpona.app.ui.legal.TermsOfUseScreen
import com.kolpona.app.ui.onboarding.OnboardingScreen
import com.kolpona.app.ui.result.ResultScreen
import com.kolpona.app.ui.result.ResultViewModel
import com.kolpona.app.ui.settings.SettingsScreen
import com.kolpona.app.ui.settings.SettingsViewModel
import com.kolpona.app.ui.theme.ElectricBlue
import com.kolpona.app.ui.theme.MidnightDeep
import com.kolpona.app.ui.theme.MutedGray

object Routes {
    const val ONBOARDING = "onboarding"
    const val MAIN = "main"
    const val RESULT = "result/{imageId}"
    const val VIEWER = "viewer/{imageId}"
    const val PRIVACY = "privacy"
    const val TERMS = "terms"
    const val ABOUT = "about"

    fun result(id: String) = "result/$id"
    fun viewer(id: String) = "viewer/$id"
}

enum class MainTab { Home, History, Settings }

@Composable
fun KolponaNav(
    showOnboarding: Boolean,
    onOnboardingFinished: () -> Unit
) {
    val navController = rememberNavController()
    val app = LocalContext.current.applicationContext as KolponaApp
    val start = if (showOnboarding) Routes.ONBOARDING else Routes.MAIN

    NavHost(navController = navController, startDestination = start) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onFinished = {
                onOnboardingFinished()
                navController.navigate(Routes.MAIN) {
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                }
            })
        }
        composable(Routes.MAIN) {
            MainTabs(
                onOpenViewer = { navController.navigate(Routes.viewer(it)) },
                onPrivacy = { navController.navigate(Routes.PRIVACY) },
                onTerms = { navController.navigate(Routes.TERMS) },
                onAbout = { navController.navigate(Routes.ABOUT) }
            )
        }
        composable(
            Routes.RESULT,
            arguments = listOf(navArgument("imageId") { type = NavType.StringType })
        ) { entry ->
            val id = entry.arguments?.getString("imageId").orEmpty()
            val vm: ResultViewModel = viewModel(
                factory = KolponaViewModelFactory(app) { ResultViewModel.create(it) }
            )
            ResultScreen(
                imageId = id,
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onCreateAnother = {
                    navController.popBackStack(Routes.MAIN, inclusive = false)
                },
                onRegenerated = { newId ->
                    navController.navigate(Routes.result(newId)) {
                        popUpTo(Routes.MAIN)
                    }
                }
            )
        }
        composable(
            Routes.VIEWER,
            arguments = listOf(navArgument("imageId") { type = NavType.StringType })
        ) { entry ->
            val id = entry.arguments?.getString("imageId").orEmpty()
            val vm: HistoryViewModel = viewModel(
                factory = KolponaViewModelFactory(app) { HistoryViewModel.create(it) }
            )
            ImageViewerScreen(
                imageId = id,
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onGenerateAgainId = { newId ->
                    navController.navigate(Routes.result(newId))
                }
            )
        }
        composable(Routes.PRIVACY) { PrivacyPolicyScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.TERMS) { TermsOfUseScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.ABOUT) { AboutScreen(onBack = { navController.popBackStack() }) }
    }
}

@Composable
private fun MainTabs(
    onOpenViewer: (String) -> Unit,
    onPrivacy: () -> Unit,
    onTerms: () -> Unit,
    onAbout: () -> Unit
) {
    var tab by rememberSaveable { mutableStateOf(MainTab.Home) }
    val app = LocalContext.current.applicationContext as KolponaApp

    Scaffold(
        containerColor = MidnightDeep,
        bottomBar = {
            NavigationBar(containerColor = MidnightDeep) {
                val itemColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = ElectricBlue,
                    selectedTextColor = ElectricBlue,
                    unselectedIconColor = MutedGray,
                    unselectedTextColor = MutedGray,
                    indicatorColor = ElectricBlue.copy(alpha = 0.16f)
                )
                NavigationBarItem(
                    selected = tab == MainTab.Home,
                    onClick = { tab = MainTab.Home },
                    icon = { Icon(Icons.Outlined.Chat, contentDescription = stringResource(R.string.nav_home)) },
                    label = { Text(stringResource(R.string.nav_home)) },
                    colors = itemColors
                )
                NavigationBarItem(
                    selected = tab == MainTab.History,
                    onClick = { tab = MainTab.History },
                    icon = { Icon(Icons.Outlined.PhotoLibrary, contentDescription = stringResource(R.string.nav_history)) },
                    label = { Text(stringResource(R.string.nav_history)) },
                    colors = itemColors
                )
                NavigationBarItem(
                    selected = tab == MainTab.Settings,
                    onClick = { tab = MainTab.Settings },
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.nav_settings)) },
                    label = { Text(stringResource(R.string.nav_settings)) },
                    colors = itemColors
                )
            }
        }
    ) { padding ->
        when (tab) {
            MainTab.Home -> {
                val vm: HomeViewModel = viewModel(
                    factory = KolponaViewModelFactory(app) { HomeViewModel.create(it) }
                )
                HomeScreen(
                    viewModel = vm,
                    onOpenSettings = { tab = MainTab.Settings },
                    onOpenImage = onOpenViewer,
                    modifier = Modifier.padding(padding)
                )
            }
            MainTab.History -> {
                val vm: HistoryViewModel = viewModel(
                    factory = KolponaViewModelFactory(app) { HistoryViewModel.create(it) }
                )
                HistoryScreen(
                    viewModel = vm,
                    onOpenImage = onOpenViewer,
                    onCreateFirst = { tab = MainTab.Home },
                    modifier = Modifier.padding(padding)
                )
            }
            MainTab.Settings -> {
                val vm: SettingsViewModel = viewModel(
                    factory = KolponaViewModelFactory(app) { SettingsViewModel.create(it) }
                )
                SettingsScreen(
                    viewModel = vm,
                    onPrivacy = onPrivacy,
                    onTerms = onTerms,
                    onAbout = onAbout,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}
