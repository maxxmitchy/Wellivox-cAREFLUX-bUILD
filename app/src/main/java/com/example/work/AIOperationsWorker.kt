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

        // Session Guard: Do not execute operational notifications without an active authenticated session and branch context
        val authUser = com.example.data.auth.AuthRepository().getCurrentUser()
        val branchId = prefs.getString("cached_branch_id", null)
        if (authUser == null || branchId.isNullOrBlank()) {
            return Result.success()
        }

        val pharmacyName = prefs.getString("pharmacy_name", "Community Pharmacy") ?: "Community Pharmacy"
        val branchName = prefs.getString("current_branch_name", null)
            ?: prefs.getString("pharmacy_branch", null)
            ?: prefs.getString("branch_name", null)
            ?: ""
        val pharmacyDisplayName = if (branchName.isNotBlank()) "$pharmacyName ($branchName)" else pharmacyName
        val apiKey = prefs.getString("custom_api_key", null)?.takeIf { it.isNotBlank() } ?: com.example.BuildConfig.GEMINI_API_KEY
        
        // Fetch current snapshot from DB directly with failure recovery
        val inventory: List<com.example.data.InventoryItem>
        val customers: List<com.example.data.Customer>
        val meds: List<com.example.data.CustomerMedication>
        val receipts: List<com.example.data.Receipt>
        val tasks: List<com.example.data.OperationTask>
        val volumes = emptyList<com.example.data.DailyPrescriptionVolume>()

        try {
            inventory = dao.getInventoryForBranch(branchId).first()
            customers = dao.getCustomersForBranch(branchId).first()
            meds = dao.getCustomerMedicationsForBranch(branchId).first()
            receipts = dao.getReceiptsForBranch(branchId).first()
            tasks = dao.getOperationTasksForBranch(branchId).first()
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
        }

        val now = System.currentTimeMillis()
        val isOvernight = RefillNotificationSchedule.isOvernightQuietWindow(now)
        val isMorningOpening = RefillNotificationSchedule.isMorningOpeningWindow(now)
        val isMorningRefillOutbound = RefillNotificationSchedule.isMorningRefillOutboundWindow(now)
        val isRefillDispatch = RefillNotificationSchedule.isRefillDispatchWindow(now)
        val isUnconfirmedRefillAlert = RefillNotificationSchedule.isUnconfirmedRefillAlertWindow(now)
        val isMorningReconcile = RefillNotificationSchedule.isMorningStockReconciliationWindow(now)
        val isMorningExpiry = RefillNotificationSchedule.isMorningExpiryWindow(now)
        val isPriorityRefill = RefillNotificationSchedule.isPriorityRefillWindow(now)
        val isPeakRush = RefillNotificationSchedule.isPeakRushWindow(now)
        val isAfternoonRestock = RefillNotificationSchedule.isAfternoonRestockWindow(now)
        val isPriorityExpiry = RefillNotificationSchedule.isPriorityExpiryWindow(now)
        val isEveningTaskDelegation = RefillNotificationSchedule.isEveningTaskDelegationWindow(now)
        val isEveningAudit = RefillNotificationSchedule.isEveningAuditWindow(now)
        val isEveningClosing = RefillNotificationSchedule.isEveningClosingWindow(now)
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(now))

        // Clean up any orphaned medications not attached to a valid customer (protected against SQLite/Room failures)
        val validMeds: List<com.example.data.CustomerMedication>
        try {
            val validList = mutableListOf<com.example.data.CustomerMedication>()
            for (med in meds) {
                val customerExists = customers.any { it.id == med.customerId && it.name.isNotBlank() }
                if (!customerExists) {
                    dao.deleteCustomerMedication(med)
                } else {
                    validList.add(med)
                }
            }
            validMeds = validList
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
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

        val dueTodayCount = validMeds.count { it.cycleDays > 0 && it.nextRefillDate <= startOfDayMs + (24L * 60 * 60 * 1000L) }
            .let { if (it > 0) it else dueRefills.size }
        val hasTwilioGateway = !prefs.getString("twilio_account_sid", null).isNullOrBlank()

        // 2a. Morning Opening Briefing (7:00 AM – 7:29 AM)
        if (isMorningOpening) {
            val lastSent = prefs.getString("last_morning_opening_date", "")
            if (lastSent != todayStr) {
                val title = "🌅 7:00 AM Opening Briefing: AI Stock Reorder & Cold-Chain Check"
                val text = "AI compiled ${lowStockItems.size} stock items at reorder point. Complete 1-tap Cold-Chain Fridge Temp Log & Controlled Vault handover before 9 AM opening."
                val dispatched = showNotification(
                    context, title, text,
                    targetTab = "branch_team", targetSubTab = "ops_task_board",
                    urgency = com.example.util.NotificationUrgency.CRITICAL,
                    targetRole = "Pharmacist",
                    dedupKey = "morning_opening_7am_${todayStr}"
                )
                if (dispatched) {
                    prefs.edit().putString("last_morning_opening_date", todayStr).apply()
                }
            }
        }

        // 2b. Automated Patient Refill Outbound Trigger (7:30 AM – 8:14 AM)
        if (isMorningRefillOutbound) {
            val lastSent = prefs.getString("last_refill_outbound_date", "")
            if (lastSent != todayStr) {
                val title = "📲 7:30 AM Refill Command Center ($dueTodayCount Pending)"
                val text = if (hasTwilioGateway) {
                    "Automated WhatsApp/SMS refill reminders dispatched to $dueTodayCount patients due today at $pharmacyDisplayName. Tap to track real-time delivery."
                } else {
                    "$dueTodayCount patient refills due today at $pharmacyDisplayName. Outbound queue ready (Twilio setup pending). Tap to review & dispatch via 1-tap WhatsApp."
                }
                val dispatched = showNotification(
                    context, title, text,
                    targetTab = "customers", targetSubTab = "refill_reminders",
                    urgency = com.example.util.NotificationUrgency.STANDARD,
                    dedupKey = "refill_outbound_730am_${todayStr}"
                )
                if (dispatched) {
                    prefs.edit().putString("last_refill_outbound_date", todayStr).apply()
                }
            }
        }

        // 2c. Refill Queue Dispatch Notification (8:15 AM – 8:44 AM)
        if (isRefillDispatch) {
            val lastSent = prefs.getString("last_refill_dispatch_date", "")
            if (lastSent != todayStr) {
                val confirmedTasks = tasks.filter { !it.isCompleted && (it.category.contains("Refill", ignoreCase = true) || it.title.contains("Refill", ignoreCase = true) || it.title.contains("Pre-label", ignoreCase = true)) }
                val confirmedCount = confirmedTasks.size
                val title: String
                val text: String
                if (confirmedCount > 0) {
                    title = "📦 8:15 AM Refill Queue Dispatch ($confirmedCount Orders)"
                    text = "$confirmedCount patient refill orders ready for pre-labeling & packaging in opening stage. Tap to view and fulfill queue."
                } else {
                    title = "📦 8:15 AM Refill Queue Stage ($dueTodayCount Scheduled)"
                    text = "Pre-opening refill queue active. $dueTodayCount patients due today. Tap to confirm patient responses and queue pre-labeling before 9 AM opening."
                }
                val dispatched = showNotification(
                    context, title, text,
                    targetTab = "customers", targetSubTab = "refill_reminders",
                    urgency = com.example.util.NotificationUrgency.STANDARD,
                    targetRole = "Staff",
                    dedupKey = "refill_dispatch_815am_${todayStr}"
                )
                if (dispatched) {
                    prefs.edit().putString("last_refill_dispatch_date", todayStr).apply()
                }
            }
        }

        // 2d. Unconfirmed Refill Escalation Alert (8:45 AM – 8:59 AM)
        if (isUnconfirmedRefillAlert) {
            val lastSent = prefs.getString("last_unconfirmed_alert_date", "")
            if (lastSent != todayStr) {
                val unconfirmedCount = dueTodayCount.coerceAtLeast(1)
                val title = "⚠️ 8:45 AM Unconfirmed Refill Escalation ($unconfirmedCount Patients)"
                val text = "$unconfirmedCount chronic care patients haven't confirmed refills due today. Flagged for pharmacist follow-up call before morning rush. Tap to view list & call."
                val dispatched = showNotification(
                    context, title, text,
                    targetTab = "customers", targetSubTab = "refill_reminders",
                    urgency = com.example.util.NotificationUrgency.CRITICAL,
                    targetRole = "Pharmacist",
                    dedupKey = "unconfirmed_alert_845am_${todayStr}"
                )
                if (dispatched) {
                    prefs.edit().putString("last_unconfirmed_alert_date", todayStr).apply()
                }
            }
        }

        // 3. Morning Stock Reconciliation Audit Window (9:00 AM – 10:59 AM)
        if (isMorningReconcile && inventory.isNotEmpty()) {
            val lastSent = prefs.getString("last_morning_reconcile_date", "")
            if (lastSent != todayStr) {
                val cutoff14Days = System.currentTimeMillis() - (14L * 24 * 60 * 60 * 1000L)
                val overdueCount = inventory.count { it.lastReconciledAt < cutoff14Days }
                val reconciledCount = inventory.size - overdueCount
                val cycleRatio = (reconciledCount).toFloat() / inventory.size.toFloat()
                val cycleRatioPctFormatted = String.format(java.util.Locale.US, "%.1f", cycleRatio * 100)

                val title: String
                val text: String
                if (cycleRatio < 0.80f) {
                    title = "Rolling Cycle Count Alert: $reconciledCount/${inventory.size} Reconciled ($cycleRatioPctFormatted%)"
                    text = "$reconciledCount of ${inventory.size} items reconciled in last 14 days ($overdueCount overdue). Tap to conduct physical count."
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
                val dispatched = showNotification(context, title, text, targetTab = "branch_team", targetSubTab = "ops_task_board", dedupKey = "stock_reconcile_$todayStr")
                if (dispatched) {
                    prefs.edit().putString("last_morning_reconcile_date", todayStr).apply()
                }
            }
        }

        // 4. Priority Refill Reminders (11:00 AM – 1:59 PM)
        if (isPriorityRefill && dueRefills.isNotEmpty()) {
            val lastSent = prefs.getString("last_priority_refill_date", "")
            if (lastSent != todayStr) {
                val lastIndex = prefs.getInt("last_refill_notification_index", -1)
                val currentIndex = (lastIndex + 1) % dueRefills.size
                prefs.edit().putInt("last_refill_notification_index", currentIndex).apply()

                val targetMed = dueRefills[currentIndex]
                val targetPatient = customers.find { it.id == targetMed.customerId }
                if (targetPatient != null) {
                    val patientName = targetPatient.name.trim()
                    val title = RefillNotificationSchedule.formatRefillTitle(patientName)
                    val text = RefillNotificationSchedule.formatRefillMessage(
                        patientName = patientName,
                        medicationName = targetMed.medicationName,
                        dosage = targetMed.customDosage,
                        phone = targetPatient.phoneNumber
                    )
                    val dispatched = showNotification(context, title, text, targetTab = "customers", targetCustomerName = patientName, dedupKey = "priority_refill_$todayStr")
                    if (dispatched) {
                        prefs.edit().putString("last_priority_refill_date", todayStr).apply()
                    }
                }
            }
        }

        // 5. Low-Stock & Reorder Audit (2:00 PM – 3:59 PM)
        if (isAfternoonRestock && lowStockItems.isNotEmpty()) {
            val lastSent = prefs.getString("last_afternoon_restock_date", "")
            if (lastSent != todayStr) {
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
                val dispatched = showNotification(
                    context, title, text, 
                    targetTab = "branch_team", targetSubTab = "ops_task_board",
                    urgency = com.example.util.NotificationUrgency.STANDARD,
                    dedupKey = "lowstock_${todayStr}_${targetItem.id}"
                )
                if (dispatched) {
                    prefs.edit().putString("last_afternoon_restock_date", todayStr).apply()
                }
            }
        }

        // 6a. Morning Short-Dated & Expiry Warning (10:00 AM – 10:59 AM)
        if (isMorningExpiry && expiringItems.isNotEmpty()) {
            val lastSent = prefs.getString("last_morning_expiry_date", "")
            if (lastSent != todayStr) {
                val lastExpiryIndex = prefs.getInt("last_expiry_notification_index", -1)
                val currentExpiryIndex = (lastExpiryIndex + 1) % expiringItems.size
                prefs.edit().putInt("last_expiry_notification_index", currentExpiryIndex).apply()

                val targetItem = expiringItems[currentExpiryIndex]
                val isExpired = targetItem.expiryDate in 1L..System.currentTimeMillis()
                val title = "🌅 Morning " + RefillNotificationSchedule.formatExpiryTitle(targetItem.name, targetItem.expiryDate)
                val text = RefillNotificationSchedule.formatExpiryMessage(
                    medicationName = targetItem.name,
                    dosage = targetItem.dosage,
                    batchNo = targetItem.batchNumber,
                    expiryDateMs = targetItem.expiryDate,
                    qtyLeft = targetItem.stockQuantity
                )
                val dispatched = showNotification(
                    context, title, text, 
                    targetTab = "branch_team", targetSubTab = "ops_task_board",
                    urgency = if (isExpired) com.example.util.NotificationUrgency.CRITICAL else com.example.util.NotificationUrgency.STANDARD,
                    targetRole = "Pharmacist",
                    dedupKey = "morning_expiry_${todayStr}_${targetItem.batchNumber}"
                )
                if (dispatched) {
                    prefs.edit().putString("last_morning_expiry_date", todayStr).apply()
                }
            }
        }

        // 6b. Afternoon Short-Dated & Expiry Warning (4:00 PM – 5:59 PM)
        if (isPriorityExpiry && expiringItems.isNotEmpty()) {
            val lastSent = prefs.getString("last_afternoon_expiry_date", "")
            if (lastSent != todayStr) {
                val lastExpiryIndex = prefs.getInt("last_expiry_notification_index", -1)
                val currentExpiryIndex = (lastExpiryIndex + 1) % expiringItems.size
                prefs.edit().putInt("last_expiry_notification_index", currentExpiryIndex).apply()

                val targetItem = expiringItems[currentExpiryIndex]
                val isExpired = targetItem.expiryDate in 1L..System.currentTimeMillis()
                val title = "🌆 Afternoon " + RefillNotificationSchedule.formatExpiryTitle(targetItem.name, targetItem.expiryDate)
                val text = RefillNotificationSchedule.formatExpiryMessage(
                    medicationName = targetItem.name,
                    dosage = targetItem.dosage,
                    batchNo = targetItem.batchNumber,
                    expiryDateMs = targetItem.expiryDate,
                    qtyLeft = targetItem.stockQuantity
                )
                val dispatched = showNotification(
                    context, title, text, 
                    targetTab = "branch_team", targetSubTab = "ops_task_board",
                    urgency = if (isExpired) com.example.util.NotificationUrgency.CRITICAL else com.example.util.NotificationUrgency.STANDARD,
                    targetRole = "Pharmacist",
                    dedupKey = "afternoon_expiry_${todayStr}_${targetItem.batchNumber}"
                )
                if (dispatched) {
                    prefs.edit().putString("last_afternoon_expiry_date", todayStr).apply()
                }
            }
        }

        // 7a. Pre-Closing Audit (8:00 PM – 8:59 PM)
        if (isEveningAudit) {
            val lastAuditSentDate = prefs.getString("last_evening_audit_date", "")
            if (lastAuditSentDate != todayStr) {
                val title = RefillNotificationSchedule.formatEveningAuditTitle(pendingTasks)
                val text = RefillNotificationSchedule.formatEveningAuditMessage(pendingTasks)
                val dispatched = showNotification(
                    context, title, text,
                    targetTab = "branch_team", targetSubTab = "ops_task_board",
                    urgency = com.example.util.NotificationUrgency.STANDARD,
                    dedupKey = "evening_audit_$todayStr"
                )
                if (dispatched) {
                    prefs.edit().putString("last_evening_audit_date", todayStr).apply()
                }
            }
        }

        // 7b. Final Shift Closing Summary (8:30 PM – 9:59 PM)
        if (isEveningClosing) {
            val lastClosingSentDate = prefs.getString("last_evening_closing_date", "")
            if (lastClosingSentDate != todayStr) {
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
                val dispatched = showNotification(
                    context, title, text, 
                    targetTab = targetTab, targetSubTab = "ops_task_board",
                    urgency = com.example.util.NotificationUrgency.DIGEST,
                    dedupKey = "evening_summary_$todayStr"
                )
                if (dispatched) {
                    prefs.edit().putString("last_evening_closing_date", todayStr).apply()
                }
            }
        }

        // 8. Shift Handover & AI Task Delegation Window (6:00 PM – 7:59 PM)
        if (isEveningTaskDelegation) {
            val lastDelegationDate = prefs.getString("last_ai_delegation_date", "")
            if (lastDelegationDate != todayStr) {
                val insights = CarefluxAIEngine.generateInsights(apiKey, inventory, customers, validMeds, volumes)
                insights?.let {
                    val firstTask = it.highPriorityTasks.firstOrNull()
                    if (firstTask != null) {
                        val title = RefillNotificationSchedule.formatTaskAssignedTitle(firstTask.title)
                        val text = RefillNotificationSchedule.formatTaskAssignedMessage("Careflux AI", firstTask.title)
                        val dispatched = showNotification(
                            context, title, text, 
                            targetTab = "branch_team", targetSubTab = "ops_task_board",
                            urgency = com.example.util.NotificationUrgency.STANDARD,
                            dedupKey = "ai_task_${todayStr}_${firstTask.title.hashCode()}"
                        )
                        if (dispatched) {
                            prefs.edit().putString("last_ai_delegation_date", todayStr).apply()
                        }
                    }
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
    ): Boolean {
        return com.example.util.SmartNotificationDispatcher.dispatchNotification(
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
