package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "prescription_volumes")
data class DailyPrescriptionVolume(
    @PrimaryKey val dateString: String, // format YYYY-MM-DD
    val volume: Int,
    val notes: String = ""
)
