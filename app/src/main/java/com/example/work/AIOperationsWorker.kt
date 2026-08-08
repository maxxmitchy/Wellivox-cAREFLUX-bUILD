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
import com.example.data.CarefluxAIEngine
import com.example.data.PharmacyDatabase
import com.example.util.RefillNotificationSchedule
import java.util.Calendar
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
        val receipts = dao.getAllReceipts().first()
        val tasks = dao.getAllOperationTasks().first()
        val volumes = emptyList<com.example.data.DailyPrescriptionVolume>()

        val now = System.currentTimeMillis()
        val isOvernight = RefillNotificationSchedule.isOvernightQuietWindow(now)
        val isMorningReconcile = RefillNotificationSchedule.isMorningStockReconciliationWindow(now)
        val isPriorityRefill = RefillNotificationSchedule.isPriorityRefillWindow(now)
        val isPeakRush = RefillNotificationSchedule.isPeakRushWindow(now)
        val isAfternoonRestock = RefillNotificationSchedule.isAfternoonRestockWindow(now)
        val isPriorityExpiry = RefillNotificationSchedule.isPriorityExpiryWindow(now)
        val isEveningClosing = RefillNotificationSchedule.isEveningClosingWindow(now)

        // Clean up any orphaned medications not attached to a valid customer
        val validMeds = meds.filter { med ->
            val customerExists = customers.any { it.id == med.customerId && it.name.isNotBlank() }
            if (!customerExists) {
                dao.deleteCustomerMedication(med)
                false
            } else {
                true
            }
        }

        // 1. Overnight Quiet Window (10 PM - 9 AM)
        if (isOvernight) {
            prefs.edit().putLong("overnight_maintenance_last_run", now).apply()
            try {
                val precomputedInsights = CarefluxAIEngine.generateInsights(apiKey, inventory, customers, validMeds, volumes)
                precomputedInsights?.let {
                    val totalTasks = it.highPriorityTasks.size + it.inventoryAlerts.size
                    prefs.edit().putInt("overnight_cached_task_count", totalTasks).apply()
                }
            } catch (e: Exception) {
                // Silent failover during overnight maintenance
            }
            return Result.success()
        }

        // 2. Peak Rush Suppressor (12 PM - 2 PM)
        if (isPeakRush) {
            return Result.success()
        }

        // Filter medications due for refill
        val sevenDaysMs = 7L * 24 * 60 * 60 * 1000
        val dueRefills = validMeds
            .filter { it.cycleDays > 0 && it.nextRefillDate <= now + sevenDaysMs }
            .sortedBy { it.nextRefillDate }

        // Filter products expiring within 90 days or already expired
        val ninetyDaysMs = 90L * 24 * 60 * 60 * 1000
        val expiringItems = inventory
            .filter { it.expiryDate > 0L && it.expiryDate <= now + ninetyDaysMs }
            .sortedBy { it.expiryDate }

        // Filter low stock items requiring restock
        val lowStockItems = inventory
            .filter { it.isLowStock || it.stockQuantity <= it.minRequiredStock }
            .sortedBy { it.stockQuantity }

        // Compute today's start timestamp
        val startOfDayCal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDayMs = startOfDayCal.timeInMillis

        // Compute daily metrics for shift closing summary
        val todayReceipts = receipts.filter { it.timestamp >= startOfDayMs && !it.paymentStatus.equals("Cancelled", ignoreCase = true) }
        val todaySalesAmount = todayReceipts.sumOf { it.totalAmount }
        val todaySalesCount = todayReceipts.size
        val todayRegisteredCustomers = customers.count { it.dateAdded >= startOfDayMs }
        val todayCompletedTasks = tasks.count { it.isCompleted && ((it.verifiedAt ?: it.createdAt) >= startOfDayMs) }
        val pendingTasks = tasks.count { !it.isCompleted }

        // 3. 9 AM - 11 AM: Stock Reconciliation Audit Window
        if (isMorningReconcile && inventory.isNotEmpty()) {
            val cutoff14Days = System.currentTimeMillis() - (14L * 24 * 60 * 60 * 1000L)
            val overdueCount = inventory.count { it.lastReconciledAt < cutoff14Days }
            val cycleRatio = (inventory.size - overdueCount).toFloat() / inventory.size.toFloat()

            val title: String
            val text: String
            if (cycleRatio < 0.80f) {
                title = "Rolling Cycle Count Alert: ${(cycleRatio * 100).toInt()}% Reconciled"
                text = "Only ${(cycleRatio * 100).toInt()}% of inventory reconciled in last 14 days ($overdueCount items overdue). Tap to conduct physical count."
            } else {
                val lastIdx = prefs.getInt("last_reconcile_notification_index", -1)
                val currIdx = (lastIdx + 1) % inventory.size
                prefs.edit().putInt("last_reconcile_notification_index", currIdx).apply()

                val targetItem = inventory[currIdx]
                title = RefillNotificationSchedule.formatStockReconciliationTitle(targetItem.name)
                text = RefillNotificationSchedule.formatStockReconciliationMessage(
                    medicationName = targetItem.name,
                    dosage = targetItem.dosage,
                    batchNo = targetItem.batchNumber
                )
            }
            showNotification(context, title, text, targetTab = "branch_team", targetSubTab = "ops_task_board")
        }
        // 4. 11 AM - 12 PM: Priority Refill Reminders
        else if (isPriorityRefill && dueRefills.isNotEmpty()) {
            val lastIndex = prefs.getInt("last_refill_notification_index", -1)
            val currentIndex = (lastIndex + 1) % dueRefills.size
            prefs.edit().putInt("last_refill_notification_index", currentIndex).apply()

            val targetMed = dueRefills[currentIndex]
            val targetPatient = customers.find { it.id == targetMed.customerId } ?: return Result.success()
            val patientName = targetPatient.name.trim()

            val title = RefillNotificationSchedule.formatRefillTitle(patientName)
            val text = RefillNotificationSchedule.formatRefillMessage(
                patientName = patientName,
                medicationName = targetMed.medicationName,
                dosage = targetMed.customDosage,
                phone = targetPatient.phoneNumber
            )
            showNotification(context, title, text, targetTab = "customers", targetCustomerName = patientName)
        }
        // 5. 2 PM - 4 PM: Low-Stock & Reorder Audit
        else if (isAfternoonRestock && lowStockItems.isNotEmpty()) {
            val lastRestockIndex = prefs.getInt("last_restock_notification_index", -1)
            val currentRestockIndex = (lastRestockIndex + 1) % lowStockItems.size
            prefs.edit().putInt("last_restock_notification_index", currentRestockIndex).apply()

            val targetItem = lowStockItems[currentRestockIndex]
            val title = RefillNotificationSchedule.formatRestockTitle(targetItem.name)
            val text = RefillNotificationSchedule.formatRestockMessage(
                medicationName = targetItem.name,
                dosage = targetItem.dosage,
                currentQty = targetItem.stockQuantity,
                minQty = targetItem.minRequiredStock
            )
            showNotification(
                context, title, text, 
                targetTab = "branch_team", targetSubTab = "ops_task_board",
                urgency = com.example.util.NotificationUrgency.STANDARD,
                dedupKey = "lowstock_${targetItem.id}"
            )
        }
        // 6. 4 PM - 6 PM: Short-Dated & Expiry Warning
        else if (isPriorityExpiry && expiringItems.isNotEmpty()) {
            val lastExpiryIndex = prefs.getInt("last_expiry_notification_index", -1)
            val currentExpiryIndex = (lastExpiryIndex + 1) % expiringItems.size
            prefs.edit().putInt("last_expiry_notification_index", currentExpiryIndex).apply()

            val targetItem = expiringItems[currentExpiryIndex]
            val isExpired = targetItem.expiryDate in 1L..System.currentTimeMillis()
            val title = RefillNotificationSchedule.formatExpiryTitle(targetItem.name, targetItem.expiryDate)
            val text = RefillNotificationSchedule.formatExpiryMessage(
                medicationName = targetItem.name,
                dosage = targetItem.dosage,
                batchNo = targetItem.batchNumber,
                expiryDateMs = targetItem.expiryDate,
                qtyLeft = targetItem.stockQuantity
            )
            showNotification(
                context, title, text, 
                targetTab = "branch_team", targetSubTab = "ops_task_board",
                urgency = if (isExpired) com.example.util.NotificationUrgency.CRITICAL else com.example.util.NotificationUrgency.STANDARD,
                targetRole = "Pharmacist",
                dedupKey = "expiry_batch_${targetItem.batchNumber}"
            )
        }
        // 7. 8 PM - 10 PM: Shift Closing & Achievements Summary
        else if (isEveningClosing) {
            val title = RefillNotificationSchedule.formatEveningSummaryTitle(todaySalesCount > 0 || todaySalesAmount > 0)
            val text = RefillNotificationSchedule.formatEveningSummaryMessage(
                totalSalesAmount = todaySalesAmount,
                salesCount = todaySalesCount,
                registeredCustomersCount = todayRegisteredCustomers,
                completedTasksCount = todayCompletedTasks,
                pendingTasksCount = pendingTasks
            )
            val targetTab = when {
                todaySalesCount > 0 -> "receipts"
                todayRegisteredCustomers > 0 -> "customers"
                else -> "branch_team"
            }
            showNotification(
                context, title, text, 
                targetTab = targetTab, targetSubTab = "ops_task_board",
                urgency = com.example.util.NotificationUrgency.DIGEST,
                dedupKey = "evening_summary_${System.currentTimeMillis() / (24 * 60 * 60 * 1000L)}"
            )
        }
        // Fallback: AI Operations Specific Task Alert
        else {
            val insights = CarefluxAIEngine.generateInsights(apiKey, inventory, customers, meds, volumes)
            insights?.let {
                val firstTask = it.highPriorityTasks.firstOrNull()
                if (firstTask != null) {
                    val title = RefillNotificationSchedule.formatTaskAssignedTitle(firstTask.title)
                    val text = RefillNotificationSchedule.formatTaskAssignedMessage("Careflux AI", firstTask.title)
                    showNotification(
                        context, title, text, 
                        targetTab = "branch_team", targetSubTab = "ops_task_board",
                        urgency = com.example.util.NotificationUrgency.STANDARD,
                        dedupKey = "ai_task_${firstTask.title.hashCode()}"
                    )
                }
            }
        }

        return Result.success()
    }

    private fun showNotification(
        context: Context,
        title: String,
        content: String,
        targetTab: String = "branch_team",
        targetSubTab: String = "ops_task_board",
        targetCustomerName: String? = null,
        urgency: com.example.util.NotificationUrgency = com.example.util.NotificationUrgency.STANDARD,
        targetRole: String? = null,
        dedupKey: String? = null
    ) {
        com.example.util.SmartNotificationDispatcher.dispatchNotification(
            context = context,
            title = title,
            content = content,
            urgency = urgency,
            targetRole = targetRole,
            targetTab = targetTab,
            targetSubTab = targetSubTab,
            targetCustomerName = targetCustomerName,
            dedupKey = dedupKey
        )
    }
}
