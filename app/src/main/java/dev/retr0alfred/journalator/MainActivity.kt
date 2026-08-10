package dev.retr0alfred.journalator

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.retr0alfred.journalator.crypto.SecureMemory
import dev.retr0alfred.journalator.ui.JournalViewModel
import dev.retr0alfred.journalator.ui.JournalatorApp
import dev.retr0alfred.journalator.ui.components.GhostButton
import dev.retr0alfred.journalator.ui.components.JTextField
import dev.retr0alfred.journalator.ui.components.PrimaryButton
import dev.retr0alfred.journalator.ui.theme.JText
import dev.retr0alfred.journalator.ui.theme.JournalatorTheme
import dev.retr0alfred.journalator.ui.theme.JournalatorTheme as ThemeTokens

/**
 * The single activity.
 *
 * `AppCompatActivity` rather than `ComponentActivity` for exactly one reason: per-app
 * language selection needs AppCompat's delegate on API levels below 33. Nothing else in the
 * app touches AppCompat.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: JournalViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /*
         * FLAG_SECURE, set before anything is drawn and never cleared.
         *
         * This blocks screenshots and screen recording, and — the part people forget — it
         * blanks the thumbnail the OS keeps of this app in the recents switcher. Without it,
         * today's entry would sit in a system-owned screenshot cache on disk.
         */
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        enableEdgeToEdge()

        val container = (application as JournalatorApplication).container
        viewModel = ViewModelProvider(this, JournalViewModel.factory(container))[JournalViewModel::class.java]

        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> viewModel.onStarted()
                    Lifecycle.Event.ON_RESUME -> viewModel.onResumed()
                    Lifecycle.Event.ON_STOP -> viewModel.onStopped()
                    else -> Unit
                }
            }
        )

        setContent {
            val settings by viewModel.settingsFlow.collectAsStateWithLifecycle()

            JournalatorTheme(
                reduceMotionSetting = settings.reduceMotion,
                textSize = settings.textSize,
            ) {
                var enrolmentPasscode by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(Unit) {
                    viewModel.setBiometricSupported(biometricAvailable())
                }

                JournalatorApp(
                    viewModel = viewModel,
                    versionName = versionName(),
                    onRequestBiometricUnlock = { startBiometricUnlock() },
                    onRequestBiometricEnrolment = { enrolmentPasscode = "" },
                    onBiometricDisable = { viewModel.disableBiometrics() },
                )

                enrolmentPasscode?.let { current ->
                    PasscodeForEnrolmentDialog(
                        value = current,
                        onValueChange = { enrolmentPasscode = it },
                        onDismiss = { enrolmentPasscode = null },
                        onConfirm = {
                            val chars = current.toCharArray()
                            enrolmentPasscode = null
                            startBiometricEnrolment(chars)
                        },
                    )
                }
            }
        }
    }

    private fun versionName(): String = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                packageName,
                android.content.pm.PackageManager.PackageInfoFlags.of(0),
            ).versionName
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionName
        }
    }.getOrNull().orEmpty()

    private fun biometricAvailable(): Boolean =
        BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

    private fun promptInfo() = BiometricPrompt.PromptInfo.Builder()
        .setTitle(getString(R.string.unlock_biometric_title))
        .setSubtitle(getString(R.string.unlock_biometric_subtitle))
        .setNegativeButtonText(getString(R.string.unlock_biometric_negative))
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        .setConfirmationRequired(true)
        .build()

    private fun startBiometricUnlock() {
        val cipher = viewModel.biometricUnlockCipher()
        if (cipher == null) {
            viewModel.biometricPromptFailed(getString(R.string.settings_biometric_unavailable))
            return
        }
        BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val authenticated = result.cryptoObject?.cipher ?: return
                    viewModel.completeBiometricUnlock(authenticated)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    viewModel.biometricPromptFailed(errString.toString())
                }
            },
        ).authenticate(promptInfo(), BiometricPrompt.CryptoObject(cipher))
    }

    private fun startBiometricEnrolment(passcode: CharArray) {
        val cipher = viewModel.biometricEnrolCipher()
        if (cipher == null) {
            viewModel.notice(getString(R.string.settings_biometric_unavailable))
            SecureMemory.wipe(passcode)
            return
        }
        BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val authenticated = result.cryptoObject?.cipher
                    if (authenticated == null) {
                        SecureMemory.wipe(passcode)
                        return
                    }
                    viewModel.completeBiometricEnrolment(authenticated, passcode)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    SecureMemory.wipe(passcode)
                    viewModel.notice(
                        getString(R.string.settings_biometric_enroll_failed, errString.toString())
                    )
                }
            },
        ).authenticate(promptInfo(), BiometricPrompt.CryptoObject(cipher))
    }
}

@Composable
private fun PasscodeForEnrolmentDialog(
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = ThemeTokens.colors
    val type = ThemeTokens.type
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceRaised,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurface,
        title = { JText(stringResource(R.string.settings_biometric), type.displaySmall) },
        text = {
            JTextField(
                value = value,
                onValueChange = onValueChange,
                label = stringResource(R.string.settings_change_current),
                password = true,
            )
        },
        confirmButton = {
            PrimaryButton(
                label = stringResource(R.string.action_confirm),
                enabled = value.isNotEmpty(),
                onClick = onConfirm,
            )
        },
        dismissButton = {
            GhostButton(
                label = stringResource(R.string.action_cancel),
                accent = colors.muted,
                onClick = onDismiss,
            )
        },
    )
}
