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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
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

data class IntentTargetDestination(
    val tab: String?,
    val subTab: String?,
    val taskId: Long?,
    val customerQuery: String?
)

class MainActivity : ComponentActivity() {
    private val viewModel: PharmacyViewModel by viewModels {
        PharmacyViewModel.Factory(application)
    }

    private val intentTargetState = mutableStateOf<IntentTargetDestination?>(null)

    private fun updateIntentTarget(intent: android.content.Intent?) {
        if (intent == null) return
        val tab = intent.getStringExtra("OPEN_TAB")
        val subTab = intent.getStringExtra("TARGET_SUB_TAB")
        val taskId = intent.getStringExtra("TARGET_TASK_ID")?.toLongOrNull()
            ?: intent.getLongExtra("TARGET_TASK_ID", -1L).takeIf { it != -1L }
        val customerQuery = intent.getStringExtra("TARGET_CUSTOMER_NAME")
            ?: intent.getStringExtra("TARGET_CUSTOMER_ID")
            ?: intent.getStringExtra("TARGET_CUSTOMER")
            ?: intent.getStringExtra("TARGET_SEARCH_QUERY")

        if (!tab.isNullOrBlank() || !subTab.isNullOrBlank() || taskId != null || !customerQuery.isNullOrBlank()) {
            intentTargetState.value = IntentTargetDestination(tab, subTab, taskId, customerQuery)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ensure Firebase is initialized safely
        try {
            if (com.google.firebase.FirebaseApp.getApps(this).isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(this)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        val sharedPrefs = getSharedPreferences("careflux_prefs", android.content.Context.MODE_PRIVATE)
        com.example.ui.theme.AppThemeManager.isDark = sharedPrefs.getBoolean("theme_dark", true)
        
        try {
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
            
            // Trigger immediate run of AI Operations to process baseline clinical alerts and notifications
            val immediateAiRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.work.AIOperationsWorker>().build()
            WorkManager.getInstance(this).enqueueUniqueWork("ai_operations_immediate", androidx.work.ExistingWorkPolicy.KEEP, immediateAiRequest)

            // Schedule background exact operational alarms
            com.example.receiver.AlarmAndBootReceiver.scheduleOperationalAlarms(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        val initialTab = intent.getStringExtra("OPEN_TAB") ?: "inventory"
        val initialSubTab = intent.getStringExtra("TARGET_SUB_TAB")
        val initialTaskId = intent.getStringExtra("TARGET_TASK_ID")?.toLongOrNull()
            ?: intent.getLongExtra("TARGET_TASK_ID", -1L).takeIf { it != -1L }
        val initialCustomerQuery = intent.getStringExtra("TARGET_CUSTOMER_NAME")
            ?: intent.getStringExtra("TARGET_CUSTOMER_ID")
            ?: intent.getStringExtra("TARGET_CUSTOMER")
            ?: intent.getStringExtra("TARGET_SEARCH_QUERY")
        
        updateIntentTarget(intent)

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val activeTarget by intentTargetState
                val currentTab = activeTarget?.tab ?: initialTab
                val currentSubTab = activeTarget?.subTab ?: initialSubTab
                val currentTaskId = activeTarget?.taskId ?: initialTaskId
                val currentCustomerQuery = activeTarget?.customerQuery ?: initialCustomerQuery

                val authRepository = remember { com.example.data.auth.AuthRepository() }
                var currentUser by remember {
                    mutableStateOf(
                        try {
                            authRepository.getCurrentUser()
                        } catch (e: Exception) {
                            null
                        }
                    )
                }
                val context = androidx.compose.ui.platform.LocalContext.current
                val prefs = remember(context) { context.getSharedPreferences("careflux_prefs", android.content.Context.MODE_PRIVATE) }
                var hasCompletedOnboarding by remember { mutableStateOf(prefs.getBoolean("has_completed_onboarding", false)) }
                val isSuspended by viewModel.isSuspended.collectAsStateWithLifecycle()
                
                LaunchedEffect(currentUser) {
                    if (currentUser != null) {
                        viewModel.saveOrUpdateDeviceConfig()
                    }
                }
                
                val currentRole by viewModel.currentPharmacistRole.collectAsStateWithLifecycle()
                
                val isUserAdmin = remember(currentUser, currentRole) {
                    val email = currentUser?.email?.lowercase() ?: ""
                    email == "maduemeziachinedu6@gmail.com" || currentRole == "Admin" || currentRole == "Branch Manager"
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
                                initialTab = currentTab,
                                initialSubTab = currentSubTab,
                                initialTaskId = currentTaskId,
                                initialCustomerQuery = currentCustomerQuery,
                                currentUser = user,
                                onSignOut = {
                                    viewModel.handleUserLoggedOut()
                                    viewModel.clearAllData()
                                    authRepository.signOut()
                                    currentUser = null
                                },
                                modifier = Modifier.padding(innerPadding)
                            )
                        } else {
                            if (!hasCompletedOnboarding) {
                                com.example.ui.OnboardingScreen(
                                    onFinishOnboarding = {
                                        prefs.edit().putBoolean("has_completed_onboarding", true).apply()
                                        hasCompletedOnboarding = true
                                    },
                                    onLoginClick = {
                                        prefs.edit().putBoolean("has_completed_onboarding", true).apply()
                                        hasCompletedOnboarding = true
                                    },
                                    modifier = Modifier.padding(innerPadding)
                                )
                            } else {
                                com.example.ui.AuthScreen(
                                    onAuthSuccess = { verifiedUser ->
                                        currentUser = verifiedUser
                                    },
                                    onShowOnboarding = {
                                        hasCompletedOnboarding = false
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

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        updateIntentTarget(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PharmacyRootScreen(
    viewModel: PharmacyViewModel,
    initialTab: String = "inventory",
    initialSubTab: String? = null,
    initialTaskId: Long? = null,
    initialCustomerQuery: String? = null,
    currentUser: com.example.data.auth.AuthUser? = null,
    onSignOut: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val drawerState = androidx.compose.material3.rememberDrawerState(androidx.compose.material3.DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val inventory by viewModel.inventoryItems.collectAsStateWithLifecycle()
    val isInventoryLoading by viewModel.isInventoryLoading.collectAsStateWithLifecycle()
    val lowStockMeds by viewModel.lowStockItems.collectAsStateWithLifecycle()
    val volumes by viewModel.prescriptionVolumes.collectAsStateWithLifecycle()
    val alerts by viewModel.customerAlerts.collectAsStateWithLifecycle()

    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val lastSyncedTime by viewModel.lastSyncedTime.collectAsStateWithLifecycle()
    val csvImportSessionState by viewModel.csvImportSession.collectAsStateWithLifecycle()

    val mappedInitialTab = if (initialTab == "ai_tasks" || initialTab == "branch_team") "branch_team" else initialTab
    var activeTab by remember { mutableStateOf(mappedInitialTab) } // inventory, volumes, customers, branch_team, receipts
    var activeSubTab by remember { mutableStateOf<String?>(initialSubTab) }
    var highlightTaskId by remember { mutableStateOf<Long?>(initialTaskId) }
    var targetCustomerQuery by remember { mutableStateOf<String?>(initialCustomerQuery) }

    LaunchedEffect(initialTab, initialSubTab, initialTaskId, initialCustomerQuery) {
        if (!initialTab.isNullOrBlank()) {
            activeTab = if (initialTab == "ai_tasks" || initialTab == "branch_team") "branch_team" else initialTab
        }
        if (!initialSubTab.isNullOrBlank()) {
            activeSubTab = initialSubTab
        }
        if (initialTaskId != null && initialTaskId > 0L) {
            highlightTaskId = initialTaskId
            viewModel.setHighlightTaskId(initialTaskId)
        }
        if (!initialCustomerQuery.isNullOrBlank()) {
            targetCustomerQuery = initialCustomerQuery
        }
    }

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
    val isClinicalEnabled by viewModel.isClinicalEnabled.collectAsStateWithLifecycle()
    val isMessagingEnabled by viewModel.isMessagingEnabled.collectAsStateWithLifecycle()
    val isTriageEnabled by viewModel.isTriageEnabled.collectAsStateWithLifecycle()
    val isMarketplaceEnabled by viewModel.isMarketplaceEnabled.collectAsStateWithLifecycle()
    val isProcurementEnabled by viewModel.isProcurementEnabled.collectAsStateWithLifecycle()
    val keyRequests by viewModel.keyRequests.collectAsStateWithLifecycle()
    val lastFailedSmsLog by viewModel.lastFailedSmsLog.collectAsStateWithLifecycle()
    val postDispatchConfirmAlert by viewModel.activePostDispatchConfirmAlert.collectAsStateWithLifecycle()
    val postDispatchConfirmMedData by viewModel.activePostDispatchConfirm.collectAsStateWithLifecycle()

    val currentRole by viewModel.currentPharmacistRole.collectAsStateWithLifecycle()

    val isUserAdmin = remember(currentUser, currentRole) {
        val email = currentUser?.email?.lowercase() ?: ""
        email == "maduemeziachinedu6@gmail.com" || currentRole == "Admin" || currentRole == "Branch Manager"
    }

    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            viewModel.handleUserAuthenticated(currentUser)
        }
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
    LaunchedEffect(isMessagingEnabled) {
        if (!isMessagingEnabled && activeTab == "customer_engagement") {
            activeTab = "inventory"
        }
    }
    LaunchedEffect(isTriageEnabled) {
        if (!isTriageEnabled && activeTab == "pharmacy_triage") {
            activeTab = "inventory"
        }
    }
    LaunchedEffect(isMarketplaceEnabled) {
        if (!isMarketplaceEnabled && activeTab == "rescue_marketplace") {
            activeTab = "inventory"
        }
    }
    LaunchedEffect(isProcurementEnabled) {
        if (!isProcurementEnabled && activeTab == "procurement") {
            activeTab = "inventory"
        }
    }

    // Dialog control states
    var showExportDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showSyncStatusDialog by remember { mutableStateOf(false) }
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
        if (viewModel.currentPharmacistRole.value != "Branch Manager" && !isUserAdmin) {
            Toast.makeText(context, "Access Denied: Only the Branch Manager can perform data imports.", Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }
        uri?.let {
            importProductsFromCsv(context, it, viewModel)
        }
    }

    val customersCsvFilePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (viewModel.currentPharmacistRole.value != "Branch Manager" && !isUserAdmin) {
            Toast.makeText(context, "Access Denied: Only the Branch Manager can perform data imports.", Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }
        uri?.let {
            importCustomersFromCsv(context, it, viewModel)
        }
    }

    val medicationsCsvFilePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (viewModel.currentPharmacistRole.value != "Branch Manager" && !isUserAdmin) {
            Toast.makeText(context, "Access Denied: Only the Branch Manager can perform data imports.", Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }
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
                    Image(
                        painter = painterResource(id = R.drawable.ic_careflux_logo),
                        contentDescription = "Careflux Logo",
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Careflux Menu", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                androidx.compose.material3.HorizontalDivider()
                if (isProcurementEnabled) {
                    androidx.compose.material3.NavigationDrawerItem(
                        label = { Text("Procurement & Stock Transfers", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        selected = activeTab == "procurement",
                        onClick = {
                            activeTab = "procurement"
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Filled.LocalShipping, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.padding(androidx.compose.material3.NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
                androidx.compose.material3.NavigationDrawerItem(
                    label = { Text("Analytics Dashboard", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    selected = activeTab == "analytics",
                    onClick = {
                        activeTab = "analytics"
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Filled.Insights, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.padding(androidx.compose.material3.NavigationDrawerItemDefaults.ItemPadding)
                )
                if (isMessagingEnabled) {
                    androidx.compose.material3.NavigationDrawerItem(
                        label = { Text("Customer Engagement", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        selected = activeTab == "customer_engagement",
                        onClick = {
                            activeTab = "customer_engagement"
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Filled.Campaign, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.padding(androidx.compose.material3.NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
 
                if (isAiContentEnabled) {
                    androidx.compose.material3.NavigationDrawerItem(
                        label = { Text("AI Content Engine", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        selected = activeTab == "ai_content_engine",
                        onClick = {
                            activeTab = "ai_content_engine"
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.padding(androidx.compose.material3.NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
                if (isTriageEnabled) {
                    androidx.compose.material3.NavigationDrawerItem(
                        label = { Text("Pharmacy Triage", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        selected = activeTab == "pharmacy_triage",
                        onClick = {
                            activeTab = "pharmacy_triage"
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Filled.ContentPasteSearch, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.padding(androidx.compose.material3.NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
                if (isMarketplaceEnabled) {
                    androidx.compose.material3.NavigationDrawerItem(
                        label = { Text("Expiry Rescue Marketplace", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        selected = activeTab == "rescue_marketplace",
                        onClick = {
                            activeTab = "rescue_marketplace"
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Filled.Storefront, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.padding(androidx.compose.material3.NavigationDrawerItemDefaults.ItemPadding)
                    )
                }



                if (isUserAdmin) {
                    androidx.compose.material3.NavigationDrawerItem(
                        label = { Text("Control Room (Admin)", color = TealPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        selected = activeTab == "admin_dashboard",
                        onClick = {
                            activeTab = "admin_dashboard"
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Filled.AdminPanelSettings, contentDescription = "Admin Dashboard", tint = TealPrimary, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.padding(androidx.compose.material3.NavigationDrawerItemDefaults.ItemPadding)
                    )
                }

                if (!isUserAdmin) {
                    androidx.compose.material3.NavigationDrawerItem(
                        label = { Text("Personal Gemini Key", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            showPersonalKeyDialog = true
                        },
                        icon = { Icon(Icons.Filled.Key, contentDescription = "Personal Gemini Key", modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.padding(androidx.compose.material3.NavigationDrawerItemDefaults.ItemPadding)
                    )
                }



                androidx.compose.material3.NavigationDrawerItem(
                    label = { Text("Sign Out", color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onSignOut()
                    },
                    icon = { Icon(Icons.Filled.ExitToApp, contentDescription = "Sign Out", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.padding(androidx.compose.material3.NavigationDrawerItemDefaults.ItemPadding)
                )

                Spacer(modifier = Modifier.weight(1f))

                // Embedded elegant sync status in the side menu
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "DATABASE STATUS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 6.dp, start = 8.dp)
                    )
                    GlobalSyncStatusBanner(
                        isOnline = isOnline,
                        syncState = syncState,
                        lastSyncedTime = lastSyncedTime,
                        onManualSyncClick = { viewModel.triggerImmediateSync() }
                    )
                }

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
        ) {
            // --- Custom App Header ---
            HeaderSection(
                cartCount = cartItems.sumOf { it.quantity },
                isAdmin = isUserAdmin,
                isOnline = isOnline,
                syncState = syncState,
                onMenuClick = { scope.launch { drawerState.open() } },
                onExportClick = {
                    showExportDialog = true
                },
                onReceiptsClick = {
                    activeTab = "analytics"
                },
                onCartClick = {
                    activeTab = "cart"
                },
                onSettingsClick = {
                    showSettingsDialog = true
                },
                onSyncStatusClick = {
                    showSyncStatusDialog = true
                }
            )

        // --- Settings Dialog ---
        if (showSettingsDialog) {
            var tempApiKey by remember { mutableStateOf(viewModel.getApiKey()) }
            var tempPharmacyName by remember { mutableStateOf(viewModel.getPharmacyName()) }
            var tempPharmacyLga by remember { mutableStateOf(viewModel.getPharmacyLga()) }
            var tempPharmacyState by remember { mutableStateOf(viewModel.getPharmacyState()) }
            var tempNotificationsEnabled by remember { mutableStateOf(viewModel.getNotificationsEnabled()) }
            var tempNotifExpiry by remember { mutableStateOf(viewModel.getNotificationPref("notif_pref_expiry", true)) }
            var tempNotifLowStock by remember { mutableStateOf(viewModel.getNotificationPref("notif_pref_low_stock", true)) }
            var tempNotifRestockCutoff by remember { mutableStateOf(viewModel.getNotificationPref("notif_pref_restock_cutoff", true)) }
            var tempNotifRefill by remember { mutableStateOf(viewModel.getNotificationPref("notif_pref_refill", true)) }
            var tempNotifFollowup by remember { mutableStateOf(viewModel.getNotificationPref("notif_pref_followup", true)) }
            var tempNotifCycleCount by remember { mutableStateOf(viewModel.getNotificationPref("notif_pref_cycle_count", true)) }
            var tempNotifTaskAssignment by remember { mutableStateOf(viewModel.getNotificationPref("notif_pref_task_assignment", true)) }
            var tempNotifStockTransfer by remember { mutableStateOf(viewModel.getNotificationPref("notif_pref_stock_transfer", true)) }
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
                                Text("Twilio Messaging Engine", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Multi-Channel WhatsApp & SMS messaging is active and managed globally with fallback support and automated compliance rate limiting.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(14.dp))

                            Text("Notification Preferences", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(8.dp))

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "Master Notifications Toggle",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                "Master switch for all device notifications & background alerts",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Switch(
                                            checked = tempNotificationsEnabled,
                                            onCheckedChange = { tempNotificationsEnabled = it },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.White,
                                                checkedTrackColor = TealPrimary
                                            )
                                        )
                                    }

                                    if (tempNotificationsEnabled) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        HorizontalDivider()
                                        Spacer(modifier = Modifier.height(10.dp))

                                        Text(
                                            "Alert Categories",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = TealPrimary
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))

                                        // Category Item 1: Refills & Patient Reminders
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Customer Refill Reminders", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                                Text("Upcoming medication due dates & refill window alerts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Switch(
                                                checked = tempNotifRefill,
                                                onCheckedChange = { tempNotifRefill = it },
                                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = TealPrimary)
                                            )
                                        }

                                        // Category Item 2: Expiry & FEFO Risk
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Expiry & Aging Stock", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                                Text("Critical, urgent, and near-expiry FEFO notifications", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Switch(
                                                checked = tempNotifExpiry,
                                                onCheckedChange = { tempNotifExpiry = it },
                                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = TealPrimary)
                                            )
                                        }

                                        // Category Item 3: Low Stock & Reorders
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Low Stock & Restock Warnings", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                                Text("Threshold replenishment alerts & inventory dips", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Switch(
                                                checked = tempNotifLowStock,
                                                onCheckedChange = { tempNotifLowStock = it },
                                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = TealPrimary)
                                            )
                                        }

                                        // Category Item 4: Restock Order Cutoffs
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Restock Cutoff Reminders (3–6 PM)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                                Text("Daily supplier ordering window deadline warnings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Switch(
                                                checked = tempNotifRestockCutoff,
                                                onCheckedChange = { tempNotifRestockCutoff = it },
                                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = TealPrimary)
                                            )
                                        }

                                        // Category Item 5: Clinical Follow-ups & Interventions
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Clinical Inquiries & Interventions", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                                Text("Day 3/7/14 care protocol and custom follow-up alarms", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Switch(
                                                checked = tempNotifFollowup,
                                                onCheckedChange = { tempNotifFollowup = it },
                                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = TealPrimary)
                                            )
                                        }

                                        // Category Item 6: Cycle Count Audits
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Cycle Count & Inventory Audits", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                                Text("Randomized 5-item daily physical inventory audit tasks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Switch(
                                                checked = tempNotifCycleCount,
                                                onCheckedChange = { tempNotifCycleCount = it },
                                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = TealPrimary)
                                            )
                                        }

                                        // Category Item 7: Task Assignments
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Direct Task Assignments", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                                Text("Alerts when a manager assigns operational tasks directly to you", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Switch(
                                                checked = tempNotifTaskAssignment,
                                                onCheckedChange = { tempNotifTaskAssignment = it },
                                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = TealPrimary)
                                            )
                                        }

                                        // Category Item 8: Stock Transfers
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Inter-Branch Stock Transfers", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                                Text("Incoming stock transit & verification requests from other nodes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Switch(
                                                checked = tempNotifStockTransfer,
                                                onCheckedChange = { tempNotifStockTransfer = it },
                                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = TealPrimary)
                                            )
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
                            viewModel.setPharmacyName(tempPharmacyName)
                            viewModel.setPharmacyLga(tempPharmacyLga)
                            viewModel.setPharmacyState(tempPharmacyState)
                            viewModel.setNotificationsEnabled(tempNotificationsEnabled)
                            viewModel.setNotificationPref("notif_pref_expiry", tempNotifExpiry)
                            viewModel.setNotificationPref("notif_pref_low_stock", tempNotifLowStock)
                            viewModel.setNotificationPref("notif_pref_restock_cutoff", tempNotifRestockCutoff)
                            viewModel.setNotificationPref("notif_pref_refill", tempNotifRefill)
                            viewModel.setNotificationPref("notif_pref_followup", tempNotifFollowup)
                            viewModel.setNotificationPref("notif_pref_cycle_count", tempNotifCycleCount)
                            viewModel.setNotificationPref("notif_pref_task_assignment", tempNotifTaskAssignment)
                            viewModel.setNotificationPref("notif_pref_stock_transfer", tempNotifStockTransfer)
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

        csvImportSessionState?.let { session ->
            CsvImportDiscrepancyDialog(
                sessionState = session,
                onResolveAction = { action -> viewModel.resolveCsvDiscrepancy(action) },
                onDismiss = { viewModel.dismissCsvImportSession() }
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

        // --- Federated Cloud Sync Dialog (Polished Pop-up) ---
        if (showSyncStatusDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showSyncStatusDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Sync,
                            contentDescription = "Sync icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Federated Cloud Sync",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "This Careflux node uses local SQLite Room storage paired with an asynchronous background worker that automatically queues and streams transactions to the administrative Firestore cloud database, ensuring absolute offline capability during cellular network dropouts.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SlateTextMedium
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Embedded status card inside dialog
                        GlobalSyncStatusBanner(
                            isOnline = isOnline,
                            syncState = syncState,
                            lastSyncedTime = lastSyncedTime,
                            onManualSyncClick = { viewModel.triggerImmediateSync() }
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        val currentSyncState = syncState
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Technical Details:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            val syncStatusText = if (!isOnline) {
                                "Offline - Queued changes are stored safely in Room SQLite on-device."
                            } else when (currentSyncState) {
                                is com.example.ui.PharmacyViewModel.SyncState.Error -> "Connection blocked: ${currentSyncState.message}"
                                com.example.ui.PharmacyViewModel.SyncState.Syncing -> "Active - Writing transaction batch directly to node database..."
                                com.example.ui.PharmacyViewModel.SyncState.Synced -> "Synced - All local records are perfectly in sync with the administrative dashboard."
                            }
                            Text(
                                text = "• Status: $syncStatusText",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "• Network State: ${if (isOnline) "Connected" else "Disconnected"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSyncStatusDialog = false }) {
                        Text("Dismiss", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // --- Dashboard / Stats Section (Collapsible) ---
        val isCoreTab = activeTab == "inventory"
        if (isCoreTab) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                var isDashboardExpanded by remember { mutableStateOf(false) }
                val pendingRefills = remember(customerMeds) {
                    customerMeds.count { it.nextRefillDate < System.currentTimeMillis() + (7L * 24 * 60 * 60 * 1000) }
                }

                // Compact/Expandable Dashboard Toggle Bar
                CompactDashboardStatsBar(
                    medsCount = inventory.size,
                    lowStockCount = lowStockMeds.size,
                    todayVolume = volumes.firstOrNull()?.volume ?: 0,
                    pendingAlerts = pendingRefills,
                    isExpanded = isDashboardExpanded,
                    onToggleExpand = { isDashboardExpanded = !isDashboardExpanded }
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Expandable full widgets with sleek animated slide/fade transitions
                androidx.compose.animation.AnimatedVisibility(
                    visible = isDashboardExpanded,
                    enter = androidx.compose.animation.expandVertically(
                        animationSpec = androidx.compose.animation.core.tween(300)
                    ) + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.shrinkVertically(
                        animationSpec = androidx.compose.animation.core.tween(250)
                    ) + androidx.compose.animation.fadeOut()
                ) {
                    Column {
                        if (lowStockMeds.isNotEmpty()) {
                            LowStockBanner(
                                lowStockCount = lowStockMeds.size,
                                onClick = { isDashboardExpanded = false }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        StatsSection(
                            medsCount = inventory.size,
                            lowStockCount = lowStockMeds.size,
                            todayVolume = volumes.firstOrNull()?.volume ?: 0,
                            pendingAlerts = pendingRefills,
                            onLowStockClick = { activeTab = "procurement" }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }

        // --- Dynamic Content Zone with Translucent Bottom Nav Overlay ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 60.dp)
                    .padding(horizontal = 8.dp)
            ) {
                when (activeTab) {
                    "inventory" -> InventoryTabContent(
                        inventory = inventory,
                        isLoading = isInventoryLoading,
                        onMedSelect = { selectedMedForEdit = it },
                        onAddNewClick = { showAddMedDialog = true },
                        onDeleteClick = { item, reason -> viewModel.deleteInventory(item, reason) },
                        onIncrementClick = { item -> 
                            viewModel.updateStockLevel(item, item.stockQuantity + 10)
                            Toast.makeText(context, "Added 10 units to ${item.name}", Toast.LENGTH_SHORT).show()
                        },
                        onAddToCart = { item ->
                            if (item.stockQuantity <= 0) {
                                Toast.makeText(context, "Cannot add to cart: ${item.name} is out of stock!", Toast.LENGTH_LONG).show()
                            } else if (item.price <= 0.0) {
                                Toast.makeText(context, "Cannot add to cart: ${item.name} has no price configured!", Toast.LENGTH_LONG).show()
                            } else {
                                selectedMedForCart = item
                            }
                        },
                        onInitializeWorkspaceClick = {
                            viewModel.initializeBranchWorkspaceData { success, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        }
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
                        targetCustomerQuery = targetCustomerQuery,
                        initialSubTab = activeSubTab,
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
                        inventory = inventory,
                        viewModel = viewModel
                    )
                    "ai_tasks", "branch_team" -> com.example.ui.BranchTeamTab(
                        viewModel = viewModel,
                        initialSubTab = activeSubTab,
                        highlightTaskId = highlightTaskId
                    )
                    "cart" -> CartTabContent(
                        cartItems = cartItems,
                        deliveryFeeString = deliveryFeeString,
                        customers = customers,
                        onDeliveryFeeChange = { viewModel.setDeliveryFee(it) },
                        onRemoveItem = { viewModel.removeFromCart(it) },
                        onNeedRefillChange = { id, need -> viewModel.updateCartItemNeedsRefill(id, need) },
                        onCheckout = { customer, total, fileName, isInvoice, status, overrideReason, prescribingDoctor, prescriptionRef ->
                            if (fileName.isNotEmpty()) {
                                viewModel.addReceipt(customer?.name ?: "Guest", total, fileName, isInvoice, status)
                            }
                            if (customer != null) {
                                // Assign everything to customer treatment history on finalize
                                val nowMs = System.currentTimeMillis()
                                for (item in cartItems) {
                                    val rawName = item.inventoryItem.name
                                    val dosage = item.inventoryItem.dosage.trim()
                                    val formattedName = if (dosage.isNotBlank() && !dosage.equals("N/A", ignoreCase = true) && !rawName.contains(dosage, ignoreCase = true)) {
                                        "$rawName $dosage"
                                    } else {
                                        rawName
                                    }
                                    val cycleDays = if (item.needsRefill) 30 else 0
                                    val nextRefill = if (item.needsRefill) nowMs + (30L * 24 * 60 * 60 * 1000) else nowMs
                                    viewModel.addCustomerMedication(
                                        customerId = customer.id,
                                        invItemId = item.inventoryItem.id,
                                        medName = formattedName,
                                        customDosage = item.inventoryItem.dosage.ifBlank { "Standard Dosage" } + " (Qty: ${item.quantity})",
                                        cost = item.inventoryItem.price * item.quantity,
                                        cycleDays = cycleDays,
                                        nextRefill = nextRefill,
                                        dateAdded = nowMs
                                    )
                                }
                                Toast.makeText(context, "Saved items to ${customer.name}'s treatment history", Toast.LENGTH_SHORT).show()
                                if (customer.phoneNumber.isNotEmpty()) {
                                    val itemsSummary = cartItems.joinToString(", ") { "${it.inventoryItem.name} (x${it.quantity})" }
                                    scope.launch {
                                        val result = viewModel.sendTwilioDispenseConfirmation(
                                            patientName = customer.name,
                                            phone = customer.phoneNumber,
                                            itemsSummary = itemsSummary,
                                            amount = total
                                        )
                                        when (result) {
                                            is com.example.util.TwilioMessagingManager.DispatchResult.Success -> {
                                                Toast.makeText(context, "Dispense Receipt sent via ${result.channel}!", Toast.LENGTH_LONG).show()
                                            }
                                            is com.example.util.TwilioMessagingManager.DispatchResult.Blocked -> {
                                                android.util.Log.w("DispenseNotice", "Receipt blocked: ${result.reason}")
                                            }
                                            is com.example.util.TwilioMessagingManager.DispatchResult.Failed -> {
                                                android.util.Log.e("DispenseNotice", "Twilio receipt dispatch failed: ${result.error}")
                                            }
                                        }
                                    }
                                }
                            }
                            // Execute checkout atomically and clear cart only on success
                            scope.launch {
                                val checkoutResult = viewModel.completeCheckout(cartItems, customer, overrideReason, prescribingDoctor, prescriptionRef)
                                if (checkoutResult.isSuccess) {
                                    viewModel.clearCart()
                                    activeTab = "inventory"
                                    Toast.makeText(context, "Checkout completed successfully!", Toast.LENGTH_SHORT).show()
                                } else {
                                    val err = checkoutResult.exceptionOrNull()?.localizedMessage ?: "Checkout failed"
                                    Toast.makeText(context, "Checkout Failed: $err. Stock was not modified.", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        onClearCart = { viewModel.clearCartAndRestoreStock() },
                        context = context,
                        viewModel = viewModel
                    )
                    "receipts", "volumes" -> com.example.ui.AnalyticsTab(
                        viewModel = viewModel,
                        isUserAdmin = isUserAdmin
                    )
                    "analytics" -> com.example.ui.AnalyticsTab(viewModel = viewModel, isUserAdmin = isUserAdmin)
                    "customer_engagement" -> com.example.ui.CustomerEngagementTab(viewModel = viewModel)
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

            // --- Bottom Navigation Bar Overlay ---
            Box(
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                TabSelector(
                    activeTab = activeTab,
                    onTabSelected = { activeTab = it },
                    cartCount = cartItems.sumOf { it.quantity },
                    isCarefluxAiEnabled = isCarefluxAiEnabled
                )
            }
        }
    }
    } // End of ModalNavigationDrawer

    // --- Dialogs ---

    // 1. Add/Edit Medicine Dialog
    if (showAddMedDialog) {
        AddEditMedDialog(
            onDismiss = { showAddMedDialog = false },
            onConfirm = { name, dosage, stock, minStock, category, price, expiryDate, batch, supplier, imageUri, unitForm, brand, isFastMoving ->
                viewModel.addOrUpdateInventory(name = name, dosage = dosage, currentStock = stock, minStock = minStock, category = category, price = price, expiryDate = expiryDate, batchNumber = batch, supplier = supplier, imageUri = imageUri, unitForm = unitForm, brand = brand, isFastMoving = isFastMoving)
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
            viewModel = viewModel,
            onDismiss = { selectedMedForEdit = null },
            onConfirm = { name, dosage, stock, minStock, category, price, expiryDate, batch, supplier, imageUri, unitForm, brand, reason, isFastMoving ->
                viewModel.addOrUpdateInventory(name = name, dosage = dosage, currentStock = stock, minStock = minStock, category = category, price = price, id = item.id, expiryDate = expiryDate, batchNumber = batch, supplier = supplier, imageUri = imageUri, unitForm = unitForm, brand = brand, reason = reason, isFastMoving = isFastMoving)
                selectedMedForEdit = null
            }
        )
    }

    // 2. Log Daily Volume Dialog
    if (showLogVolumeDialog) {
        LogVolumeDialog(
            onDismiss = { showLogVolumeDialog = false },
            onConfirm = { date, count, notes, imgUri ->
                viewModel.logPrescriptionVolume(date, count, notes, imgUri)
                showLogVolumeDialog = false
            }
        )
    }

    // --- Post-Dispatch & Note-Alert Smart Dialogs ---
    if (postDispatchConfirmAlert != null) {
        RenderPostDispatchConfirmAlert(
            alert = postDispatchConfirmAlert!!,
            customers = customers,
            viewModel = viewModel,
            context = context,
            onDismiss = { viewModel.activePostDispatchConfirmAlert.value = null }
        )
    }

    if (postDispatchConfirmMedData != null) {
        RenderPostDispatchConfirmMedData(
            data = postDispatchConfirmMedData!!,
            viewModel = viewModel,
            context = context,
            onDismiss = { viewModel.activePostDispatchConfirm.value = null }
        )
    }

    // 3. Add Customer Dialog
    if (showAddCustomerDialog) {
        AddCustomerDialog(
            onDismiss = { showAddCustomerDialog = false },
            onConfirm = { name, phone, email, notes, age, gender, state, lga, city, consentPresc, consentSms, consentCloud, consentChan ->
                viewModel.addCustomer(
                    name = name,
                    phone = phone,
                    email = email,
                    notes = notes,
                    age = age,
                    gender = gender,
                    state = state,
                    lga = lga,
                    city = city,
                    consentPrescriptionTracking = consentPresc,
                    consentSmsRefills = consentSms,
                    consentCloudSync = consentCloud,
                    consentChannel = consentChan
                )
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

    // 6. Outbound SMS Failure Fallback Dialog
    lastFailedSmsLog?.let { failedLog ->
        AlertDialog(
            onDismissRequest = { viewModel.clearFailedSmsLog() },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(40.dp)
                )
            },
            title = {
                Text(
                    text = "SMS Dispatch Failed",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "The system attempted to send an SMS to ${failedLog.recipientPhone} but it failed or API credentials are not set.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Status: ${failedLog.deliveryStatus}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Message Content:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = failedLog.messageContent,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "COMMUNICATION SAFEGUARD: Use the fallback button below to quickly dispatch this pre-filled message via WhatsApp instead.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppThemeManager.slateTextMedium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                val url = "https://api.whatsapp.com/send?phone=${failedLog.recipientPhone}&text=${android.net.Uri.encode(failedLog.messageContent)}"
                                data = android.net.Uri.parse(url)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open WhatsApp. Copying message to clipboard instead.", Toast.LENGTH_LONG).show()
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Careflux SMS Backup", failedLog.messageContent)
                            clipboard.setPrimaryClip(clip)
                        }
                        viewModel.clearFailedSmsLog()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OKGreen, contentColor = Color.Black),
                    modifier = Modifier.testTag("whatsapp_fallback_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("WhatsApp Fallback")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.clearFailedSmsLog() },
                    modifier = Modifier.testTag("dismiss_sms_fallback_button")
                ) {
                    Text("Dismiss")
                }
            }
        )
    }
}

// ==========================================
// COMPONENT: Header
// ==========================================
@Composable
fun HeaderSection(
    cartCount: Int = 0,
    isAdmin: Boolean = true,
    isOnline: Boolean = true,
    syncState: com.example.ui.PharmacyViewModel.SyncState = com.example.ui.PharmacyViewModel.SyncState.Synced,
    onMenuClick: () -> Unit = {},
    onExportClick: () -> Unit = {},
    onReceiptsClick: () -> Unit = {},
    onCartClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onSyncStatusClick: () -> Unit = {}
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
            .padding(horizontal = 8.dp, vertical = 14.dp)
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

                // Tiny elegant sync status indicator
                val dotColor = if (!isOnline) {
                    Color(0xFFFF9800) // Amber
                } else when (syncState) {
                    is com.example.ui.PharmacyViewModel.SyncState.Error -> Color(0xFFF44336) // Red
                    com.example.ui.PharmacyViewModel.SyncState.Syncing -> Color(0xFF2196F3) // Blue
                    com.example.ui.PharmacyViewModel.SyncState.Synced -> Color(0xFF4CAF50) // Green
                }

                val syncLabel = if (!isOnline) {
                    "Offline Mode"
                } else when (syncState) {
                    is com.example.ui.PharmacyViewModel.SyncState.Error -> "Sync Error"
                    com.example.ui.PharmacyViewModel.SyncState.Syncing -> "Syncing..."
                    com.example.ui.PharmacyViewModel.SyncState.Synced -> "Synced"
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onSyncStatusClick() }
                        .background(dotColor.copy(alpha = 0.12f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(dotColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = syncLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = dotColor
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = "Sync Details",
                        tint = dotColor,
                        modifier = Modifier.size(10.dp)
                    )
                }
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
    pendingAlerts: Int,
    onLowStockClick: () -> Unit = {}
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
            onClick = onLowStockClick,
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
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = if (onClick != null) modifier.clickable { onClick() } else modifier,
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
    val isDark = com.example.ui.theme.AppThemeManager.isDark
    Surface(
        color = if (isDark) Color(0xFF0F172A).copy(alpha = 0.92f) else Color.White.copy(alpha = 0.95f),
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            HorizontalDivider(
                color = if (isDark) SlateBorderLight.copy(alpha = 0.3f) else Color(0xFFE2E8F0),
                thickness = 0.5.dp
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 0.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TabButton(
                    title = "Inventory",
                    icon = Icons.Filled.Inventory,
                    isActive = activeTab == "inventory",
                    onClick = { onTabSelected("inventory") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tab_inventory")
                )
                TabButton(
                    title = "Branch & Team",
                    icon = Icons.Filled.Group,
                    isActive = activeTab == "branch_team",
                    onClick = { onTabSelected("branch_team") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tab_branch_team")
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
    val isDark = com.example.ui.theme.AppThemeManager.isDark
    val activeContentColor = if (isActive) MaterialTheme.colorScheme.primary else if (isDark) SlateTextMedium.copy(alpha = 0.75f) else Color(0xFF64748B)

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = activeContentColor,
                modifier = Modifier.size(22.dp)
            )
            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-4).dp)
                        .size(16.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(com.example.ui.theme.WarningRed),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badgeCount.toString(),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            color = activeContentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ==========================================
// SCREEN COMS: Tab 1 - Inventory
// ==========================================
@Composable
fun InventoryTabContent(
    inventory: List<InventoryItem>,
    isLoading: Boolean = false,
    onMedSelect: (InventoryItem) -> Unit,
    onAddNewClick: () -> Unit,
    onDeleteClick: (InventoryItem, String) -> Unit,
    onIncrementClick: (InventoryItem) -> Unit,
    onAddToCart: (InventoryItem) -> Unit,
    onInitializeWorkspaceClick: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterCategory by remember { mutableStateOf("All") }
    var showOnlyLowStock by remember { mutableStateOf(false) }
    var itemToDeleteConfirm by remember { mutableStateOf<InventoryItem?>(null) }
    var deletionReason by remember { mutableStateOf("") }

    val categories = remember(inventory) {
        listOf("All") + inventory.map { it.category }.distinct().sorted()
    }

    val filteredList = remember(inventory, searchQuery, filterCategory, showOnlyLowStock) {
        inventory.filter { item ->
            val matchesSearch = item.name.contains(searchQuery, ignoreCase = true) ||
                    item.brand.contains(searchQuery, ignoreCase = true) ||
                    item.dosage.contains(searchQuery, ignoreCase = true) ||
                    item.category.contains(searchQuery, ignoreCase = true)
            val matchesCategory = filterCategory == "All" || item.category == filterCategory
            val matchesLowStock = !showOnlyLowStock || item.isLowStock
            matchesSearch && matchesCategory && matchesLowStock
        }
    }

    if (itemToDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { 
                itemToDeleteConfirm = null 
                deletionReason = ""
            },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Confirm Delete Product",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Are you sure you want to completely remove \"${itemToDeleteConfirm?.name}\" from the pharmacy inventory? This action is irreversible and will permanently delete all logs of its stock levels.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Audit Justification Required",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    
                    OutlinedTextField(
                        value = deletionReason,
                        onValueChange = { deletionReason = it },
                        label = { Text("Reason for deletion") },
                        placeholder = { Text("e.g. Expired batch, supplier recall, damaged stock") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = deletionReason.trim().isEmpty()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        itemToDeleteConfirm?.let { onDeleteClick(it, deletionReason.trim()) }
                        itemToDeleteConfirm = null
                        deletionReason = ""
                    },
                    enabled = deletionReason.trim().isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text("Delete Product")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        itemToDeleteConfirm = null 
                        deletionReason = ""
                    },
                    modifier = Modifier.testTag("cancel_delete_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Search Input (Using BasicTextField for robust vertical alignment and clipping-free 48dp height styling)
            androidx.compose.foundation.text.BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp
                ),
                singleLine = true,
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("med_search_input"),
                decorationBox = { innerTextField ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search meds...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                                    fontSize = 13.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                }
            )

            // Add Med button (Cohesive 48dp size)
            IconButton(
                onClick = onAddNewClick,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .testTag("add_item_button")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add New Medicine")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Unified Horizontal Filter Ribbon (saves an entire line!)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Filter:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 2.dp)
            )

            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Interactive warning low-stock chip
                item {
                    val activeLowStock = showOnlyLowStock
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (activeLowStock) WarningRed 
                                else WarningRed.copy(alpha = 0.08f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (activeLowStock) Color.Transparent else WarningRed.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { showOnlyLowStock = !showOnlyLowStock }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = if (activeLowStock) Color.White else WarningRed,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "Low Stock",
                                color = if (activeLowStock) Color.White else WarningRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Ribbon Divider
                item {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(20.dp)
                            .background(SlateBorderLight)
                    )
                }

                // Category badges
                items(categories) { cat ->
                    val isSelected = cat == filterCategory
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
                            .clickable { filterCategory = cat }
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

        Spacer(modifier = Modifier.height(10.dp))

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading Stock Data...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        } else if (filteredList.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                EmptyStatePlaceholder(
                    message = "No matching medicines in stock logs.",
                    tip = "Tap the + button to catalog a new medication line."
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredList) { item ->
                    InventoryCard(
                        item = item,
                        onClick = { onMedSelect(item) },
                        onDelete = { itemToDeleteConfirm = item },
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
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerBgColor
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = cardBorderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Compact Image Box
                if (item.imageUri != null) {
                    coil.compose.AsyncImage(
                        model = item.imageUri,
                        contentDescription = "Medicine Image",
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocalPharmacy,
                            contentDescription = "Medicine",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Info Section
                Column(modifier = Modifier.weight(1f)) {
                    // Title & Action Icons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onAddToCart, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.AddShoppingCart, "Cart", tint = TealPrimary, modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.Delete, "Delete", tint = SlateTextMedium, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // Brand & Category Subtitle
                    val brandCatText = buildString {
                        if (item.brand.isNotBlank()) append(item.brand)
                        if (item.brand.isNotBlank() && item.category.isNotBlank()) append(" • ")
                        if (item.category.isNotBlank()) append(item.category)
                    }
                    if (brandCatText.isNotBlank()) {
                        Text(
                            text = brandCatText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Tags & Price Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Dosage & UnitForm Compact Pills
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            if (item.dosage.isNotBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Text(
                                        text = item.dosage,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (item.isFastMoving) {
                                Surface(
                                    color = Color(0xFFFEF3C7),
                                    contentColor = Color(0xFF92400E),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Text(
                                        text = "⚡ Fast",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (item.unitForm.isNotBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Text(
                                        text = item.unitForm,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Text(
                            text = "₦${"%,.2f".format(item.price)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(thickness = 0.5.dp, color = SlateBorderLight.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(4.dp))

            // Footer for stock logic (Single Line)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Stock: ", style = MaterialTheme.typography.labelMedium, color = SlateTextMedium)
                    Text("${item.stockQuantity} units", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = textAmtColor)
                    Text(" (Min: ${item.minRequiredStock})", style = MaterialTheme.typography.labelSmall, color = SlateTextMedium.copy(alpha = 0.7f))
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.isLowStock) {
                        Surface(
                            color = WarningRedContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "LOW STOCK",
                                style = MaterialTheme.typography.labelSmall,
                                color = WarningRed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    TextButton(
                        onClick = onIncrementTen,
                        colors = ButtonDefaults.textButtonColors(containerColor = TealSecondary.copy(alpha = 0.6f), contentColor = TealPrimary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(10.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Add 10", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, fontSize = 10.sp)
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
    var selectedVolumeLogForDetails by remember { mutableStateOf<DailyPrescriptionVolume?>(null) }

    if (selectedVolumeLogForDetails != null) {
        VolumeLogDetailsDialog(
            log = selectedVolumeLogForDetails!!,
            onDismiss = { selectedVolumeLogForDetails = null }
        )
    }

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
                    VolumeLogCard(
                        log = log,
                        onDelete = { onDeleteVolume(log) },
                        onCardClick = { selectedVolumeLogForDetails = log }
                    )
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
    onDelete: () -> Unit,
    onCardClick: () -> Unit
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
            .clickable(onClick = onCardClick)
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = log.dateString,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (log.imageUri != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Filled.Attachment,
                            contentDescription = "Has image attachment",
                            tint = TealPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
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
                onClick = {
                    onDelete()
                },
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

@Composable
fun VolumeLogDetailsDialog(
    log: DailyPrescriptionVolume,
    onDismiss: () -> Unit
) {
    var showLightbox by remember { mutableStateOf(false) }

    if (showLightbox && log.imageUri != null) {
        VolumeLogImageLightbox(
            imageUri = log.imageUri,
            onDismiss = { showLightbox = false }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.CalendarToday,
                        contentDescription = null,
                        tint = TealPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = log.dateString,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(OKGreenContainer)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${log.volume} Rx",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = TealPrimary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Peak Efficiency Rating Banner
                val workloadText = when {
                    log.volume < 10 -> "Light Shift Demand"
                    log.volume <= 25 -> "Highly Efficient Coverage"
                    else -> "Extremely Busy Workflow Peak"
                }
                val workloadColor = when {
                    log.volume < 10 -> MaterialTheme.colorScheme.outline
                    log.volume <= 25 -> OKGreen
                    else -> Color(0xFFE65100)
                }
                val workloadBg = when {
                    log.volume < 10 -> MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                    log.volume <= 25 -> OKGreenContainer
                    else -> Color(0xFFFFE0B2)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(workloadBg)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Verified,
                        contentDescription = null,
                        tint = workloadColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = workloadText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = workloadColor
                    )
                }

                // Image/Attachment Section
                if (log.imageUri != null) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "SHIFT ATTACHMENT",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, SlateBorderLight, RoundedCornerShape(12.dp))
                                .clickable { showLightbox = true },
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                coil.compose.AsyncImage(
                                    model = log.imageUri,
                                    contentDescription = "Prescription report photo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black.copy(alpha = 0.6f))
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.ZoomIn,
                                            contentDescription = "Zoom",
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Tap to enlarge",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Notes Section
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "SHIFT LOG REPORT (MARKDOWN)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    if (log.notes.isBlank()) {
                        Text(
                            text = "No shift report notes captured for this shift.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = SlateTextMedium
                        )
                    } else {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, SlateBorderLight, RoundedCornerShape(12.dp))
                        ) {
                            Box(modifier = Modifier.padding(12.dp)) {
                                CompactMarkdownView(text = log.notes)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close Details")
            }
        }
    )
}

@Composable
fun VolumeLogImageLightbox(
    imageUri: String,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.2f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            coil.compose.AsyncImage(
                model = imageUri,
                contentDescription = "Enlarged Shift Photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close zoom",
                    tint = Color.White
                )
            }
        }
    }
}

fun parseMarkdownToAnnotatedString(text: String, primaryColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            if (text.startsWith("**", i)) {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append("**")
                    i += 2
                }
            } else if (text.startsWith("*", i)) {
                val end = text.indexOf("*", i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append("*")
                    i += 1
                }
            } else if (text.startsWith("`", i)) {
                val end = text.indexOf("`", i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = primaryColor.copy(alpha = 0.1f),
                        color = primaryColor
                    )) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append("`")
                    i += 1
                }
            } else {
                append(text[i])
                i++
            }
        }
    }
}

@Composable
fun CompactMarkdownView(text: String) {
    val lines = text.split("\n")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        lines.forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("### ") -> {
                    Text(
                        text = parseMarkdownToAnnotatedString(trimmed.removePrefix("### "), MaterialTheme.colorScheme.primary),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }
                trimmed.startsWith("## ") -> {
                    Text(
                        text = parseMarkdownToAnnotatedString(trimmed.removePrefix("## "), MaterialTheme.colorScheme.primary),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 6.dp, bottom = 3.dp)
                    )
                }
                trimmed.startsWith("# ") -> {
                    Text(
                        text = parseMarkdownToAnnotatedString(trimmed.removePrefix("# "), MaterialTheme.colorScheme.primary),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    val listContent = if (trimmed.startsWith("- ")) trimmed.removePrefix("- ") else trimmed.removePrefix("* ")
                    Row(
                        modifier = Modifier.padding(start = 8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = parseMarkdownToAnnotatedString(listContent, MaterialTheme.colorScheme.primary),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                trimmed.startsWith("> ") -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp, horizontal = 4.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
                            .border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)
                            )
                            .padding(8.dp)
                    ) {
                        Text(
                            text = parseMarkdownToAnnotatedString(trimmed.removePrefix("> "), MaterialTheme.colorScheme.primary),
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    if (trimmed.isNotEmpty()) {
                        Text(
                            text = parseMarkdownToAnnotatedString(trimmed, MaterialTheme.colorScheme.primary),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
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
    onConfirm: (String, String, Int, Int, String, Double, Long?, String, String, String?, String, String, Boolean) -> Unit
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
    var isFastMoving by remember { mutableStateOf(false) }
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
    var showDiscardConfirm by remember { mutableStateOf(false) }

    val isFormDirty = name.isNotBlank() ||
                      brand.isNotBlank() ||
                      dosage != "500mg" ||
                      unitForm.isNotBlank() ||
                      stock != "50" ||
                      minStock != "15" ||
                      category != "Antibiotic" ||
                      price != "0.0" ||
                      expiryDateStr.isNotBlank() ||
                      batchNumber.isNotBlank() ||
                      supplier.isNotBlank() ||
                      imageUri != null

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

                // High Velocity Fast Moving Switch Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isFastMoving) Color(0xFFFEF3C7) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("⚡ High-Velocity Product", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (isFastMoving) Color(0xFF92400E) else MaterialTheme.colorScheme.onSurface)
                        Text("Triggers 7-day rolling cycle count audits instead of standard 14-day cycle", fontSize = 10.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = isFastMoving,
                        onCheckedChange = { isFastMoving = it }
                    )
                }

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
                        onConfirm(name, dosage, stockVal, minStockVal, category, priceVal, parsedExpiry, batchNumber, supplier, imageUri, unitForm, brand, isFastMoving)
                    }
                }
            ) {
                Text("Catalog Medicine")
            }
        },
        dismissButton = {
            TextButton(onClick = { if (isFormDirty) showDiscardConfirm = true else onDismiss() }) {
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
    viewModel: PharmacyViewModel,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int, Int, String, Double, Long?, String, String, String?, String, String, String, Boolean) -> Unit
) {
    var name by remember { mutableStateOf(item.name) }
    var dosage by remember { mutableStateOf(item.dosage) }
    var unitForm by remember { mutableStateOf(item.unitForm) }
    var brand by remember { mutableStateOf(item.brand) }
    var stock by remember { mutableStateOf(item.stockQuantity.toString()) }
    var minStock by remember { mutableStateOf(item.minRequiredStock.toString()) }
    var category by remember { mutableStateOf(item.category) }
    var price by remember { mutableStateOf(item.price.toString()) }
    var adjustmentReason by remember { mutableStateOf("") }
    var isFastMoving by remember { mutableStateOf(item.isFastMoving) }
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
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

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
                modifier = Modifier.verticalScroll(scrollState)
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

                // High Velocity Fast Moving Switch Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isFastMoving) Color(0xFFFEF3C7) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("⚡ High-Velocity Product", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (isFastMoving) Color(0xFF92400E) else MaterialTheme.colorScheme.onSurface)
                        Text("Triggers 7-day rolling cycle count audits instead of standard 14-day cycle", fontSize = 10.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = isFastMoving,
                        onCheckedChange = { isFastMoving = it }
                    )
                }

                val currentStockVal = stock.toIntOrNull() ?: 0
                val stockChanged = currentStockVal != item.stockQuantity

                if (stockChanged) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Security, 
                                    contentDescription = null, 
                                    tint = MaterialTheme.colorScheme.error, 
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Audit Justification Required",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Text(
                                text = "You are modifying stock level from ${item.stockQuantity} to $currentStockVal. Please specify an audit justification.",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            val predefinedReasons = listOf(
                                "Physical stock count",
                                "Damaged product",
                                "Expired product removed",
                                "Supplier shortage",
                                "Theft/Loss",
                                "Stock transfer",
                                "Initial inventory correction",
                                "Other"
                            )
                            var selectedPredefinedReason by remember { mutableStateOf<String?>(null) }
                            var customReasonText by remember { mutableStateOf("") }

                            LaunchedEffect(selectedPredefinedReason, customReasonText) {
                                adjustmentReason = if (selectedPredefinedReason == "Other") {
                                    customReasonText
                                } else {
                                    selectedPredefinedReason ?: ""
                                }
                            }

                            Text(
                                text = "Select Standardized Audit Reason:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            // Horizontal Chip Flow Row for Predefined Audit Reasons
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                predefinedReasons.forEach { reason ->
                                    val isSelected = selectedPredefinedReason == reason
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { selectedPredefinedReason = reason },
                                        label = { Text(reason, fontSize = 10.5.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                            selectedLabelColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }
                            }

                            if (selectedPredefinedReason == "Other") {
                                OutlinedTextField(
                                    value = customReasonText,
                                    onValueChange = { customReasonText = it },
                                    label = { Text("Specify Custom Audit Justification") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    isError = customReasonText.trim().isEmpty()
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "COMPLIANCE STOCK MANAGEMENT",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = Color(0xFF10B981),
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Log immutable transactions for transfers, returns, or spoilage write-offs.",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                var showTransferForm by remember { mutableStateOf(false) }
                var showReturnForm by remember { mutableStateOf(false) }
                var showWriteoffForm by remember { mutableStateOf(false) }

                // 1. Branch Transfer Form
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { showTransferForm = !showTransferForm },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = Color(0xFF10B981))
                                Text("Inter-Branch Stock Transfer", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Icon(if (showTransferForm) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                        }
                        if (showTransferForm) {
                            Spacer(modifier = Modifier.height(8.dp))
                            var destinationBranch by remember { mutableStateOf("") }
                            var transferQty by remember { mutableStateOf("") }
                            var transferReason by remember { mutableStateOf("") }

                            val isItemExpired = item.expiryDate > 0L && item.expiryDate <= System.currentTimeMillis()

                            if (isItemExpired) {
                                androidx.compose.material3.Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "⚠️ COMPLIANCE BLOCK: This medication batch has EXPIRED and cannot be transferred to any other branch.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = destinationBranch,
                                onValueChange = { destinationBranch = it },
                                label = { Text("Destination Branch Code/Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isItemExpired
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = transferQty,
                                onValueChange = { transferQty = it },
                                label = { Text("Quantity to Transfer") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isItemExpired
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = transferReason,
                                onValueChange = { transferReason = it },
                                label = { Text("Authorized Reason / Approval Ref") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isItemExpired
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    val qty = transferQty.toIntOrNull() ?: 0
                                    if (destinationBranch.isBlank() || qty <= 0 || transferReason.isBlank()) {
                                        android.widget.Toast.makeText(appContext, "Please enter all transfer parameters", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        viewModel.performBranchTransfer(item, qty, destinationBranch, transferReason)
                                        showTransferForm = false
                                        stock = (item.stockQuantity - qty).coerceAtLeast(0).toString()
                                    }
                                },
                                enabled = !isItemExpired,
                                modifier = Modifier.align(Alignment.End),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                            ) {
                                Text("Execute Transfer", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }

                // 2. Customer Return Form
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { showReturnForm = !showReturnForm },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.KeyboardReturn, contentDescription = null, tint = Color(0xFF10B981))
                                Text("Customer Product Return", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Icon(if (showReturnForm) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                        }
                        if (showReturnForm) {
                            Spacer(modifier = Modifier.height(8.dp))
                            var customerName by remember { mutableStateOf("") }
                            var returnQty by remember { mutableStateOf("") }
                            var returnReason by remember { mutableStateOf("") }

                            OutlinedTextField(
                                value = customerName,
                                onValueChange = { customerName = it },
                                label = { Text("Customer Name / Receipt Info") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = returnQty,
                                onValueChange = { returnQty = it },
                                label = { Text("Returned Quantity") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = returnReason,
                                onValueChange = { returnReason = it },
                                label = { Text("Return Dispensation Justification") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    val qty = returnQty.toIntOrNull() ?: 0
                                    if (customerName.isBlank() || qty <= 0 || returnReason.isBlank()) {
                                        android.widget.Toast.makeText(appContext, "Please enter all return parameters", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        viewModel.performReturn(item, qty, customerName, returnReason)
                                        showReturnForm = false
                                        stock = (item.stockQuantity + qty).toString()
                                    }
                                },
                                modifier = Modifier.align(Alignment.End),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                            ) {
                                Text("Log Return", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }

                // 3. Expiry / Damaged Write-off Form
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { showWriteoffForm = !showWriteoffForm },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color(0xFF10B981))
                                Text("Expiry / Spoilage Write-Off", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Icon(if (showWriteoffForm) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                        }
                        if (showWriteoffForm) {
                            Spacer(modifier = Modifier.height(8.dp))
                            var writeoffQty by remember { mutableStateOf("") }
                            var writeoffReason by remember { mutableStateOf("") }

                            OutlinedTextField(
                                value = writeoffQty,
                                onValueChange = { writeoffQty = it },
                                label = { Text("Quantity to Write Off") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = writeoffReason,
                                onValueChange = { writeoffReason = it },
                                label = { Text("Spoilage / Damage Audit Documentation") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    val qty = writeoffQty.toIntOrNull() ?: 0
                                    if (qty <= 0 || writeoffReason.isBlank()) {
                                        android.widget.Toast.makeText(appContext, "Please enter write-off metrics and justification", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        viewModel.performExpiryWriteOff(item, qty, writeoffReason)
                                        showWriteoffForm = false
                                        stock = (item.stockQuantity - qty).coerceAtLeast(0).toString()
                                    }
                                },
                                modifier = Modifier.align(Alignment.End),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                            ) {
                                Text("Log Stock Loss", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val stockVal = stock.toIntOrNull() ?: 0
                    val stockChanged = stockVal != item.stockQuantity
                    if (name.isBlank()) {
                        isError = true
                    } else if (stockChanged && adjustmentReason.trim().isBlank()) {
                        android.widget.Toast.makeText(appContext, "Compliance error: Please select an audit justification reason or specify one before saving stock changes.", android.widget.Toast.LENGTH_LONG).show()
                        coroutineScope.launch {
                            scrollState.animateScrollTo(scrollState.maxValue)
                        }
                    } else {
                        val minStockVal = minStock.toIntOrNull() ?: 10
                        val priceVal = price.toDoubleOrNull() ?: 0.0
                        var parsedExpiry: Long? = null
                        if (expiryDateStr.isNotBlank()) {
                            try {
                                parsedExpiry = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).parse(expiryDateStr)?.time
                            } catch (e: Exception) { }
                        }
                        onConfirm(name, dosage, stockVal, minStockVal, category, priceVal, parsedExpiry, batchNumber, supplier, imageUri, unitForm, brand, adjustmentReason.trim(), isFastMoving)
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
// ==========================================
// DIALOG: 3. Log Daily Volume Dialog
// ==========================================
@Composable
fun LogVolumeDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Int, String, String?) -> Unit
) {
    val appContext = LocalContext.current
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    var dateString by remember { mutableStateOf(sdf.format(Date())) }
    var volumeString by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<String?>(null) }

    var isError by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }

    val imageLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            val savedUri = saveImageToInternalStorage(appContext, uri)
            imageUri = savedUri?.toString()
        }
    }

    val isFormDirty = volumeString.isNotBlank() || notes.isNotBlank() || imageUri != null

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.DriveFileRenameOutline,
                    contentDescription = null,
                    tint = TealPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Log Daily Shift Rx Volume",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
                    leadingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null, tint = TealPrimary) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = volumeString,
                    onValueChange = { volumeString = it; isError = false },
                    label = { Text("Prescriptions Filled (Count)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Tag, contentDescription = null, tint = TealPrimary) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Shift Notes (Markdown Supported!)") },
                    placeholder = { Text("Use **bold**, # titles, - bullet points...") },
                    maxLines = 8,
                    leadingIcon = { Icon(Icons.Filled.EditNote, contentDescription = null, tint = TealPrimary) },
                    modifier = Modifier.fillMaxWidth()
                )

                // Live Markdown Preview Card
                if (notes.isNotBlank()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Live Markdown Preview",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = TealPrimary
                            )
                            Text(
                                text = "Rich Formatting Active",
                                style = MaterialTheme.typography.labelSmall,
                                fontStyle = FontStyle.Italic,
                                color = OKGreen
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, SlateBorderLight, RoundedCornerShape(12.dp))
                        ) {
                            Box(modifier = Modifier.padding(10.dp)) {
                                CompactMarkdownView(text = notes)
                            }
                        }
                    }
                }

                // Photo attachment section
                Text(
                    text = "Shift Image Attachment (Optional)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.outline
                )

                if (imageUri == null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .border(
                                width = 1.dp,
                                color = SlateBorderLight,
                                shape = RoundedCornerShape(12.dp)
                            ),
                        onClick = { imageLauncher.launch("image/*") }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AddAPhoto,
                                contentDescription = null,
                                tint = TealPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Choose Report Photo from Device",
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateTextMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Pre-made clinical templates for instant evaluation/convenience
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Or attach pre-set clinical mock template:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val templates = listOf(
                            "📝 Report" to "https://picsum.photos/id/201/600/400",
                            "📊 Chart" to "https://picsum.photos/id/180/600/400",
                            "🖥️ Log" to "https://picsum.photos/id/60/600/400"
                        )
                        templates.forEach { (label, url) ->
                            AssistChip(
                                onClick = { imageUri = url },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, SlateBorderLight, RoundedCornerShape(12.dp))
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            coil.compose.AsyncImage(
                                model = imageUri,
                                contentDescription = "Selected photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                            IconButton(
                                onClick = { imageUri = null },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                    .size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Remove photo",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
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
                    val count = volumeString.toIntOrNull()
                    if (count == null || count < 0) {
                        isError = true
                    } else {
                        onConfirm(dateString, count, notes, imageUri)
                    }
                }
            ) {
                Text("Save Log Entry")
            }
        },
        dismissButton = {
            TextButton(onClick = { if (isFormDirty) showDiscardConfirm = true else onDismiss() }) {
                Text("Cancel")
            }
        }
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
            val parsedItems = mutableListOf<com.example.ui.CsvProductImportItem>()
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

                        parsedItems.add(
                            com.example.ui.CsvProductImportItem(
                                csvId = idVal,
                                name = nameStr,
                                brand = brandStr,
                                dosage = dosageStr,
                                category = categoryStr,
                                stockQuantity = stock,
                                threshold = minStock,
                                price = price,
                                expiryDate = expiryL,
                                batchNumber = batchNumberStr,
                                supplier = supplierStr,
                                unitForm = unitFormStr
                            )
                        )
                    }
                }
            }
            
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (parsedItems.isEmpty()) {
                    android.widget.Toast.makeText(context, "No valid product records found in CSV", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.prepareCsvProductImport(parsedItems)
                }
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
    val confirmedItem = if (recommendation != null && useRecommendation) recommendation else item
    val qParsed = quantity.toIntOrNull()

    val isExpired = confirmedItem.expiryDate > 0L && confirmedItem.expiryDate <= System.currentTimeMillis()

    val errorMessage = when {
        isExpired -> "CRITICAL COMPLIANCE BLOCK: This medication batch has EXPIRED and cannot be added to the sales cart or sold."
        quantity.isBlank() -> "Please enter a quantity"
        qParsed == null || qParsed <= 0 -> "Quantity must be greater than 0"
        qParsed > confirmedItem.stockQuantity -> "Exceeds available stock (${confirmedItem.stockQuantity} available)"
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Add to Cart", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text("Item: ${item.name}", style = MaterialTheme.typography.bodyMedium)
                Text("Price: ₦${"%,.2f".format(item.price)}", style = MaterialTheme.typography.bodyMedium)
                Text("Available Stock: ${confirmedItem.stockQuantity}", style = MaterialTheme.typography.bodySmall, color = AppThemeManager.slateTextMedium)
                
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
                    isError = errorMessage != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (qParsed != null && errorMessage == null) {
                        onConfirm(confirmedItem, qParsed)
                    }
                },
                enabled = errorMessage == null
            ) { Text("Add") }
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
    onCheckout: (Customer?, Double, String, Boolean, String, String, String, String) -> Unit,
    onClearCart: () -> Unit,
    context: android.content.Context,
    viewModel: com.example.ui.PharmacyViewModel
) {
    var selectedCustomer by remember { mutableStateOf<Customer?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var customerSearchQuery by remember { mutableStateOf("") }

    val filteredCustomers = remember(customers, customerSearchQuery) {
        if (customerSearchQuery.isBlank()) {
            customers
        } else {
            val q = customerSearchQuery.trim()
            customers.filter { cust ->
                cust.name.contains(q, ignoreCase = true) ||
                cust.phoneNumber.contains(q, ignoreCase = true) ||
                cust.email.contains(q, ignoreCase = true) ||
                cust.city.contains(q, ignoreCase = true)
            }
        }
    }

    val hasExpiredItem = remember(cartItems) { 
        cartItems.any { it.inventoryItem.expiryDate > 0L && it.inventoryItem.expiryDate <= System.currentTimeMillis() } 
    }

    var deliveryAddress by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("Cash") }

    // Dynamic delivery check state
    var needsDelivery by remember { mutableStateOf(false) }

    // Clinical Override and Prescription compliance state variables
    var overrideJustification by remember { mutableStateOf("") }
    var prescribingDoctor by remember { mutableStateOf("") }
    var prescriptionRef by remember { mutableStateOf("") }
    var showPrescriptionFields by remember { mutableStateOf(false) }

    LaunchedEffect(deliveryFeeString) {
        val parsedFee = deliveryFeeString.toDoubleOrNull() ?: 0.0
        if (parsedFee > 0.0) {
            needsDelivery = true
        }
    }

    val deliveryFee = if (needsDelivery) (deliveryFeeString.toDoubleOrNull() ?: 0.0) else 0.0
    val subtotal = cartItems.sumOf { it.inventoryItem.price * it.quantity }
    val total = subtotal + deliveryFee

    val cartWarnings = remember(cartItems, selectedCustomer) {
        val names = cartItems.map { it.inventoryItem.name }
        com.example.data.ClinicalDdiEngine.checkInteractions(names, selectedCustomer)
    }

    var showClearCartConfirm by remember { mutableStateOf(false) }

    if (showClearCartConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCartConfirm = false },
            icon = {
                Icon(
                    imageVector = Icons.Filled.DeleteSweep,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Clear Cart?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to remove all items from your current cart? This will also return their quantities back to the stock.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearCart()
                        showClearCartConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_clear_cart_button")
                ) {
                    Text("Clear Cart")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearCartConfirm = false },
                    modifier = Modifier.testTag("cancel_clear_cart_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(bottom = 16.dp)) {
        if (cartItems.isEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ShoppingBag,
                    contentDescription = null,
                    tint = TealPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Current Cart",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            EmptyStatePlaceholder(message = "Cart is empty", tip = "Add items from the Stock tab.")
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.ShoppingBag,
                                contentDescription = null,
                                tint = TealPrimary,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Current Cart",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Review items and select distribution parameters",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppThemeManager.slateTextMedium
                        )
                    }
                    
                    Column(horizontalAlignment = Alignment.End) {
                        // Clear Cart button
                        androidx.compose.material3.TextButton(
                            onClick = { showClearCartConfirm = true },
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp).testTag("clear_cart_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Clear all",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear Cart", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        // Cute pill showing total items inside the cart
                        androidx.compose.material3.Surface(
                            color = TealPrimary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(
                                text = "${cartItems.size} ${if (cartItems.size == 1) "Med" else "Meds"}",
                                color = TealPrimary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                if (cartWarnings.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(12.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = "Dispensing Warning Icon",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Clinical Contraindication Warning!",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            cartWarnings.forEach { warning ->
                                Text(
                                    text = warning,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    lineHeight = 16.sp,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = overrideJustification,
                                onValueChange = { overrideJustification = it },
                                label = { Text("Clinical Override Justification *", fontSize = 11.sp, color = MaterialTheme.colorScheme.onErrorContainer) },
                                placeholder = { Text("e.g. Doctor approved co-administration, patient is under observation...") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.error,
                                    focusedLabelColor = MaterialTheme.colorScheme.error,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                                    focusedTextColor = MaterialTheme.colorScheme.onErrorContainer,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onErrorContainer
                                ),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("override_justification_input")
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Compliance mandate: An override reason (min 5 characters) is required to dispense these interacting medications.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                lineHeight = 12.sp
                            )
                        }
                    }
                }
            }

            items(cartItems) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppThemeManager.slateBorderLight)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            // Weight-allocated column to prevent text overlapping or text break-ups
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.inventoryItem.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Unit Cost: ₦${"%,.2f".format(item.inventoryItem.price)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppThemeManager.slateTextMedium
                                )
                                Text(
                                    text = "Quantity Selected: ${item.quantity}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppThemeManager.slateTextMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // Dynamic, highly structured Price & Delete Column
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = { onRemoveItem(item.inventoryItem.id) },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "Remove",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "₦${"%,.2f".format(item.inventoryItem.price * item.quantity)}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TealPrimary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = AppThemeManager.slateBorderLight.copy(alpha = 0.5f))

                        // Custom Interactive Clickable Refill Toggle Chip
                        androidx.compose.material3.Surface(
                            onClick = { onNeedRefillChange(item.inventoryItem.id, !item.needsRefill) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (item.needsRefill) TealPrimary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                color = if (item.needsRefill) TealPrimary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .align(Alignment.Start)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = if (item.needsRefill) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (item.needsRefill) TealPrimary else AppThemeManager.slateTextMedium,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Continuous Rx Auto-Refill Active",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = if (item.needsRefill) TealPrimary else AppThemeManager.slateTextMedium
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(6.dp))

                // Unified settings card of dispatch configuration
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppThemeManager.slateBorderLight)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = null,
                                tint = TealPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "LOGISTICS & DISPATCH SETTINGS",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        // Logistics delivery toggle switch using beautiful custom selection chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val modes = listOf(
                                false to "In-Store Pickup",
                                true to "Home Delivery"
                            )
                            modes.forEach { (isDelivery, label) ->
                                val isSelected = needsDelivery == isDelivery
                                androidx.compose.material3.Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) TealPrimary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = 1.dp,
                                        color = if (isSelected) TealPrimary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                    ),
                                    onClick = {
                                        needsDelivery = isDelivery
                                        if (!isDelivery) {
                                            onDeliveryFeeChange("0")
                                        } else if (deliveryFeeString.isEmpty() || deliveryFeeString == "0") {
                                            onDeliveryFeeChange("1500") // set standard delivery fee
                                        }
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isDelivery) Icons.Filled.LocalShipping else Icons.Filled.ShoppingBag,
                                            contentDescription = null,
                                            tint = if (isSelected) TealPrimary else AppThemeManager.slateTextMedium,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) TealPrimary else AppThemeManager.slateTextMedium
                                        )
                                    }
                                }
                            }
                        }

                        // Input fields specifically for Active Shipping Address if shipping is needed
                        if (needsDelivery) {
                            OutlinedTextField(
                                value = deliveryFeeString,
                                onValueChange = onDeliveryFeeChange,
                                label = { Text("Delivery Courier Fee (₦)") },
                                leadingIcon = { Icon(Icons.Filled.LocalShipping, contentDescription = null, tint = TealPrimary) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = deliveryAddress,
                                onValueChange = { deliveryAddress = it },
                                label = { Text("Client Destination Address") },
                                placeholder = { Text("Specify complete street shipping location") },
                                leadingIcon = { Icon(Icons.Filled.Place, contentDescription = null, tint = TealPrimary) },
                                shape = RoundedCornerShape(12.dp),
                                isError = deliveryAddress.isBlank(),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        HorizontalDivider(color = AppThemeManager.slateBorderLight.copy(alpha = 0.5f))

                        // Interactive payment selectors using choice pills
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Select Financial Settlement Mode",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val paymentModes = listOf("Cash", "Transfer", "POS")
                                paymentModes.forEach { payMode ->
                                    val isSelected = paymentMethod == payMode
                                    androidx.compose.material3.Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) TealPrimary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                        border = androidx.compose.foundation.BorderStroke(
                                            width = 1.dp,
                                            color = if (isSelected) TealPrimary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                        ),
                                        onClick = { paymentMethod = payMode }
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxSize(),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.Payment,
                                                contentDescription = null,
                                                tint = if (isSelected) TealPrimary else AppThemeManager.slateTextMedium,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = payMode,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) TealPrimary else AppThemeManager.slateTextMedium
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = AppThemeManager.slateBorderLight.copy(alpha = 0.5f))

                        // Customer Selection Dropdown with Instant Search
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Client Account Association",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                if (selectedCustomer != null) {
                                    Text(
                                        text = "Tagged Account",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TealPrimary
                                    )
                                } else {
                                    Text(
                                        text = "${customers.size} Registered Contacts",
                                        fontSize = 10.sp,
                                        color = AppThemeManager.slateTextMedium
                                    )
                                }
                            }

                            @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
                            androidx.compose.material3.ExposedDropdownMenuBox(
                                expanded = expanded,
                                onExpandedChange = { expanded = it },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = if (selectedCustomer != null) {
                                        if (selectedCustomer!!.phoneNumber.isNotBlank()) {
                                            "${selectedCustomer!!.name} (${selectedCustomer!!.phoneNumber})"
                                        } else {
                                            selectedCustomer!!.name
                                        }
                                    } else {
                                        "Walk-in Customer / Cash Sale"
                                    },
                                    onValueChange = {},
                                    readOnly = true,
                                    leadingIcon = { 
                                        Icon(
                                            imageVector = if (selectedCustomer != null) Icons.Filled.AccountCircle else Icons.Filled.Person, 
                                            contentDescription = null, 
                                            tint = if (selectedCustomer != null) TealPrimary else AppThemeManager.slateTextMedium
                                        ) 
                                    },
                                    trailingIcon = { 
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (selectedCustomer != null) {
                                                IconButton(
                                                    onClick = { 
                                                        selectedCustomer = null 
                                                        customerSearchQuery = ""
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Close, 
                                                        contentDescription = "Clear Tagged Customer",
                                                        tint = AppThemeManager.slateTextMedium,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                            androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) 
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable, true),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = androidx.compose.material3.ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                                        focusedBorderColor = TealPrimary,
                                        unfocusedBorderColor = if (selectedCustomer != null) TealPrimary.copy(alpha = 0.5f) else AppThemeManager.slateBorderLight
                                    )
                                )

                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { 
                                        expanded = false 
                                        customerSearchQuery = ""
                                    },
                                    properties = androidx.compose.ui.window.PopupProperties(focusable = true),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 380.dp)
                                ) {
                                    // Interactive Search Bar at top of dropdown menu
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = customerSearchQuery,
                                            onValueChange = { customerSearchQuery = it },
                                            placeholder = { Text("Search 500+ contacts by name, phone...", fontSize = 12.sp) },
                                            leadingIcon = { 
                                                Icon(
                                                    imageVector = Icons.Filled.Search, 
                                                    contentDescription = "Search", 
                                                    tint = TealPrimary, 
                                                    modifier = Modifier.size(18.dp)
                                                ) 
                                            },
                                            trailingIcon = {
                                                if (customerSearchQuery.isNotEmpty()) {
                                                    IconButton(
                                                        onClick = { customerSearchQuery = "" },
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
                                            shape = RoundedCornerShape(8.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = TealPrimary,
                                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    HorizontalDivider(color = AppThemeManager.slateBorderLight.copy(alpha = 0.5f))

                                    // Walk-in Option
                                    DropdownMenuItem(
                                        text = { 
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.PersonOff, 
                                                    contentDescription = null, 
                                                    tint = AppThemeManager.slateTextMedium,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Column {
                                                    Text(
                                                        text = "Walk-in Customer / Cash Sale",
                                                        fontWeight = if (selectedCustomer == null) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (selectedCustomer == null) TealPrimary else MaterialTheme.colorScheme.onSurface,
                                                        fontSize = 13.sp
                                                    )
                                                    Text(
                                                        text = "No registered client account linked to invoice",
                                                        fontSize = 10.sp,
                                                        color = AppThemeManager.slateTextMedium
                                                    )
                                                }
                                            }
                                        },
                                        onClick = { 
                                            selectedCustomer = null
                                            expanded = false 
                                            customerSearchQuery = ""
                                        }
                                    )

                                    HorizontalDivider(color = AppThemeManager.slateBorderLight.copy(alpha = 0.5f))

                                    if (filteredCustomers.isEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 16.dp, horizontal = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(
                                                    imageVector = Icons.Filled.SearchOff,
                                                    contentDescription = null,
                                                    tint = AppThemeManager.slateTextMedium,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "No contact found matching \"$customerSearchQuery\"",
                                                    fontSize = 12.sp,
                                                    color = AppThemeManager.slateTextMedium,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    } else {
                                        if (customerSearchQuery.isNotBlank()) {
                                            Text(
                                                text = "MATCHING CONTACTS (${filteredCustomers.size})",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TealPrimary,
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                            )
                                        }

                                        filteredCustomers.take(20).forEach { cust ->
                                            val isSelected = selectedCustomer?.id == cust.id
                                            DropdownMenuItem(
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                text = {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                                            Text(
                                                                text = cust.name,
                                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                                color = if (isSelected) TealPrimary else MaterialTheme.colorScheme.onSurface,
                                                                fontSize = 13.sp,
                                                                maxLines = 1,
                                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                            )
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                            ) {
                                                                if (cust.phoneNumber.isNotBlank()) {
                                                                    Text(
                                                                        text = "📞 ${cust.phoneNumber}",
                                                                        fontSize = 11.sp,
                                                                        color = AppThemeManager.slateTextMedium
                                                                    )
                                                                }
                                                                if (cust.city.isNotBlank()) {
                                                                    Text(
                                                                        text = "• ${cust.city}",
                                                                        fontSize = 11.sp,
                                                                        color = AppThemeManager.slateTextMedium
                                                                    )
                                                                }
                                                            }
                                                        }
                                                        if (cust.loyaltyPoints > 0) {
                                                            Surface(
                                                                color = TealPrimary.copy(alpha = 0.1f),
                                                                shape = RoundedCornerShape(10.dp)
                                                            ) {
                                                                Text(
                                                                    text = "${cust.loyaltyPoints} pts",
                                                                    fontSize = 9.5.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = TealPrimary,
                                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.Person,
                                                        contentDescription = null,
                                                        tint = if (isSelected) TealPrimary else AppThemeManager.slateTextMedium,
                                                        modifier = Modifier.size(18.dp)
                                                     )
                                                 },
                                                 onClick = {
                                                     selectedCustomer = cust
                                                     expanded = false
                                                     customerSearchQuery = ""
                                                 }
                                            )
                                        }
                                        if (filteredCustomers.size > 20) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 6.dp, horizontal = 12.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "Showing top 20 of ${filteredCustomers.size} contacts. Type to filter...",
                                                    fontSize = 10.5.sp,
                                                    color = AppThemeManager.slateTextMedium,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = AppThemeManager.slateBorderLight.copy(alpha = 0.5f))

                        // Prescription Details Section
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showPrescriptionFields = !showPrescriptionFields },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Assignment,
                                        contentDescription = null,
                                        tint = TealPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "REGULATORY PRESCRIPTION COMPLIANCE",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Icon(
                                    imageVector = if (showPrescriptionFields) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = null,
                                    tint = AppThemeManager.slateTextMedium,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            val hasRegulatedItem = remember(cartItems) {
                                val regulatedKeywords = listOf("antibiotic", "augmentin", "cipro", "narcotic", "morphine", "prescription", "pom", "malacide", "artemether")
                                cartItems.any { item -> 
                                    regulatedKeywords.any { kw -> 
                                        item.inventoryItem.name.contains(kw, ignoreCase = true) || 
                                        item.inventoryItem.category.contains(kw, ignoreCase = true)
                                    }
                                }
                            }

                            if (hasRegulatedItem) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(AppThemeManager.pendingOrange.copy(alpha = 0.12f))
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = "⚠️ Prescription check recommended: Cart contains potentially regulated (POM) medications.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AppThemeManager.pendingOrange,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (showPrescriptionFields || hasRegulatedItem) {
                                OutlinedTextField(
                                    value = prescribingDoctor,
                                    onValueChange = { prescribingDoctor = it },
                                    label = { Text("Prescribing Physician Name", fontSize = 11.sp) },
                                    placeholder = { Text("e.g. Dr. Jude Cole") },
                                    leadingIcon = { Icon(Icons.Filled.MedicalServices, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(16.dp)) },
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("prescribing_doctor_input")
                                )
                                OutlinedTextField(
                                    value = prescriptionRef,
                                    onValueChange = { prescriptionRef = it },
                                    label = { Text("Prescription Reference / Rx ID", fontSize = 11.sp) },
                                    placeholder = { Text("e.g. RX-99201-B") },
                                    leadingIcon = { Icon(Icons.Filled.FactCheck, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(16.dp)) },
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("prescription_ref_input")
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Sleek Ledger / Paper-style billing transaction card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, AppThemeManager.slateBorderLight, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = TealSurface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.ReceiptLong,
                                contentDescription = null,
                                tint = TealPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "DISPENSING TRANSACTION BREAKDOWN",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Medications Subtotal", style = MaterialTheme.typography.bodySmall, color = AppThemeManager.slateTextMedium)
                            Text("₦${"%,.2f".format(subtotal)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Delivery Surcharge", style = MaterialTheme.typography.bodySmall, color = AppThemeManager.slateTextMedium)
                            Text("₦${"%,.2f".format(deliveryFee)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        }
                        
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = AppThemeManager.slateBorderLight.copy(alpha = 0.5f)
                        )
                        
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Total Due",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "₦${"%,.2f".format(total)}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = if (total > 0.0) OKGreen else TealPrimary
                            )
                        }

                        // Instant User Input Verification Warnings
                        if (hasExpiredItem) {
                            Spacer(modifier = Modifier.height(14.dp))
                            androidx.compose.material3.Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "CRITICAL COMPLIANCE BLOCK: One or more medications in your cart have EXPIRED and cannot be sold. Please remove them to continue.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (needsDelivery && deliveryAddress.isBlank()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            androidx.compose.material3.Surface(
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Please complete client destination address to dispatch",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Multi-Mode double checkout action grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isCheckoutEnabled = !hasExpiredItem && (!needsDelivery || deliveryAddress.isNotBlank()) && (cartWarnings.isEmpty() || overrideJustification.trim().length >= 5)

                    androidx.compose.material3.Button(
                        onClick = {
                            val (invoiceUri, invoiceFileName) = com.example.DocumentGenerator.generateDocument(
                                context = context,
                                isInvoice = true,
                                cartItems = cartItems,
                                deliveryFee = deliveryFee,
                                totalAmount = total,
                                customerName = selectedCustomer?.name ?: "Guest",
                                customerPhone = selectedCustomer?.phoneNumber ?: "+234 000 000 0000",
                                deliveryAddress = if (needsDelivery) deliveryAddress else "In-Store Pickup",
                                pharmacistName = viewModel.currentPharmacistName.value ?: "Pharm. Olawale A.",
                                pharmacyName = viewModel.currentPharmacistBranchName.value ?: "Careflux Central Pharmacy"
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
                            onCheckout(selectedCustomer, total, invoiceFileName ?: "", true, "Pending", overrideJustification, prescribingDoctor, prescriptionRef)
                        },
                        enabled = isCheckoutEnabled,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Send,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Send Invoice",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }

                    androidx.compose.material3.Button(
                        onClick = {
                            val (_, receiptFileName) = com.example.DocumentGenerator.generateDocument(
                                context = context,
                                isInvoice = false,
                                cartItems = cartItems,
                                deliveryFee = deliveryFee,
                                totalAmount = total,
                                customerName = selectedCustomer?.name ?: "Guest",
                                customerPhone = selectedCustomer?.phoneNumber ?: "+234 000 000 0000",
                                deliveryAddress = if (needsDelivery) deliveryAddress else "In-Store Pickup",
                                pharmacistName = viewModel.currentPharmacistName.value ?: "Pharm. Olawale A.",
                                pharmacyName = viewModel.currentPharmacistBranchName.value ?: "Careflux Central Pharmacy"
                            )
                            onCheckout(selectedCustomer, total, receiptFileName ?: "", false, "Paid", overrideJustification, prescribingDoctor, prescriptionRef)
                            Toast.makeText(context, "Sale completed", Toast.LENGTH_SHORT).show()
                        },
                        enabled = isCheckoutEnabled,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TealPrimary,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Finalize Sale",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
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

@Composable
fun CompactDashboardStatsBar(
    medsCount: Int,
    lowStockCount: Int,
    todayVolume: Int,
    pendingAlerts: Int,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    androidx.compose.material3.Card(
        shape = RoundedCornerShape(12.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = SlateBackgroundLight
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorderLight),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleExpand() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Stats summary grouping
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Indicator 1: Tracked
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(
                        imageVector = Icons.Filled.HealthAndSafety,
                        contentDescription = null,
                        tint = TealPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = medsCount.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = TealTertiary
                    )
                }
                
                // Divider dot
                Text("•", color = SlateTextMedium.copy(alpha = 0.5f), fontSize = 10.sp)

                // Indicator 2: Low Stock
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = if (lowStockCount > 0) WarningRed else SlateTextMedium,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = lowStockCount.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (lowStockCount > 0) WarningRed else TealTertiary
                    )
                }

                // Divider dot
                Text("•", color = SlateTextMedium.copy(alpha = 0.5f), fontSize = 10.sp)

                // Indicator 3: Today's Volume
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(
                        imageVector = Icons.Filled.ReceiptLong,
                        contentDescription = null,
                        tint = TealPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = todayVolume.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = TealTertiary
                    )
                }

                // Divider dot
                Text("•", color = SlateTextMedium.copy(alpha = 0.5f), fontSize = 10.sp)

                // Indicator 4: Pending Alerts
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(
                        imageVector = Icons.Filled.SmsFailed,
                        contentDescription = null,
                        tint = PendingOrange,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = pendingAlerts.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = TealTertiary
                    )
                }
            }

            // Expand/Collapse Label and Arrow Action
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (isExpanded) "Hide" else "Stats",
                    color = TealPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = TealPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// =========================================================================
// CENTRALIZED SYNC & OFFLINE COOP MONITORING ENGINE (CTO DESIGN SPEC)
// =========================================================================
@Composable
fun GlobalSyncStatusBanner(
    isOnline: Boolean,
    syncState: com.example.ui.PharmacyViewModel.SyncState,
    lastSyncedTime: Long,
    onManualSyncClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formatter = remember { 
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("Africa/Lagos")
        }
    }
    val formattedTime = remember(lastSyncedTime) { formatter.format(Date(lastSyncedTime)) }

    androidx.compose.material3.Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (!isOnline) {
                Color(0xFF2C241E) // Warm Amber-Dark
            } else when (syncState) {
                is com.example.ui.PharmacyViewModel.SyncState.Error -> Color(0xFF2E1C1D) // Soft Crimson-Dark
                com.example.ui.PharmacyViewModel.SyncState.Syncing -> Color(0xFF1B2329) // Deep Teal-Grey
                com.example.ui.PharmacyViewModel.SyncState.Synced -> Color(0xFF132219) // Forest-Dark
            }
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (!isOnline) {
                Color(0xFF8B5E3C).copy(alpha = 0.5f)
            } else when (syncState) {
                is com.example.ui.PharmacyViewModel.SyncState.Error -> Color(0xFFB3261E).copy(alpha = 0.5f)
                com.example.ui.PharmacyViewModel.SyncState.Syncing -> Color(0xFF2196F3).copy(alpha = 0.5f)
                com.example.ui.PharmacyViewModel.SyncState.Synced -> Color(0xFF4CAF50).copy(alpha = 0.5f)
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Animated Status Dot Indicators
                Box(
                    modifier = Modifier.size(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isOnline && syncState == com.example.ui.PharmacyViewModel.SyncState.Syncing) {
                        androidx.compose.material3.CircularProgressIndicator(
                            color = Color(0xFF2196F3),
                            strokeWidth = 2.dp,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        val dotColor = if (!isOnline) {
                            Color(0xFFFF9800) // Amber
                        } else when (syncState) {
                            is com.example.ui.PharmacyViewModel.SyncState.Error -> Color(0xFFF44336) // Red
                            else -> Color(0xFF4CAF50) // Green
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(dotColor, androidx.compose.foundation.shape.CircleShape)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    val statusHeader = if (!isOnline) {
                        "Cooperative Local Storage Mode (Offline)"
                    } else when (syncState) {
                        is com.example.ui.PharmacyViewModel.SyncState.Error -> "Database Write Blocked"
                        com.example.ui.PharmacyViewModel.SyncState.Syncing -> "CloudSync Worker active..."
                        com.example.ui.PharmacyViewModel.SyncState.Synced -> "Federated Cloud Database synced"
                    }
                    Text(
                        text = statusHeader,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    val statusSub = if (!isOnline) {
                        "Queueing mutations to SQLite Room. Auto-publishing when connection recovers."
                    } else when (syncState) {
                        is com.example.ui.PharmacyViewModel.SyncState.Error -> "Cloud rules/permissions denied: ${syncState.message}"
                        com.example.ui.PharmacyViewModel.SyncState.Syncing -> "Streaming encrypted transactions to Firestore node..."
                        com.example.ui.PharmacyViewModel.SyncState.Synced -> "Last synchronized securely at $formattedTime"
                    }
                    Text(
                        text = statusSub,
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        lineHeight = 14.sp
                    )
                }
            }

            if (isOnline) {
                IconButton(
                    onClick = onManualSyncClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Sync Now",
                        tint = TealPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// =========================================================================
// PRISTINE BRANCH INITIALIZATION & SETUP ENGINE (CTO DESIGN SPEC)
// =========================================================================
@Composable
fun WorkspaceInitializationCard(
    onInitializeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isInitializing by remember { mutableStateOf(false) }

    androidx.compose.material3.Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF15181F) // Sleek Premium Slate
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            androidx.compose.ui.graphics.Brush.horizontalGradient(
                colors = listOf(TealPrimary, Color(0xFF8A2BE2)) // Teal to Indigo gradient
            )
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.CloudDownload,
                    contentDescription = null,
                    tint = TealPrimary,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "Initialize Professional Workspace",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Welcome to Priscilla! Since your branch database is empty, initialize baseline reference datasets and standard medicine catalogs to begin branch operations.",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.8f),
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Beautiful scannable list of data to be initialized
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "10 Branded Medications (with prices, alerts, and lot batch levels)",
                    "5 Complete Customer Profiles (featuring chronic conditions & histories)",
                    "Active Clinical Intervention Trackers (with Day 3 / 7 / 14 follow-ups)",
                    "7 Days of Historical Medication Sales & Invoices (hydrates analytics charts)",
                    "5 Operational Tasks assigned to roles with full due urgency levels"
                ).forEach { item ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("✔", color = TealPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = item,
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            lineHeight = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    isInitializing = true
                    onInitializeClick()
                },
                enabled = !isInitializing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TealPrimary,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isInitializing) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = Color.Black,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Initializing Production Workspace...", fontWeight = FontWeight.Bold)
                } else {
                    Icon(
                        imageVector = Icons.Filled.ElectricBolt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.Black
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Initialize Production Workspace", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun PriorityFollowUpInbox(
    pendingAlerts: List<com.example.data.CustomerAlert>,
    onCompleteAlert: (com.example.data.CustomerAlert) -> Unit,
    onDeleteAlert: (com.example.data.CustomerAlert) -> Unit,
    onScanRadar: () -> Unit,
    onNavigateToCustomer: ((String) -> Unit)? = null
) {
    if (pendingAlerts.isEmpty()) return

    var isDismissed by remember { mutableStateOf(false) }
    if (isDismissed) return

    var showOverlayDialog by remember { mutableStateOf(false) }

    // Sleek, ultra-compact capsule that occupies very little vertical space
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = TealSurface.copy(alpha = 0.15f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = TealPrimary.copy(alpha = 0.25f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.clickable { showOverlayDialog = true }
            ) {
                // Micro alert beacon
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(Color.Red, CircleShape)
                )
                
                Icon(
                    imageVector = Icons.Filled.NotificationsActive,
                    contentDescription = null,
                    tint = TealPrimary,
                    modifier = Modifier.size(14.dp)
                )
                
                Text(
                    text = "Priority Care:",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = TealPrimary,
                    fontSize = 11.sp
                )
                
                Text(
                    text = "${pendingAlerts.size} pending",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp
                )
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable { showOverlayDialog = true }
                ) {
                    Text(
                        text = "Manage Inbox",
                        style = MaterialTheme.typography.labelSmall,
                        color = TealPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    Icon(
                        imageVector = Icons.Filled.OpenInNew,
                        contentDescription = "Manage Inbox",
                        tint = TealPrimary,
                        modifier = Modifier.size(11.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Smooth close button to dismiss the alerts card from the stock page workspace
                IconButton(
                    onClick = { isDismissed = true },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Dismiss Alerts Card",
                        tint = SlateTextMedium,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }

    // High-density floating cockpit Dialog overlay
    if (showOverlayDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showOverlayDialog = false }
        ) {
            androidx.compose.material3.Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Title section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.NotificationsActive,
                                contentDescription = null,
                                tint = TealPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Priority Care Inbox",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Box(
                                modifier = Modifier
                                    .background(TealPrimary, CircleShape)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = pendingAlerts.size.toString(),
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        IconButton(
                            onClick = { showOverlayDialog = false },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close",
                                tint = SlateTextMedium,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Take action on critical patient care follow-ups, calls, and handshake validations.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateTextMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        androidx.compose.material3.TextButton(
                            onClick = onScanRadar,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.defaultMinSize(minHeight = 24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = "Scan Radar",
                                tint = TealPrimary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Scan Radar",
                                fontSize = 11.sp,
                                color = TealPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    androidx.compose.material3.HorizontalDivider(color = SlateBorderLight.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(10.dp))

                    // Highly compact list
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(pendingAlerts) { alert ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorderLight.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = alert.customerName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.weight(1f, fill = false),
                                                maxLines = 2,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .background(TealSurface, RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = alert.alertType,
                                                    color = TealPrimary,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = alert.medicationName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Target Date: ${alert.scheduledTime}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SlateTextMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // View Patient button
                                        if (onNavigateToCustomer != null) {
                                            IconButton(
                                                onClick = { 
                                                    showOverlayDialog = false
                                                    onNavigateToCustomer(alert.customerName)
                                                },
                                                modifier = Modifier
                                                    .background(TealSurface, CircleShape)
                                                    .size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.PersonSearch,
                                                    contentDescription = "View Patient Ledger",
                                                    tint = TealPrimary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        // Complete action button
                                        IconButton(
                                            onClick = { 
                                                onCompleteAlert(alert)
                                                showOverlayDialog = false 
                                            },
                                            modifier = Modifier
                                                .background(TealPrimary.copy(alpha = 0.1f), CircleShape)
                                                .size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = "Mark Complete",
                                                tint = TealPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        // Dismiss action button
                                        IconButton(
                                            onClick = { onDeleteAlert(alert) },
                                            modifier = Modifier
                                                .background(Color.Red.copy(alpha = 0.05f), CircleShape)
                                                .size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Delete,
                                                contentDescription = "Dismiss",
                                                tint = Color.Red.copy(alpha = 0.8f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { showOverlayDialog = false }
                        ) {
                            Text("Close", color = TealPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RenderPostDispatchConfirmAlert(
    alert: com.example.data.CustomerAlert,
    customers: List<com.example.data.Customer>,
    viewModel: com.example.ui.PharmacyViewModel,
    context: android.content.Context,
    onDismiss: () -> Unit
) {
    var notesText by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    
    // Find current customer notes
    val customer = remember(alert, customers) {
        customers.find { it.name.equals(alert.customerName, ignoreCase = true) || it.phoneNumber == alert.phoneNumber }
    }
    
    LaunchedEffect(alert) {
        notesText = customer?.notes ?: ""
        validationError = null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.FactCheck, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(36.dp)) },
        title = { Text("Complete & Update Patient Note", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "You are completing the follow-up alert for ${alert.customerName}. To ensure adherence and compliance, you must update their notes section with something different and non-suggestive of an action, unless it is for a future date.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateTextMedium
                )
                
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { 
                        notesText = it 
                        val (isValid, err) = viewModel.validateCustomerNotes(it)
                        validationError = err
                    },
                    label = { Text("Patient Care Notes") },
                    isError = validationError != null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = RoundedCornerShape(8.dp)
                )
                
                if (validationError != null) {
                    Text(
                        text = validationError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .background(TealSurface.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(14.dp))
                        Text(
                            text = "To schedule a future follow-up, write: 'call patient on 8th of July 2026' or 'follow up in 7 days'. Static updates are saved directly.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TealTertiary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isSaving = true
                    viewModel.completeCustomerAlertAndLog(alert, notesText) { success, err ->
                        isSaving = false
                        if (success) {
                            onDismiss()
                            Toast.makeText(context, "Care follow-up successfully logged and alert marked off!", Toast.LENGTH_SHORT).show()
                        } else {
                            validationError = err
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                enabled = validationError == null && !isSaving,
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                } else {
                    Text("Complete & Log Care")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun RenderPostDispatchConfirmMedData(
    data: com.example.ui.PostDispatchConfirmData,
    viewModel: com.example.ui.PharmacyViewModel,
    context: android.content.Context,
    onDismiss: () -> Unit
) {
    val customer = data.customer
    val med = data.medication
    var selectedDateMs by remember { mutableStateOf(System.currentTimeMillis() + med.cycleDays * 24L * 60 * 60 * 1000) }
    var notesText by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Upcoming, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(36.dp)) },
        title = { Text("Reschedule Next Refill Cycle", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Refill reminder sent for ${med.medicationName}. Review and confirm or adjust the rescheduled next cycle date and notes below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateTextMedium
                )

                // Date Selection Box
                Card(
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorderLight),
                    colors = CardDefaults.cardColors(containerColor = SlateBackgroundLight.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Calculated Next Refill Date", style = MaterialTheme.typography.labelSmall, color = SlateTextMedium)
                        Text(
                            text = sdf.format(java.util.Date(selectedDateMs)),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = TealPrimary
                        )
                    }
                }

                // Presets
                Text("Quick Adjust Date Presets:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(15, 30, 45, 60).forEach { days ->
                        val isSelected = selectedDateMs == (System.currentTimeMillis() + days * 24L * 60 * 60 * 1000)
                        OutlinedButton(
                            onClick = { 
                                selectedDateMs = System.currentTimeMillis() + days * 24L * 60 * 60 * 1000 
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) TealPrimary else SlateBorderLight),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSelected) TealSurface else Color.Transparent
                            ),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("${days}d", fontSize = 11.sp, color = if (isSelected) TealPrimary else SlateTextMedium)
                        }
                    }
                }

                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Add Patient Care Notes (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isSaving = true
                    coroutineScope.launch {
                        // 1. Reschedule refill date
                        val updatedMed = med.copy(nextRefillDate = selectedDateMs)
                        viewModel.updateCustomerMedication(updatedMed)
                        
                        // 2. Append notes if provided
                        if (notesText.isNotBlank()) {
                            val updatedCustomer = com.example.ui.PatientIntelligenceParser.appendTextNote(
                                customer = customer,
                                medications = emptyList(),
                                noteText = notesText.trim()
                            )
                            viewModel.updateCustomer(updatedCustomer)
                        }
                        
                        // 3. Log compliance event
                        viewModel.repository.insertAdminAuditLog(
                            com.example.data.AdminAuditLog(
                                adminName = "Automated Completion Handler",
                                actionPerformed = "AUTO_RESCHEDULE_REFILL",
                                reason = "Rescheduled refill for ${customer.name} -> ${med.medicationName} to ${sdf.format(java.util.Date(selectedDateMs))}.",
                                affectedNodeId = med.id.toString(),
                                affectedNodeModel = "CustomerMedication"
                            )
                        )
                        
                        isSaving = false
                        onDismiss()
                        Toast.makeText(context, "Refill successfully rescheduled and logged!", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                enabled = !isSaving,
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                } else {
                    Text("Confirm & Reschedule")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    )
}

@Composable
fun CsvImportDiscrepancyDialog(
    sessionState: com.example.ui.CsvImportSessionState,
    onResolveAction: (com.example.ui.CsvDiscrepancyAction) -> Unit,
    onDismiss: () -> Unit
) {
    val discrepancies = sessionState.discrepancies
    val idx = sessionState.currentIndex
    if (idx !in discrepancies.indices) return

    val currentDiscrepancy = discrepancies[idx]
    val csv = currentDiscrepancy.csvItem
    val existing = currentDiscrepancy.existingItem

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.errorContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Stock Discrepancy Warning",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Item ${idx + 1} of ${discrepancies.size} matching items found",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "The product \"${csv.name}\" in your new CSV matches an item currently in your account stock. Choose how to handle this discrepancy:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Comparison Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Existing In Stock Card
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                "CURRENT IN STOCK",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(existing.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            if (existing.dosage.isNotBlank() && existing.dosage != "N/A") {
                                Text("Dosage: ${existing.dosage}", style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Quantity: ${existing.stockQuantity} units",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Price: ₦${String.format("%.2f", existing.price)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (existing.category.isNotBlank()) {
                                Text("Category: ${existing.category}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    // New CSV Item Card
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                "NEW CSV RECORD",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(csv.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            if (csv.dosage.isNotBlank() && csv.dosage != "N/A") {
                                Text("Dosage: ${csv.dosage}", style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Quantity: ${csv.stockQuantity} units",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Price: ₦${String.format("%.2f", if (csv.price > 0) csv.price else existing.price)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (csv.category.isNotBlank()) {
                                Text("Category: ${csv.category}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Select Resolution Action:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Option 1: Replace
                OutlinedButton(
                    onClick = { onResolveAction(com.example.ui.CsvDiscrepancyAction.REPLACE) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Replace Existing Item", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        Text("Overwrites stock with ${csv.stockQuantity} units & CSV details", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Option 2: Update / Add Quantity
                Button(
                    onClick = { onResolveAction(com.example.ui.CsvDiscrepancyAction.UPDATE_ADD_QTY) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Filled.AddCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Update / Add Quantity (+${csv.stockQuantity})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        Text("Combined total will be ${existing.stockQuantity + csv.stockQuantity} units", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Option 3: Skip Line
                OutlinedButton(
                    onClick = { onResolveAction(com.example.ui.CsvDiscrepancyAction.SKIP) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Skip This CSV Line", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        Text("Keep current stock (${existing.stockQuantity} units) unchanged", style = MaterialTheme.typography.labelSmall)
                    }
                }

                if (discrepancies.size > 1) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        "Apply to All Remaining (${discrepancies.size - idx} items):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onResolveAction(com.example.ui.CsvDiscrepancyAction.REPLACE_ALL) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("Replace All", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                        }

                        Button(
                            onClick = { onResolveAction(com.example.ui.CsvDiscrepancyAction.UPDATE_ADD_QTY_ALL) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("Add Qty to All", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                        }

                        OutlinedButton(
                            onClick = { onResolveAction(com.example.ui.CsvDiscrepancyAction.SKIP_ALL) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("Skip All", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel Import")
            }
        }
    )
}

