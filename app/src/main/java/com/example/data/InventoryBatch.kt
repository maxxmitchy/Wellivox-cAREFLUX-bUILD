package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inventory_batches")
data class InventoryBatch(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val inventoryItemId: Int,
    val batchNumber: String,
    val stockQuantity: Int,
    val expiryDate: Long,
    val dateReceived: Long = System.currentTimeMillis(),
    val supplier: String = "",
    val price: Double = 0.0
)
