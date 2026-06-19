package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "admin_audit_logs")
data class AdminAuditLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val adminName: String,
    val actionPerformed: String,         // e.g., "SUSPEND_NODE", "REACTIVATE_NODE"
    val timestamp: Long = System.currentTimeMillis(),
    val affectedNodeId: String = "",
    val affectedNodeModel: String = "",
    val reason: String = ""
)
