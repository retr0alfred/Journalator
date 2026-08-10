package dev.retr0alfred.journalator.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.retr0alfred.journalator.R
import dev.retr0alfred.journalator.backup.BackupEntry
import dev.retr0alfred.journalator.backup.JrnlxArchive
import dev.retr0alfred.journalator.settings.AppSettings
import dev.retr0alfred.journalator.settings.AutoLockDelay
import dev.retr0alfred.journalator.settings.TextSize
import dev.retr0alfred.journalator.ui.BackupOutcome
import dev.retr0alfred.journalator.ui.SettingsState
import dev.retr0alfred.journalator.ui.components.GhostButton
import dev.retr0alfred.journalator.ui.components.HalftoneBand
import dev.retr0alfred.journalator.ui.components.JTextField
import dev.retr0alfred.journalator.ui.components.PrimaryButton
import dev.retr0alfred.journalator.ui.components.StencilHeader
import dev.retr0alfred.journalator.ui.theme.JText
import dev.retr0alfred.journalator.ui.theme.JournalatorTheme
import java.io.InputStream
import java.io.OutputStream

/**
 * Settings, and the only place the app explains itself.
 *
 * Anything with a permanent consequence — the wipe switch, the plaintext export — is behind
 * a dialog that states the consequence in the words a person would use, not in the words a
 * lawyer would.
 */
@Composable
fun SettingsScreen(
    state: SettingsState,
    versionName: String,
    onClose: () -> Unit,
    onAutoLock: (AutoLockDelay) -> Unit,
    onReduceMotion: (Boolean) -> Unit,
    onTextSize: (TextSize) -> Unit,
    onHeatmap: (Boolean) -> Unit,
    onWipeOnFailure: (Boolean) -> Unit,
    onBiometricToggle: (Boolean, CharArray?) -> Unit,
    onChangePasscode: (CharArray, CharArray, (Boolean, String?) -> Unit) -> Unit,
    onExportEncrypted: (() -> OutputStream?, CharArray, (BackupOutcome?, String?) -> Unit) -> Unit,
    onExportPlain: (() -> OutputStream?, (BackupOutcome?, String?) -> Unit) -> Unit,
    onInspectImport: (
        () -> InputStream?,
        CharArray,
        (List<BackupEntry>?, Int, BackupOutcome?, String?) -> Unit,
    ) -> Unit,
    onCommitImport: (List<BackupEntry>, Boolean, (Int?, BackupOutcome?, String?) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JournalatorTheme.colors
    val type = JournalatorTheme.type
    val context = LocalContext.current

    var notice by remember { mutableStateOf<String?>(null) }
    var showChangePasscode by remember { mutableStateOf(false) }
    var showWipeConfirm by remember { mutableStateOf(false) }
    var showPlainWarning by remember { mutableStateOf(false) }
    var showLicences by remember { mutableStateOf(false) }
    var backupPassphrase by remember { mutableStateOf("") }
    var pendingImport by remember { mutableStateOf<List<BackupEntry>?>(null) }
    var pendingCollisions by remember { mutableIntStateOf(0) }

    val exportEncryptedLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(JrnlxArchive.MIME_TYPE)
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val passphrase = backupPassphrase.toCharArray()
        onExportEncrypted(
            { context.contentResolver.openOutputStream(uri) },
            passphrase,
        ) { outcome, detail ->
            notice = outcome?.let { context.describe(it, detail, exporting = true) }
                ?: context.getString(R.string.settings_export_ok, "jrnlx")
        }
    }

    val exportPlainLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        onExportPlain({ context.contentResolver.openOutputStream(uri) }) { outcome, detail ->
            notice = outcome?.let { context.describe(it, detail, exporting = true) }
                ?: context.getString(R.string.settings_export_ok, "txt")
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        onInspectImport(
            { context.contentResolver.openInputStream(uri) },
            backupPassphrase.toCharArray(),
        ) { entries, collisions, outcome, detail ->
            if (outcome != null || entries == null) {
                notice = context.describe(
                    outcome ?: BackupOutcome.BAD_BACKUP,
                    detail,
                    exporting = false,
                )
            } else {
                pendingImport = entries
                pendingCollisions = collisions
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            StencilHeader(text = stringResource(R.string.settings_title), modifier = Modifier.weight(1f))
            GhostButton(
                label = stringResource(R.string.action_close),
                description = stringResource(R.string.cd_close),
                onClick = onClose,
                accent = colors.muted,
            )
        }

        Section(title = stringResource(R.string.settings_section_security))

        SettingRow(
            title = stringResource(R.string.settings_change_passcode),
            body = stringResource(R.string.settings_change_passcode_body),
        ) {
            GhostButton(
                label = stringResource(R.string.action_continue),
                onClick = { showChangePasscode = true },
            )
        }

        ChoiceRow(
            title = stringResource(R.string.settings_autolock),
            options = listOf(
                AutoLockDelay.IMMEDIATE to stringResource(R.string.settings_autolock_0),
                AutoLockDelay.THIRTY_SECONDS to stringResource(R.string.settings_autolock_30),
                AutoLockDelay.TWO_MINUTES to stringResource(R.string.settings_autolock_120),
            ),
            selected = state.settings.autoLock,
            onSelect = onAutoLock,
        )

        ToggleRow(
            title = stringResource(R.string.settings_biometric),
            body = if (state.biometricSupported) stringResource(R.string.settings_biometric_body)
            else stringResource(R.string.settings_biometric_unavailable),
            checked = state.settings.biometricEnabled,
            enabled = state.biometricSupported,
            onChange = { onBiometricToggle(it, null) },
        )

        ToggleRow(
            title = stringResource(R.string.settings_wipe_on_failure),
            body = pluralStringResource(
                R.plurals.settings_wipe_on_failure_body,
                AppSettings.WIPE_THRESHOLD,
                AppSettings.WIPE_THRESHOLD,
            ),
            checked = state.settings.wipeOnFailure,
            onChange = { wanted ->
                if (wanted) showWipeConfirm = true else onWipeOnFailure(false)
            },
        )

        Section(title = stringResource(R.string.settings_section_reading))

        ToggleRow(
            title = stringResource(R.string.settings_reduce_motion),
            body = stringResource(R.string.settings_reduce_motion_body),
            checked = state.settings.reduceMotion,
            onChange = onReduceMotion,
        )

        ChoiceRow(
            title = stringResource(R.string.settings_font_size),
            options = listOf(
                TextSize.SMALL to stringResource(R.string.settings_font_small),
                TextSize.MEDIUM to stringResource(R.string.settings_font_medium),
                TextSize.LARGE to stringResource(R.string.settings_font_large),
            ),
            selected = state.settings.textSize,
            onSelect = onTextSize,
        )

        ToggleRow(
            title = stringResource(R.string.settings_heatmap),
            body = stringResource(R.string.settings_heatmap_body),
            checked = state.settings.heatmapMetadata,
            onChange = onHeatmap,
        )

        JText(
            text = stringResource(R.string.settings_immutable_note),
            style = type.meta,
            color = colors.muted,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        Section(title = stringResource(R.string.settings_section_data))

        JText(
            text = stringResource(R.string.settings_export_passphrase_body),
            style = type.meta,
            color = colors.muted,
        )
        Spacer(Modifier.height(8.dp))
        JTextField(
            value = backupPassphrase,
            onValueChange = { backupPassphrase = it },
            label = stringResource(R.string.settings_export_passphrase),
            password = true,
        )
        Spacer(Modifier.height(12.dp))

        SettingRow(
            title = stringResource(R.string.settings_export_encrypted),
            body = stringResource(R.string.settings_export_encrypted_body),
        ) {
            GhostButton(
                label = stringResource(R.string.action_continue),
                enabled = backupPassphrase.isNotEmpty(),
                onClick = { exportEncryptedLauncher.launch("journalator-backup.jrnlx") },
            )
        }

        SettingRow(
            title = stringResource(R.string.settings_import),
            body = stringResource(R.string.settings_import_body),
        ) {
            GhostButton(
                label = stringResource(R.string.action_continue),
                enabled = backupPassphrase.isNotEmpty(),
                onClick = { importLauncher.launch(arrayOf("*/*")) },
            )
        }

        SettingRow(
            title = stringResource(R.string.settings_export_plain),
            body = stringResource(R.string.settings_export_plain_body),
        ) {
            GhostButton(
                label = stringResource(R.string.action_continue),
                accent = colors.warning,
                onClick = { showPlainWarning = true },
            )
        }

        Section(title = stringResource(R.string.settings_section_about))

        JText(
            text = stringResource(R.string.settings_security_summary_title),
            style = type.header,
            color = colors.secondary,
        )
        Spacer(Modifier.height(8.dp))
        JText(
            text = stringResource(R.string.settings_security_summary),
            style = type.body,
            color = colors.onSurface,
        )
        Spacer(Modifier.height(12.dp))
        JText(
            text = stringResource(R.string.settings_nonstandard_note),
            style = type.meta,
            color = colors.muted,
        )
        Spacer(Modifier.height(12.dp))
        JText(
            text = stringResource(R.string.settings_version, versionName),
            style = type.meta,
            color = colors.muted,
        )
        Spacer(Modifier.height(12.dp))
        GhostButton(
            label = stringResource(R.string.settings_licences),
            onClick = { showLicences = true },
            accent = colors.muted,
        )

        notice?.let { text ->
            Spacer(Modifier.height(16.dp))
            JText(text = text, style = type.body, color = colors.warning)
        }

        Spacer(Modifier.height(32.dp))
    }

    if (showChangePasscode) {
        ChangePasscodeDialog(
            onDismiss = { showChangePasscode = false },
            onSubmit = { current, next ->
                onChangePasscode(current, next) { ok, error ->
                    notice = when {
                        ok -> context.getString(R.string.settings_change_done)
                        error != null -> context.getString(R.string.error_generic, error)
                        else -> context.getString(R.string.settings_change_wrong)
                    }
                    if (ok) showChangePasscode = false
                }
            },
        )
    }

    if (showWipeConfirm) {
        TypeToConfirmDialog(
            title = stringResource(R.string.settings_wipe_confirm_title),
            body = pluralStringResource(
                R.plurals.settings_wipe_confirm_body,
                AppSettings.WIPE_THRESHOLD,
                AppSettings.WIPE_THRESHOLD,
            ),
            word = stringResource(R.string.settings_wipe_confirm_word),
            fieldLabel = stringResource(R.string.settings_wipe_confirm_field),
            onDismiss = { showWipeConfirm = false },
            onConfirmed = {
                showWipeConfirm = false
                onWipeOnFailure(true)
            },
        )
    }

    if (showPlainWarning) {
        ConfirmDialog(
            title = stringResource(R.string.settings_export_plain_warning_title),
            body = stringResource(R.string.settings_export_plain_warning_body),
            confirmLabel = stringResource(R.string.settings_export_plain_warning_action),
            onDismiss = { showPlainWarning = false },
            onConfirm = {
                showPlainWarning = false
                exportPlainLauncher.launch("journalator-export.txt")
            },
        )
    }

    pendingImport?.let { entries ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            containerColor = colors.surfaceRaised,
            titleContentColor = colors.onSurface,
            textContentColor = colors.onSurface,
            title = { JText(stringResource(R.string.settings_import_conflict_title), type.displaySmall) },
            text = {
                JText(
                    pluralStringResource(
                        R.plurals.settings_import_conflict_body,
                        entries.size,
                        entries.size,
                        pendingCollisions,
                    ),
                    type.body,
                )
            },
            confirmButton = {
                GhostButton(
                    label = stringResource(R.string.settings_import_take_backup),
                    onClick = {
                        onCommitImport(entries, true) { written, outcome, detail ->
                            notice = context.importResult(written, outcome, detail)
                        }
                        pendingImport = null
                    },
                )
            },
            dismissButton = {
                GhostButton(
                    label = stringResource(R.string.settings_import_keep_mine),
                    accent = colors.muted,
                    onClick = {
                        onCommitImport(entries, false) { written, outcome, detail ->
                            notice = context.importResult(written, outcome, detail)
                        }
                        pendingImport = null
                    },
                )
            },
        )
    }

    if (showLicences) {
        AlertDialog(
            onDismissRequest = { showLicences = false },
            containerColor = colors.surfaceRaised,
            titleContentColor = colors.onSurface,
            textContentColor = colors.onSurface,
            title = { JText(stringResource(R.string.settings_licences), type.displaySmall) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    JText(stringResource(R.string.licences_body), type.body)
                }
            },
            confirmButton = {
                GhostButton(
                    label = stringResource(R.string.action_close),
                    onClick = { showLicences = false },
                )
            },
        )
    }
}

/**
 * Turns a [BackupOutcome] into words. The mapping lives here, next to the screen that shows
 * it, rather than in the view model — a view model that knows English cannot be translated.
 */
private fun android.content.Context.describe(
    outcome: BackupOutcome,
    detail: String?,
    exporting: Boolean,
): String = when (outcome) {
    BackupOutcome.UNLOCK_FIRST -> getString(R.string.settings_backup_needs_unlock)
    BackupOutcome.NO_STORAGE_APP -> getString(R.string.error_no_storage_app)
    BackupOutcome.BAD_PASSPHRASE -> getString(R.string.settings_import_bad_passphrase)
    BackupOutcome.BAD_BACKUP -> getString(R.string.settings_import_bad_file)
    BackupOutcome.FAILED -> if (exporting) {
        getString(R.string.settings_export_failed, detail.orEmpty())
    } else {
        getString(R.string.settings_import_failed, detail.orEmpty())
    }
}

private fun android.content.Context.importResult(
    written: Int?,
    outcome: BackupOutcome?,
    detail: String?,
): String = if (outcome != null) {
    describe(outcome, detail, exporting = false)
} else {
    resources.getQuantityString(R.plurals.settings_import_ok, written ?: 0, written ?: 0)
}

// ---------------------------------------------------------------- building blocks

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(20.dp))
    HalftoneBand(modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(10.dp))
    JText(text = title, style = JournalatorTheme.type.header, color = JournalatorTheme.colors.primary)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun SettingRow(title: String, body: String, action: @Composable () -> Unit) {
    val colors = JournalatorTheme.colors
    val type = JournalatorTheme.type
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            JText(text = title, style = type.bodyStrong, color = colors.onSurface)
            Spacer(Modifier.height(4.dp))
            JText(text = body, style = type.meta, color = colors.muted)
        }
        Spacer(Modifier.width(12.dp))
        action()
    }
}

@Composable
private fun ToggleRow(
    title: String,
    body: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val colors = JournalatorTheme.colors
    val type = JournalatorTheme.type
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onChange,
            )
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            JText(text = title, style = type.bodyStrong, color = colors.onSurface)
            Spacer(Modifier.height(4.dp))
            JText(text = body, style = type.meta, color = colors.muted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onPrimary,
                checkedTrackColor = colors.primary,
                uncheckedThumbColor = colors.muted,
                uncheckedTrackColor = colors.surfaceRaised,
            ),
        )
    }
}

@Composable
private fun <T> ChoiceRow(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    val colors = JournalatorTheme.colors
    val type = JournalatorTheme.type
    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        JText(text = title, style = type.bodyStrong, color = colors.onSurface)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            options.forEach { (value, label) ->
                val isSelected = value == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = { onSelect(value) },
                        )
                        .background(if (isSelected) colors.primary else colors.surfaceRaised),
                    contentAlignment = Alignment.Center,
                ) {
                    JText(
                        text = label,
                        style = type.meta,
                        color = if (isSelected) colors.onPrimary else colors.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = JournalatorTheme.colors
    val type = JournalatorTheme.type
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceRaised,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurface,
        title = { JText(title, type.displaySmall) },
        text = { JText(body, type.body) },
        confirmButton = {
            PrimaryButton(label = confirmLabel, onClick = onConfirm)
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

/** For the irreversible switch: the user has to type the word, not just tap twice. */
@Composable
private fun TypeToConfirmDialog(
    title: String,
    body: String,
    word: String,
    fieldLabel: String,
    onDismiss: () -> Unit,
    onConfirmed: () -> Unit,
) {
    val colors = JournalatorTheme.colors
    val type = JournalatorTheme.type
    var typed by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceRaised,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurface,
        title = { JText(title, type.displaySmall) },
        text = {
            Column {
                JText(body, type.body)
                Spacer(Modifier.height(12.dp))
                JTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = fieldLabel,
                )
            }
        },
        confirmButton = {
            PrimaryButton(
                label = stringResource(R.string.action_confirm),
                enabled = typed.trim() == word,
                containerColor = colors.warning,
                onClick = onConfirmed,
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

@Composable
private fun ChangePasscodeDialog(
    onDismiss: () -> Unit,
    onSubmit: (CharArray, CharArray) -> Unit,
) {
    val colors = JournalatorTheme.colors
    val type = JournalatorTheme.type
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceRaised,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurface,
        title = { JText(stringResource(R.string.settings_change_passcode), type.displaySmall) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                JTextField(
                    value = current,
                    onValueChange = { current = it },
                    label = stringResource(R.string.settings_change_current),
                    password = true,
                )
                Spacer(Modifier.height(10.dp))
                JTextField(
                    value = next,
                    onValueChange = { next = it },
                    label = stringResource(R.string.settings_change_new),
                    password = true,
                )
                Spacer(Modifier.height(10.dp))
                JTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = stringResource(R.string.settings_change_confirm),
                    password = true,
                )
            }
        },
        confirmButton = {
            PrimaryButton(
                label = stringResource(R.string.action_confirm),
                enabled = current.isNotEmpty() && next.length >= 6 && next == confirm,
                onClick = { onSubmit(current.toCharArray(), next.toCharArray()) },
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
