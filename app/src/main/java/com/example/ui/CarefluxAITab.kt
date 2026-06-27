package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    val scope = rememberCoroutineScope()

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
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
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
                                    onCheckedChange = { viewModel.toggleOperationTask(task) }
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

