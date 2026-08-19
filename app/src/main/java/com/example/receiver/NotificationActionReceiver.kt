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

        // Session & Branch verification
        val authUser = com.example.data.auth.AuthRepository().getCurrentUser()
        val branchId = prefs.getString("cached_branch_id", null)
        if (authUser == null || branchId.isNullOrBlank()) {
            return
        }

        val staffName = prefs.getString("cached_user_name", null)?.takeIf { it.isNotBlank() }
            ?: authUser.displayName?.takeIf { it.isNotBlank() }
            ?: authUser.email
            ?: "Staff Pharmacist"
        val userRole = prefs.getString("cached_role", "Staff") ?: "Staff"

        val db = PharmacyDatabase.getDatabase(context)
        val repository = PharmacyRepository(db.pharmacyDao())

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_CLAIM_TASK -> {
                        val task = repository.getOperationTaskById(taskId.toInt())
                        // Only claim if task exists and is not already completed
                        if (task != null && !task.isCompleted) {
                            val updated = task.copy(
                                assignedToName = staffName,
                                assignedToUid = authUser.uid
                            )
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
                        val isAuthorized = userRole.equals("Pharmacist", ignoreCase = true) ||
                                userRole.equals("Admin", ignoreCase = true) ||
                                userRole.equals("SuperAdmin", ignoreCase = true) ||
                                userRole.equals("Manager", ignoreCase = true)

                        if (!isAuthorized) {
                            CoroutineScope(Dispatchers.Main).launch {
                                Toast.makeText(
                                    context,
                                    "Unauthorized: Quarantine action requires Pharmacist or Manager role",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            return@launch
                        }

                        val task = repository.getOperationTaskById(taskId.toInt())
                        // Do not overwrite verification metadata if already completed
                        if (task != null && !task.isCompleted) {
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
