package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clinical_interventions")
data class ClinicalIntervention(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val customerId: Int, // Link to Customer
    val presentation: String,
    val testResults: String,
    val recommendation: String,
    val currentStatus: String = "Pending", // Pending, Feeling Better
    val followUpDay3Sent: Boolean = false,
    val followUpDay7Sent: Boolean = false,
    val followUpDay14Sent: Boolean = false,
    val dateAdded: Long = System.currentTimeMillis(),
    val branchId: String = "",
    val originatingUserUid: String = ""
)
