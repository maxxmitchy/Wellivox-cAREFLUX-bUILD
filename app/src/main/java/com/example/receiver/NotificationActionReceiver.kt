package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.example.data.PharmacyDatabase
import com.example.data.PharmacyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_CLAIM_TASK = "com.example.ACTION_CLAIM_TASK"
        const val ACTION_QUARANTINE_TASK = "com.example.ACTION_QUARANTINE_TASK"
        const val EXTRA_TASK_ID = "EXTRA_TASK_ID"
        const val EXTRA_NOTIFICATION_ID = "EXTRA_NOTIFICATION_ID"
        const val EXTRA_ITEM_NAME = "EXTRA_ITEM_NAME"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L).takeIf { it != -1L }
            ?: intent.getStringExtra(EXTRA_TASK_ID)?.toLongOrNull() ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, taskId.toInt())
        val itemName = intent.getStringExtra(EXTRA_ITEM_NAME) ?: "Item"

        // Cancel the system notification immediately
        try {
            NotificationManagerCompat.from(context).cancel(notificationId)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val staffName = prefs.getString("cached_user_name", "Staff Pharmacist") ?: "Staff Pharmacist"

        val db = PharmacyDatabase.getDatabase(context)
        val repository = PharmacyRepository(db.pharmacyDao())

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_CLAIM_TASK -> {
                        val task = repository.getOperationTaskById(taskId.toInt())
                        if (task != null) {
                            val updated = task.copy(assignedToName = staffName)
                            repository.updateOperationTask(updated)

                            CoroutineScope(Dispatchers.Main).launch {
                                Toast.makeText(
                                    context,
                                    "Task '$taskId' claimed by $staffName",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                    ACTION_QUARANTINE_TASK -> {
                        val task = repository.getOperationTaskById(taskId.toInt())
                        if (task != null) {
                            val updated = task.copy(
                                isCompleted = true,
                                verifiedBy = staffName,
                                verificationNotes = "Quarantined immediately via 1-tap notification action shade.",
                                verificationChannel = "SYSTEM_NOTIFICATION_SHADE",
                                verifiedAt = System.currentTimeMillis()
                            )
                            repository.updateOperationTask(updated)

                            CoroutineScope(Dispatchers.Main).launch {
                                Toast.makeText(
                                    context,
                                    "Stock for '${task.title}' moved to Quarantine",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
