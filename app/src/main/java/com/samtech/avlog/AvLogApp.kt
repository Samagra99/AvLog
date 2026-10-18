package com.samtech.avlog

import android.app.Application
import androidx.room.Room
import com.samtech.avlog.core.Clock
import com.samtech.avlog.core.SystemClock

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
    }
}

class AppContainer(val app: Application) {
    val clock: Clock = SystemClock

    val database: AppDatabase by lazy {
        Room.databaseBuilder(app, AppDatabase::class.java, AppDatabase.NAME).build()
    }
}

val android.content.Context.appContainer: AppContainer
    get() = (applicationContext as AvLogApp).container
