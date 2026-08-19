package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import java.util.concurrent.ConcurrentHashMap

enum class NotificationUrgency {
    CRITICAL, // High priority compliance blockers (expired quarantine, severe stock discrepancies, unverified controlled drug logs)
    STANDARD, // Normal operations (incoming stock transfers, routine refill follow-ups, unassigned staff tasks)
    DIGEST    // Low priority shift digests & sales summaries (silent)
}

object SmartNotificationDispatcher {

    // Notification Channel IDs
    const val CHANNEL_CRITICAL = "careflux_critical_channel"
    const val CHANNEL_OPERATIONS = "careflux_ops_channel"
    const val CHANNEL_DIGEST = "careflux_digest_channel"

    // In-memory deduplication cache: key -> timestamp when last dispatched
    private val recentDispatches = ConcurrentHashMap<String, Long>()
    private const val DEDUPLICATION_WINDOW_MS = 15 * 60 * 1000L // 15 minutes deduplication window

    fun dispatchNotification(
        context: Context,
        title: String,
        content: String,
        urgency: NotificationUrgency = NotificationUrgency.STANDARD,
        targetRole: String? = null, // "Manager", "Pharmacist", "Staff", or null for all
        targetBranchId: String? = null,
        targetTab: String = "branch_team",
        targetSubTab: String? = "ops_task_board",
        targetTaskId: Long? = null,
        targetCustomerName: String? = null,
        dedupKey: String? = null,
        isCompletedTask: Boolean = false
    ): Boolean {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        // 1. Check Global Notification Toggle
        if (!prefs.getBoolean("notifications_enabled", true)) return false

        // 1b. Check Granular Category Notification Preferences
        val lowerTitle = title.lowercase()
        val lowerContent = content.lowercase()
        val isExpiry = lowerTitle.contains("expiry") || lowerTitle.contains("expired") || lowerContent.contains("expiry") || lowerContent.contains("fefo")
        val isLowStock = lowerTitle.contains("stock") || lowerTitle.contains("restock") || lowerContent.contains("low stock") || lowerContent.contains("reorder")
        val isRestockCutoff = lowerTitle.contains("cutoff") || lowerContent.contains("cutoff") || lowerTitle.contains("supplier order")
        val isCycleCount = lowerTitle.contains("cycle count") || lowerContent.contains("cycle count") || lowerTitle.contains("reconcil") || lowerTitle.contains("audit")
        val isRefill = lowerTitle.contains("refill") || lowerTitle.contains("outbound") || lowerContent.contains("refill")
        val isFollowup = lowerTitle.contains("follow-up") || lowerTitle.contains("intervention") || lowerContent.contains("follow-up") || lowerContent.contains("intervention")
        val isEscalation = lowerTitle.contains("escalation") || lowerTitle.contains("unconfirmed") || lowerContent.contains("escalat")
        val isBriefing = lowerTitle.contains("opening") || lowerTitle.contains("closing") || lowerTitle.contains("briefing") || lowerTitle.contains("summary")
        val isTaskAssignment = lowerTitle.contains("task") || lowerContent.contains("assigned to you")
        val isStockTransfer = lowerTitle.contains("transfer") || lowerContent.contains("stock transfer")

        if (isExpiry && (!prefs.getBoolean("notif_pref_expiry", true))) return false
        if (isLowStock && (!prefs.getBoolean("notif_pref_low_stock", true))) return false
        if (isRestockCutoff && (!prefs.getBoolean("notif_pref_restock_cutoff", true))) return false
        if (isCycleCount && (!prefs.getBoolean("notif_pref_cycle_count", true) || !prefs.getBoolean("notif_pref_cycle_counts", true))) return false
        if (isRefill && (!prefs.getBoolean("notif_pref_refill", true) || !prefs.getBoolean("notif_pref_refill_outbound", true))) return false
        if (isFollowup && (!prefs.getBoolean("notif_pref_followup", true))) return false
        if (isTaskAssignment && (!prefs.getBoolean("notif_pref_task_assignment", true))) return false
        if (isStockTransfer && (!prefs.getBoolean("notif_pref_stock_transfer", true))) return false
        if (isEscalation && !prefs.getBoolean("notif_pref_escalations", true)) return false
        if (isBriefing && !prefs.getBoolean("notif_pref_shift_briefings", true)) return false

        // 2. Active Completed Task Suppressor: If target task is already completed, suppress notification
        if (isCompletedTask) return false

        // 3. Branch Context Filtering: If target branch is specified and does not match staff's current branch, suppress
        val cachedBranchId = prefs.getString("cached_branch_id", null)
        if (!targetBranchId.isNullOrBlank() && !cachedBranchId.isNullOrBlank() && targetBranchId != cachedBranchId) {
            return false
        }

        // 4. Role Context Filtering:
        val cachedRole = prefs.getString("cached_role", "Pharmacist") ?: "Pharmacist"
        val isManager = cachedRole.equals("Branch Manager", ignoreCase = true) || 
                        cachedRole.equals("Admin", ignoreCase = true) || 
                        cachedRole.equals("Manager", ignoreCase = true)
        
        if (!targetRole.isNullOrBlank()) {
            if (targetRole.equals("Manager", ignoreCase = true) && !isManager) {
                return false // Staff members do not receive Manager-only notifications
            }
            if (targetRole.equals("Pharmacist", ignoreCase = true) && !isManager && !cachedRole.contains("Pharmacist", ignoreCase = true)) {
                return false // Non-pharmacists do not receive clinical/quarantine alerts
            }
        }

        // 5. Intelligent Deduplication Engine
        val effectiveDedupKey = dedupKey ?: "${title.trim()}_${content.trim()}"
        val lastDispatched = recentDispatches[effectiveDedupKey] ?: 0L
        val now = System.currentTimeMillis()
        val dedupWindow = if (urgency == NotificationUrgency.CRITICAL) 5 * 60 * 1000L else DEDUPLICATION_WINDOW_MS
        if (now - lastDispatched < dedupWindow) {
            // Suppress duplicate notification within window to prevent notification fatigue
            return false
        }

        // 6. Time Window Suppressors (Peak Rush & Overnight) for non-critical alerts
        if (urgency != NotificationUrgency.CRITICAL) {
            if (RefillNotificationSchedule.isPeakRushWindow(now)) {
                // Peak dispensing rush suppressor (12 PM - 2 PM) - suppress non-critical notifications to prevent distraction
                return false
            }
        }

        // 7. Ensure Notification Channels Exist
        setupNotificationChannels(context)

        // 8. Select Channel and Sound/Vibration Profile
        val channelId = when (urgency) {
            NotificationUrgency.CRITICAL -> CHANNEL_CRITICAL
            NotificationUrgency.STANDARD -> CHANNEL_OPERATIONS
            NotificationUrgency.DIGEST -> CHANNEL_DIGEST
        }

        val priority = when (urgency) {
            NotificationUrgency.CRITICAL -> NotificationCompat.PRIORITY_HIGH
            NotificationUrgency.STANDARD -> NotificationCompat.PRIORITY_DEFAULT
            NotificationUrgency.DIGEST -> NotificationCompat.PRIORITY_LOW
        }

        // 9. Build Deep Link Intent
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_TAB", targetTab)
            if (targetSubTab != null) putExtra("TARGET_SUB_TAB", targetSubTab)
            if (targetTaskId != null && targetTaskId > 0L) putExtra("TARGET_TASK_ID", targetTaskId)
            if (!targetCustomerName.isNullOrBlank()) putExtra("TARGET_CUSTOMER_NAME", targetCustomerName)
        }

        val deterministicId = if (targetTaskId != null && targetTaskId > 0L) {
            targetTaskId.toInt()
        } else {
            val stableKey = dedupKey ?: "${channelId}_${title.trim()}_${targetTab ?: ""}_${targetSubTab ?: ""}"
            (stableKey.hashCode() and 0x7FFFFFFF) % 900000 + 10000
        }

        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            deterministicId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationId = deterministicId

        // 10. Construct Notification
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(priority)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        // 10a. Add Notification Shade Direct System Actions
        if (targetTaskId != null && targetTaskId > 0L) {
            val claimIntent = Intent(context, com.example.receiver.NotificationActionReceiver::class.java).apply {
                action = com.example.receiver.NotificationActionReceiver.ACTION_CLAIM_TASK
                putExtra(com.example.receiver.NotificationActionReceiver.EXTRA_TASK_ID, targetTaskId)
                putExtra(com.example.receiver.NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            }
            val claimPendingIntent = PendingIntent.getBroadcast(
                context,
                (targetTaskId.toInt() * 10) + 1,
                claimIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_menu_add, "Claim Task", claimPendingIntent)

            if (urgency == NotificationUrgency.CRITICAL || title.contains("Expiry", ignoreCase = true) || title.contains("Expired", ignoreCase = true)) {
                val quarantineIntent = Intent(context, com.example.receiver.NotificationActionReceiver::class.java).apply {
                    action = com.example.receiver.NotificationActionReceiver.ACTION_QUARANTINE_TASK
                    putExtra(com.example.receiver.NotificationActionReceiver.EXTRA_TASK_ID, targetTaskId)
                    putExtra(com.example.receiver.NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                }
                val quarantinePendingIntent = PendingIntent.getBroadcast(
                    context,
                    (targetTaskId.toInt() * 10) + 2,
                    quarantineIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(android.R.drawable.ic_delete, "Mark Quarantined", quarantinePendingIntent)
            }
        }

        if (urgency == NotificationUrgency.CRITICAL) {
            builder.setVibrate(longArrayOf(0, 400, 200, 400))
        } else if (urgency == NotificationUrgency.DIGEST) {
            builder.setVibrate(longArrayOf(0L))
            builder.setSound(null)
        }
        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
            // Record dispatch timestamp for deduplication
            recentDispatches[effectiveDedupKey] = now
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun setupNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            // Critical Channel (High Importance, Vibration)
            if (manager.getNotificationChannel(CHANNEL_CRITICAL) == null) {
                val criticalChannel = NotificationChannel(
                    CHANNEL_CRITICAL,
                    "Critical & Compliance Blockers",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Urgent expired stock quarantine, severe discrepancies, and compliance alerts."
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 400, 200, 400)
                }
                manager.createNotificationChannel(criticalChannel)
            }

            // Operations Channel (Default Importance)
            if (manager.getNotificationChannel(CHANNEL_OPERATIONS) == null) {
                val opsChannel = NotificationChannel(
                    CHANNEL_OPERATIONS,
                    "Standard Operations & Tasks",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Incoming stock transfers, patient refill follow-ups, and assigned tasks."
                }
                manager.createNotificationChannel(opsChannel)
            }

            // Digest Channel (Low Importance, Silent)
            if (manager.getNotificationChannel(CHANNEL_DIGEST) == null) {
                val digestChannel = NotificationChannel(
                    CHANNEL_DIGEST,
                    "Shift Digest & Summaries",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Evening sales summaries and daily shift achievements (silent)."
                    setSound(null, null)
                    enableVibration(false)
                }
                manager.createNotificationChannel(digestChannel)
            }
        }
    }
}
