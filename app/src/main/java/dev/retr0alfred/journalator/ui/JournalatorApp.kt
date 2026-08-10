package dev.retr0alfred.journalator.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.retr0alfred.journalator.R
import dev.retr0alfred.journalator.ui.components.GhostButton
import dev.retr0alfred.journalator.ui.components.PrimaryButton
import dev.retr0alfred.journalator.ui.components.SealOverlay
import dev.retr0alfred.journalator.ui.components.concreteWall
import dev.retr0alfred.journalator.ui.screens.ArchiveScreen
import dev.retr0alfred.journalator.ui.screens.SetupScreen
import dev.retr0alfred.journalator.ui.screens.SettingsScreen
import dev.retr0alfred.journalator.ui.screens.UnlockScreen
import dev.retr0alfred.journalator.ui.screens.WriteScreen
import dev.retr0alfred.journalator.ui.theme.JText
import dev.retr0alfred.journalator.ui.theme.JournalatorTheme
import dev.retr0alfred.journalator.ui.theme.Motion

/**
 * The router. Five destinations, one `when`, no navigation library.
 *
 * Insets are handled here once — `safeDrawing` covers the status bar, the navigation bar and
 * a display cutout in one go — so no individual screen has to remember to do it and none of
 * them can get it wrong.
 */
@Composable
fun JournalatorApp(
    viewModel: JournalViewModel,
    versionName: String,
    onRequestBiometricUnlock: () -> Unit,
    onRequestBiometricEnrolment: () -> Unit,
    onBiometricDisable: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val motion = JournalatorTheme.motion

    BackHandler(enabled = state.screen != Screen.Write && state.screen != Screen.Setup) {
        viewModel.onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .concreteWall()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .consumeWindowInsets(WindowInsets.navigationBars)
            .consumeWindowInsets(WindowInsets.ime)
    ) {
        AnimatedContent(
            targetState = state.screen,
            transitionSpec = {
                val slide = if (motion.enabled) 40 else 0
                (slideInHorizontally(motion.tween(Motion.SCREEN_TRANSITION)) { slide } +
                    fadeIn(motion.tween(Motion.SCREEN_TRANSITION))) togetherWith
                    (slideOutHorizontally(motion.tween(Motion.SCREEN_TRANSITION)) { -slide } +
                        fadeOut(motion.tween(Motion.SCREEN_TRANSITION)))
            },
            label = "screen",
        ) { screen ->
            when (screen) {
                Screen.Setup -> SetupScreen(
                    state = state.setup,
                    onModeChange = viewModel::setSetupMode,
                    onDigit = viewModel::setupAppendDigit,
                    onDelete = viewModel::setupDeleteDigit,
                    onClear = viewModel::setupClear,
                    onPassphrase = viewModel::setupSetPassphrase,
                    onAcknowledge = viewModel::setupAcknowledge,
                    onContinue = viewModel::setupGoToConfirm,
                    onBackToChoose = viewModel::setupBackToChoose,
                    onConfirmDigit = viewModel::confirmAppendDigit,
                    onConfirmDelete = viewModel::confirmDeleteDigit,
                    onConfirmClear = viewModel::confirmClear,
                    onConfirmPassphrase = viewModel::confirmSetPassphrase,
                    onComplete = viewModel::completeSetup,
                    onFinished = viewModel::setupFinished,
                )

                Screen.Write -> WriteScreen(
                    state = state.write,
                    onTextChanged = viewModel::onTextChanged,
                    onMoodChanged = viewModel::onMoodChanged,
                    onRequestSeal = viewModel::requestSeal,
                    onOpenArchive = viewModel::openUnlock,
                    onOpenSettings = viewModel::openSettings,
                )

                Screen.Unlock -> UnlockScreen(
                    state = state.unlock,
                    onModeChange = viewModel::setUnlockMode,
                    onDigit = viewModel::unlockAppendDigit,
                    onDelete = viewModel::unlockDeleteDigit,
                    onClear = viewModel::unlockClear,
                    onPassphrase = viewModel::unlockSetPassphrase,
                    onSubmit = viewModel::submitPasscode,
                    onBiometric = onRequestBiometricUnlock,
                )

                Screen.Archive -> ArchiveScreen(
                    state = state.archive,
                    onTabChange = viewModel::setArchiveTab,
                    onQueryChange = viewModel::setArchiveQuery,
                    onYearChange = viewModel::setArchiveYear,
                    onOpenEntry = viewModel::openEntry,
                    onCloseEntry = viewModel::closeEntry,
                    onLock = viewModel::lock,
                    onOpenSettings = viewModel::openSettings,
                )

                Screen.Settings -> SettingsScreen(
                    state = state.settings,
                    versionName = versionName,
                    onClose = viewModel::closeSettings,
                    onAutoLock = viewModel::setAutoLock,
                    onReduceMotion = viewModel::setReduceMotion,
                    onTextSize = viewModel::setTextSize,
                    onHeatmap = viewModel::setHeatmapMetadata,
                    onWipeOnFailure = viewModel::setWipeOnFailure,
                    onBiometricToggle = { wanted, _ ->
                        if (wanted) onRequestBiometricEnrolment() else onBiometricDisable()
                    },
                    onChangePasscode = viewModel::changePasscode,
                    onExportEncrypted = viewModel::exportEncrypted,
                    onExportPlain = viewModel::exportPlainText,
                    onInspectImport = viewModel::inspectImport,
                    onCommitImport = viewModel::commitImport,
                )
            }
        }

        if (state.write.showSealConfirm) {
            SealConfirmDialog(
                onDismiss = viewModel::dismissSealConfirm,
                onConfirm = viewModel::confirmSeal,
            )
        }

        SealOverlay(
            visible = state.write.sealAnimating,
            label = stringResource(R.string.write_sealed_stamp),
            onFinished = viewModel::sealAnimationFinished,
        )

        state.message?.let { message ->
            MessageBanner(
                message = message,
                onDismiss = viewModel::clearMessage,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * The one place a transient, app-level message can appear.
 *
 * It always says what happened and, where the caller can, what to do about it. There is no
 * toast anywhere in this app that says "Error."
 */
@Composable
private fun MessageBanner(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = JournalatorTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(colors.surfaceRaised)
            .border(1.dp, colors.warning)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JText(
            text = message,
            style = JournalatorTheme.type.body,
            color = colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        GhostButton(
            label = stringResource(R.string.action_close),
            description = stringResource(R.string.cd_close),
            accent = colors.muted,
            onClick = onDismiss,
        )
    }
}

@Composable
private fun SealConfirmDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val colors = JournalatorTheme.colors
    val type = JournalatorTheme.type
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceRaised,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurface,
        title = {
            JText(
                text = stringResource(R.string.write_seal_confirm_title),
                style = type.displaySmall,
            )
        },
        text = {
            JText(
                text = stringResource(R.string.write_seal_confirm_body),
                style = type.body,
            )
        },
        confirmButton = {
            PrimaryButton(
                label = stringResource(R.string.write_seal_confirm_action),
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
