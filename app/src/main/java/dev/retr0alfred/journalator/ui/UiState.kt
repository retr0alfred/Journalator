package dev.retr0alfred.journalator.ui

import androidx.compose.runtime.Immutable
import dev.retr0alfred.journalator.crypto.PasscodeStrength
import dev.retr0alfred.journalator.data.DecryptedEntry
import dev.retr0alfred.journalator.settings.Settings
import java.time.LocalDate

/** A harmless placeholder until the real date is loaded; never shown to the user. */
private val EPOCH_DATE: LocalDate = LocalDate.of(1970, 1, 1)

/**
 * Navigation is a sealed class in one view model, not a navigation library.
 *
 * Five screens with no deep links, no arguments and no back stack worth the name do not
 * justify a graph DSL, a compiler plugin and a dependency that changes shape every year.
 * A `when` over this type is the whole router.
 */
sealed interface Screen {
    data object Setup : Screen
    data object Write : Screen
    data object Unlock : Screen
    data object Archive : Screen
    data object Settings : Screen
}

enum class SetupStep { CHOOSE, CONFIRM, GENERATING, DONE }

enum class PasscodeMode { PIN, PASSPHRASE }

@Immutable
data class SetupState(
    val step: SetupStep = SetupStep.CHOOSE,
    val mode: PasscodeMode = PasscodeMode.PIN,
    val enteredLength: Int = 0,
    val confirmLength: Int = 0,
    val strength: PasscodeStrength.Result? = null,
    val acknowledged: Boolean = false,
    val mismatch: Boolean = false,
    val error: String? = null,
) {
    val canContinueFromChoose: Boolean
        get() = strength != null &&
            strength.verdict != PasscodeStrength.Verdict.TOO_SHORT &&
            acknowledged

    val canConfirm: Boolean get() = confirmLength > 0
}

@Immutable
data class WriteState(
    val date: LocalDate = EPOCH_DATE,
    val dayNumber: String = "0001",
    val text: String = "",
    val mood: Int? = null,
    val sealedToday: Boolean = false,
    val saving: Boolean = false,
    val savedAtLeastOnce: Boolean = false,
    val showSealConfirm: Boolean = false,
    val sealAnimating: Boolean = false,
    val error: String? = null,
) {
    val wordCount: Int get() = dev.retr0alfred.journalator.data.Sealing.wordCount(text)
    val canSeal: Boolean get() = !sealedToday && text.isNotBlank() && !saving
}

enum class UnlockFailure { WRONG_PASSCODE, LOCKED_OUT, BIOMETRIC_FAILED, WIPED }

@Immutable
data class UnlockState(
    val mode: PasscodeMode = PasscodeMode.PIN,
    val enteredLength: Int = 0,
    val busy: Boolean = false,
    val failure: UnlockFailure? = null,
    val failureDetail: String? = null,
    val attemptsLeft: Int = 4,
    val lockedRemainingMillis: Long = 0L,
    val glitchTrigger: Int = 0,
    val successProgress: Float = 0f,
    val biometricOffered: Boolean = false,
) {
    val lockedOut: Boolean get() = lockedRemainingMillis > 0L
}

/**
 * The named ways a backup operation can fail.
 *
 * The view model reports one of these rather than an English sentence: user-facing wording
 * belongs in `strings.xml`, and a string baked into a view model is a string that can never
 * be translated.
 */
enum class BackupOutcome { UNLOCK_FIRST, NO_STORAGE_APP, BAD_PASSPHRASE, BAD_BACKUP, FAILED }

enum class ArchiveTab { LIST, CALENDAR }

@Immutable
data class ArchiveState(
    val tab: ArchiveTab = ArchiveTab.LIST,
    val entries: List<DecryptedEntry> = emptyList(),
    val writtenDates: Set<LocalDate> = emptySet(),
    val query: String = "",
    val year: Int = EPOCH_DATE.year,
    val open: DecryptedEntry? = null,
    val loading: Boolean = false,
    val error: String? = null,
) {
    /**
     * Search is a linear scan over the decrypted list held in memory for as long as the
     * archive is unlocked. There is deliberately no index on disk: an index is a plaintext
     * keyword list, which would hand an attacker the contents of every entry in summary form.
     */
    val visible: List<DecryptedEntry>
        get() = if (query.isBlank()) entries else entries.filter {
            it.text.contains(query, ignoreCase = true)
        }
}

@Immutable
data class SettingsState(
    val settings: Settings = Settings(),
    val entryCount: Int = 0,
    val biometricSupported: Boolean = false,
    val busy: Boolean = false,
    val notice: String? = null,
)

@Immutable
data class AppUiState(
    val screen: Screen = Screen.Write,
    val booting: Boolean = true,
    val setup: SetupState = SetupState(),
    val write: WriteState = WriteState(),
    val unlock: UnlockState = UnlockState(),
    val archive: ArchiveState = ArchiveState(),
    val settings: SettingsState = SettingsState(),
    val message: String? = null,
)
