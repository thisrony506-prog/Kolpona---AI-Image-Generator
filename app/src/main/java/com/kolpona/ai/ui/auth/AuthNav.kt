package com.kolpona.ai.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kolpona.ai.KolponaApp
import com.kolpona.ai.ui.KolponaViewModelFactory
import com.kolpona.ai.ui.legal.PrivacyPolicyScreen
import com.kolpona.ai.ui.legal.TermsOfUseScreen

private object AuthRoutes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val FORGOT = "forgot"
    const val PRIVACY = "privacy"
    const val TERMS = "terms"
}

@Composable
fun AuthNav(
    onSignedIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nav = rememberNavController()
    val app = LocalContext.current.applicationContext as KolponaApp
    val viewModel: AuthViewModel = viewModel(
        factory = KolponaViewModelFactory(app) { AuthViewModel.create(it) }
    )
    NavHost(
        navController = nav,
        startDestination = AuthRoutes.LOGIN,
        modifier = modifier
    ) {
        composable(AuthRoutes.LOGIN) {
            LoginScreen(
                viewModel = viewModel,
                onCreateAccount = {
                    viewModel.clearMessages()
                    nav.navigate(AuthRoutes.REGISTER)
                },
                onForgotPassword = {
                    viewModel.clearMessages()
                    nav.navigate(AuthRoutes.FORGOT)
                },
                onSignedIn = onSignedIn
            )
        }
        composable(AuthRoutes.REGISTER) {
            RegisterScreen(
                viewModel = viewModel,
                onHaveAccount = {
                    viewModel.clearMessages()
                    nav.popBackStack()
                },
                onPrivacy = { nav.navigate(AuthRoutes.PRIVACY) },
                onTerms = { nav.navigate(AuthRoutes.TERMS) },
                onSignedIn = onSignedIn
            )
        }
        composable(AuthRoutes.FORGOT) {
            ForgotPasswordScreen(
                viewModel = viewModel,
                onBack = {
                    viewModel.clearMessages()
                    nav.popBackStack()
                }
            )
        }
        composable(AuthRoutes.PRIVACY) {
            PrivacyPolicyScreen(onBack = { nav.popBackStack() })
        }
        composable(AuthRoutes.TERMS) {
            TermsOfUseScreen(onBack = { nav.popBackStack() })
        }
    }
}
