package com.example.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // The system sends Day 3/7/14 alerts here via server-cron
        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "Patient Reminder"
        val body = remoteMessage.notification?.body ?: remoteMessage.data["body"] ?: "Follow up time!"
        val targetTab = remoteMessage.data["targetTab"] ?: "branch_team"
        val targetSubTab = remoteMessage.data["targetSubTab"] ?: "ops_task_board"
        val taskId = remoteMessage.data["taskId"] ?: remoteMessage.data["targetTaskId"]
        val customerName = remoteMessage.data["targetCustomerName"] ?: remoteMessage.data["customerName"] ?: remoteMessage.data["patientName"]
        val urgencyStr = remoteMessage.data["urgency"] ?: remoteMessage.data["priority"]
        val targetRole = remoteMessage.data["targetRole"]
        val targetBranchId = remoteMessage.data["targetBranchId"] ?: remoteMessage.data["branchId"]
        val dedupKey = remoteMessage.data["dedupKey"]

        val urgency = when (urgencyStr?.lowercase()) {
            "critical", "high" -> com.example.util.NotificationUrgency.CRITICAL
            "digest", "low", "silent" -> com.example.util.NotificationUrgency.DIGEST
            else -> if (title.contains("Expired", ignoreCase = true) || title.contains("Quarantine", ignoreCase = true)) {
                com.example.util.NotificationUrgency.CRITICAL
            } else {
                com.example.util.NotificationUrgency.STANDARD
            }
        }

        com.example.util.SmartNotificationDispatcher.dispatchNotification(
            context = applicationContext,
            title = title,
            content = body,
            urgency = urgency,
            targetRole = targetRole,
            targetBranchId = targetBranchId,
            targetTab = targetTab,
            targetSubTab = targetSubTab,
            targetTaskId = taskId?.toLongOrNull(),
            targetCustomerName = customerName,
            dedupKey = dedupKey
        )
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        try {
            val deviceRepository = com.example.data.device.DeviceRepository(applicationContext)
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    deviceRepository.handleTokenRefreshed(token)
                } catch (e: Exception) {
                    // Fail-safe handling
                }
            }
        } catch (e: Exception) {
            // Never crash
        }
    }
}
