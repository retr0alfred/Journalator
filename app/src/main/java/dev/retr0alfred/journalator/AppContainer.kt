package dev.retr0alfred.journalator

import android.content.Context
import dev.retr0alfred.journalator.crypto.BiometricVaultKey
import dev.retr0alfred.journalator.crypto.FailureStateStore
import dev.retr0alfred.journalator.crypto.KeystoreDraftCipher
import dev.retr0alfred.journalator.crypto.Vault
import dev.retr0alfred.journalator.crypto.VaultConfigStore
import dev.retr0alfred.journalator.data.JournalDatabase
import dev.retr0alfred.journalator.data.JournalRepository
import dev.retr0alfred.journalator.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * Manual dependency injection, on purpose.
 *
 * A DI framework would add an annotation processor, a plugin, a code generator and a set of
 * compile-time contracts that all have to keep working for the next ten years, in exchange
 * for saving about forty lines of `by lazy`. For a single-module app with one graph and one
 * scope, that trade is not worth making.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    /**
     * `filesDir` — app-internal storage. Not `getExternalFilesDir`, not `Downloads`, not
     * anywhere the Files app or another app can enumerate. Nothing Journalator writes here
     * is visible to the rest of the phone on an unrooted device.
     */
    private val vaultFile: File get() = File(appContext.filesDir, "vault.cfg")

    private val failureFile: File get() = File(appContext.filesDir, "failures.bin")

    val settings: AppSettings by lazy { AppSettings(appContext) }

    val vault: Vault by lazy { Vault(VaultConfigStore(vaultFile)) }

    val failureStore: FailureStateStore by lazy { FailureStateStore(failureFile) }

    val biometricKey: BiometricVaultKey by lazy { BiometricVaultKey() }

    private val database: JournalDatabase by lazy { JournalDatabase.build(appContext) }

    val repository: JournalRepository by lazy {
        JournalRepository(
            entryDao = database.entryDao(),
            draftDao = database.draftDao(),
            draftCipher = KeystoreDraftCipher(appContext.packageManager),
            io = Dispatchers.IO,
        )
    }

    /** Used by the "erase everything" paths so nothing survives in a stray file. */
    suspend fun destroyEverything() {
        repository.wipeEverything()
        vault.destroy()
        failureStore.clear()
        biometricKey.deleteKey()
        settings.clear()
    }
}
