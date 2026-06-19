package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@Entity(tableName = "triage_conditions")
data class TriageCondition(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val conditionName: String,
    val alternativeNames: String = "",
    val category: String,
    val briefDescription: String,
    val keySymptoms: String = "",
    val questionsJson: String = "[]", // Stores List<TriageQuestion> stringified in JSON
    val referralCriteria: String = "",
    val severityAssessment: String = "",
    val recommendedOtcs: String = "",
    val prescriptionOptions: String = "",
    val counsellingPoints: String = "",
    val lifestyleAdvice: String = "",
    val followUpTimeline: String = "",
    val whatsappTemplate: String = "",
    val isFavorite: Boolean = false,
    val usageCount: Int = 0,
    val lastEditedBy: String = "System",
    val lastUpdated: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class TriageQuestion(
    val question: String,
    val required: Boolean = true,
    val isRedFlag: Boolean = false
)
