package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object RefillNotificationSchedule {

    // 1. 7:00 AM – 7:29 AM: Morning Opening Operational Readiness & AI Stock Briefing
    fun isMorningOpeningWindow(currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = currentTimeMs }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        return hour == 7 && minute < 30
    }

    // 2. 7:30 AM – 8:14 AM: Automated Patient Refill WhatsApp/SMS Outbound
    fun isMorningRefillOutboundWindow(currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = currentTimeMs }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        return (hour == 7 && minute >= 30) || (hour == 8 && minute < 15)
    }

    // 3. 8:15 AM – 8:44 AM: Refill Queue Dispatch Notification for Technician
    fun isRefillDispatchWindow(currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = currentTimeMs }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        return hour == 8 && minute in 15..44
    }

    // 4. 8:45 AM – 8:59 AM: Unconfirmed Refill Clinical Alert for Pharmacist
    fun isUnconfirmedRefillAlertWindow(currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = currentTimeMs }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        return hour == 8 && minute in 45..59
    }

    // 5. 9:00 AM – 11:00 AM: Morning Stock Reconciliation Audit Window
    fun isMorningStockReconciliationWindow(currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = currentTimeMs }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return hour in 9..10 // 9:00 AM to 10:59 AM
    }

    // Legacy alias
    fun isMorningRefillWindow(currentTimeMs: Long = System.currentTimeMillis()): Boolean = isMorningStockReconciliationWindow(currentTimeMs)

    // 6. 11:00 AM – 2:00 PM: Extended Priority Patient Refills Window (3 Hours)
    fun isPriorityRefillWindow(currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = currentTimeMs }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return hour in 11..13 // 11:00 AM - 1:59 PM (3-hour window to distribute large patient datasets)
    }

    fun isEveningRefillWindow(currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = currentTimeMs }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return hour in 20..21
    }

    // 7. 12:00 PM – 2:00 PM: Peak Dispensing Rush Window
    fun isPeakRushWindow(currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = currentTimeMs }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return hour in 12..13 // 12:00 PM to 1:59 PM
    }

    // 8. 2:00 PM – 4:00 PM: Afternoon Low-Stock & Reorder Audit Window
    fun isAfternoonRestockWindow(currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = currentTimeMs }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return hour in 14..15 // 2:00 PM to 3:59 PM
    }

    // 9. 4:00 PM – 6:00 PM: Expiry & Short-Dated Stock Alert Window
    fun isPriorityExpiryWindow(currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = currentTimeMs }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return hour in 16..17 // 4:00 PM to 5:59 PM
    }

    // 10:00 AM – 10:59 AM: Morning Short-Dated & Expiry Warning Window
    fun isMorningExpiryWindow(currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = currentTimeMs }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return hour == 10
    }

    // 10. 6:00 PM – 8:00 PM: Shift Handover & Delegation Window
    fun isEveningTaskDelegationWindow(currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = currentTimeMs }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return hour in 18..19 // 6:00 PM to 7:59 PM
    }

    // 11. 8:00 PM – 8:29 PM: Pre-Closing Audit & Night Till Reconciliation Window
    fun isEveningAuditWindow(currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = currentTimeMs }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        return hour == 20 && minute < 30 // 8:00 PM to 8:29 PM
    }

    // 12. 8:30 PM – 9:59 PM: Final Shift Closing & Achievements Summary Window
    fun isEveningClosingWindow(currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = currentTimeMs }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        return (hour == 20 && minute >= 30) || (hour == 21)
    }

    // 13. 10:00 PM – 7:00 AM: Overnight Quiet Window
    fun isOvernightQuietWindow(currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = currentTimeMs }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return hour >= 22 || hour < 7 // 10:00 PM to 6:59 AM
    }

    fun getWindowBadgeLabel(currentTimeMs: Long = System.currentTimeMillis()): String = "Refill Reminder"

    // --- Specific Notification Formatters ---

    // 9 AM - 11 AM Formatter
    fun formatStockReconciliationTitle(medicationName: String): String {
        val cleanMed = medicationName.trim().ifBlank { "Medication" }
        return "Reconcile Stock: $cleanMed"
    }

    fun formatStockReconciliationMessage(
        medicationName: String,
        dosage: String,
        batchNo: String
    ): String {
        val cleanMed = medicationName.trim().ifBlank { "Medication" }
        val cleanDosage = dosage.trim().takeIf { it.isNotBlank() && !it.equals("N/A", ignoreCase = true) } ?: "500mg"
        val cleanBatch = batchNo.trim().takeIf { it.isNotBlank() && !it.equals("N/A", ignoreCase = true) } ?: "B8204"
        return "Count and verify $cleanMed $cleanDosage (Batch $cleanBatch). If the physical count differs, update the quantity and provide a reason."
    }

    // 11 AM - 12 PM Formatter
    fun formatRefillTitle(patientName: String): String {
        val cleanName = patientName.trim().takeIf { it.isNotBlank() && !it.equals("Patient", ignoreCase = true) } ?: "Valued Patient"
        return "Refill Due: $cleanName"
    }

    fun formatRefillMessage(
        patientName: String,
        medicationName: String,
        dosage: String,
        phone: String = "+2348000000000"
    ): String {
        val cleanName = patientName.trim().takeIf { it.isNotBlank() && !it.equals("Patient", ignoreCase = true) } ?: "Valued Patient"
        val cleanMed = medicationName.trim().ifBlank { "Medication" }
        val cleanDosage = dosage.trim().takeIf { it.isNotBlank() && !it.equals("N/A", ignoreCase = true) } ?: ""
        val cleanPhone = phone.trim().ifBlank { "+2348000000000" }
        val medWithDosage = if (cleanDosage.isNotBlank() && !cleanMed.contains(cleanDosage, ignoreCase = true)) {
            "$cleanMed $cleanDosage"
        } else {
            cleanMed
        }
        return "$cleanName needs a refill for $medWithDosage. Contact patient at $cleanPhone to confirm."
    }

    // 2 PM - 4 PM Formatter
    fun formatRestockTitle(medicationName: String): String {
        val cleanMed = medicationName.trim().ifBlank { "Medication" }
        return "Low Stock Warning: $cleanMed"
    }

    fun formatRestockMessage(
        medicationName: String,
        dosage: String,
        currentQty: Int,
        minQty: Int
    ): String {
        val cleanMed = medicationName.trim().ifBlank { "Medication" }
        val cleanDosage = dosage.trim().takeIf { it.isNotBlank() && !it.equals("N/A", ignoreCase = true) } ?: ""
        val medWithDosage = if (cleanDosage.isNotBlank() && !cleanMed.contains(cleanDosage, ignoreCase = true)) {
            "$cleanMed $cleanDosage"
        } else {
            cleanMed
        }
        return "$medWithDosage is down to $currentQty units (below min $minQty). Tap to create a supplier purchase order."
    }

    // 4 PM - 6 PM Formatter
    fun formatExpiryTitle(
        medicationName: String,
        expiryDateMs: Long = 0L,
        currentTimeMs: Long = System.currentTimeMillis()
    ): String {
        val cleanMed = medicationName.trim().ifBlank { "Medication" }
        val isExpired = expiryDateMs in 1L..currentTimeMs
        return if (isExpired) "Expired Stock Alert: $cleanMed" else "Expiry Warning: $cleanMed"
    }

    fun formatExpiryMessage(
        medicationName: String,
        dosage: String,
        batchNo: String,
        expiryDateMs: Long,
        qtyLeft: Int,
        currentTimeMs: Long = System.currentTimeMillis()
    ): String {
        val cleanMed = medicationName.trim().ifBlank { "Medication" }
        val cleanDosage = dosage.trim().takeIf { it.isNotBlank() && !it.equals("N/A", ignoreCase = true) } ?: ""
        val cleanBatch = batchNo.trim().takeIf { it.isNotBlank() && !it.equals("N/A", ignoreCase = true) } ?: "Batch A1"
        val medWithDosage = if (cleanDosage.isNotBlank() && !cleanMed.contains(cleanDosage, ignoreCase = true)) {
            "$cleanMed $cleanDosage"
        } else {
            cleanMed
        }
        val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        val dateStr = if (expiryDateMs > 0) sdf.format(Date(expiryDateMs)) else "Soon"
        val isExpired = expiryDateMs in 1L..currentTimeMs
        return if (isExpired) {
            "$medWithDosage (Batch $cleanBatch) expired on $dateStr ($qtyLeft units left). Make sure you remove it from the shelf immediately and put it in quarantine."
        } else {
            "$medWithDosage (Batch $cleanBatch) expires on $dateStr ($qtyLeft units left). Tap to review or discount."
        }
    }

    // 8:00 PM - 8:59 PM Pre-Closing Audit Formatters
    fun formatEveningAuditTitle(pendingTasksCount: Int): String {
        return if (pendingTasksCount > 0) "📋 8:00 PM Night Audit ($pendingTasksCount Pending Tasks)" else "📋 8:00 PM Night Reconciliation & Till Audit"
    }

    fun formatEveningAuditMessage(pendingTasksCount: Int): String {
        return if (pendingTasksCount > 0) {
            "$pendingTasksCount task(s) remain pending before store shutdown. Tap to complete final till reconciliation & task review."
        } else {
            "All operational tasks completed for today! Tap to conduct final cash register & till reconciliation before closing."
        }
    }

    // 9:00 PM - 9:59 PM Closing Summary Formatters
    fun formatEveningSummaryTitle(hasSales: Boolean): String {
        return if (hasSales) "Shift Summary: Today's Revenue" else "End of Shift Summary"
    }

    fun formatEveningSummaryMessage(
        totalSalesAmount: Double,
        salesCount: Int,
        registeredCustomersCount: Int,
        completedTasksCount: Int,
        pendingTasksCount: Int
    ): String {
        return if (salesCount > 0 || totalSalesAmount > 0) {
            val formattedAmount = String.format(Locale.getDefault(), "%,.2f", totalSalesAmount)
            "Great job today! Total sales: ₦$formattedAmount across $salesCount transactions. Tap to verify till reconciliation."
        } else if (registeredCustomersCount > 0) {
            val suffix = if (registeredCustomersCount > 1) "s" else ""
            "You registered $registeredCustomersCount new patient$suffix today. Great work building customer relationships!"
        } else if (completedTasksCount > 0) {
            val suffix = if (completedTasksCount > 1) "s" else ""
            "You reconciled $completedTasksCount medication$suffix across staff today. Your inventory data is sharp!"
        } else {
            val suffix = if (pendingTasksCount != 1) "s" else ""
            "Shift almost over! $pendingTasksCount pending task$suffix remaining for tomorrow. Clean slate for closing!"
        }
    }

    // Task Assignment Formatter
    fun formatTaskAssignedTitle(taskTitle: String): String {
        val cleanTitle = taskTitle.trim().ifBlank { "Task" }
        return "Task Assigned: $cleanTitle"
    }

    fun formatTaskAssignedMessage(assignedBy: String, taskTitle: String): String {
        val cleanBy = assignedBy.trim().ifBlank { "Manager" }
        val cleanTitle = taskTitle.trim().ifBlank { "Review operations task" }
        return "$cleanBy assigned: \"$cleanTitle\". Tap to review on Ops Task Board."
    }
}
