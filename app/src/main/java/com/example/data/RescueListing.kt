package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rescue_listings")
data class RescueListing(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val firestoreId: String = "",       // Remote ID for node-to-node sync
    val productName: String,
    val batchNumber: String = "",
    val quantity: Int = 0,
    val expiryDate: Long = 0L,
    val sellingPrice: Double = 0.0,
    val commissionPercentage: Double = 10.0,
    val rescueDurationDays: Int = 30,
    val ownerDeviceId: String = "",
    val ownerDeviceModel: String = "",
    val listedAt: Long = System.currentTimeMillis(),
    val status: String = "Available",   // Available, Accepted, Sold, Expired
    val acceptedByDeviceId: String = "",
    val acceptedByDeviceModel: String = "",
    val acceptedAt: Long = 0L,
    val soldAt: Long = 0L,
    val profitShareAmount: Double = 0.0
) {
    val isExpired: Boolean
        get() = (System.currentTimeMillis() - listedAt) > (rescueDurationDays * 24L * 60 * 60 * 1000)
}
