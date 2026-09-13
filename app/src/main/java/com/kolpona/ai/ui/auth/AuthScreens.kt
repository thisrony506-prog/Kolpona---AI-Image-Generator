package com.kolpona.ai.ui.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kolpona.ai.R
import com.kolpona.ai.ui.components.KolponaMark
import com.kolpona.ai.ui.components.StudioBackdrop
import com.kolpona.ai.ui.theme.ElectricBlue
import com.kolpona.ai.ui.theme.GlassFill
import com.kolpona.ai.ui.theme.GlassStroke
import com.kolpona.ai.ui.theme.MutedGray
import com.kolpona.ai.ui.theme.NeonViolet
import com.kolpona.ai.ui.theme.PinkAccent
import com.kolpona.ai.ui.theme.SoftWhite

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onCreateAccount: () -> Unit,
    onForgotPassword: () -> Unit,
    onSignedIn: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GoogleAuthHost(viewModel, state.signedIn, onSignedIn, requirePolicy = false) { google ->
        AuthScaffold(
            title = stringResource(R.string.auth_login_title),
            subtitle = stringResource(R.string.auth_login_subtitle)
        ) {
            AuthGoogleButton(enabled = !state.loading, onClick = google)
            AuthOrDivider()
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
            TextButton(onClick = onForgotPassword, enabled = !state.loading) {
                Text(stringResource(R.string.auth_forgot), color = ElectricBlue)
            }
            TextButton(onClick = onCreateAccount, enabled = !state.loading) {
                Text(stringResource(R.string.auth_need_account), color = SoftWhite)
            }
        }
    }
}

@Composable
fun RegisterScreen(
    viewModel: AuthViewModel,
    onHaveAccount: () -> Unit,
    onPrivacy: () -> Unit,
    onTerms: () -> Unit,
    onSignedIn: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GoogleAuthHost(viewModel, state.signedIn, onSignedIn, requirePolicy = true) { google ->
        AuthScaffold(
            title = stringResource(R.string.auth_register_title),
            subtitle = stringResource(R.string.auth_register_subtitle)
        ) {
            AuthGoogleButton(enabled = !state.loading, onClick = google)
            AuthOrDivider()
            AuthNameField(value = state.name, onChange = viewModel::onName)
            Spacer(Modifier.height(12.dp))
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
            Spacer(Modifier.height(8.dp))
            PolicyRow(
                accepted = state.acceptedPolicy,
                enabled = !state.loading,
                onAccepted = viewModel::onPolicy,
                onPrivacy = onPrivacy,
                onTerms = onTerms
            )
            AuthStatus(state)
            Spacer(Modifier.height(16.dp))
            AuthSignUpButton(
                text = stringResource(R.string.auth_sign_up),
                loading = state.loading,
                enabled = !state.loading,
                onClick = viewModel::register
            )
            TextButton(onClick = onHaveAccount, enabled = !state.loading) {
                Text(stringResource(R.string.auth_have_account), color = SoftWhite)
            }
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
private fun GoogleAuthHost(
    viewModel: AuthViewModel,
    signedIn: Boolean,
    onSignedIn: () -> Unit,
    requirePolicy: Boolean,
    content: @Composable (onGoogle: () -> Unit) -> Unit
) {
    val activity = LocalContext.current as Activity
    val googleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.handleGoogleIntent(result.data)
    }
    LaunchedEffect(signedIn) {
        if (signedIn) onSignedIn()
    }
    content {
        viewModel.signInWithGoogle(activity, { intent -> googleLauncher.launch(intent) }, requirePolicy)
    }
}

@Composable
private fun AuthScaffold(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    val titleBrush = Brush.linearGradient(listOf(ElectricBlue, NeonViolet, PinkAccent))
    Box(Modifier.fillMaxSize()) {
        StudioBackdrop(Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(28.dp))
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .border(1.dp, ElectricBlue.copy(alpha = 0.4f), RoundedCornerShape(28.dp))
                    .background(ElectricBlue.copy(alpha = 0.08f), RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center
            ) {
                KolponaMark(size = 64.dp)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = TextStyle(
                    brush = titleBrush,
                    fontWeight = FontWeight.Bold,
                    fontSize = 34.sp,
                    letterSpacing = (-0.4).sp
                )
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.app_tagline),
                color = MutedGray,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(GlassFill)
                    .border(1.dp, GlassStroke, RoundedCornerShape(28.dp))
                    .padding(22.dp)
            ) {
                Text(
                    text = title,
                    color = SoftWhite,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    color = MutedGray,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(20.dp))
                content()
            }
            Spacer(Modifier.height(28.dp))
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
private fun AuthNameField(
    value: String,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.auth_name)) },
        leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null, tint = ElectricBlue) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Next
        ),
        colors = authFieldColors(),
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun PolicyRow(
    accepted: Boolean,
    enabled: Boolean,
    onAccepted: (Boolean) -> Unit,
    onPrivacy: () -> Unit,
    onTerms: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Checkbox(
            checked = accepted,
            onCheckedChange = onAccepted,
            enabled = enabled,
            colors = CheckboxDefaults.colors(
                checkedColor = ElectricBlue,
                uncheckedColor = GlassStroke,
                checkmarkColor = Color.White
            )
        )
        Column(modifier = Modifier.padding(top = 12.dp)) {
            Text(
                text = stringResource(R.string.auth_policy),
                color = SoftWhite,
                style = MaterialTheme.typography.bodyMedium
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.privacy_policy),
                    color = ElectricBlue,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable(enabled = enabled, onClick = onPrivacy)
                )
                Text(
                    text = "  ·  ",
                    color = MutedGray,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(R.string.terms_of_use),
                    color = ElectricBlue,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable(enabled = enabled, onClick = onTerms)
                )
            }
        }
    }
}

@Composable
private fun AuthSignUpButton(
    text: String,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val brush = Brush.horizontalGradient(listOf(ElectricBlue, NeonViolet, PinkAccent))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(brush)
            .clickable(enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp)
            )
        } else {
            Text(
                text = text,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        }
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
            .height(54.dp),
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
            .padding(vertical = 16.dp),
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
            .height(54.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = SoftWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, GlassStroke)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "G",
                color = Color(0xFF4285F4),
                fontWeight = FontWeight.Black,
                fontSize = 13.sp
            )
        }
        Spacer(Modifier.width(10.dp))
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
