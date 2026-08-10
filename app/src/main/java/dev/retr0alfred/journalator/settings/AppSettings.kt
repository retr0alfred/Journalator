package dev.retr0alfred.journalator.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AutoLockDelay(val seconds: Int) {
    IMMEDIATE(0),
    THIRTY_SECONDS(30),
    TWO_MINUTES(120);

    companion object {
        fun fromSeconds(seconds: Int): AutoLockDelay =
            entries.firstOrNull { it.seconds == seconds } ?: IMMEDIATE
    }
}

enum class TextSize(val scale: Float) {
    SMALL(0.9f),
    MEDIUM(1.0f),
    LARGE(1.2f),
}

data class Settings(
    val autoLock: AutoLockDelay = AutoLockDelay.IMMEDIATE,
    val biometricEnabled: Boolean = false,
    val reduceMotion: Boolean = false,
    val textSize: TextSize = TextSize.MEDIUM,
    /** When true, word counts and moods are written to the database unencrypted. */
    val heatmapMetadata: Boolean = false,
    val wipeOnFailure: Boolean = false,
)

/**
 * Preferences that are not secrets.
 *
 * `SharedPreferences` is fine here and only here: every value in [Settings] is a display
 * choice. Nothing that could help an attacker — no key material, no hash, no counter that
 * gates access — is ever written through this class. The failure counter that *does* gate
 * access lives in its own file under `FailureStateStore`, precisely so it is not sitting in
 * a world-readable-by-root XML blob next to the theme setting.
 */
class AppSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<Settings> = _state.asStateFlow()

    val current: Settings get() = _state.value

    fun setAutoLock(value: AutoLockDelay) = update { putInt(KEY_AUTO_LOCK, value.seconds) }

    fun setBiometricEnabled(value: Boolean) = update { putBoolean(KEY_BIOMETRIC, value) }

    fun setReduceMotion(value: Boolean) = update { putBoolean(KEY_REDUCE_MOTION, value) }

    fun setTextSize(value: TextSize) = update { putString(KEY_TEXT_SIZE, value.name) }

    fun setHeatmapMetadata(value: Boolean) = update { putBoolean(KEY_HEATMAP, value) }

    fun setWipeOnFailure(value: Boolean) = update { putBoolean(KEY_WIPE, value) }

    fun clear() {
        prefs.edit().clear().apply()
        _state.value = read()
    }

    private inline fun update(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        _state.value = read()
    }

    private fun read() = Settings(
        autoLock = AutoLockDelay.fromSeconds(prefs.getInt(KEY_AUTO_LOCK, 0)),
        biometricEnabled = prefs.getBoolean(KEY_BIOMETRIC, false),
        reduceMotion = prefs.getBoolean(KEY_REDUCE_MOTION, false),
        textSize = runCatching {
            TextSize.valueOf(prefs.getString(KEY_TEXT_SIZE, TextSize.MEDIUM.name)!!)
        }.getOrDefault(TextSize.MEDIUM),
        heatmapMetadata = prefs.getBoolean(KEY_HEATMAP, false),
        wipeOnFailure = prefs.getBoolean(KEY_WIPE, false),
    )

    companion object {
        private const val FILE = "journalator_settings"
        private const val KEY_AUTO_LOCK = "auto_lock_seconds"
        private const val KEY_BIOMETRIC = "biometric_enabled"
        private const val KEY_REDUCE_MOTION = "reduce_motion"
        private const val KEY_TEXT_SIZE = "text_size"
        private const val KEY_HEATMAP = "heatmap_metadata"
        private const val KEY_WIPE = "wipe_on_failure"

        /** Consecutive wrong passcodes that trigger the optional wipe. */
        const val WIPE_THRESHOLD = 10
    }
}
