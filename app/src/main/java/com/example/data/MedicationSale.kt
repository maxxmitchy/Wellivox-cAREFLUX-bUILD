package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medication_sales")
data class MedicationSale(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val productName: String,
    val brand: String = "",
    val genericName: String = "",
    val category: String = "",
    val quantitySold: Int,
    val dateSold: Long = System.currentTimeMillis(),
    val pharmacyNode: String = "",         // Name or ID of device node
    val patientAge: Int = 30,
    val patientGender: String = "Male",    // Male, Female, Other
    val patientState: String = "Lagos",    // Location fields
    val patientLga: String = "Ikeja",
    val patientCity: String = "Ikeja",
    val salePrice: Double = 0.0,
    val batchNumber: String = "",
    val clientTransactionId: String = "",
    val branchId: String = "",
    val originatingUserUid: String = ""
)
