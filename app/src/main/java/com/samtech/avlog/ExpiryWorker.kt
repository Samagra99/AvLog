package com.samtech.avlog

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class ExpiryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(applicationContext)
        val licenses = database.licenseDao().getAllLicensesSync()

        licenses.forEach { license ->
            val daysLeft = calculateDays(license.expiryDate)
            if (daysLeft in listOf(30L, 7L, 1L, 0L)) {
                sendNotification(license.name, daysLeft)
            }
        }
        return Result.success()
    }

    private fun calculateDays(expiryMillis: Long): Long {
        val expiry = Instant.ofEpochMilli(expiryMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        val today = LocalDate.now()
        return ChronoUnit.DAYS.between(today, expiry)
    }

    private fun sendNotification(name: String, days: Long) {
        val channelId = "license_expiry"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 1. Create Channel (Required for Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "License Alerts", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        // 2. Build and Post
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Qualification Alert")
            .setContentText("$name expires in $days days!")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(name.hashCode(), notification)
    }
}
