package com.kolpona.ai.ui.legal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kolpona.ai.BuildConfig
import com.kolpona.ai.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            content()
        }
    }
}

@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    LegalScaffold(title = stringResource(R.string.privacy_policy), onBack = onBack) {
        Text(
            text = "Kolpona generates images and videos from the text you provide. Prompts and media are stored on your device. " +
                "Generation requests are sent to Hugging Face and Cloudflare Workers AI so the result can be created. " +
                "We do not sell your personal information.\n\n" +
                "Account sign-in uses Firebase Authentication. Your name, email, and Google account identifiers are processed " +
                "by Google Firebase so you can create and use a Kolpona account. " +
                "Kolpona may show device notifications when an update is ready, a creation finishes, or you sign in. " +
                "Optional Firebase Cloud Messaging delivers those alerts if you allow notifications.\n\n" +
                "Optional rewarded videos are provided by Start.io. Their SDK may collect device advertising identifiers " +
                "to show ads. Credits and settings stay on this device.\n\n" +
                "You can clear local history at any time from Settings. Uninstalling the app removes locally stored images " +
                "and credit data.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun TermsOfUseScreen(onBack: () -> Unit) {
    LegalScaffold(title = stringResource(R.string.terms_of_use), onBack = onBack) {
        Text(
            text = "Kolpona is provided as-is for personal creative use. You are responsible for the prompts you submit " +
                "and for how you use generated images.\n\n" +
                "Do not use the app to create illegal, harmful, or abusive content. Daily credits and rewarded-video " +
                "bonuses are a usage allowance, not a stored-value balance, and may change in future versions.\n\n" +
                "AI generations can be imperfect. Kolpona does not guarantee that an image will match your prompt. " +
                "Credits are only deducted after a successful generation.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    LegalScaffold(title = stringResource(R.string.about_kolpona), onBack = onBack) {
        Text(stringResource(R.string.app_full_name), style = MaterialTheme.typography.headlineMedium)
        Text(
            text = stringResource(R.string.app_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
        )
        Text(
            text = stringResource(R.string.about_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "•  " + stringResource(R.string.about_feature_create),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = "•  " + stringResource(R.string.about_feature_unlimited),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = "•  " + stringResource(R.string.about_feature_languages),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = "•  " + stringResource(R.string.about_feature_voice),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = "•  " + stringResource(R.string.about_feature_history),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = stringResource(R.string.version_format, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 24.dp)
        )
    }
}
