@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Customer
import com.example.data.CustomerMedication
import com.example.data.InventoryItem
import com.example.data.ClinicalIntervention
import com.example.ui.theme.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.PharmacyViewModel
import android.app.DatePickerDialog
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CustomersTabContent(
    customers: List<Customer>,
    customerMeds: List<CustomerMedication>,
    inventoryMeds: List<InventoryItem>,
    clinicalInterventions: List<ClinicalIntervention>,
    targetCustomerQuery: String? = null,
    initialSubTab: String? = null,
    onAddNewCustomerClick: () -> Unit,
    onEditCustomerClick: (Customer) -> Unit,
    onDeleteCustomer: (Customer) -> Unit,
    onAddPrescriptionClick: (Customer) -> Unit,
    onDeletePrescription: (CustomerMedication) -> Unit,
    onAddInterventionClick: (Customer) -> Unit,
    viewModel: PharmacyViewModel,
    context: Context
) {
    var searchQuery by remember { mutableStateOf(targetCustomerQuery ?: "") }
    val defaultSubTab = when (initialSubTab) {
        "refill_reminders", "refill", "refills", "reminders", "refill_queue", "ops_task_board" -> 1
        "interventions", "clinical", "clinical_followups", "followups" -> 2
        else -> 0
    }
    var activeSubTab by remember { mutableStateOf(defaultSubTab) } // 0 = Patient Ledger, 1 = Refill Command Center, 2 = Clinical Follow-ups
    var customerToDelete by remember { mutableStateOf<Customer?>(null) }
    val customTemplates by viewModel.customTemplates.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(initialSubTab) {
        if (!initialSubTab.isNullOrBlank()) {
            when (initialSubTab) {
                "refill_reminders", "refill", "refills", "reminders", "refill_queue", "ops_task_board" -> activeSubTab = 1
                "interventions", "clinical", "clinical_followups", "followups" -> activeSubTab = 2
            }
        }
    }

    LaunchedEffect(targetCustomerQuery, customers) {
        if (!targetCustomerQuery.isNullOrBlank()) {
            val matchedCustomer = customers.find { 
                it.id.toString() == targetCustomerQuery ||
                it.name.equals(targetCustomerQuery, ignoreCase = true) ||
                it.name.contains(targetCustomerQuery, ignoreCase = true) ||
                it.phoneNumber.contains(targetCustomerQuery)
            }
            if (matchedCustomer != null) {
                searchQuery = matchedCustomer.name
            } else {
                searchQuery = targetCustomerQuery
            }
            activeSubTab = 0
        }
    }
    
    if (customerToDelete != null) {
        AlertDialog(
            onDismissRequest = { customerToDelete = null },
            title = { Text("Delete Customer") },
            text = { Text("Are you sure you want to delete ${customerToDelete?.name}? This action cannot be undone and will also remove their prescriptions.") },
            confirmButton = {
                TextButton(onClick = {
                    customerToDelete?.let { onDeleteCustomer(it) }
                    customerToDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { customerToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    val filteredCustomers = if (searchQuery.isBlank()) {
        customers
    } else {
        customers.filter { customer -> 
            val inName = customer.name.contains(searchQuery, ignoreCase = true)
            val inPhone = customer.phoneNumber.contains(searchQuery)
            val inNotes = customer.notes.contains(searchQuery, ignoreCase = true)
            val inMeds = customerMeds.any { it.customerId == customer.id && it.medicationName.contains(searchQuery, ignoreCase = true) }
            
            inName || inPhone || inNotes || inMeds
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp)
            ) {
                Text(
                    text = "Customer Ledger",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Manage patients and WhatsApp refills.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateTextMedium
                )
            }

            Button(
                onClick = onAddNewCustomerClick,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.align(Alignment.CenterVertically)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Patient", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Professional Pill Tab Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = { activeSubTab = 0 },
                label = { Text("Patient Roster (${customers.size})", fontSize = 12.sp) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (activeSubTab == 0) TealSurface else Color.Transparent,
                    labelColor = if (activeSubTab == 0) TealPrimary else SlateTextMedium
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (activeSubTab == 0) TealPrimary else SlateBorderLight.copy(alpha = 0.5f)
                )
            )
            AssistChip(
                onClick = { activeSubTab = 1 },
                label = {
                    val pendingCount = customerMeds.count { it.cycleDays > 0 && it.nextRefillDate <= System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L }
                    Text("Refill Command Center ($pendingCount)", fontSize = 12.sp)
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (activeSubTab == 1) TealSurface else Color.Transparent,
                    labelColor = if (activeSubTab == 1) TealPrimary else SlateTextMedium
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (activeSubTab == 1) TealPrimary else SlateBorderLight.copy(alpha = 0.5f)
                )
            )
            AssistChip(
                onClick = { activeSubTab = 2 },
                label = {
                    val followUpsCount = clinicalInterventions.count { it.currentStatus == "Pending" }
                    Text("Clinical Follow-ups ($followUpsCount)", fontSize = 12.sp)
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (activeSubTab == 2) TealSurface else Color.Transparent,
                    labelColor = if (activeSubTab == 2) TealPrimary else SlateTextMedium
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (activeSubTab == 2) TealPrimary else SlateBorderLight.copy(alpha = 0.5f)
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (activeSubTab == 0) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                placeholder = { Text("Search by name or phone...", color = SlateTextMedium, fontSize = 14.sp) },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = "Search", tint = SlateTextMedium, modifier = Modifier.size(20.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = SlateTextMedium, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = UnfocusedTextFieldBorder,
                    focusedBorderColor = TealPrimary,
                    unfocusedContainerColor = SlateBackgroundLight,
                    focusedContainerColor = TealSurface
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

        LaunchedEffect(searchQuery) {
            val cleanPhone = searchQuery.trim().replace("[^0-9]".toRegex(), "")
            if (cleanPhone.length >= 10) {
                viewModel.checkAndAutoSyncExternalPatientByPhone(searchQuery)
            }
        }

        if (filteredCustomers.isEmpty()) {
            EmptyCustomerPlaceholder()
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                items(filteredCustomers) { customer ->
                    val meds = customerMeds.filter { it.customerId == customer.id }
                    val interventions = clinicalInterventions.filter { it.customerId == customer.id }
                    CustomerCard(
                        customer = customer,
                        medications = meds,
                        inventoryMeds = inventoryMeds,
                        interventions = interventions,
                        onEditClick = { onEditCustomerClick(customer) },
                        onDeleteClick = { customerToDelete = customer },
                        onAddMedClick = { onAddPrescriptionClick(customer) },
                        onDelMedClick = { onDeletePrescription(it) },
                        onAddInterventionClick = { onAddInterventionClick(customer) },
                        viewModel = viewModel,
                        context = context
                    )
                }
            }
        }
    } else if (activeSubTab == 1) {
        RefillCommandCenter(
            customers = customers,
            customerMeds = customerMeds,
            viewModel = viewModel,
            context = context
        )
    } else {
        ClinicalFollowUpsQueue(
            customers = customers,
            clinicalInterventions = clinicalInterventions,
            viewModel = viewModel,
            context = context
        )
    }
}
}

@Composable
fun CustomerCard(
    customer: Customer,
    medications: List<CustomerMedication>,
    inventoryMeds: List<InventoryItem>,
    interventions: List<ClinicalIntervention>,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onAddMedClick: () -> Unit,
    onDelMedClick: (CustomerMedication) -> Unit,
    onAddInterventionClick: () -> Unit,
    viewModel: PharmacyViewModel,
    context: Context
) {
    var expanded by remember { mutableStateOf(false) }
    var showClinicalWorkspace by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var medToPushRefill by remember { mutableStateOf<CustomerMedication?>(null) }
    var pushDaysText by remember { mutableStateOf("") }
    val customTemplates by viewModel.customTemplates.collectAsStateWithLifecycle()
    var showSendMessageDialog by remember { mutableStateOf(false) }
    var showHistoryPdfDialog by remember { mutableStateOf(false) }

    val customerAlertsList by viewModel.customerAlerts.collectAsStateWithLifecycle()
    val hasStockAlert = remember(customerAlertsList, customer) {
        customerAlertsList.any { 
            it.customerName.equals(customer.name, ignoreCase = true) && 
            it.status == "Pending" && 
            it.alertType == "Stock Shortage Warning" 
        }
    }
    val hasRadarAlert = remember(customerAlertsList, customer) {
        customerAlertsList.any { 
            it.customerName.equals(customer.name, ignoreCase = true) && 
            it.status == "Pending" && 
            it.alertType == "Silent Radar" 
        }
    }

    val customerDdiAlerts = remember(medications, customer) {
        val names = medications.map { it.medicationName }
        com.example.data.ClinicalDdiEngine.checkInteractions(names, customer)
    }

    if (medToPushRefill != null) {
        AlertDialog(
            onDismissRequest = { medToPushRefill = null },
            title = { Text("Push Refill Date") },
            text = {
                Column {
                    Text("Enter number of days to push the refill date by:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pushDaysText,
                        onValueChange = { pushDaysText = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val days = pushDaysText.toLongOrNull()
                    if (days != null && days > 0) {
                        val currentDue = medToPushRefill!!.nextRefillDate
                        val msPerDay = 1000L * 60 * 60 * 24
                        val newDue = currentDue + (days * msPerDay)
                        viewModel.updateCustomerMedication(medToPushRefill!!.copy(nextRefillDate = newDue))
                        Toast.makeText(context, "Refill date pushed by $days days", Toast.LENGTH_SHORT).show()
                    }
                    medToPushRefill = null
                }) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { medToPushRefill = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSendMessageDialog) {
        SendCustomerMessageDialog(
            customer = customer,
            medications = medications,
            viewModel = viewModel,
            onDismiss = { showSendMessageDialog = false },
            context = context
        )
    }

    if (showHistoryPdfDialog) {
        PatientTreatmentHistoryDialog(
            customer = customer,
            medications = medications,
            context = context,
            onDismiss = { showHistoryPdfDialog = false }
        )
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = TealSurface),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = SlateBorderLight,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { 
                keyboardController?.hide()
                focusManager.clearFocus()
                expanded = !expanded 
            }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(TealSecondary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = "Customer",
                        tint = TealPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = customer.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TealTertiary,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (customerDdiAlerts.isNotEmpty()) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                                modifier = Modifier.padding(start = 4.dp)
                            ) {
                                Text(
                                    "🚨 CLASH",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        if (customer.phoneNumber.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Filled.Phone, contentDescription = "Phone", modifier = Modifier.size(12.dp), tint = SlateTextMedium)
                                Text(customer.phoneNumber, style = MaterialTheme.typography.labelSmall, color = SlateTextMedium)
                            }
                        }
                        if (customer.email.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Filled.Email, contentDescription = "Email", modifier = Modifier.size(12.dp), tint = SlateTextMedium)
                                Text(customer.email, style = MaterialTheme.typography.labelSmall, color = SlateTextMedium)
                            }
                        }
                    }
                    if (customer.notes.isNotEmpty()) {
                        val notesTrim = customer.notes.trim()
                        val isJson = notesTrim.startsWith("{") && notesTrim.endsWith("}")
                        val displayText = if (isJson) {
                            try {
                                val intelligence = PatientIntelligenceParser.parse(customer, medications)
                                intelligence.aiSummary.ifBlank { "Clinical Intelligence Profile Active" }
                            } catch (e: Exception) {
                                "Clinical Intelligence Profile Active"
                            }
                        } else {
                            customer.notes
                        }
                        Text(
                            text = displayText,
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateTextMedium,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // NDPA Shield Badge (Positioned cleanly among status chips to avoid squeezing patient name)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                                .border(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Security,
                                    contentDescription = "NDPA Compliant",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = "NDPA Compliant",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        // Loyalty badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFFFD700).copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Icon(Icons.Filled.Stars, contentDescription = "Loyalty", tint = Color(0xffff8c00), modifier = Modifier.size(12.dp))
                                Text(
                                    text = "${customer.loyaltyPoints} Pts", 
                                    style = MaterialTheme.typography.labelSmall, 
                                    fontWeight = FontWeight.Bold, 
                                    color = Color(0xffb26a00),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                        // Streak badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Red.copy(alpha = 0.08f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Icon(Icons.Filled.LocalFireDepartment, contentDescription = "Streak", tint = Color.Red, modifier = Modifier.size(12.dp))
                                Text(
                                    text = "${customer.refillStreak} Streak", 
                                    style = MaterialTheme.typography.labelSmall, 
                                    fontWeight = FontWeight.Bold, 
                                    color = Color.Red,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        if (hasStockAlert) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Icon(Icons.Filled.Error, contentDescription = "Stock Alert", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(12.dp))
                                    Text(
                                        text = "Stock Low", 
                                        style = MaterialTheme.typography.labelSmall, 
                                        fontWeight = FontWeight.Bold, 
                                        color = MaterialTheme.colorScheme.error,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        if (hasRadarAlert) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.04f))
                                    .border(
                                        BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Icon(
                                        imageVector = Icons.Filled.Info, 
                                        contentDescription = "Radar Alert", 
                                        tint = MaterialTheme.colorScheme.tertiary, 
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = "Clinical Recheck Flag", 
                                        style = MaterialTheme.typography.labelSmall, 
                                        fontWeight = FontWeight.Bold, 
                                        color = MaterialTheme.colorScheme.tertiary,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showHistoryPdfDialog = true },
                        modifier = Modifier.size(32.dp).testTag("patient_history_pdf_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PictureAsPdf,
                            contentDescription = "Export Treatment History PDF",
                            tint = TealPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box {
                        var showGeneralWhatsAppMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { showGeneralWhatsAppMenu = true }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Message,
                            contentDescription = "WhatsApp Customer",
                            tint = OKGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showGeneralWhatsAppMenu,
                        onDismissRequest = { showGeneralWhatsAppMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("💬 Customize & Send Template...") },
                            onClick = {
                                showGeneralWhatsAppMenu = false
                                showSendMessageDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("General Check-in (WhatsApp)") },
                            onClick = {
                                showGeneralWhatsAppMenu = false
                                sendWhatsAppTemplate(context, customer, "", "General Check-in", "Hi ${customer.name}, just checking in to see how you are doing! Let us know if you need any assistance.")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Twilio Welfare Follow-up (WhatsApp/SMS)") },
                            onClick = {
                                showGeneralWhatsAppMenu = false
                                coroutineScope.launch {
                                    val result = viewModel.sendTwilioWelfareCheck(
                                        patientName = customer.name,
                                        phone = customer.phoneNumber,
                                        wellnessQuestion = "We hope you are recovering well. Let us know if you need any support!"
                                    )
                                    when (result) {
                                        is com.example.util.TwilioMessagingManager.DispatchResult.Success -> {
                                            Toast.makeText(context, "Twilio check sent via ${result.channel} (SID: ${result.sid})", Toast.LENGTH_SHORT).show()
                                        }
                                        is com.example.util.TwilioMessagingManager.DispatchResult.Blocked -> {
                                            Toast.makeText(context, result.reason, Toast.LENGTH_LONG).show()
                                        }
                                        is com.example.util.TwilioMessagingManager.DispatchResult.Failed -> {
                                            Toast.makeText(context, "Twilio dispatch failed: ${result.error}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Special Promo (WhatsApp)") },
                            onClick = {
                                showGeneralWhatsAppMenu = false
                                sendWhatsAppTemplate(context, customer, "", "Promo", "Hello ${customer.name}! We have some special offers happening this week at Careflux Pharmacy. Stop by to check them out!")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Twilio Multi-Channel Promo") },
                            onClick = {
                                showGeneralWhatsAppMenu = false
                                coroutineScope.launch {
                                    val result = viewModel.sendTwilioMessage(
                                        phone = customer.phoneNumber,
                                        messageContent = "CareFlux Promo Notice:\nHello ${customer.name}, special wellness discounts are happening this week at CareFlux Pharmacy! Visit us for up to 15% off prescriptions.",
                                        messageType = "Promo",
                                        medicationIdOrKey = "promo_${System.currentTimeMillis()}"
                                    )
                                    when (result) {
                                        is com.example.util.TwilioMessagingManager.DispatchResult.Success -> {
                                            Toast.makeText(context, "Promo dispatched via ${result.channel}!", Toast.LENGTH_SHORT).show()
                                        }
                                        is com.example.util.TwilioMessagingManager.DispatchResult.Blocked -> {
                                            Toast.makeText(context, result.reason, Toast.LENGTH_LONG).show()
                                        }
                                        is com.example.util.TwilioMessagingManager.DispatchResult.Failed -> {
                                            Toast.makeText(context, "Twilio promo failed: ${result.error}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        )
                        val customTemplates by viewModel.customTemplates.collectAsStateWithLifecycle()
                        if (customTemplates.isNotEmpty()) {
                            Divider()
                            customTemplates.forEach { template ->
                                DropdownMenuItem(
                                    text = { Text(template.title) },
                                    onClick = {
                                        showGeneralWhatsAppMenu = false
                                        val parsedMsg = template.message
                                            .replace("{NAME}", customer.name)
                                            .replace("{MED}", "medication")
                                        sendWhatsAppTemplate(context, customer, "", template.title, parsedMsg)
                                    }
                                )
                            }
                        }
                    }
                }
            }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = SlateBorderLight)
                Spacer(modifier = Modifier.height(12.dp))

                // Demographic & NDPA Consent Section
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, SlateBorderLight),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(16.dp))
                            Text("Demographics & NDPA Consent Logs", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = TealTertiary)
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Row 1: Demographics
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Demographics", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = SlateTextMedium)
                                Text("Age: ${customer.age} | Gender: ${customer.gender}", fontSize = 10.sp, color = TealTertiary)
                                Text("Location: ${customer.city}, ${customer.lga}, ${customer.state} State", fontSize = 10.sp, color = TealTertiary)
                            }
                            
                            // Last Updated Stamp
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Consent Updated", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = SlateTextMedium)
                                val sdf = java.text.SimpleDateFormat("dd MMM yyyy HH:mm", java.util.Locale.getDefault())
                                Text(sdf.format(java.util.Date(customer.consentLastUpdated)), fontSize = 10.sp, color = TealTertiary)
                                Text("Via: ${customer.consentChannel}", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = SlateTextMedium)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = SlateBorderLight.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Consent Grid (Section 26 compliance audit trail)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Local Processing
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(
                                        imageVector = if (customer.consentPrescriptionTracking) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                                        contentDescription = null,
                                        tint = if (customer.consentPrescriptionTracking) OKGreen else Color.Red,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text("Clinical Presc.", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TealTertiary)
                                }
                                Text("Local database interactions", fontSize = 8.sp, color = SlateTextMedium, lineHeight = 10.sp)
                            }
                            
                            // SMS Reminders
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(
                                        imageVector = if (customer.consentSmsRefills) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                                        contentDescription = null,
                                        tint = if (customer.consentSmsRefills) OKGreen else Color.Red,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text("SMS Alerts", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TealTertiary)
                                }
                                Text("WhatsApp/SMS refill reminders", fontSize = 8.sp, color = SlateTextMedium, lineHeight = 10.sp)
                            }
                            
                            // Multi-node sync
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(
                                        imageVector = if (customer.consentCloudSync) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                                        contentDescription = null,
                                        tint = if (customer.consentCloudSync) OKGreen else Color.Red,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text("Global Sync", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TealTertiary)
                                }
                                Text("Shared clinic network syncing", fontSize = 8.sp, color = SlateTextMedium, lineHeight = 10.sp)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.ReceiptLong,
                            contentDescription = null,
                            tint = TealTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Active Prescriptions",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = TealTertiary
                        )
                    }
                    AssistChip(
                        onClick = onAddMedClick,
                        label = { Text("Add Med", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) },
                        leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = TealPrimary.copy(alpha = 0.08f),
                            labelColor = TealPrimary,
                            leadingIconContentColor = TealPrimary
                        ),
                        border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (customerDdiAlerts.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(12.dp))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Dangerous,
                                    contentDescription = "Clinical Interaction Risk Alert",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Clinical Interaction Warning",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            customerDdiAlerts.forEach { alert ->
                                Text(
                                    text = alert,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    lineHeight = 16.sp,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                if (medications.isEmpty()) {
                    Text(
                        text = "No medications mapped.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateTextMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    val sdf = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        medications.forEach { med ->
                            // Check stock
                            val stockItem = inventoryMeds.find { it.id == med.inventoryItemId }
                            val stockOk = (stockItem?.stockQuantity ?: 0) >= 1 // minimal check

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(1.dp, SlateBorderLight)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    // Header Segment
                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(TealPrimary.copy(alpha = 0.08f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.MedicalServices,
                                                contentDescription = null,
                                                tint = TealPrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = med.medicationName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = TealTertiary,
                                                lineHeight = 18.sp
                                            )
                                            if (med.customDosage.isNotEmpty()) {
                                                Text(
                                                    text = med.customDosage,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = SlateTextMedium,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = { onDelMedClick(med) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Close,
                                                contentDescription = "Remove Medication",
                                                tint = SlateTextMedium.copy(alpha = 0.7f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Details Row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "Cost: ",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SlateTextMedium
                                            )
                                            Text(
                                                text = "₦%,.2f".format(med.cost),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = TealPrimary
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(TealPrimary.copy(alpha = 0.08f))
                                                .border(0.5.dp, TealPrimary.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                                                .clickable {
                                                    val cal = Calendar.getInstance().apply {
                                                        timeInMillis = if (med.nextRefillDate > 0L) med.nextRefillDate else System.currentTimeMillis()
                                                    }
                                                    DatePickerDialog(
                                                        context,
                                                        { _, year, month, dayOfMonth ->
                                                            val selectedCal = Calendar.getInstance().apply {
                                                                set(Calendar.YEAR, year)
                                                                set(Calendar.MONTH, month)
                                                                set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                                                set(Calendar.HOUR_OF_DAY, 12)
                                                                set(Calendar.MINUTE, 0)
                                                                set(Calendar.SECOND, 0)
                                                                set(Calendar.MILLISECOND, 0)
                                                            }
                                                            val newDateMs = selectedCal.timeInMillis
                                                            viewModel.updateCustomerMedication(med.copy(nextRefillDate = newDateMs))
                                                            Toast.makeText(context, "Refill date updated for ${med.medicationName}", Toast.LENGTH_SHORT).show()
                                                        },
                                                        cal.get(Calendar.YEAR),
                                                        cal.get(Calendar.MONTH),
                                                        cal.get(Calendar.DAY_OF_MONTH)
                                                    ).show()
                                                }
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                                Icon(
                                                    imageVector = Icons.Filled.Event,
                                                    contentDescription = "Select Next Refill Date",
                                                    tint = TealPrimary,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    text = "Due: ${sdf.format(Date(med.nextRefillDate))}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TealPrimary
                                                )
                                            }
                                        }
                                    }

                                    if (stockItem != null && !stockOk) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(WarningRed.copy(alpha = 0.08f))
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Warning,
                                                contentDescription = "Alert",
                                                tint = WarningRed,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Insufficient pharmacy inventory stock!",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = WarningRed,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))
                                    HorizontalDivider(color = SlateBorderLight)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Actions Layout
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        var showWhatsAppTemplatesFor by remember { mutableStateOf<CustomerMedication?>(null) }
                                        Box(modifier = Modifier.weight(1f)) {
                                            OutlinedButton(
                                                onClick = { showWhatsAppTemplatesFor = med },
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    contentColor = OKGreen
                                                ),
                                                border = BorderStroke(1.dp, OKGreen.copy(alpha = 0.3f)),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(34.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Message,
                                                    contentDescription = "Send Reminder",
                                                    tint = OKGreen,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Send Reminder",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = showWhatsAppTemplatesFor == med,
                                                onDismissRequest = { showWhatsAppTemplatesFor = null }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("WhatsApp Refill Reminder") },
                                                    onClick = {
                                                        showWhatsAppTemplatesFor = null
                                                        sendWhatsAppWalletReminder(context, customer, med, sdf.format(Date(med.nextRefillDate)))
                                                        viewModel.activePostDispatchConfirm.value = com.example.ui.PostDispatchConfirmData(customer, med)
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Twilio Refill Reminder (WhatsApp/SMS)") },
                                                    onClick = {
                                                        showWhatsAppTemplatesFor = null
                                                        coroutineScope.launch {
                                                            val result = viewModel.sendTwilioRefillReminder(
                                                                patientName = customer.name,
                                                                phone = customer.phoneNumber,
                                                                medicationName = med.medicationName,
                                                                dateStr = sdf.format(Date(med.nextRefillDate)),
                                                                cost = med.cost,
                                                                medicationId = med.id.toLong()
                                                            )
                                                            when (result) {
                                                                is com.example.util.TwilioMessagingManager.DispatchResult.Success -> {
                                                                    Toast.makeText(context, "Refill Notice sent via ${result.channel} (SID: ${result.sid})", Toast.LENGTH_LONG).show()
                                                                    viewModel.activePostDispatchConfirm.value = com.example.ui.PostDispatchConfirmData(customer, med)
                                                                }
                                                                is com.example.util.TwilioMessagingManager.DispatchResult.Blocked -> {
                                                                    Toast.makeText(context, result.reason, Toast.LENGTH_LONG).show()
                                                                }
                                                                is com.example.util.TwilioMessagingManager.DispatchResult.Failed -> {
                                                                    Toast.makeText(context, "Twilio dispatch failed: ${result.error}", Toast.LENGTH_LONG).show()
                                                                }
                                                            }
                                                        }
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Pick-up Ready (WhatsApp)") },
                                                    onClick = {
                                                        showWhatsAppTemplatesFor = null
                                                        sendWhatsAppTemplate(context, customer, med.medicationName, "Pick-up Ready", "Your prescription for ${med.medicationName} is packed and ready for pick-up. Stop by at your convenience!")
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Twilio Pick-up Ready Notice") },
                                                    onClick = {
                                                        showWhatsAppTemplatesFor = null
                                                        coroutineScope.launch {
                                                            val result = viewModel.sendTwilioMessage(
                                                                phone = customer.phoneNumber,
                                                                messageContent = "CareFlux Ready Notice:\nHello ${customer.name}, your prescription for ${med.medicationName} is packed and ready for pick-up. Stop by CareFlux Pharmacy at your convenience!",
                                                                messageType = "Pick-up Ready",
                                                                medicationIdOrKey = med.id.toString(),
                                                                forceOverrideQuietHours = true
                                                            )
                                                            when (result) {
                                                                is com.example.util.TwilioMessagingManager.DispatchResult.Success -> {
                                                                    Toast.makeText(context, "Pick-up alert dispatched via ${result.channel}!", Toast.LENGTH_SHORT).show()
                                                                }
                                                                is com.example.util.TwilioMessagingManager.DispatchResult.Blocked -> {
                                                                    Toast.makeText(context, result.reason, Toast.LENGTH_LONG).show()
                                                                }
                                                                is com.example.util.TwilioMessagingManager.DispatchResult.Failed -> {
                                                                    Toast.makeText(context, "Twilio alert failed: ${result.error}", Toast.LENGTH_SHORT).show()
                                                                }
                                                            }
                                                        }
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Missed Dose Check-in (WhatsApp)") },
                                                    onClick = {
                                                        showWhatsAppTemplatesFor = null
                                                        sendWhatsAppTemplate(context, customer, med.medicationName, "Missed Dose Check", "Hi ${customer.name}, just checking in to see how you are doing with your ${med.medicationName}. Let us know if you need any advice!")
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Twilio Welfare Follow-up Check") },
                                                    onClick = {
                                                        showWhatsAppTemplatesFor = null
                                                        coroutineScope.launch {
                                                            val result = viewModel.sendTwilioWelfareCheck(
                                                                patientName = customer.name,
                                                                phone = customer.phoneNumber,
                                                                wellnessQuestion = "How are you getting on with your ${med.medicationName} dose regimen?"
                                                            )
                                                            when (result) {
                                                                is com.example.util.TwilioMessagingManager.DispatchResult.Success -> {
                                                                    Toast.makeText(context, "Welfare follow-up sent via ${result.channel}!", Toast.LENGTH_SHORT).show()
                                                                }
                                                                is com.example.util.TwilioMessagingManager.DispatchResult.Blocked -> {
                                                                    Toast.makeText(context, result.reason, Toast.LENGTH_LONG).show()
                                                                }
                                                                is com.example.util.TwilioMessagingManager.DispatchResult.Failed -> {
                                                                    Toast.makeText(context, "Follow-up failed: ${result.error}", Toast.LENGTH_SHORT).show()
                                                                }
                                                            }
                                                        }
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Special Promo") },
                                                    onClick = {
                                                        showWhatsAppTemplatesFor = null
                                                        sendWhatsAppTemplate(context, customer, med.medicationName, "Promo", "Great news! We have a special discount on your ${med.medicationName} this week. Contact us to claim it.")
                                                    }
                                                )
                                                if (customTemplates.isNotEmpty()) {
                                                    HorizontalDivider(color = SlateBorderLight)
                                                    customTemplates.forEach { template ->
                                                        DropdownMenuItem(
                                                            text = { Text(template.title) },
                                                            onClick = {
                                                                showWhatsAppTemplatesFor = null
                                                                val parsedMsg = template.message
                                                                    .replace("{NAME}", customer.name)
                                                                    .replace("{MED}", med.medicationName)
                                                                sendWhatsAppTemplate(context, customer, med.medicationName, template.title, parsedMsg)
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        IconButton(
                                            onClick = { sendEmailWalletReminder(context, customer, med, sdf.format(Date(med.nextRefillDate))) },
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(TealPrimary.copy(alpha = 0.05f))
                                                .size(34.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Email,
                                                contentDescription = "Email",
                                                tint = TealPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = { 
                                                pushDaysText = med.cycleDays.toString()
                                                medToPushRefill = med
                                            },
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(TealPrimary.copy(alpha = 0.05f))
                                                .size(34.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.EventRepeat,
                                                contentDescription = "Push Refill",
                                                tint = TealPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } // End if (expanded)

                if (expanded) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = SlateBorderLight)
                    Spacer(modifier = Modifier.height(12.dp))

                    PatientIntelligencePreviewCard(
                        customer = customer,
                        medications = medications,
                        onOpenWorkspace = { showClinicalWorkspace = true }
                    )

                    if (showClinicalWorkspace) {
                        PatientIntelligenceWorkspaceDialog(
                            customer = customer,
                            medications = medications,
                            interventions = interventions,
                            viewModel = viewModel,
                            context = context,
                            onAddInterventionClick = onAddInterventionClick,
                            onDismissRequest = { showClinicalWorkspace = false }
                        )
                    }

                    // Profile Management Actions
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = SlateBorderLight)
                    Spacer(modifier = Modifier.height(12.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = { showSendMessageDialog = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = TealPrimary),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Filled.Chat, contentDescription = "Send Message", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Send Message", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            onClick = onEditClick,
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit Profile", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit Profile", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            onClick = onDeleteClick,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete Profile", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete Profile", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendCustomerMessageDialog(
    customer: Customer,
    medications: List<CustomerMedication>,
    viewModel: PharmacyViewModel,
    onDismiss: () -> Unit,
    context: Context
) {
    val templates by viewModel.customTemplates.collectAsStateWithLifecycle()
    var selectedTemplateIndex by remember { mutableStateOf(-1) }
    var messageText by remember { mutableStateOf("") }
    var selectedMedication by remember { mutableStateOf("") }
    
    // States for adding a new template
    var showAddTemplateForm by remember { mutableStateOf(false) }
    var newTemplateTitle by remember { mutableStateOf("") }
    var newTemplateMessage by remember { mutableStateOf("") }

    // List of medications options
    val medOptions = medications.map { it.medicationName }.distinct()
    
    // Auto-update message preview when selected template or medication changes
    LaunchedEffect(selectedTemplateIndex, selectedMedication) {
        val baseMsg = when (selectedTemplateIndex) {
            -1 -> ""
            0 -> "Hi {NAME}, just checking in to see how you are doing! Let us know if you need any assistance."
            1 -> "Hello {NAME}! We have some special offers happening this week at Careflux Pharmacy. Stop by to check them out!"
            else -> templates.getOrNull(selectedTemplateIndex - 2)?.message ?: ""
        }
        val currentMed = if (selectedMedication.isNotEmpty()) selectedMedication else "your medication"
        messageText = baseMsg
            .replace("{NAME}", customer.name)
            .replace("{MED}", currentMed)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Chat, contentDescription = null, tint = TealPrimary)
                Text("Send Message to ${customer.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Medication Picker (if they want to substitute {MED})
                if (medOptions.isNotEmpty()) {
                    Text("Select Medication for Context (Optional)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = SlateTextMedium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        medOptions.forEach { med ->
                            FilterChip(
                                selected = selectedMedication == med,
                                onClick = { selectedMedication = if (selectedMedication == med) "" else med },
                                label = { Text(med, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                // Choose a template
                Text("Choose Template", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = SlateTextMedium)
                
                // Template list
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val templateList = listOf(
                        "General Check-in" to "Hi {NAME}, just checking in to see how you are doing! Let us know if you need any assistance.",
                        "Special Promo" to "Hello {NAME}! We have some special offers happening this week at Careflux Pharmacy. Stop by to check them out!"
                    ) + templates.map { it.title to it.message }

                    var showTemplateDropdown by remember { mutableStateOf(false) }
                    val currentLabel = if (selectedTemplateIndex == -1) "Custom Text (No Template)" else {
                        if (selectedTemplateIndex < 2) templateList[selectedTemplateIndex].first else templates[selectedTemplateIndex - 2].title
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { showTemplateDropdown = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(currentLabel, maxLines = 1)
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                            }
                        }
                        DropdownMenu(
                            expanded = showTemplateDropdown,
                            onDismissRequest = { showTemplateDropdown = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Custom Text (No Template)") },
                                onClick = {
                                    selectedTemplateIndex = -1
                                    messageText = ""
                                    showTemplateDropdown = false
                                }
                            )
                            templateList.forEachIndexed { idx, pair ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(pair.first, modifier = Modifier.weight(1f))
                                            if (idx >= 2) {
                                                IconButton(
                                                    onClick = {
                                                        val targetTemplate = templates[idx - 2]
                                                        viewModel.deleteWhatsAppTemplate(targetTemplate)
                                                        if (selectedTemplateIndex == idx) {
                                                            selectedTemplateIndex = -1
                                                        }
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Filled.Delete, contentDescription = "Delete Template", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    },
                                    onClick = {
                                        selectedTemplateIndex = idx
                                        showTemplateDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Create a template toggle
                if (!showAddTemplateForm) {
                    TextButton(
                        onClick = { showAddTemplateForm = true },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Create New Template", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Create New Template", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TealTertiary)
                            Text("Use {NAME} and {MED} as placeholders. Example: Hello {NAME}, your {MED} is ready.", fontSize = 9.sp, lineHeight = 11.sp, color = SlateTextMedium)
                            OutlinedTextField(
                                value = newTemplateTitle,
                                onValueChange = { newTemplateTitle = it },
                                label = { Text("Template Title", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = newTemplateMessage,
                                onValueChange = { newTemplateMessage = it },
                                label = { Text("Template Message", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { showAddTemplateForm = false }) {
                                    Text("Cancel", fontSize = 12.sp)
                                }
                                Button(
                                    onClick = {
                                        if (newTemplateTitle.isNotBlank() && newTemplateMessage.isNotBlank()) {
                                            viewModel.addWhatsAppTemplate(newTemplateTitle, newTemplateMessage)
                                            newTemplateTitle = ""
                                            newTemplateMessage = ""
                                            showAddTemplateForm = false
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                                ) {
                                    Text("Save Template", fontSize = 12.sp, color = Color.Black)
                                }
                            }
                        }
                    }
                }

                // Preview & Edit
                Text("Message Preview & Customization", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = SlateTextMedium)
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    label = { Text("Message Body") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    sendWhatsAppTemplate(context, customer, "", "WhatsApp Message", messageText)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
            ) {
                Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Send (WhatsApp)")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}


fun sendWhatsAppTemplate(context: Context, customer: Customer, medName: String, templateTitle: String, message: String) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        val encodedMsg = Uri.encode(message)
        data = Uri.parse("https://api.whatsapp.com/send?phone=${customer.phoneNumber}&text=$encodedMsg")
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "WhatsApp not installed.", Toast.LENGTH_SHORT).show()
    }
}

fun sendWhatsAppWalletReminder(context: Context, customer: Customer, med: CustomerMedication, dateStr: String) {
    val formattedCost = "%,.2f".format(med.cost)
    val message = "Hello ${customer.name}, just a friendly clinical reminder that your refill for ${med.medicationName} (${med.customDosage}) is due on $dateStr. The estimated cost will be ₦$formattedCost. Please ready your wallet and we'll see you soon!"
    val intent = Intent(Intent.ACTION_VIEW).apply {
        val encodedMsg = Uri.encode(message)
        data = Uri.parse("https://api.whatsapp.com/send?phone=${customer.phoneNumber}&text=$encodedMsg")
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "WhatsApp not installed.", Toast.LENGTH_SHORT).show()
    }
}

fun sendEmailWalletReminder(context: Context, customer: Customer, med: CustomerMedication, dateStr: String) {
    if (customer.email.isBlank()) {
        Toast.makeText(context, "Customer email not provided.", Toast.LENGTH_SHORT).show()
        return
    }
    val subject = "Refill Reminder: ${med.medicationName}"
    val formattedCost = "%,.2f".format(med.cost)
    val message = "Hello ${customer.name},\n\nJust a friendly clinical reminder that your refill for ${med.medicationName} (${med.customDosage}) is due on $dateStr. The estimated cost will be ₦$formattedCost. Please ready your wallet and we'll see you soon!\n\nBest regards,\nCareflux Pharmacy"
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:")
        putExtra(Intent.EXTRA_EMAIL, arrayOf(customer.email))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, message)
    }
    try {
        context.startActivity(Intent.createChooser(intent, "Send email via"))
    } catch (e: Exception) {
        Toast.makeText(context, "Email app not found.", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun EmptyCustomerPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .background(color = TealSecondary.copy(alpha = 0.3f), shape = RoundedCornerShape(16.dp))
            .border(width = 1.dp, color = SlateBorderLight, shape = RoundedCornerShape(16.dp))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.PeopleAlt,
            contentDescription = null,
            tint = TealPrimary.copy(alpha = 0.5f),
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Customer list is empty.",
            style = MaterialTheme.typography.titleMedium,
            color = TealTertiary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tap 'Add Customer' to start managing your patients.",
            style = MaterialTheme.typography.bodySmall,
            color = SlateTextMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

// ==========================================
// DIALOG: Edit Customer
// ==========================================
@Composable
fun EditCustomerDialog(
    customer: Customer,
    onDismiss: () -> Unit,
    onConfirm: (Customer) -> Unit
) {
    var name by remember { mutableStateOf(customer.name) }
    var phone by remember { mutableStateOf(customer.phoneNumber) }
    var email by remember { mutableStateOf(customer.email) }
    var notes by remember { mutableStateOf(customer.notes) }

    var isExpanded by remember { mutableStateOf(false) }
    var isNdpaExpanded by remember { mutableStateOf(false) }
    var ageStr by remember { mutableStateOf(customer.age.toString()) }
    var gender by remember { mutableStateOf(customer.gender) }
    var stateField by remember { mutableStateOf(customer.state) }
    var lgaField by remember { mutableStateOf(customer.lga) }
    var cityField by remember { mutableStateOf(customer.city) }

    // NDPA States
    var consentPrescriptionTracking by remember { mutableStateOf(customer.consentPrescriptionTracking) }
    var consentSmsRefills by remember { mutableStateOf(customer.consentSmsRefills) }
    var consentCloudSync by remember { mutableStateOf(customer.consentCloudSync) }
    var consentChannel by remember { mutableStateOf(customer.consentChannel) }

    var isError by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }

    val isFormDirty = name != customer.name ||
                      phone != customer.phoneNumber ||
                      email != customer.email ||
                      notes != customer.notes ||
                      ageStr != customer.age.toString() ||
                      gender != customer.gender ||
                      stateField != customer.state ||
                      lgaField != customer.lga ||
                      cityField != customer.city ||
                      consentPrescriptionTracking != customer.consentPrescriptionTracking ||
                      consentSmsRefills != customer.consentSmsRefills ||
                      consentCloudSync != customer.consentCloudSync ||
                      consentChannel != customer.consentChannel

    if (showDiscardConfirm) {
        DiscardChangesConfirmationDialog(
            onConfirmDiscard = {
                showDiscardConfirm = false
                onDismiss()
            },
            onDismissConfirm = { showDiscardConfirm = false }
        )
    }

    AlertDialog(
        onDismissRequest = { if (isFormDirty) showDiscardConfirm = true else onDismiss() },
        title = {
            Text("Edit Patient", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isError) {
                    Text("Name and phone are required.", color = Color.Red, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = name, onValueChange = { name = it; isError = false },
                    label = { Text("Patient Name") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it; isError = false },
                    label = { Text("WhatsApp Number") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
                OutlinedTextField(
                    value = email, onValueChange = { email = it },
                    label = { Text("Email Address") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                val isJsonNotes = remember(customer.notes) {
                    val trimmed = customer.notes.trim()
                    trimmed.startsWith("{") && trimmed.endsWith("}")
                }
                if (isJsonNotes) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f))
                            .border(1.dp, TealPrimary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Hub,
                                contentDescription = "Intelligence Active",
                                tint = TealPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Clinical Intelligence Profile Active",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TealPrimary
                                )
                                Text(
                                    text = "Clinical notes and summaries are managed inside the Clinical Workspace dashboard.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SlateTextMedium
                                )
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = notes, onValueChange = { notes = it },
                        label = { Text("Notes (Optional)") }, maxLines = 2, modifier = Modifier.fillMaxWidth()
                    )
                }

                // Demographic Expandable Accordion
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                        .clickable { isExpanded = !isExpanded }
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Analytics,
                                contentDescription = null,
                                tint = TealPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Demographics & Location",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Icon(
                            imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isExpanded) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = ageStr,
                                    onValueChange = { ageStr = it },
                                    label = { Text("Age") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Gender",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        listOf("Male", "Female", "Other").forEach { gName ->
                                            val isSelected = gender == gName
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (isSelected) TealPrimary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                                    .clickable { gender = gName }
                                                    .padding(horizontal = 6.dp, vertical = 6.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = gName,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = stateField,
                                onValueChange = { stateField = it },
                                label = { Text("State") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = lgaField,
                                    onValueChange = { lgaField = it },
                                    label = { Text("LGA") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = cityField,
                                    onValueChange = { cityField = it },
                                    label = { Text("City") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // NDPA Consent Expandable Accordion
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                        .clickable { isNdpaExpanded = !isNdpaExpanded }
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Security,
                                contentDescription = null,
                                tint = TealPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "NDPA Data Protection & Consent",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Icon(
                            imageVector = if (isNdpaExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (isNdpaExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isNdpaExpanded) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Under the Nigeria Data Protection Act (NDPA) 2023, the patient must authorize clinical record-keeping, messaging, and multi-node cloud syncing.",
                                    fontSize = 10.sp,
                                    lineHeight = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                            
                            // Switch 1: Prescription Tracking
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(0.8f)) {
                                    Text("Local Clinical Processing", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Text("Permits logging medications & running clinical drug interaction checks locally.", fontSize = 9.sp, lineHeight = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = consentPrescriptionTracking,
                                    onCheckedChange = { consentPrescriptionTracking = it }
                                )
                            }
                            
                            // Switch 2: SMS Refill Alerts
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(0.8f)) {
                                    Text("WhatsApp & SMS Refill Alerts", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Text("Permits sending automatic SMS notifications for drug refills and dose reminders.", fontSize = 9.sp, lineHeight = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = consentSmsRefills,
                                    onCheckedChange = { consentSmsRefills = it }
                                )
                            }
                            
                            // Switch 3: Multi-Branch Cloud Sync
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(0.8f)) {
                                    Text("Global Care-Node Syncing", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Text("Permits secure, encrypted syncing with global clinical nodes.", fontSize = 9.sp, lineHeight = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = consentCloudSync,
                                    onCheckedChange = { consentCloudSync = it }
                                )
                            }
                            
                            // Consent collection channel
                            Column {
                                Text(
                                    text = "Consent Collection Channel",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    listOf("Verbal Consent", "OTP Verified", "Written Sheet").forEach { ch ->
                                        val isSel = consentChannel == ch
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isSel) TealPrimary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                                .clickable { consentChannel = ch }
                                                .padding(vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = ch,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSel) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isBlank() || phone.isBlank()) {
                    isError = true
                } else {
                    val ageNum = ageStr.toIntOrNull() ?: customer.age
                    onConfirm(
                        customer.copy(
                            name = name,
                            phoneNumber = phone,
                            email = email,
                            notes = notes,
                            age = ageNum,
                            gender = gender,
                            state = stateField,
                            lga = lgaField,
                            city = cityField,
                            consentPrescriptionTracking = consentPrescriptionTracking,
                            consentSmsRefills = consentSmsRefills,
                            consentCloudSync = consentCloudSync,
                            consentChannel = consentChannel,
                            consentLastUpdated = System.currentTimeMillis()
                        )
                    )
                }
            }) { Text("Save Changes") }
        },
        dismissButton = { TextButton(onClick = { if (isFormDirty) showDiscardConfirm = true else onDismiss() }) { Text("Cancel") } }
    )
}

@Composable
fun AddCustomerDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, Int, String, String, String, String, Boolean, Boolean, Boolean, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    var isExpanded by remember { mutableStateOf(false) }
    var isNdpaExpanded by remember { mutableStateOf(false) }
    var ageStr by remember { mutableStateOf("30") }
    var gender by remember { mutableStateOf("Male") }
    var stateField by remember { mutableStateOf("Lagos") }
    var lgaField by remember { mutableStateOf("Ikeja") }
    var cityField by remember { mutableStateOf("Ikeja") }

    // NDPA States
    var consentPrescriptionTracking by remember { mutableStateOf(true) }
    var consentSmsRefills by remember { mutableStateOf(false) }
    var consentCloudSync by remember { mutableStateOf(false) }
    var consentChannel by remember { mutableStateOf("Verbal Consent") }

    var isError by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }

    val isFormDirty = name.isNotBlank() ||
                      phone.isNotBlank() ||
                      email.isNotBlank() ||
                      notes.isNotBlank() ||
                      ageStr != "30" ||
                      gender != "Male" ||
                      stateField != "Lagos" ||
                      lgaField != "Ikeja" ||
                      cityField != "Ikeja" ||
                      !consentPrescriptionTracking ||
                      consentSmsRefills ||
                      consentCloudSync ||
                      consentChannel != "Verbal Consent"

    if (showDiscardConfirm) {
        DiscardChangesConfirmationDialog(
            onConfirmDiscard = {
                showDiscardConfirm = false
                onDismiss()
            },
            onDismissConfirm = { showDiscardConfirm = false }
        )
    }

    AlertDialog(
        onDismissRequest = { if (isFormDirty) showDiscardConfirm = true else onDismiss() },
        title = {
            Text("Add Patient", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isError) {
                    Text("Name and phone are required.", color = Color.Red, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = name, onValueChange = { name = it; isError = false },
                    label = { Text("Patient Name") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it; isError = false },
                    label = { Text("WhatsApp Number") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
                OutlinedTextField(
                    value = email, onValueChange = { email = it },
                    label = { Text("Email Address") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text("Notes (Optional)") }, maxLines = 2, modifier = Modifier.fillMaxWidth()
                )

                // Demographic Expandable Accordion
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                        .clickable { isExpanded = !isExpanded }
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Analytics,
                                contentDescription = null,
                                tint = TealPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Demographics & Location",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Icon(
                            imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isExpanded) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = ageStr,
                                    onValueChange = { ageStr = it },
                                    label = { Text("Age") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Gender",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        listOf("Male", "Female", "Other").forEach { gName ->
                                            val isSelected = gender == gName
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (isSelected) TealPrimary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                                    .clickable { gender = gName }
                                                    .padding(horizontal = 6.dp, vertical = 6.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = gName,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = stateField,
                                onValueChange = { stateField = it },
                                label = { Text("State") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = lgaField,
                                    onValueChange = { lgaField = it },
                                    label = { Text("LGA") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = cityField,
                                    onValueChange = { cityField = it },
                                    label = { Text("City") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // NDPA Consent Expandable Accordion
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                        .clickable { isNdpaExpanded = !isNdpaExpanded }
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Security,
                                contentDescription = null,
                                tint = TealPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "NDPA Data Protection & Consent",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Icon(
                            imageVector = if (isNdpaExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (isNdpaExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isNdpaExpanded) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Under the Nigeria Data Protection Act (NDPA) 2023, the patient must authorize clinical record-keeping, messaging, and multi-node cloud syncing.",
                                    fontSize = 10.sp,
                                    lineHeight = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                            
                            // Switch 1: Prescription Tracking
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(0.8f)) {
                                    Text("Local Clinical Processing", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Text("Permits logging medications & running clinical drug interaction checks locally.", fontSize = 9.sp, lineHeight = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = consentPrescriptionTracking,
                                    onCheckedChange = { consentPrescriptionTracking = it }
                                )
                            }
                            
                            // Switch 2: SMS Refill Alerts
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(0.8f)) {
                                    Text("WhatsApp & SMS Refill Alerts", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Text("Permits sending automatic SMS notifications for drug refills and dose reminders.", fontSize = 9.sp, lineHeight = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = consentSmsRefills,
                                    onCheckedChange = { consentSmsRefills = it }
                                )
                            }
                            
                            // Switch 3: Multi-Branch Cloud Sync
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(0.8f)) {
                                    Text("Global Care-Node Syncing", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Text("Permits secure, encrypted syncing with global clinical nodes.", fontSize = 9.sp, lineHeight = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = consentCloudSync,
                                    onCheckedChange = { consentCloudSync = it }
                                )
                            }
                            
                            // Consent collection channel
                            Column {
                                Text(
                                    text = "Consent Collection Channel",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    listOf("Verbal Consent", "OTP Verified", "Written Sheet").forEach { ch ->
                                        val isSel = consentChannel == ch
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isSel) TealPrimary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                                .clickable { consentChannel = ch }
                                                .padding(vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = ch,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSel) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isBlank() || phone.isBlank()) {
                    isError = true
                } else {
                    val ageNum = ageStr.toIntOrNull() ?: 30
                    onConfirm(
                        name, phone, email, notes, ageNum, gender, stateField, lgaField, cityField,
                        consentPrescriptionTracking, consentSmsRefills, consentCloudSync, consentChannel
                    )
                }
            }) { Text("Save Patient") }
        },
        dismissButton = { TextButton(onClick = { if (isFormDirty) showDiscardConfirm = true else onDismiss() }) { Text("Cancel") } }
    )
}

// ==========================================
// DIALOG: Add Prescription
// ==========================================
@Composable
fun AddPrescriptionDialog(
    customer: Customer,
    inventoryMeds: List<InventoryItem>,
    currentMeds: List<CustomerMedication> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (Int, Int, String, String, Double, Int, Long) -> Unit
) {
    var selectedMedId by remember { mutableStateOf(inventoryMeds.firstOrNull()?.id ?: 0) }
    var dose by remember { mutableStateOf("") }
    var costStr by remember { mutableStateOf("") }
    var daysStr by remember { mutableStateOf("30") }
    var errorMsg by remember { mutableStateOf("") }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var medSearchQuery by remember { mutableStateOf("") }

    val filteredMeds = remember(inventoryMeds, medSearchQuery) {
        if (medSearchQuery.isBlank()) {
            inventoryMeds
        } else {
            val q = medSearchQuery.trim()
            inventoryMeds.filter { item ->
                item.name.contains(q, ignoreCase = true) ||
                item.brand.contains(q, ignoreCase = true) ||
                item.category.contains(q, ignoreCase = true) ||
                item.dosage.contains(q, ignoreCase = true)
            }
        }
    }

    val isFormDirty = dose.isNotBlank() ||
                      costStr.isNotBlank() ||
                      daysStr != "30"

    if (showDiscardConfirm) {
        DiscardChangesConfirmationDialog(
            onConfirmDiscard = {
                showDiscardConfirm = false
                onDismiss()
            },
            onDismissConfirm = { showDiscardConfirm = false }
        )
    }

    val selectedMed = inventoryMeds.find { it.id == selectedMedId } ?: inventoryMeds.firstOrNull()
    val interactionWarnings = remember(selectedMed, currentMeds, customer) {
        if (selectedMed != null && currentMeds.isNotEmpty()) {
            val allNames = currentMeds.map { it.medicationName } + selectedMed.name
            com.example.data.ClinicalDdiEngine.checkInteractions(allNames, customer)
        } else {
            emptyList()
        }
    }

    AlertDialog(
        onDismissRequest = { if (isFormDirty) showDiscardConfirm = true else onDismiss() },
        title = { Text("Add Med to ${customer.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (errorMsg.isNotEmpty()) Text(errorMsg, color = Color.Red, style = MaterialTheme.typography.bodySmall)

                if (interactionWarnings.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(12.dp))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = "Interaction Risk Warning",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Critical Clinical Hazard!",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            interactionWarnings.forEach { warning ->
                                Text(
                                    text = warning,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
                
                var showMedPickerModal by remember { mutableStateOf(false) }

                // Interactive Trigger Card for Medication Selection
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showMedPickerModal = true },
                    shape = RoundedCornerShape(14.dp),
                    color = TealPrimary.copy(alpha = 0.05f),
                    border = BorderStroke(1.5.dp, TealPrimary.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(TealPrimary.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Medication,
                                contentDescription = null,
                                tint = TealPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "INVENTORY LINKED MEDICATION",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TealPrimary,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = selectedMed?.let { "${it.name} (${it.dosage})" } ?: "Select Medication from Inventory...",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (selectedMed != null) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${selectedMed.brand} • Stock: ${selectedMed.stockQuantity} • ₦${String.format(Locale.getDefault(), "%,.0f", selectedMed.price)}",
                                    fontSize = 11.sp,
                                    color = AppThemeManager.slateTextMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = TealPrimary
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (selectedMed != null) "Change" else "Select",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                // Dedicated High-Contrast Medication Selection Modal Dialog
                if (showMedPickerModal) {
                    androidx.compose.ui.window.Dialog(
                        onDismissRequest = { 
                            showMedPickerModal = false
                            medSearchQuery = ""
                        },
                        properties = androidx.compose.ui.window.DialogProperties(
                            usePlatformDefaultWidth = false,
                            dismissOnBackPress = true,
                            dismissOnClickOutside = true
                        )
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .heightIn(max = 560.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 8.dp,
                            shadowElevation = 24.dp,
                            border = BorderStroke(1.5.dp, TealPrimary.copy(alpha = 0.35f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                            ) {
                                // Top Visual Header Bar with Accent Background
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                colors = listOf(TealPrimary, TealPrimary.copy(alpha = 0.85f))
                                            )
                                        )
                                        .padding(horizontal = 16.dp, vertical = 14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .background(Color.White.copy(alpha = 0.2f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Medication,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Column {
                                                Text(
                                                    text = "Select Inventory Item",
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                                Text(
                                                    text = "${inventoryMeds.size} items in stock • Tap item to link",
                                                    fontSize = 11.sp,
                                                    color = Color.White.copy(alpha = 0.85f)
                                                )
                                            }
                                        }

                                        IconButton(
                                            onClick = { 
                                                showMedPickerModal = false 
                                                medSearchQuery = ""
                                            },
                                            modifier = Modifier
                                                .size(30.dp)
                                                .background(Color.White.copy(alpha = 0.2f), CircleShape)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Close,
                                                contentDescription = "Close Modal",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp)
                                ) {
                                    // Sticky Search Bar
                                    OutlinedTextField(
                                        value = medSearchQuery,
                                        onValueChange = { medSearchQuery = it },
                                        placeholder = { 
                                            Text(
                                                text = "Type to filter by name, brand, dosage...", 
                                                fontSize = 12.5.sp, 
                                                color = AppThemeManager.slateTextMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            ) 
                                        },
                                        leadingIcon = { 
                                            Icon(
                                                imageVector = Icons.Filled.Search, 
                                                contentDescription = "Search", 
                                                tint = TealPrimary, 
                                                modifier = Modifier.size(20.dp)
                                            ) 
                                        },
                                        trailingIcon = {
                                            if (medSearchQuery.isNotEmpty()) {
                                                IconButton(
                                                    onClick = { medSearchQuery = "" },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Close, 
                                                        contentDescription = "Clear Search", 
                                                        tint = AppThemeManager.slateTextMedium,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = TealPrimary,
                                            unfocusedBorderColor = TealPrimary.copy(alpha = 0.4f),
                                            focusedContainerColor = TealPrimary.copy(alpha = 0.03f)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))
                                    HorizontalDivider(color = AppThemeManager.slateBorderLight.copy(alpha = 0.5f))
                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Scrollable Medication List
                                    if (filteredMeds.isEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f)
                                                .padding(vertical = 32.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(
                                                    imageVector = Icons.Filled.SearchOff,
                                                    contentDescription = null,
                                                    tint = AppThemeManager.slateTextMedium,
                                                    modifier = Modifier.size(36.dp)
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = "No medication found matching \"$medSearchQuery\"",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = AppThemeManager.slateTextMedium,
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                )
                                            }
                                        }
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                            contentPadding = PaddingValues(vertical = 2.dp)
                                        ) {
                                            items(filteredMeds, key = { it.id }) { item ->
                                                val isSelected = item.id == selectedMedId
                                                Surface(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            selectedMedId = item.id
                                                            if (costStr.isEmpty() || costStr == "0") {
                                                                costStr = item.price.toInt().toString()
                                                            }
                                                            showMedPickerModal = false
                                                            medSearchQuery = ""
                                                        },
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = if (isSelected) TealPrimary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
                                                    border = BorderStroke(
                                                        width = if (isSelected) 1.5.dp else 1.dp,
                                                        color = if (isSelected) TealPrimary else AppThemeManager.slateBorderLight.copy(alpha = 0.6f)
                                                    )
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        // Left Icon Box Badge
                                                        Box(
                                                            modifier = Modifier
                                                                .size(40.dp)
                                                                .background(
                                                                    color = if (isSelected) TealPrimary.copy(alpha = 0.15f) else AppThemeManager.slateBackgroundLight,
                                                                    shape = RoundedCornerShape(10.dp)
                                                                ),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            if (isSelected) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(22.dp)
                                                                        .background(TealPrimary, CircleShape),
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Filled.Check,
                                                                        contentDescription = "Selected",
                                                                        tint = Color.White,
                                                                        modifier = Modifier.size(14.dp)
                                                                    )
                                                                }
                                                            } else {
                                                                Icon(
                                                                    imageVector = Icons.Filled.Medication,
                                                                    contentDescription = null,
                                                                    tint = AppThemeManager.tertiary.copy(alpha = 0.75f),
                                                                    modifier = Modifier.size(20.dp)
                                                                )
                                                            }
                                                        }

                                                        Spacer(modifier = Modifier.width(10.dp))

                                                        // Middle Details
                                                        Column(
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                                modifier = Modifier.fillMaxWidth()
                                                            ) {
                                                                Text(
                                                                    text = item.name,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = if (isSelected) TealPrimary else MaterialTheme.colorScheme.onSurface,
                                                                    fontSize = 13.5.sp,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis,
                                                                    modifier = Modifier.weight(1f, fill = false)
                                                                )

                                                                if (isSelected) {
                                                                    Surface(
                                                                        shape = RoundedCornerShape(4.dp),
                                                                        color = TealPrimary
                                                                    ) {
                                                                        Text(
                                                                            text = "SELECTED",
                                                                            fontSize = 8.5.sp,
                                                                            fontWeight = FontWeight.ExtraBold,
                                                                            color = Color.White,
                                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                                            maxLines = 1
                                                                        )
                                                                    }
                                                                }
                                                            }

                                                            if (item.brand.isNotBlank()) {
                                                                Spacer(modifier = Modifier.height(1.dp))
                                                                Text(
                                                                    text = item.brand.uppercase(),
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.Medium,
                                                                    color = AppThemeManager.slateTextMedium,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis,
                                                                    modifier = Modifier.fillMaxWidth()
                                                                )
                                                            }

                                                            Spacer(modifier = Modifier.height(3.dp))

                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                                modifier = Modifier.fillMaxWidth()
                                                            ) {
                                                                if (item.dosage.isNotBlank()) {
                                                                    Surface(
                                                                        shape = RoundedCornerShape(6.dp),
                                                                        color = AppThemeManager.slateBackgroundLight,
                                                                        modifier = Modifier.weight(1f, fill = false)
                                                                    ) {
                                                                        Text(
                                                                            text = item.dosage,
                                                                            fontSize = 10.sp,
                                                                            fontWeight = FontWeight.Medium,
                                                                            color = AppThemeManager.tertiary,
                                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                                                            maxLines = 1,
                                                                            overflow = TextOverflow.Ellipsis
                                                                        )
                                                                    }

                                                                    Text(
                                                                        text = "•",
                                                                        fontSize = 10.sp,
                                                                        color = AppThemeManager.slateTextMedium.copy(alpha = 0.4f)
                                                                    )
                                                                }

                                                                Text(
                                                                    text = "Stock: ${item.stockQuantity}",
                                                                    fontSize = 10.5.sp,
                                                                    fontWeight = FontWeight.SemiBold,
                                                                    color = if (item.stockQuantity <= 5) WarningRed else AppThemeManager.slateTextMedium,
                                                                    maxLines = 1
                                                                )
                                                            }
                                                        }

                                                        Spacer(modifier = Modifier.width(8.dp))

                                                        // Right Price & Chevron
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                        ) {
                                                            Text(
                                                                text = "₦${String.format(Locale.getDefault(), "%,.0f", item.price)}",
                                                                fontSize = 13.5.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = TealPrimary,
                                                                maxLines = 1
                                                            )
                                                            Icon(
                                                                imageVector = Icons.Filled.ChevronRight,
                                                                contentDescription = null,
                                                                tint = TealPrimary,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        // Bottom Hint / Close Footer
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Showing ${filteredMeds.size} items",
                                                fontSize = 10.5.sp,
                                                color = AppThemeManager.slateTextMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                            TextButton(
                                                onClick = {
                                                    showMedPickerModal = false
                                                    medSearchQuery = ""
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Text(
                                                    text = "Cancel",
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = AppThemeManager.slateTextMedium
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = dose, onValueChange = { dose = it },
                    label = { Text("Prescribed Dosage (e.g. 1 tab daily)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = costStr, onValueChange = { costStr = it },
                    label = { Text("Total Cycle Cost (₦)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), 
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = daysStr, onValueChange = { daysStr = it },
                    label = { Text("Refill Cycle (Days)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), 
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val c = costStr.toDoubleOrNull()
                val d = daysStr.toIntOrNull()
                if (c == null || d == null || selectedMedId == 0) {
                    errorMsg = "Check your inputs."
                } else {
                    val medItem = inventoryMeds.find { it.id == selectedMedId }
                    val rawName = medItem?.name ?: "Unknown"
                    val stockDosage = medItem?.dosage?.trim() ?: ""
                    val mName = if (stockDosage.isNotBlank() && !stockDosage.equals("N/A", ignoreCase = true) && !rawName.contains(stockDosage, ignoreCase = true)) {
                        "$rawName $stockDosage"
                    } else {
                        rawName
                    }
                    val nextRefill = System.currentTimeMillis() + (d.toLong() * 24L * 60L * 60L * 1000L)
                    onConfirm(customer.id, selectedMedId, mName, dose, c, d, nextRefill)
                }
            }) { Text("Save Rx") }
        },
        dismissButton = { TextButton(onClick = { if (isFormDirty) showDiscardConfirm = true else onDismiss() }) { Text("Cancel") } }
    )
}

// ==========================================
// DIALOG: Add Clinical Intervention
// ==========================================
@Composable
fun AddInterventionDialog(
    customer: Customer,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var presentation by remember { mutableStateOf("") }
    var testResults by remember { mutableStateOf("") }
    var recommendation by remember { mutableStateOf("") }

    var isError by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }

    val isFormDirty = presentation.isNotBlank() ||
                      testResults.isNotBlank() ||
                      recommendation.isNotBlank()

    if (showDiscardConfirm) {
        DiscardChangesConfirmationDialog(
            onConfirmDiscard = {
                showDiscardConfirm = false
                onDismiss()
            },
            onDismissConfirm = { showDiscardConfirm = false }
        )
    }

    AlertDialog(
        onDismissRequest = { if (isFormDirty) showDiscardConfirm = true else onDismiss() },
        title = {
            Text("Clinical Consult - ${customer.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isError) {
                    Text("Please fill out the presentation and recommendation.", color = Color.Red, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = presentation, onValueChange = { presentation = it; isError = false },
                    label = { Text("Symptoms / Presentation") }, maxLines = 3, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = testResults, onValueChange = { testResults = it; isError = false },
                    label = { Text("Test Results (Optional)") }, maxLines = 2, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = recommendation, onValueChange = { recommendation = it },
                    label = { Text("Recommendations / Prescriptions") }, maxLines = 3, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (presentation.isBlank() || recommendation.isBlank()) isError = true else onConfirm(presentation, testResults, recommendation)
            }) { Text("Save Intervention") }
        },
        dismissButton = { TextButton(onClick = { if (isFormDirty) showDiscardConfirm = true else onDismiss() }) { Text("Cancel") } }
    )
}

@Composable
fun DiscardChangesConfirmationDialog(
    onConfirmDiscard: () -> Unit,
    onDismissConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissConfirm,
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
                onClick = onConfirmDiscard,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("Discard Details")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissConfirm) {
                Text("Keep Editing")
            }
        }
    )
}

@Composable
fun RefillCommandCenter(
    customers: List<Customer>,
    customerMeds: List<CustomerMedication>,
    viewModel: PharmacyViewModel,
    context: Context
) {
    val coroutineScope = rememberCoroutineScope()
    
    // Filter medications with refills due within 7 days or overdue
    val now = System.currentTimeMillis()
    val sevenDaysFromNow = now + 7L * 24 * 60 * 60 * 1000
    val chronicMedsDue = remember(customerMeds) {
        customerMeds.filter { it.cycleDays > 0 && it.nextRefillDate <= sevenDaysFromNow }
            .sortedBy { it.nextRefillDate }
    }

    // Keep track of selected refills for bulk action
    val selectedRefills = remember { mutableStateMapOf<Int, Boolean>() }

    // Bulk Sending State
    var isSendingBulk by remember { mutableStateOf(false) }
    var bulkSentCount by remember { mutableStateOf(0) }
    var bulkTotalCount by remember { mutableStateOf(0) }
    var showBulkSmsConfirmDialog by remember { mutableStateOf(false) }

    // WhatsApp Sequencer State
    var showWhatsAppSequencer by remember { mutableStateOf(false) }
    var sequencerIndicesBySelection by remember { mutableStateOf<List<CustomerMedication>>(emptyList()) }
    var currentSequencerStep by remember { mutableStateOf(0) }

    // Trigger update of selection on data change
    LaunchedEffect(chronicMedsDue) {
        selectedRefills.clear()
        chronicMedsDue.forEach { selectedRefills[it.id] = true } // default select all
    }

    if (showWhatsAppSequencer && sequencerIndicesBySelection.isNotEmpty()) {
        val currentMed = sequencerIndicesBySelection.getOrNull(currentSequencerStep)
        val currentCustomer = currentMed?.let { med -> customers.find { it.id == med.customerId } }

        if (currentMed != null && currentCustomer != null) {
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(currentMed.nextRefillDate))
            val messageText = "Hello ${currentCustomer.name}, just a friendly clinical reminder that your chronic refill for ${currentMed.medicationName} (${currentMed.customDosage}) is due on $dateStr. The estimated cost is ₦${String.format("%,.2f", currentMed.cost)}. Please ready your wallet and let us know if you need delivery! - Careflux Pharmacy"

            AlertDialog(
                onDismissRequest = { showWhatsAppSequencer = false },
                icon = { Icon(Icons.Filled.Chat, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(36.dp)) },
                title = { Text("WhatsApp Refill Sequencer", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LinearProgressIndicator(
                            progress = { (currentSequencerStep + 1).toFloat() / sequencerIndicesBySelection.size },
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                            color = TealPrimary,
                            trackColor = SlateBorderLight
                        )
                        Text(
                            text = "Patient ${currentSequencerStep + 1} of ${sequencerIndicesBySelection.size}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = TealPrimary
                        )
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SlateBackgroundLight),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("To: ${currentCustomer.name}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Phone: ${currentCustomer.phoneNumber}", fontSize = 12.sp, color = SlateTextMedium)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(messageText, fontSize = 12.sp, lineHeight = 16.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val encodedMsg = Uri.encode(messageText)
                            val wpUrl = "https://api.whatsapp.com/send?phone=${currentCustomer.phoneNumber.trim().replace("[^0-9]".toRegex(), "")}&text=$encodedMsg"
                            val wpIntent = Intent(Intent.ACTION_VIEW, Uri.parse(wpUrl))
                            context.startActivity(wpIntent)

                            if (currentSequencerStep < sequencerIndicesBySelection.size - 1) {
                                currentSequencerStep++
                            } else {
                                showWhatsAppSequencer = false
                                Toast.makeText(context, "Completed WhatsApp Refill Reminder sequence!", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary, contentColor = Color.Black)
                    ) {
                        Text(if (currentSequencerStep < sequencerIndicesBySelection.size - 1) "Launch Chat & Next" else "Launch Chat & Finish", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            if (currentSequencerStep < sequencerIndicesBySelection.size - 1) {
                                currentSequencerStep++
                            } else {
                                showWhatsAppSequencer = false
                            }
                        }
                    ) {
                        Text("Skip Step")
                    }
                }
            )
        }
    }

    if (showBulkSmsConfirmDialog) {
        val selectedIds = chronicMedsDue.filter { selectedRefills[it.id] == true }
        AlertDialog(
            onDismissRequest = { showBulkSmsConfirmDialog = false },
            icon = { Icon(Icons.Filled.FlashOn, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(36.dp)) },
            title = { Text("Confirm Bulk Multi-Channel Dispatch", fontWeight = FontWeight.Bold) },
            text = {
                Text("Are you sure you want to send refill reminders to the ${selectedIds.size} selected patient(s) via Twilio (WhatsApp/SMS)?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBulkSmsConfirmDialog = false
                        if (selectedIds.isEmpty()) return@Button
                        isSendingBulk = true
                        bulkSentCount = 0
                        bulkTotalCount = selectedIds.size

                        coroutineScope.launch {
                            for (med in selectedIds) {
                                val customer = customers.find { it.id == med.customerId }
                                if (customer != null) {
                                    val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(med.nextRefillDate))
                                    val result = viewModel.sendTwilioRefillReminder(
                                        patientName = customer.name,
                                        phone = customer.phoneNumber,
                                        medicationName = med.medicationName,
                                        dateStr = dateStr,
                                        cost = med.cost,
                                        medicationId = med.id.toLong()
                                    )
                                    if (result is com.example.util.TwilioMessagingManager.DispatchResult.Success) {
                                        bulkSentCount++
                                    }
                                    kotlinx.coroutines.delay(1000L) // Anti-spam rate limiting: 1 msg/sec
                                }
                            }
                            isSendingBulk = false
                            Toast.makeText(context, "Bulk Refill Dispatch complete! Sent $bulkSentCount / $bulkTotalCount.", Toast.LENGTH_LONG).show()
                            selectedRefills.clear()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary, contentColor = Color.Black)
                ) {
                    Text("Proceed", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBulkSmsConfirmDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (chronicMedsDue.isEmpty()) {
            Spacer(modifier = Modifier.height(32.dp))
            EmptyStatePlaceholder(
                message = "Zero Pending Refills Found",
                tip = "Chronic patients whose medication refills are due within 7 days or are overdue will populate here automatically."
            )
            return
        }

        // Bulk Control Panel
        val selectedIds = chronicMedsDue.filter { selectedRefills[it.id] == true }
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = TealSurface.copy(alpha = 0.3f)),
            border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${selectedIds.size} Refills Selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TealPrimary
                        )
                        Text(
                            text = "Across ${selectedIds.distinctBy { it.customerId }.size} unique patients",
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateTextMedium
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = { chronicMedsDue.forEach { selectedRefills[it.id] = true } }
                        ) {
                            Text("Select All", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            onClick = { selectedRefills.clear() }
                        ) {
                            Text("Deselect All", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (isSendingBulk) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(
                            progress = { bulkSentCount.toFloat() / bulkTotalCount },
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                            color = TealPrimary,
                            trackColor = SlateBorderLight
                        )
                        Text(
                            text = "Dispatching Twilio Multi-Channel: $bulkSentCount / $bulkTotalCount completed...",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (selectedIds.isEmpty()) return@Button
                                showBulkSmsConfirmDialog = true
                            },
                            enabled = selectedIds.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary, contentColor = Color.Black),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.FlashOn, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Bulk SMS", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }

                        Button(
                            onClick = {
                                if (selectedIds.isEmpty()) return@Button
                                sequencerIndicesBySelection = chronicMedsDue.filter { selectedRefills[it.id] == true }
                                currentSequencerStep = 0
                                showWhatsAppSequencer = true
                            },
                            enabled = selectedIds.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Chat, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("WhatsApp", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }
        }

        // Active Due List
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            items(chronicMedsDue) { med ->
                val customer = customers.find { it.id == med.customerId }
                val isSelected = selectedRefills[med.id] == true
                
                if (customer != null) {
                    val isOverdue = med.nextRefillDate < now
                    val daysDiff = ((med.nextRefillDate - now) / (1000 * 60 * 60 * 24)).toInt()

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.05f)
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isSelected) TealPrimary.copy(alpha = 0.4f) else SlateBorderLight.copy(alpha = 0.1f)
                        ),
                        modifier = Modifier.fillMaxWidth().clickable {
                            selectedRefills[med.id] = !isSelected
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { selectedRefills[med.id] = it },
                                colors = CheckboxDefaults.colors(checkedColor = TealPrimary),
                                modifier = Modifier.size(24.dp).padding(top = 2.dp)
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = customer.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    // Status Badge
                                    if (isOverdue) {
                                        Card(
                                            shape = RoundedCornerShape(4.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.15f))
                                        ) {
                                            Text(
                                                text = "OVERDUE",
                                                color = Color.Red,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    } else {
                                        Card(
                                            shape = RoundedCornerShape(4.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color.Yellow.copy(alpha = 0.15f))
                                        ) {
                                            Text(
                                                text = "DUE IN $daysDiff DAYS",
                                                color = Color(0xFFD4AF37),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = med.medicationName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TealPrimary,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "${med.customDosage} • ₦${String.format("%,.0f", med.cost)}",
                                        fontSize = 11.sp,
                                        color = SlateTextMedium
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(TealPrimary.copy(alpha = 0.08f))
                                            .border(0.5.dp, TealPrimary.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                                            .clickable {
                                                val cal = Calendar.getInstance().apply {
                                                    timeInMillis = if (med.nextRefillDate > 0L) med.nextRefillDate else System.currentTimeMillis()
                                                }
                                                DatePickerDialog(
                                                    context,
                                                    { _, year, month, dayOfMonth ->
                                                        val selectedCal = Calendar.getInstance().apply {
                                                            set(Calendar.YEAR, year)
                                                            set(Calendar.MONTH, month)
                                                            set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                                            set(Calendar.HOUR_OF_DAY, 12)
                                                            set(Calendar.MINUTE, 0)
                                                            set(Calendar.SECOND, 0)
                                                            set(Calendar.MILLISECOND, 0)
                                                        }
                                                        val newDateMs = selectedCal.timeInMillis
                                                        viewModel.updateCustomerMedication(med.copy(nextRefillDate = newDateMs))
                                                        Toast.makeText(context, "Refill date updated for ${med.medicationName}", Toast.LENGTH_SHORT).show()
                                                    },
                                                    cal.get(Calendar.YEAR),
                                                    cal.get(Calendar.MONTH),
                                                    cal.get(Calendar.DAY_OF_MONTH)
                                                ).show()
                                            }
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Icon(
                                                imageVector = Icons.Filled.Event,
                                                contentDescription = "Select Next Refill Date",
                                                tint = TealPrimary,
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Text(
                                                text = "Due: ${SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(med.nextRefillDate))}",
                                                fontSize = 10.sp,
                                                color = TealPrimary,
                                                fontWeight = FontWeight.Bold
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
}

@Composable
fun EmptyStatePlaceholder(message: String, tip: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = null,
                tint = SlateTextMedium,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = tip,
                style = MaterialTheme.typography.bodySmall,
                color = SlateTextMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}

@Composable
fun ClinicalFollowUpsQueue(
    customers: List<com.example.data.Customer>,
    clinicalInterventions: List<com.example.data.ClinicalIntervention>,
    viewModel: com.example.ui.PharmacyViewModel,
    context: android.content.Context
) {
    val pendingInterventions = remember(clinicalInterventions) {
        clinicalInterventions.sortedByDescending { it.dateAdded }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = "Clinical Follow-ups Queue",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TealTertiary
        )
        Text(
            text = "Track patient recovery progress, answer inquiries, or trigger doctor escalations",
            style = MaterialTheme.typography.bodySmall,
            color = SlateTextMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (pendingInterventions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No clinical follow-ups currently scheduled", style = MaterialTheme.typography.bodyMedium, color = SlateTextMedium)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(pendingInterventions) { interv ->
                    val customer = customers.find { it.id == interv.customerId }
                    val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                    val dateStr = sdf.format(java.util.Date(interv.dateAdded))

                    if (customer != null) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (interv.currentStatus == "Pending") TealSurface.copy(alpha = 0.3f) else SlateBackgroundLight.copy(alpha = 0.5f)
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (interv.currentStatus == "Pending") TealPrimary.copy(alpha = 0.3f) else SlateBorderLight.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = customer.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = TealTertiary
                                        )
                                        Text(
                                            text = "Phone: ${customer.phoneNumber}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SlateTextMedium
                                        )
                                    }

                                    val isPending = interv.currentStatus == "Pending"
                                    SuggestionChip(
                                        onClick = {},
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = if (isPending) WarningRedContainerSoft else TealSurface,
                                            labelColor = if (isPending) WarningRed else OKGreen
                                        ),
                                        label = {
                                            Text(
                                                text = if (isPending) "Pending Follow-up" else "Resolved",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        }
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = interv.presentation,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TealTertiary
                                )

                                if (interv.testResults.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Symptoms Checked: ${interv.testResults}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SlateTextMedium
                                    )
                                }

                                if (interv.recommendation.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = interv.recommendation,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SlateTextMedium,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = SlateBorderLight.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Scheduled: $dateStr",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SlateTextMedium
                                    )

                                    if (interv.currentStatus == "Pending") {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            TextButton(
                                                onClick = {
                                                    viewModel.updateClinicalInterventionStatus(interv, "Feeling Better")
                                                    android.widget.Toast.makeText(context, "Patient recovery marked as resolved!", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            ) {
                                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Mark Resolved", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }

                                            Button(
                                                onClick = {
                                                    val conditionClean = interv.presentation.replace("Triage: ", "")
                                                    val draftMessage = "Hi ${customer.name}, just checking in from Careflux Pharmacy regarding your screening for $conditionClean a few days ago. How are you feeling today? Are your symptoms improving, or do you need further support or custom advice?"
                                                    val encodedMsg = android.net.Uri.encode(draftMessage)
                                                    val wpUrl = "https://api.whatsapp.com/send?phone=${customer.phoneNumber.trim().replace("[^0-9]".toRegex(), "")}&text=$encodedMsg"
                                                    val wpIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(wpUrl))
                                                    try {
                                                        context.startActivity(wpIntent)
                                                    } catch (e: Exception) {
                                                        android.widget.Toast.makeText(context, "WhatsApp is not installed.", android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Icon(Icons.Filled.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("WhatsApp Triage Follow-up", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = "Closed Protocol",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = OKGreen
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

@Composable
fun PatientTreatmentHistoryDialog(
    customer: Customer,
    medications: List<CustomerMedication>,
    context: Context,
    onDismiss: () -> Unit
) {
    var selectedOption by remember { mutableStateOf(0) }
    val nowMs = remember { System.currentTimeMillis() }

    val sdfDate = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    var startDateStr by remember { mutableStateOf(sdfDate.format(Date(nowMs - 30L * 24 * 60 * 60 * 1000))) }
    var endDateStr by remember { mutableStateOf(sdfDate.format(Date(nowMs))) }

    val startDateMs: Long? = remember(selectedOption, startDateStr, nowMs) {
        when (selectedOption) {
            1 -> nowMs - (30L * 24 * 60 * 60 * 1000)
            2 -> nowMs - (90L * 24 * 60 * 60 * 1000)
            3 -> nowMs - (365L * 24 * 60 * 60 * 1000)
            4 -> try { sdfDate.parse(startDateStr.trim())?.time } catch (e: Exception) { null }
            else -> null
        }
    }

    val endDateMs: Long? = remember(selectedOption, endDateStr, nowMs) {
        when (selectedOption) {
            4 -> try { sdfDate.parse(endDateStr.trim())?.time } catch (e: Exception) { null }
            else -> null
        }
    }

    val filteredMeds = remember(medications, startDateMs, endDateMs) {
        medications.filter { med ->
            val timestamp = if (med.dateAdded > 0) med.dateAdded else med.nextRefillDate
            val matchesStart = startDateMs == null || timestamp >= startDateMs
            val matchesEnd = endDateMs == null || timestamp <= endDateMs + (24L * 60 * 60 * 1000 - 1)
            matchesStart && matchesEnd
        }.sortedByDescending { if (it.dateAdded > 0) it.dateAdded else it.nextRefillDate }
    }

    val totalCost = remember(filteredMeds) { filteredMeds.sumOf { it.cost } }

    var previewResult by remember { mutableStateOf<com.example.PatientHistoryPdfResult?>(null) }
    var showInAppViewer by remember { mutableStateOf(false) }

    if (showInAppViewer && previewResult != null) {
        InAppPdfPreviewDialog(
            result = previewResult!!,
            customerName = customer.name,
            context = context,
            onDismiss = { showInAppViewer = false }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(TealSecondary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(20.dp))
                }
                Column {
                    Text("Treatment & Medication History PDF", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TealTertiary, fontSize = 14.sp)
                    Text("Patient: ${customer.name}", style = MaterialTheme.typography.bodySmall, color = SlateTextMedium, fontSize = 11.sp)
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(
                    text = "Select date scope to generate the historical treatment dataset PDF report:",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateTextMedium
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val filterLabels = listOf("All Time", "Past 30 Days", "Past 90 Days", "Past 1 Year", "Custom Range")
                    filterLabels.forEachIndexed { index, label ->
                        FilterChip(
                            selected = selectedOption == index,
                            onClick = { selectedOption = index },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TealPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                if (selectedOption == 4) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = startDateStr,
                            onValueChange = { startDateStr = it },
                            label = { Text("Start (YYYY-MM-DD)", fontSize = 10.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = endDateStr,
                            onValueChange = { endDateStr = it },
                            label = { Text("End (YYYY-MM-DD)", fontSize = 10.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = TealSurface),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, SlateBorderLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Records Found:", fontSize = 12.sp, color = SlateTextMedium)
                            Text("${filteredMeds.size} Medications", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TealTertiary)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Total Cumulative Spend:", fontSize = 12.sp, color = SlateTextMedium)
                            Text(String.format("₦%,.2f", totalCost), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = OKGreen)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (filteredMeds.isNotEmpty()) {
                    Text("Recent Items in Dataset:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SlateTextMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    filteredMeds.take(3).forEach { med ->
                        val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(if (med.dateAdded > 0) med.dateAdded else med.nextRefillDate))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("• ${med.medicationName}", fontSize = 11.sp, color = TealTertiary, maxLines = 1, modifier = Modifier.weight(1f))
                            Text("$dateStr (₦${med.cost})", fontSize = 11.sp, color = SlateTextMedium)
                        }
                    }
                    if (filteredMeds.size > 3) {
                        Text("+ ${filteredMeds.size - 3} more items included in full PDF...", fontSize = 10.sp, color = SlateTextMedium)
                    }
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = {
                        val result = com.example.DocumentGenerator.generatePatientTreatmentHistoryReport(
                            context = context,
                            customer = customer,
                            medications = medications,
                            startDateMs = startDateMs,
                            endDateMs = endDateMs
                        )
                        previewResult = result

                        val pdfUri = result.pdfUri
                        if (pdfUri != null) {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(pdfUri, "application/pdf")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            try {
                                context.startActivity(Intent.createChooser(intent, "Open Patient Treatment History PDF"))
                            } catch (e: Exception) {
                                showInAppViewer = true
                            }
                        } else {
                            showInAppViewer = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("generate_open_pdf_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Generate & Open PDF Document", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val result = com.example.DocumentGenerator.generatePatientTreatmentHistoryReport(
                                context = context,
                                customer = customer,
                                medications = medications,
                                startDateMs = startDateMs,
                                endDateMs = endDateMs
                            )
                            val shareUri = result.pdfUri
                            if (shareUri != null) {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_STREAM, shareUri)
                                    putExtra(Intent.EXTRA_SUBJECT, "Treatment History Report - ${customer.name}")
                                    putExtra(Intent.EXTRA_TEXT, "Attached is the NDPA-compliant patient medication and treatment history dataset PDF report for ${customer.name}.")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Treatment PDF via"))
                            } else {
                                Toast.makeText(context, "Failed to generate report file.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share PDF", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            previewResult = com.example.DocumentGenerator.generatePatientTreatmentHistoryReport(context, customer, medications, startDateMs, endDateMs)
                            showInAppViewer = true
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("In-App View", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Close", color = SlateTextMedium, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = null
    )
}

@Composable
fun InAppPdfPreviewDialog(
    result: com.example.PatientHistoryPdfResult,
    customerName: String,
    context: Context,
    onDismiss: () -> Unit
) {
    val bitmap = remember(result.pngFileName) {
        try {
            if (!result.pngFileName.isNullOrEmpty()) {
                val file = java.io.File(context.filesDir, result.pngFileName)
                if (file.exists()) {
                    android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Patient Report: $customerName", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TealTertiary, fontSize = 14.sp)
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(
                    text = "Scope: ${result.dateFilterLabel} • ${result.totalRecords} Medications logged",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateTextMedium
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (bitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "PDF Document Preview",
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).border(1.dp, SlateBorderLight, RoundedCornerShape(8.dp))
                    )
                } else {
                    Text("PDF Document Ready (${result.pdfFile?.name})")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val shareUri = result.pdfUri
                    if (shareUri != null) {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, shareUri)
                            putExtra(Intent.EXTRA_SUBJECT, "Treatment History - $customerName")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share PDF Document"))
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Share PDF File", fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    )
}
