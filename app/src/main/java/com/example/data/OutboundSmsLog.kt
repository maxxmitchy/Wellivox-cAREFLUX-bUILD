package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "outbound_sms_logs")
data class OutboundSmsLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val recipientPhone: String,
    val messageContent: String,
    val deliveryStatus: String, // "Delivered", "Failed", "Pending", "Fallback WhatsApp Redirected"
    val timestamp: Long = System.currentTimeMillis(),
    val gatewayUsed: String, // "Termii API" or "Local Device Fallback"
    val errorMessage: String? = null
)
