package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "customer_branches")
data class CustomerBranch(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val customerId: Int,
    val branchId: String,
    val firstSeenAt: Long = System.currentTimeMillis(),
    val lastInteractionAt: Long = System.currentTimeMillis()
)
