package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "operations_tasks")
data class OperationTask(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val urgency: String, // High, Medium, Low
    val category: String, // Manual, AI Insight, Patient Follow-up
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
