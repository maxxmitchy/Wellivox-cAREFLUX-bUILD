package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val phoneNumber: String,
    val email: String = "",
    val notes: String = "",
    val loyaltyPoints: Int = 0,
    val refillStreak: Int = 0,
    val dateAdded: Long = System.currentTimeMillis(),
    val age: Int = 30,
    val gender: String = "Male",   // Male, Female, Other
    val state: String = "Lagos",   // e.g., Lagos, Abuja, Rivers
    val lga: String = "Ikeja",     // Local Government Area
    val city: String = "Ikeja"
)
