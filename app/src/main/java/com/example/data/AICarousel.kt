package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_carousels")
data class AICarousel(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val topicTitle: String,
    val caption: String,
    val createdAt: Long = System.currentTimeMillis(),
    val slidesJson: String,
    val visualTheme: String = "Minimalist"
)
