package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "outbound_sms_logs")
data class OutboundSmsLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val recipientPhone: String,
    val messageContent: String,
    val deliveryStatus: String, // "Queued", "Sent", "Failed", "Delivered"
    val timestamp: Long = System.currentTimeMillis(),
    val gatewayUsed: String = "Twilio Multi-Channel Gateway",
    val errorMessage: String? = null,
    val channel: String = "SMS", // "SMS" or "WhatsApp"
    val messageType: String = "General", // "Refill Reminder", "Dispense Receipt", "Promo", "Welfare Check"
    val twilioSid: String? = null,
    val costEstimate: String = "$0.0075"
)

