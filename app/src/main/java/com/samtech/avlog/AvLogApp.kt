package com.samtech.avlog

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.samtech.avlog.core.Clock
import com.samtech.avlog.core.SystemClock
import com.samtech.avlog.data.ALL_MIGRATIONS
import com.samtech.avlog.data.AppSettings
import com.samtech.avlog.data.BackupManager
import com.samtech.avlog.data.CsvExporter
import com.samtech.avlog.data.DutyRepository
import com.samtech.avlog.data.LicenseRepository
import com.samtech.avlog.licenses.ExpiryNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application-scoped dependency container. The database is built exactly once here, which removes the
 * double-instance race in the old AppDatabase.getDatabase() and keeps database lookups out of composition.
 */
class AvLogApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // P5.5: channel + periodic check regardless of notification permission.
        ExpiryNotifications.createChannel(this)
        ExpiryNotifications.schedule(this)
        container.appScope.launch { migrateLegacyPrefs() }
    }

    /** f784030 kept the BA check time in SharedPreferences; move it into duty_days, then drop the file. */
    private suspend fun migrateLegacyPrefs() {
        val legacy = getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        if (!legacy.contains(LEGACY_BA_KEY)) return
        container.dutyRepository.importLegacyBaCheck(legacy.getLong(LEGACY_BA_KEY, 0L))
        deleteSharedPreferences(LEGACY_PREFS)
    }

    private companion object {
        const val LEGACY_PREFS = "duty_prefs"
        const val LEGACY_BA_KEY = "last_ba_check_time"
    }
}

class AppContainer(val app: Application) {
    val clock: Clock = SystemClock

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase by lazy {
        Room.databaseBuilder(app, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(*ALL_MIGRATIONS)
            .build()
    }

    val dutyRepository by lazy { DutyRepository(database.dutyDao(), clock) }
    val licenseRepository by lazy { LicenseRepository(database.licenseDao()) }
    val settings by lazy { AppSettings(app) }
    val csvExporter by lazy { CsvExporter(app, dutyRepository, clock) }
    val backupManager by lazy { BackupManager(app, database, clock) }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as AvLogApp).container
