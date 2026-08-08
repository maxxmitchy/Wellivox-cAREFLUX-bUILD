package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expiry_alert_claims")
data class ExpiryAlertClaim(
    @PrimaryKey val inventoryItemId: Int,
    val medicationName: String,
    val batchNumber: String = "",
    val expiryDate: Long = 0L,
    val claimedByStaffName: String = "",
    val claimTimestamp: Long = System.currentTimeMillis(),
    val status: String = "CLAIMED", // "CLAIMED", "RESOLVED"
    val actionTaken: String = "",   // e.g., "PRICE_DISCOUNT", "RESCUE_LISTING", "TRANSFER", "WRITE_OFF"
    val actionDetails: String = "", // e.g., "Applied 20% discount markdown"
    val actionTimestamp: Long = 0L
)
