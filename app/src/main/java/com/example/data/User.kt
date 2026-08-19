package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val globalId: String = UUID.randomUUID().toString(),
    val fullName: String,
    val phoneNumber: String,
    val passwordHash: String = "",
    val role: String = "PHARMACIST", // SUPER_ADMIN, BRANCH_MANAGER, PHARMACIST, CASHIER
    val isActive: Boolean = true,
    val lastLoginAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
