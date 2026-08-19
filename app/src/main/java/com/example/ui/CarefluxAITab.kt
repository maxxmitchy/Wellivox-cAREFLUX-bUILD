package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarefluxAITab(
    inventory: List<InventoryItem>,
    customers: List<Customer>,
    meds: List<CustomerMedication>,
    volumes: List<DailyPrescriptionVolume>,
    operationTasks: List<OperationTask>,
    viewModel: PharmacyViewModel
) {
    val aiResponse by viewModel.aiInsightsResponse.collectAsStateWithLifecycle()
    var isLoading by remember { mutableStateOf(false) }
    var showAddTaskDialog by remember { mutableStateOf(false) }
    var taskForComplianceVerification by remember { mutableStateOf<OperationTask?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val tealPrimary = Color(0xFF007A78)

    LaunchedEffect(Unit) {
        if (aiResponse == null && !isLoading) {
            isLoading = true
            val response = CarefluxAIEngine.generateInsights(viewModel.getApiKey(), inventory, customers, meds, volumes)
            viewModel.setAiInsightsResponse(response)
            isLoading = false
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddTaskDialog = true }) {
                Icon(Icons.Filled.Add, "Add Task")
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Operations & Tasks", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("System analysis engine by Wellivox", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            val response = CarefluxAIEngine.generateInsights(viewModel.getApiKey(), inventory, customers, meds, volumes, forceRefresh = true)
                            viewModel.setAiInsightsResponse(response)
                            isLoading = false
                        }
                    },
                    enabled = !isLoading
                ) {
                    Text("Refresh AI")
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                // MANUAL & PERSISTENT TASKS
                if (operationTasks.isNotEmpty()) {
                    item { SectionTitle("My Tasks", Icons.Filled.Checklist) }
                    items(operationTasks) { task ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .clickable {
                                    if (!task.isCompleted) {
                                        taskForComplianceVerification = task
                                    }
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (task.isCompleted) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = task.isCompleted,
                                    onCheckedChange = { checked ->
                                        if (checked && !task.isCompleted) {
                                            taskForComplianceVerification = task
                                        } else {
                                            viewModel.toggleOperationTask(task)
                                        }
                                    }
                                )
                                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                                    Text(
                                        text = task.title, 
                                        fontWeight = FontWeight.Bold,
                                        style = if (task.isCompleted) MaterialTheme.typography.bodyLarge.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)
                                                else MaterialTheme.typography.bodyLarge
                                    )
                                    Text(task.description, style = MaterialTheme.typography.bodyMedium)
                                    Row(modifier = Modifier.padding(top = 4.dp)) {
                                        Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) { Text(task.urgency) }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Badge(containerColor = MaterialTheme.colorScheme.tertiaryContainer) { Text(task.category) }
                                    }
                                }
                                if (!task.isCompleted) {
                                    Button(
                                        onClick = { taskForComplianceVerification = task },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = tealPrimary),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp).padding(end = 4.dp)
                                    ) {
                                        Icon(Icons.Filled.FactCheck, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Resolve", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                                IconButton(onClick = { viewModel.deleteOperationTask(task) }) {
                                    Icon(Icons.Filled.DeleteOutline, "Delete Task", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                val response = aiResponse
                if (response != null && !isLoading) {
                    item { SectionTitle("AI Active Insights", Icons.Filled.AutoAwesome) }
                    
                    if (response.highPriorityTasks.isNotEmpty()) {
                        item { Text("High Priority", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp)) }
                        items(response.highPriorityTasks) { item ->
                            AITaskCard(item.title, item.description, item.urgency, onAdd = {
                                viewModel.addOperationTask(item.title, item.description, item.urgency, "AI Priority")
                            })
                        }
                    }

                    if (response.inventoryAlerts.isNotEmpty()) {
                        item { Text("Inventory", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp)) }
                        items(response.inventoryAlerts) { item ->
                            AITaskCard(item.title, item.description, item.urgency, onAdd = {
                                viewModel.addOperationTask(item.title, item.description, item.urgency, "AI Inventory")
                            })
                        }
                    }

                    if (response.patientFollowUps.isNotEmpty()) {
                        item { Text("Patient Follow-Ups", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp)) }
                        items(response.patientFollowUps) { item ->
                            AITaskCard(item.title, item.description, item.urgency, onAdd = {
                                viewModel.addOperationTask(item.title, item.description, item.urgency, "Patient Care")
                            })
                        }
                    }

                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }

    val currentVerifyTask = taskForComplianceVerification
    if (currentVerifyTask != null) {
        val task = currentVerifyTask
        var complianceNotes by remember(task.id) { mutableStateOf("") }
        var selectedChannel by remember(task.id) { mutableStateOf("Phone Call") }
        var linkedCustomerName by remember(task.id) { mutableStateOf("") }
        var isSavingCompliance by remember(task.id) { mutableStateOf(false) }
        var auditCountedQuantityString by remember(task.id) { mutableStateOf("") }
        var showCustomNoteInput by remember(task.id) { mutableStateOf(false) }

        val isStockTransfer = task.category == "Stock Transfer"
        val isExpiryTask = task.title.contains("Expiry", ignoreCase = true) || task.category.contains("Expiry", ignoreCase = true)
        val isInventoryTask = !isExpiryTask && (task.title.contains("Inventory", ignoreCase = true) || task.title.contains("Reconcile", ignoreCase = true) || task.title.contains("Stock", ignoreCase = true) || task.category.contains("Inventory", ignoreCase = true))
        val isPatientTask = !isStockTransfer && !isExpiryTask && !isInventoryTask && (task.title.contains("Refill", ignoreCase = true) || task.title.contains("Patient", ignoreCase = true) || task.category.contains("Patient", ignoreCase = true))

        val presetVerificationNotes = when {
            isStockTransfer -> listOf(
                "Received intact, count verified, batch logged in shelf.",
                "Stock transfer verified and accepted.",
                "Stock verified with minor packaging damage, items intact."
            )
            isExpiryTask || isInventoryTask -> listOf(
                "Physical count verified with shelf stock.",
                "Count verified. Applied FEFO sticker for quick depletion.",
                "Stock counted & verified intact.",
                "Discrepancy resolved after shelf recount."
            )
            isPatientTask -> listOf(
                "Contacted patient via phone, refill confirmed.",
                "Patient contacted via WhatsApp, appointment scheduled.",
                "Patient confirmed prescription pick-up for tomorrow."
            )
            else -> listOf(
                "Task completed according to branch guidelines.",
                "Operational check passed and verified.",
                "Maintenance and inspection completed."
            )
        }

        val auditActionChannel = if (isExpiryTask) "Expiry Audit" else "Shelf Audit"

        AlertDialog(
            onDismissRequest = { if (!isSavingCompliance) taskForComplianceVerification = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FactCheck,
                        contentDescription = null,
                        tint = tealPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Task Compliance Verification",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 4.dp)
                ) {
                    if (isStockTransfer) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)),
                                border = BorderStroke(1.dp, tealPrimary.copy(alpha = 0.2f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(text = "INTER-BRANCH STOCK RECEIPT", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = tealPrimary)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(text = task.title, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(text = task.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Select Verification Note Preset", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    TextButton(
                                        onClick = { showCustomNoteInput = !showCustomNoteInput },
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.EditNote, contentDescription = null, tint = tealPrimary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (showCustomNoteInput) "Hide Text Area" else "Type / Edit Note", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tealPrimary)
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    presetVerificationNotes.forEach { preset ->
                                        val isSelected = complianceNotes == preset
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { complianceNotes = preset },
                                            label = { Text(preset, fontSize = 10.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = tealPrimary.copy(alpha = 0.2f),
                                                selectedLabelColor = tealPrimary,
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                labelColor = MaterialTheme.colorScheme.onSurface
                                            )
                                        )
                                    }
                                }

                                if (showCustomNoteInput || complianceNotes.isBlank() || presetVerificationNotes.none { it == complianceNotes }) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = complianceNotes,
                                        onValueChange = { complianceNotes = it },
                                        label = { Text("Verification / Condition Notes", fontSize = 12.sp) },
                                        placeholder = { Text("Type custom verification notes...") },
                                        minLines = 2,
                                        maxLines = 4,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = tealPrimary,
                                            focusedLabelColor = tealPrimary
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    } else if (isExpiryTask || isInventoryTask) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)),
                                border = BorderStroke(1.dp, tealPrimary.copy(alpha = 0.2f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(text = if (isExpiryTask) "EXPIRY AUDIT NODE" else "INVENTORY RECONCILIATION NODE", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = tealPrimary)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(text = task.title, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(text = task.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            // Counted Physical Quantity Input Field
                            OutlinedTextField(
                                value = auditCountedQuantityString,
                                onValueChange = { auditCountedQuantityString = it.filter { c -> c.isDigit() } },
                                label = { Text("Counted Physical Stock Quantity (Units) *", fontSize = 12.sp) },
                                placeholder = { Text("e.g. 45") },
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = tealPrimary,
                                    focusedLabelColor = tealPrimary,
                                    unfocusedBorderColor = if (auditCountedQuantityString.isBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                                ),
                                modifier = Modifier.fillMaxWidth().testTag("audit_counted_qty_input")
                            )
                            if (auditCountedQuantityString.isNotBlank()) {
                                Text(
                                    text = "⚡ Entered count '${auditCountedQuantityString.trim()}' will automatically update inventory stock upon verification.",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = tealPrimary
                                )
                            } else {
                                Text(
                                    text = "⚠️ Counted physical stock quantity is required to resolve this audit task.",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            // Presets
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Select Audit Note Preset", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    TextButton(
                                        onClick = { showCustomNoteInput = !showCustomNoteInput },
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.EditNote, contentDescription = null, tint = tealPrimary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (showCustomNoteInput) "Hide Text Area" else "Type / Edit Note", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tealPrimary)
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    presetVerificationNotes.forEach { preset ->
                                        val isSelected = complianceNotes == preset
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { complianceNotes = preset },
                                            label = { Text(preset, fontSize = 10.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = tealPrimary.copy(alpha = 0.2f),
                                                selectedLabelColor = tealPrimary,
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                labelColor = MaterialTheme.colorScheme.onSurface
                                            )
                                        )
                                    }
                                }

                                if (showCustomNoteInput || complianceNotes.isBlank() || presetVerificationNotes.none { it == complianceNotes }) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = complianceNotes,
                                        onValueChange = { complianceNotes = it },
                                        label = { Text("Audit Resolution Notes", fontSize = 12.sp) },
                                        placeholder = { Text("e.g. Physical count confirmed...") },
                                        minLines = 2,
                                        maxLines = 4,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = tealPrimary,
                                            focusedLabelColor = tealPrimary
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    } else if (isPatientTask) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = linkedCustomerName,
                                onValueChange = { linkedCustomerName = it },
                                label = { Text("Patient / Customer Full Name *", fontSize = 12.sp) },
                                placeholder = { Text("e.g. Eleanor Vance") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Select Outreach Preset Note", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    TextButton(
                                        onClick = { showCustomNoteInput = !showCustomNoteInput },
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.EditNote, contentDescription = null, tint = tealPrimary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (showCustomNoteInput) "Hide Text Area" else "Type / Edit Note", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tealPrimary)
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    presetVerificationNotes.forEach { preset ->
                                        val isSelected = complianceNotes == preset
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { complianceNotes = preset },
                                            label = { Text(preset, fontSize = 10.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = tealPrimary.copy(alpha = 0.2f),
                                                selectedLabelColor = tealPrimary,
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                labelColor = MaterialTheme.colorScheme.onSurface
                                            )
                                        )
                                    }
                                }

                                if (showCustomNoteInput || complianceNotes.isBlank() || presetVerificationNotes.none { it == complianceNotes }) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = complianceNotes,
                                        onValueChange = { complianceNotes = it },
                                        label = { Text("Clinical Outreach Notes", fontSize = 12.sp) },
                                        placeholder = { Text("e.g. Contacted patient via WhatsApp...") },
                                        minLines = 2,
                                        maxLines = 4,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = tealPrimary,
                                            focusedLabelColor = tealPrimary
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Select Completion Preset Note", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    TextButton(
                                        onClick = { showCustomNoteInput = !showCustomNoteInput },
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.EditNote, contentDescription = null, tint = tealPrimary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (showCustomNoteInput) "Hide Text Area" else "Type / Edit Note", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tealPrimary)
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    presetVerificationNotes.forEach { preset ->
                                        val isSelected = complianceNotes == preset
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { complianceNotes = preset },
                                            label = { Text(preset, fontSize = 10.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = tealPrimary.copy(alpha = 0.2f),
                                                selectedLabelColor = tealPrimary,
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                labelColor = MaterialTheme.colorScheme.onSurface
                                            )
                                        )
                                    }
                                }

                                if (showCustomNoteInput || complianceNotes.isBlank() || presetVerificationNotes.none { it == complianceNotes }) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = complianceNotes,
                                        onValueChange = { complianceNotes = it },
                                        label = { Text("Completion & Resolution Notes", fontSize = 12.sp) },
                                        placeholder = { Text("Provide details on how task was completed...") },
                                        minLines = 2,
                                        maxLines = 4,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = tealPrimary,
                                            focusedLabelColor = tealPrimary
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanNotes = complianceNotes.trim()
                        if (isStockTransfer) {
                            if (cleanNotes.length < 5) {
                                Toast.makeText(context, "Verification notes must be at least 5 characters long", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            taskForComplianceVerification = null
                            Toast.makeText(context, "Stock transfer verified and received.", Toast.LENGTH_SHORT).show()
                            viewModel.verifyAndReceiveStockTransfer(task, cleanNotes) { _, _ -> }
                        } else if (isExpiryTask || isInventoryTask) {
                            val countedQty = auditCountedQuantityString.trim().toIntOrNull()
                            if (countedQty == null) {
                                Toast.makeText(context, "Please enter the counted physical stock quantity to resolve this audit task", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (cleanNotes.length < 5) {
                                Toast.makeText(context, "Resolution notes must be at least 5 characters long", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            taskForComplianceVerification = null
                            Toast.makeText(context, "Task completed and inventory reconciled.", Toast.LENGTH_SHORT).show()
                            viewModel.verifiablyCompleteOperationTask(
                                task = task,
                                notes = cleanNotes,
                                channel = auditActionChannel,
                                patientName = "Internal Stock Audit",
                                countedQuantity = countedQty
                            ) { _, _ -> }
                        } else if (isPatientTask) {
                            val cleanPatient = linkedCustomerName.trim()
                            if (cleanPatient.isEmpty()) {
                                Toast.makeText(context, "Patient Name is required for follow-up audit", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (cleanNotes.length < 8) {
                                Toast.makeText(context, "Outreach notes must be at least 8 characters long", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            taskForComplianceVerification = null
                            Toast.makeText(context, "Clinical outreach logged and task completed.", Toast.LENGTH_SHORT).show()
                            viewModel.verifiablyCompleteOperationTask(
                                task = task,
                                notes = cleanNotes,
                                channel = selectedChannel,
                                patientName = cleanPatient
                            ) { _, _ -> }
                        } else {
                            if (cleanNotes.length < 5) {
                                Toast.makeText(context, "Completion notes must be at least 5 characters long", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            taskForComplianceVerification = null
                            Toast.makeText(context, "Task completed.", Toast.LENGTH_SHORT).show()
                            viewModel.verifiablyCompleteOperationTask(
                                task = task,
                                notes = cleanNotes,
                                channel = "System/Other",
                                patientName = "N/A - General Ops"
                            ) { _, _ -> }
                        }
                    },
                    enabled = (
                        (isStockTransfer && complianceNotes.trim().length >= 5) ||
                        ((isExpiryTask || isInventoryTask) && auditCountedQuantityString.trim().toIntOrNull() != null && complianceNotes.trim().length >= 5) ||
                        (isPatientTask && linkedCustomerName.trim().isNotEmpty() && complianceNotes.trim().length >= 8) ||
                        (!isStockTransfer && !isExpiryTask && !isInventoryTask && !isPatientTask && complianceNotes.trim().length >= 5)
                    ),
                    colors = ButtonDefaults.buttonColors(containerColor = tealPrimary)
                ) {
                    Text("Confirm & Resolve Audit", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { taskForComplianceVerification = null },
                    enabled = !isSavingCompliance
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddTaskDialog) {
        ManualTaskDialog(
            onDismiss = { showAddTaskDialog = false },
            onConfirm = { title, desc, urgency, cat ->
                viewModel.addOperationTask(title, desc, urgency, cat)
                showAddTaskDialog = false
            }
        )
    }
}

@Composable
fun AITaskCard(title: String, desc: String, urgency: String, onAdd: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (urgency.equals("High", true)) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(desc, style = MaterialTheme.typography.bodyMedium)
                Text("Urgency: $urgency", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
            }
            IconButton(onClick = onAdd) {
                Icon(Icons.Filled.AddTask, "Add to My Tasks")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualTaskDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var urgency by remember { mutableStateOf("Medium") }
    var category by remember { mutableStateOf("Manual") }

    var showDiscardConfirm by remember { mutableStateOf(false) }

    val isFormDirty = title.isNotBlank() || desc.isNotBlank()

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = "Unsaved Changes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "You have unsaved details in this form. Are you sure you want to discard your progress?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardConfirm = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Discard Details")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) {
                    Text("Keep Editing")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = { if (isFormDirty) showDiscardConfirm = true else onDismiss() },
        title = { Text("Add Custom Task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Task Title") }, singleLine = true)
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, maxLines = 3)
                
                Text("Urgency", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("High", "Medium", "Low").forEach { level ->
                        FilterChip(
                            selected = urgency == level,
                            onClick = { urgency = level },
                            label = { Text(level) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title, desc, urgency, category) },
                enabled = title.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = { if (isFormDirty) showDiscardConfirm = true else onDismiss() }) { Text("Cancel") }
        }
    )
}

@Composable
fun SectionTitle(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

