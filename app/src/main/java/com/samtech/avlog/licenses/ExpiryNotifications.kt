package com.samtech.avlog.licenses

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.samtech.avlog.ExpiryWorker
import com.samtech.avlog.MainActivity
import com.samtech.avlog.R
import com.samtech.avlog.data.LicenseEntity
import java.util.concurrent.TimeUnit

object ExpiryNotifications {
    const val CHANNEL_ID = "license_expiry"
    const val EXTRA_DESTINATION = "destination"
    const val DESTINATION_LICENSES = "licenses"
    private const val WORK_NAME = "LicenseCheck"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Daily check, no battery constraint (safety alerts must not be deferred). UPDATE replaces old constraints. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<ExpiryWorker>(24, TimeUnit.HOURS).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /**
     * Immediate one-off check (app start, permission just granted). Safe to repeat: ExpiryEvaluator never
     * notifies the same threshold twice.
     */
    fun checkNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "$WORK_NAME-now", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<ExpiryWorker>().build()
        )
    }

    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun message(context: Context, license: LicenseEntity, daysLeft: Long): String {
        val r = context.resources
        return when {
            daysLeft < 0 -> r.getString(R.string.notif_expired, license.name)
            daysLeft == 0L -> r.getString(R.string.notif_expires_today, license.name)
            else -> r.getQuantityString(R.plurals.notif_expires_in_days, daysLeft.toInt(), license.name, daysLeft.toInt())
        }
    }

    fun notify(context: Context, license: LicenseEntity, daysLeft: Long): Boolean {
        if (!canNotify(context)) return false
        val tap = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_DESTINATION, DESTINATION_LICENSES)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            context, license.id.hashCode(), tap,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_license)
            .setContentTitle(context.getString(R.string.notif_title))
            .setContentText(message(context, license, daysLeft))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(license.id.hashCode(), notification)
            true
        } catch (e: SecurityException) {
            false
        }
    }
}
