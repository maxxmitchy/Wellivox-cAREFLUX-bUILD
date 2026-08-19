package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inventory_items")
data class InventoryItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val dosage: String,
    val stockQuantity: Int,
    val minRequiredStock: Int,
    val category: String,
    val price: Double = 0.0,
    val expiryDate: Long = 0L,
    val batchNumber: String = "",
    val supplier: String = "",
    val unitForm: String = "",
    val lastSoldDate: Long = 0L,
    val totalSoldQuantity: Int = 0,
    val imageUri: String? = null,
    val brand: String = "",
    val salesStrategy: String = "",
    val isFastMoving: Boolean = false,
    val globalId: String = java.util.UUID.randomUUID().toString(),
    val syncStatus: String = "SYNCED",
    val lastUpdated: Long = System.currentTimeMillis(),
    val lastReconciledAt: Long = 0L
) {
    val isLowStock: Boolean
        get() = stockQuantity <= minRequiredStock
}
