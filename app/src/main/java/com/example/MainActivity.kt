package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.ui.*
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.*
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import java.util.concurrent.TimeUnit
import com.example.work.CloudSyncWorker

class MainActivity : ComponentActivity() {
    private val viewModel: PharmacyViewModel by viewModels {
        PharmacyViewModel.Factory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val sharedPrefs = getSharedPreferences("careflux_prefs", android.content.Context.MODE_PRIVATE)
        com.example.ui.theme.AppThemeManager.isDark = sharedPrefs.getBoolean("theme_dark", true)
        
        // Schedule secure cloud background synchronization (WorkManager)
        val syncRequest = PeriodicWorkRequestBuilder<CloudSyncWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "cloud_sync_job",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
        
        // Schedule AI Operations Worker
        val aiRequest = PeriodicWorkRequestBuilder<com.example.work.AIOperationsWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "ai_operations_job",
            ExistingPeriodicWorkPolicy.KEEP,
            aiRequest
        )
        
        // Setup initial demo fire so they can see the notification immediately
        val immediateAiRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.work.AIOperationsWorker>().build()
        WorkManager.getInstance(this).enqueueUniqueWork("ai_operations_immediate", androidx.work.ExistingWorkPolicy.KEEP, immediateAiRequest)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        val initialTab = intent.getStringExtra("OPEN_TAB") ?: "inventory"
        
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                var currentUser by remember { mutableStateOf(com.google.firebase.auth.FirebaseAuth.getInstance().currentUser) }
                val isSuspended by viewModel.isSuspended.collectAsStateWithLifecycle()
                
                LaunchedEffect(currentUser) {
                    if (currentUser != null) {
                        viewModel.saveOrUpdateDeviceConfig()
                    }
                }
                
                val isUserAdmin = remember(currentUser) {
                    val email = currentUser?.email?.lowercase() ?: ""
                    email == "maduemeziachinedu6@gmail.com"
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    if (isSuspended && !isUserAdmin) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Block,
                                    contentDescription = "Access Suspended",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(80.dp)
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = "Node Suspension Active",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "This pharmacy node has been suspended by the network administrator due to pending compliance audit, subscription expiry, or operational policy violation. Please contact Careflux headquarters or your local supervisor.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                Spacer(modifier = Modifier.height(32.dp))
                                Button(
                                    onClick = {
                                        viewModel.triggerImmediateSync()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Recheck Node Status")
                                }
                            }
                        }
                    } else {
                        val user = currentUser
                        if (user != null && (user.isEmailVerified || com.example.ui.isGoogleProvider(user))) {
                            PharmacyRootScreen(
                                viewModel = viewModel,
                                initialTab = initialTab,
                                currentUser = user,
                                onSignOut = {
                                    com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                                    currentUser = null
                                },
                                modifier = Modifier.padding(innerPadding)
                            )
                        } else {
                            com.example.ui.AuthScreen(
                                onAuthSuccess = { verifiedUser ->
                                    currentUser = verifiedUser
                                },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PharmacyRootScreen(
    viewModel: PharmacyViewModel,
    initialTab: String = "inventory",
    currentUser: com.google.firebase.auth.FirebaseUser? = null,
    onSignOut: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val drawerState = androidx.compose.material3.rememberDrawerState(androidx.compose.material3.DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val inventory by viewModel.inventoryItems.collectAsStateWithLifecycle()
    val lowStockMeds by viewModel.lowStockItems.collectAsStateWithLifecycle()
    val volumes by viewModel.prescriptionVolumes.collectAsStateWithLifecycle()
    val alerts by viewModel.customerAlerts.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(initialTab) } // inventory, volumes, customers, ai_tasks, receipts

    // Collect new Customer datasets
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    
    val allCustomerMeds by viewModel.customerMedications.collectAsStateWithLifecycle()
    val customerMeds = remember(customers, allCustomerMeds) {
        allCustomerMeds.filter { med -> customers.any { it.id == med.customerId } }
    }
    
    val allInterventions by viewModel.clinicalInterventions.collectAsStateWithLifecycle()
    val clinicalInterventions = remember(customers, allInterventions) {
        allInterventions.filter { item -> customers.any { it.id == item.customerId } }
    }

    val cartItems by viewModel.cart.collectAsStateWithLifecycle()
    val deliveryFeeString by viewModel.deliveryFeeString.collectAsStateWithLifecycle()
    val operationTasks by viewModel.operationTasks.collectAsStateWithLifecycle()
    val receipts by viewModel.receipts.collectAsStateWithLifecycle()
    val triageConditions by viewModel.triageConditions.collectAsStateWithLifecycle()
    val carousels by viewModel.repository.allAICarousels.collectAsStateWithLifecycle(initialValue = emptyList())

    val isAiContentEnabled by viewModel.isAiContentEnabled.collectAsStateWithLifecycle()
    val isCarefluxAiEnabled by viewModel.isCarefluxAiEnabled.collectAsStateWithLifecycle()
    val keyRequests by viewModel.keyRequests.collectAsStateWithLifecycle()

    val isUserAdmin = remember(currentUser) {
        val email = currentUser?.email?.lowercase() ?: ""
        email == "maduemeziachinedu6@gmail.com"
    }

    LaunchedEffect(isCarefluxAiEnabled) {
        if (!isCarefluxAiEnabled && activeTab == "ai_tasks") {
            activeTab = "inventory"
        }
    }
    LaunchedEffect(isAiContentEnabled) {
        if (!isAiContentEnabled && activeTab == "ai_content_engine") {
            activeTab = "inventory"
        }
    }

    // Dialog control states
    var showExportDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showPersonalKeyDialog by remember { mutableStateOf(false) }
    var showAddMedDialog by remember { mutableStateOf(false) }
    var showLogVolumeDialog by remember { mutableStateOf(false) }
    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var selectedCustForEdit by remember { mutableStateOf<Customer?>(null) }
    var selectedCustForMed by remember { mutableStateOf<Customer?>(null) }
    var selectedCustForIntervention by remember { mutableStateOf<Customer?>(null) }

    // State for selected medication to edit stock quantity or details
    var selectedMedForEdit by remember { mutableStateOf<InventoryItem?>(null) }
    var selectedMedForCart by remember { mutableStateOf<InventoryItem?>(null) }

    val productsCsvFilePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            importProductsFromCsv(context, it, viewModel)
        }
    }

    val customersCsvFilePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            importCustomersFromCsv(context, it, viewModel)
        }
    }

    val medicationsCsvFilePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            importMedicationsFromCsv(context, it, viewModel)
        }
    }

    val csvFilePickerLauncher = productsCsvFilePickerLauncher

    androidx.compose.material3.ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            androidx.compose.material3.ModalDrawerSheet(
                modifier = Modifier.systemBarsPadding()
            ) {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(TealPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MedicalServices,
                            contentDescription = "Careflux Logo",
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Careflux Menu", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                androidx.compose.material3.HorizontalDivider()
                androidx.compose.material3.NavigationDrawerItem(
                    label = { Text("Procurement List") },
                    selected = activeTab == "procurement",
                    onClick = {
                        activeTab = "procurement"
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Filled.ShoppingCart, contentDescription = null) },
                    modifier = Modifier.padding(androidx.compose.material3.NavigationDrawerItemDefaults.ItemPadding)
                )
                androidx.compose.material3.NavigationDrawerItem(
                    label = { Text("Analytics Dashboard") },
                    selected = activeTab == "analytics",
                    onClick = {
                        activeTab = "analytics"
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Filled.Insights, contentDescription = null) },
                    modifier = Modifier.padding(androidx.compose.material3.NavigationDrawerItemDefaults.ItemPadding)
                )
                androidx.compose.material3.NavigationDrawerItem(
                    label = { Text("WhatsApp Templates") },
                    selected = activeTab == "whatsapp_templates",
                    onClick = {
                        activeTab = "whatsapp_templates"
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Filled.Message, contentDescription = null) },
                    modifier = Modifier.padding(androidx.compose.material3.NavigationDrawerItemDefaults.ItemPadding)
                )
                if (isAiContentEnabled) {
                    androidx.compose.material3.NavigationDrawerItem(
                        label = { Text("AI Content Engine") },
                        selected = activeTab == "ai_content_engine",
                        onClick = {
                            activeTab = "ai_content_engine"
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
                        modifier = Modifier.padding(androidx.compose.material3.NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
                androidx.compose.material3.NavigationDrawerItem(
                    label = { Text("Pharmacy Triage") },
                    selected = activeTab == "pharmacy_triage",
                    onClick = {
                        activeTab = "pharmacy_triage"
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Filled.ContentPasteSearch, contentDescription = null) },
                    modifier = Modifier.padding(androidx.compose.material3.NavigationDrawerItemDefaults.ItemPadding)
                )
                androidx.compose.material3.NavigationDrawerItem(
                    label = { Text("Expiry Rescue Marketplace") },
                    selected = activeTab == "rescue_marketplace",
                    onClick = {
                        activeTab = "rescue_marketplace"
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Filled.Storefront, contentDescription = null) },
                    modifier = Modifier.padding(androidx.compose.material3.NavigationDrawerItemDefaults.ItemPadding)
                )

                if (isUserAdmin) {
                    androidx.compose.material3.NavigationDrawerItem(
                        label = { Text("Control Room (Admin)", color = TealPrimary, fontWeight = FontWeight.Bold) },
                        selected = activeTab == "admin_dashboard",
                        onClick = {
                            activeTab = "admin_dashboard"
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Filled.AdminPanelSettings, contentDescription = "Admin Dashboard", tint = TealPrimary) },
                        modifier = Modifier.padding(androidx.compose.material3.NavigationDrawerItemDefaults.ItemPadding)
                    )
                }

                if (!isUserAdmin) {
                    androidx.compose.material3.NavigationDrawerItem(
                        label = { Text("Personal Gemini Key") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            showPersonalKeyDialog = true
                        },
                        icon = { Icon(Icons.Filled.Key, contentDescription = "Personal Gemini Key") },
                        modifier = Modifier.padding(androidx.compose.material3.NavigationDrawerItemDefaults.ItemPadding)
                    )
                }



                androidx.compose.material3.NavigationDrawerItem(
                    label = { Text("Sign Out", color = MaterialTheme.colorScheme.error) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onSignOut()
                    },
                    icon = { Icon(Icons.Filled.ExitToApp, contentDescription = "Sign Out", tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.padding(androidx.compose.material3.NavigationDrawerItemDefaults.ItemPadding)
                )

                Spacer(modifier = Modifier.weight(1f))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp, start = 24.dp, end = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))
                    Text(
                        text = "Careflux is a product of Wellivox",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "© 2026 Wellivox. All rights reserved.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // --- Custom App Header ---
            HeaderSection(
                cartCount = cartItems.sumOf { it.quantity },
                isAdmin = isUserAdmin,
                onMenuClick = { scope.launch { drawerState.open() } },
                onExportClick = {
                    showExportDialog = true
                },
                onReceiptsClick = {
                    activeTab = "receipts"
                },
                onCartClick = {
                    activeTab = "cart"
                },
                onSettingsClick = {
                    showSettingsDialog = true
                }
            )

        // --- Settings Dialog ---
        if (showSettingsDialog) {
            var tempApiKey by remember { mutableStateOf(viewModel.getApiKey()) }
            var tempTermiiApiKey by remember { mutableStateOf(viewModel.getTermiiApiKey()) }
            var tempTermiiSenderId by remember { mutableStateOf(viewModel.getTermiiSenderId()) }
            var tempPharmacyName by remember { mutableStateOf(viewModel.getPharmacyName()) }
            var tempPharmacyLga by remember { mutableStateOf(viewModel.getPharmacyLga()) }
            var tempPharmacyState by remember { mutableStateOf(viewModel.getPharmacyState()) }
            var showClearDbDialog by remember { mutableStateOf(false) }

            if (showClearDbDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showClearDbDialog = false },
                    title = { Text("Clear All Data") },
                    text = { Text("Are you sure you want to delete all database items? This cannot be undone.") },
                    confirmButton = {
                        TextButton(
                            onClick = { 
                                viewModel.clearAllData()
                                showClearDbDialog = false 
                                showSettingsDialog = false
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Clear Data")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearDbDialog = false }) { Text("Cancel") }
                    }
                )
            } else {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showSettingsDialog = false },
                    title = { Text("App Settings") },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text("Cooperative Node Profile", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text("Pharmacy Name", style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = tempPharmacyName,
                                onValueChange = { tempPharmacyName = it },
                                placeholder = { Text("e.g. Wellivox Health Palace") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("LGA Location", style = MaterialTheme.typography.labelMedium)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = tempPharmacyLga,
                                        onValueChange = { tempPharmacyLga = it },
                                        placeholder = { Text("e.g. Ikeja") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Regional State", style = MaterialTheme.typography.labelMedium)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = tempPharmacyState,
                                        onValueChange = { tempPharmacyState = it },
                                        placeholder = { Text("e.g. Lagos") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(14.dp))

                            if (isUserAdmin) {
                                Text("Gemini API Key", style = MaterialTheme.typography.labelMedium)
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = tempApiKey,
                                    onValueChange = { tempApiKey = it },
                                    placeholder = { Text("Paste your API Key here") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Adding custom keys bypasses shared quotas and avoids HTTP 429 exceptions.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Termii SMS API Key", style = MaterialTheme.typography.labelMedium)
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = tempTermiiApiKey,
                                    onValueChange = { tempTermiiApiKey = it },
                                    placeholder = { Text("At_...") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Termii Registered Sender ID", style = MaterialTheme.typography.labelMedium)
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = tempTermiiSenderId,
                                    onValueChange = { tempTermiiSenderId = it },
                                    placeholder = { Text("e.g. N-Alert") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Requires a verified Termii.com sender; unregistered values will fail delivery.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Dedicated Node Credentials", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TealPrimary)
                                        
                                        val myRequest = keyRequests.find { (it["deviceId"] as? String) == viewModel.deviceId }
                                        if (myRequest == null) {
                                            Text(
                                                text = "This node is currently running on the shared clinical cooperative resource pool. For dedicated API quotas and guaranteed delivery services, you can request a personal API suite.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Button(
                                                onClick = { viewModel.submitKeyRequest() },
                                                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("Submit Request for Personal Keys", color = Color.Black, fontWeight = FontWeight.Bold)
                                            }
                                        } else {
                                            val status = myRequest["status"] as? String ?: "PENDING"
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text("Request Status:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                                val statusColor = when (status) {
                                                    "APPROVED" -> Color(0xFF4CAF50)
                                                    "REJECTED" -> MaterialTheme.colorScheme.error
                                                    else -> Color(0xFFFF9800)
                                                }
                                                Text(status.uppercase(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = statusColor)
                                            }
                                            
                                            if (status == "PENDING") {
                                                Text(
                                                    text = "Your request for a personal Gemini & Termii suite is submitted and pending review by administrative compliance officers.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            } else if (status == "APPROVED") {
                                                Text(
                                                    text = "✓ Dedicated API suite successfully provisioned and active on this node. Shared database queries and messaging tasks are now running on personal isolated keys.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color(0xFF4CAF50),
                                                    fontWeight = FontWeight.Medium
                                                )
                                            } else if (status == "REJECTED") {
                                                Text(
                                                    text = "Your request was declined. Please verify your node registration or reach out to Wellivox administration.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Button(
                                                    onClick = { viewModel.submitKeyRequest() },
                                                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text("Re-submit Key Request", color = Color.Black, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Corporate Documentation", style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Careflux is an enterprise clinical and logistics application operated as a registered product under Wellivox.\nFor system operations, support, and compliance inquiries, contact Wellivox administrative offices.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isUserAdmin) {
                                Spacer(modifier = Modifier.height(16.dp))
                                HorizontalDivider()
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Data Management", style = MaterialTheme.typography.labelMedium)
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { showClearDbDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Clear Entire Database")
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.setApiKey(tempApiKey)
                            viewModel.setTermiiApiKey(tempTermiiApiKey)
                            viewModel.setTermiiSenderId(tempTermiiSenderId)
                            viewModel.setPharmacyName(tempPharmacyName)
                            viewModel.setPharmacyLga(tempPharmacyLga)
                            viewModel.setPharmacyState(tempPharmacyState)
                            showSettingsDialog = false
                        }) {
                            Text("Save")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSettingsDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }

        if (showPersonalKeyDialog) {
            var tempPersonalApiKey by remember { mutableStateOf(viewModel.getApiKey()) }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showPersonalKeyDialog = false },
                title = { Text("Personal Gemini Key", fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Configure your own personal Gemini API Key for Careflux's clinical AI assistants. This bypasses system shared quotas and avoids error rates.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Personal API Key", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = tempPersonalApiKey,
                            onValueChange = { tempPersonalApiKey = it },
                            placeholder = { Text("Paste your API Key here") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "This key is saved locally to your device and is never synchronized to administrative database dashboards.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.setApiKey(tempPersonalApiKey)
                        showPersonalKeyDialog = false
                    }) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPersonalKeyDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showExportDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showExportDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.List,
                            contentDescription = "Data Hub icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Import / Export Data Hub",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                text = {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Text(
                                text = "Restore blank systems or back up clinical datasets, patient profiles, and educational drafts individually using standardized, Excel-compatible CSV files.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SlateTextMedium,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }

                        // 1. Stock / Products
                        item {
                            DataHubOptionItem(
                                title = "Products & Stock Levels",
                                detail = "${inventory.size} items registered",
                                icon = Icons.Filled.Inventory,
                                onExport = {
                                    val csv = generateProductsCsv(inventory)
                                    shareCsvFile(context, csv, "Products_Stock_${System.currentTimeMillis()}.csv")
                                },
                                onImport = {
                                    productsCsvFilePickerLauncher.launch("*/*")
                                }
                            )
                        }

                        // 2. Customers
                        item {
                            DataHubOptionItem(
                                title = "Customers & Patient Profiles",
                                detail = "${customers.size} profiles registered",
                                icon = Icons.Filled.PeopleAlt,
                                onExport = {
                                    val csv = generateCustomersCsv(customers)
                                    shareCsvFile(context, csv, "Customers_List_${System.currentTimeMillis()}.csv")
                                },
                                onImport = {
                                    customersCsvFilePickerLauncher.launch("*/*")
                                }
                            )
                        }

                        // 3. Customer Medications/Prescriptions
                        item {
                            DataHubOptionItem(
                                title = "Active Meds & Prescriptions",
                                detail = "${customerMeds.size} refills scheduled",
                                icon = Icons.Filled.EventRepeat,
                                onExport = {
                                    val csv = generatePrescriptionsCsv(customerMeds)
                                    shareCsvFile(context, csv, "Active_Prescriptions_${System.currentTimeMillis()}.csv")
                                },
                                onImport = {
                                    medicationsCsvFilePickerLauncher.launch("*/*")
                                }
                            )
                        }

                        // 4. Educational Carousel
                        item {
                            DataHubOptionItem(
                                title = "Educational Carousels",
                                detail = "${carousels.size} carousels saved",
                                icon = Icons.Filled.AutoAwesome,
                                onExport = {
                                    val csv = generateCarouselCsv(carousels)
                                    shareCsvFile(context, csv, "Educational_Carousels_${System.currentTimeMillis()}.csv")
                                }
                            )
                        }

                        // 5. Pharmacy Triage
                        item {
                            DataHubOptionItem(
                                title = "Pharmacy Triage & Protocols",
                                detail = "${triageConditions.size} clinical conditions",
                                icon = Icons.Filled.ContentPasteSearch,
                                onExport = {
                                    val csv = generateTriageCsv(triageConditions)
                                    shareCsvFile(context, csv, "Pharmacy_Triage_Protocols_${System.currentTimeMillis()}.csv")
                                }
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            HorizontalDivider()
                        }

                        // Consolidated / Legacy Ledger
                        item {
                            DataHubOptionItem(
                                title = "Consolidated Careflux Ledger",
                                detail = "Combined legacy inventory & refill report",
                                icon = Icons.Filled.List,
                                onExport = {
                                    val csv = generateInventoryCsv(inventory, customerMeds)
                                    shareCsvFile(context, csv, "Careflux_Ledger_${System.currentTimeMillis()}.csv")
                                }
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showExportDialog = false }) {
                        Text("Done", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // --- Low Stock Broadcaster ---
        val isCoreTab = activeTab in listOf("inventory", "volumes", "customers", "ai_tasks")
        if (isCoreTab && lowStockMeds.isNotEmpty()) {
            LowStockBanner(
                lowStockCount = lowStockMeds.size,
                onClick = { activeTab = "inventory" }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // --- Quick Stats Overview Widgets ---
        if (isCoreTab) {
            val pendingRefills = customerMeds.count { it.nextRefillDate < System.currentTimeMillis() + (7L * 24 * 60 * 60 * 1000) }
            StatsSection(
                medsCount = inventory.size,
                lowStockCount = lowStockMeds.size,
                todayVolume = volumes.firstOrNull()?.volume ?: 0,
                pendingAlerts = pendingRefills
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // --- Dynamic Content Zone ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (activeTab) {
                "inventory" -> InventoryTabContent(
                    inventory = inventory,
                    onMedSelect = { selectedMedForEdit = it },
                    onAddNewClick = { showAddMedDialog = true },
                    onDeleteClick = { viewModel.deleteInventory(it) },
                    onIncrementClick = { item -> 
                        viewModel.updateStockLevel(item, item.stockQuantity + 10)
                        Toast.makeText(context, "Added 10 units to ${item.name}", Toast.LENGTH_SHORT).show()
                    },
                    onAddToCart = { selectedMedForCart = it }
                )
                "volumes" -> VolumesTabContent(
                    volumes = volumes,
                    onLogVolumeClick = { showLogVolumeDialog = true },
                    onDeleteVolume = { viewModel.deletePrescriptionVolume(it) }
                )
                "customers" -> CustomersTabContent(
                    customers = customers,
                    customerMeds = customerMeds,
                    inventoryMeds = inventory,
                    clinicalInterventions = clinicalInterventions,
                    onAddNewCustomerClick = { showAddCustomerDialog = true },
                    onEditCustomerClick = { selectedCustForEdit = it },
                    onDeleteCustomer = { viewModel.deleteCustomer(it) },
                    onAddPrescriptionClick = { selectedCustForMed = it },
                    onDeletePrescription = { viewModel.deleteCustomerMedication(it) },
                    onAddInterventionClick = { selectedCustForIntervention = it },
                    viewModel = viewModel,
                    context = context
                )
                "procurement" -> com.example.ui.ProcurementTabContent(
                    inventory = inventory
                )
                "ai_tasks" -> com.example.ui.CarefluxAITab(
                    inventory = inventory,
                    customers = customers,
                    meds = customerMeds,
                    volumes = volumes,
                    operationTasks = operationTasks,
                    viewModel = viewModel
                )
                "whatsapp_templates" -> WhatsAppTemplatesTabContent(viewModel = viewModel)
                "cart" -> CartTabContent(
                    cartItems = cartItems,
                    deliveryFeeString = deliveryFeeString,
                    customers = customers,
                    onDeliveryFeeChange = { viewModel.setDeliveryFee(it) },
                    onRemoveItem = { viewModel.removeFromCart(it) },
                    onNeedRefillChange = { id, need -> viewModel.updateCartItemNeedsRefill(id, need) },
                    onCheckout = { customer, total, fileName, isInvoice, status ->
                        if (fileName.isNotEmpty()) {
                            viewModel.addReceipt(customer?.name ?: "Guest", total, fileName, isInvoice, status)
                        }
                        if (customer != null) {
                            // Assign everything to customer!
                            for (item in cartItems) {
                                if (item.needsRefill) {
                                    viewModel.addCustomerMedication(
                                        customerId = customer.id,
                                        invItemId = item.inventoryItem.id,
                                        medName = item.inventoryItem.name,
                                        customDosage = item.inventoryItem.dosage + " (Qty: ${item.quantity})",
                                        cost = item.inventoryItem.price * item.quantity,
                                        cycleDays = 0,
                                        nextRefill = System.currentTimeMillis()
                                    )
                                }
                            }
                            Toast.makeText(context, "Saved items to ${customer.name}", Toast.LENGTH_SHORT).show()
                            if (customer.phoneNumber.isNotEmpty()) {
                                val itemsSummary = cartItems.joinToString(", ") { "${it.inventoryItem.name} (x${it.quantity})" }
                                scope.launch {
                                    val success = viewModel.sendTermiiDispenseConfirmationSms(
                                        patientName = customer.name,
                                        phone = customer.phoneNumber,
                                        itemsSummary = itemsSummary,
                                        amount = total
                                    )
                                    if (success) {
                                        Toast.makeText(context, "Dispense Receipt SMS dispatched via Termii!", Toast.LENGTH_LONG).show()
                                    } else {
                                        android.util.Log.e("DispenseNotice", "Termii receipt SMS dispatch failed or API not configured.")
                                    }
                                }
                            }
                        }
                        // Update stock and stats!
                        for (item in cartItems) {
                            val inv = item.inventoryItem
                            viewModel.recordMedicationSale(item, customer)
                            viewModel.addOrUpdateInventory(
                                id = inv.id,
                                name = inv.name,
                                dosage = inv.dosage,
                                currentStock = (inv.stockQuantity - item.quantity).coerceAtLeast(0),
                                minStock = inv.minRequiredStock,
                                category = inv.category,
                                price = inv.price,
                                updateStockStats = true,
                                addedQty = item.quantity
                            )
                        }
                        viewModel.clearCart()
                        activeTab = "inventory"
                    },
                    context = context
                )
                "receipts" -> com.example.ui.ReceiptsTab(
                    receipts = receipts,
                    context = context,
                    onDeleteReceipt = { viewModel.deleteReceipt(it) },
                    onUpdateReceiptStatus = { receipt, newStatus -> viewModel.updateReceipt(receipt.copy(paymentStatus = newStatus)) }
                )
                "analytics" -> com.example.ui.AnalyticsTab(viewModel = viewModel, isUserAdmin = isUserAdmin)
                "ai_content_engine" -> {
                    val aiViewModel: com.example.ui.AIContentEngineViewModel = androidx.lifecycle.viewmodel.compose.viewModel {
                        com.example.ui.AIContentEngineViewModel(viewModel.repository)
                    }
                    com.example.ui.AIContentEngineTab(viewModel = aiViewModel)
                }
                "pharmacy_triage" -> com.example.ui.PharmacyTriageTabContent(viewModel = viewModel)
                "admin_dashboard" -> com.example.ui.AdminDashboardScreen(viewModel = viewModel)
                "rescue_marketplace" -> com.example.ui.RescueMarketplaceScreen(viewModel = viewModel)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Tab Selection Pills (Satisfying single-view tabless layout) ---
        TabSelector(
            activeTab = activeTab,
            onTabSelected = { activeTab = it },
            cartCount = cartItems.sumOf { it.quantity },
            isCarefluxAiEnabled = isCarefluxAiEnabled
        )
        
        Spacer(modifier = Modifier.height(16.dp))
    }
    } // End of ModalNavigationDrawer

    // --- Dialogs ---

    // 1. Add/Edit Medicine Dialog
    if (showAddMedDialog) {
        AddEditMedDialog(
            onDismiss = { showAddMedDialog = false },
            onConfirm = { name, dosage, stock, minStock, category, price, expiryDate, batch, supplier, imageUri, unitForm, brand ->
                viewModel.addOrUpdateInventory(name = name, dosage = dosage, currentStock = stock, minStock = minStock, category = category, price = price, expiryDate = expiryDate, batchNumber = batch, supplier = supplier, imageUri = imageUri, unitForm = unitForm, brand = brand)
                showAddMedDialog = false
            }
        )
    }

    // Add to Cart Dialog
    selectedMedForCart?.let { item ->
        AddToCartDialog(
            item = item,
            inventory = inventory,
            onDismiss = { selectedMedForCart = null },
            onConfirm = { confirmedItem, quantity ->
                viewModel.addToCart(confirmedItem, quantity)
                Toast.makeText(context, "Added $quantity ${confirmedItem.name} to Cart", Toast.LENGTH_SHORT).show()
                selectedMedForCart = null
            }
        )
    }

    // Edit Specific Stock Level Quick Sheet Dialog
    selectedMedForEdit?.let { item ->
        EditStockQuantityDialog(
            item = item,
            onDismiss = { selectedMedForEdit = null },
            onConfirm = { name, dosage, stock, minStock, category, price, expiryDate, batch, supplier, imageUri, unitForm, brand ->
                viewModel.addOrUpdateInventory(name = name, dosage = dosage, currentStock = stock, minStock = minStock, category = category, price = price, id = item.id, expiryDate = expiryDate, batchNumber = batch, supplier = supplier, imageUri = imageUri, unitForm = unitForm, brand = brand)
                selectedMedForEdit = null
            }
        )
    }

    // 2. Log Daily Volume Dialog
    if (showLogVolumeDialog) {
        LogVolumeDialog(
            onDismiss = { showLogVolumeDialog = false },
            onConfirm = { date, count, notes ->
                viewModel.logPrescriptionVolume(date, count, notes)
                showLogVolumeDialog = false
            }
        )
    }

    // 3. Add Customer Dialog
    if (showAddCustomerDialog) {
        AddCustomerDialog(
            onDismiss = { showAddCustomerDialog = false },
            onConfirm = { name, phone, email, notes, age, gender, state, lga, city ->
                viewModel.addCustomer(name, phone, email, notes, age, gender, state, lga, city)
                showAddCustomerDialog = false
            }
        )
    }

    if (selectedCustForEdit != null) {
        EditCustomerDialog(
            customer = selectedCustForEdit!!,
            onDismiss = { selectedCustForEdit = null },
            onConfirm = { updatedCustomer ->
                viewModel.updateCustomer(updatedCustomer)
                selectedCustForEdit = null
            }
        )
    }

    // 4. Add Prescription Dialog
    if (selectedCustForMed != null) {
        val selectedCustMeds = remember(selectedCustForMed, allCustomerMeds) {
            allCustomerMeds.filter { it.customerId == selectedCustForMed!!.id }
        }
        AddPrescriptionDialog(
            customer = selectedCustForMed!!,
            inventoryMeds = inventory,
            currentMeds = selectedCustMeds,
            onDismiss = { selectedCustForMed = null },
            onConfirm = { cId, iId, mName, dose, cost, days, nextMs ->
                viewModel.addCustomerMedication(cId, iId, mName, dose, cost, days, nextMs)
                selectedCustForMed = null
            }
        )
    }

    // 5. Add Intervention Dialog
    if (selectedCustForIntervention != null) {
        AddInterventionDialog(
            customer = selectedCustForIntervention!!,
            onDismiss = { selectedCustForIntervention = null },
            onConfirm = { presentation, testResults, recommendation ->
                viewModel.addClinicalIntervention(selectedCustForIntervention!!.id, presentation, testResults, recommendation)
                selectedCustForIntervention = null
            }
        )
    }
}

// ==========================================
// COMPONENT: Header
// ==========================================
@Composable
fun HeaderSection(
    cartCount: Int,
    isAdmin: Boolean,
    onMenuClick: () -> Unit,
    onExportClick: () -> Unit,
    onReceiptsClick: () -> Unit,
    onCartClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val todayDate = remember {
        val sdf = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault())
        sdf.format(Date())
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Filled.Menu, contentDescription = "Menu")
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(
                    text = "Careflux",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "Central Pharmacy Unit",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateTextMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Action Buttons on Right
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clickable { onCartClick() },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                )
                Icon(
                    imageVector = Icons.Filled.ShoppingCart,
                    contentDescription = "Cart",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                if (cartCount > 0) {
                    androidx.compose.material3.Badge(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 2.dp, y = (-2).dp),
                        containerColor = Color.Red,
                        contentColor = Color.White
                    ) {
                        Text(
                            text = cartCount.toString(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .clickable { onReceiptsClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Receipt,
                    contentDescription = "Receipts",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .clickable { onExportClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = "Export Data",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .clickable { onSettingsClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Theme Toggle Button
            val context = LocalContext.current
            val isDarkTheme = com.example.ui.theme.AppThemeManager.isDark
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .clickable {
                        val nextMode = !isDarkTheme
                        com.example.ui.theme.AppThemeManager.isDark = nextMode
                        val prefs = context.getSharedPreferences("careflux_prefs", android.content.Context.MODE_PRIVATE)
                        prefs.edit().putBoolean("theme_dark", nextMode).apply()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isDarkTheme) Icons.Filled.WbSunny else Icons.Filled.Nightlight,
                    contentDescription = "Toggle Theme",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ==========================================
// COMPONENT: Low Stock Critical Banner
// ==========================================
@Composable
fun LowStockBanner(
    lowStockCount: Int,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = WarningRedContainerSoft
        ),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = WarningRedContainer,
                shape = RoundedCornerShape(24.dp)
            )
            .clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(WarningRed),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = "Alert logo",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Critical Inventory Warning",
                    style = MaterialTheme.typography.titleSmall,
                    color = WarningRed,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$lowStockCount medication line(s) have dropped below the required threshold.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarningRedTitle
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "Navigate to stock",
                tint = WarningRed
            )
        }
    }
}

// ==========================================
// COMPONENT: Metric Quick Stats Boxes
// ==========================================
@Composable
fun StatsSection(
    medsCount: Int,
    lowStockCount: Int,
    todayVolume: Int,
    pendingAlerts: Int
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        StatBox(
            title = "Tracked",
            value = medsCount.toString(),
            icon = Icons.Filled.HealthAndSafety,
            color = TealPrimary,
            bgColor = TealSurface,
            borderColor = SlateBorderLight,
            textColor = TealPrimary,
            subTextColor = SlateTextMedium,
            modifier = Modifier.weight(1f)
        )
        StatBox(
            title = "Low Stock",
            value = lowStockCount.toString(),
            icon = Icons.Filled.ReportGmailerrorred,
            color = WarningRed,
            bgColor = if (lowStockCount > 0) WarningRedContainerSoft else TealSurface,
            borderColor = if (lowStockCount > 0) WarningRedContainer else SlateBorderLight,
            textColor = if (lowStockCount > 0) WarningRed else TealPrimary,
            subTextColor = if (lowStockCount > 0) WarningRedTitle else SlateTextMedium,
            highlight = lowStockCount > 0,
            modifier = Modifier.weight(1f)
        )
        StatBox(
            title = "Today's Rx",
            value = todayVolume.toString(),
            icon = Icons.Filled.ReceiptLong,
            color = TealPrimary,
            bgColor = OKGreenContainer,
            borderColor = Color(0xFFB0DEDE),
            textColor = TealPrimary,
            subTextColor = OKGreenText,
            modifier = Modifier.weight(1f)
        )
        StatBox(
            title = "Refills Due",
            value = pendingAlerts.toString(),
            icon = Icons.Filled.SmsFailed,
            color = PendingOrange,
            bgColor = PendingOrangeContainer,
            borderColor = PendingOrangeBorder,
            textColor = PendingOrange,
            subTextColor = PendingOrange,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun StatBox(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    bgColor: Color,
    borderColor: Color,
    textColor: Color,
    subTextColor: Color,
    highlight: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp), // Spec rounded-[28px] matching, 24dp fits perfectly on mobile
        colors = CardDefaults.cardColors(
            containerColor = bgColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = textColor
            )
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = subTextColor,
                fontWeight = FontWeight.Bold,
                fontSize = 8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                letterSpacing = 0.5.sp
            )
        }
    }
}

// ==========================================
// COMPONENT: Pill Tab selector
// ==========================================
@Composable
fun TabSelector(
    activeTab: String,
    onTabSelected: (String) -> Unit,
    cartCount: Int = 0,
    isCarefluxAiEnabled: Boolean = true
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = TealSurface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .border(
                width = 1.dp,
                color = SlateBorderLight,
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TabButton(
                title = "Stock",
                icon = Icons.Filled.Inventory,
                isActive = activeTab == "inventory",
                onClick = { onTabSelected("inventory") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("tab_inventory")
            )
            TabButton(
                title = "Rx",
                icon = Icons.Filled.BarChart,
                isActive = activeTab == "volumes",
                onClick = { onTabSelected("volumes") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("tab_volumes")
            )
            TabButton(
                title = "Customers",
                icon = Icons.Filled.PeopleAlt,
                isActive = activeTab == "customers",
                onClick = { onTabSelected("customers") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("tab_customers")
            )
            if (isCarefluxAiEnabled) {
                TabButton(
                    title = "AI Tasks",
                    icon = Icons.Filled.Assistant,
                    isActive = activeTab == "ai_tasks",
                    onClick = { onTabSelected("ai_tasks") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tab_ai")
                )
            }
        }
    }
}

@Composable
fun TabButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0
) {
    val containerColor = if (isActive) TealSecondary else Color.Transparent
    val contentColor = if (isActive) TealTertiary else SlateTextMedium

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (badgeCount > 0) {
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(com.example.ui.theme.WarningRed),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badgeCount.toString(),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ==========================================
// SCREEN COMS: Tab 1 - Inventory
// ==========================================
@Composable
fun InventoryTabContent(
    inventory: List<InventoryItem>,
    onMedSelect: (InventoryItem) -> Unit,
    onAddNewClick: () -> Unit,
    onDeleteClick: (InventoryItem) -> Unit,
    onIncrementClick: (InventoryItem) -> Unit,
    onAddToCart: (InventoryItem) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterCategory by remember { mutableStateOf("All") }
    var showOnlyLowStock by remember { mutableStateOf(false) }

    val categories = remember(inventory) {
        listOf("All") + inventory.map { it.category }.distinct().sorted()
    }

    val filteredList = remember(inventory, searchQuery, filterCategory, showOnlyLowStock) {
        inventory.filter { item ->
            val matchesSearch = item.name.contains(searchQuery, ignoreCase = true) ||
                    item.dosage.contains(searchQuery, ignoreCase = true)
            val matchesCategory = filterCategory == "All" || item.category == filterCategory
            val matchesLowStock = !showOnlyLowStock || item.isLowStock
            matchesSearch && matchesCategory && matchesLowStock
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search meds...", fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search", modifier = Modifier.size(18.dp)) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("med_search_input")
            )

            // Add Med FAB icon button
            IconButton(
                onClick = onAddNewClick,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .testTag("add_item_button")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add New Medicine")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Filters Pill Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category scroll dropdown simulation or label
            Text(
                text = "Filter:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            // Category select scroll row
            CustomScrollableFilterRow(
                categories = categories,
                selectedCategory = filterCategory,
                onSelected = { filterCategory = it }
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Checkbox filter low stock list
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable { showOnlyLowStock = !showOnlyLowStock }
                .padding(vertical = 4.dp)
        ) {
            Checkbox(
                checked = showOnlyLowStock,
                onCheckedChange = { showOnlyLowStock = it },
                colors = CheckboxDefaults.colors(checkedColor = WarningRed)
            )
            Text(
                text = "Show Only Low Stock Warnings",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = if (showOnlyLowStock) WarningRed else MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (filteredList.isEmpty()) {
            EmptyStatePlaceholder(
                message = "No matching medicines in stock logs.",
                tip = "Tap the + button to catalog a new medication line."
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredList) { item ->
                    InventoryCard(
                        item = item,
                        onClick = { onMedSelect(item) },
                        onDelete = { onDeleteClick(item) },
                        onIncrementTen = { onIncrementClick(item) },
                        onAddToCart = { onAddToCart(item) }
                    )
                }
            }
        }
    }
}

@Composable
fun InventoryCard(
    item: InventoryItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onIncrementTen: () -> Unit,
    onAddToCart: () -> Unit
) {
    val containerBgColor = if (item.isLowStock) WarningRedContainerSoft else TealSurface
    val cardBorderColor = if (item.isLowStock) WarningRedContainer else SlateBorderLight
    val textAmtColor = if (item.isLowStock) WarningRed else OKGreen

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerBgColor
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = cardBorderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                // Image Box
                if (item.imageUri != null) {
                    coil.compose.AsyncImage(
                        model = item.imageUri,
                        contentDescription = "Medicine Image",
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocalPharmacy,
                            contentDescription = "Medicine",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Info Column
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Row {
                            IconButton(onClick = onAddToCart, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.AddShoppingCart, "Cart", tint = TealPrimary, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Delete, "Delete", tint = SlateTextMedium, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    if (item.brand.isNotBlank()) {
                        Text(
                            text = item.brand,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        SuggestionChip(
                            onClick = { },
                            label = { Text(item.dosage, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
                            modifier = Modifier.height(22.dp)
                        )
                        if (item.unitForm.isNotBlank()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            SuggestionChip(
                                onClick = { },
                                label = { Text(item.unitForm, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
                                modifier = Modifier.height(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = item.category, style = MaterialTheme.typography.labelMedium, color = SlateTextMedium)
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "₦${"%,.2f".format(item.price)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Footer for stock logic
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Stock: ", style = MaterialTheme.typography.bodyMedium, color = SlateTextMedium)
                        Text("${item.stockQuantity} units", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, color = textAmtColor)
                    }
                    Text("Min threshold: ${item.minRequiredStock}", style = MaterialTheme.typography.labelSmall, color = SlateTextMedium.copy(alpha = 0.8f))
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.isLowStock) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(WarningRedContainer)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("LOW STOCK", style = MaterialTheme.typography.labelSmall, color = WarningRed, fontWeight = FontWeight.Bold)
                        }
                    }
                    TextButton(
                        onClick = onIncrementTen,
                        colors = ButtonDefaults.textButtonColors(containerColor = TealSecondary.copy(alpha = 0.6f), contentColor = TealPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add 10", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun CustomScrollableFilterRow(
    categories: List<String>,
    selectedCategory: String,
    onSelected: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(categories) { cat ->
                val isSelected = cat == selectedCategory
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) TealPrimary 
                            else SlateBackgroundLight
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) Color.Transparent else SlateBorderLight,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelected(cat) }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = cat,
                        color = if (isSelected) Color.White 
                                else SlateTextMedium,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

// ==========================================
// SCREEN COMS: Tab 2 - Daily Volume Logs
// ==========================================
@Composable
fun VolumesTabContent(
    volumes: List<DailyPrescriptionVolume>,
    onLogVolumeClick: () -> Unit,
    onDeleteVolume: (DailyPrescriptionVolume) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = "Prescription Volumes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Track medication fills & efficiency curves.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Button(
                onClick = onLogVolumeClick,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("add_volume_button")
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Log")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Custom Native Compose Chart (No heavy/broken dynamic libraries)
        if (volumes.isNotEmpty()) {
            CustomPrescriptionVolumeChart(volumes = volumes)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Workflow History",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.outline,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (volumes.isEmpty()) {
            EmptyStatePlaceholder(
                message = "Workflow volume logs are blank.",
                tip = "Tap 'Log' above to add statistics for a daily shift."
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(volumes) { log ->
                    VolumeLogCard(log = log, onDelete = { onDeleteVolume(log) })
                }
            }
        }
    }
}

@Composable
fun CustomPrescriptionVolumeChart(volumes: List<DailyPrescriptionVolume>) {
    // Select up to last 7 days to display in standard bar chart format
    val chartData = remember(volumes) {
        volumes.take(7).reversed()
    }
    val maxVolume = remember(chartData) {
        val max = chartData.maxOfOrNull { it.volume } ?: 100
        if (max == 0) 100 else max
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Text(
                text = "Prescription Filling Curves (7-Day Log)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Graph Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                chartData.forEach { data ->
                    // Calculate relative height bar percentage
                    val fillPercent = (data.volume.toFloat() / maxVolume.toFloat()).coerceIn(0.1f, 1f)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = data.volume.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 10.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxHeight(fillPercent * 0.75f)
                                .width(20.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    color = if (data.volume > maxVolume * 0.75) OKGreen else MaterialTheme.colorScheme.primary
                                )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // Extract just last MM-DD digits for cleaner representation
                        val displayDate = try {
                            val parts = data.dateString.split("-")
                            if (parts.size >= 3) "${parts[1]}/${parts[2]}" else data.dateString
                        } catch (e: Exception) {
                            data.dateString
                        }

                        Text(
                            text = displayDate,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 8.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VolumeLogCard(
    log: DailyPrescriptionVolume,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = TealSurface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = SlateBorderLight,
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(OKGreenContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.TrendingUp,
                    contentDescription = null,
                    tint = TealPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.dateString,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (log.notes.isNotEmpty()) {
                    Text(
                        text = log.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateTextMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Text(
                text = "${log.volume} Rx",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = TealTertiary
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete log entry",
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// Legacy Alerts Tab Content removed

// ==========================================
// COMPONENT: Empty States
// ==========================================
@Composable
fun EmptyStatePlaceholder(
    message: String,
    tip: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Inbox,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = tip,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )
    }
}

// ==========================================
// UTILITY: WhatsApp Send Router
// ==========================================
fun sendWhatsAppMessage(context: Context, phoneNumber: String, message: String) {
    // Filter just digits and leading + sign for safe routing
    val formattedNo = phoneNumber.replace(Regex("[^+\\d]"), "")
    
    // Generates a fully compliant universal WhatsApp link that runs on WhatsApp App or Web
    val url = "https://api.whatsapp.com/send?phone=$formattedNo&text=${Uri.encode(message)}"
    val intent = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse(url)
    }
    
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "No WhatsApp Client. Redirecting to text sharing...", Toast.LENGTH_SHORT).show()
        val chooserIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
        }
        context.startActivity(Intent.createChooser(chooserIntent, "Share Medical Reminder"))
    }
}

// ==========================================
// DIALOG: 1. Add / Edit Medicine
// ==========================================
@Composable
fun AddEditMedDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int, Int, String, Double, Long?, String, String, String?, String, String) -> Unit
) {
    val appContext = LocalContext.current
    var name by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var dosage by remember { mutableStateOf("500mg") }
    var unitForm by remember { mutableStateOf("") }
    var stock by remember { mutableStateOf("50") }
    var minStock by remember { mutableStateOf("15") }
    var category by remember { mutableStateOf("Antibiotic") }
    var price by remember { mutableStateOf("0.0") }
    var expiryDateStr by remember { mutableStateOf("") }
    var batchNumber by remember { mutableStateOf("") }
    var supplier by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<String?>(null) }
    
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            val savedUri = saveImageToInternalStorage(appContext, uri)
            imageUri = savedUri?.toString()
        }
    }

    val categoriesList = listOf("Antibiotic", "Analgesic", "Antidiabetic", "Cardiology", "Respiratory", "Antimalarial", "PEP", "Antihypertensives", "Vitamins", "Other")

    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "New Medication Record",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                if (isError) {
                    Text(
                        text = "Medicine name is required.",
                        color = Color.Red,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    if (imageUri != null) {
                        coil.compose.AsyncImage(
                            model = imageUri,
                            contentDescription = "Selected Image",
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(Color.LightGray),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Image, contentDescription = "Placeholder", tint = Color.Gray)
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(onClick = { launcher.launch("image/*") }) {
                        Text(if (imageUri == null) "Add Image" else "Change Image")
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; isError = false },
                    label = { Text("Medicine Name (e.g. Paracetamol)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = dosage,
                        onValueChange = { dosage = it },
                        label = { Text("Dosage (e.g. 500mg)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = unitForm,
                        onValueChange = { unitForm = it },
                        label = { Text("Unit Form (e.g. Card of 10)") },
                        singleLine = true,
                        modifier = Modifier.weight(1.5f)
                    )
                }
                
                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text("Brand (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Price (₦)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = stock,
                        onValueChange = { stock = it },
                        label = { Text("Initial Stock") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = minStock,
                        onValueChange = { minStock = it },
                        label = { Text("Min Stock Alert") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category (e.g. Antimalarial)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = batchNumber,
                        onValueChange = { batchNumber = it },
                        label = { Text("Batch # (Optional)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = supplier,
                        onValueChange = { supplier = it },
                        label = { Text("Supplier (Optional)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = expiryDateStr,
                    onValueChange = { expiryDateStr = it },
                    label = { Text("Expiry Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Simple spinner replacement category chips row
                Text(
                    text = "Suggestions:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )

                CategoryFlowSelection(
                    categories = categoriesList,
                    selected = category,
                    onSelected = { category = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        isError = true
                    } else {
                        val stockVal = stock.toIntOrNull() ?: 0
                        val minStockVal = minStock.toIntOrNull() ?: 10
                        val priceVal = price.toDoubleOrNull() ?: 0.0
                        var parsedExpiry: Long? = null
                        if (expiryDateStr.isNotBlank()) {
                            try {
                                parsedExpiry = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).parse(expiryDateStr)?.time
                            } catch (e: Exception) { }
                        }
                        onConfirm(name, dosage, stockVal, minStockVal, category, priceVal, parsedExpiry, batchNumber, supplier, imageUri, unitForm, brand)
                    }
                }
            ) {
                Text("Catalog Medicine")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// ==========================================
// DIALOG: 2. Edit Stock Quantity Panel Sheet
// ==========================================
@Composable
fun EditStockQuantityDialog(
    item: InventoryItem,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int, Int, String, Double, Long?, String, String, String?, String, String) -> Unit
) {
    var name by remember { mutableStateOf(item.name) }
    var dosage by remember { mutableStateOf(item.dosage) }
    var unitForm by remember { mutableStateOf(item.unitForm) }
    var brand by remember { mutableStateOf(item.brand) }
    var stock by remember { mutableStateOf(item.stockQuantity.toString()) }
    var minStock by remember { mutableStateOf(item.minRequiredStock.toString()) }
    var category by remember { mutableStateOf(item.category) }
    var price by remember { mutableStateOf(item.price.toString()) }
    var expiryDateStr by remember { 
        mutableStateOf(if (item.expiryDate > 0) java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(item.expiryDate)) else "") 
    }
    var batchNumber by remember { mutableStateOf(item.batchNumber) }
    var supplier by remember { mutableStateOf(item.supplier) }
    var imageUri by remember { mutableStateOf(item.imageUri) }
    val appContext = LocalContext.current

    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            val savedUri = saveImageToInternalStorage(appContext, uri)
            imageUri = savedUri?.toString()
        }
    }

    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit Medication",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                if (isError) {
                    Text(
                        text = "Medicine name is required.",
                        color = Color.Red,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    if (imageUri != null) {
                        coil.compose.AsyncImage(
                            model = imageUri,
                            contentDescription = "Selected Image",
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(Color.LightGray),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Image, contentDescription = "Placeholder", tint = Color.Gray)
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(onClick = { launcher.launch("image/*") }) {
                        Text(if (imageUri == null) "Add Image" else "Change Image")
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; isError = false },
                    label = { Text("Medicine Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = dosage,
                        onValueChange = { dosage = it },
                        label = { Text("Dosage") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = unitForm,
                        onValueChange = { unitForm = it },
                        label = { Text("Unit Form") },
                        singleLine = true,
                        modifier = Modifier.weight(1.5f)
                    )
                }

                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text("Brand (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Price (₦)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = stock,
                        onValueChange = { stock = it },
                        label = { Text("Current Stock") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = minStock,
                        onValueChange = { minStock = it },
                        label = { Text("Min Alert Lvl") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = batchNumber,
                        onValueChange = { batchNumber = it },
                        label = { Text("Batch # (Optional)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = supplier,
                        onValueChange = { supplier = it },
                        label = { Text("Supplier (Optional)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = expiryDateStr,
                    onValueChange = { expiryDateStr = it },
                    label = { Text("Expiry Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        isError = true
                    } else {
                        val stockVal = stock.toIntOrNull() ?: 0
                        val minStockVal = minStock.toIntOrNull() ?: 10
                        val priceVal = price.toDoubleOrNull() ?: 0.0
                        var parsedExpiry: Long? = null
                        if (expiryDateStr.isNotBlank()) {
                            try {
                                parsedExpiry = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).parse(expiryDateStr)?.time
                            } catch (e: Exception) { }
                        }
                        onConfirm(name, dosage, stockVal, minStockVal, category, priceVal, parsedExpiry, batchNumber, supplier, imageUri, unitForm, brand)
                    }
                }
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// ==========================================
// DIALOG: 3. Log Daily Volume Dialog
// ==========================================
@Composable
fun LogVolumeDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Int, String) -> Unit
) {
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    var dateString by remember { mutableStateOf(sdf.format(Date())) }
    var volumeString by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Log Daily Shift Rx Volume",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isError) {
                    Text(
                        text = "A valid prescription count is required.",
                        color = Color.Red,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                OutlinedTextField(
                    value = dateString,
                    onValueChange = { dateString = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = volumeString,
                    onValueChange = { volumeString = it; isError = false },
                    label = { Text("Prescriptions Filled (Count)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Shift Notes (Optional)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val count = volumeString.toIntOrNull()
                    if (count == null || count < 0) {
                        isError = true
                    } else {
                        onConfirm(dateString, count, notes)
                    }
                }
            ) {
                Text("Save Log Entry")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Legacy Add Reminder Dialog Removed

// ==========================================
// EXTRA SUBCOMP: Dialog helpers flow selections
// ==========================================
@Composable
fun CategoryFlowSelection(
    categories: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(categories) { cat ->
            val isSel = cat == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onSelected(cat) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = cat,
                    color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ==========================================
// CSV EXPORT HELPERS
// ==========================================
fun escapeCsvValue(value: Any?): String {
    val str = value?.toString() ?: ""
    if (str.contains(",") || str.contains("\"") || str.contains("\n") || str.contains("\r")) {
        return "\"" + str.replace("\"", "\"\"") + "\""
    }
    return str
}

fun generateProductsCsv(inventory: List<InventoryItem>): String {
    val builder = java.lang.StringBuilder()
    builder.append("ID,Name,Brand,Dosage,Category,Stock Quantity,Threshold,Price,Expiry Date,Batch Number,Supplier,Unit Form,Total Sold,Status\n")
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    inventory.forEach {
        val expiryStr = if (it.expiryDate > 0) sdf.format(Date(it.expiryDate)) else "N/A"
        val status = if (it.isLowStock) "LOW" else "OK"
        builder.append(
            "${it.id}," +
            "${escapeCsvValue(it.name)}," +
            "${escapeCsvValue(it.brand)}," +
            "${escapeCsvValue(it.dosage)}," +
            "${escapeCsvValue(it.category)}," +
            "${it.stockQuantity}," +
            "${it.minRequiredStock}," +
            "${it.price}," +
            "${escapeCsvValue(expiryStr)}," +
            "${escapeCsvValue(it.batchNumber)}," +
            "${escapeCsvValue(it.supplier)}," +
            "${escapeCsvValue(it.unitForm)}," +
            "${it.totalSoldQuantity}," +
            "$status\n"
        )
    }
    return builder.toString()
}

fun generateCustomersCsv(customers: List<Customer>): String {
    val builder = java.lang.StringBuilder()
    builder.append("ID,Name,Phone,Email,Loyalty Points,Refill Streak,Date Added,Notes\n")
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    customers.forEach {
        val dateAddedStr = sdf.format(Date(it.dateAdded))
        builder.append(
            "${it.id}," +
            "${escapeCsvValue(it.name)}," +
            "${escapeCsvValue(it.phoneNumber)}," +
            "${escapeCsvValue(it.email)}," +
            "${it.loyaltyPoints}," +
            "${it.refillStreak}," +
            "${escapeCsvValue(dateAddedStr)}," +
            "${escapeCsvValue(it.notes)}\n"
        )
    }
    return builder.toString()
}

fun generateCarouselCsv(carousels: List<AICarousel>): String {
    val builder = java.lang.StringBuilder()
    builder.append("Carousel_ID,Topic_Title,Caption,Theme,Created_At,Slide_Number,Heading,Body_Text,Key_Points,Recommended_Products\n")
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    
    val moshi = com.squareup.moshi.Moshi.Builder().build()
    val slidesAdapter = moshi.adapter<List<CarouselSlide>>(
        com.squareup.moshi.Types.newParameterizedType(List::class.java, CarouselSlide::class.java)
    )
    
    carousels.forEach { carousel ->
        val dateStr = sdf.format(Date(carousel.createdAt))
        val slidesList: List<CarouselSlide> = try {
            slidesAdapter.fromJson(carousel.slidesJson) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        
        if (slidesList.isEmpty()) {
            builder.append(
                "${carousel.id}," +
                "${escapeCsvValue(carousel.topicTitle)}," +
                "${escapeCsvValue(carousel.caption)}," +
                "${escapeCsvValue(carousel.visualTheme)}," +
                "${escapeCsvValue(dateStr)}," +
                "N/A,N/A,N/A,N/A,N/A\n"
            )
        } else {
            slidesList.forEach { slide ->
                val pointsStr = slide.keyPoints.joinToString("; ")
                val prodStr = slide.recommendedProducts?.joinToString("; ") ?: ""
                builder.append(
                    "${carousel.id}," +
                    "${escapeCsvValue(carousel.topicTitle)}," +
                    "${escapeCsvValue(carousel.caption)}," +
                    "${escapeCsvValue(carousel.visualTheme)}," +
                    "${escapeCsvValue(dateStr)}," +
                    "${slide.slideNumber}," +
                    "${escapeCsvValue(slide.heading)}," +
                    "${escapeCsvValue(slide.text)}," +
                    "${escapeCsvValue(pointsStr)}," +
                    "${escapeCsvValue(prodStr)}\n"
                )
            }
        }
    }
    return builder.toString()
}

fun generateTriageCsv(conditions: List<TriageCondition>): String {
    val builder = java.lang.StringBuilder()
    builder.append("Condition_ID,Condition_Name,Alternative_Names,Category,Brief_Description,Key_Symptoms,Referral_Criteria,Severity_Assessment,Recommended_OTCs,Prescription_Options,Counselling_Points,Lifestyle_Advice,Follow_Up,Favorite,Usage_Count,Last_Edited_By,Last_Updated,Question_Text,Question_Required,Question_Is_Red_Flag\n")
    
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val moshi = com.squareup.moshi.Moshi.Builder().build()
    val questionAdapter = moshi.adapter<List<TriageQuestion>>(
        com.squareup.moshi.Types.newParameterizedType(List::class.java, TriageQuestion::class.java)
    )
    
    conditions.forEach { condition ->
        val updatedStr = sdf.format(Date(condition.lastUpdated))
        val questionsList: List<TriageQuestion> = try {
            questionAdapter.fromJson(condition.questionsJson) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        
        val baseRow = "${condition.id}," +
                "${escapeCsvValue(condition.conditionName)}," +
                "${escapeCsvValue(condition.alternativeNames)}," +
                "${escapeCsvValue(condition.category)}," +
                "${escapeCsvValue(condition.briefDescription)}," +
                "${escapeCsvValue(condition.keySymptoms)}," +
                "${escapeCsvValue(condition.referralCriteria)}," +
                "${escapeCsvValue(condition.severityAssessment)}," +
                "${escapeCsvValue(condition.recommendedOtcs)}," +
                "${escapeCsvValue(condition.prescriptionOptions)}," +
                "${escapeCsvValue(condition.counsellingPoints)}," +
                "${escapeCsvValue(condition.lifestyleAdvice)}," +
                "${escapeCsvValue(condition.followUpTimeline)}," +
                "${condition.isFavorite}," +
                "${condition.usageCount}," +
                "${escapeCsvValue(condition.lastEditedBy)}," +
                "${escapeCsvValue(updatedStr)}"
                
        if (questionsList.isEmpty()) {
            builder.append("$baseRow,N/A,N/A,N/A\n")
        } else {
            questionsList.forEach { q ->
                builder.append("$baseRow,${escapeCsvValue(q.question)},${q.required},${q.isRedFlag}\n")
            }
        }
    }
    return builder.toString()
}

fun generateInventoryCsv(inventory: List<InventoryItem>, customerMeds: List<com.example.data.CustomerMedication>): String {
    val builder = StringBuilder()
    builder.append("Careflux Ledger Export\n")
    builder.append("Report Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}\n\n")

    builder.append("=== INVENTORY OVERVIEW ===\n")
    builder.append("ID,Name,Dosage,Category,Stock Quantity,Threshold,Status\n")
    inventory.forEach {
        val status = if (it.isLowStock) "LOW_STOCK" else "OK"
        builder.append("${it.id},\"${it.name}\",\"${it.dosage}\",\"${it.category}\",${it.stockQuantity},${it.minRequiredStock},$status\n")
    }

    builder.append("\n=== IMPENDING REFILLS / PRESCRIPTIONS ===\n")
    builder.append("Patient_ID,Medication,Dosage,Refill_Next\n")
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    customerMeds.forEach {
        val nextRefill = sdf.format(Date(it.nextRefillDate))
        builder.append("${it.customerId},\"${it.medicationName}\",\"${it.customDosage}\",$nextRefill\n")
    }

    return builder.toString()
}

fun shareCsvFile(context: android.content.Context, content: String, proposedFileName: String = "export.csv") {
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        try {
            val file = java.io.File(context.cacheDir, proposedFileName)
            file.writeText(content)
            
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${com.example.BuildConfig.APPLICATION_ID}.fileprovider", file)
            
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(android.content.Intent.EXTRA_TITLE, "Careflux Export")
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            val chooser = android.content.Intent.createChooser(intent, "Export Pharmacy Data")
            chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                context.startActivity(chooser)
            }
        } catch (e: Exception) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                android.widget.Toast.makeText(context, "Export error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}

fun generatePrescriptionsCsv(customerMeds: List<com.example.data.CustomerMedication>): String {
    val builder = java.lang.StringBuilder()
    builder.append("Patient_ID,Medication,Dosage,Refill_Next\n")
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    customerMeds.forEach {
        val refillDate = sdf.format(Date(it.nextRefillDate))
        builder.append("${it.customerId},${escapeCsvValue(it.medicationName)},${escapeCsvValue(it.customDosage)},$refillDate\n")
    }
    return builder.toString()
}

fun importProductsFromCsv(context: android.content.Context, uri: android.net.Uri, viewModel: PharmacyViewModel) {
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        try {
            var importedCount = 0
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                val sequence = reader.lineSequence().iterator()
                if (!sequence.hasNext()) return@use
                
                var isFirstLine = true
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                
                while (sequence.hasNext()) {
                    val line = sequence.next().trim()
                    if (line.isEmpty() || line.startsWith("==") || line.startsWith("Careflux Ledger Export") || line.startsWith("Report Date") || line.startsWith("=== INVENTORY OVERVIEW ===")) continue
                    val parts = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex()).map { it.trim(' ', '"') }
                    
                    if (isFirstLine && parts.getOrNull(0)?.equals("ID", ignoreCase = true) == true) {
                        isFirstLine = false
                        continue
                    }
                    isFirstLine = false

                    if (parts.size >= 2) {
                        val idVal = parts.getOrNull(0)?.toIntOrNull() ?: 0
                        val nameStr = if (idVal != 0) parts.getOrNull(1) ?: "" else parts.getOrNull(0) ?: ""
                        if (nameStr.isBlank()) continue

                        val brandStr = if (idVal != 0) parts.getOrNull(2) ?: "" else ""
                        val dosageStr = if (idVal != 0) parts.getOrNull(3) ?: "" else parts.getOrNull(1) ?: "N/A"
                        val categoryStr = if (idVal != 0) parts.getOrNull(4) ?: "" else parts.getOrNull(4) ?: "Other"
                        val stockStr = if (idVal != 0) parts.getOrNull(5) ?: "0" else parts.getOrNull(2) ?: "0"
                        val minStockStr = if (idVal != 0) parts.getOrNull(6) ?: "10" else parts.getOrNull(3) ?: "10"
                        val priceStr = if (idVal != 0) parts.getOrNull(7) ?: "0.0" else parts.getOrNull(5) ?: "0.0"

                        val expiryStr = if (idVal != 0) parts.getOrNull(8) ?: "" else ""
                        val batchNumberStr = if (idVal != 0) parts.getOrNull(9) ?: "" else ""
                        val supplierStr = if (idVal != 0) parts.getOrNull(10) ?: "" else ""
                        val unitFormStr = if (idVal != 0) parts.getOrNull(11) ?: "" else ""

                        val stock = stockStr.toIntOrNull() ?: 0
                        val minStock = minStockStr.toIntOrNull() ?: 10
                        val price = priceStr.toDoubleOrNull() ?: 0.0

                        val expiryL = try {
                            if (expiryStr.isNotBlank() && expiryStr != "N/A") {
                                sdf.parse(expiryStr)?.time ?: 0L
                            } else 0L
                        } catch (e: Exception) {
                            0L
                        }

                        viewModel.addOrUpdateInventory(
                            id = idVal,
                            name = nameStr,
                            dosage = dosageStr,
                            currentStock = stock,
                            minStock = minStock,
                            category = categoryStr,
                            price = price,
                            expiryDate = expiryL,
                            batchNumber = batchNumberStr,
                            supplier = supplierStr,
                            unitForm = unitFormStr,
                            brand = brandStr
                        )
                        importedCount++
                    }
                }
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                android.widget.Toast.makeText(context, "Successfully imported $importedCount products", android.widget.Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                android.widget.Toast.makeText(context, "Import error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
}

fun importCustomersFromCsv(context: android.content.Context, uri: android.net.Uri, viewModel: PharmacyViewModel) {
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        try {
            var importedCount = 0
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                val sequence = reader.lineSequence().iterator()
                if (!sequence.hasNext()) return@use
                
                var isFirstLine = true
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                
                while (sequence.hasNext()) {
                    val line = sequence.next().trim()
                    if (line.isEmpty() || line.startsWith("==")) continue
                    val parts = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex()).map { it.trim(' ', '"') }
                    
                    if (parts.isNotEmpty()) {
                        val firstValue = parts[0]
                        if (isFirstLine && firstValue.equals("ID", ignoreCase = true)) {
                            isFirstLine = false
                            continue
                        }
                        isFirstLine = false
                        
                        val idVal = parts.getOrNull(0)?.toIntOrNull() ?: 0
                        val nameStr = parts.getOrNull(1) ?: ""
                        if (nameStr.isBlank()) continue
                        
                        val phoneStr = parts.getOrNull(2) ?: ""
                        val emailStr = parts.getOrNull(3) ?: ""
                        val loyaltyStr = parts.getOrNull(4) ?: "0"
                        val refillStr = parts.getOrNull(5) ?: "0"
                        val dateAddedStr = parts.getOrNull(6) ?: ""
                        val notesStr = parts.getOrNull(7) ?: ""
                        
                        val loyalty = loyaltyStr.toIntOrNull() ?: 0
                        val refill = refillStr.toIntOrNull() ?: 0
                        
                        val dateAddedL = try {
                            if (dateAddedStr.isNotBlank()) {
                                sdf.parse(dateAddedStr)?.time ?: System.currentTimeMillis()
                            } else {
                                System.currentTimeMillis()
                            }
                        } catch (e: Exception) {
                            dateAddedStr.toLongOrNull() ?: System.currentTimeMillis()
                        }
                        
                        val customerToSave = com.example.data.Customer(
                            id = idVal,
                            name = nameStr.trim(),
                            phoneNumber = phoneStr.trim(),
                            email = emailStr.trim(),
                            notes = notesStr.trim(),
                            loyaltyPoints = loyalty,
                            refillStreak = refill,
                            dateAdded = dateAddedL
                        )
                        val phoneNorm = phoneStr.trim().replace(Regex("[^+\\d]"), "")
                        val isDuplicate = if (phoneNorm.isNotEmpty()) {
                            viewModel.customers.value.any {
                                it.phoneNumber.replace(Regex("[^+\\d]"), "").equals(phoneNorm, ignoreCase = true)
                            }
                        } else false

                        if (!isDuplicate) {
                            viewModel.repository.insertCustomer(customerToSave)
                            importedCount++
                        }
                    }
                }
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                android.widget.Toast.makeText(context, "Successfully imported $importedCount customers", android.widget.Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                android.widget.Toast.makeText(context, "Import error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
}

fun importMedicationsFromCsv(context: android.content.Context, uri: android.net.Uri, viewModel: PharmacyViewModel) {
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        try {
            var importedCount = 0
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                val sequence = reader.lineSequence().iterator()
                if (!sequence.hasNext()) return@use
                
                var isFirstLine = true
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                
                while (sequence.hasNext()) {
                    val line = sequence.next().trim()
                    if (line.isEmpty() || line.startsWith("==") || line.startsWith("Careflux Ledger Export") || line.startsWith("Report Date") || line.startsWith("=== INVENTORY OVERVIEW ===") || line.startsWith("Patient_ID,Medication,Dosage,Refill_Next")) {
                        if (line.startsWith("Patient_ID,Medication,Dosage,Refill_Next")) {
                            isFirstLine = false
                        }
                        continue
                    }
                    val parts = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex()).map { it.trim(' ', '"') }
                    
                    if (parts.isNotEmpty()) {
                        val firstValue = parts[0]
                        if (isFirstLine && firstValue.equals("Patient_ID", ignoreCase = true)) {
                            isFirstLine = false
                            continue
                        }
                        isFirstLine = false
                        
                        val patientIdStr = parts.getOrNull(0) ?: ""
                        val patientId = patientIdStr.toIntOrNull() ?: 0
                        if (patientId == 0) continue
                        
                        val medName = parts.getOrNull(1) ?: ""
                        if (medName.isBlank()) continue
                        
                        val dosageStr = parts.getOrNull(2) ?: "N/A"
                        val refillNextStr = parts.getOrNull(3) ?: ""
                        
                        val nextRefillL = try {
                            if (refillNextStr.isNotBlank() && refillNextStr != "N/A") {
                                sdf.parse(refillNextStr)?.time ?: System.currentTimeMillis()
                            } else {
                                System.currentTimeMillis()
                            }
                        } catch (e: Exception) {
                            refillNextStr.toLongOrNull() ?: System.currentTimeMillis()
                        }
                        
                        viewModel.addCustomerMedication(
                            customerId = patientId,
                            invItemId = 0,
                            medName = medName,
                            customDosage = dosageStr,
                            cost = 0.0,
                            cycleDays = 30,
                            nextRefill = nextRefillL
                        )
                        importedCount++
                    }
                }
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                android.widget.Toast.makeText(context, "Successfully imported $importedCount active prescriptions", android.widget.Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                android.widget.Toast.makeText(context, "Import error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
}

// ==========================================
// CART & RECEIPT FUNCTIONALITY
// ==========================================

@Composable
fun AddToCartDialog(
    item: InventoryItem,
    inventory: List<InventoryItem>,
    onDismiss: () -> Unit,
    onConfirm: (InventoryItem, Int) -> Unit
) {
    var quantity by remember { mutableStateOf("1") }
    
    val recommendation = remember(item, inventory) {
        val sameItems = inventory.filter { 
            it.name.equals(item.name, ignoreCase = true) && 
            it.stockQuantity > 0 && 
            it.expiryDate > 0L 
        }
        val closestItem = sameItems.minByOrNull { it.expiryDate }
        if (closestItem != null && closestItem.id != item.id) {
            if (item.expiryDate == 0L || closestItem.expiryDate < item.expiryDate) {
                closestItem
            } else {
                null
            }
        } else {
            null
        }
    }

    var useRecommendation by remember { mutableStateOf(recommendation != null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Add to Cart", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text("Item: ${item.name}", style = MaterialTheme.typography.bodyMedium)
                Text("Price: ₦${"%,.2f".format(item.price)}", style = MaterialTheme.typography.bodyMedium)
                
                if (recommendation != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    androidx.compose.material3.Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("Recommendation: Closer to Expiry", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            val df = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                            Text("A batch expiring on ${df.format(java.util.Date(recommendation.expiryDate))} is available. Prioritize this batch?", style = MaterialTheme.typography.bodySmall)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.material3.Checkbox(
                                    checked = useRecommendation,
                                    onCheckedChange = { useRecommendation = it }
                                )
                                Text("Use Recommended Batch", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text("Quantity") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val q = quantity.toIntOrNull() ?: 1
                val confirmedItem = if (recommendation != null && useRecommendation) recommendation else item
                onConfirm(confirmedItem, q)
            }) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun CartTabContent(
    cartItems: List<com.example.ui.CartItem>,
    deliveryFeeString: String,
    customers: List<Customer>,
    onDeliveryFeeChange: (String) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onNeedRefillChange: (Int, Boolean) -> Unit,
    onCheckout: (Customer?, Double, String, Boolean, String) -> Unit,
    context: android.content.Context
) {
    var selectedCustomer by remember { mutableStateOf<Customer?>(null) }
    var expanded by remember { mutableStateOf(false) }

    var deliveryAddress by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("Cash") }

    val deliveryFee = deliveryFeeString.toDoubleOrNull() ?: 0.0
    val subtotal = cartItems.sumOf { it.inventoryItem.price * it.quantity }
    val total = subtotal + deliveryFee

    val cartWarnings = remember(cartItems) {
        val names = cartItems.map { it.inventoryItem.name }
        com.example.data.ClinicalDdiEngine.checkInteractions(names)
    }

    Column(modifier = Modifier.fillMaxSize().padding(bottom = 16.dp)) {
        if (cartItems.isEmpty()) {
            Text("Current Cart", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            EmptyStatePlaceholder(message = "Cart is empty", tip = "Add items from the Stock tab.")
            return
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Text("Current Cart", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                if (cartWarnings.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(12.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = "Dispensing Warning Icon",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Dispensing Contraindication Warning!",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            cartWarnings.forEach { warning ->
                                Text(
                                    text = warning,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    lineHeight = 16.sp,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            items(cartItems) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateBackgroundLight)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(item.inventoryItem.name, fontWeight = FontWeight.Bold)
                            Text("Qty: ${item.quantity} x ₦${"%,.2f".format(item.inventoryItem.price)}")
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = item.needsRefill, onCheckedChange = { onNeedRefillChange(item.inventoryItem.id, it) })
                                Text("Create Refill/Active Med", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("₦${"%,.2f".format(item.inventoryItem.price * item.quantity)}", fontWeight = FontWeight.SemiBold)
                            IconButton(onClick = { onRemoveItem(item.inventoryItem.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = Color.Red)
                            }
                        }
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = deliveryFeeString,
                    onValueChange = onDeliveryFeeChange,
                    label = { Text("Delivery Fee (₦)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = deliveryAddress,
                    onValueChange = { deliveryAddress = it },
                    label = { Text("Delivery Address (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = paymentMethod,
                    onValueChange = { paymentMethod = it },
                    label = { Text("Payment Method") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Customer Selection
                @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
                androidx.compose.material3.ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedCustomer?.name ?: "Assign to Customer (Optional)",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable, true),
                        trailingIcon = { androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = androidx.compose.material3.ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(text = { Text("None") }, onClick = { selectedCustomer = null; expanded = false })
                        customers.forEach { cust ->
                            DropdownMenuItem(text = { Text(cust.name) }, onClick = { selectedCustomer = cust; expanded = false })
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = TealSurface)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Subtotal: ₦${"%,.2f".format(subtotal)}")
                        Text("Delivery: ₦${"%,.2f".format(deliveryFee)}")
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text("Total: ₦${"%,.2f".format(total)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val (invoiceUri, invoiceFileName) = com.example.DocumentGenerator.generateDocument(
                                context = context,
                                isInvoice = true,
                                cartItems = cartItems,
                                deliveryFee = deliveryFee,
                                totalAmount = total,
                                customerName = selectedCustomer?.name ?: "Guest",
                                customerPhone = selectedCustomer?.phoneNumber ?: "+234 000 000 0000",
                                deliveryAddress = deliveryAddress
                            )
                            if (invoiceUri != null) {
                                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "image/png"
                                    putExtra(android.content.Intent.EXTRA_STREAM, invoiceUri)
                                    putExtra(android.content.Intent.EXTRA_TEXT, "Here is your invoice from Careflux!")
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(android.content.Intent.createChooser(intent, "Send Invoice via WhatsApp"))
                            } else {
                                Toast.makeText(context, "Failed to generate invoice image", Toast.LENGTH_SHORT).show()
                            }
                            onCheckout(selectedCustomer, total, invoiceFileName ?: "", true, "Pending")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Send Invoice", style = MaterialTheme.typography.bodySmall)
                    }

                    Button(
                        onClick = {
                            val (_, receiptFileName) = com.example.DocumentGenerator.generateDocument(
                                context = context,
                                isInvoice = false,
                                cartItems = cartItems,
                                deliveryFee = deliveryFee,
                                totalAmount = total,
                                customerName = selectedCustomer?.name ?: "Guest",
                                customerPhone = selectedCustomer?.phoneNumber ?: "+234 000 000 0000",
                                deliveryAddress = deliveryAddress
                            )
                            onCheckout(selectedCustomer, total, receiptFileName ?: "", false, "Paid")
                            Toast.makeText(context, "Sale completed", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Pay (Checkout)", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

fun saveImageToInternalStorage(context: android.content.Context, uri: android.net.Uri): android.net.Uri? {
    return try {
        val fileName = "img_${System.currentTimeMillis()}.jpg"
        val file = java.io.File(context.filesDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            file.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        } ?: return null
        android.net.Uri.fromFile(file)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@Composable
fun DataHubOptionItem(
    title: String,
    detail: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onExport: () -> Unit,
    onImport: (() -> Unit)? = null
) {
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Export Button
                androidx.compose.material3.OutlinedButton(
                    onClick = onExport,
                    modifier = Modifier.weight(1f).height(36.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = "Export $title",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                
                // Import Button
                if (onImport != null) {
                    androidx.compose.material3.Button(
                        onClick = onImport,
                        modifier = Modifier.weight(1f).height(36.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Upload,
                            contentDescription = "Import $title",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Import", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

