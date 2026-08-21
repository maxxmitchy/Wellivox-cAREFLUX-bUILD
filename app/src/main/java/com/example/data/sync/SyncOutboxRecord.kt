package com.example.data.sync

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "sync_outbox",
    indices = [
        Index(value = ["branchId"]),
        Index(value = ["status"]),
        Index(value = ["clientTransactionId"])
    ]
)
data class SyncOutboxRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val outboxId: String = UUID.randomUUID().toString(),
    val branchId: String,
    val entityType: String,        // CUSTOMER, INVENTORY, CUSTOMER_MEDICATION, INTERVENTION, TASK, RECEIPT, SALE, SMS_LOG
    val entityId: String,          // Primary key or remote ID string
    val operationType: String,     // UPSERT, DELETE, SALE_SYNC
    val payloadJson: String,       // JSON representation of entity/data
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val lastAttemptAt: Long = 0L,
    val status: String = "PENDING",// PENDING, IN_PROGRESS, SYNCED, FAILED, BLOCKED
    val clientTransactionId: String = "",
    val originatingUserUid: String = "",
    val errorMessage: String? = null
)
