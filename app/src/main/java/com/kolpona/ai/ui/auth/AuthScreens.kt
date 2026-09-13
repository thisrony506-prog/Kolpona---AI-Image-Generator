package com.kolpona.ai.ui.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kolpona.ai.R
import com.kolpona.ai.ui.components.KolponaMark
import com.kolpona.ai.ui.components.StudioBackdrop
import com.kolpona.ai.ui.theme.ElectricBlue
import com.kolpona.ai.ui.theme.GlassFill
import com.kolpona.ai.ui.theme.GlassStroke
import com.kolpona.ai.ui.theme.MutedGray
import com.kolpona.ai.ui.theme.NeonViolet
import com.kolpona.ai.ui.theme.SoftWhite

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onCreateAccount: () -> Unit,
    onForgotPassword: () -> Unit,
    onSignedIn: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current as Activity
    val googleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.handleGoogleIntent(result.data)
    }
    LaunchedEffect(state.signedIn) {
        if (state.signedIn) onSignedIn()
    }
    AuthScaffold(
        title = stringResource(R.string.auth_login_title),
        subtitle = stringResource(R.string.auth_login_subtitle)
    ) {
        AuthFields(
            email = state.email,
            password = state.password,
            onEmail = viewModel::onEmail,
            onPassword = viewModel::onPassword,
            passwordIme = ImeAction.Done,
            onDone = viewModel::signIn
        )
        AuthStatus(state)
        Spacer(Modifier.height(16.dp))
        AuthPrimaryButton(
            text = stringResource(R.string.auth_login),
            loading = state.loading,
            onClick = viewModel::signIn
        )
        AuthOrDivider()
        AuthGoogleButton(enabled = !state.loading) {
            viewModel.signInWithGoogle(activity) { intent ->
                googleLauncher.launch(intent)
            }
        }
        TextButton(onClick = onForgotPassword, enabled = !state.loading) {
            Text(stringResource(R.string.auth_forgot), color = ElectricBlue)
        }
        TextButton(onClick = onCreateAccount, enabled = !state.loading) {
            Text(stringResource(R.string.auth_need_account), color = SoftWhite)
        }
    }
}

@Composable
fun RegisterScreen(
    viewModel: AuthViewModel,
    onHaveAccount: () -> Unit,
    onSignedIn: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current as Activity
    val googleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.handleGoogleIntent(result.data)
    }
    LaunchedEffect(state.signedIn) {
        if (state.signedIn) onSignedIn()
    }
    AuthScaffold(
        title = stringResource(R.string.auth_register_title),
        subtitle = stringResource(R.string.auth_register_subtitle)
    ) {
        AuthFields(
            email = state.email,
            password = state.password,
            onEmail = viewModel::onEmail,
            onPassword = viewModel::onPassword,
            confirm = state.confirmPassword,
            onConfirm = viewModel::onConfirm,
            passwordIme = ImeAction.Next,
            onDone = viewModel::register
        )
        AuthStatus(state)
        Spacer(Modifier.height(16.dp))
        AuthPrimaryButton(
            text = stringResource(R.string.auth_register),
            loading = state.loading,
            onClick = viewModel::register
        )
        AuthOrDivider()
        AuthGoogleButton(enabled = !state.loading) {
            viewModel.signInWithGoogle(activity) { intent ->
                googleLauncher.launch(intent)
            }
        }
        TextButton(onClick = onHaveAccount, enabled = !state.loading) {
            Text(stringResource(R.string.auth_have_account), color = SoftWhite)
        }
    }
}

@Composable
fun ForgotPasswordScreen(
    viewModel: AuthViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AuthScaffold(
        title = stringResource(R.string.auth_forgot_title),
        subtitle = stringResource(R.string.auth_forgot_subtitle)
    ) {
        AuthEmailField(value = state.email, onChange = viewModel::onEmail, onDone = viewModel::sendReset)
        AuthStatus(state)
        Spacer(Modifier.height(16.dp))
        AuthPrimaryButton(
            text = stringResource(R.string.auth_send_reset),
            loading = state.loading,
            onClick = viewModel::sendReset
        )
        TextButton(onClick = onBack, enabled = !state.loading) {
            Text(stringResource(R.string.back), color = SoftWhite)
        }
    }
}

@Composable
private fun AuthScaffold(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        StudioBackdrop(Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            KolponaMark(size = 72.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.app_name),
                color = SoftWhite,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(24.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(GlassFill)
                    .border(1.dp, GlassStroke, RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Text(
                    text = title,
                    color = SoftWhite,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    color = MutedGray,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(18.dp))
                content()
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AuthFields(
    email: String,
    password: String,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    confirm: String? = null,
    onConfirm: ((String) -> Unit)? = null,
    passwordIme: ImeAction,
    onDone: () -> Unit
) {
    AuthEmailField(email, onEmail, onDone = if (confirm == null && passwordIme == ImeAction.Done) onDone else null)
    Spacer(Modifier.height(12.dp))
    AuthPasswordField(
        value = password,
        onChange = onPassword,
        label = stringResource(R.string.auth_password),
        ime = if (onConfirm != null) ImeAction.Next else ImeAction.Done,
        onDone = onDone
    )
    if (confirm != null && onConfirm != null) {
        Spacer(Modifier.height(12.dp))
        AuthPasswordField(
            value = confirm,
            onChange = onConfirm,
            label = stringResource(R.string.auth_confirm_password),
            ime = ImeAction.Done,
            onDone = onDone
        )
    }
}

@Composable
private fun AuthEmailField(
    value: String,
    onChange: (String) -> Unit,
    onDone: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.auth_email)) },
        leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null, tint = ElectricBlue) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = if (onDone != null) ImeAction.Done else ImeAction.Next
        ),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        colors = authFieldColors(),
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun AuthPasswordField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    ime: ImeAction,
    onDone: () -> Unit
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null, tint = ElectricBlue) },
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = stringResource(
                        if (visible) R.string.auth_hide_password else R.string.auth_show_password
                    ),
                    tint = MutedGray
                )
            }
        },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ime
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        colors = authFieldColors(),
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun AuthStatus(state: AuthUiState) {
    val message = state.errorRes ?: state.infoRes
    if (message != null) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(message),
            color = if (state.errorRes != null) Color(0xFFFFB4AB) else NeonViolet,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun AuthPrimaryButton(text: String, loading: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !loading,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ElectricBlue,
            contentColor = Color.White,
            disabledContainerColor = ElectricBlue.copy(alpha = 0.4f)
        )
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp)
            )
        } else {
            Text(text, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AuthOrDivider() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(GlassStroke)
        )
        Text(
            text = stringResource(R.string.auth_or),
            color = MutedGray,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(GlassStroke)
        )
    }
}

@Composable
private fun AuthGoogleButton(enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = SoftWhite)
    ) {
        Text(stringResource(R.string.auth_google), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun authFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = ElectricBlue,
    unfocusedBorderColor = GlassStroke,
    focusedTextColor = SoftWhite,
    unfocusedTextColor = SoftWhite,
    cursorColor = ElectricBlue,
    focusedLabelColor = ElectricBlue,
    unfocusedLabelColor = MutedGray,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedLeadingIconColor = ElectricBlue,
    unfocusedLeadingIconColor = ElectricBlue
)
