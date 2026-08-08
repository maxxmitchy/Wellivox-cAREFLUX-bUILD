@file:OptIn(ExperimentalLayoutApi::class)
package com.example.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Customer
import com.example.data.CustomerMedication
import com.example.data.ClinicalIntervention
import com.example.ui.theme.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.text.SimpleDateFormat
import java.util.*

// ==========================================
// SERIALIZABLE MODELS FOR PATIENT INTELLIGENCE
// ==========================================

@Serializable
data class PatientIntelligence(
    val diagnoses: List<String> = emptyList(),
    val activeProblems: List<String> = emptyList(),
    val medicationTimeline: List<TimelineEvent> = emptyList(),
    val labTimeline: LabTimeline = LabTimeline(),
    val vitalSignsTimeline: List<VitalSignEvent> = emptyList(),
    val baseline: PatientBaseline = PatientBaseline(),
    val clinicalAlerts: List<String> = emptyList(),
    val monitoringChecklist: List<ChecklistItem> = emptyList(),
    val pharmacistTasks: List<ChecklistItem> = emptyList(),
    val clinicalConfidence: List<ConfidenceItem> = emptyList(),
    val aiSummary: String = "",
    val dialysisDetails: DialysisDetails = DialysisDetails(),
    val hivDetails: HivDetails = HivDetails(),
    val pendingInvestigations: List<String> = emptyList(),
    val clinicalTimeline: List<ClinicalCardEvent> = emptyList()
)

@Serializable
data class TimelineEvent(val month: String, val event: String)

@Serializable
data class LabTimeline(
    val hemoglobin: List<LabValue> = emptyList(),
    val platelets: List<LabValue> = emptyList(),
    val calcium: List<LabValue> = emptyList(),
    val phosphate: List<LabValue> = emptyList(),
    val potassium: List<LabValue> = emptyList(),
    val sodium: List<LabValue> = emptyList(),
    val creatinine: List<LabValue> = emptyList(),
    val egfr: List<LabValue> = emptyList(),
    val urea: List<LabValue> = emptyList(),
    val albumin: List<LabValue> = emptyList(),
    val wbc: List<LabValue> = emptyList(),
    val hba1c: List<LabValue> = emptyList()
)

@Serializable
data class LabValue(val month: String, val value: Double)

@Serializable
data class VitalSignEvent(val date: String, val bp: String, val hr: String, val weight: String)

@Serializable
data class PatientBaseline(
    val bp: String = "N/A",
    val hb: String = "N/A",
    val platelets: String = "N/A",
    val potassium: String = "N/A",
    val weight: String = "N/A",
    val dialysis: String = "None",
    val residualUrine: String = "Normal"
)

@Serializable
data class ChecklistItem(val name: String, val isCompleted: Boolean = false)

@Serializable
data class ConfidenceItem(val finding: String, val confidence: String) // Confirmed, Highly Likely, Probable, Possible

@Serializable
data class DialysisDetails(
    val frequency: String = "None",
    val dryWeight: String = "N/A",
    val residualUrine: String = "N/A"
)

@Serializable
data class HivDetails(
    val status: String = "Negative/Not Assessed",
    val cd4Count: String = "N/A",
    val viralLoad: String = "N/A"
)

@Serializable
data class ClinicalCardEvent(
    val date: String,
    val title: String,
    val description: String,
    val noteType: String = "Standard" // Standard, Critical, Refill
)

// ==========================================
// JSON SERIALIZER HELPER
// ==========================================

object PatientIntelligenceParser {
    private val json = Json { 
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    fun parse(customer: Customer, medications: List<CustomerMedication>): PatientIntelligence {
        val notes = customer.notes.trim()
        if (notes.startsWith("{") && notes.endsWith("}")) {
            try {
                return json.decodeFromString<PatientIntelligence>(notes)
            } catch (e: Exception) {
                // Fallback to default if JSON is corrupt
            }
        }
        return generateDefaultProfile(customer, medications)
    }

    fun serialize(intelligence: PatientIntelligence): String {
        return try {
            json.encodeToString(intelligence)
        } catch (e: Exception) {
            ""
        }
    }

    fun appendTextNote(customer: Customer, medications: List<CustomerMedication> = emptyList(), noteText: String): Customer {
        val notes = customer.notes.trim()
        if (notes.startsWith("{") && notes.endsWith("}")) {
            try {
                val intelligence = parse(customer, medications)
                val dateFormat = SimpleDateFormat("dd MMM", java.util.Locale.getDefault())
                val dateStr = dateFormat.format(java.util.Date())
                val updatedTimeline = intelligence.clinicalTimeline + ClinicalCardEvent(
                    date = dateStr,
                    title = "System Note",
                    description = noteText,
                    noteType = "Standard"
                )
                val updatedIntelligence = intelligence.copy(
                    clinicalTimeline = updatedTimeline,
                    aiSummary = if (intelligence.aiSummary.isBlank()) noteText else "${intelligence.aiSummary}\n• ${noteText}"
                )
                return customer.copy(notes = serialize(updatedIntelligence))
            } catch (e: Exception) {
                // Ignore and fallback
            }
        }
        
        val currentNotes = customer.notes
        val newNotes = if (currentNotes.isBlank()) noteText.trim() else "${currentNotes}\n• ${noteText.trim()}"
        return customer.copy(notes = newNotes)
    }

    private fun generateDefaultProfile(customer: Customer, medications: List<CustomerMedication>): PatientIntelligence {
        val nameLower = customer.name.lowercase()
        return when {
            nameLower.contains("uzoma") -> {
                PatientIntelligence(
                    diagnoses = listOf(
                        "ESRD on haemodialysis",
                        "Resistant hypertension",
                        "HIV",
                        "CKD Mineral Bone Disease",
                        "Chronic anaemia",
                        "Persistent thrombocytopenia"
                    ),
                    activeProblems = listOf(
                        "High BP -> Likely volume overload -> Need dry weight review",
                        "Anaemia improving",
                        "Platelets falling",
                        "Phosphate elevated"
                    ),
                    medicationTimeline = listOf(
                        TimelineEvent("March", "Started Amlodipine"),
                        TimelineEvent("April", "Added Torsemide"),
                        TimelineEvent("June", "Changed Torsemide 20→40 mg"),
                        TimelineEvent("July", "Added Doxazosin")
                    ),
                    labTimeline = LabTimeline(
                        hemoglobin = listOf(LabValue("May", 6.5), LabValue("June", 8.7), LabValue("July", 9.2)),
                        platelets = listOf(LabValue("May", 197.0), LabValue("June", 100.0), LabValue("July", 97.0)),
                        calcium = listOf(LabValue("May", 2.01), LabValue("June", 1.83)),
                        phosphate = listOf(LabValue("May", 1.32), LabValue("June", 1.87))
                    ),
                    vitalSignsTimeline = listOf(
                        VitalSignEvent("17 Jul", "164/112", "78", "69 kg"),
                        VitalSignEvent("25 Jul", "158/104", "80", "68.5 kg")
                    ),
                    baseline = PatientBaseline(
                        bp = "160/110",
                        hb = "9.2",
                        platelets = "97",
                        potassium = "4.1",
                        weight = "69 kg",
                        dialysis = "3x/week",
                        residualUrine = "Present"
                    ),
                    clinicalAlerts = listOf(
                        "⚠ Resistant hypertension",
                        "⚠ ESRD",
                        "⚠ Dialysis patient",
                        "⚠ On Eliquis",
                        "⚠ HIV",
                        "⚠ Persistent thrombocytopenia",
                        "⚠ Avoid NSAIDs",
                        "⚠ Sevelamer MUST be taken with meals",
                        "⚠ Monitor calcium/phosphate",
                        "⚠ Monitor bleeding"
                    ),
                    monitoringChecklist = listOf(
                        ChecklistItem("Ferritin", false),
                        ChecklistItem("TSAT", false),
                        ChecklistItem("Reticulocyte count", false),
                        ChecklistItem("PTH", true),
                        ChecklistItem("Vitamin D", false),
                        ChecklistItem("Albumin", true),
                        ChecklistItem("Kt/V", false),
                        ChecklistItem("URR", false),
                        ChecklistItem("Viral load", true),
                        ChecklistItem("CD4", true),
                        ChecklistItem("Dry weight", false),
                        ChecklistItem("ESA review", false)
                    ),
                    pharmacistTasks = listOf(
                        ChecklistItem("Review BP next refill", false),
                        ChecklistItem("Check adherence", false),
                        ChecklistItem("Request Ferritin", false),
                        ChecklistItem("Counsel on phosphate diet", false),
                        ChecklistItem("Review Sevelamer timing", false)
                    ),
                    clinicalConfidence = listOf(
                        ConfidenceItem("ESRD", "🟢 Confirmed"),
                        ConfidenceItem("Resistant hypertension", "🟢 Confirmed"),
                        ConfidenceItem("Volume overload causing hypertension", "🟡 Highly likely"),
                        ConfidenceItem("Secondary hyperparathyroidism", "🟡 Probable (awaiting PTH)"),
                        ConfidenceItem("Dialysis inadequacy", "🟠 Possible (needs Kt/V/URR)"),
                        ConfidenceItem("Iron deficiency", "🟡 Probable (needs ferritin/TSAT)")
                    ),
                    aiSummary = "27-year-old dialysis patient with resistant hypertension likely secondary to volume overload and CKD-related RAAS activation. Anaemia improving (Hb 6.5→9.2). Persistent thrombocytopenia. Hyperphosphataemia despite Sevelamer. Pending PTH and iron studies.",
                    dialysisDetails = DialysisDetails("3x/week", "69 kg", "Present"),
                    hivDetails = HivDetails("Positive", "450 cells/uL", "Undetectable"),
                    pendingInvestigations = listOf("Ferritin", "TSAT", "PTH test"),
                    clinicalTimeline = listOf(
                        ClinicalCardEvent("17 Jul", "BP Uncontrolled", "BP uncontrolled. No clinical emergency. Refill/Regimen Adjusted.", "Critical"),
                        ClinicalCardEvent("25 Jul", "Hb Improving", "Anemia showing steady improvement. Continue ESA.", "Standard"),
                        ClinicalCardEvent("05 Aug", "Phosphate Elevated", "Dietary phosphorus reinforced. Checked Sevelamer timing.", "Standard")
                    )
                )
            }
            nameLower.contains("celestine") -> {
                PatientIntelligence(
                    diagnoses = listOf(
                        "Severe Peptic Ulcer Disease",
                        "GERD",
                        "Chronic Gastritis",
                        "Mild Anaemia"
                    ),
                    activeProblems = listOf(
                        "Epigastric pain radiating to back",
                        "Adherence check needed for Omeprazole",
                        "Iron stores might be depleted"
                    ),
                    medicationTimeline = listOf(
                        TimelineEvent("January", "Started Antacids"),
                        TimelineEvent("February", "Added Ranitidine"),
                        TimelineEvent("April", "Switched to Omeprazole 20mg BID"),
                        TimelineEvent("June", "Changed to Esomeprazole 40mg QD")
                    ),
                    labTimeline = LabTimeline(
                        hemoglobin = listOf(LabValue("May", 11.2), LabValue("June", 10.8), LabValue("July", 11.5)),
                        platelets = listOf(LabValue("May", 250.0), LabValue("June", 240.0), LabValue("July", 245.0)),
                        calcium = listOf(LabValue("May", 2.4), LabValue("June", 2.42)),
                        phosphate = listOf(LabValue("May", 1.1), LabValue("June", 1.05))
                    ),
                    vitalSignsTimeline = listOf(
                        VitalSignEvent("12 Jun", "122/82", "72", "75 kg"),
                        VitalSignEvent("15 Jul", "118/78", "75", "74.8 kg")
                    ),
                    baseline = PatientBaseline(
                        bp = "120/80",
                        hb = "11.5",
                        platelets = "245",
                        potassium = "4.4",
                        weight = "75 kg",
                        dialysis = "None",
                        residualUrine = "Normal"
                    ),
                    clinicalAlerts = listOf(
                        "⚠ Severe Peptic Ulcer",
                        "⚠ Avoid NSAIDs / Diclofenac",
                        "⚠ Take Esomeprazole 30 mins before breakfast",
                        "⚠ Monitor stool color for bleeding",
                        "⚠ Monitor Hb level"
                    ),
                    monitoringChecklist = listOf(
                        ChecklistItem("H. pylori Stool Antigen", true),
                        ChecklistItem("Hemoglobin level", false),
                        ChecklistItem("Endoscopy followup", false),
                        ChecklistItem("Serum Ferritin", false)
                    ),
                    pharmacistTasks = listOf(
                        ChecklistItem("Verify Esomeprazole dose timing", false),
                        ChecklistItem("Counsel on lifestyle & spicy foods", false),
                        ChecklistItem("Check for dark / tarry stools", false)
                    ),
                    clinicalConfidence = listOf(
                        ConfidenceItem("Severe Peptic Ulcer Disease", "🟢 Confirmed"),
                        ConfidenceItem("NSAID-induced ulcer risk", "🟡 Highly likely"),
                        ConfidenceItem("Iron deficiency secondary to blood loss", "🟠 Possible")
                    ),
                    aiSummary = "Patient presenting with a history of recurrent epigastric pain and gastric acidity. Currently on Esomeprazole with mild improvement. Hb levels stable but borderline. Avoid any NSAIDs and counsel on dietary triggers.",
                    dialysisDetails = DialysisDetails("None", "N/A", "N/A"),
                    hivDetails = HivDetails("Negative", "N/A", "N/A"),
                    pendingInvestigations = listOf("Serum Ferritin", "Endoscopy"),
                    clinicalTimeline = listOf(
                        ClinicalCardEvent("12 Jun", "Consult: Epigastric Pain", "Severe epigastric burning pain. Initiated Esomeprazole daily.", "Standard"),
                        ClinicalCardEvent("15 Jul", "Consult: Mild Relief", "Symptoms improved but burning persists at night. Adjusted dosing time.", "Standard")
                    )
                )
            }
            else -> {
                // Default Profile for any generic customer
                val isHypertensive = medications.any { it.medicationName.lowercase().contains("amlodipine") || it.medicationName.lowercase().contains("lisinopril") || it.medicationName.lowercase().contains("doxazosin") }
                val isDiabetic = medications.any { it.medicationName.lowercase().contains("metformin") || it.medicationName.lowercase().contains("insulin") }
                
                val calculatedDiagnoses = mutableListOf<String>()
                val calculatedAlerts = mutableListOf<String>()
                
                if (isHypertensive) {
                    calculatedDiagnoses.add("Essential Hypertension")
                    calculatedAlerts.add("⚠ Monitor Daily Blood Pressure")
                    calculatedAlerts.add("⚠ Keep salt intake minimal")
                }
                if (isDiabetic) {
                    calculatedDiagnoses.add("Type 2 Diabetes Mellitus")
                    calculatedAlerts.add("⚠ Check fasting blood glucose")
                    calculatedAlerts.add("⚠ Inspect feet daily for neuropathy")
                }
                if (calculatedDiagnoses.isEmpty()) {
                    calculatedDiagnoses.add("General Care Protocol")
                    calculatedAlerts.add("⚠ General health check advised")
                }
                
                PatientIntelligence(
                    diagnoses = calculatedDiagnoses,
                    activeProblems = listOf("General patient followup", "Check medication adherence"),
                    medicationTimeline = listOf(
                        TimelineEvent("Current", "Dispensed active medicines: " + medications.joinToString { it.medicationName })
                    ),
                    labTimeline = LabTimeline(),
                    vitalSignsTimeline = emptyList(),
                    baseline = PatientBaseline(
                        bp = "N/A",
                        hb = "N/A",
                        platelets = "N/A",
                        potassium = "N/A",
                        weight = "N/A",
                        dialysis = "None",
                        residualUrine = "Normal"
                    ),
                    clinicalAlerts = calculatedAlerts,
                    monitoringChecklist = listOf(
                        ChecklistItem("Basic Metabolic Panel", false),
                        ChecklistItem("Complete Blood Count", false)
                    ),
                    pharmacistTasks = listOf(
                        ChecklistItem("Assess medication compliance", false),
                        ChecklistItem("Answer patient health questions", false)
                    ),
                    clinicalConfidence = listOf(
                        ConfidenceItem("Indicated Conditions", "🟢 Confirmed")
                    ),
                    aiSummary = "Longitudinal patient record initialized. Active medications: ${medications.joinToString { it.medicationName }}.",
                    dialysisDetails = DialysisDetails("None", "N/A", "N/A"),
                    hivDetails = HivDetails("Not Assessed", "N/A", "N/A"),
                    pendingInvestigations = emptyList(),
                    clinicalTimeline = listOf(
                        ClinicalCardEvent("Current", "Record Created", "Added patient file to digital pharmacist register.", "Standard")
                    )
                )
            }
        }
    }
}

// ==========================================
// MAIN COMPOSABLE: PATIENT INTELLIGENCE DASHBOARD
// ==========================================

@Composable
fun PatientIntelligenceDashboard(
    customer: Customer,
    medications: List<CustomerMedication>,
    interventions: List<ClinicalIntervention>,
    viewModel: PharmacyViewModel,
    context: Context,
    onAddInterventionClick: () -> Unit,
    onCloseClick: (() -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(0) }
    
    // Parse the clinical profile from notes
    val intelligence = remember(customer, medications) {
        PatientIntelligenceParser.parse(customer, medications)
    }

    // Helper to persist changes
    val saveChanges: (PatientIntelligence) -> Unit = { updated ->
        val serialized = PatientIntelligenceParser.serialize(updated)
        viewModel.updateCustomer(customer.copy(notes = serialized))
    }

    // AI Assist Dialog States
    var showAiBpAssist by remember { mutableStateOf<String?>(null) } // holds BP value if triggered
    var showAiHbAssist by remember { mutableStateOf<Double?>(null) } // holds Hb value if triggered

    // Dialog Edit States
    var showEditDiagnosesDialog by remember { mutableStateOf(false) }
    var showEditProblemsDialog by remember { mutableStateOf(false) }
    var showEditBaselineDialog by remember { mutableStateOf(false) }
    var showEditAlertsDialog by remember { mutableStateOf(false) }
    var showAddLabTimelineDialog by remember { mutableStateOf(false) }
    var showResetConfirmationDialog by remember { mutableStateOf(false) }

    // Intercept when new clinical interventions are loaded, and if there is a fresh one we trigger the AI Assistant!
    // Since we want this to trigger "when they enter a consult", let's hook it up.
    LaunchedEffect(interventions) {
        if (interventions.isNotEmpty()) {
            val latest = interventions.maxByOrNull { it.dateAdded }
            if (latest != null && (System.currentTimeMillis() - latest.dateAdded) < 8000) {
                // A very recent intervention was added!
                // Let's parse latest.presentation or latest.testResults for BP and Hb
                val textToScan = "${latest.presentation} ${latest.testResults}".lowercase()
                
                // Scan for Blood Pressure pattern like 164/112
                val bpRegex = """(\d{2,3})\s*/\s*(\d{2,3})""".toRegex()
                val bpMatch = bpRegex.find(textToScan)
                if (bpMatch != null) {
                    val bpVal = bpMatch.value
                    // If BP is high compared to normal baseline
                    showAiBpAssist = bpVal
                }

                // Scan for Hb level like "hb 8.3" or "hb: 8.3" or "hemoglobin 8.3"
                val hbRegex = """hb\s*[:\s]*\s*([0-9.]+)""".toRegex()
                val hbMatch = hbRegex.find(textToScan)
                if (hbMatch != null) {
                    hbMatch.groupValues.getOrNull(1)?.toDoubleOrNull()?.let { hbVal ->
                        showAiHbAssist = hbVal
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.2.dp, SlateBorderLight)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Section
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(TealPrimary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Hub,
                                contentDescription = "Intelligence",
                                tint = TealPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "PATIENT CLINICAL INTELLIGENCE",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = TealPrimary,
                                letterSpacing = 0.5.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(OKGreen)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Active Clinical Workspace • ${customer.name}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SlateTextMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onAddInterventionClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TealPrimary,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AddCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Clinical Consult",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        OutlinedButton(
                            onClick = { showResetConfirmationDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(36.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Clear Hub",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Clear Hub",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        if (onCloseClick != null) {
                            IconButton(
                                onClick = onCloseClick,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Close Workspace",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Tabs Selector
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = TealPrimary,
                    edgePadding = 12.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(
                        "Summary & AI" to Icons.Filled.AutoAwesome,
                        "Problems & Alerts" to Icons.Filled.WarningAmber,
                        "Labs & Diagnostics" to Icons.Filled.Timeline,
                        "Tasks & Checklist" to Icons.Filled.PlaylistAddCheck,
                        "Timelines & Notes" to Icons.Filled.History
                    ).forEachIndexed { index, (label, icon) ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            icon = { Icon(icon, contentDescription = label, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }

                // Tab Content Frame (Fixed Header & Scrollable Content Frame)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    when (selectedTab) {
                        0 -> SummaryAndAiTab(
                            intelligence = intelligence,
                            onEditBaselineClick = { showEditBaselineDialog = true },
                            saveChanges = saveChanges
                        )
                        1 -> ProblemsAndAlertsTab(
                            intelligence = intelligence,
                            onEditDiagnosesClick = { showEditDiagnosesDialog = true },
                            onEditProblemsClick = { showEditProblemsDialog = true },
                            onEditAlertsClick = { showEditAlertsDialog = true }
                        )
                        2 -> LabsAndDiagnosticsTab(
                            intelligence = intelligence,
                            onAddLabClick = { showAddLabTimelineDialog = true }
                        )
                        3 -> TasksAndChecklistTab(
                            intelligence = intelligence,
                            onCheckedChange = { updated -> saveChanges(updated) }
                        )
                        4 -> TimelinesAndNotesTab(
                            customer = customer,
                            intelligence = intelligence,
                            interventions = interventions,
                            viewModel = viewModel,
                            context = context
                        )
                    }
                }
            }
        }
    }

    // ==========================================
    // DIALOGS & POPUPS FOR INTERACTION
    // ==========================================

    // 1. AI Assistant BP Suggestion Popup
    showAiBpAssist?.let { bpVal ->
        AlertDialog(
            onDismissRequest = { showAiBpAssist = null },
            icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(32.dp)) },
            title = { Text("Careflux Clinical Intelligence Assistant", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "High Blood Pressure Detected!",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "You entered BP reading: $bpVal.\nThis remains significantly elevated above the patient's normal baseline (Normal Baseline: ${intelligence.baseline.bp}).",
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Would you like Careflux AI to automatically classify this as 'Persistent uncontrolled hypertension' and add it to active problems?",
                        fontSize = 12.sp,
                        color = SlateTextMedium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val currentProbs = intelligence.activeProblems.toMutableList()
                        val currentAlerts = intelligence.clinicalAlerts.toMutableList()
                        
                        if (!currentProbs.contains("Persistent uncontrolled hypertension")) {
                            currentProbs.add("Persistent uncontrolled hypertension (Confirmed via Consult: BP $bpVal)")
                        }
                        if (!currentAlerts.contains("⚠ Persistent uncontrolled hypertension")) {
                            currentAlerts.add("⚠ Persistent uncontrolled hypertension (BP $bpVal)")
                        }
                        
                        saveChanges(intelligence.copy(
                            activeProblems = currentProbs,
                            clinicalAlerts = currentAlerts,
                            clinicalTimeline = intelligence.clinicalTimeline + ClinicalCardEvent(
                                date = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date()),
                                title = "Uncontrolled BP Alert",
                                description = "AI automatically flagged persistent hypertension. BP: $bpVal.",
                                noteType = "Critical"
                            )
                        ))
                        Toast.makeText(context, "Added persistent hypertension to patient profile", Toast.LENGTH_LONG).show()
                        showAiBpAssist = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                ) {
                    Text("YES, Classify")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAiBpAssist = null }) {
                    Text("No, Skip")
                }
            }
        )
    }

    // 2. AI Assistant Hb Drop Suggestion Popup
    showAiHbAssist?.let { hbVal ->
        AlertDialog(
            onDismissRequest = { showAiHbAssist = null },
            icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(32.dp)) },
            title = { Text("Careflux Clinical Intelligence Assistant", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                val baseHb = intelligence.baseline.hb.toDoubleOrNull() ?: 9.2
                val diff = baseHb - hbVal
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Anaemia Trend Warning!",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "You entered Hemoglobin level: $hbVal g/dL.\nThis represents a drop of ${String.format("%.1f", diff)} g/dL from baseline ($baseHb g/dL).",
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Would you like Careflux AI to flag 'Worsening anaemia' as a high-priority patient problem?",
                        fontSize = 12.sp,
                        color = SlateTextMedium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val currentProbs = intelligence.activeProblems.toMutableList()
                        val currentAlerts = intelligence.clinicalAlerts.toMutableList()
                        
                        if (!currentProbs.contains("Worsening anaemia")) {
                            currentProbs.add("Worsening anaemia (Hb dropped to $hbVal g/dL)")
                        }
                        if (!currentAlerts.contains("⚠ Worsening anaemia (Hb: $hbVal g/dL)")) {
                            calculatedAlertsPlaceholder(currentAlerts, hbVal)
                        }
                        
                        saveChanges(intelligence.copy(
                            activeProblems = currentProbs,
                            clinicalAlerts = currentAlerts,
                            clinicalTimeline = intelligence.clinicalTimeline + ClinicalCardEvent(
                                date = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date()),
                                title = "Worsening Anaemia Alert",
                                description = "Hb dropped to $hbVal. AI auto-flagged warning.",
                                noteType = "Critical"
                            )
                        ))
                        Toast.makeText(context, "Flagged worsening anaemia on clinical dashboard", Toast.LENGTH_LONG).show()
                        showAiHbAssist = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                ) {
                    Text("YES, Flag")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAiHbAssist = null }) {
                    Text("No, Skip")
                }
            }
        )
    }

    // Edit Baseline Dialog
    if (showEditBaselineDialog) {
        var editBp by remember { mutableStateOf(intelligence.baseline.bp) }
        var editHb by remember { mutableStateOf(intelligence.baseline.hb) }
        var editPlt by remember { mutableStateOf(intelligence.baseline.platelets) }
        var editPot by remember { mutableStateOf(intelligence.baseline.potassium) }
        var editWt by remember { mutableStateOf(intelligence.baseline.weight) }
        var editDialysis by remember { mutableStateOf(intelligence.baseline.dialysis) }
        var editUrine by remember { mutableStateOf(intelligence.baseline.residualUrine) }

        AlertDialog(
            onDismissRequest = { showEditBaselineDialog = false },
            title = { Text("Edit Patient Baseline", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(value = editBp, onValueChange = { editBp = it }, label = { Text("Baseline BP") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = editHb, onValueChange = { editHb = it }, label = { Text("Baseline Hemoglobin (g/dL)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = editPlt, onValueChange = { editPlt = it }, label = { Text("Baseline Platelets") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = editPot, onValueChange = { editPot = it }, label = { Text("Baseline Potassium") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = editWt, onValueChange = { editWt = it }, label = { Text("Baseline Weight") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = editDialysis, onValueChange = { editDialysis = it }, label = { Text("Dialysis Frequency") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = editUrine, onValueChange = { editUrine = it }, label = { Text("Residual Urine") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    saveChanges(intelligence.copy(
                        baseline = PatientBaseline(
                            bp = editBp, hb = editHb, platelets = editPlt, potassium = editPot,
                            weight = editWt, dialysis = editDialysis, residualUrine = editUrine
                        )
                    ))
                    showEditBaselineDialog = false
                    Toast.makeText(context, "Baseline clinical parameters updated.", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditBaselineDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Edit Diagnoses Dialog
    if (showEditDiagnosesDialog) {
        var diagnosesText by remember { mutableStateOf(intelligence.diagnoses.joinToString("\n")) }
        AlertDialog(
            onDismissRequest = { showEditDiagnosesDialog = false },
            title = { Text("Edit Diagnoses Profile", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter patient diagnoses (one per line):", fontSize = 12.sp, color = SlateTextMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = diagnosesText,
                        onValueChange = { diagnosesText = it },
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                        maxLines = 10
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val list = diagnosesText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                    saveChanges(intelligence.copy(diagnoses = list))
                    showEditDiagnosesDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showEditDiagnosesDialog = false }) { Text("Cancel") } }
        )
    }

    // Edit Active Problems Dialog
    if (showEditProblemsDialog) {
        var problemsText by remember { mutableStateOf(intelligence.activeProblems.joinToString("\n")) }
        AlertDialog(
            onDismissRequest = { showEditProblemsDialog = false },
            title = { Text("Edit Active Problems", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter current problems (one per line):", fontSize = 12.sp, color = SlateTextMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = problemsText,
                        onValueChange = { problemsText = it },
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                        maxLines = 10
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val list = problemsText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                    saveChanges(intelligence.copy(activeProblems = list))
                    showEditProblemsDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showEditProblemsDialog = false }) { Text("Cancel") } }
        )
    }

    // Edit Alerts Dialog
    if (showEditAlertsDialog) {
        var alertsText by remember { mutableStateOf(intelligence.clinicalAlerts.joinToString("\n")) }
        AlertDialog(
            onDismissRequest = { showEditAlertsDialog = false },
            title = { Text("Edit Clinical Warnings", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter clinical alerts (one per line):", fontSize = 12.sp, color = SlateTextMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = alertsText,
                        onValueChange = { alertsText = it },
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                        maxLines = 10
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val list = alertsText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                    saveChanges(intelligence.copy(clinicalAlerts = list))
                    showEditAlertsDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showEditAlertsDialog = false }) { Text("Cancel") } }
        )
    }

    // Add Lab Timeline Dialog
    if (showAddLabTimelineDialog) {
        var month by remember { mutableStateOf("Aug") }
        var valueVal by remember { mutableStateOf("") }
        var selectedLabType by remember { mutableStateOf(0) } // 0: Hb, 1: Platelets, 2: Calcium, 3: Phosphate, 4: Potassium, 5: Sodium, 6: Creatinine, 7: eGFR, 8: BUN, 9: Albumin, 10: WBC, 11: HbA1c

        AlertDialog(
            onDismissRequest = { showAddLabTimelineDialog = false },
            title = { Text("Record New Laboratory Trend", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Select Lab Parameter:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "Hemoglobin", "Platelets", "Calcium", "Phosphate",
                            "Potassium", "Sodium", "Creatinine", "eGFR",
                            "BUN/Urea", "Albumin", "WBC", "HbA1c"
                        ).forEachIndexed { idx, label ->
                            FilterChip(
                                selected = selectedLabType == idx,
                                onClick = { selectedLabType = idx },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                    OutlinedTextField(value = month, onValueChange = { month = it }, label = { Text("Month (e.g. Aug)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        value = valueVal, onValueChange = { valueVal = it },
                        label = { Text("Numeric Value") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val dVal = valueVal.toDoubleOrNull()
                    if (dVal == null) {
                        Toast.makeText(context, "Please enter a valid number.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val currentTimeline = intelligence.labTimeline
                    val updatedTimeline = when (selectedLabType) {
                        0 -> currentTimeline.copy(hemoglobin = currentTimeline.hemoglobin + LabValue(month, dVal))
                        1 -> currentTimeline.copy(platelets = currentTimeline.platelets + LabValue(month, dVal))
                        2 -> currentTimeline.copy(calcium = currentTimeline.calcium + LabValue(month, dVal))
                        3 -> currentTimeline.copy(phosphate = currentTimeline.phosphate + LabValue(month, dVal))
                        4 -> currentTimeline.copy(potassium = currentTimeline.potassium + LabValue(month, dVal))
                        5 -> currentTimeline.copy(sodium = currentTimeline.sodium + LabValue(month, dVal))
                        6 -> currentTimeline.copy(creatinine = currentTimeline.creatinine + LabValue(month, dVal))
                        7 -> currentTimeline.copy(egfr = currentTimeline.egfr + LabValue(month, dVal))
                        8 -> currentTimeline.copy(urea = currentTimeline.urea + LabValue(month, dVal))
                        9 -> currentTimeline.copy(albumin = currentTimeline.albumin + LabValue(month, dVal))
                        10 -> currentTimeline.copy(wbc = currentTimeline.wbc + LabValue(month, dVal))
                        else -> currentTimeline.copy(hba1c = currentTimeline.hba1c + LabValue(month, dVal))
                    }
                    saveChanges(intelligence.copy(labTimeline = updatedTimeline))
                    showAddLabTimelineDialog = false
                    Toast.makeText(context, "New lab measurement logged.", Toast.LENGTH_SHORT).show()
                }) { Text("Record") }
            },
            dismissButton = { TextButton(onClick = { showAddLabTimelineDialog = false }) { Text("Cancel") } }
        )
    }

    // Reset Confirmation Dialog
    if (showResetConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmationDialog = false },
            title = { Text("Reset Clinical Hub?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Are you sure you want to delete all entries in this Patient Intelligence Hub? This will clear all diagnoses, active problems, baseline clinical parameters, timelines, and checklist states, letting you enter new clinical data from scratch.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val emptyIntelligence = PatientIntelligence(
                            diagnoses = emptyList(),
                            activeProblems = emptyList(),
                            medicationTimeline = emptyList(),
                            labTimeline = LabTimeline(),
                            vitalSignsTimeline = emptyList(),
                            baseline = PatientBaseline(
                                bp = "N/A", hb = "N/A", platelets = "N/A", potassium = "N/A", weight = "N/A", dialysis = "None", residualUrine = "Normal"
                            ),
                            clinicalAlerts = emptyList(),
                            monitoringChecklist = emptyList(),
                            pharmacistTasks = emptyList(),
                            clinicalConfidence = emptyList(),
                            aiSummary = "Clinical Intelligence Profile initialized from scratch. Click Edit to add diagnoses, baseline parameters, and notes.",
                            dialysisDetails = DialysisDetails(),
                            hivDetails = HivDetails(),
                            pendingInvestigations = emptyList(),
                            clinicalTimeline = listOf(
                                ClinicalCardEvent(
                                    date = SimpleDateFormat("dd MMM", java.util.Locale.getDefault()).format(java.util.Date()),
                                    title = "Clinical Hub Reset",
                                    description = "All previous clinical intelligence records cleared. Profile reset to blank slate.",
                                    noteType = "Standard"
                                )
                            )
                        )
                        saveChanges(emptyIntelligence)
                        showResetConfirmationDialog = false
                        Toast.makeText(context, "Clinical workspace reset to scratch.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = Color.White
                    )
                ) {
                    Text("Reset All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun calculatedAlertsPlaceholder(currentAlerts: MutableList<String>, hbVal: Double) {
    currentAlerts.add("⚠ Worsening anaemia (Hb dropped to $hbVal g/dL)")
}

// ==========================================
// SUB-TAB COMPOSABLES
// ==========================================

@Composable
fun SummaryAndAiTab(
    intelligence: PatientIntelligence,
    onEditBaselineClick: () -> Unit,
    saveChanges: (PatientIntelligence) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // AI Longitudinal Summary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = TealPrimary.copy(alpha = 0.05f)),
            border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.15f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = "AI", tint = TealPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "AI Clinical Summary",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TealPrimary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = intelligence.aiSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = TealTertiary,
                    lineHeight = 16.sp
                )
            }
        }

        // Clinical Confidence Board
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, SlateBorderLight),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.FactCheck, contentDescription = null, tint = OKGreen, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Evidence-Based Clinical Confidence",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = TealTertiary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (intelligence.clinicalConfidence.isEmpty()) {
                    Text("No confidence metrics recorded.", style = MaterialTheme.typography.bodySmall, color = SlateTextMedium)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        intelligence.clinicalConfidence.forEach { conf ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(SlateBackgroundLight)
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = conf.finding,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TealTertiary,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = conf.confidence,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Patient Baseline Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, SlateBorderLight),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Analytics, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Patient Baseline Parameters",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = TealTertiary
                        )
                    }
                    IconButton(onClick = onEditBaselineClick, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit Baseline", tint = TealPrimary, modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val items = listOf(
                        Triple("Blood Pressure", intelligence.baseline.bp, Icons.Filled.MonitorHeart),
                        Triple("Hemoglobin", if (intelligence.baseline.hb == "N/A") "N/A" else "${intelligence.baseline.hb} g/dL", Icons.Filled.Bloodtype),
                        Triple("Platelets", if (intelligence.baseline.platelets == "N/A") "N/A" else "${intelligence.baseline.platelets} K/uL", Icons.Filled.BubbleChart),
                        Triple("Potassium", if (intelligence.baseline.potassium == "N/A") "N/A" else "${intelligence.baseline.potassium} mEq/L", Icons.Filled.WaterDrop),
                        Triple("Weight", intelligence.baseline.weight, Icons.Filled.MonitorWeight),
                        Triple("Dialysis Frequency", intelligence.baseline.dialysis, Icons.Filled.HourglassEmpty),
                        Triple("Residual Urine", intelligence.baseline.residualUrine, Icons.Filled.Science)
                    )
                    
                    // Simple key-value display grid
                    items.chunked(2).forEach { pair ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SlateBackgroundLight)
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(item.third, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(item.first, style = MaterialTheme.typography.labelSmall, color = SlateTextMedium, fontSize = 8.sp)
                                        Text(item.second, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = TealTertiary)
                                    }
                                }
                            }
                            if (pair.size == 1) {
                                Box(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProblemsAndAlertsTab(
    intelligence: PatientIntelligence,
    onEditDiagnosesClick: () -> Unit,
    onEditProblemsClick: () -> Unit,
    onEditAlertsClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // High-Priority Clinical Warnings (Section 6)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.08f)),
            border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.25f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, contentDescription = "Alerts", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "CRITICAL CLINICAL ALERTS",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    IconButton(onClick = onEditAlertsClick, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit Alerts", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                if (intelligence.clinicalAlerts.isEmpty()) {
                    Text("No clinical warnings generated.", style = MaterialTheme.typography.bodySmall, color = SlateTextMedium)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        intelligence.clinicalAlerts.forEach { alert ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = alert.replace("⚠", "").trim(),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }

        // Diagnoses (Section 1)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, SlateBorderLight),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.MedicalInformation, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Patient Diagnoses",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = TealTertiary
                        )
                    }
                    IconButton(onClick = onEditDiagnosesClick, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = TealPrimary, modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (intelligence.diagnoses.isEmpty()) {
                    Text("No diagnoses entered.", style = MaterialTheme.typography.bodySmall, color = SlateTextMedium)
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        intelligence.diagnoses.forEach { diag ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(TealPrimary.copy(alpha = 0.08f))
                                    .border(0.5.dp, TealPrimary.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(10.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(diag, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TealTertiary)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Active Problems (Section 2)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, SlateBorderLight),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.List, contentDescription = null, tint = PendingOrange, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Today's Active Problems",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = TealTertiary
                        )
                    }
                    IconButton(onClick = onEditProblemsClick, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = TealPrimary, modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (intelligence.activeProblems.isEmpty()) {
                    Text("No active clinical problems.", style = MaterialTheme.typography.bodySmall, color = SlateTextMedium)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        intelligence.activeProblems.forEach { prob ->
                            val parts = prob.split("->").map { it.trim() }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(SlateBackgroundLight)
                                    .padding(8.dp)
                            ) {
                                if (parts.size > 1) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(parts[0], style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = TealTertiary)
                                        Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = SlateTextMedium, modifier = Modifier.size(12.dp).padding(horizontal = 4.dp))
                                        Text(parts[1], style = MaterialTheme.typography.bodySmall, color = SlateTextMedium)
                                        if (parts.size > 2) {
                                            Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = SlateTextMedium, modifier = Modifier.size(12.dp).padding(horizontal = 4.dp))
                                            Text(parts[2], style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = TealPrimary)
                                        }
                                    }
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(5.dp).clip(RoundedCornerShape(3.dp)).background(PendingOrange))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(prob, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = TealTertiary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LabsAndDiagnosticsTab(
    intelligence: PatientIntelligence,
    onAddLabClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Laboratory Timeline (Section 4)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, SlateBorderLight),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Science, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Lab Values Trend Explorer",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = TealTertiary
                        )
                    }
                    IconButton(onClick = onAddLabClick, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.Add, contentDescription = "Log Lab", tint = TealPrimary, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                // Trend display for the extended lab values
                listOf(
                    "Hemoglobin (g/dL)" to intelligence.labTimeline.hemoglobin,
                    "Platelets (K/uL)" to intelligence.labTimeline.platelets,
                    "Calcium (mmol/L)" to intelligence.labTimeline.calcium,
                    "Phosphate (mmol/L)" to intelligence.labTimeline.phosphate,
                    "Potassium (mEq/L)" to intelligence.labTimeline.potassium,
                    "Sodium (mEq/L)" to intelligence.labTimeline.sodium,
                    "Serum Creatinine (mg/dL)" to intelligence.labTimeline.creatinine,
                    "eGFR (mL/min/1.73m²)" to intelligence.labTimeline.egfr,
                    "BUN / Urea (mg/dL)" to intelligence.labTimeline.urea,
                    "Albumin (g/dL)" to intelligence.labTimeline.albumin,
                    "WBC Count (K/uL)" to intelligence.labTimeline.wbc,
                    "HbA1c (%)" to intelligence.labTimeline.hba1c
                ).forEach { (labName, trends) ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SlateBackgroundLight)
                            .padding(10.dp)
                    ) {
                        Text(labName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Black, color = TealPrimary)
                        Spacer(modifier = Modifier.height(6.dp))
                        if (trends.isEmpty()) {
                            Text("No history recorded for this parameter.", style = MaterialTheme.typography.labelSmall, color = SlateTextMedium)
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                trends.forEachIndexed { index, item ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(item.month, style = MaterialTheme.typography.labelSmall, color = SlateTextMedium, fontSize = 9.sp)
                                            Text("${item.value}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = TealTertiary)
                                        }
                                        if (index < trends.size - 1) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            val diff = trends[index+1].value - item.value
                                            val tint = if (diff > 0) OKGreen else if (diff < 0) MaterialTheme.colorScheme.error else SlateTextMedium
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(
                                                    imageVector = if (diff > 0) Icons.Filled.ArrowUpward else if (diff < 0) Icons.Filled.ArrowDownward else Icons.Filled.TrendingFlat,
                                                    contentDescription = null,
                                                    tint = tint,
                                                    modifier = Modifier.size(11.dp)
                                                )
                                                Text(
                                                    text = if (diff != 0.0) "${if (diff > 0) "+" else ""}${String.format("%.1f", diff)}" else "flat",
                                                    fontSize = 7.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = tint
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Vital Signs Timeline
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, SlateBorderLight),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.MonitorHeart, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Vital Signs Log",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = TealTertiary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (intelligence.vitalSignsTimeline.isEmpty()) {
                    Text("No vitals signs logged.", style = MaterialTheme.typography.bodySmall, color = SlateTextMedium)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        intelligence.vitalSignsTimeline.forEach { vit ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(SlateBackgroundLight)
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(vit.date, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TealPrimary)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("BP: ${vit.bp}", style = MaterialTheme.typography.labelSmall, color = TealTertiary, fontWeight = FontWeight.SemiBold)
                                    Text("HR: ${vit.hr}", style = MaterialTheme.typography.labelSmall, color = TealTertiary, fontWeight = FontWeight.SemiBold)
                                    Text("Wt: ${vit.weight}", style = MaterialTheme.typography.labelSmall, color = TealTertiary, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
        
    }
}

@Composable
fun TasksAndChecklistTab(
    intelligence: PatientIntelligence,
    onCheckedChange: (PatientIntelligence) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Monitoring Checklist (Section 7)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, SlateBorderLight),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Checklist, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Clinical Monitoring Checklist",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = TealTertiary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("Checked tests automatically disappear or fade to maintain focus.", fontSize = 10.sp, color = SlateTextMedium)
                Spacer(modifier = Modifier.height(10.dp))

                val sortedChecklist = remember(intelligence.monitoringChecklist) {
                    intelligence.monitoringChecklist.sortedBy { it.isCompleted }
                }

                if (sortedChecklist.isEmpty()) {
                    Text("No monitoring items defined.", style = MaterialTheme.typography.bodySmall, color = SlateTextMedium)
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        sortedChecklist.forEach { item ->
                            val chipBg = if (item.isCompleted) SlateBackgroundLight.copy(alpha = 0.5f) else TealPrimary.copy(alpha = 0.08f)
                            val textColor = if (item.isCompleted) SlateTextMedium.copy(alpha = 0.6f) else TealTertiary
                            val borderCol = if (item.isCompleted) SlateBorderLight.copy(alpha = 0.5f) else TealPrimary.copy(alpha = 0.2f)

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(chipBg)
                                    .border(0.5.dp, borderCol, RoundedCornerShape(8.dp))
                                    .clickable {
                                        val updatedList = intelligence.monitoringChecklist.map {
                                            if (it.name == item.name) it.copy(isCompleted = !it.isCompleted) else it
                                        }
                                        onCheckedChange(intelligence.copy(monitoringChecklist = updatedList))
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (item.isCompleted) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (item.isCompleted) OKGreen.copy(alpha = 0.7f) else TealPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Pharmacist Tasks Checklist (Section 9)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, SlateBorderLight),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.TaskAlt, contentDescription = null, tint = PendingOrange, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Interactive Pharmacist Tasks",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = TealTertiary
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                if (intelligence.pharmacistTasks.isEmpty()) {
                    Text("No task workflows generated.", style = MaterialTheme.typography.bodySmall, color = SlateTextMedium)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        intelligence.pharmacistTasks.forEach { task ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (task.isCompleted) SlateBackgroundLight.copy(alpha = 0.5f) else SlateBackgroundLight)
                                    .clickable {
                                        val updatedTasks = intelligence.pharmacistTasks.map {
                                            if (it.name == task.name) it.copy(isCompleted = !it.isCompleted) else it
                                        }
                                        onCheckedChange(intelligence.copy(pharmacistTasks = updatedTasks))
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (task.isCompleted) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (task.isCompleted) OKGreen.copy(alpha = 0.7f) else PendingOrange,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = task.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (task.isCompleted) SlateTextMedium.copy(alpha = 0.6f) else TealTertiary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TimelinesAndNotesTab(
    customer: Customer,
    intelligence: PatientIntelligence,
    interventions: List<ClinicalIntervention>,
    viewModel: PharmacyViewModel,
    context: Context
) {
    val sdf = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Clinical Timeline (Section 8)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, SlateBorderLight),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.QuestionAnswer, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Clinical Consultation Timeline",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = TealTertiary
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                if (intelligence.clinicalTimeline.isEmpty()) {
                    Text("No historical consultation timeline recorded.", style = MaterialTheme.typography.bodySmall, color = SlateTextMedium)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        intelligence.clinicalTimeline.forEach { log ->
                            val cardBg = if (log.noteType == "Critical") MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.12f) else SlateBackgroundLight
                            val cardBorder = if (log.noteType == "Critical") BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)) else BorderStroke(0.5.dp, SlateBorderLight)
                            
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = cardBg),
                                border = cardBorder
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(log.date, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = TealPrimary)
                                        if (log.noteType == "Critical") {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Text("CLINICAL ALERT", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(log.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = TealTertiary)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(log.description, style = MaterialTheme.typography.bodySmall, color = SlateTextMedium)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Medication History Timeline (Section 3)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, SlateBorderLight),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.History, contentDescription = null, tint = PendingOrange, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Medication Timeline Log",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = TealTertiary
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                if (intelligence.medicationTimeline.isEmpty()) {
                    Text("No medication milestones recorded.", style = MaterialTheme.typography.bodySmall, color = SlateTextMedium)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        intelligence.medicationTimeline.forEachIndexed { idx, ev ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(TealPrimary)
                                    )
                                    if (idx < intelligence.medicationTimeline.size - 1) {
                                        Box(
                                            modifier = Modifier
                                                .width(1.5.dp)
                                                .height(26.dp)
                                                .background(TealPrimary.copy(alpha = 0.3f))
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(ev.month, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TealPrimary)
                                    Text(ev.event, style = MaterialTheme.typography.bodySmall, color = TealTertiary)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Intervention History Logs
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, SlateBorderLight),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AssignmentTurnedIn, contentDescription = null, tint = OKGreen, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Consultation Records",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = TealTertiary
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                if (interventions.isEmpty()) {
                    Text("No consults saved in database.", style = MaterialTheme.typography.bodySmall, color = SlateTextMedium)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        interventions.forEach { interv ->
                            val statusColor = if (interv.currentStatus == "Feeling Better") OKGreen else PendingOrange
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SlateBackgroundLight)
                                    .padding(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(sdf.format(Date(interv.dateAdded)), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TealPrimary)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(statusColor.copy(alpha = 0.12f))
                                            .clickable {
                                                val newStatus = if (interv.currentStatus == "Feeling Better") "Follow-up Needed" else "Feeling Better"
                                                viewModel.updateClinicalInterventionStatus(interv, newStatus)
                                            }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(interv.currentStatus, fontSize = 8.sp, fontWeight = FontWeight.Black, color = statusColor)
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Symptoms:", style = MaterialTheme.typography.labelSmall, color = SlateTextMedium, fontWeight = FontWeight.Bold)
                                Text(interv.presentation, style = MaterialTheme.typography.bodySmall, color = TealTertiary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Recommendation:", style = MaterialTheme.typography.labelSmall, color = SlateTextMedium, fontWeight = FontWeight.Bold)
                                Text(interv.recommendation, style = MaterialTheme.typography.bodySmall, color = TealTertiary)
                                
                                if (interv.currentStatus != "Feeling Better") {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.generateAndSendFollowUp(interv, customer, context)
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TealPrimary),
                                        border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.3f)),
                                        modifier = Modifier.fillMaxWidth().height(28.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("AI Welfare Check SMS", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// COMPACT CLINICAL PREVIEW CARD
// ==========================================

@Composable
fun PatientIntelligencePreviewCard(
    customer: Customer,
    medications: List<CustomerMedication>,
    onOpenWorkspace: () -> Unit
) {
    val intelligence = remember(customer, medications) {
        PatientIntelligenceParser.parse(customer, medications)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenWorkspace() }
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = TealPrimary.copy(alpha = 0.03f)
        ),
        border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(TealPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Hub,
                            contentDescription = null,
                            tint = TealPrimary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "PATIENT CLINICAL INTELLIGENCE",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = TealPrimary,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(OKGreen)
                    )
                    Text(
                        text = "Active Workspace",
                        fontSize = 10.sp,
                        color = SlateTextMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = intelligence.aiSummary.ifEmpty { "Longitudinal health profile. Open the workspace to manage diagnoses, lab timelines, alerts, and active checklists." },
                style = MaterialTheme.typography.bodySmall,
                color = TealTertiary,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                lineHeight = 15.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (intelligence.diagnoses.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                            .border(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${intelligence.diagnoses.size} Diagnoses",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (intelligence.clinicalAlerts.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                            .border(0.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(9.dp))
                            Text(
                                text = "${intelligence.clinicalAlerts.count { !it.contains("⚠") || it.length > 2 }} Alerts",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                val uncompletedTasks = intelligence.pharmacistTasks.count { !it.isCompleted }
                if (uncompletedTasks > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFFFF3E0))
                            .border(0.5.dp, Color(0xFFFFB74D), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "$uncompletedTasks Tasks Pending",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onOpenWorkspace,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TealPrimary
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(
                        imageVector = Icons.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Access Clinical Intelligence Hub",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ==========================================
// DEDICATED CLINICAL WORKSPACE OVERLAY DIALOG
// ==========================================

@Composable
fun PatientIntelligenceWorkspaceDialog(
    customer: Customer,
    medications: List<CustomerMedication>,
    interventions: List<ClinicalIntervention>,
    viewModel: PharmacyViewModel,
    context: Context,
    onAddInterventionClick: () -> Unit,
    onDismissRequest: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismissRequest,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 6.dp
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                PatientIntelligenceDashboard(
                    customer = customer,
                    medications = medications,
                    interventions = interventions,
                    viewModel = viewModel,
                    context = context,
                    onAddInterventionClick = onAddInterventionClick,
                    onCloseClick = onDismissRequest
                )
            }
        }
    }
}



