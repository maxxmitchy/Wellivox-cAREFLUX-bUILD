package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "inventory_ledger_entries")
data class InventoryLedgerEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val globalId: String = UUID.randomUUID().toString(),
    val inventoryItemId: Int = 0,
    val itemName: String,
    val batchNumber: String = "",
    val transactionType: String, // SALE, PURCHASE, BRANCH_TRANSFER, WRITE_OFF, RECONCILIATION_ADJUSTMENT, RETURN
    val debitAccount: String,    // e.g. "BRANCH:Lagos", "EXPENSE:WriteOff", "CUSTOMER:POS"
    val creditAccount: String,   // e.g. "SUPPLIER:Acquisitions", "BRANCH:Lagos"
    val quantity: Int,           // quantity moved
    val unitPrice: Double = 0.0,
    val totalValue: Double = 0.0,
    val referenceId: String = "",// Sale ID, Transfer ID, Batch ID, Reconcile ID
    val actorName: String = "Pharmacist",
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val syncStatus: String = "SYNCED"
)
