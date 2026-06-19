package com.example.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.data.CarefluxAIEngine
import com.example.data.PharmacyDatabase
import kotlinx.coroutines.flow.first

class AIOperationsWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val dao = PharmacyDatabase.getDatabase(context).pharmacyDao()
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("custom_api_key", null)?.takeIf { it.isNotBlank() } ?: com.example.BuildConfig.GEMINI_API_KEY
        
        // Fetch current snapshot from DB directly
        val inventory = dao.getAllInventoryItems().first()
        val customers = dao.getAllCustomers().first()
        val meds = dao.getAllCustomerMedications().first()
        val volumes = emptyList<com.example.data.DailyPrescriptionVolume>() // we can omit this constraint for now

        // Call the AI Engine
        val insights = CarefluxAIEngine.generateInsights(apiKey, inventory, customers, meds, volumes)
        
        insights?.let {
            val totalUrgent = it.highPriorityTasks.size + it.inventoryAlerts.size
            if (totalUrgent > 0) {
                val title = "Careflux AI: ${totalUrgent} Operations Tasks"
                val text = it.highPriorityTasks.firstOrNull()?.title ?: "Action required on inventory & patients."
                showNotification(context, title, text)
            }
        }

        return Result.success()
    }

    private fun showNotification(context: Context, title: String, content: String) {
        val channelId = "careflux_ai_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "AI Operations Dashboard",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        // Open App on tap
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("OPEN_TAB", "ai_tasks")
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(9001, notification)
        } catch (e: SecurityException) {
            // Missing POST_NOTIFICATIONS permission
        }
    }
}
