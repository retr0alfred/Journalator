package dev.retr0alfred.journalator.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.retr0alfred.journalator.AppContainer
import dev.retr0alfred.journalator.backup.BackupCorruptException
import dev.retr0alfred.journalator.backup.BackupEntry
import dev.retr0alfred.journalator.backup.BackupPassphraseException
import dev.retr0alfred.journalator.backup.JrnlxArchive
import dev.retr0alfred.journalator.backup.PlainTextExport
import dev.retr0alfred.journalator.crypto.Backoff
import dev.retr0alfred.journalator.crypto.PasscodeStrength
import dev.retr0alfred.journalator.crypto.SecureMemory
import dev.retr0alfred.journalator.crypto.Vault
import dev.retr0alfred.journalator.crypto.WrongPasscodeException
import dev.retr0alfred.journalator.data.Sealing
import dev.retr0alfred.journalator.settings.AppSettings
import dev.retr0alfred.journalator.settings.AutoLockDelay
import dev.retr0alfred.journalator.settings.TextSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.security.PrivateKey
import java.security.PublicKey
import java.time.LocalDate
import java.time.ZoneId
import javax.crypto.Cipher

/**
 * The whole app's state, in one view model.
 *
 * Everything expensive — key derivation, RSA, database access — runs off the main thread.
 * The decrypted private key lives in a single nullable field that [lock] clears, and every
 * decrypted entry is dropped at the same moment, so backgrounding the app genuinely removes
 * the plaintext from memory rather than merely hiding the screen.
 */
class JournalViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    val settingsFlow: StateFlow<dev.retr0alfred.journalator.settings.Settings> =
        container.settings.state

    /** Non-null exactly while the archive is unlocked. Cleared by [lock]. */
    private var privateKey: PrivateKey? = null
    private var cachedPublicKey: PublicKey? = null

    /** Passcode buffers. Held as CharArray and wiped; never turned into a String. */
    private var setupPasscode: CharArray = CharArray(0)
    private var setupConfirm: CharArray = CharArray(0)
    private var unlockBuffer: CharArray = CharArray(0)

    private var autosaveJob: Job? = null
    private var lockCountdownJob: Job? = null
    private var lockoutTickerJob: Job? = null

    private val zone: ZoneId get() = ZoneId.systemDefault()

    private fun today(): LocalDate = Sealing.today(zone)

    private fun now(): Long = System.currentTimeMillis()

    // ================================================================ lifecycle

    init {
        viewModelScope.launch { boot() }
    }

    private suspend fun boot() {
        val initialised = withContext(Dispatchers.IO) { container.vault.isInitialised }
        if (!initialised) {
            _state.update { it.copy(booting = false, screen = Screen.Setup) }
            return
        }
        loadPublicKey()
        sealStaleDraftIfAny()
        loadToday()
        refreshSettingsState()
        _state.update { it.copy(booting = false, screen = Screen.Write) }
    }

    /** Called from the activity's ON_RESUME. Lazy sealing runs here as well as at start. */
    fun onResumed() {
        viewModelScope.launch {
            if (!withContext(Dispatchers.IO) { container.vault.isInitialised }) return@launch
            if (sealStaleDraftIfAny()) loadToday()
            refreshLockoutState()
        }
    }

    /**
     * Called from the activity's ON_STOP. Everything sensitive goes now — not on a timer
     * that a killed process would never fire.
     */
    fun onStopped() {
        flushDraftImmediately()
        when (container.settings.current.autoLock) {
            AutoLockDelay.IMMEDIATE -> lock()
            else -> scheduleDelayedLock(container.settings.current.autoLock.seconds)
        }
    }

    fun onStarted() {
        lockCountdownJob?.cancel()
        lockCountdownJob = null
    }

    private fun scheduleDelayedLock(seconds: Int) {
        lockCountdownJob?.cancel()
        lockCountdownJob = viewModelScope.launch {
            delay(seconds * 1000L)
            lock()
        }
    }

    override fun onCleared() {
        lock()
        SecureMemory.wipe(setupPasscode)
        SecureMemory.wipe(setupConfirm)
        SecureMemory.wipe(unlockBuffer)
        super.onCleared()
    }

    // ================================================================ setup

    fun setSetupMode(mode: PasscodeMode) {
        SecureMemory.wipe(setupPasscode)
        setupPasscode = CharArray(0)
        _state.update {
            it.copy(setup = it.setup.copy(mode = mode, enteredLength = 0, strength = null))
        }
    }

    fun setupAppendDigit(digit: Char) = updateSetupPasscode(setupPasscode + digit)

    fun setupDeleteDigit() {
        if (setupPasscode.isNotEmpty()) {
            updateSetupPasscode(setupPasscode.copyOf(setupPasscode.size - 1))
        }
    }

    fun setupClear() = updateSetupPasscode(CharArray(0))

    fun setupSetPassphrase(value: CharArray) = updateSetupPasscode(value)

    private fun updateSetupPasscode(next: CharArray) {
        SecureMemory.wipe(setupPasscode)
        setupPasscode = next
        val mode = _state.value.setup.mode
        val strength = if (next.isEmpty()) null else PasscodeStrength.evaluate(
            passcode = next,
            numericOnly = mode == PasscodeMode.PIN,
            kdfIterations = dev.retr0alfred.journalator.crypto.Pbkdf2.MIN_ITERATIONS,
        )
        _state.update {
            it.copy(setup = it.setup.copy(enteredLength = next.size, strength = strength))
        }
    }

    fun setupAcknowledge(value: Boolean) {
        _state.update { it.copy(setup = it.setup.copy(acknowledged = value)) }
    }

    fun setupGoToConfirm() {
        SecureMemory.wipe(setupConfirm)
        setupConfirm = CharArray(0)
        _state.update {
            it.copy(setup = it.setup.copy(step = SetupStep.CONFIRM, confirmLength = 0, mismatch = false))
        }
    }

    fun setupBackToChoose() {
        SecureMemory.wipe(setupConfirm)
        setupConfirm = CharArray(0)
        _state.update {
            it.copy(setup = it.setup.copy(step = SetupStep.CHOOSE, confirmLength = 0, mismatch = false))
        }
    }

    fun confirmAppendDigit(digit: Char) = updateSetupConfirm(setupConfirm + digit)

    fun confirmDeleteDigit() {
        if (setupConfirm.isNotEmpty()) {
            updateSetupConfirm(setupConfirm.copyOf(setupConfirm.size - 1))
        }
    }

    fun confirmClear() = updateSetupConfirm(CharArray(0))

    fun confirmSetPassphrase(value: CharArray) = updateSetupConfirm(value)

    private fun updateSetupConfirm(next: CharArray) {
        SecureMemory.wipe(setupConfirm)
        setupConfirm = next
        _state.update {
            it.copy(setup = it.setup.copy(confirmLength = next.size, mismatch = false))
        }
    }

    fun completeSetup() {
        if (!SecureMemory.contentEquals(setupPasscode, setupConfirm)) {
            _state.update { it.copy(setup = it.setup.copy(mismatch = true)) }
            return
        }
        _state.update { it.copy(setup = it.setup.copy(step = SetupStep.GENERATING, error = null)) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.Default) {
                    container.vault.create(setupPasscode)
                }
                SecureMemory.wipe(setupPasscode)
                SecureMemory.wipe(setupConfirm)
                setupPasscode = CharArray(0)
                setupConfirm = CharArray(0)
                loadPublicKey()
                loadToday()
                refreshSettingsState()
                _state.update { it.copy(setup = it.setup.copy(step = SetupStep.DONE)) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        setup = it.setup.copy(
                            step = SetupStep.CHOOSE,
                            error = e.readableMessage(),
                        )
                    )
                }
            }
        }
    }

    fun setupFinished() {
        _state.update { it.copy(screen = Screen.Write) }
    }

    // ================================================================ write

    private suspend fun loadPublicKey() {
        cachedPublicKey = withContext(Dispatchers.IO) {
            runCatching { container.vault.publicKey() }.getOrNull()
        }
    }

    /** @return true when something was sealed, so the caller knows to reload today. */
    private suspend fun sealStaleDraftIfAny(): Boolean {
        val key = cachedPublicKey ?: return false
        val sealed = runCatching {
            container.repository.sealStaleDraft(
                today = today(),
                publicKey = key,
                storeMetadataInClear = container.settings.current.heatmapMetadata,
                nowEpochMillis = now(),
            )
        }.getOrNull()
        return sealed != null
    }

    private suspend fun loadToday() {
        val today = today()
        val draft = runCatching { container.repository.loadDraft() }.getOrNull()
        val sealedToday = runCatching { container.repository.hasSealedToday(today) }
            .getOrDefault(false)
        val first = runCatching { container.repository.firstEntryDate() }.getOrNull()
        val relevantDraft = draft?.takeIf { it.date == today }
        _state.update {
            it.copy(
                write = it.write.copy(
                    date = today,
                    dayNumber = Sealing.dayNumber(first ?: today, today),
                    text = relevantDraft?.text.orEmpty(),
                    mood = relevantDraft?.mood,
                    sealedToday = sealedToday,
                    savedAtLeastOnce = relevantDraft != null,
                    error = null,
                )
            )
        }
    }

    fun onTextChanged(value: String) {
        _state.update { it.copy(write = it.write.copy(text = value)) }
        scheduleAutosave()
    }

    fun onMoodChanged(mood: Int?) {
        _state.update { it.copy(write = it.write.copy(mood = mood)) }
        scheduleAutosave()
    }

    /**
     * 400 ms debounce. Long enough that a fast typist does not trigger a database write per
     * keystroke, short enough that nothing meaningful is lost to a crash. Every lifecycle
     * pause also forces an immediate flush, so the debounce window is never the last word.
     */
    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(AUTOSAVE_DEBOUNCE_MILLIS)
            persistDraft()
        }
    }

    fun flushDraftImmediately() {
        autosaveJob?.cancel()
        viewModelScope.launch { persistDraft() }
    }

    private suspend fun persistDraft() {
        val write = _state.value.write
        if (write.sealedToday) return
        _state.update { it.copy(write = it.write.copy(saving = true)) }
        try {
            container.repository.saveDraft(
                date = write.date,
                zone = zone,
                text = write.text,
                mood = write.mood,
                nowEpochMillis = now(),
            )
            _state.update {
                it.copy(write = it.write.copy(saving = false, savedAtLeastOnce = true, error = null))
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(write = it.write.copy(saving = false, error = e.readableMessage()))
            }
        }
    }

    fun requestSeal() {
        _state.update { it.copy(write = it.write.copy(showSealConfirm = true)) }
    }

    fun dismissSealConfirm() {
        _state.update { it.copy(write = it.write.copy(showSealConfirm = false)) }
    }

    fun confirmSeal() {
        val write = _state.value.write
        val key = cachedPublicKey ?: return
        _state.update {
            it.copy(write = it.write.copy(showSealConfirm = false, saving = true))
        }
        viewModelScope.launch {
            try {
                container.repository.sealToday(
                    today = write.date,
                    zone = zone,
                    text = write.text,
                    mood = write.mood,
                    publicKey = key,
                    storeMetadataInClear = container.settings.current.heatmapMetadata,
                    nowEpochMillis = now(),
                )
                _state.update {
                    it.copy(
                        write = it.write.copy(
                            saving = false,
                            sealedToday = true,
                            sealAnimating = true,
                            text = "",
                        )
                    )
                }
                refreshSettingsState()
            } catch (e: Exception) {
                _state.update {
                    it.copy(write = it.write.copy(saving = false, error = e.readableMessage()))
                }
            }
        }
    }

    fun sealAnimationFinished() {
        _state.update { it.copy(write = it.write.copy(sealAnimating = false)) }
    }

    // ================================================================ unlock

    fun openUnlock() {
        viewModelScope.launch {
            refreshLockoutState()
            val biometricReady = withContext(Dispatchers.IO) {
                container.settings.current.biometricEnabled &&
                    runCatching { container.vault.config().hasBiometric }.getOrDefault(false)
            }
            _state.update {
                it.copy(
                    screen = Screen.Unlock,
                    unlock = it.unlock.copy(
                        failure = null,
                        enteredLength = 0,
                        successProgress = 0f,
                        biometricOffered = biometricReady,
                    ),
                )
            }
        }
    }

    fun setUnlockMode(mode: PasscodeMode) {
        SecureMemory.wipe(unlockBuffer)
        unlockBuffer = CharArray(0)
        _state.update { it.copy(unlock = it.unlock.copy(mode = mode, enteredLength = 0)) }
    }

    fun unlockAppendDigit(digit: Char) = updateUnlockBuffer(unlockBuffer + digit)

    fun unlockDeleteDigit() {
        if (unlockBuffer.isNotEmpty()) updateUnlockBuffer(unlockBuffer.copyOf(unlockBuffer.size - 1))
    }

    fun unlockClear() = updateUnlockBuffer(CharArray(0))

    fun unlockSetPassphrase(value: CharArray) = updateUnlockBuffer(value)

    private fun updateUnlockBuffer(next: CharArray) {
        SecureMemory.wipe(unlockBuffer)
        unlockBuffer = next
        _state.update { it.copy(unlock = it.unlock.copy(enteredLength = next.size, failure = null)) }
    }

    private suspend fun refreshLockoutState() {
        val failure = withContext(Dispatchers.IO) { container.failureStore.read() }
        val remaining = failure.remainingMillis(now())
        _state.update {
            it.copy(
                unlock = it.unlock.copy(
                    lockedRemainingMillis = remaining,
                    attemptsLeft = Backoff.attemptsBeforeDelay(failure),
                )
            )
        }
        if (remaining > 0) startLockoutTicker()
    }

    private fun startLockoutTicker() {
        lockoutTickerJob?.cancel()
        lockoutTickerJob = viewModelScope.launch {
            while (true) {
                val failure = withContext(Dispatchers.IO) { container.failureStore.read() }
                val remaining = failure.remainingMillis(now())
                _state.update { it.copy(unlock = it.unlock.copy(lockedRemainingMillis = remaining)) }
                if (remaining <= 0L) break
                delay(500)
            }
        }
    }

    fun submitPasscode() {
        val current = _state.value.unlock
        if (current.busy || current.lockedOut || unlockBuffer.isEmpty()) return
        _state.update { it.copy(unlock = it.unlock.copy(busy = true, failure = null)) }

        viewModelScope.launch {
            val stored = withContext(Dispatchers.IO) { container.failureStore.read() }
            if (stored.isLockedOut(now())) {
                _state.update {
                    it.copy(
                        unlock = it.unlock.copy(
                            busy = false,
                            failure = UnlockFailure.LOCKED_OUT,
                            lockedRemainingMillis = stored.remainingMillis(now()),
                        )
                    )
                }
                startLockoutTicker()
                return@launch
            }

            val attempt = withContext(Dispatchers.Default) {
                runCatching { container.vault.unlock(unlockBuffer) }
            }
            SecureMemory.wipe(unlockBuffer)
            unlockBuffer = CharArray(0)

            attempt.fold(
                onSuccess = { key ->
                    withContext(Dispatchers.IO) { container.failureStore.write(Backoff.onSuccess()) }
                    privateKey = key
                    _state.update {
                        it.copy(
                            unlock = it.unlock.copy(
                                busy = false,
                                enteredLength = 0,
                                successProgress = 1f,
                                failure = null,
                            )
                        )
                    }
                    openArchive()
                },
                onFailure = { error ->
                    if (error !is WrongPasscodeException) {
                        _state.update {
                            it.copy(
                                unlock = it.unlock.copy(
                                    busy = false,
                                    enteredLength = 0,
                                    failure = UnlockFailure.BIOMETRIC_FAILED,
                                    failureDetail = error.readableMessage(),
                                    glitchTrigger = it.unlock.glitchTrigger + 1,
                                )
                            )
                        }
                        return@fold
                    }
                    registerFailedAttempt()
                },
            )
        }
    }

    private suspend fun registerFailedAttempt() {
        val next = Backoff.onFailure(
            withContext(Dispatchers.IO) { container.failureStore.read() },
            now(),
        )
        withContext(Dispatchers.IO) { container.failureStore.write(next) }

        val wipeArmed = container.settings.current.wipeOnFailure &&
            next.consecutiveFailures >= AppSettings.WIPE_THRESHOLD
        if (wipeArmed) {
            withContext(Dispatchers.IO) { container.repository.wipeEverything() }
            withContext(Dispatchers.IO) { container.failureStore.clear() }
            _state.update {
                it.copy(
                    unlock = it.unlock.copy(
                        busy = false,
                        enteredLength = 0,
                        failure = UnlockFailure.WIPED,
                        glitchTrigger = it.unlock.glitchTrigger + 1,
                        lockedRemainingMillis = 0L,
                    )
                )
            }
            return
        }

        _state.update {
            it.copy(
                unlock = it.unlock.copy(
                    busy = false,
                    enteredLength = 0,
                    failure = if (next.isLockedOut(now())) UnlockFailure.LOCKED_OUT
                    else UnlockFailure.WRONG_PASSCODE,
                    attemptsLeft = Backoff.attemptsBeforeDelay(next),
                    lockedRemainingMillis = next.remainingMillis(now()),
                    glitchTrigger = it.unlock.glitchTrigger + 1,
                )
            )
        }
        if (next.isLockedOut(now())) startLockoutTicker()
    }

    // ---- biometrics -------------------------------------------------------

    /** @return the cipher to hand to `BiometricPrompt`, or null when unavailable. */
    fun biometricUnlockCipher(): Cipher? = runCatching {
        val config = container.vault.config()
        val iv = config.biometricIv ?: return null
        container.biometricKey.decryptCipher(iv)
    }.getOrNull()

    fun completeBiometricUnlock(cipher: Cipher) {
        viewModelScope.launch {
            try {
                val config = withContext(Dispatchers.IO) { container.vault.config() }
                val blob = config.biometricBlob ?: return@launch
                val pkcs8 = withContext(Dispatchers.Default) { cipher.doFinal(blob) }
                try {
                    privateKey = withContext(Dispatchers.Default) {
                        dev.retr0alfred.journalator.crypto.KeyForge.privateKeyFromPkcs8(pkcs8)
                    }
                } finally {
                    SecureMemory.wipe(pkcs8)
                }
                withContext(Dispatchers.IO) { container.failureStore.write(Backoff.onSuccess()) }
                _state.update { it.copy(unlock = it.unlock.copy(successProgress = 1f, failure = null)) }
                openArchive()
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        unlock = it.unlock.copy(
                            failure = UnlockFailure.BIOMETRIC_FAILED,
                            failureDetail = e.readableMessage(),
                            glitchTrigger = it.unlock.glitchTrigger + 1,
                        )
                    )
                }
            }
        }
    }

    fun biometricPromptFailed(detail: String) {
        _state.update {
            it.copy(
                unlock = it.unlock.copy(
                    failure = UnlockFailure.BIOMETRIC_FAILED,
                    failureDetail = detail,
                )
            )
        }
    }

    /** Enrolment: encrypt a second copy of the private key under the biometric-gated key. */
    fun biometricEnrolCipher(): Cipher? =
        runCatching { container.biometricKey.encryptCipher() }.getOrNull()

    fun completeBiometricEnrolment(cipher: Cipher, passcode: CharArray) {
        viewModelScope.launch {
            var pkcs8: ByteArray? = null
            try {
                pkcs8 = withContext(Dispatchers.Default) {
                    container.vault.exportPrivateKeyMaterial(passcode)
                }
                val blob = withContext(Dispatchers.Default) { cipher.doFinal(pkcs8) }
                withContext(Dispatchers.IO) {
                    container.vault.attachBiometricBlob(cipher.iv.copyOf(), blob)
                }
                container.settings.setBiometricEnabled(true)
                notice(null)
            } catch (e: Exception) {
                container.biometricKey.deleteKey()
                container.settings.setBiometricEnabled(false)
                notice(e.readableMessage())
            } finally {
                SecureMemory.wipe(pkcs8)
                SecureMemory.wipe(passcode)
            }
            refreshSettingsState()
        }
    }

    fun disableBiometrics() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { container.vault.clearBiometricBlob() } }
            container.biometricKey.deleteKey()
            container.settings.setBiometricEnabled(false)
            refreshSettingsState()
        }
    }

    // ================================================================ archive

    private fun openArchive() {
        _state.update {
            it.copy(
                screen = Screen.Archive,
                archive = it.archive.copy(loading = true, year = today().year),
            )
        }
        viewModelScope.launch { reloadArchive() }
    }

    private suspend fun reloadArchive() {
        val key = privateKey ?: return
        try {
            val entries = container.repository.decryptAll(key)
            _state.update {
                it.copy(
                    archive = it.archive.copy(
                        entries = entries,
                        writtenDates = entries.map { entry -> entry.date }.toSet(),
                        loading = false,
                        error = null,
                    )
                )
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(archive = it.archive.copy(loading = false, error = e.readableMessage()))
            }
        }
    }

    fun setArchiveTab(tab: ArchiveTab) {
        _state.update { it.copy(archive = it.archive.copy(tab = tab)) }
    }

    fun setArchiveQuery(query: String) {
        _state.update { it.copy(archive = it.archive.copy(query = query)) }
    }

    fun setArchiveYear(year: Int) {
        _state.update { it.copy(archive = it.archive.copy(year = year)) }
    }

    fun openEntry(date: LocalDate) {
        val entry = _state.value.archive.entries.firstOrNull { it.date == date } ?: return
        _state.update { it.copy(archive = it.archive.copy(open = entry)) }
    }

    fun closeEntry() {
        _state.update { it.copy(archive = it.archive.copy(open = null)) }
    }

    /**
     * Drops the private key and every decrypted entry. Called on lock, on back out of the
     * archive, and on leaving the app. After this the process holds no plaintext at all.
     */
    fun lock() {
        privateKey = null
        _state.update {
            it.copy(
                screen = if (it.screen == Screen.Setup) Screen.Setup else Screen.Write,
                archive = ArchiveState(),
                unlock = UnlockState(),
            )
        }
    }

    // ================================================================ settings

    fun openSettings() {
        viewModelScope.launch { refreshSettingsState() }
        _state.update { it.copy(screen = Screen.Settings) }
    }

    fun closeSettings() {
        _state.update {
            it.copy(screen = if (privateKey != null) Screen.Archive else Screen.Write)
        }
    }

    private suspend fun refreshSettingsState() {
        val count = runCatching { container.repository.entryCount() }.getOrDefault(0)
        _state.update {
            it.copy(
                settings = it.settings.copy(
                    settings = container.settings.current,
                    entryCount = count,
                )
            )
        }
    }

    fun setBiometricSupported(value: Boolean) {
        _state.update { it.copy(settings = it.settings.copy(biometricSupported = value)) }
    }

    fun setAutoLock(value: AutoLockDelay) {
        container.settings.setAutoLock(value)
        viewModelScope.launch { refreshSettingsState() }
    }

    fun setReduceMotion(value: Boolean) {
        container.settings.setReduceMotion(value)
        viewModelScope.launch { refreshSettingsState() }
    }

    fun setTextSize(value: TextSize) {
        container.settings.setTextSize(value)
        viewModelScope.launch { refreshSettingsState() }
    }

    fun setHeatmapMetadata(value: Boolean) {
        container.settings.setHeatmapMetadata(value)
        viewModelScope.launch { refreshSettingsState() }
    }

    fun setWipeOnFailure(value: Boolean) {
        container.settings.setWipeOnFailure(value)
        viewModelScope.launch { refreshSettingsState() }
    }

    fun changePasscode(current: CharArray, next: CharArray, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.Default) { container.vault.changePasscode(current, next) }
                container.settings.setBiometricEnabled(false)
                container.biometricKey.deleteKey()
                onResult(true, null)
            } catch (e: WrongPasscodeException) {
                onResult(false, null)
            } catch (e: Exception) {
                onResult(false, e.readableMessage())
            } finally {
                SecureMemory.wipe(current)
                SecureMemory.wipe(next)
                refreshSettingsState()
            }
        }
    }

    // ---- backup -----------------------------------------------------------

    /**
     * Export needs the private key, which means the archive must be unlocked. That is not an
     * inconvenience to work around — an export the app could perform while locked would be a
     * way to read the archive without the passcode.
     */
    fun exportEncrypted(
        open: () -> OutputStream?,
        passphrase: CharArray,
        onDone: (BackupOutcome?, String?) -> Unit,
    ) {
        val key = privateKey
        if (key == null) {
            onDone(BackupOutcome.UNLOCK_FIRST, null)
            SecureMemory.wipe(passphrase)
            return
        }
        viewModelScope.launch {
            try {
                val entries = container.repository.exportAll(key)
                withContext(Dispatchers.IO) {
                    val stream = open() ?: throw NoStorageAppException()
                    stream.use { JrnlxArchive.write(it, entries, passphrase) }
                }
                onDone(null, null)
            } catch (e: NoStorageAppException) {
                onDone(BackupOutcome.NO_STORAGE_APP, null)
            } catch (e: Exception) {
                onDone(BackupOutcome.FAILED, e.readableMessage())
            } finally {
                SecureMemory.wipe(passphrase)
            }
        }
    }

    fun exportPlainText(open: () -> OutputStream?, onDone: (BackupOutcome?, String?) -> Unit) {
        val key = privateKey
        if (key == null) {
            onDone(BackupOutcome.UNLOCK_FIRST, null)
            return
        }
        viewModelScope.launch {
            try {
                val entries = container.repository.exportAll(key)
                withContext(Dispatchers.IO) {
                    val stream = open() ?: throw NoStorageAppException()
                    stream.use { PlainTextExport.write(it, entries) }
                }
                onDone(null, null)
            } catch (e: NoStorageAppException) {
                onDone(BackupOutcome.NO_STORAGE_APP, null)
            } catch (e: Exception) {
                onDone(BackupOutcome.FAILED, e.readableMessage())
            }
        }
    }

    /** Step one of import: read and decrypt, then report collisions so the user can decide. */
    fun inspectImport(
        open: () -> InputStream?,
        passphrase: CharArray,
        onResult: (List<BackupEntry>?, Int, BackupOutcome?, String?) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val entries = withContext(Dispatchers.IO) {
                    val stream = open() ?: throw NoStorageAppException()
                    stream.use { JrnlxArchive.read(it, passphrase) }
                }
                val collisions = container.repository.countCollisions(entries)
                onResult(entries, collisions, null, null)
            } catch (e: BackupPassphraseException) {
                onResult(null, 0, BackupOutcome.BAD_PASSPHRASE, null)
            } catch (e: BackupCorruptException) {
                onResult(null, 0, BackupOutcome.BAD_BACKUP, null)
            } catch (e: NoStorageAppException) {
                onResult(null, 0, BackupOutcome.NO_STORAGE_APP, null)
            } catch (e: Exception) {
                onResult(null, 0, BackupOutcome.FAILED, e.readableMessage())
            } finally {
                SecureMemory.wipe(passphrase)
            }
        }
    }

    /** Step two: commit, having been told explicitly what to do about the collisions. */
    fun commitImport(
        entries: List<BackupEntry>,
        overwriteExisting: Boolean,
        onDone: (Int?, BackupOutcome?, String?) -> Unit,
    ) {
        val key = cachedPublicKey
        if (key == null) {
            onDone(null, BackupOutcome.UNLOCK_FIRST, null)
            return
        }
        viewModelScope.launch {
            try {
                val written = container.repository.importAll(
                    entries = entries,
                    publicKey = key,
                    overwriteExisting = overwriteExisting,
                    storeMetadataInClear = container.settings.current.heatmapMetadata,
                )
                if (privateKey != null) reloadArchive()
                refreshSettingsState()
                onDone(written, null, null)
            } catch (e: Exception) {
                onDone(null, BackupOutcome.FAILED, e.readableMessage())
            }
        }
    }

    // ================================================================ misc

    fun notice(text: String?) {
        _state.update { it.copy(message = text) }
    }

    fun clearMessage() = notice(null)

    /**
     * Back handling. Backing out of the archive locks it rather than leaving an unlocked
     * screen sitting in the stack for whoever picks the phone up next.
     */
    fun onBack(): Boolean = when (_state.value.screen) {
        Screen.Archive -> {
            if (_state.value.archive.open != null) closeEntry() else lock()
            true
        }
        Screen.Unlock -> {
            _state.update { it.copy(screen = Screen.Write) }
            true
        }
        Screen.Settings -> {
            closeSettings()
            true
        }
        else -> false
    }

    private fun Throwable.readableMessage(): String =
        message?.takeIf { it.isNotBlank() } ?: this::class.java.simpleName

    /** Raised when the system document picker hands back nothing openable. */
    private class NoStorageAppException : Exception()

    companion object {
        const val AUTOSAVE_DEBOUNCE_MILLIS = 400L

        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    JournalViewModel(container) as T
            }
    }
}
