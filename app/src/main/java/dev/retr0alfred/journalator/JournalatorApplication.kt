package dev.retr0alfred.journalator

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.StrictMode

class JournalatorApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        installStrictModeInDebug()
        container = AppContainer(this)
    }

    /**
     * StrictMode is the enforcement behind "no disk or crypto on the main thread". It only
     * runs in debug builds, and it is set to log rather than crash so a false positive from a
     * platform component cannot brick a developer's session — the log line is loud enough.
     */
    private fun installStrictModeInDebug() {
        // Read from the manifest flag rather than BuildConfig: the build generates no
        // BuildConfig class, which keeps one more generated file out of the release APK.
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (!debuggable) return

        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .detectCustomSlowCalls()
                .penaltyLog()
                .build()
        )

        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .detectLeakedRegistrationObjects()
                .detectFileUriExposure()
                .penaltyLog()
                .build()
        )
    }
}
