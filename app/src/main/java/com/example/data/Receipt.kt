package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "receipts")
data class Receipt(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val customerName: String,
    val totalAmount: Double,
    val imageFileName: String,
    val isInvoice: Boolean = false,
    val paymentStatus: String = "Paid", // e.g., Paid, Pending, Rejected, Cancelled
    val orderId: String = "",
    val branchId: String = "",
    val originatingUserUid: String = ""
)
