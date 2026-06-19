package com.example.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AIResponse(
    val highPriorityTasks: List<AITask>,
    val inventoryAlerts: List<AITask>,
    val patientFollowUps: List<AITask>,
    val businessOpportunities: List<Opportunity>,
    val riskAlerts: List<Alert>,
    val staffPerformanceGoals: List<Goal>,
    val suggestedWhatsAppMessages: List<WhatsAppMessage>
)

@JsonClass(generateAdapter = true)
data class AITask(
    val title: String,
    val description: String,
    val urgency: String // e.g. "High", "Medium"
)

@JsonClass(generateAdapter = true)
data class Opportunity(
    val recommendation: String,
    val potentialImpact: String
)

@JsonClass(generateAdapter = true)
data class Alert(
    val title: String,
    val severity: String,
    val details: String
)

@JsonClass(generateAdapter = true)
data class Goal(
    val goal: String,
    val target: String
)

@JsonClass(generateAdapter = true)
data class WhatsAppMessage(
    val to: String,
    val message: String
)
