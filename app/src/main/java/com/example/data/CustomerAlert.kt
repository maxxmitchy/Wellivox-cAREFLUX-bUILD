package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "customer_alerts")
data class CustomerAlert(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val customerName: String,
    val phoneNumber: String, // E.g., "+1234567890" or "0901234567"
    val medicationName: String,
    val alertType: String, // "Daily Remind", "Refill Due", "Ready for Pickup"
    val status: String, // "Pending", "Sent"
    val scheduledTime: String, // "08:00 AM", "Morning", "Immediate"
    val timestamp: Long = System.currentTimeMillis()
) {
    // Generates pre-filled, highly professional pharmacy reminder messages for WhatsApp
    fun generateMessage(pharmacyName: String = "Community Pharmacy"): String {
        return when (alertType) {
            "Daily Remind" -> {
                "Hello, $customerName. This is a vital reminder from $pharmacyName to take your medication: $medicationName at your scheduled time ($scheduledTime). Consistent adherence ensures the best care. Stay healthy!"
            }
            "Refill Due" -> {
                "Friendly reminder from $pharmacyName: It is time to refill your prescription for $medicationName to ensure you don't run out. Reply or click to request a refill."
            }
            "Ready for Pickup" -> {
                "Great news, $customerName! Your prescription for $medicationName is fully prepared and ready for pickup at $pharmacyName. Please visit us to collect it at your earliest convenience."
            }
            else -> "Hello $customerName, this is a message from $pharmacyName regarding your medication: $medicationName."
        }
    }
}
