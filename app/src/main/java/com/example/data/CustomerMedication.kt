package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "customer_medications")
data class CustomerMedication(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val customerId: Int,
    val inventoryItemId: Int,
    val medicationName: String,
    val customDosage: String,
    val cost: Double,
    val cycleDays: Int,
    val nextRefillDate: Long, // Timestamp in ms
    val dateAdded: Long = System.currentTimeMillis()
)
