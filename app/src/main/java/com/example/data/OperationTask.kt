package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "operations_tasks",
    indices = [
        Index(value = ["isCompleted"]),
        Index(value = ["createdAt"]),
        Index(value = ["category"]),
        Index(value = ["assignedToUid"])
    ]
)
data class OperationTask(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val urgency: String, // High, Medium, Low
    val category: String, // Manual, AI Insight, Patient Follow-up
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val verifiedBy: String? = null,
    val verificationNotes: String? = null,
    val verificationChannel: String? = null,
    val verificationCustomerName: String? = null,
    val verifiedAt: Long? = null,
    val isApproved: Boolean = false,
    val approvedBy: String? = null,
    val approvedAt: Long? = null,
    val approvalNotes: String? = null,
    val assignedToName: String? = null,
    val assignedToUid: String? = null
)
