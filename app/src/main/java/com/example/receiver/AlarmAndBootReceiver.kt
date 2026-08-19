package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.work.AIOperationsWorker
import com.example.work.CloudSyncWorker
import java.util.Calendar
import java.util.concurrent.TimeUnit

class AlarmAndBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        if (action == Intent.ACTION_BOOT_COMPLETED || action == ACTION_EXACT_ALARM_TRIGGER || action == "android.intent.action.MY_PACKAGE_REPLACED") {
            try {
                // Enqueue immediate execution of AI Operations Worker
                val immediateAiRequest = OneTimeWorkRequestBuilder<AIOperationsWorker>().build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    "ai_operations_immediate",
                    ExistingWorkPolicy.REPLACE,
                    immediateAiRequest
                )

                // Ensure periodic syncs are scheduled
                val aiRequest = PeriodicWorkRequestBuilder<AIOperationsWorker>(15, TimeUnit.MINUTES).build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    "ai_operations_job",
                    ExistingPeriodicWorkPolicy.KEEP,
                    aiRequest
                )

                val syncRequest = PeriodicWorkRequestBuilder<CloudSyncWorker>(15, TimeUnit.MINUTES).build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    "cloud_sync_job",
                    ExistingPeriodicWorkPolicy.KEEP,
                    syncRequest
                )

                // Reschedule exact alarms for upcoming operational windows
                scheduleOperationalAlarms(context)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    companion object {
        const val ACTION_EXACT_ALARM_TRIGGER = "com.example.ACTION_EXACT_ALARM_TRIGGER"

        fun scheduleOperationalAlarms(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager ?: return
            val times = listOf(
                Pair(7, 0),   // 7:00 AM Morning Opening Briefing
                Pair(8, 15),  // 8:15 AM Refill Queue Dispatch
                Pair(8, 45),  // 8:45 AM Unconfirmed Refill Escalation
                Pair(10, 0),  // 10:00 AM Morning Short-Dated & Expiry Warning
                Pair(14, 0),  // 2:00 PM Low-Stock Restock Audit
                Pair(16, 0),  // 4:00 PM Afternoon Short-Dated & Expiry Warning
                Pair(20, 0)   // 8:00 PM Evening Closing Till Review
            )

            val now = System.currentTimeMillis()
            times.forEachIndexed { index, (hour, minute) ->
                val cal = Calendar.getInstance().apply {
                    timeInMillis = now
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    if (timeInMillis <= now) {
                        add(Calendar.DAY_OF_YEAR, 1)
                    }
                }

                val intent = Intent(context, AlarmAndBootReceiver::class.java).apply {
                    action = ACTION_EXACT_ALARM_TRIGGER
                }
                val pendingIntent = android.app.PendingIntent.getBroadcast(
                    context,
                    100 + index,
                    intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )

                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        alarmManager.setAndAllowWhileIdle(
                            android.app.AlarmManager.RTC_WAKEUP,
                            cal.timeInMillis,
                            pendingIntent
                        )
                    } else {
                        alarmManager.set(
                            android.app.AlarmManager.RTC_WAKEUP,
                            cal.timeInMillis,
                            pendingIntent
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
