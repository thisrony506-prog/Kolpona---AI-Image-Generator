package com.kolpona.ai.data.auth

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.util.Patterns
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.kolpona.ai.R
import com.kolpona.ai.utils.NetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed class AuthOutcome {
    data object Success : AuthOutcome()
    data object ContinueGoogle : AuthOutcome()
    data class Message(val resId: Int) : AuthOutcome()
    data class Failure(val resId: Int) : AuthOutcome()
}

class AuthRepository(
    private val app: Application,
    private val network: NetworkMonitor
) {
    private val firebaseAuth: FirebaseAuth? by lazy {
        runCatching {
            if (FirebaseApp.getApps(app).isEmpty()) {
                FirebaseApp.initializeApp(app) ?: return@runCatching null
            }
            FirebaseAuth.getInstance().also { it.useAppLanguage() }
        }.getOrNull()
    }

    val currentUser: FirebaseUser?
        get() = firebaseAuth?.currentUser

    val user: Flow<FirebaseUser?> = callbackFlow {
        val auth = firebaseAuth
        if (auth == null) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        val listener = FirebaseAuth.AuthStateListener { firebase ->
            trySend(firebase.currentUser)
        }
        auth.addAuthStateListener(listener)
        trySend(auth.currentUser)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    fun isSignedIn(): Boolean = currentUser != null

    suspend fun signIn(email: String, password: String): AuthOutcome {
        val auth = requireAuth() ?: return AuthOutcome.Failure(R.string.auth_error_not_configured)
        validateEmail(email)?.let { return it }
        if (password.isBlank()) return AuthOutcome.Failure(R.string.auth_error_password_required)
        if (!network.isOnline()) return AuthOutcome.Failure(R.string.error_network)
        return runAuth {
            auth.signInWithEmailAndPassword(email.trim(), password).await()
            AuthOutcome.Success
        }
    }

    suspend fun register(
        name: String,
        email: String,
        password: String,
        confirm: String,
        acceptedPolicy: Boolean
    ): AuthOutcome {
        val auth = requireAuth() ?: return AuthOutcome.Failure(R.string.auth_error_not_configured)
        validateName(name)?.let { return it }
        validateEmail(email)?.let { return it }
        validateNewPassword(password)?.let { return it }
        if (password != confirm) return AuthOutcome.Failure(R.string.auth_error_password_mismatch)
        if (!acceptedPolicy) return AuthOutcome.Failure(R.string.auth_error_policy)
        if (!network.isOnline()) return AuthOutcome.Failure(R.string.error_network)
        return runAuth {
            auth.createUserWithEmailAndPassword(email.trim(), password).await()
            val profile = UserProfileChangeRequest.Builder()
                .setDisplayName(name.trim())
                .build()
            runCatching { auth.currentUser?.updateProfile(profile)?.await() }
            runCatching { auth.currentUser?.sendEmailVerification()?.await() }
            AuthOutcome.Success
        }
    }

    suspend fun sendPasswordReset(email: String): AuthOutcome {
        val auth = requireAuth() ?: return AuthOutcome.Failure(R.string.auth_error_not_configured)
        validateEmail(email)?.let { return it }
        if (!network.isOnline()) return AuthOutcome.Failure(R.string.error_network)
        return runAuth {
            auth.sendPasswordResetEmail(email.trim()).await()
            AuthOutcome.Message(R.string.auth_reset_sent)
        }
    }

    suspend fun prepareGoogleSignIn(activity: Activity): Intent? {
        val webClientId = webClientId()
        if (webClientId.isBlank()) return null
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .requestProfile()
            .build()
        val client = GoogleSignIn.getClient(activity, options)
        runCatching { client.signOut().await() }
        return client.signInIntent
    }

    suspend fun signInWithGoogle(activity: Activity): AuthOutcome {
        val auth = requireAuth() ?: return AuthOutcome.Failure(R.string.auth_error_not_configured)
        if (!network.isOnline()) return AuthOutcome.Failure(R.string.error_network)
        val webClientId = webClientId()
        if (webClientId.isBlank()) return AuthOutcome.Failure(R.string.auth_error_google_config)
        val manager = CredentialManager.create(activity)
        val button = runCatching {
            val option = GetSignInWithGoogleOption.Builder(webClientId).build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            withContext(Dispatchers.Main) { manager.getCredential(activity, request) }
        }
        button.getOrNull()?.let { result ->
            return tokenFromCredential(auth, result.credential)
        }
        val buttonError = button.exceptionOrNull()
        if (buttonError is GetCredentialCancellationException) {
            return AuthOutcome.Failure(R.string.auth_error_google_cancelled)
        }
        return try {
            val option = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(false)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build()
            val result = withContext(Dispatchers.Main) {
                manager.getCredential(activity, request)
            }
            tokenFromCredential(auth, result.credential)
        } catch (_: GetCredentialCancellationException) {
            AuthOutcome.Failure(R.string.auth_error_google_cancelled)
        } catch (_: NoCredentialException) {
            AuthOutcome.Failure(R.string.auth_error_google_no_account)
        } catch (e: GetCredentialException) {
            AuthOutcome.Failure(R.string.auth_error_google_no_account)
        } catch (e: Exception) {
            if (isDeveloperError(e)) AuthOutcome.Failure(R.string.auth_error_google_sha)
            else AuthOutcome.Failure(R.string.auth_error_google_no_account)
        }
    }

    fun googleSignInIntent(activity: Activity): Intent? {
        val webClientId = webClientId()
        if (webClientId.isBlank()) return null
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .requestProfile()
            .build()
        return GoogleSignIn.getClient(activity, options).signInIntent
    }

    suspend fun handleGoogleIntent(data: Intent?): AuthOutcome {
        val auth = requireAuth() ?: return AuthOutcome.Failure(R.string.auth_error_not_configured)
        if (data == null) return AuthOutcome.Failure(R.string.auth_error_google_cancelled)
        return try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(data)
                .getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken.isNullOrBlank()) AuthOutcome.Failure(R.string.auth_error_google)
            else signInWithGoogleIdToken(auth, idToken)
        } catch (e: ApiException) {
            when (e.statusCode) {
                12501 -> AuthOutcome.Failure(R.string.auth_error_google_cancelled)
                CommonStatusCodes.NETWORK_ERROR -> AuthOutcome.Failure(R.string.error_network)
                CommonStatusCodes.DEVELOPER_ERROR, 10 -> AuthOutcome.Failure(R.string.auth_error_google_sha)
                else -> AuthOutcome.Failure(R.string.auth_error_google)
            }
        } catch (e: Exception) {
            mapException(e)
        }
    }

    suspend fun signOut(activity: Activity?) {
        runCatching { firebaseAuth?.signOut() }
        runCatching {
            val webClientId = webClientId()
            if (activity != null && webClientId.isNotBlank()) {
                val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(webClientId)
                    .requestEmail()
                    .build()
                GoogleSignIn.getClient(activity, options).signOut().await()
            }
        }
    }

    private suspend fun tokenFromCredential(
        auth: FirebaseAuth,
        credential: androidx.credentials.Credential
    ): AuthOutcome {
        val idToken = when {
            credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL ->
                GoogleIdTokenCredential.createFrom(credential.data).idToken
            else -> null
        }
        return if (idToken.isNullOrBlank()) AuthOutcome.Failure(R.string.auth_error_google)
        else signInWithGoogleIdToken(auth, idToken)
    }

    private suspend fun signInWithGoogleIdToken(auth: FirebaseAuth, idToken: String): AuthOutcome =
        runAuth {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            auth.signInWithCredential(credential).await()
            AuthOutcome.Success
        }

    private fun isDeveloperError(error: Throwable): Boolean {
        val message = error.message.orEmpty()
        return message.contains("10:") ||
            message.contains("DEVELOPER_ERROR") ||
            (error as? ApiException)?.statusCode == CommonStatusCodes.DEVELOPER_ERROR
    }

    private fun requireAuth(): FirebaseAuth? = firebaseAuth

    private fun validateName(name: String): AuthOutcome.Failure? {
        val trimmed = name.trim()
        if (trimmed.length < 2) return AuthOutcome.Failure(R.string.auth_error_name_required)
        return null
    }

    private fun validateEmail(email: String): AuthOutcome.Failure? {
        val trimmed = email.trim()
        if (trimmed.isEmpty()) return AuthOutcome.Failure(R.string.auth_error_email_required)
        if (!Patterns.EMAIL_ADDRESS.matcher(trimmed).matches()) {
            return AuthOutcome.Failure(R.string.auth_error_email_invalid)
        }
        return null
    }

    private fun validateNewPassword(password: String): AuthOutcome.Failure? {
        if (password.length < 8) return AuthOutcome.Failure(R.string.auth_error_password_short)
        if (password.any { it.isWhitespace() }) return AuthOutcome.Failure(R.string.auth_error_password_spaces)
        if (!password.any { it.isLetter() } || !password.any { it.isDigit() }) {
            return AuthOutcome.Failure(R.string.auth_error_password_weak)
        }
        return null
    }

    private suspend fun runAuth(block: suspend () -> AuthOutcome): AuthOutcome {
        return try {
            block()
        } catch (e: Exception) {
            mapException(e)
        }
    }

    private fun mapException(error: Throwable): AuthOutcome.Failure {
        val code = (error as? FirebaseAuthException)?.errorCode.orEmpty()
        val res = when {
            error is FirebaseNetworkException || code == "ERROR_NETWORK_REQUEST_FAILED" ->
                R.string.error_network
            error is FirebaseTooManyRequestsException || code == "ERROR_TOO_MANY_REQUESTS" ->
                R.string.auth_error_too_many
            error is FirebaseAuthInvalidUserException || code == "ERROR_USER_NOT_FOUND" ->
                R.string.auth_error_user_not_found
            error is FirebaseAuthInvalidCredentialsException && code == "ERROR_WRONG_PASSWORD" ->
                R.string.auth_error_wrong_password
            error is FirebaseAuthInvalidCredentialsException ||
                code == "ERROR_INVALID_CREDENTIAL" ||
                code == "ERROR_INVALID_EMAIL" ->
                R.string.auth_error_invalid_credential
            error is FirebaseAuthUserCollisionException ||
                code == "ERROR_EMAIL_ALREADY_IN_USE" ||
                code == "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" ->
                R.string.auth_error_email_in_use
            error is FirebaseAuthWeakPasswordException || code == "ERROR_WEAK_PASSWORD" ->
                R.string.auth_error_password_weak
            code == "ERROR_USER_DISABLED" -> R.string.auth_error_user_disabled
            code == "ERROR_OPERATION_NOT_ALLOWED" -> R.string.auth_error_not_enabled
            else -> R.string.auth_error_generic
        }
        return AuthOutcome.Failure(res)
    }

    private fun webClientId(): String {
        val id = app.resources.getIdentifier("default_web_client_id", "string", app.packageName)
        val fromResources = if (id != 0) {
            runCatching { app.getString(id) }.getOrNull().orEmpty()
        } else {
            ""
        }
        return fromResources.ifBlank { WEB_CLIENT_ID }
    }

    companion object {
        private const val WEB_CLIENT_ID =
            "727257597579-c89ss6i0iv81jq0095t6n11fi4g0812k.apps.googleusercontent.com"
    }
}
