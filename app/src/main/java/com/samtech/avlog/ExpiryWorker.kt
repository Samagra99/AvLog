package com.samtech.avlog

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.samtech.avlog.core.localToday
import com.samtech.avlog.licenses.ExpiryEvaluator
import com.samtech.avlog.licenses.ExpiryNotifications

/**
 * Daily license check. Kept in this package: WorkManager persists the worker's class name, so moving it
 * would break work already enqueued on existing installs.
 */
class ExpiryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = applicationContext.appContainer
        val dao = container.database.licenseDao()
        val today = container.clock.localToday()

        dao.getAll().forEach { license ->
            val threshold = ExpiryEvaluator.evaluate(license, today) ?: return@forEach
            val daysLeft = ExpiryEvaluator.daysLeft(license.expiryDate, today)
            // Only record the threshold if the user actually saw it; otherwise catch up once enabled.
            if (ExpiryNotifications.notify(applicationContext, license, daysLeft)) {
                dao.setLastNotifiedThreshold(license.id, threshold)
            }
        }
        return Result.success()
    }
}
