package com.kolpona.ai.ui.auth

import android.app.Activity
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kolpona.ai.R
import com.kolpona.ai.data.auth.AuthOutcome
import com.kolpona.ai.data.auth.AuthRepository
import com.kolpona.ai.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val acceptedPolicy: Boolean = false,
    val loading: Boolean = false,
    val errorRes: Int? = null,
    val infoRes: Int? = null,
    val signedIn: Boolean = false
)

class AuthViewModel(
    private val auth: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState(signedIn = auth.isSignedIn()))
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onName(value: String) {
        _state.update { it.copy(name = value, errorRes = null, infoRes = null) }
    }

    fun onEmail(value: String) {
        _state.update { it.copy(email = value, errorRes = null, infoRes = null) }
    }

    fun onPassword(value: String) {
        _state.update { it.copy(password = value, errorRes = null, infoRes = null) }
    }

    fun onConfirm(value: String) {
        _state.update { it.copy(confirmPassword = value, errorRes = null, infoRes = null) }
    }

    fun onPolicy(accepted: Boolean) {
        _state.update { it.copy(acceptedPolicy = accepted, errorRes = null, infoRes = null) }
    }

    fun signIn() = launchAuth {
        auth.signIn(_state.value.email, _state.value.password)
    }

    fun register() = launchAuth {
        val s = _state.value
        auth.register(s.name, s.email, s.password, s.confirmPassword, s.acceptedPolicy)
    }

    fun sendReset() = launchAuth {
        auth.sendPasswordReset(_state.value.email)
    }

    fun signInWithGoogle(
        activity: Activity,
        launchLegacy: (Intent) -> Unit,
        requirePolicy: Boolean = false
    ) = launchAuth {
        if (requirePolicy && !_state.value.acceptedPolicy) {
            return@launchAuth AuthOutcome.Failure(R.string.auth_error_policy)
        }
        val intent = auth.prepareGoogleSignIn(activity)
        if (intent != null) {
            launchLegacy(intent)
            AuthOutcome.ContinueGoogle
        } else {
            auth.signInWithGoogle(activity)
        }
    }

    fun handleGoogleIntent(data: Intent?) {
        viewModelScope.launch {
            applyOutcome(auth.handleGoogleIntent(data))
        }
    }

    fun clearMessages() {
        _state.update { it.copy(errorRes = null, infoRes = null) }
    }

    private fun launchAuth(block: suspend () -> AuthOutcome) {
        if (_state.value.loading) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, errorRes = null, infoRes = null) }
            applyOutcome(block())
        }
    }

    private fun applyOutcome(result: AuthOutcome) {
        when (result) {
            AuthOutcome.Success -> _state.update {
                it.copy(loading = false, signedIn = true, password = "", confirmPassword = "")
            }
            AuthOutcome.ContinueGoogle -> _state.update {
                it.copy(loading = true, errorRes = null, infoRes = null)
            }
            is AuthOutcome.Message -> _state.update {
                it.copy(loading = false, infoRes = result.resId, errorRes = null)
            }
            is AuthOutcome.Failure -> _state.update {
                it.copy(loading = false, errorRes = result.resId, signedIn = auth.isSignedIn())
            }
        }
    }

    companion object {
        fun create(container: AppContainer) = AuthViewModel(container.authRepository)
    }
}
