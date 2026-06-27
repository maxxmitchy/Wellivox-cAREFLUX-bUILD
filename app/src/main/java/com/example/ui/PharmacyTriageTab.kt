package com.example.ui

import com.example.ui.PharmacyViewModel.TriageAiState
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.TriageCondition
import com.example.data.TriageQuestion
import com.example.ui.theme.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.text.SimpleDateFormat
import java.util.*

private val moshiLocal = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
private val questionsLocalAdapter = moshiLocal.adapter<List<TriageQuestion>>(
    Types.newParameterizedType(List::class.java, TriageQuestion::class.java)
)

private fun parseQuestions(questionsJson: String): List<TriageQuestion> {
    return try {
        questionsLocalAdapter.fromJson(questionsJson) ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}

private fun encodeQuestions(questions: List<TriageQuestion>): String {
    return try {
        questionsLocalAdapter.toJson(questions)
    } catch (e: Exception) {
        "[]"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PharmacyTriageTabContent(
    viewModel: PharmacyViewModel
) {
    val context = LocalContext.current
    val conditions by viewModel.triageConditions.collectAsStateWithLifecycle()
    val triageAiState by viewModel.triageAiState.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    // Dialog state controllers
    var activeConditionForDetail by remember { mutableStateOf<TriageCondition?>(null) }
    var showCreateEditDialog by remember { mutableStateOf(false) }
    var editingCondition by remember { mutableStateOf<TriageCondition?>(null) }
    var showAiGeneratorDialog by remember { mutableStateOf(false) }
    var showWhatsAppShareDialog by remember { mutableStateOf<TriageCondition?>(null) }

    // Derive category filters dynamically
    val categories = remember(conditions) {
        listOf("All") + conditions.map { it.category }.distinct().filter { it.isNotBlank() }.sorted()
    }

    val filteredConditions = remember(conditions, searchQuery, selectedCategory) {
        conditions.filter { cond ->
            val matchesSearch = cond.conditionName.contains(searchQuery, ignoreCase = true) ||
                    cond.alternativeNames.contains(searchQuery, ignoreCase = true) ||
                    cond.keySymptoms.contains(searchQuery, ignoreCase = true) ||
                    cond.briefDescription.contains(searchQuery, ignoreCase = true) ||
                    cond.category.contains(searchQuery, ignoreCase = true)
            
            val matchesCategory = selectedCategory == "All" || cond.category.equals(selectedCategory, ignoreCase = true)
            matchesSearch && matchesCategory
        }
    }

    // Main layout
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp)
            .testTag("triage_main_container")
    ) {
        // Core Title Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp)
            ) {
                Text(
                    text = "Pharmacy Triage",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TealTertiary
                )
                Text(
                    text = "Clinical Knowledge Base & Patient Screener",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateTextMedium
                )
            }
            // Quick count badge
            Surface(
                color = TealSurface,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "${conditions.size} Conditions",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = TealTertiary,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        // Action Buttons Grid (Manual creation & AI generation)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { 
                    editingCondition = null
                    showCreateEditDialog = true 
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("create_protocol_button"),
                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Manual Protocol")
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "New Protocol",
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val aiBtnBg = if (AppThemeManager.isDark) Color(0xFF132D37) else Color(0xFFE0F7FA)
            val aiBtnText = TealPrimary
            val aiBtnBorder = if (AppThemeManager.isDark) TealPrimary.copy(alpha = 0.4f) else Color(0xFF0D9488)

            ElevatedButton(
                onClick = { 
                    viewModel.resetTriageAiState()
                    showAiGeneratorDialog = true 
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("ai_generator_button"),
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = aiBtnBg, 
                    contentColor = aiBtnText
                ),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, aiBtnBorder),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = "AI Generator", tint = aiBtnText)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Generate AI",
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Search Bar with explicit clear button
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by name, symptoms or keywords...") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("triage_search_input"),
            shape = RoundedCornerShape(14.dp),
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "SearchIcon") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = "ClearSearch")
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TealPrimary,
                unfocusedBorderColor = UnfocusedTextFieldBorder
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Dynamic Categories Horizontal List
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { category ->
                val isSelected = category == selectedCategory
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) TealPrimary else TealSecondary)
                        .clickable { selectedCategory = category }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) Color.White else SlateTextMedium
                    )
                }
            }
        }

        // Conditions Library List
        if (filteredConditions.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.SearchOff,
                        contentDescription = "No conditions",
                        modifier = Modifier.size(64.dp),
                        tint = SlateTextMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No clinical protocols found.",
                        style = MaterialTheme.typography.titleMedium,
                        color = SlateTextMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Try refining your search or create a new entry manually/using AI.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateTextMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredConditions) { condition ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.incrementTriageUsage(condition)
                                activeConditionForDetail = condition
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (condition.isFavorite) {
                                if (AppThemeManager.isDark) Color(0xFF1E1F1A) else Color(0xFFFFFDF5)
                            } else TealSurface
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (condition.isFavorite) Color(0xFFEAB308) else SlateBorderLight
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = condition.conditionName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TealTertiary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (condition.isFavorite) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            Icons.Filled.Star,
                                            contentDescription = "Pinned",
                                            tint = Color(0xFFEAB308),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                if (condition.alternativeNames.isNotBlank()) {
                                    Text(
                                        text = "Synonyms: ${condition.alternativeNames}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SlateTextMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = condition.briefDescription,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SlateTextMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text(condition.category, fontSize = 11.sp) },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = TealSecondary,
                                            labelColor = TealTertiary
                                        )
                                    )
                                    if (condition.usageCount > 0) {
                                        SuggestionChip(
                                            onClick = {},
                                            label = { Text("Used ${condition.usageCount}x", fontSize = 11.sp) },
                                            colors = SuggestionChipDefaults.suggestionChipColors(
                                                containerColor = if (AppThemeManager.isDark) Color(0x331D4ED8) else Color(0xFFEFF6FF),
                                                labelColor = if (AppThemeManager.isDark) Color(0xFF93C5FD) else Color(0xFF1D4ED8)
                                            )
                                        )
                                    }
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text(
                                        text = "Updated: ${SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(condition.lastUpdated))}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SlateTextMedium
                                    )
                                }
                            }
                            IconButton(onClick = { viewModel.toggleTriageFavorite(condition) }) {
                                Icon(
                                    imageVector = if (condition.isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                                    contentDescription = "Favorite",
                                    tint = if (condition.isFavorite) Color(0xFFEAB308) else Color(0xFF94A3B8)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // DIALOG 1: CLINICAL DETAIL VIEW WITH SCREENER COMPANION
    // ==========================================
    activeConditionForDetail?.let { condition ->
        AlertDialog(
            onDismissRequest = { activeConditionForDetail = null },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            text = {
                val questions = remember(condition.questionsJson) { parseQuestions(condition.questionsJson) }
                // Interactive checklist state for pharmacist real-time assessment
                var checkedQuestions by remember { mutableStateOf(setOf<Int>()) }
                val redFlagQuestionsChecked = remember(checkedQuestions, questions) {
                    questions.filterIndexed { index, triageQuestion ->
                        triageQuestion.isRedFlag && checkedQuestions.contains(index)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = TealSurface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = condition.conditionName,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = TealTertiary
                                    )
                                    if (condition.alternativeNames.isNotBlank()) {
                                        Text(
                                            text = "Alternative: ${condition.alternativeNames}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SlateTextMedium
                                        )
                                    }
                                }
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(condition.category, fontWeight = FontWeight.Bold) }
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = condition.briefDescription,
                                style = MaterialTheme.typography.bodyMedium,
                                color = SlateTextMedium
                            )
                        }
                    }

                    // LIVE INTERVIEW COMPANION
                    Text(
                        "Clinical Assessment Screener",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = TealTertiary
                    )

                    if (questions.isEmpty()) {
                        Text(
                            "No assessment questions set for this protocol.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SlateTextMedium
                        )
                    } else {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (redFlagQuestionsChecked.isNotEmpty()) {
                                    WarningRedContainerSoft
                                } else TealSurface
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (redFlagQuestionsChecked.isNotEmpty()) WarningRed else SlateBorderLight
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Patient Screening Companion",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (redFlagQuestionsChecked.isNotEmpty()) WarningRedTitle else TealTertiary
                                    )
                                    Text(
                                        "${checkedQuestions.size}/${questions.size} Checked",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = SlateTextMedium
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                questions.forEachIndexed { idx, q ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                checkedQuestions = if (checkedQuestions.contains(idx)) {
                                                    checkedQuestions - idx
                                                } else {
                                                    checkedQuestions + idx
                                                }
                                            }
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = checkedQuestions.contains(idx),
                                            onCheckedChange = {
                                                checkedQuestions = if (it == true) {
                                                    checkedQuestions + idx
                                                } else {
                                                    checkedQuestions - idx
                                                }
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = q.question,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = TealTertiary
                                                )
                                            }
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier.padding(top = 2.dp)
                                            ) {
                                                if (q.required) {
                                                    Surface(
                                                        color = TealSecondary,
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(
                                                            "Required",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = SlateTextMedium,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                if (q.isRedFlag) {
                                                    Surface(
                                                        color = WarningRedContainerSoft,
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(
                                                            "Red Flag / Urgent",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = WarningRed,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (idx < questions.lastIndex) {
                                        HorizontalDivider(color = SlateBorderLight)
                                    }
                                }

                                // Interactive warning trigger
                                AnimatedVisibility(
                                    visible = redFlagQuestionsChecked.isNotEmpty(),
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .padding(top = 12.dp)
                                            .fillMaxWidth()
                                            .background(WarningRedContainer, RoundedCornerShape(8.dp))
                                            .padding(12.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Filled.Warning,
                                                contentDescription = "Warning",
                                                tint = WarningRed,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                "CRITICAL REFERRAL WARNING",
                                                fontWeight = FontWeight.Bold,
                                                color = WarningRedTitle,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "Patient exhibits urgent symptoms matching: ${redFlagQuestionsChecked.map { it.question.take(25) + "..." }.joinToString(", ")}",
                                            color = WarningRedTitle,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            "Action: Initiate doctor/ER handoff immediately based on referral criteria.",
                                            color = WarningRedTitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // CLINICAL GUIDANCE SECTIONS
                    ClinicalGuidelineRow(
                        title = "Key Symptoms",
                        content = condition.keySymptoms,
                        icon = Icons.Filled.List
                    )
                    ClinicalGuidelineRow(
                        title = "Referral Criteria / Red Flags",
                        content = condition.referralCriteria,
                        icon = Icons.Filled.Emergency,
                        isWarning = true
                    )
                    ClinicalGuidelineRow(
                        title = "Severity Risk Classification",
                        content = condition.severityAssessment,
                        icon = Icons.Filled.BarChart
                    )
                    ClinicalGuidelineRow(
                        title = "Recommended OTC Options",
                        content = condition.recommendedOtcs,
                        icon = Icons.Filled.MedicalServices
                    )
                    ClinicalGuidelineRow(
                        title = "Prescription Alternatives (Reference Only)",
                        content = condition.prescriptionOptions,
                        icon = Icons.Filled.ReceiptLong
                    )
                    ClinicalGuidelineRow(
                        title = "Counseling Points",
                        content = condition.counsellingPoints,
                        icon = Icons.Filled.Forum
                    )
                    ClinicalGuidelineRow(
                        title = "Lifestyle & Non-Pharmacological Advice",
                        content = condition.lifestyleAdvice,
                        icon = Icons.Filled.Spa
                    )
                    ClinicalGuidelineRow(
                        title = "Follow-up Timeline",
                        content = condition.followUpTimeline,
                        icon = Icons.Filled.Update
                    )

                    // METADATA / VERSION CONTROL
                    Card(
                        colors = CardDefaults.cardColors(containerColor = TealSurface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Clinical Auditor Account",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SlateTextMedium
                                )
                                Text(
                                    condition.lastEditedBy,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TealTertiary
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "Last Auditor Sync",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SlateTextMedium
                                )
                                Text(
                                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(condition.lastUpdated)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TealTertiary
                                )
                            }
                        }
                    }

                    // BOTTOM ACTIONS (WhatsApp Send, Copy, Edit, Delete)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { 
                                showWhatsAppShareDialog = condition 
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = "WhatsApp", tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share Template", color = Color.White, fontSize = 13.sp)
                        }

                        IconButton(
                            onClick = {
                                val textToCopy = "Condition: ${condition.conditionName}\n" +
                                        "Assessment Screening Questions:\n" +
                                        questions.mapIndexed { idx, q -> "${idx + 1}. ${q.question} (Required: ${q.required}, RedFlag: ${q.isRedFlag})" }.joinToString("\n")
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Copied Triage Questions", textToCopy))
                                Toast.makeText(context, "Copied assessment to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(TealSecondary)
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy Questions", tint = TealPrimary)
                        }

                        IconButton(
                            onClick = {
                                editingCondition = condition
                                showCreateEditDialog = true
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(TealSecondary)
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = SlateTextMedium)
                        }

                        IconButton(
                            onClick = {
                                viewModel.deleteTriageCondition(condition)
                                activeConditionForDetail = null
                                Toast.makeText(context, "${condition.conditionName} deleted.", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(WarningRedContainerSoft)
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = WarningRed)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { activeConditionForDetail = null }) {
                    Text("Close Panel")
                }
            }
        )
    }

    // ==========================================
    // DIALOG 2: WHATSAPP SHARE CUSTOM CUSTOMER PICKER
    // ==========================================
    showWhatsAppShareDialog?.let { condition ->
        var manualPhone by remember { mutableStateOf("") }
        var manualName by remember { mutableStateOf("") }
        var selectedCustIdx by remember { mutableStateOf(-1) }
        var draftText by remember { mutableStateOf(condition.whatsappTemplate) }
        var isDropdownExpanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showWhatsAppShareDialog = null },
            title = { Text("Draft Patient Message") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Send a customized screening or advice prompt directly to the patient's phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateTextMedium
                    )

                    // Customer Selection Dropdown
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = if (selectedCustIdx != -1) customers[selectedCustIdx].name else "Select registered patient (Optional)",
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { isDropdownExpanded = !isDropdownExpanded }) {
                                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "Drop")
                                }
                            }
                        )
                        DropdownMenu(
                            expanded = isDropdownExpanded,
                            onDismissRequest = { isDropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Clear / Manual Input") },
                                onClick = {
                                    selectedCustIdx = -1
                                    manualPhone = ""
                                    manualName = ""
                                    draftText = condition.whatsappTemplate
                                    isDropdownExpanded = false
                                }
                            )
                            customers.forEachIndexed { index, customer ->
                                DropdownMenuItem(
                                        text = { Text("${customer.name} (${customer.phoneNumber})") },
                                        onClick = {
                                            selectedCustIdx = index
                                            manualPhone = customer.phoneNumber
                                            manualName = customer.name
                                            // Process template replacement
                                            draftText = condition.whatsappTemplate
                                                .replace("[Patient Name]", customer.name)
                                                .replace("{NAME}", customer.name)
                                            isDropdownExpanded = false
                                        }
                                    )
                            }
                        }
                    }

                    if (selectedCustIdx == -1) {
                        OutlinedTextField(
                            value = manualName,
                            onValueChange = {
                                manualName = it
                                draftText = condition.whatsappTemplate
                                    .replace("[Patient Name]", it)
                                    .replace("{NAME}", it)
                            },
                            label = { Text("Patient Name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = manualPhone,
                            onValueChange = { manualPhone = it },
                            label = { Text("Patient Phone Number") },
                            placeholder = { Text("e.g. +1234567890") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedTextField(
                        value = draftText,
                        onValueChange = { draftText = it },
                        label = { Text("Message Draft") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val destinationPhone = if (selectedCustIdx != -1) customers[selectedCustIdx].phoneNumber else manualPhone
                        if (destinationPhone.isBlank()) {
                            Toast.makeText(context, "Please enter or select a phone number", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val encodedMsg = Uri.encode(draftText)
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("https://api.whatsapp.com/send?phone=$destinationPhone&text=$encodedMsg")
                        }
                        try {
                            context.startActivity(intent)
                            showWhatsAppShareDialog = null
                        } catch (e: Exception) {
                            Toast.makeText(context, "WhatsApp seems missing. Draft was copied.", Toast.LENGTH_SHORT).show()
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("WhatsApp message draft", draftText))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                ) {
                    Text("Send on WhatsApp", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWhatsAppShareDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ==========================================
    // DIALOG 3: MANUAL CREATE & EDIT PROTOCOL FORM
    // ==========================================
    if (showCreateEditDialog) {
        val isEditing = editingCondition != null
        
        // Editable state values
        var formName by remember { mutableStateOf(editingCondition?.conditionName ?: "") }
        var formAltNames by remember { mutableStateOf(editingCondition?.alternativeNames ?: "") }
        var formCategory by remember { mutableStateOf(editingCondition?.category ?: "Urology") }
        var formDesc by remember { mutableStateOf(editingCondition?.briefDescription ?: "") }
        var formSymptoms by remember { mutableStateOf(editingCondition?.keySymptoms ?: "") }
        var formReferral by remember { mutableStateOf(editingCondition?.referralCriteria ?: "") }
        var formSeverity by remember { mutableStateOf(editingCondition?.severityAssessment ?: "") }
        var formOtcs by remember { mutableStateOf(editingCondition?.recommendedOtcs ?: "") }
        var formPrescription by remember { mutableStateOf(editingCondition?.prescriptionOptions ?: "") }
        var formCounselling by remember { mutableStateOf(editingCondition?.counsellingPoints ?: "") }
        var formLifestyle by remember { mutableStateOf(editingCondition?.lifestyleAdvice ?: "") }
        var formFollowUp by remember { mutableStateOf(editingCondition?.followUpTimeline ?: "") }
        var formWhatsApp by remember { mutableStateOf(editingCondition?.whatsappTemplate ?: "") }
        var formAuditor by remember { mutableStateOf("Pharmacist Sync") }

        // Structured state for Questions Editor
        var formQuestions by remember {
            mutableStateOf(
                editingCondition?.let { parseQuestions(it.questionsJson) } ?: listOf(
                    TriageQuestion("Has the patient had fever/chills?", true, true)
                )
            )
        }

        var newQuestionText by remember { mutableStateOf("") }
        var newQuestionRequired by remember { mutableStateOf(true) }
        var newQuestionRedFlag by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showCreateEditDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            title = { Text(if (isEditing) "Modify Clinical Protocol" else "Create New Clinical Protocol") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Fill core medical database fields accurately to standard diagnostic safety benchmarks.", style = MaterialTheme.typography.bodySmall, color = SlateTextMedium)

                    OutlinedTextField(
                        value = formName,
                        onValueChange = { formName = it },
                        label = { Text("Condition Name*") },
                        modifier = Modifier.fillMaxWidth().testTag("form_condition_name")
                    )

                    OutlinedTextField(
                        value = formAltNames,
                        onValueChange = { formAltNames = it },
                        label = { Text("Alternative Names / Synonyms") },
                        placeholder = { Text("e.g. Acid reflux, Heartburn") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = formCategory,
                        onValueChange = { formCategory = it },
                        label = { Text("Specialty / Category") },
                        placeholder = { Text("e.g. Urology, Dermatology, Neurology") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = formDesc,
                        onValueChange = { formDesc = it },
                        label = { Text("Brief Pathophysiology Description") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )

                    OutlinedTextField(
                        value = formSymptoms,
                        onValueChange = { formSymptoms = it },
                        label = { Text("Key Clinical Symptoms") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )

                    // -----------------------------
                    // DYNAMIC QUESTION BUILDER
                    // -----------------------------
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Dynamic Assessment Screeners Builder", style = MaterialTheme.typography.titleMedium, color = TealTertiary, fontWeight = FontWeight.Bold)
                    
                    Surface(
                        color = TealSecondary,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (formQuestions.isEmpty()) {
                                Text("No assessment questions created. Create at least one.", style = MaterialTheme.typography.bodySmall, color = SlateTextMedium)
                            } else {
                                formQuestions.forEachIndexed { index, triageQuestion ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${index + 1}. ${triageQuestion.question}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = TealTertiary
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                if (triageQuestion.required) {
                                                    Text("Required", fontSize = 10.sp, color = SlateTextMedium, fontWeight = FontWeight.Bold)
                                                }
                                                if (triageQuestion.isRedFlag) {
                                                    Text("Red Flag / Referral Trigger", fontSize = 10.sp, color = WarningRed, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                        IconButton(onClick = { formQuestions = formQuestions.filterIndexed { i, _ -> i != index } }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete Q", tint = WarningRed)
                                        }
                                    }
                                    if (index < formQuestions.lastIndex) {
                                        HorizontalDivider(color = SlateBorderLight, modifier = Modifier.padding(vertical = 4.dp))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(10.dp))

                            // Editor inputs for adding a single question
                            Text(
                                text = "Setup New Question",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = TealTertiary
                            )
                            OutlinedTextField(
                                value = newQuestionText,
                                onValueChange = { newQuestionText = it },
                                placeholder = { Text("e.g. Are you vomiting blood?") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = newQuestionRequired, onCheckedChange = { newQuestionRequired = it == true })
                                    Text(
                                        text = "Required",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SlateTextMedium
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = newQuestionRedFlag, onCheckedChange = { newQuestionRedFlag = it == true })
                                    Text("Red Flag Question", style = MaterialTheme.typography.bodySmall, color = WarningRed)
                                }
                            }
                            Button(
                                onClick = {
                                    if (newQuestionText.isNotBlank()) {
                                        formQuestions = formQuestions + TriageQuestion(
                                            question = newQuestionText.trim(),
                                            required = newQuestionRequired,
                                            isRedFlag = newQuestionRedFlag
                                        )
                                        // Reset inputs
                                        newQuestionText = ""
                                        newQuestionRequired = true
                                        newQuestionRedFlag = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TealPrimary,
                                    contentColor = if (AppThemeManager.isDark) Color(0xFF0F172A) else Color.White
                                ),
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Add", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Insert Question", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = formReferral,
                        onValueChange = { formReferral = it },
                        label = { Text("Referral Criteria / Red Flags") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )

                    OutlinedTextField(
                        value = formSeverity,
                        onValueChange = { formSeverity = it },
                        label = { Text("Severity risk grading rule book") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )

                    OutlinedTextField(
                        value = formOtcs,
                        onValueChange = { formOtcs = it },
                        label = { Text("Recommended OTC options") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )

                    OutlinedTextField(
                        value = formPrescription,
                        onValueChange = { formPrescription = it },
                        label = { Text("Prescription Alternatives (Reference Only)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )

                    OutlinedTextField(
                        value = formCounselling,
                        onValueChange = { formCounselling = it },
                        label = { Text("Counseling notes") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )

                    OutlinedTextField(
                        value = formLifestyle,
                        onValueChange = { formLifestyle = it },
                        label = { Text("Lifestyle, non-pharmacological advice") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )

                    OutlinedTextField(
                        value = formFollowUp,
                        onValueChange = { formFollowUp = it },
                        label = { Text("Follow-up schedule timeline") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = formWhatsApp,
                        onValueChange = { formWhatsApp = it },
                        label = { Text("WhatsApp Template string") },
                        placeholder = { Text("Hello [Patient Name], ...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )

                    // VERSION CONTROL AUDITOR CONTROL
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Version Audit Log Details", style = MaterialTheme.typography.titleMedium, color = TealTertiary, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = formAuditor,
                        onValueChange = { formAuditor = it },
                        label = { Text("Auditor / Pharmacist Name*") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (formName.isBlank()) {
                            Toast.makeText(context, "Condition name is required.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val formattedQuestionsStr = encodeQuestions(formQuestions)

                        val finalCondition = if (isEditing) {
                            editingCondition!!.copy(
                                conditionName = formName.trim(),
                                alternativeNames = formAltNames.trim(),
                                category = formCategory.trim(),
                                briefDescription = formDesc.trim(),
                                keySymptoms = formSymptoms.trim(),
                                questionsJson = formattedQuestionsStr,
                                referralCriteria = formReferral.trim(),
                                severityAssessment = formSeverity.trim(),
                                recommendedOtcs = formOtcs.trim(),
                                prescriptionOptions = formPrescription.trim(),
                                counsellingPoints = formCounselling.trim(),
                                lifestyleAdvice = formLifestyle.trim(),
                                followUpTimeline = formFollowUp.trim(),
                                whatsappTemplate = formWhatsApp.trim(),
                                lastEditedBy = formAuditor.trim(),
                                lastUpdated = System.currentTimeMillis()
                            )
                        } else {
                            TriageCondition(
                                conditionName = formName.trim(),
                                alternativeNames = formAltNames.trim(),
                                category = formCategory.trim(),
                                briefDescription = formDesc.trim(),
                                keySymptoms = formSymptoms.trim(),
                                questionsJson = formattedQuestionsStr,
                                referralCriteria = formReferral.trim(),
                                severityAssessment = formSeverity.trim(),
                                recommendedOtcs = formOtcs.trim(),
                                prescriptionOptions = formPrescription.trim(),
                                counsellingPoints = formCounselling.trim(),
                                lifestyleAdvice = formLifestyle.trim(),
                                followUpTimeline = formFollowUp.trim(),
                                whatsappTemplate = formWhatsApp.trim(),
                                isFavorite = false,
                                usageCount = 0,
                                lastEditedBy = formAuditor.trim(),
                                lastUpdated = System.currentTimeMillis()
                            )
                        }

                        if (isEditing) {
                            viewModel.updateTriageCondition(finalCondition)
                        } else {
                            viewModel.insertTriageCondition(finalCondition)
                        }

                        showCreateEditDialog = false
                        Toast.makeText(context, "${finalCondition.conditionName} saved to triage database.", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(if (isEditing) "Update Baseline" else "Save Protocol")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateEditDialog = false }) {
                    Text("Discard Draft")
                }
            }
        )
    }

    // ==========================================
    // DIALOG 4: GEMINI AI PROTOCOL GENERATOR
    // ==========================================
    if (showAiGeneratorDialog) {
        var aiTopic by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { 
                showAiGeneratorDialog = false
                viewModel.resetTriageAiState()
            },
            title = { Text("Generate Protocol with Gemini") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Describe any medical disease, illness, or primary care compliant conditions, and our structured AI will compile a clinical-grade protocol draft.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateTextMedium
                    )

                    OutlinedTextField(
                        value = aiTopic,
                        onValueChange = { aiTopic = it },
                        modifier = Modifier.fillMaxWidth().testTag("ai_topic_field"),
                        placeholder = { Text("e.g. Acute Otitis Media, Dyspepsia, Eczema") },
                        label = { Text("Requested Condition") },
                        singleLine = true
                    )

                    // Render dynamic generation lifecycle states
                    when (val state = triageAiState) {
                        is TriageAiState.Generating -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = TealPrimary)
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Analyzing literature & drafting checklist guidelines...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TealPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        is TriageAiState.Error -> {
                            Surface(
                                color = WarningRedContainer,
                                border = BorderStroke(1.dp, WarningRed),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = state.message,
                                    color = WarningRedTitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                        is TriageAiState.Success -> {
                            Surface(
                                color = OKGreenContainer,
                                border = BorderStroke(1.dp, OKGreen),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "Success: Created template for ${state.condition.conditionName}",
                                        fontWeight = FontWeight.Bold,
                                        color = OKGreenText,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "You can review and refine all generated details in the protocol edit dialog next, then save to make it live.",
                                        color = OKGreenText,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                        TriageAiState.Idle -> { /* Do nothing */ }
                    }
                }
            },
            confirmButton = {
                val state = triageAiState
                if (state is TriageAiState.Success) {
                    Button(
                        onClick = {
                            editingCondition = state.condition
                            showAiGeneratorDialog = false
                            showCreateEditDialog = true
                            viewModel.resetTriageAiState()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TealPrimary,
                            contentColor = if (AppThemeManager.isDark) Color(0xFF0F172A) else Color.White
                        )
                    ) {
                        Text("Review in Form & Save", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {
                            if (aiTopic.isNotBlank()) {
                                viewModel.generateTriageConditionWithAI(aiTopic.trim())
                            }
                        },
                        enabled = aiTopic.isNotBlank() && state !is TriageAiState.Generating,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TealPrimary,
                            contentColor = if (AppThemeManager.isDark) Color(0xFF0F172A) else Color.White
                        )
                    ) {
                        Text("Generate Draft", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showAiGeneratorDialog = false
                        viewModel.resetTriageAiState()
                    },
                    enabled = triageAiState !is TriageAiState.Generating
                ) {
                    Text("Close")
                }
            }
        )
    }
}

// ==========================================
// LOWER-LEVEL UI SUB-COMPONENT: GUIDELINE ROW
// ==========================================
@Composable
fun ClinicalGuidelineRow(
    title: String,
    content: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isWarning: Boolean = false
) {
    if (content.isBlank()) return

    val containerColor = if (isWarning) {
        PendingOrangeContainer
    } else {
        TealSurface
    }

    val borderColor = if (isWarning) {
        PendingOrangeBorder
    } else {
        SlateBorderLight
    }

    val iconColor = if (isWarning) {
        PendingOrange
    } else {
        TealPrimary
    }

    val titleColor = if (isWarning) {
        PendingOrange
    } else {
        TealTertiary
    }

    val contentColor = if (isWarning) {
        if (AppThemeManager.isDark) Color(0xFFFFD180) else Color(0xFF78350F)
    } else {
        SlateTextMedium
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        border = BorderStroke(
            1.dp,
            borderColor
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                lineHeight = 20.sp
            )
        }
    }
}
