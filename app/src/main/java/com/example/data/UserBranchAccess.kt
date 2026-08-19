package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_branch_access")
data class UserBranchAccess(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val branchId: String,
    val isPrimary: Boolean = true,
    val grantedAt: Long = System.currentTimeMillis()
)
