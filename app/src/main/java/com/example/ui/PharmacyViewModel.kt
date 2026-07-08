package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.BuildConfig
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.squareup.moshi.Types
import java.text.SimpleDateFormat
import java.util.*

data class CartItem(
    val inventoryItem: InventoryItem,
    var quantity: Int,
    var needsRefill: Boolean = true
)

data class WhatsAppTemplate(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val message: String
)

class PharmacyViewModel(application: Application) : AndroidViewModel(application) {

    val repository: PharmacyRepository
    private val prefs = application.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private var isFirstTaskSyncDone = false

    private fun showLocalNotification(title: String, content: String, targetTab: String = "branch_team") {
        try {
            val context = getApplication<Application>().applicationContext
            val channelId = "careflux_notifications_channel"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    "Careflux Notifications",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                )
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                manager.createNotificationChannel(channel)
            }

            val intent = android.content.Intent(context, com.example.MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("OPEN_TAB", targetTab)
            }
            val pendingIntent: android.app.PendingIntent = android.app.PendingIntent.getActivity(
                context, (0..9999).random(), intent, android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(content))
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            androidx.core.app.NotificationManagerCompat.from(context).notify((1000..9999).random(), notification)
        } catch (e: SecurityException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getApiKey(): String {
        return prefs.getString("custom_api_key", null)?.takeIf { it.isNotBlank() } 
            ?: com.example.BuildConfig.GEMINI_API_KEY
    }

    fun setApiKey(key: String) {
        prefs.edit().putString("custom_api_key", key.trim()).apply()
    }

    private val _isNdpaPledgeSigned = kotlinx.coroutines.flow.MutableStateFlow(prefs.getBoolean("ndpa_pledge_signed", false))
    val isNdpaPledgeSigned: StateFlow<Boolean> = _isNdpaPledgeSigned.asStateFlow()

    fun signNdpaPledge(adminName: String) {
        prefs.edit().putBoolean("ndpa_pledge_signed", true).apply()
        _isNdpaPledgeSigned.value = true
        logAdminAction(
            admin = adminName,
            action = "SIGN_NDPA_COMPLIANCE_PLEDGE",
            nodeId = deviceId,
            nodeModel = android.os.Build.MODEL,
            reason = "Pharmacist $adminName signed the official Data Processing Agreement (DPA) and Nigeria Data Protection Act (NDPA) compliance pledge."
        )
    }

    private val _customTemplates = kotlinx.coroutines.flow.MutableStateFlow<List<WhatsAppTemplate>>(emptyList())

    val customTemplates: StateFlow<List<WhatsAppTemplate>> = _customTemplates.asStateFlow()

    private val _aiInsightsResponse = kotlinx.coroutines.flow.MutableStateFlow<com.example.data.AIResponse?>(null)
    val aiInsightsResponse: kotlinx.coroutines.flow.StateFlow<com.example.data.AIResponse?> = _aiInsightsResponse.asStateFlow()

    fun setAiInsightsResponse(response: com.example.data.AIResponse?) {
        _aiInsightsResponse.value = response
    }


    // --- POS Cart State ---
    private val _cart = kotlinx.coroutines.flow.MutableStateFlow<List<CartItem>>(emptyList())
    val cart: StateFlow<List<CartItem>> = _cart.asStateFlow()

    private val _deliveryFeeString = kotlinx.coroutines.flow.MutableStateFlow("")
    val deliveryFeeString: StateFlow<String> = _deliveryFeeString.asStateFlow()
    
    fun setDeliveryFee(fee: String) {
        _deliveryFeeString.value = fee
    }

    fun addToCart(item: InventoryItem, quantity: Int) {
        if (quantity <= 0) return
        _cart.value = _cart.value.let { currentCart ->
            val existing = currentCart.find { it.inventoryItem.id == item.id }
            if (existing != null) {
                currentCart.map { if (it.inventoryItem.id == item.id) it.copy(quantity = it.quantity + quantity) else it }
            } else {
                currentCart + CartItem(item, quantity)
            }
        }
        viewModelScope.launch {
            val dbItem = repository.getInventoryItemById(item.id)
            if (dbItem != null) {
                val newQty = (dbItem.stockQuantity - quantity).coerceAtLeast(0)
                saveAndSyncInventoryItemDirectly(dbItem.copy(stockQuantity = newQty, lastUpdated = System.currentTimeMillis()))
            }
        }
    }

    fun updateCartItemQuantity(itemId: Int, quantity: Int) {
        val currentCart = _cart.value
        val existing = currentCart.find { it.inventoryItem.id == itemId } ?: return
        val diff = quantity - existing.quantity
        if (quantity <= 0) {
            removeFromCart(itemId)
            return
        }
        viewModelScope.launch {
            val dbItem = repository.getInventoryItemById(itemId)
            if (dbItem != null) {
                val newQty = (dbItem.stockQuantity - diff).coerceAtLeast(0)
                saveAndSyncInventoryItemDirectly(dbItem.copy(stockQuantity = newQty, lastUpdated = System.currentTimeMillis()))
            }
        }
        _cart.value = _cart.value.map { if (it.inventoryItem.id == itemId) it.copy(quantity = quantity) else it }
    }

    fun updateCartItemNeedsRefill(itemId: Int, needsRefill: Boolean) {
        _cart.value = _cart.value.map { if (it.inventoryItem.id == itemId) it.copy(needsRefill = needsRefill) else it }
    }

    fun removeFromCart(itemId: Int) {
        val currentCart = _cart.value
        val itemToRemove = currentCart.find { it.inventoryItem.id == itemId }
        if (itemToRemove != null) {
            val restoredQty = itemToRemove.quantity
            viewModelScope.launch {
                val dbItem = repository.getInventoryItemById(itemId)
                if (dbItem != null) {
                    val newQty = dbItem.stockQuantity + restoredQty
                    saveAndSyncInventoryItemDirectly(dbItem.copy(stockQuantity = newQty, lastUpdated = System.currentTimeMillis()))
                }
            }
        }
        _cart.value = currentCart.filter { it.inventoryItem.id != itemId }
    }

    fun clearCart() {
        _cart.value = emptyList()
        _deliveryFeeString.value = ""
    }

    fun clearCartAndRestoreStock() {
        val currentCart = _cart.value
        viewModelScope.launch {
            currentCart.forEach { cartItem ->
                val dbItem = repository.getInventoryItemById(cartItem.inventoryItem.id)
                if (dbItem != null) {
                    val newQty = dbItem.stockQuantity + cartItem.quantity
                    saveAndSyncInventoryItemDirectly(dbItem.copy(stockQuantity = newQty, lastUpdated = System.currentTimeMillis()))
                }
            }
        }
        _cart.value = emptyList()
        _deliveryFeeString.value = ""
    }

    // Streams of data from room database
    val activePostDispatchConfirm = kotlinx.coroutines.flow.MutableStateFlow<PostDispatchConfirmData?>(null)
    val activePostDispatchConfirmAlert = kotlinx.coroutines.flow.MutableStateFlow<com.example.data.CustomerAlert?>(null)

    val operationTasks: StateFlow<List<OperationTask>>
    val receipts: StateFlow<List<Receipt>>
    val inventoryItems: StateFlow<List<InventoryItem>>
    val lowStockItems: StateFlow<List<InventoryItem>>
    val prescriptionVolumes: StateFlow<List<DailyPrescriptionVolume>>
    val customerAlerts: StateFlow<List<CustomerAlert>>
    
    val customers: StateFlow<List<Customer>>
    val customerMedications: StateFlow<List<CustomerMedication>>
    val clinicalInterventions: StateFlow<List<ClinicalIntervention>>
    val triageConditions: StateFlow<List<TriageCondition>>
    val medicationSales: StateFlow<List<MedicationSale>>
    val rescueListings: StateFlow<List<RescueListing>>
    val adminAuditLogs: StateFlow<List<AdminAuditLog>>
    val inventoryBatches: StateFlow<List<com.example.data.InventoryBatch>>
    val smsLogs: StateFlow<List<com.example.data.OutboundSmsLog>>
    
    private val _lastFailedSmsLog = kotlinx.coroutines.flow.MutableStateFlow<com.example.data.OutboundSmsLog?>(null)
    val lastFailedSmsLog: StateFlow<com.example.data.OutboundSmsLog?> = _lastFailedSmsLog.asStateFlow()
    private val _branchTransfers = kotlinx.coroutines.flow.MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val branchTransfers: StateFlow<List<Map<String, Any>>> = _branchTransfers.asStateFlow()

    // --- Dynamic Feature Flags State ---
    val deviceId: String
    private val _isAiContentEnabled = kotlinx.coroutines.flow.MutableStateFlow(true)
    val isAiContentEnabled: StateFlow<Boolean> = _isAiContentEnabled.asStateFlow()

    private val _isCarefluxAiEnabled = kotlinx.coroutines.flow.MutableStateFlow(true)
    val isCarefluxAiEnabled: StateFlow<Boolean> = _isCarefluxAiEnabled.asStateFlow()

    private val _isSuspended = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isSuspended: StateFlow<Boolean> = _isSuspended.asStateFlow()

    private val _isInventoryLoading = kotlinx.coroutines.flow.MutableStateFlow(true)
    val isInventoryLoading: StateFlow<Boolean> = _isInventoryLoading.asStateFlow()

    // --- Branch Multi-User & Real-time Integration Engine ---
    private var userProfileListener: com.google.firebase.firestore.ListenerRegistration? = null
    private val activeSyncListeners = mutableListOf<com.google.firebase.firestore.ListenerRegistration>()

    private val _currentPharmacistBranchId = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val currentPharmacistBranchId: StateFlow<String?> = _currentPharmacistBranchId.asStateFlow()

    private val _currentPharmacistRole = kotlinx.coroutines.flow.MutableStateFlow<String?>("Pharmacist")
    val currentPharmacistRole: StateFlow<String?> = _currentPharmacistRole.asStateFlow()

    private val _currentPharmacistName = kotlinx.coroutines.flow.MutableStateFlow<String?>("Staff Pharmacist")
    val currentPharmacistName: StateFlow<String?> = _currentPharmacistName.asStateFlow()

    private val _currentPharmacistBranchName = kotlinx.coroutines.flow.MutableStateFlow<String?>("Careflux Branch")
    val currentPharmacistBranchName: StateFlow<String?> = _currentPharmacistBranchName.asStateFlow()

    private val _currentPharmacistPhone = kotlinx.coroutines.flow.MutableStateFlow<String?>("+2348000000000")
    val currentPharmacistPhone: StateFlow<String?> = _currentPharmacistPhone.asStateFlow()

    // Realtime staff list in the same branch
    private val _branchStaffList = kotlinx.coroutines.flow.MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val branchStaffList: StateFlow<List<Map<String, Any>>> = _branchStaffList.asStateFlow()

    // Sync and Connection Monitor State Flows
    private val _syncState = kotlinx.coroutines.flow.MutableStateFlow<SyncState>(SyncState.Synced)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _lastSyncedTime = kotlinx.coroutines.flow.MutableStateFlow<Long>(System.currentTimeMillis())
    val lastSyncedTime: StateFlow<Long> = _lastSyncedTime.asStateFlow()

    private val _isOnline = kotlinx.coroutines.flow.MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    fun generateUniqueId(): Int {
        return java.util.UUID.randomUUID().hashCode() and 0x7FFFFFFF
    }

    // --- Cooperative Location Preferences & Matching Engine ---
    fun getPharmacyName(): String {
        return prefs.getString("pharmacy_name", "Community Pharmacy") ?: "Community Pharmacy"
    }
    fun setPharmacyName(name: String) {
        prefs.edit().putString("pharmacy_name", name.trim()).apply()
        saveOrUpdateDeviceConfig()
    }

    fun getPharmacyState(): String {
        return prefs.getString("pharmacy_state", "Lagos") ?: "Lagos"
    }
    fun setPharmacyState(state: String) {
        prefs.edit().putString("pharmacy_state", state.trim()).apply()
        saveOrUpdateDeviceConfig()
    }

    fun getPharmacyLga(): String {
        return prefs.getString("pharmacy_lga", "Ikeja") ?: "Ikeja"
    }
    fun setPharmacyLga(lga: String) {
        prefs.edit().putString("pharmacy_lga", lga.trim()).apply()
        saveOrUpdateDeviceConfig()
    }

    private val _registeredNodes = kotlinx.coroutines.flow.MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val registeredNodes: StateFlow<List<Map<String, Any>>> = _registeredNodes.asStateFlow()

    private val _keyRequests = kotlinx.coroutines.flow.MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val keyRequests: StateFlow<List<Map<String, Any>>> = _keyRequests.asStateFlow()

    private val _allBranches = kotlinx.coroutines.flow.MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val allBranches: StateFlow<List<Map<String, Any>>> = _allBranches.asStateFlow()

    data class RedistributionMatch(
        val nodeId: String,
        val pharmacyName: String,
        val deviceModel: String,
        val lga: String,
        val state: String,
        val score: Int,
        val reasons: List<String>,
        val classMatch: Boolean,
        val locMatch: Boolean,
        val escalationTier: Int
    )

    fun calculateRedistributionOpportunities(productName: String, category: String): List<RedistributionMatch> {
        val currentLga = getPharmacyLga()
        val currentState = getPharmacyState()
        val allNodes = registeredNodes.value
        val allSales = medicationSales.value

        return allNodes.filter { (it["deviceId"] as? String ?: it["id"] as? String ?: "") != deviceId }.map { node ->
            val nodeDeviceId = node["deviceId"] as? String ?: node["id"] as? String ?: ""
            val nodeName = node["displayName"] as? String ?: node["pharmacyName"] as? String ?: "Cooperative Pharmacy"
            val nodeModel = node["deviceModel"] as? String ?: "Network Node"
            
            // Locate based on registered stats OR inferred from their historical sales
            val nodeLga = (node["lga"] as? String)?.takeIf { it.isNotBlank() }
                ?: allSales.filter { it.pharmacyNode == nodeModel }.groupBy { it.patientLga }
                    .maxByOrNull { it.value.size }?.key ?: "Ikeja"
                    
            val nodeState = (node["state"] as? String)?.takeIf { it.isNotBlank() }
                ?: allSales.filter { it.pharmacyNode == nodeModel }.groupBy { it.patientState }
                    .maxByOrNull { it.value.size }?.key ?: "Lagos"

            val reasons = mutableListOf<String>()
            var score = 0

            // 1. Geography Proximity (Max 40 pts)
            if (nodeLga.equals(currentLga, ignoreCase = true)) {
                score += 40
                reasons.add("Situated in the same LGA ($currentLga)")
            } else if (nodeState.equals(currentState, ignoreCase = true)) {
                score += 15
                reasons.add("Situated in the same Regional State ($currentState)")
            }

            // 2. High Demand Probability (Max 30 pts)
            val nodeSalesOfCategory = allSales.filter { it.pharmacyNode == nodeModel && it.category.equals(category, ignoreCase = true) }
            val nodeSalesOfProduct = allSales.filter { it.pharmacyNode == nodeModel && it.productName.equals(productName, ignoreCase = true) }

            val categorySalesQty = nodeSalesOfCategory.sumOf { it.quantitySold }
            val productSalesQty = nodeSalesOfProduct.sumOf { it.quantitySold }

            if (productSalesQty > 0) {
                score += 30
                reasons.add("Proven SKU demand: sold $productSalesQty units of '$productName' recently")
            } else if (categorySalesQty > 0) {
                score += 20
                reasons.add("Strong class affinity: sold $categorySalesQty units under '$category' category")
            }

            // 3. Step-wise Escalation Classification
            val tier = when {
                score >= 60 -> 1 // High demand + proximate (Targeted Priority-1)
                score >= 30 -> 2 // Moderate demand or proximate (Priority-2)
                else -> 3        // Open general pool (Priority-3)
            }

            RedistributionMatch(
                nodeId = nodeDeviceId,
                pharmacyName = nodeName,
                deviceModel = nodeModel,
                lga = nodeLga,
                state = nodeState,
                score = score,
                reasons = reasons,
                classMatch = categorySalesQty > 0 || productSalesQty > 0,
                locMatch = nodeLga.equals(currentLga, ignoreCase = true),
                escalationTier = tier
            )
        }.sortedByDescending { it.score }
    }

    init {
        val carefluxPrefs = application.getSharedPreferences("careflux_prefs", Context.MODE_PRIVATE)
        var id = carefluxPrefs.getString("device_uuid", null)
        if (id == null) {
            id = java.util.UUID.randomUUID().toString()
            carefluxPrefs.edit().putString("device_uuid", id).apply()
        }
        deviceId = id

        // Connection Monitoring Network Callback
        try {
            val connectivityManager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val networkRequest = android.net.NetworkRequest.Builder()
                .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            
            _isOnline.value = isNetworkAvailable(application)
            connectivityManager.registerNetworkCallback(networkRequest, object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    _isOnline.value = true
                    triggerImmediateSync()
                }
                override fun onLost(network: android.net.Network) {
                    _isOnline.value = false
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Start snapshot listener for dynamic feature flags
        try {
            val deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val docRef = firestore
                .collection("device_configs")
                .document(deviceId)

            val initialMap = hashMapOf(
                "deviceId" to deviceId,
                "deviceModel" to deviceModel,
                "aiContentEnabled" to true,
                "carefluxAiEnabled" to true,
                "lastActive" to System.currentTimeMillis()
            )
            docRef.get().addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    docRef.set(initialMap)
                } else {
                    docRef.update("lastActive", System.currentTimeMillis())
                }
            }

            docRef.addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                if (snapshot != null && snapshot.exists()) {
                    val aiContent = snapshot.getBoolean("aiContentEnabled") ?: true
                    val carefluxAi = snapshot.getBoolean("carefluxAiEnabled") ?: true
                    val suspended = snapshot.getBoolean("isSuspended") ?: false
                    _isAiContentEnabled.value = aiContent
                    _isCarefluxAiEnabled.value = carefluxAi
                    _isSuspended.value = suspended

                    // Deep sync approved personal credentials if generated
                    val customGemini = snapshot.getString("customGeminiApiKey")
                    val customTermii = snapshot.getString("customTermiiApiKey")
                    val customTermiiSender = snapshot.getString("customTermiiSenderId")
                    if (!customGemini.isNullOrBlank()) {
                        prefs.edit().putString("custom_api_key", customGemini.trim()).apply()
                    }
                    if (!customTermii.isNullOrBlank()) {
                        prefs.edit().putString("custom_termii_api_key", customTermii.trim()).apply()
                    }
                    if (!customTermiiSender.isNullOrBlank()) {
                        prefs.edit().putString("custom_termii_sender_id", customTermiiSender.trim()).apply()
                    }
                }
            }

            // Sync Registered Cooperative Nodes & Pharmacists
            firestore.collection("registered_pharmacists")
                .addSnapshotListener { snapshot, e ->
                    if (e == null && snapshot != null) {
                        val nodesList = snapshot.documents.mapNotNull { doc ->
                            val data = doc.data?.toMutableMap() ?: return@mapNotNull null
                            data["id"] = doc.id
                            data
                        }
                        _registeredNodes.value = nodesList
                    }
                }

            // Sync Key Creation Requests (real-time stream)
            firestore.collection("key_creation_requests")
                .addSnapshotListener { snapshot, e ->
                    if (e == null && snapshot != null) {
                        val reqList = snapshot.documents.mapNotNull { doc ->
                            val data = doc.data?.toMutableMap() ?: return@mapNotNull null
                            data["id"] = doc.id
                            data
                        }
                        _keyRequests.value = reqList
                    }
                }

            // Sync all registered branches / pharmacies (real-time stream)
            firestore.collection("branches")
                .addSnapshotListener { snapshot, e ->
                    if (e == null && snapshot != null) {
                        val branchList = snapshot.documents.mapNotNull { doc ->
                            val data = doc.data?.toMutableMap() ?: return@mapNotNull null
                            data["id"] = doc.id
                            data
                        }
                        _allBranches.value = branchList
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        loadTemplates()
        val database = PharmacyDatabase.getDatabase(application)
        repository = PharmacyRepository(database.pharmacyDao())

        viewModelScope.launch {
            repository.allInventoryItems.collect {
                _isInventoryLoading.value = false
            }
        }

        triageConditions = repository.allTriageConditions.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        receipts = repository.allReceipts.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        operationTasks = repository.allOperationTasks.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        inventoryItems = repository.allInventoryItems.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        lowStockItems = repository.lowStockItems.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        prescriptionVolumes = repository.allPrescriptionVolumes.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        customerAlerts = repository.allCustomerAlerts.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        customers = repository.allCustomers.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        customerMedications = repository.allCustomerMedications.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        clinicalInterventions = repository.allClinicalInterventions.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        medicationSales = repository.allMedicationSales.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        rescueListings = repository.allRescueListings.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        adminAuditLogs = repository.allAdminAuditLogs.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        inventoryBatches = repository.allInventoryBatches.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        smsLogs = repository.allSmsLogs.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Real-time synchronization from Firestore
        try {
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            
            // Sync Expiry Rescue Listings
            firestore.collection("expiry_rescue_listings")
                .addSnapshotListener { snapshot, e ->
                    if (e == null && snapshot != null) {
                        viewModelScope.launch {
                            try {
                                val activeFirestoreIds = snapshot.documents.map { it.id }.toSet()
                                
                                // Step 1: Prune local listings that are retracted/removed from Firestore
                                val localListings = rescueListings.value
                                for (local in localListings) {
                                    if (local.firestoreId.isNotEmpty() && !activeFirestoreIds.contains(local.firestoreId)) {
                                        // Avoid race conditions for newly created local entries by ensuring they are at least 30 seconds old
                                        if (System.currentTimeMillis() - local.listedAt > 30000L) {
                                            repository.deleteRescueListingByFirestoreId(local.firestoreId)
                                        }
                                    }
                                }

                                // Step 2: Insert or update newly fetched/updated documents
                                for (doc in snapshot.documents) {
                                    val itemMap = doc.data ?: continue
                                    val remoteId = doc.id
                                    val name = itemMap["productName"] as? String ?: ""
                                    val batch = itemMap["batchNumber"] as? String ?: ""
                                    val qty = (itemMap["quantity"] as? Number)?.toInt() ?: 0
                                    val expDate = (itemMap["expiryDate"] as? Number)?.toLong() ?: 0L
                                    val price = (itemMap["sellingPrice"] as? Number)?.toDouble() ?: 0.0
                                    val comm = (itemMap["commissionPercentage"] as? Number)?.toDouble() ?: 10.0
                                    val dur = (itemMap["rescueDurationDays"] as? Number)?.toInt() ?: 30
                                    val origNode = itemMap["ownerDeviceId"] as? String ?: ""
                                    val origNodeModel = itemMap["ownerDeviceModel"] as? String ?: ""
                                    val listedAtTime = (itemMap["listedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                                    val statusStr = itemMap["status"] as? String ?: "Available"
                                    val acceptedNode = itemMap["acceptedByDeviceId"] as? String ?: ""
                                    val acceptedNodeModel = itemMap["acceptedByDeviceModel"] as? String ?: ""
                                    val acceptedAtTime = (itemMap["acceptedAt"] as? Number)?.toLong() ?: 0L
                                    val soldAtTime = (itemMap["soldAt"] as? Number)?.toLong() ?: 0L
                                    val profitVal = (itemMap["profitShareAmount"] as? Number)?.toDouble() ?: 0.0

                                    val matched = repository.getRescueListingByFirestoreId(remoteId)
                                    val idToUse = matched?.id ?: 0
                                    val lga = itemMap["ownerLga"] as? String ?: "Ikeja"
                                    val state = itemMap["ownerState"] as? String ?: "Lagos"

                                    repository.insertRescueListing(
                                        RescueListing(
                                            id = idToUse,
                                            firestoreId = remoteId,
                                            productName = name,
                                            batchNumber = batch,
                                            quantity = qty,
                                            expiryDate = expDate,
                                            sellingPrice = price,
                                            commissionPercentage = comm,
                                            rescueDurationDays = dur,
                                            ownerDeviceId = origNode,
                                            ownerDeviceModel = origNodeModel,
                                            listedAt = listedAtTime,
                                            status = statusStr,
                                            acceptedByDeviceId = acceptedNode,
                                            acceptedByDeviceModel = acceptedNodeModel,
                                            acceptedAt = acceptedAtTime,
                                            soldAt = soldAtTime,
                                            profitShareAmount = profitVal,
                                            ownerLga = lga,
                                            ownerState = state
                                        )
                                    )
                                }
                            } catch (error: Exception) {
                                error.printStackTrace()
                            }
                        }
                    }
                }

            // Sync Admin Audit Logs
            firestore.collection("admin_audit_logs")
                .addSnapshotListener { snapshot, e ->
                    if (e == null && snapshot != null) {
                        viewModelScope.launch {
                            for (doc in snapshot.documents) {
                                val data = doc.data ?: continue
                                val act = data["actionPerformed"] as? String ?: ""
                                val admin = data["adminName"] as? String ?: ""
                                val timestampVal = data["timestamp"] as? Long ?: System.currentTimeMillis()
                                val affected = data["affectedNodeId"] as? String ?: ""
                                val affectedModel = data["affectedNodeModel"] as? String ?: ""
                                val res = data["reason"] as? String ?: ""

                                val matches = adminAuditLogs.value.any { it.actionPerformed == act && it.timestamp == timestampVal }
                                if (!matches) {
                                    repository.insertAdminAuditLog(
                                        AdminAuditLog(
                                            adminName = admin,
                                            actionPerformed = act,
                                            timestamp = timestampVal,
                                            affectedNodeId = affected,
                                            affectedNodeModel = affectedModel,
                                            reason = res
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

            // Sync Medication Sales
            firestore.collection("medication_sales")
                .addSnapshotListener { snapshot, e ->
                    if (e == null && snapshot != null) {
                        viewModelScope.launch {
                            for (doc in snapshot.documents) {
                                val data = doc.data ?: continue
                                val name = data["productName"] as? String ?: ""
                                val brand = data["brand"] as? String ?: ""
                                val gen = data["genericName"] as? String ?: ""
                                val cat = data["category"] as? String ?: ""
                                val qty = (data["quantitySold"] as? Long ?: 0L).toInt()
                                val dSold = data["dateSold"] as? Long ?: System.currentTimeMillis()
                                val node = data["pharmacyNode"] as? String ?: ""
                                val age = (data["patientAge"] as? Long ?: 30L).toInt()
                                val genGender = data["patientGender"] as? String ?: "Male"
                                val st = data["patientState"] as? String ?: "Lagos"
                                val lgaName = data["patientLga"] as? String ?: "Ikeja"
                                val city = data["patientCity"] as? String ?: "Ikeja"
                                val price = (data["salePrice"] as? Number)?.toDouble() ?: 0.0
                                val batchNum = data["batchNumber"] as? String ?: ""

                                val matches = medicationSales.value.any { it.productName == name && it.dateSold == dSold && it.quantitySold == qty }
                                if (!matches) {
                                    repository.insertMedicationSale(
                                        MedicationSale(
                                            productName = name,
                                            brand = brand,
                                            genericName = gen,
                                            category = cat,
                                            quantitySold = qty,
                                            dateSold = dSold,
                                            pharmacyNode = node,
                                            patientAge = age,
                                            patientGender = genGender,
                                            patientState = st,
                                            patientLga = lgaName,
                                            patientCity = city,
                                            salePrice = price,
                                            batchNumber = batchNum
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Seed baseline product registry if database is launched empty
        viewModelScope.launch {
            repository.allInventoryItems.collect { items ->
                if (items.isEmpty()) {
                    seedDatabase()
                }
            }
        }
        viewModelScope.launch {
            repository.allTriageConditions.collect { conds ->
                if (conds.isEmpty()) {
                    seedTriageDatabase()
                }
            }
        }
    }

    private suspend fun seedTriageDatabase() {
        val defaultConditions = listOf(
            TriageCondition(
                conditionName = "Urinary Tract Infection (UTI)",
                alternativeNames = "Cystitis, Water Infection",
                category = "Urology",
                briefDescription = "Gram-negative bacterial invasion of the lower urinary tract, extremely common in adult females.",
                keySymptoms = "Dysuria, increased frequency, urgency, suprapubic pain, cloudy/foul-smelling urine.",
                questionsJson = """[{"question":"How long have you felt this burning pain or urgency?","required":true,"isRedFlag":false},{"question":"Do you have lower back pain, flank pain, or fever/chills?","required":true,"isRedFlag":true},{"question":"Is there any visible blood in your urine?","required":true,"isRedFlag":true},{"question":"Are you currently pregnant or could you be?","required":true,"isRedFlag":true},{"question":"Have you experienced vaginal discharge or itching?","required":true,"isRedFlag":false}]""",
                referralCriteria = "Flank pain, fever, pregnancy, male patients, hematuria, pediatric patients, recurrent infections (>2 in 6 months).",
                severityAssessment = "Mild dysuria without systemic symptoms is low risk. Systemic symptoms (fever, chills), pregnancy, male gender, or child patient points to high risk requiring immediate GP or ER referral.",
                recommendedOtcs = "Sodium Citrate / Potassium Citrate sachets (symptomatic relief), Cranberry extract, fluid intake. Paracetamol/Ibuprofen for pain.",
                prescriptionOptions = "Nitrofurantoin 100mg Modified Release (twice daily for 3 days), Trimethoprim 200mg (twice daily for 3 days) as per local microbiology guidelines.",
                counsellingPoints = "Complete the entire antibiotic course if prescribed. Drink 2-3 liters of water. Avoid acidic/caffeinated drinks to prevent bladder irritation.",
                lifestyleAdvice = "Wipe front to back. Empty bladder post-coitus. Avoid scented products or bubble baths.",
                followUpTimeline = "48-72 hours. If symptoms worsen or fail to start resolving within 48 hours of starting treatment, consult GP.",
                whatsappTemplate = "Hello [Patient Name],\n\nBased on our discussion, I would like to understand your symptoms better.\n\nPlease reply with:\n1. How long have the symptoms been present?\n2. Do you have lower back/flank pain or fever?\n3. Is there blood in your urine?\n4. Are you pregnant?\n5. Do you have vaginal discharge or itching?\n\nYour answers will help us guide you appropriately.\n\nRegards,\nCareflux Pharmacist",
                isFavorite = true,
                usageCount = 0,
                lastEditedBy = "System",
                lastUpdated = System.currentTimeMillis()
            ),
            TriageCondition(
                conditionName = "Gastroesophageal Reflux Disease (GERD)",
                alternativeNames = "Heartburn, Acid Reflux, Indigestion",
                category = "Gastroenterology",
                briefDescription = "Retrograde flow of stomach acid into the esophagus, irritating the esophageal lining.",
                keySymptoms = "Heartburn (retrosternal burning), acid regurgitation, dysphagia, chest pain.",
                questionsJson = """[{"question":"Do you experience a burning sensation in your chest, especially after eating or when lying down?","required":true,"isRedFlag":false},{"question":"Do you have difficulty swallowing or pain when swallowing?","required":true,"isRedFlag":true},{"question":"Have you experienced unexplained weight loss or blood in vomit/stool?","required":true,"isRedFlag":true},{"question":"Do you experience persistent coughing, hoarseness, or wheezing?","required":true,"isRedFlag":false}]""",
                referralCriteria = "Dysphagia, odynophagia, unexplained weight loss, hematemesis, black tarry stools (melena), chest pain radiating to arm/jaw, symptoms lasting >4 weeks without relief.",
                severityAssessment = "Intermittent/mild symptoms are manageable OTC. Daily/severe symptoms, especially with red flags (difficulty swallowing, weight loss), represent high risk needing diagnostic evaluation.",
                recommendedOtcs = "Antacids (Gaviscon, Mylanta) for immediate relief; H2 Receptor Antagonists (Famotidine) or PPIs (Omeprazole, Esomeprazole) for acid suppression.",
                prescriptionOptions = "High dose PPIs (Lansoprazole 30mg), H2RAs, or prokinetics as prescribed by a GP.",
                counsellingPoints = "Take PPIs 30-60 minutes before the first meal of the day. Avoid taking antacids within 2 hours of other medications as they can reduce absorption.",
                lifestyleAdvice = "Eat smaller, more frequent meals. Avoid lying down for at least 3 hours after eating. Elevate the head of your bed by 6-8 inches. Limit trigger foods (fatty/spicy, chocolate, caffeine, citrus, alcohol).",
                followUpTimeline = "2 weeks. If OTC treatments do not resolve symptoms, or if symptoms recur when stopping medication, refer to a GP.",
                whatsappTemplate = "Hello [Patient Name],\n\nBased on our discussion, I would like to understand your symptoms better.\n\nPlease reply with:\n1. How long have you had these reflux/heartburn symptoms?\n2. Do you experience difficulty or pain when swallowing?\n3. Have you had any unexplained weight loss or blood in vomit/stool?\n4. Do you experience persistent coughing or hoarseness?\n\nYour answers will help us guide you appropriately.\n\nRegards,\nCareflux Pharmacist",
                isFavorite = false,
                usageCount = 0,
                lastEditedBy = "System",
                lastUpdated = System.currentTimeMillis()
            )
        )
        for (cond in defaultConditions) {
            repository.insertTriageCondition(cond)
        }
    }

    private suspend fun seedDatabase() {
        // Database is left empty by default to allow the user to start from a clean slate.
        // AI insights will generate naturally based on the data the user imports/creates.
    }

    // --- Actions ---

    fun insertAndSyncInventoryItem(item: InventoryItem) {
        viewModelScope.launch {
            saveAndSyncInventoryItemDirectly(item)
        }
    }

    private suspend fun saveAndSyncInventoryItemDirectly(item: InventoryItem): Int {
        val updated = item.copy(lastUpdated = System.currentTimeMillis())
        val generatedId = repository.insertInventoryItem(updated)
        val finalItem = if (updated.id == 0) updated.copy(id = generatedId.toInt()) else updated
        
        val branchId = _currentPharmacistBranchId.value
        if (!branchId.isNullOrBlank()) {
            val map = mapOf(
                "id" to finalItem.id,
                "name" to finalItem.name,
                "dosage" to finalItem.dosage,
                "stockQuantity" to finalItem.stockQuantity,
                "minRequiredStock" to finalItem.minRequiredStock,
                "category" to finalItem.category,
                "price" to finalItem.price,
                "expiryDate" to finalItem.expiryDate,
                "batchNumber" to finalItem.batchNumber,
                "supplier" to finalItem.supplier,
                "unitForm" to finalItem.unitForm,
                "lastSoldDate" to finalItem.lastSoldDate,
                "totalSoldQuantity" to finalItem.totalSoldQuantity,
                "brand" to finalItem.brand,
                "salesStrategy" to finalItem.salesStrategy,
                "lastUpdated" to finalItem.lastUpdated,
                "branchId" to branchId,
                "imageUri" to (finalItem.imageUri ?: "")
            )
            syncEntityToFirestore("branch_inventory", finalItem.id.toString(), map)
            
            // Clean up any old, legacy corrupted "id = 0" documents if this was a new item creation
            if (item.id == 0) {
                deleteEntityFromFirestore("branch_inventory", "0")
            }
        }
        return finalItem.id
    }

    fun addOrUpdateInventory(
        name: String,
        dosage: String,
        currentStock: Int,
        minStock: Int,
        category: String,
        price: Double = 0.0,
        id: Int = 0,
        updateStockStats: Boolean = false,
        addedQty: Int = 0,
        expiryDate: Long? = null,
        batchNumber: String = "",
        supplier: String = "",
        imageUri: String? = null,
        unitForm: String = "",
        brand: String = "",
        reason: String = "Manual Adjustment"
    ) {
        viewModelScope.launch {
            var actualId = id
            var previousExisting: com.example.data.InventoryItem? = null
            
            if (actualId == 0) {
                // Find all existing items with the same name
                val existingItems = repository.getInventoryItemsByName(name.trim())
                
                // Strictly match the batch number. If the user provides a blank batch, it only merges 
                // with an existing item that also has a blank batch.
                previousExisting = existingItems.find { it.batchNumber == batchNumber.trim() }

                if (previousExisting != null) {
                    actualId = previousExisting.id
                }
            } else {
                previousExisting = repository.getInventoryItemById(actualId)
            }

            val oldQty = previousExisting?.stockQuantity ?: 0
            val delta = currentStock - oldQty

            val itemExpiry = expiryDate ?: previousExisting?.expiryDate ?: 0L
            val itemBatch = batchNumber.ifBlank { previousExisting?.batchNumber ?: "" }
            val itemSupplier = supplier.ifBlank { previousExisting?.supplier ?: "" }
            val itemUnitForm = unitForm.ifBlank { previousExisting?.unitForm ?: "" }
            val itemBrand = brand.ifBlank { previousExisting?.brand ?: "" }
            val itemLastSold = if (updateStockStats) System.currentTimeMillis() else previousExisting?.lastSoldDate ?: 0L
            val itemSoldQty = (previousExisting?.totalSoldQuantity ?: 0) + (if (updateStockStats) addedQty else 0)
            val finalImageUri = imageUri ?: previousExisting?.imageUri

            val item = com.example.data.InventoryItem(
                id = actualId,
                name = name.trim(),
                dosage = dosage.trim(),
                stockQuantity = currentStock,
                minRequiredStock = minStock,
                category = category.trim(),
                price = price,
                expiryDate = itemExpiry,
                batchNumber = itemBatch,
                supplier = itemSupplier,
                unitForm = itemUnitForm,
                lastSoldDate = itemLastSold,
                totalSoldQuantity = itemSoldQty,
                imageUri = finalImageUri,
                brand = itemBrand
            )
            val finalSavedId = saveAndSyncInventoryItemDirectly(item)

            // Log stock adjustments for manual audits (exclude checkout reductions)
            if (delta != 0 && !updateStockStats) {
                val isManager = _currentPharmacistRole.value == "Branch Manager"
                val userName = _currentPharmacistName.value ?: "Staff Pharmacist"
                val userRole = _currentPharmacistRole.value ?: "Pharmacist"
                val userUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "LocalNode"
                val branchId = _currentPharmacistBranchId.value ?: "Self"
                
                val finalReason = if (previousExisting == null) "Initial Stock Intake" else reason
                
                val auditMap = hashMapOf(
                    "branchId" to branchId,
                    "uid" to userUid,
                    "displayName" to userName,
                    "role" to userRole,
                    "action" to "STOCK_ADJUSTMENT",
                    "timestamp" to System.currentTimeMillis(),
                    "details" to if (previousExisting == null) 
                        "Registered new medication '${item.name}' with initial stock of $currentStock units."
                    else
                        "Adjusted stock of ${item.name} (${item.dosage}) from $oldQty to $currentStock (change: ${if (delta > 0) "+$delta" else delta})",
                    "affectedId" to finalSavedId.toString(),
                    "medicationId" to finalSavedId,
                    "medicationName" to item.name,
                    "previousQty" to oldQty,
                    "newQty" to currentStock,
                    "adjustment" to delta,
                    "reason" to finalReason,
                    "verified" to isManager,
                    "verifiedBy" to if (isManager) "$userName ($userRole)" else "",
                    "verifiedAt" to if (isManager) System.currentTimeMillis() else 0L
                )
                
                try {
                    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    db.collection("branch_audit_logs").add(auditMap)
                    
                    repository.insertAdminAuditLog(
                        com.example.data.AdminAuditLog(
                            adminName = "$userName ($userRole)",
                            actionPerformed = "STOCK_ADJUSTMENT",
                            timestamp = System.currentTimeMillis(),
                            affectedNodeId = finalSavedId.toString(),
                            affectedNodeModel = "Branch: ${_currentPharmacistBranchName.value ?: "Careflux"}",
                            reason = "Stock adjusted from $oldQty to $currentStock (change: ${if (delta > 0) "+$delta" else delta}). Reason: $finalReason"
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun deleteInventory(item: InventoryItem, reason: String = "Discontinued / Removed") {
        viewModelScope.launch {
            repository.deleteInventoryItem(item)
            deleteEntityFromFirestore("branch_inventory", item.id.toString())

            val isManager = _currentPharmacistRole.value == "Branch Manager"
            val userName = _currentPharmacistName.value ?: "Staff Pharmacist"
            val userRole = _currentPharmacistRole.value ?: "Pharmacist"
            val userUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "LocalNode"
            val branchId = _currentPharmacistBranchId.value ?: "Self"
            
            val auditMap = hashMapOf(
                "branchId" to branchId,
                "uid" to userUid,
                "displayName" to userName,
                "role" to userRole,
                "action" to "STOCK_ADJUSTMENT",
                "timestamp" to System.currentTimeMillis(),
                "details" to "Permanently DELETED medication '${item.name}' (${item.dosage}) from stock. (Previous stock was ${item.stockQuantity} units)",
                "affectedId" to item.id.toString(),
                "medicationId" to item.id,
                "medicationName" to item.name,
                "previousQty" to item.stockQuantity,
                "newQty" to 0,
                "adjustment" to -item.stockQuantity,
                "reason" to "DELETION: $reason",
                "verified" to isManager,
                "verifiedBy" to if (isManager) "$userName ($userRole)" else "",
                "verifiedAt" to if (isManager) System.currentTimeMillis() else 0L
            )
            
            try {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                db.collection("branch_audit_logs").add(auditMap)
                
                repository.insertAdminAuditLog(
                    com.example.data.AdminAuditLog(
                        adminName = "$userName ($userRole)",
                        actionPerformed = "STOCK_DELETION",
                        timestamp = System.currentTimeMillis(),
                        affectedNodeId = item.id.toString(),
                        affectedNodeModel = "Branch: ${_currentPharmacistBranchName.value ?: "Careflux"}",
                        reason = "Permanently deleted medication '${item.name}' with ${item.stockQuantity} remaining stock. Justification: $reason"
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun insertInventoryBatch(batch: com.example.data.InventoryBatch) {
        viewModelScope.launch {
            repository.insertInventoryBatch(batch)
            recalculateItemStockFromBatches(batch.inventoryItemId)
        }
    }

    fun updateInventoryBatch(batch: com.example.data.InventoryBatch) {
        viewModelScope.launch {
            repository.updateInventoryBatch(batch)
            recalculateItemStockFromBatches(batch.inventoryItemId)
        }
    }

    fun deleteInventoryBatch(batch: com.example.data.InventoryBatch) {
        viewModelScope.launch {
            repository.deleteInventoryBatch(batch)
            recalculateItemStockFromBatches(batch.inventoryItemId)
        }
    }

    fun clearFailedSmsLog() {
        _lastFailedSmsLog.value = null
    }

    fun recalculateItemStockFromBatches(itemId: Int) {
        viewModelScope.launch {
            try {
                val batches = repository.getBatchesForItem(itemId).first()
                val totalStock = batches.sumOf { it.stockQuantity }
                val item = repository.getInventoryItemById(itemId)
                if (item != null) {
                    val now = System.currentTimeMillis()
                    val validBatches = batches.filter { it.expiryDate > now }
                    val closestExpiry = validBatches.minOfOrNull { it.expiryDate } 
                        ?: batches.minOfOrNull { it.expiryDate } 
                        ?: item.expiryDate

                    val updatedItem = item.copy(
                        stockQuantity = totalStock,
                        expiryDate = closestExpiry,
                        lastUpdated = System.currentTimeMillis()
                    )
                    repository.insertInventoryItem(updatedItem)
                    
                    val map = mapOf(
                        "id" to updatedItem.id,
                        "name" to updatedItem.name,
                        "dosage" to updatedItem.dosage,
                        "stockQuantity" to updatedItem.stockQuantity,
                        "minRequiredStock" to updatedItem.minRequiredStock,
                        "category" to updatedItem.category,
                        "price" to updatedItem.price,
                        "expiryDate" to updatedItem.expiryDate,
                        "lastUpdated" to updatedItem.lastUpdated
                    )
                    syncEntityToFirestore("branch_inventory", updatedItem.id.toString(), map)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deduplicateLocalInventory() {
        viewModelScope.launch {
            try {
                val allItems = repository.allInventoryItems.first()
                val groups = allItems.groupBy { 
                    "${it.name.trim().lowercase()}_${it.dosage.trim().lowercase()}_${it.batchNumber.trim().lowercase()}" 
                }
                for ((_, group) in groups) {
                    if (group.size > 1) {
                        val sorted = group.sortedByDescending { it.lastUpdated }
                        val keep = sorted.first()
                        val toDelete = sorted.drop(1)
                        for (item in toDelete) {
                            repository.deleteInventoryItem(item)
                            deleteEntityFromFirestore("branch_inventory", item.id.toString())
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateStockLevel(item: InventoryItem, newQuantity: Int, reason: String = "Quick stock increment (+10)") {
        viewModelScope.launch {
            val oldQty = item.stockQuantity
            val delta = newQuantity - oldQty
            if (delta != 0) {
                saveAndSyncInventoryItemDirectly(item.copy(stockQuantity = newQuantity, lastUpdated = System.currentTimeMillis()))
                
                val isManager = _currentPharmacistRole.value == "Branch Manager"
                val userName = _currentPharmacistName.value ?: "Staff Pharmacist"
                val userRole = _currentPharmacistRole.value ?: "Pharmacist"
                val userUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "LocalNode"
                val branchId = _currentPharmacistBranchId.value ?: "Self"
                
                val auditMap = hashMapOf(
                    "branchId" to branchId,
                    "uid" to userUid,
                    "displayName" to userName,
                    "role" to userRole,
                    "action" to "STOCK_ADJUSTMENT",
                    "timestamp" to System.currentTimeMillis(),
                    "details" to "Adjusted stock of ${item.name} (${item.dosage}) from $oldQty to $newQuantity (change: ${if (delta > 0) "+$delta" else delta})",
                    "affectedId" to item.id.toString(),
                    "medicationId" to item.id,
                    "medicationName" to item.name,
                    "previousQty" to oldQty,
                    "newQty" to newQuantity,
                    "adjustment" to delta,
                    "reason" to reason,
                    "verified" to isManager,
                    "verifiedBy" to if (isManager) "$userName ($userRole)" else "",
                    "verifiedAt" to if (isManager) System.currentTimeMillis() else 0L
                )
                
                try {
                    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    db.collection("branch_audit_logs").add(auditMap)
                    
                    repository.insertAdminAuditLog(
                        com.example.data.AdminAuditLog(
                            adminName = "$userName ($userRole)",
                            actionPerformed = "STOCK_ADJUSTMENT",
                            timestamp = System.currentTimeMillis(),
                            affectedNodeId = item.id.toString(),
                            affectedNodeModel = "Branch: ${_currentPharmacistBranchName.value ?: "Careflux"}",
                            reason = "Stock adjusted from $oldQty to $newQuantity (change: ${if (delta > 0) "+$delta" else delta}). Reason: $reason"
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun verifyStockAdjustment(logId: String) {
        val userName = _currentPharmacistName.value ?: "Staff Pharmacist"
        val userRole = _currentPharmacistRole.value ?: "Pharmacist"
        viewModelScope.launch {
            try {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val docRef = db.collection("branch_audit_logs").document(logId)
                docRef.update(
                    mapOf(
                        "verified" to true,
                        "verifiedBy" to "$userName ($userRole)",
                        "verifiedAt" to System.currentTimeMillis()
                    )
                )
                logAuditTrail("VERIFY_STOCK_ADJUSTMENT", "Approved/Verified stock adjustment with log ID: $logId")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateInventorySalesStrategy(item: InventoryItem, strategy: String) {
        viewModelScope.launch {
            saveAndSyncInventoryItemDirectly(item.copy(salesStrategy = strategy, lastUpdated = System.currentTimeMillis()))
        }
    }

    fun logPrescriptionVolume(date: String, volume: Int, notes: String, imageUri: String? = null) {
        viewModelScope.launch {
            repository.insertPrescriptionVolume(DailyPrescriptionVolume(date, volume, notes, imageUri))
        }
    }

    fun deletePrescriptionVolume(log: DailyPrescriptionVolume) {
        viewModelScope.launch {
            repository.deletePrescriptionVolume(log)
        }
    }

    fun addCustomerAlert(name: String, phone: String, medication: String, type: String, scheduledTime: String) {
        viewModelScope.launch {
            val alert = CustomerAlert(
                customerName = name.trim(),
                phoneNumber = phone.trim(),
                medicationName = medication.trim(),
                alertType = type,
                status = "Pending",
                scheduledTime = scheduledTime
            )
            repository.insertCustomerAlert(alert)
        }
    }

    fun markAlertAsSent(alert: CustomerAlert) {
        viewModelScope.launch {
            repository.updateCustomerAlert(alert.copy(status = "Sent"))
        }
    }

    fun deleteCustomerAlert(alert: CustomerAlert) {
        viewModelScope.launch {
            repository.deleteCustomerAlert(alert)
        }
    }

    // --- Smart Note-Alert Parsing & Validation Engine ---
    fun validateCustomerNotes(notes: String): Pair<Boolean, String?> {
        val trimmed = notes.trim()
        if (trimmed.isBlank()) {
            return Pair(true, null)
        }

        // Check for action words suggesting uncompleted or pending work
        val keywords = listOf("call", "contact", "follow up", "remind", "alert", "refill", "message", "notify", "reach out", "check on")
        val hasActionKeyword = keywords.any { trimmed.contains(it, ignoreCase = true) }

        if (!hasActionKeyword) {
            return Pair(true, null) // Safe note
        }

        // If there is an action keyword, there MUST be a future date
        val parsedTime = parseFutureDateFromText(trimmed)
        if (parsedTime == null) {
            return Pair(
                false,
                "Your note suggests an active follow-up action (contains keywords like 'call' or 'follow up') but does not specify a valid future date or duration (e.g. '8th of july 2026' or 'in 5 days'). Please specify a valid future date or remove the action keywords."
            )
        }

        if (parsedTime <= System.currentTimeMillis()) {
            return Pair(
                false,
                "The follow-up date specified in your note is in the past or today. Active follow-ups must be set for a future date."
            )
        }

        return Pair(true, null)
    }

    fun parseFutureDateFromText(text: String): Long? {
        val trimmed = text.trim()
        
        // 1. Check for "in X days" pattern
        val daysRegex = Regex("""(?i)(?:call|contact|follow up|remind|alert|refill|message|notify|check on)\s+(?:this\s+)?(?:customer|patient|her|him|them|me)?\s*(?:again)?\s*in\s+(\d+)\s+days?""")
        val daysMatch = daysRegex.find(trimmed)
        if (daysMatch != null) {
            val days = daysMatch.groupValues[1].toInt()
            val calendar = java.util.Calendar.getInstance()
            calendar.add(java.util.Calendar.DAY_OF_YEAR, days)
            // Set to 9 AM
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 9)
            calendar.set(java.util.Calendar.MINUTE, 0)
            calendar.set(java.util.Calendar.SECOND, 0)
            return calendar.timeInMillis
        }

        // 2. Check for date patterns (e.g. "8th of july 2026", "july 8, 2026")
        val monthNames = "january|february|march|april|may|june|july|august|september|october|november|december|jan|feb|mar|apr|jun|jul|aug|sep|oct|nov|dec"
        val dateRegex1 = Regex("""(?i)(?:on\s+)?(\d{1,2})(?:st|nd|rd|th)?\s+(?:of\s+)?($monthNames)\s+(\d{4})""")
        val dateRegex2 = Regex("""(?i)(?:on\s+)?($monthNames)\s+(\d{1,2})(?:st|nd|rd|th)?(?:,\s*|\s+)(\d{4})""")
        val dateRegex3 = Regex("""(\d{4})[-/](\d{1,2})[-/](\d{1,2})""")
        val dateRegex4 = Regex("""(\d{1,2})[-/](\d{1,2})[-/](\d{4})""")

        val match1 = dateRegex1.find(trimmed)
        val match2 = dateRegex2.find(trimmed)
        val match3 = dateRegex3.find(trimmed)
        val match4 = dateRegex4.find(trimmed)

        var parsedDate: java.util.Date? = null
        if (match1 != null) {
            val day = match1.groupValues[1].toInt()
            val monthStr = match1.groupValues[2].lowercase()
            val year = match1.groupValues[3].toInt()
            parsedDate = parseDateParts(day, monthStr, year)
        } else if (match2 != null) {
            val monthStr = match2.groupValues[1].lowercase()
            val day = match2.groupValues[2].toInt()
            val year = match2.groupValues[3].toInt()
            parsedDate = parseDateParts(day, monthStr, year)
        } else if (match3 != null) {
            val year = match3.groupValues[1].toInt()
            val month = match3.groupValues[2].toInt() - 1
            val day = match3.groupValues[3].toInt()
            val cal = java.util.Calendar.getInstance()
            cal.set(year, month, day, 9, 0, 0)
            parsedDate = cal.time
        } else if (match4 != null) {
            val p1 = match4.groupValues[1].toInt()
            val p2 = match4.groupValues[2].toInt()
            val year = match4.groupValues[3].toInt()
            val month = if (p2 <= 12) p2 - 1 else p1 - 1
            val day = if (p2 <= 12) p1 else p2
            val cal = java.util.Calendar.getInstance()
            cal.set(year, month, day, 9, 0, 0)
            parsedDate = cal.time
        }

        return parsedDate?.time
    }

    private fun parseDateParts(day: Int, monthStr: String, year: Int): java.util.Date? {
        val monthMap = mapOf(
            "jan" to 0, "january" to 0,
            "feb" to 1, "february" to 1,
            "mar" to 2, "march" to 2,
            "apr" to 3, "april" to 3,
            "may" to 4,
            "jun" to 5, "june" to 5,
            "jul" to 6, "july" to 6,
            "aug" to 7, "august" to 7,
            "sep" to 8, "september" to 8,
            "oct" to 9, "october" to 9,
            "nov" to 10, "november" to 10,
            "dec" to 11, "december" to 11
        )
        val month = monthMap[monthStr.take(3)] ?: return null
        val cal = java.util.Calendar.getInstance()
        cal.set(year, month, day, 9, 0, 0)
        return cal.time
    }

    fun parseCustomerNotesForAlerts(customer: Customer) {
        val notes = customer.notes.trim()
        if (notes.isBlank()) return

        val parsedTime = parseFutureDateFromText(notes)
        if (parsedTime != null && parsedTime > System.currentTimeMillis()) {
            viewModelScope.launch {
                // Ensure no duplicate active alert for the same customer and same note prefix
                val notePrefix = "Note Alert: ${notes.take(30)}"
                val existing = customerAlerts.value.any {
                    it.customerName.equals(customer.name, ignoreCase = true) &&
                    it.alertType == "Call Follow-up" &&
                    it.status == "Pending" &&
                    it.medicationName.startsWith(notePrefix)
                }
                if (!existing) {
                    val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                    val scheduledTimeStr = sdf.format(java.util.Date(parsedTime))
                    val alert = CustomerAlert(
                        customerName = customer.name,
                        phoneNumber = customer.phoneNumber,
                        medicationName = "Note Alert: ${notes.take(50)}...",
                        alertType = "Call Follow-up",
                        status = "Pending",
                        scheduledTime = scheduledTimeStr,
                        timestamp = parsedTime
                    )
                    repository.insertCustomerAlert(alert)
                    
                    // Log audit action
                    repository.insertAdminAuditLog(
                        AdminAuditLog(
                            adminName = "Automated Completion Handler",
                            actionPerformed = "AUTO_CREATE_NOTE_ALERT",
                            reason = "Automatically scheduled follow-up alert for ${customer.name} on $scheduledTimeStr based on customer notes analysis.",
                            affectedNodeId = customer.id.toString(),
                            affectedNodeModel = "Customer"
                        )
                    )
                }
            }
        }
    }

    // --- Automated Completion Handler ---
    fun completeCustomerAlertAndLog(alert: CustomerAlert, userNotes: String?, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val customer = customers.value.find { 
                it.name.equals(alert.customerName, ignoreCase = true) || 
                it.phoneNumber == alert.phoneNumber 
            }

            if (userNotes != null && customer != null) {
                val (isValid, errorMsg) = validateCustomerNotes(userNotes)
                if (!isValid) {
                    onComplete(false, errorMsg)
                    return@launch
                }
                // Update customer notes
                val updatedCustomer = customer.copy(notes = userNotes.trim())
                repository.updateCustomer(updatedCustomer)
                syncCustomerToBranch(updatedCustomer)
                
                // Re-scan newly saved notes in case user specified a NEW future follow-up!
                parseCustomerNotesForAlerts(updatedCustomer)
            }

            // Update alert status to completed / sent
            repository.updateCustomerAlert(alert.copy(status = "Sent"))

            // Log administrative compliance event
            repository.insertAdminAuditLog(
                AdminAuditLog(
                    adminName = "Automated Completion Handler",
                    actionPerformed = "RESOLVE_ALERT_NOTIF",
                    reason = "Successfully marked follow-up alert of ${alert.customerName} as completed. Outcome Note: ${userNotes ?: "N/A"}",
                    affectedNodeId = alert.id.toString(),
                    affectedNodeModel = "CustomerAlert"
                )
            )

            // Log corresponding clinical intervention
            if (customer != null) {
                repository.insertClinicalIntervention(
                    ClinicalIntervention(
                        customerId = customer.id,
                        presentation = "Resolved pending alert: '${alert.medicationName}'",
                        testResults = "Follow-up complete via phone/care note update",
                        recommendation = "Marked care follow-up alert as resolved. Patient care notes updated.",
                        currentStatus = "Feeling Better",
                        dateAdded = System.currentTimeMillis()
                    )
                )
            }

            triggerImmediateSync()
            onComplete(true, null)
        }
    }

    // --- Operation Tasks Actions ---
    fun addOperationTask(
        title: String, 
        description: String, 
        urgency: String, 
        category: String,
        assignedToName: String? = null,
        assignedToUid: String? = null
    ) {
        viewModelScope.launch {
            val localId = (100000..999999).random()
            val task = OperationTask(
                id = localId, 
                title = title, 
                description = description, 
                urgency = urgency, 
                category = category, 
                isCompleted = false,
                assignedToName = assignedToName,
                assignedToUid = assignedToUid
            )
            repository.insertOperationTask(task)
            
            val branchId = _currentPharmacistBranchId.value
            if (!branchId.isNullOrBlank()) {
                val map = mapOf(
                    "id" to localId,
                    "title" to title,
                    "description" to description,
                    "urgency" to urgency,
                    "category" to category,
                    "isCompleted" to false,
                    "createdAt" to task.createdAt,
                    "branchId" to branchId,
                    "assignedToName" to (assignedToName ?: ""),
                    "assignedToUid" to (assignedToUid ?: "")
                )
                syncEntityToFirestore("branch_operation_tasks", localId.toString(), map)
                logAuditTrail(
                    action = "DELEGATE_TASK",
                    details = "Delegated task '$title' to ${assignedToName ?: "Staff"} inside category '$category' ($urgency urgency level).",
                    affectedId = localId.toString()
                )
            }
        }
    }

    fun verifiablyCompleteOperationTask(
        task: OperationTask,
        notes: String,
        channel: String,
        patientName: String,
        onFinished: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val updaterName = _currentPharmacistName.value ?: "Staff Pharmacist"
            val updated = task.copy(
                isCompleted = true,
                verifiedBy = updaterName,
                verificationNotes = notes.trim(),
                verificationChannel = channel,
                verificationCustomerName = patientName.trim(),
                verifiedAt = System.currentTimeMillis()
            )
            repository.updateOperationTask(updated)
            
            val branchId = _currentPharmacistBranchId.value
            if (!branchId.isNullOrBlank()) {
                val map: Map<String, Any> = mapOf(
                    "id" to updated.id,
                    "title" to updated.title,
                    "description" to updated.description,
                    "urgency" to updated.urgency,
                    "category" to updated.category,
                    "isCompleted" to updated.isCompleted,
                    "createdAt" to updated.createdAt,
                    "branchId" to branchId,
                    "assignedToName" to (updated.assignedToName ?: ""),
                    "assignedToUid" to (updated.assignedToUid ?: ""),
                    "verifiedBy" to (updated.verifiedBy ?: ""),
                    "verificationNotes" to (updated.verificationNotes ?: ""),
                    "verificationChannel" to (updated.verificationChannel ?: ""),
                    "verificationCustomerName" to (updated.verificationCustomerName ?: ""),
                    "verifiedAt" to (updated.verifiedAt ?: 0L),
                    "isApproved" to updated.isApproved,
                    "approvedBy" to (updated.approvedBy ?: ""),
                    "approvedAt" to (updated.approvedAt ?: 0L),
                    "approvalNotes" to (updated.approvalNotes ?: "")
                )
                syncEntityToFirestore("branch_operation_tasks", updated.id.toString(), map)
                logAuditTrail(
                    action = "TASK_VERIFIED_COMPLETE",
                    details = "Task '${updated.title}' verified by $updaterName. Channel: $channel, Note: ${notes.take(40)}...",
                    affectedId = updated.id.toString()
                )
            }
            onFinished(true, "Task compliance check passed.")
        }
    }

    fun approveOperationTask(
        task: OperationTask,
        notes: String,
        onFinished: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val managerName = _currentPharmacistName.value ?: "Branch Manager"
            val updated = task.copy(
                isApproved = true,
                approvedBy = managerName,
                approvedAt = System.currentTimeMillis(),
                approvalNotes = notes.trim()
            )
            repository.updateOperationTask(updated)
            
            val branchId = _currentPharmacistBranchId.value
            if (!branchId.isNullOrBlank()) {
                val map: Map<String, Any> = mapOf(
                    "id" to updated.id,
                    "title" to updated.title,
                    "description" to updated.description,
                    "urgency" to updated.urgency,
                    "category" to updated.category,
                    "isCompleted" to updated.isCompleted,
                    "createdAt" to updated.createdAt,
                    "branchId" to branchId,
                    "assignedToName" to (updated.assignedToName ?: ""),
                    "assignedToUid" to (updated.assignedToUid ?: ""),
                    "verifiedBy" to (updated.verifiedBy ?: ""),
                    "verificationNotes" to (updated.verificationNotes ?: ""),
                    "verificationChannel" to (updated.verificationChannel ?: ""),
                    "verificationCustomerName" to (updated.verificationCustomerName ?: ""),
                    "verifiedAt" to (updated.verifiedAt ?: 0L),
                    "isApproved" to updated.isApproved,
                    "approvedBy" to (updated.approvedBy ?: ""),
                    "approvedAt" to (updated.approvedAt ?: 0L),
                    "approvalNotes" to (updated.approvalNotes ?: "")
                )
                syncEntityToFirestore("branch_operation_tasks", updated.id.toString(), map)
                logAuditTrail(
                    action = "TASK_APPROVED_BY_MANAGER",
                    details = "Task '${updated.title}' approved by $managerName. Review notes: ${notes.take(40)}...",
                    affectedId = updated.id.toString()
                )
            }
            onFinished(true, "Task approved and finalized by manager.")
        }
    }

    fun toggleOperationTask(task: OperationTask) {
        viewModelScope.launch {
            val updated = task.copy(isCompleted = !task.isCompleted)
            repository.updateOperationTask(updated)
            
            val branchId = _currentPharmacistBranchId.value
            if (!branchId.isNullOrBlank()) {
                val map: Map<String, Any> = mapOf(
                    "id" to updated.id,
                    "title" to updated.title,
                    "description" to updated.description,
                    "urgency" to updated.urgency,
                    "category" to updated.category,
                    "isCompleted" to updated.isCompleted,
                    "createdAt" to updated.createdAt,
                    "branchId" to branchId,
                    "assignedToName" to (updated.assignedToName ?: ""),
                    "assignedToUid" to (updated.assignedToUid ?: ""),
                    "verifiedBy" to (updated.verifiedBy ?: ""),
                    "verificationNotes" to (updated.verificationNotes ?: ""),
                    "verificationChannel" to (updated.verificationChannel ?: ""),
                    "verificationCustomerName" to (updated.verificationCustomerName ?: ""),
                    "verifiedAt" to (updated.verifiedAt ?: 0L),
                    "isApproved" to updated.isApproved,
                    "approvedBy" to (updated.approvedBy ?: ""),
                    "approvedAt" to (updated.approvedAt ?: 0L),
                    "approvalNotes" to (updated.approvalNotes ?: "")
                )
                syncEntityToFirestore("branch_operation_tasks", updated.id.toString(), map)
                logAuditTrail(
                    action = "TASK_STATE_CHANGE",
                    details = "Task '${updated.title}' completion updated to ${updated.isCompleted}.",
                    affectedId = updated.id.toString()
                )
            }
        }
    }

    fun deleteOperationTask(task: OperationTask) {
        viewModelScope.launch {
            repository.deleteOperationTask(task)
            val branchId = _currentPharmacistBranchId.value
            if (!branchId.isNullOrBlank()) {
                deleteEntityFromFirestore("branch_operation_tasks", task.id.toString())
                logAuditTrail(
                    action = "DELETE_TASK",
                    details = "Deleted task '${task.title}'",
                    affectedId = task.id.toString()
                )
            }
        }
    }

    // --- Receipt Actions ---
    fun addReceipt(customerName: String, totalAmount: Double, imageFileName: String, isInvoice: Boolean = false, paymentStatus: String = "Paid") {
        viewModelScope.launch {
            repository.insertReceipt(
                Receipt(
                    customerName = customerName,
                    totalAmount = totalAmount,
                    imageFileName = imageFileName,
                    isInvoice = isInvoice,
                    paymentStatus = paymentStatus
                )
            )
        }
    }

    fun updateReceipt(receipt: Receipt) {
        viewModelScope.launch {
            repository.updateReceipt(receipt)
        }
    }

    fun deleteReceipt(receipt: Receipt) {
        viewModelScope.launch {
            repository.deleteReceipt(receipt)
        }
    }

    // --- Customer Actions ---
    fun triggerImmediateSync() {
        try {
            val syncRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.work.CloudSyncWorker>().build()
            androidx.work.WorkManager.getInstance(getApplication()).enqueue(syncRequest)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isCustomerPhoneUnique(phone: String, excludeId: Int = 0): Boolean {
        val normalizedNew = phone.replace(Regex("[^+\\d]"), "")
        if (normalizedNew.isEmpty()) return true
        return customers.value.none { 
            it.id != excludeId && it.phoneNumber.replace(Regex("[^+\\d]"), "").equals(normalizedNew, ignoreCase = true)
        }
    }

    fun addCustomer(
        name: String,
        phone: String,
        email: String = "",
        notes: String = "",
        age: Int = 30,
        gender: String = "Male",
        state: String = "Lagos",
        lga: String = "Ikeja",
        city: String = "Ikeja",
        consentPrescriptionTracking: Boolean = true,
        consentSmsRefills: Boolean = false,
        consentCloudSync: Boolean = false,
        consentChannel: String = "Verbal Consent"
    ) {
        viewModelScope.launch {
            if (!isCustomerPhoneUnique(phone)) {
                android.widget.Toast.makeText(
                    getApplication(),
                    "Error: A customer with phone number '$phone' already exists!",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                return@launch
            }
            val newCust = Customer(
                name = name.trim(),
                phoneNumber = phone.trim(),
                email = email.trim(),
                notes = notes.trim(),
                age = age,
                gender = gender,
                state = state.trim(),
                lga = lga.trim(),
                city = city.trim(),
                consentPrescriptionTracking = consentPrescriptionTracking,
                consentSmsRefills = consentSmsRefills,
                consentCloudSync = consentCloudSync,
                consentChannel = consentChannel,
                consentLastUpdated = System.currentTimeMillis()
            )
            val insertedId = repository.insertCustomer(newCust)
            val finalCust = newCust.copy(id = insertedId)
            parseCustomerNotesForAlerts(finalCust)
            syncCustomerToBranch(finalCust)
            triggerImmediateSync()
        }
    }
    
    fun updateCustomer(customer: Customer) {
        viewModelScope.launch { 
            if (!isCustomerPhoneUnique(customer.phoneNumber, customer.id)) {
                android.widget.Toast.makeText(
                    getApplication(),
                    "Error: Another customer is already registered with phone number '${customer.phoneNumber}'!",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                return@launch
            }
            repository.updateCustomer(customer) 
            parseCustomerNotesForAlerts(customer)
            syncCustomerToBranch(customer)
            triggerImmediateSync()
        }
    }
    
    fun deleteCustomer(customer: Customer) {
        viewModelScope.launch { 
            repository.deleteCustomer(customer)
            // Cleanup orphans since no ForeignKeys cascade
            customerMedications.value.filter { it.customerId == customer.id }.forEach { med ->
                repository.deleteCustomerMedication(med)
                deleteEntityFromFirestore("branch_customer_medications", med.id.toString())
                try {
                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("customer_medications").document("${deviceId}_${med.id}").delete()
                } catch (e: Exception) { e.printStackTrace() }
            }
            clinicalInterventions.value.filter { it.customerId == customer.id }.forEach { inter ->
                repository.deleteClinicalIntervention(inter)
                deleteEntityFromFirestore("branch_interventions", inter.id.toString())
                try {
                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("interventions").document("${deviceId}_${inter.id}").delete()
                } catch (e: Exception) { e.printStackTrace() }
            }
            deleteEntityFromFirestore("branch_customers", customer.id.toString())
            try {
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("customers").document("${deviceId}_${customer.id}").delete()
            } catch (e: Exception) { e.printStackTrace() }
            triggerImmediateSync()
        }
    }
    
    fun addCustomerMedication(customerId: Int, invItemId: Int, medName: String, customDosage: String, cost: Double, cycleDays: Int, nextRefill: Long) {
        viewModelScope.launch {
            val med = CustomerMedication(
                customerId = customerId,
                inventoryItemId = invItemId,
                medicationName = medName,
                customDosage = customDosage,
                cost = cost,
                cycleDays = cycleDays,
                nextRefillDate = nextRefill
            )
            val insertedId = repository.insertCustomerMedication(med)
            syncCustomerMedicationToBranch(med.copy(id = insertedId))
            triggerImmediateSync()
        }
    }
    
    fun updateCustomerMedication(med: CustomerMedication) {
        viewModelScope.launch { 
            repository.updateCustomerMedication(med) 
            syncCustomerMedicationToBranch(med)
            triggerImmediateSync()
        }
    }

    fun deleteCustomerMedication(med: CustomerMedication) {
        viewModelScope.launch { 
            repository.deleteCustomerMedication(med) 
            deleteEntityFromFirestore("branch_customer_medications", med.id.toString())
            triggerImmediateSync()
        }
    }

    // --- Clinical Intervention Actions ---
    fun addClinicalIntervention(customerId: Int, presentation: String, testResults: String, recommendation: String) {
        viewModelScope.launch {
            val inter = ClinicalIntervention(
                customerId = customerId,
                presentation = presentation,
                testResults = testResults,
                recommendation = recommendation
            )
            val insertedId = repository.insertClinicalIntervention(inter)
            val finalInter = inter.copy(id = insertedId)
            syncClinicalInterventionToBranch(finalInter)
            triggerImmediateSync()

            // Automate follow-up reminders for Day 3, 7, and 14
            val customer = repository.getCustomerById(customerId)
            if (customer != null) {
                val sdf = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
                val nowL = System.currentTimeMillis()
                
                val day3 = nowL + (3L * 24 * 60 * 60 * 1000)
                val day7 = nowL + (7L * 24 * 60 * 60 * 1000)
                val day14 = nowL + (14L * 24 * 60 * 60 * 1000)

                val alert3 = CustomerAlert(
                    customerName = customer.name,
                    phoneNumber = customer.phoneNumber,
                    medicationName = "Clinical Follow-up (Day 3)",
                    alertType = "Check-in",
                    status = "Pending",
                    scheduledTime = sdf.format(java.util.Date(day3))
                )
                val alert7 = CustomerAlert(
                    customerName = customer.name,
                    phoneNumber = customer.phoneNumber,
                    medicationName = "Clinical Follow-up (Day 7)",
                    alertType = "Check-in",
                    status = "Pending",
                    scheduledTime = sdf.format(java.util.Date(day7))
                )
                val alert14 = CustomerAlert(
                    customerName = customer.name,
                    phoneNumber = customer.phoneNumber,
                    medicationName = "Clinical Follow-up (Day 14)",
                    alertType = "Check-in",
                    status = "Pending",
                    scheduledTime = sdf.format(java.util.Date(day14))
                )

                repository.insertCustomerAlert(alert3)
                repository.insertCustomerAlert(alert7)
                repository.insertCustomerAlert(alert14)
            }
        }
    }

    fun updateClinicalInterventionStatus(intervention: ClinicalIntervention, newStatus: String) {
        viewModelScope.launch {
            val updated = intervention.copy(currentStatus = newStatus)
            repository.updateClinicalIntervention(updated)
            syncClinicalInterventionToBranch(updated)
        }
    }

    fun saveTriageToDossier(
        customerName: String,
        customerPhone: String,
        customerId: Int?, // if null, create new customer
        conditionName: String,
        checkedSymptoms: String,
        recommendedMed: String,
        followUpDays: Int,
        onFinished: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val finalCustomerId: Int
                if (customerId == null || customerId == 0) {
                    // Create new customer
                    val finalPhone = customerPhone.trim()
                    val normalizedNew = finalPhone.replace(Regex("[^+\\d]"), "")
                    val existingCust = customers.value.find { 
                        it.phoneNumber.replace(Regex("[^+\\d]"), "").equals(normalizedNew, ignoreCase = true)
                    }
                    if (existingCust != null) {
                        finalCustomerId = existingCust.id
                    } else {
                        val newCust = Customer(
                            name = customerName.trim(),
                            phoneNumber = finalPhone,
                            email = "",
                            notes = "Triage Patient: $conditionName",
                            age = 30,
                            gender = "Male",
                            state = getPharmacyState().ifBlank { "Lagos" },
                            lga = getPharmacyLga().ifBlank { "Ikeja" },
                            city = "Ikeja",
                            consentPrescriptionTracking = true,
                            consentSmsRefills = true, // Opt-in for triage follow-ups
                            consentCloudSync = true,
                            consentChannel = "Verbal Consent",
                            consentLastUpdated = System.currentTimeMillis()
                        )
                        val insertedId = repository.insertCustomer(newCust)
                        finalCustomerId = insertedId
                        // Sync
                        syncCustomerToBranch(newCust.copy(id = insertedId))
                    }
                } else {
                    finalCustomerId = customerId
                }

                // Create Clinical Intervention
                val inter = ClinicalIntervention(
                    customerId = finalCustomerId,
                    presentation = "Triage: $conditionName",
                    testResults = checkedSymptoms,
                    recommendation = "Recommended Care Plan: $recommendedMed. Follow-up scheduled in $followUpDays days."
                )
                val insertedInterId = repository.insertClinicalIntervention(inter)
                syncClinicalInterventionToBranch(inter.copy(id = insertedInterId))

                // Create Customer Medication (Follow-up Schedule)
                if (recommendedMed.isNotBlank()) {
                    val nextRefill = System.currentTimeMillis() + (followUpDays * 24L * 60 * 60 * 1000)
                    val med = CustomerMedication(
                        customerId = finalCustomerId,
                        inventoryItemId = 0,
                        medicationName = recommendedMed,
                        customDosage = "As directed by pharmacist",
                        cost = 0.0,
                        cycleDays = followUpDays,
                        nextRefillDate = nextRefill
                    )
                    val insertedMedId = repository.insertCustomerMedication(med)
                    syncCustomerMedicationToBranch(med.copy(id = insertedMedId))
                }

                // Also automate follow-up alerts
                val customer = repository.getCustomerById(finalCustomerId)
                if (customer != null) {
                    val sdf = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
                    val followUpTime = System.currentTimeMillis() + (followUpDays * 24L * 60 * 60 * 1000)
                    val alert = CustomerAlert(
                        customerName = customer.name,
                        phoneNumber = customer.phoneNumber,
                        medicationName = "Clinical Follow-up ($conditionName)",
                        alertType = "Check-in",
                        status = "Pending",
                        scheduledTime = sdf.format(java.util.Date(followUpTime))
                    )
                    repository.insertCustomerAlert(alert)
                }

                triggerImmediateSync()
                onFinished(true, "Successfully saved triage outcome and scheduled follow-up for $customerName!")
            } catch (e: Exception) {
                e.printStackTrace()
                onFinished(false, "Failed to save dossier: ${e.localizedMessage}")
            }
        }
    }

    fun generateAndSendFollowUp(intervention: ClinicalIntervention, customer: Customer, context: Context) {
        viewModelScope.launch {
            // First we notify the user we are generating the message
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Drafting automated follow-up via Gemini...", Toast.LENGTH_SHORT).show()
            }

            try {
                val apiKey = getApiKey()
                
                val instruction = "You are a professional, empathetic pharmacist assistant. Draft a short, warm, and professional WhatsApp follow-up message to a patient. Just output the message itself without any preamble."
                
                val promptText = """
                The patient's name is ${customer.name}.
                Symptom/Presentation: ${intervention.presentation}.
                Recommendations given: ${intervention.recommendation}.
                Test Result (if any): ${intervention.testResults}.
                Check in on them to see how their symptoms are doing and if the recommendation is helping.
                """.trimIndent()
                
                val request = GenerateContentRequest(
                    systemInstruction = Content(listOf(Part(instruction))),
                    contents = listOf(Content(listOf(Part(promptText))))
                )
                
                val response = RetrofitClient.service.generateContent(apiKey, request)
                val generatedMsg = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "Could not generate message at this time."
                
                // Switch back to Main for starting intent
                withContext(Dispatchers.Main) {
                    val encodedMsg = Uri.encode(generatedMsg.trim())
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://api.whatsapp.com/send?phone=${customer.phoneNumber}&text=$encodedMsg")
                    }
                    try {
                        context.startActivity(intent)
                        // Wait, don't update status until successful?
                        // Let's assume after drafting, they might be feeling better or we update status manually in the UI
                    } catch (e: Exception) {
                        Toast.makeText(context, "WhatsApp not installed.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (e is retrofit2.HttpException && e.code() == 429) {
                        val fallbackMsg = "Hi ${customer.name}, checking in regarding your ${intervention.presentation}. Let me know if you need any further assistance!"
                        val encodedMsg = Uri.encode(fallbackMsg)
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("https://api.whatsapp.com/send?phone=${customer.phoneNumber}&text=$encodedMsg")
                        }
                        try {
                            context.startActivity(intent)
                        } catch (ex: Exception) {
                            Toast.makeText(context, "WhatsApp not installed.", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Error drafting: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun loadTemplates() {
        val json = prefs.getString("templates", "[]") ?: "[]"
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val type = Types.newParameterizedType(List::class.java, WhatsAppTemplate::class.java)
        val adapter = moshi.adapter<List<WhatsAppTemplate>>(type)
        try {
            val templates = adapter.fromJson(json) ?: emptyList()
            _customTemplates.value = templates
        } catch (e: Exception) {
            _customTemplates.value = emptyList()
        }
    }

    private fun saveTemplates(templates: List<WhatsAppTemplate>) {
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val type = Types.newParameterizedType(List::class.java, WhatsAppTemplate::class.java)
        val adapter = moshi.adapter<List<WhatsAppTemplate>>(type)
        prefs.edit().putString("templates", adapter.toJson(templates)).apply()
        _customTemplates.value = templates
    }

    fun addWhatsAppTemplate(title: String, message: String) {
        val current = _customTemplates.value.toMutableList()
        current.add(WhatsAppTemplate(title = title, message = message))
        saveTemplates(current)
    }

    fun deleteWhatsAppTemplate(template: WhatsAppTemplate) {
        val current = _customTemplates.value.toMutableList()
        current.remove(template)
        saveTemplates(current)
    }

    // --- Pharmacy Triage Operations ---
    fun insertTriageCondition(condition: TriageCondition) {
        viewModelScope.launch {
            repository.insertTriageCondition(condition)
        }
    }

    fun updateTriageCondition(condition: TriageCondition) {
        viewModelScope.launch {
            repository.updateTriageCondition(condition)
        }
    }

    fun deleteTriageCondition(condition: TriageCondition) {
        viewModelScope.launch {
            repository.deleteTriageCondition(condition)
        }
    }

    fun toggleTriageFavorite(condition: TriageCondition) {
        viewModelScope.launch {
            repository.updateTriageCondition(condition.copy(
                isFavorite = !condition.isFavorite,
                lastUpdated = System.currentTimeMillis()
            ))
        }
    }

    fun incrementTriageUsage(condition: TriageCondition) {
        viewModelScope.launch {
            repository.updateTriageCondition(condition.copy(
                usageCount = condition.usageCount + 1
            ))
        }
    }

    // --- Pharmacy Triage AI Generator State ---
    sealed interface TriageAiState {
        object Idle : TriageAiState
        object Generating : TriageAiState
        data class Success(val condition: TriageCondition) : TriageAiState
        data class Error(val message: String) : TriageAiState
    }

    private val _triageAiState = kotlinx.coroutines.flow.MutableStateFlow<TriageAiState>(TriageAiState.Idle)
    val triageAiState: StateFlow<TriageAiState> = _triageAiState.asStateFlow()

    fun resetTriageAiState() {
        _triageAiState.value = TriageAiState.Idle
    }

    fun generateTriageConditionWithAI(topic: String) {
        if (topic.isBlank()) return
        _triageAiState.value = TriageAiState.Generating
        viewModelScope.launch {
            try {
                val apiKey = getApiKey()
                if (apiKey.isBlank()) {
                    _triageAiState.value = TriageAiState.Error("Gemini API key is not configured. Please supply your API key in the App Settings.")
                    return@launch
                }

                val prompt = """
                    You are an expert clinical pharmacy triage guide developer.
                    TASK: Generate a complete clinical triage protocol template for the following condition/symptoms: "$topic".
                    
                    Respond strictly in JSON format matching this schema without any markdown wrapping (no ```json):
                    {
                        "conditionName": "Condition Name",
                        "alternativeNames": "Alternative/clerical names or synonyms",
                        "category": "Medical Specialty / Category",
                        "briefDescription": "Short description of pathophysiology, prevalence, and typical clinical presentation",
                        "keySymptoms": "List main symptoms, e.g. sharp joint pain, sudden swelling",
                        "questions": [
                            {
                                "question": "Patient-facing screening question, e.g. Do you have a high fever or flank pain?",
                                "required": true,
                                "isRedFlag": true
                            },
                            {
                                "question": "Another screener question, e.g. How many days have you had these symptoms?",
                                "required": true,
                                "isRedFlag": false
                            }
                        ],
                        "referralCriteria": "Clear signs and indications of when to refer the patient to a doctor or Emergency Room",
                        "severityAssessment": "How to classify severity categories (Mild, Moderate, Severe / Urgent)",
                        "recommendedOtcs": "Common over-the-counter treatment options/doses",
                        "prescriptionOptions": "Common first-line prescription options (strictly for informational reference only)",
                        "counsellingPoints": "Crucial clinical instructions to give to the patient",
                        "lifestyleAdvice": "Non-pharmacological lifestyle recommendations",
                        "followUpTimeline": "When the patient should follow up with a professional if unresolved"
                    }
                """.trimIndent()

                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                    generationConfig = GenerationConfig(responseMimeType = "application/json")
                )
                
                val rawResponse = RetrofitClient.service.generateContent(apiKey, request)
                var rawText = rawResponse.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
                rawText = rawText.replace("```json", "").replace("```", "").trim()

                val moshiIn = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
                val mapAdapter = moshiIn.adapter(Map::class.java)
                val parsedMap = mapAdapter.fromJson(rawText) as? Map<*, *>
                
                if (parsedMap != null) {
                    val condName = parsedMap["conditionName"] as? String ?: topic
                    val altNames = parsedMap["alternativeNames"] as? String ?: ""
                    val cat = parsedMap["category"] as? String ?: "General"
                    val desc = parsedMap["briefDescription"] as? String ?: "No description provided."
                    val symptoms = parsedMap["keySymptoms"] as? String ?: ""
                    
                    val questionsList = parsedMap["questions"] as? List<*>
                    val triageQuestions = mutableListOf<TriageQuestion>()
                    if (questionsList != null) {
                        for (qItem in questionsList) {
                            if (qItem is Map<*, *>) {
                                val text = qItem["question"] as? String ?: ""
                                val required = qItem["required"] as? Boolean ?: true
                                val redVal = qItem["isRedFlag"] as? Boolean ?: (qItem["redFlag"] as? Boolean ?: (qItem["red_flag"] as? Boolean ?: false))
                                if (text.isNotBlank()) {
                                    triageQuestions.add(TriageQuestion(text, required, redVal))
                                }
                            }
                        }
                    }

                    val listType = Types.newParameterizedType(List::class.java, TriageQuestion::class.java)
                    val qAdapter = moshiIn.adapter<List<TriageQuestion>>(listType)
                    val questionsJsonStr = qAdapter.toJson(triageQuestions)

                    val waTemplateBuilder = StringBuilder()
                    waTemplateBuilder.append("Hello [Patient Name],\n\n")
                    waTemplateBuilder.append("Based on our discussion regarding $condName ($altNames), I would like to understand your symptoms better.\n\n")
                    waTemplateBuilder.append("Please reply with:\n")
                    triageQuestions.forEachIndexed { index, questionObj ->
                        val redTagStr = if (questionObj.isRedFlag) " (Urgent)" else ""
                        waTemplateBuilder.append("${index + 1}. ${questionObj.question}$redTagStr\n")
                    }
                    waTemplateBuilder.append("\nYour answers will help us guide you appropriately.\n\n")
                    waTemplateBuilder.append("Regards,\nCareflux Pharmacist")

                    val generatedCondition = TriageCondition(
                        conditionName = condName,
                        alternativeNames = altNames,
                        category = cat,
                        briefDescription = desc,
                        keySymptoms = symptoms,
                        questionsJson = questionsJsonStr,
                        referralCriteria = parsedMap["referralCriteria"] as? String ?: "",
                        severityAssessment = parsedMap["severityAssessment"] as? String ?: "",
                        recommendedOtcs = parsedMap["recommendedOtcs"] as? String ?: "",
                        prescriptionOptions = parsedMap["prescriptionOptions"] as? String ?: "",
                        counsellingPoints = parsedMap["counsellingPoints"] as? String ?: "",
                        lifestyleAdvice = parsedMap["lifestyleAdvice"] as? String ?: "",
                        followUpTimeline = parsedMap["followUpTimeline"] as? String ?: "",
                        whatsappTemplate = waTemplateBuilder.toString(),
                        lastEditedBy = "Careflux AI Generator",
                        lastUpdated = System.currentTimeMillis()
                    )

                    _triageAiState.value = TriageAiState.Success(generatedCondition)
                } else {
                    _triageAiState.value = TriageAiState.Error("Failed to parse the Gemini structure response.")
                }
            } catch (e: Exception) {
                val is429 = e.message?.contains("429") == true || (e is retrofit2.HttpException && e.code() == 429)
                if (is429) {
                    _triageAiState.value = TriageAiState.Error("AI Generation rate limit active (429). Please try again shortly or configure a custom API key in App Settings.")
                } else {
                    _triageAiState.value = TriageAiState.Error(e.message ?: "An unknown error occurred during AI generation.")
                }
            }
        }
    }

    fun recordMedicationSale(cartItem: CartItem, customer: Customer?, overrideReason: String? = null, prescribingDoctor: String? = null, prescriptionRef: String? = null) {
        viewModelScope.launch {
            val inv = cartItem.inventoryItem
            val age = customer?.age ?: 30
            val gender = customer?.gender ?: "Male"
            val state = customer?.state ?: "Lagos"
            val lga = customer?.lga ?: "Ikeja"
            val city = customer?.city ?: "Ikeja"

            // 1. Core FEFO Batch Deduction Engine
            val batches = repository.getBatchesForItem(inv.id).first()
            val activeBatches = if (batches.isEmpty()) {
                listOf(
                    com.example.data.InventoryBatch(
                        inventoryItemId = inv.id,
                        batchNumber = inv.batchNumber.ifBlank { "B-DEFAULT" },
                        stockQuantity = inv.stockQuantity,
                        expiryDate = inv.expiryDate,
                        price = inv.price
                    )
                )
            } else {
                batches
            }

            var remainingToDeduct = cartItem.quantity
            val updatedBatches = mutableListOf<com.example.data.InventoryBatch>()

            val now = System.currentTimeMillis()
            // Sort: prioritize active, unexpired batches sorted by expiryDate ascending (FEFO)
            val sortedBatches = activeBatches.sortedWith(
                compareBy<com.example.data.InventoryBatch> { it.expiryDate <= now }
                    .thenBy { it.expiryDate }
            )

            for (batch in sortedBatches) {
                if (remainingToDeduct <= 0) {
                    updatedBatches.add(batch)
                    continue
                }

                if (batch.stockQuantity >= remainingToDeduct) {
                    val newBatchQty = batch.stockQuantity - remainingToDeduct
                    updatedBatches.add(batch.copy(stockQuantity = newBatchQty))
                    remainingToDeduct = 0
                } else {
                    remainingToDeduct -= batch.stockQuantity
                    updatedBatches.add(batch.copy(stockQuantity = 0))
                }
            }

            // Save updated batches
            for (batch in updatedBatches) {
                if (batch.id == 0) {
                    repository.insertInventoryBatch(batch)
                } else {
                    repository.updateInventoryBatch(batch)
                }
            }

            // Recalculate total item stock
            val totalBatchStock = updatedBatches.sumOf { it.stockQuantity }
            val updatedItem = inv.copy(
                stockQuantity = totalBatchStock,
                totalSoldQuantity = inv.totalSoldQuantity + cartItem.quantity,
                lastSoldDate = System.currentTimeMillis()
            )
            saveAndSyncInventoryItemDirectly(updatedItem)

            val sale = MedicationSale(
                productName = inv.name,
                brand = inv.brand,
                genericName = inv.name,
                category = inv.category,
                quantitySold = cartItem.quantity,
                dateSold = System.currentTimeMillis(),
                pharmacyNode = android.os.Build.MODEL,
                patientAge = age,
                patientGender = gender,
                patientState = state,
                patientLga = lga,
                patientCity = city,
                salePrice = inv.price * cartItem.quantity,
                batchNumber = inv.batchNumber
            )

            // Insert locally
            repository.insertMedicationSale(sale)

            // Sync to Firestore
            try {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                db.collection("medication_sales").add(sale)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Secure IPC Broadcast to UBA Savings App
            sendSaleIPCBroadcast(
                context = getApplication(),
                customerName = customer?.name,
                amount = sale.salePrice,
                productName = sale.productName,
                quantity = sale.quantitySold
            )

            // Secure Clinical Audit Logging for Override or Prescription Checked
            if (!overrideReason.isNullOrBlank() || !prescribingDoctor.isNullOrBlank() || !prescriptionRef.isNullOrBlank()) {
                val auditMsg = StringBuilder("Clinical sale tracing:")
                if (!overrideReason.isNullOrBlank()) {
                    auditMsg.append(" [OVERRIDE] Justification: $overrideReason")
                }
                if (!prescribingDoctor.isNullOrBlank() || !prescriptionRef.isNullOrBlank()) {
                    auditMsg.append(" [RX VERIFIED] Dr. ${prescribingDoctor ?: "N/A"} (Ref: ${prescriptionRef ?: "N/A"})")
                }
                logAuditTrail(
                    action = "CLINICAL_DISPENSE_COMPLIANCE",
                    details = auditMsg.toString(),
                    affectedId = inv.id.toString()
                )
            }
        }
    }

    fun sendSaleIPCBroadcast(
        context: Context,
        customerName: String?,
        amount: Double,
        productName: String,
        quantity: Int
    ) {
        val intent = Intent("com.example.savingsapp.ACTION_UPDATE_TARGET_GOAL").apply {
            // CRITICAL: Explicitly targets the Savings App's dynamic package ID in AI Studio
            setClassName(
                "com.aistudio.ubasave.pqwzvx", 
                "com.example.savingsapp.receivers.SaleReceiver"
            )
            
            // Populate transaction data payload
            putExtra("key_customer_name", customerName ?: "Walk-in Customer")
            putExtra("key_sale_amount", amount)
            putExtra("key_product_name", productName)
            putExtra("key_quantity_sold", quantity)
            putExtra("key_timestamp", System.currentTimeMillis())
            
            // Ensure background/closed apps can successfully wake up and register this
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        
        context.sendBroadcast(intent)
    }

    fun createRescueListing(inv: InventoryItem, qty: Int, price: Double, commPercentage: Double, durationDays: Int) {
        viewModelScope.launch {
            val deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
            try {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val docRef = db.collection("expiry_rescue_listings").document()
                val firestoreId = docRef.id

                val lgaVal = getPharmacyLga()
                val stateVal = getPharmacyState()

                // 1. Insert local copy to Room immediately for instant responsiveness
                repository.insertRescueListing(
                    RescueListing(
                        id = 0,
                        firestoreId = firestoreId,
                        productName = inv.name,
                        batchNumber = inv.batchNumber,
                        quantity = qty,
                        expiryDate = inv.expiryDate,
                        sellingPrice = price,
                        commissionPercentage = commPercentage,
                        rescueDurationDays = durationDays,
                        ownerDeviceId = deviceId,
                        ownerDeviceModel = deviceModel,
                        listedAt = System.currentTimeMillis(),
                        status = "Available",
                        ownerLga = lgaVal,
                        ownerState = stateVal
                    )
                )

                // 2. Synchronize to Firestore (supports offline caching seamlessly)
                val listingData = hashMapOf(
                    "productName" to inv.name,
                    "batchNumber" to inv.batchNumber,
                    "quantity" to qty,
                    "expiryDate" to inv.expiryDate,
                    "sellingPrice" to price,
                    "commissionPercentage" to commPercentage,
                    "rescueDurationDays" to durationDays,
                    "ownerDeviceId" to deviceId,
                    "ownerDeviceModel" to deviceModel,
                    "listedAt" to System.currentTimeMillis(),
                    "status" to "Available",
                    "acceptedByDeviceId" to "",
                    "acceptedByDeviceModel" to "",
                    "acceptedAt" to 0L,
                    "soldAt" to 0L,
                    "profitShareAmount" to 0.0,
                    "ownerLga" to lgaVal,
                    "ownerState" to stateVal
                )
                docRef.set(listingData).await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun retractRescueListing(listing: RescueListing) {
        viewModelScope.launch {
            if (listing.firestoreId.isEmpty()) return@launch
            try {
                // Remove local Room record immediately
                repository.deleteRescueListingByFirestoreId(listing.firestoreId)

                // Trigger Firestore deletion
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                db.collection("expiry_rescue_listings").document(listing.firestoreId).delete().await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun acceptRescueListing(listing: RescueListing, onFinished: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
            if (listing.firestoreId.isEmpty()) {
                onFinished(false, "Invalid listing document reference.")
                return@launch
            }

            try {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val docRef = db.collection("expiry_rescue_listings").document(listing.firestoreId)

                // Atomic transaction to secure exclusive claim and prevent double-allocation
                val success = db.runTransaction { transaction ->
                    val snapshot = transaction.get(docRef)
                    val currentStatus = snapshot.getString("status") ?: "Available"
                    if (currentStatus == "Available") {
                        transaction.update(docRef, mapOf(
                            "status" to "Accepted",
                            "acceptedByDeviceId" to deviceId,
                            "acceptedByDeviceModel" to deviceModel,
                            "acceptedAt" to System.currentTimeMillis()
                        ))
                        true
                    } else {
                        false
                    }
                }.await()

                if (success) {
                    // Update local Room database with Accepted status
                    val matched = repository.getRescueListingByFirestoreId(listing.firestoreId)
                    if (matched != null) {
                        repository.insertRescueListing(
                            matched.copy(
                                status = "Accepted",
                                acceptedByDeviceId = deviceId,
                                acceptedByDeviceModel = deviceModel,
                                acceptedAt = System.currentTimeMillis()
                            )
                        )
                    }
                    onFinished(true, "Rescue listing secured successfully!")
                } else {
                    // Claim failed because someone else booked it! Update local database state to prevent ghost listings
                    val matched = repository.getRescueListingByFirestoreId(listing.firestoreId)
                    if (matched != null) {
                        repository.insertRescueListing(
                            matched.copy(
                                status = "Claimed_Other"
                            )
                        )
                    }
                    onFinished(false, "Transaction failed: This medicine listing was already claimed by another pharmacy.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onFinished(false, "Network error: ${e.localizedMessage ?: "Could not verify claim status."}")
            }
        }
    }

    fun sellRescueListing(listing: RescueListing, qtyToSell: Int, customer: Customer?) {
        viewModelScope.launch {
            if (listing.firestoreId.isEmpty() || qtyToSell <= 0) return@launch
            val isFullSale = qtyToSell >= listing.quantity
            val remainingQty = (listing.quantity - qtyToSell).coerceAtLeast(0)
            val newStatus = if (isFullSale) "Sold" else "Accepted"
            
            val totalSaleRevenue = listing.sellingPrice * qtyToSell
            val rescuerCommission = totalSaleRevenue * (listing.commissionPercentage / 100.0)

            try {
                // Update local Room record immediately
                val matched = repository.getRescueListingByFirestoreId(listing.firestoreId)
                if (matched != null) {
                    repository.insertRescueListing(
                        matched.copy(
                            quantity = remainingQty,
                            status = newStatus,
                            soldAt = System.currentTimeMillis(),
                            profitShareAmount = (listing.profitShareAmount + rescuerCommission)
                        )
                    )
                }

                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val docRef = db.collection("expiry_rescue_listings").document(listing.firestoreId)
                
                docRef.update(
                    mapOf(
                        "quantity" to remainingQty,
                        "status" to newStatus,
                        "soldAt" to System.currentTimeMillis(),
                        "profitShareAmount" to (listing.profitShareAmount + rescuerCommission)
                    )
                ).await()

                // Double record medication sale for demographics analytics with correct attribution
                val deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
                val patientAge = customer?.age ?: 30
                val patientGen = customer?.gender ?: "Male"
                val patientSt = customer?.state ?: "Lagos"
                val patientLgaName = customer?.lga ?: "Ikeja"
                val patientCityVal = customer?.city ?: "Ikeja"

                val sale = MedicationSale(
                    productName = listing.productName,
                    brand = "Rescued from ${listing.ownerDeviceModel}",
                    genericName = listing.productName,
                    category = "Rescue Marketplace",
                    quantitySold = qtyToSell,
                    dateSold = System.currentTimeMillis(),
                    pharmacyNode = deviceModel,
                    patientAge = patientAge,
                    patientGender = patientGen,
                    patientState = patientSt,
                    patientLga = patientLgaName,
                    patientCity = patientCityVal,
                    salePrice = totalSaleRevenue,
                    batchNumber = listing.batchNumber
                )
                repository.insertMedicationSale(sale)
                db.collection("medication_sales").add(sale)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun logAdminAction(admin: String, action: String, nodeId: String, nodeModel: String, reason: String) {
        viewModelScope.launch {
            val log = AdminAuditLog(
                adminName = admin,
                actionPerformed = action,
                timestamp = System.currentTimeMillis(),
                affectedNodeId = nodeId,
                affectedNodeModel = nodeModel,
                reason = reason
            )
            repository.insertAdminAuditLog(log)
            try {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                db.collection("admin_audit_logs").add(log)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateNodeStatus(nodeId: String, action: String, reason: String) {
        viewModelScope.launch {
            if (nodeId.isEmpty()) return@launch
            val resolvedVal = when (action) {
                "SUSPEND" -> true
                "REACTIVATE" -> false
                else -> false
            }

            try {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val docRef = db.collection("device_configs").document(nodeId)
                val updateData = mapOf(
                    "isSuspended" to resolvedVal,
                    "statusReason" to reason,
                    "statusLastUpdated" to System.currentTimeMillis()
                )
                
                docRef.set(updateData, com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener {
                        // Also update in registered_pharmacists
                        db.collection("registered_pharmacists")
                            .document(nodeId)
                            .set(mapOf("isSuspended" to resolvedVal), com.google.firebase.firestore.SetOptions.merge())
                        db.collection("registered_pharmacists")
                            .whereEqualTo("deviceId", nodeId)
                            .get()
                            .addOnSuccessListener { qSnap ->
                                for (docSnap in qSnap.documents) {
                                    docSnap.reference.set(mapOf("isSuspended" to resolvedVal), com.google.firebase.firestore.SetOptions.merge())
                                }
                            }

                        // Get node detail to log
                        docRef.get().addOnSuccessListener { snap ->
                            val modelName = snap.getString("deviceModel") ?: "Unknown Node"
                            logAdminAction(
                                admin = "Chinedu (Admin)",
                                action = if (resolvedVal) "SUSPEND_NODE" else "REACTIVATE_NODE",
                                nodeId = nodeId,
                                nodeModel = modelName,
                                reason = reason
                            )
                        }
                    }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun saveOrUpdateDeviceConfig() {
        viewModelScope.launch {
            try {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                val user = auth.currentUser
                val deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
                val docRef = db.collection("device_configs").document(deviceId)

                val dataMap = hashMapOf<String, Any>(
                    "deviceId" to deviceId,
                    "deviceModel" to deviceModel,
                    "aiContentEnabled" to true,
                    "carefluxAiEnabled" to true,
                    "lastActive" to System.currentTimeMillis(),
                    "pharmacyName" to getPharmacyName(),
                    "lga" to getPharmacyLga(),
                    "state" to getPharmacyState()
                )
                if (user != null) {
                    dataMap["ownerEmail"] = user.email.orEmpty()
                    dataMap["ownerName"] = user.displayName.orEmpty()
                    dataMap["ownerUid"] = user.uid

                    // Also sync to registered_pharmacists
                    try {
                        db.collection("registered_pharmacists").document(user.uid)
                            .set(
                                mapOf(
                                    "uid" to user.uid,
                                    "email" to user.email.orEmpty(),
                                    "displayName" to (user.displayName ?: user.email?.substringBefore("@") ?: "Staff Pharmacist"),
                                    "pharmacyName" to getPharmacyName(),
                                    "lga" to getPharmacyLga(),
                                    "state" to getPharmacyState()
                                ),
                                com.google.firebase.firestore.SetOptions.merge()
                            )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                docRef.set(dataMap, com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener {
                        android.util.Log.d("PharmacyViewModel", "Device configuration successfully merged/saved for $deviceId")
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e("PharmacyViewModel", "Error saving device config", e)
                    }

                // Restart snapshot listener of features under authenticated scope
                docRef.addSnapshotListener { snapshot, e ->
                    if (e != null) return@addSnapshotListener
                    if (snapshot != null && snapshot.exists()) {
                        val aiContent = snapshot.getBoolean("aiContentEnabled") ?: true
                        val carefluxAi = snapshot.getBoolean("carefluxAiEnabled") ?: true
                        val suspended = snapshot.getBoolean("isSuspended") ?: false
                        _isAiContentEnabled.value = aiContent
                        _isCarefluxAiEnabled.value = carefluxAi
                        _isSuspended.value = suspended
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearAllData() = viewModelScope.launch {
        repository.clearAllData()
    }

    // --- Termii API Management ---
    fun getTermiiApiKey(): String {
        return prefs.getString("custom_termii_api_key", null)?.takeIf { it.isNotBlank() }
            ?: com.example.BuildConfig.TERMII_API_KEY
    }

    fun setTermiiApiKey(key: String) {
        prefs.edit().putString("custom_termii_api_key", key.trim()).apply()
    }

    fun getTermiiSenderId(): String {
        return prefs.getString("custom_termii_sender_id", null)?.takeIf { it.isNotBlank() }
            ?: "N-Alert"
    }

    fun setTermiiSenderId(senderId: String) {
        prefs.edit().putString("custom_termii_sender_id", senderId.trim()).apply()
    }

    suspend fun sendTermiiSms(to: String, smsContent: String): Boolean {
        var cleanPhone = to.trim().replace("[^0-9]".toRegex(), "")
        if (cleanPhone.startsWith("0") && cleanPhone.length == 11) {
            cleanPhone = "234" + cleanPhone.substring(1)
        } else if (!cleanPhone.startsWith("234") && cleanPhone.length == 10) {
            cleanPhone = "234" + cleanPhone
        }
        if (cleanPhone.isBlank()) {
            cleanPhone = to
        }

        val apiKey = getTermiiApiKey()
        val senderId = getTermiiSenderId()

        if (apiKey.isBlank() || apiKey == "YOUR_TERMII_API_KEY" || apiKey == "TERMII_API_KEY_DEFAULT_VALUE") {
            android.util.Log.w("PharmacyViewModel", "Termii API Key is not configured or using default placeholder.")
            val errorLog = com.example.data.OutboundSmsLog(
                recipientPhone = cleanPhone,
                messageContent = smsContent,
                deliveryStatus = "Failed",
                gatewayUsed = "Termii API",
                errorMessage = "Termii API credentials missing or unconfigured"
            )
            val logId = repository.insertSmsLog(errorLog)
            _lastFailedSmsLog.value = errorLog.copy(id = logId.toInt())
            return false
        }

        return try {
            val request = com.example.data.TermiiSmsRequest(
                to = cleanPhone,
                from = senderId,
                sms = smsContent,
                apiKey = apiKey
            )
            val response = com.example.data.TermiiRetrofitClient.service.sendSms(request)
            val isSuccess = response.code == "ok" || 
                            response.code == "200" || 
                            response.message?.contains("Successfully Sent", ignoreCase = true) == true || 
                            !response.messageId.isNullOrBlank() || 
                            !response.messageIdStr.isNullOrBlank()
            
            val status = if (isSuccess) "Delivered" else "Failed"
            val errorMsg = if (isSuccess) null else (response.message ?: "Response error code: ${response.code}")
            
            val smsLog = com.example.data.OutboundSmsLog(
                recipientPhone = cleanPhone,
                messageContent = smsContent,
                deliveryStatus = status,
                gatewayUsed = "Termii API",
                errorMessage = errorMsg
            )
            val logId = repository.insertSmsLog(smsLog)
            
            if (!isSuccess) {
                _lastFailedSmsLog.value = smsLog.copy(id = logId.toInt())
            }
            
            android.util.Log.d("PharmacyViewModel", "Termii SMS Sent to $cleanPhone. Success: $isSuccess. Code: ${response.code}. Message: ${response.message}")
            isSuccess
        } catch (e: Exception) {
            e.printStackTrace()
            val smsLog = com.example.data.OutboundSmsLog(
                recipientPhone = cleanPhone,
                messageContent = smsContent,
                deliveryStatus = "Failed",
                gatewayUsed = "Termii API",
                errorMessage = e.message ?: e.toString()
            )
            val logId = repository.insertSmsLog(smsLog)
            _lastFailedSmsLog.value = smsLog.copy(id = logId.toInt())
            false
        }
    }

    // --- Option 1: Automated / Direct Refill Reminders via Termii ---
    suspend fun sendTermiiRefillReminderSms(patientName: String, phone: String, medicationName: String, dateStr: String, cost: Double): Boolean {
        val formattedCost = "%,.2f".format(cost)
        val message = "Careflux Refill Reminder:\nHello $patientName, your medication $medicationName is due for refill on $dateStr (Est. Cost: ₦$formattedCost). Stay consistent with your therapy! Reply or visit Careflux to refill."
        return sendTermiiSms(phone, message)
    }

    // --- Option 2: Patient Welfare Check-up / Clinical Follow-up via Termii ---
    suspend fun sendTermiiWelfareCheckSms(patientName: String, phone: String, wellnessQuestion: String): Boolean {
        val message = "Careflux Health Follow-up:\nHello $patientName, this is Careflux Pharmacy checking up on your recovery! $wellnessQuestion We care about your health journey. Let us know if you need any adjustments."
        return sendTermiiSms(phone, message)
    }

    // --- Option 3: Dispensing Confirmation / Receipt Notice via Termii ---
    suspend fun sendTermiiDispenseConfirmationSms(patientName: String, phone: String, itemsSummary: String, amount: Double): Boolean {
        val formattedAmount = "%,.2f".format(amount)
        val message = "Careflux Transaction Alert:\nDear $patientName, your prescription ($itemsSummary) was successfully dispensed. Total: ₦$formattedAmount. Thank you for choosing Careflux Pharmacy!"
        return sendTermiiSms(phone, message)
    }

    fun insertCustomSmsLog(log: com.example.data.OutboundSmsLog) {
        viewModelScope.launch {
            repository.insertSmsLog(log)
        }
    }

    fun clearAllSmsLogs() {
        viewModelScope.launch {
            repository.clearSmsLogs()
        }
    }

    fun submitKeyRequest() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val requestDoc = db.collection("key_creation_requests").document(deviceId)
                val requestData = hashMapOf(
                    "deviceId" to deviceId,
                    "pharmacyName" to getPharmacyName(),
                    "lga" to getPharmacyLga(),
                    "state" to getPharmacyState(),
                    "requestedAt" to System.currentTimeMillis(),
                    "status" to "PENDING",
                    "geminiKey" to "",
                    "termiiApiKey" to "",
                    "termiiSenderId" to ""
                )
                requestDoc.set(requestData, com.google.firebase.firestore.SetOptions.merge())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun approveKeyRequest(targetDeviceId: String, gemini: String, termii: String, sender: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                
                // 1. Update request status
                db.collection("key_creation_requests").document(targetDeviceId)
                    .update(
                        "status", "APPROVED",
                        "geminiKey", gemini,
                        "termiiApiKey", termii,
                        "termiiSenderId", sender
                    )
                
                // 2. Provision custom keys to device config
                db.collection("device_configs").document(targetDeviceId)
                    .update(
                        "customGeminiApiKey", gemini,
                        "customTermiiApiKey", termii,
                        "customTermiiSenderId", sender
                    )
                
                // 3. Log to audit trail
                val logId = java.util.UUID.randomUUID().toString()
                db.collection("admin_audit_logs").document(logId).set(
                    hashMapOf(
                        "id" to logId,
                        "adminName" to "Administrator",
                        "action" to "APPROVE_KEYS",
                        "affectedNodeId" to targetDeviceId,
                        "affectedNodeModel" to "Personal Keys Issued",
                        "reason" to "Custom API Keys request approved and provisioned.",
                        "timestamp" to System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun rejectKeyRequest(targetDeviceId: String, rationale: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                db.collection("key_creation_requests").document(targetDeviceId)
                    .update("status", "REJECTED")
                
                // Log to audit trail
                val logId = java.util.UUID.randomUUID().toString()
                db.collection("admin_audit_logs").document(logId).set(
                    hashMapOf(
                        "id" to logId,
                        "adminName" to "Administrator",
                        "action" to "REJECT_KEYS",
                        "affectedNodeId" to targetDeviceId,
                        "affectedNodeModel" to "Personal Keys Denied",
                        "reason" to "Keys request rejected: $rationale",
                        "timestamp" to System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun seedSimulationNodesAndSales() {
        viewModelScope.launch {
            try {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                
                // 1. Seed 3 Simulated Cooperative Node Pharmacies
                val nodes = listOf(
                    mapOf(
                        "uid" to "sim_node_1",
                        "deviceId" to "sim_device_1",
                        "displayName" to "CitiCare Kosofe Pharmacy",
                        "pharmacyName" to "CitiCare Kosofe Pharmacy",
                        "deviceModel" to "Infinix Note 30",
                        "lga" to "Kosofe",
                        "state" to "Lagos",
                        "registeredAt" to System.currentTimeMillis()
                    ),
                    mapOf(
                        "uid" to "sim_node_2",
                        "deviceId" to "sim_device_2",
                        "displayName" to "Careflux Ikeja Central",
                        "pharmacyName" to "Careflux Ikeja Central",
                        "deviceModel" to "TECNO Camon 20",
                        "lga" to "Ikeja",
                        "state" to "Lagos",
                        "registeredAt" to System.currentTimeMillis()
                    ),
                    mapOf(
                        "uid" to "sim_node_3",
                        "deviceId" to "sim_device_3",
                        "displayName" to "Surulere Co-op Health",
                        "pharmacyName" to "Surulere Co-op Health",
                        "deviceModel" to "Xiaomi Redmi Note 12",
                        "lga" to "Surulere",
                        "state" to "Lagos",
                        "registeredAt" to System.currentTimeMillis()
                    )
                )

                for (node in nodes) {
                    db.collection("registered_pharmacists")
                        .document(node["uid"].toString())
                        .set(node)
                }

                // 2. Seed Related Medication Sales to showcase demand probability matching
                val sales = listOf(
                    // Sales for Ikeja node (high SKU demand for 'Coartem' / Antimalarials)
                    mapOf(
                        "productName" to "Coartem 80/480",
                        "brand" to "Novartis",
                        "genericName" to "Artemether/Lumefantrine",
                        "category" to "Antimalarials",
                        "quantitySold" to 45,
                        "dateSold" to System.currentTimeMillis() - 86400000L * 3, // 3 days ago
                        "pharmacyNode" to "TECNO Camon 20", // Matches Node 2
                        "patientState" to "Lagos",
                        "patientLga" to "Ikeja",
                        "salePrice" to 112500.0
                    ),
                    // Sales for Kosofe node (moderate class affinity for 'Antimalarials' in Kosofe)
                    mapOf(
                        "productName" to "Amatem Softgel",
                        "brand" to "Elbe",
                        "genericName" to "Artemether/Lumefantrine",
                        "category" to "Antimalarials",
                        "quantitySold" to 22,
                        "dateSold" to System.currentTimeMillis() - 86400000L * 5,
                        "pharmacyNode" to "Infinix Note 30", // Matches Node 1
                        "patientState" to "Lagos",
                        "patientLga" to "Kosofe",
                        "salePrice" to 44000.0
                    )
                )

                for (sale in sales) {
                    db.collection("medication_sales")
                        .add(sale)
                }

                android.util.Log.d("PharmacyViewModel", "Simulated cooperative nodes and sales successfully seeded!")

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- Branch Realtime Synchronization, Security & Auditing Logic ---
    
    fun handleUserAuthenticated(user: com.google.firebase.auth.FirebaseUser) {
        try {
            userProfileListener?.remove()
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            userProfileListener = db.collection("registered_pharmacists").document(user.uid)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        android.util.Log.e("PharmacyViewModel", "Error fetching user details", e)
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        val bId = snapshot.getString("branchId") ?: ""
                        val bName = snapshot.getString("branchName") ?: "Careflux Rx"
                        val role = snapshot.getString("role") ?: "Pharmacist"
                        val displayName = snapshot.getString("displayName") ?: user.displayName ?: "Staff Pharmacist"
                        val phoneNumber = snapshot.getString("phoneNumber") ?: user.phoneNumber ?: "+2348000000000"
                        
                        _currentPharmacistBranchId.value = bId
                        _currentPharmacistBranchName.value = bName
                        _currentPharmacistRole.value = role
                        _currentPharmacistName.value = displayName
                        _currentPharmacistPhone.value = phoneNumber
                        
                        if (bId.isNotEmpty()) {
                            setupBranchRealtimeSync(bId)
                            triggerImmediateSync()
                        }
                    } else if (snapshot != null && !snapshot.exists()) {
                        // Create default registered_pharmacist profile document if missing (e.g. for Google Sign-In or new users)
                        val devId = deviceId
                        val deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
                        val defaultMap = hashMapOf(
                            "uid" to user.uid,
                            "email" to user.email.orEmpty(),
                            "displayName" to (user.displayName ?: user.email?.substringBefore("@") ?: "Staff Pharmacist"),
                            "deviceId" to devId,
                            "deviceModel" to deviceModel,
                            "phoneNumber" to (user.phoneNumber ?: "+2348000000000"),
                            "registeredAt" to System.currentTimeMillis(),
                            "lastLoginAt" to System.currentTimeMillis(),
                            "branchId" to "",
                            "branchName" to "",
                            "role" to "Pharmacist",
                            "isApproved" to true
                        )
                        db.collection("registered_pharmacists").document(user.uid).set(defaultMap)
                            .addOnSuccessListener {
                                android.util.Log.d("PharmacyViewModel", "Default pharmacist profile created successfully for ${user.uid}")
                            }
                            .addOnFailureListener { err ->
                                android.util.Log.e("PharmacyViewModel", "Failed to create default pharmacist profile", err)
                            }
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupBranchRealtimeSync(userBranchId: String) {
        try {
            // Run self-healing local deduplication immediately on sync setup
            deduplicateLocalInventory()

            // Step 1: Remove existing listen channels to prevent leakages
            activeSyncListeners.forEach { it.remove() }
            activeSyncListeners.clear()
            
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            
            // Listener 1: Sync staff members in the same branch in realtime (for Manager view)
        val staffListener = db.collection("registered_pharmacists")
            .whereEqualTo("branchId", userBranchId)
            .addSnapshotListener { snapshot, e ->
                if (e == null && snapshot != null) {
                    val list = snapshot.documents.map { doc ->
                        (doc.data ?: emptyMap()).toMutableMap().apply {
                            this["uid"] = doc.id
                        }
                    }
                    _branchStaffList.value = list
                }
            }
        activeSyncListeners.add(staffListener)

        // Listener 2: Real-time Branch Stock Coordination
        val invListener = db.collection("branch_inventory")
            .whereEqualTo("branchId", userBranchId)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                viewModelScope.launch {
                    try {
                        // Safely purge any old corrupt document ID ending with _0 in Firestore
                        snapshot.documents.forEach { doc ->
                            if (doc.id.endsWith("_0") || (doc.data?.get("id") as? Number)?.toInt() == 0) {
                                deleteEntityFromFirestore("branch_inventory", "0")
                            }
                        }

                        val remoteItems = snapshot.documents.mapNotNull { doc ->
                            val data = doc.data ?: return@mapNotNull null
                            val id = (data["id"] as? Number)?.toInt() ?: return@mapNotNull null
                            if (id == 0) return@mapNotNull null // Ignore corrupt ID 0 items

                            val name = data["name"] as? String ?: ""
                            val dosage = data["dosage"] as? String ?: ""
                            val stockQuantity = (data["stockQuantity"] as? Number)?.toInt() ?: 0
                            val minRequiredStock = (data["minRequiredStock"] as? Number)?.toInt() ?: 0
                            val category = data["category"] as? String ?: ""
                            val price = (data["price"] as? Number)?.toDouble() ?: 0.0
                            val expiryDate = (data["expiryDate"] as? Number)?.toLong() ?: 0L
                            val batchNumber = data["batchNumber"] as? String ?: ""
                            val supplier = data["supplier"] as? String ?: ""
                            val unitForm = data["unitForm"] as? String ?: ""
                            val lastSoldDate = (data["lastSoldDate"] as? Number)?.toLong() ?: 0L
                            val totalSoldQuantity = (data["totalSoldQuantity"] as? Number)?.toInt() ?: 0
                            val imageUri = data["imageUri"] as? String
                            val brand = data["brand"] as? String ?: ""
                            val salesStrategy = data["salesStrategy"] as? String ?: ""
                            val lastUpdated = (data["lastUpdated"] as? Number)?.toLong() ?: System.currentTimeMillis()
                            
                            com.example.data.InventoryItem(
                                id = id,
                                name = name,
                                dosage = dosage,
                                stockQuantity = stockQuantity,
                                minRequiredStock = minRequiredStock,
                                category = category,
                                price = price,
                                expiryDate = expiryDate,
                                batchNumber = batchNumber,
                                supplier = supplier,
                                unitForm = unitForm,
                                lastSoldDate = lastSoldDate,
                                totalSoldQuantity = totalSoldQuantity,
                                imageUri = imageUri,
                                brand = brand,
                                salesStrategy = salesStrategy,
                                lastUpdated = lastUpdated
                            )
                        }
                        
                        var needsDeduplicate = false
                        remoteItems.forEach { remote ->
                            val local = repository.getInventoryItemById(remote.id)
                            if (local == null || remote.lastUpdated >= local.lastUpdated) {
                                repository.insertInventoryItem(remote)
                                if (local == null) {
                                    needsDeduplicate = true
                                }
                            }
                        }
                        if (needsDeduplicate) {
                            deduplicateLocalInventory()
                        }
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                }
            }
        activeSyncListeners.add(invListener)

        // Listener 3: Realtime Branch Customers Sync
        val custListener = db.collection("branch_customers")
            .whereEqualTo("branchId", userBranchId)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                viewModelScope.launch {
                    try {
                        val remoteList = snapshot.documents.mapNotNull { doc ->
                            val data = doc.data ?: return@mapNotNull null
                            val id = (data["id"] as? Number)?.toInt() ?: return@mapNotNull null
                            val name = data["name"] as? String ?: ""
                            val phoneNumber = data["phoneNumber"] as? String ?: ""
                            val email = data["email"] as? String ?: ""
                            val notes = data["notes"] as? String ?: ""
                            val loyaltyPoints = (data["loyaltyPoints"] as? Number)?.toInt() ?: 0
                            val refillStreak = (data["refillStreak"] as? Number)?.toInt() ?: 0
                            val dateAdded = (data["dateAdded"] as? Number)?.toLong() ?: System.currentTimeMillis()
                            val age = (data["age"] as? Number)?.toInt() ?: 30
                            val gender = data["gender"] as? String ?: "Male"
                            val state = data["state"] as? String ?: "Lagos"
                            val lga = data["lga"] as? String ?: "Ikeja"
                            val city = data["city"] as? String ?: "Ikeja"
                            
                            com.example.data.Customer(
                                id = id,
                                name = name,
                                phoneNumber = phoneNumber,
                                email = email,
                                notes = notes,
                                loyaltyPoints = loyaltyPoints,
                                refillStreak = refillStreak,
                                dateAdded = dateAdded,
                                age = age,
                                gender = gender,
                                state = state,
                                lga = lga,
                                city = city
                            )
                        }
                        remoteList.forEach { remote ->
                            val local = repository.getCustomerById(remote.id)
                            if (local == null || remote.dateAdded >= local.dateAdded) {
                                repository.insertCustomer(remote)
                            }
                        }
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                }
            }
        activeSyncListeners.add(custListener)

        // Listener 3b: Realtime Branch Customer Medications Sync
        val medListener = db.collection("branch_customer_medications")
            .whereEqualTo("branchId", userBranchId)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                viewModelScope.launch {
                    try {
                        snapshot.documents.forEach { doc ->
                            val data = doc.data ?: return@forEach
                            val id = (data["id"] as? Number)?.toInt() ?: return@forEach
                            val customerId = (data["customerId"] as? Number)?.toInt() ?: return@forEach
                            val inventoryItemId = (data["inventoryItemId"] as? Number)?.toInt() ?: 0
                            val medicationName = data["medicationName"] as? String ?: ""
                            val customDosage = data["customDosage"] as? String ?: ""
                            val cost = (data["cost"] as? Number)?.toDouble() ?: 0.0
                            val cycleDays = (data["cycleDays"] as? Number)?.toInt() ?: 30
                            val nextRefillDate = (data["nextRefillDate"] as? Number)?.toLong() ?: System.currentTimeMillis()

                            repository.insertCustomerMedication(
                                com.example.data.CustomerMedication(
                                    id = id,
                                    customerId = customerId,
                                    inventoryItemId = inventoryItemId,
                                    medicationName = medicationName,
                                    customDosage = customDosage,
                                    cost = cost,
                                    cycleDays = cycleDays,
                                    nextRefillDate = nextRefillDate
                                )
                            )
                        }
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                }
            }
        activeSyncListeners.add(medListener)

        // Listener 3c: Realtime Branch Clinical Interventions Sync
        val interventionListener = db.collection("branch_interventions")
            .whereEqualTo("branchId", userBranchId)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                viewModelScope.launch {
                    try {
                        snapshot.documents.forEach { doc ->
                            val data = doc.data ?: return@forEach
                            val id = (data["id"] as? Number)?.toInt() ?: return@forEach
                            val customerId = (data["customerId"] as? Number)?.toInt() ?: return@forEach
                            val presentation = data["presentation"] as? String ?: ""
                            val testResults = data["testResults"] as? String ?: ""
                            val recommendation = data["recommendation"] as? String ?: ""
                            val currentStatus = data["currentStatus"] as? String ?: "Pending"
                            val followUpDay3Sent = data["followUpDay3Sent"] as? Boolean ?: false
                            val followUpDay7Sent = data["followUpDay7Sent"] as? Boolean ?: false
                            val followUpDay14Sent = data["followUpDay14Sent"] as? Boolean ?: false
                            val dateAdded = (data["dateAdded"] as? Number)?.toLong() ?: System.currentTimeMillis()

                            repository.insertClinicalIntervention(
                                com.example.data.ClinicalIntervention(
                                    id = id,
                                    customerId = customerId,
                                    presentation = presentation,
                                    testResults = testResults,
                                    recommendation = recommendation,
                                    currentStatus = currentStatus,
                                    followUpDay3Sent = followUpDay3Sent,
                                    followUpDay7Sent = followUpDay7Sent,
                                    followUpDay14Sent = followUpDay14Sent,
                                    dateAdded = dateAdded
                                )
                            )
                        }
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                }
            }
        activeSyncListeners.add(interventionListener)

        // Listener 4: Realtime Branch Operations Tasks
        val taskListener = db.collection("branch_operation_tasks")
            .whereEqualTo("branchId", userBranchId)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                viewModelScope.launch {
                    try {
                        val remoteList = snapshot.documents.mapNotNull { doc ->
                            val data = doc.data ?: return@mapNotNull null
                            val id = (data["id"] as? Number)?.toInt() ?: return@mapNotNull null
                            val title = data["title"] as? String ?: ""
                            val description = data["description"] as? String ?: ""
                            val urgency = data["urgency"] as? String ?: "Medium"
                            val category = data["category"] as? String ?: "Manual"
                            val isCompleted = data["isCompleted"] as? Boolean ?: false
                            val createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                            
                            val verifiedBy = data["verifiedBy"] as? String
                            val verificationNotes = data["verificationNotes"] as? String
                            val verificationChannel = data["verificationChannel"] as? String
                            val verificationCustomerName = data["verificationCustomerName"] as? String
                            val verifiedAtNum = data["verifiedAt"] as? Number
                            val verifiedAt = if (verifiedAtNum != null && verifiedAtNum.toLong() != 0L) verifiedAtNum.toLong() else null
                            
                            val isApproved = data["isApproved"] as? Boolean ?: false
                            val approvedBy = data["approvedBy"] as? String
                            val approvedAtNum = data["approvedAt"] as? Number
                            val approvedAt = if (approvedAtNum != null && approvedAtNum.toLong() != 0L) approvedAtNum.toLong() else null
                            val approvalNotes = data["approvalNotes"] as? String
                            
                            val assignedToName = data["assignedToName"] as? String
                            val assignedToUid = data["assignedToUid"] as? String

                            com.example.data.OperationTask(
                                id = id,
                                title = title,
                                description = description,
                                urgency = urgency,
                                category = category,
                                isCompleted = isCompleted,
                                createdAt = createdAt,
                                verifiedBy = if (verifiedBy.isNullOrEmpty()) null else verifiedBy,
                                verificationNotes = if (verificationNotes.isNullOrEmpty()) null else verificationNotes,
                                verificationChannel = if (verificationChannel.isNullOrEmpty()) null else verificationChannel,
                                verificationCustomerName = if (verificationCustomerName.isNullOrEmpty()) null else verificationCustomerName,
                                verifiedAt = verifiedAt,
                                isApproved = isApproved,
                                approvedBy = if (approvedBy.isNullOrEmpty()) null else approvedBy,
                                approvedAt = approvedAt,
                                approvalNotes = if (approvalNotes.isNullOrEmpty()) null else approvalNotes,
                                assignedToName = if (assignedToName.isNullOrEmpty()) null else assignedToName,
                                assignedToUid = if (assignedToUid.isNullOrEmpty()) null else assignedToUid
                            )
                        }
                        
                        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                        val isManager = _currentPharmacistRole.value == "Branch Manager"

                        remoteList.forEach { remote ->
                            val local = repository.getOperationTaskById(remote.id)
                            
                            if (isFirstTaskSyncDone) {
                                // 1. Notify pharmacist if task is assigned to them
                                if (remote.assignedToUid == currentUid && remote.assignedToUid?.isNotEmpty() == true) {
                                    if (local == null) {
                                        showLocalNotification(
                                            title = "New Task Assigned",
                                            content = "You have been assigned a new task: ${remote.title}.",
                                            targetTab = "ai_tasks"
                                        )
                                    } else if (local.assignedToUid != remote.assignedToUid) {
                                        showLocalNotification(
                                            title = "Task Reassigned to You",
                                            content = "Task '${remote.title}' is now assigned to you.",
                                            targetTab = "ai_tasks"
                                        )
                                    }
                                }
                                
                                // 2. Notify manager if a task is completed
                                if (isManager) {
                                    if (remote.isCompleted && (local == null || !local.isCompleted)) {
                                        showLocalNotification(
                                            title = "Task Completed by Staff",
                                            content = "Task '${remote.title}' has been marked completed by ${remote.verifiedBy ?: "staff"}.",
                                            targetTab = "branch_team"
                                        )
                                    }
                                }

                                // 3. Notify manager/staff about new incoming Stock Transfer or unassigned manager task
                                if (local == null) {
                                    if (remote.category == "Stock Transfer") {
                                        showLocalNotification(
                                            title = "Incoming Stock Transfer",
                                            content = remote.description,
                                            targetTab = "branch_team"
                                        )
                                    } else if (isManager && remote.assignedToName == "Branch Manager" && remote.assignedToUid.isNullOrEmpty()) {
                                        showLocalNotification(
                                            title = "New Manager Task",
                                            content = "A new task requires your attention: ${remote.title}.",
                                            targetTab = "branch_team"
                                        )
                                    }
                                }
                            }
                            
                            repository.insertOperationTask(remote)
                        }
                        isFirstTaskSyncDone = true
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                }
            }
        activeSyncListeners.add(taskListener)

        // Listener 5: Realtime Branch Receipts Sync
        val receiptListener = db.collection("branch_receipts")
            .whereEqualTo("branchId", userBranchId)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                viewModelScope.launch {
                    try {
                        snapshot.documents.forEach { doc ->
                            val data = doc.data ?: return@forEach
                            val id = (data["id"] as? Number)?.toInt() ?: return@forEach
                            val timestamp = (data["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
                            val customerName = data["customerName"] as? String ?: ""
                            val totalAmount = (data["totalAmount"] as? Number)?.toDouble() ?: 0.0
                            val imageFileName = data["imageFileName"] as? String ?: ""
                            val isInvoice = data["isInvoice"] as? Boolean ?: false
                            val paymentStatus = data["paymentStatus"] as? String ?: "Paid"
                            val orderId = data["orderId"] as? String ?: ""
                            
                            repository.insertReceipt(
                                com.example.data.Receipt(
                                    id = id,
                                    timestamp = timestamp,
                                    customerName = customerName,
                                    totalAmount = totalAmount,
                                    imageFileName = imageFileName,
                                    isInvoice = isInvoice,
                                    paymentStatus = paymentStatus,
                                    orderId = orderId
                                )
                            )
                        }
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                }
            }
        activeSyncListeners.add(receiptListener)

        // Listener 6: Realtime Branch Audit Logs Sync (for transfers ledger)
        val auditListener = db.collection("branch_audit_logs")
            .whereEqualTo("branchId", userBranchId)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                val logs = snapshot.documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    val act = data["action"] as? String ?: ""
                    if (act == "TRANSFER" || act == "BULK_TRANSFER" || act == "STOCK_ADJUSTMENT") {
                        val mutableMap = data.toMutableMap()
                        mutableMap["id"] = doc.id
                        mutableMap
                    } else {
                        null
                    }
                }.sortedByDescending { (it["timestamp"] as? Number)?.toLong() ?: 0L }
                _branchTransfers.value = logs
            }
        activeSyncListeners.add(auditListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun syncEntityToFirestore(collectionName: String, docId: String, dataMap: Map<String, Any?>) {
        val branchId = _currentPharmacistBranchId.value ?: return
        _syncState.value = SyncState.Syncing
        viewModelScope.launch {
            try {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val mutableMap = dataMap.toMutableMap()
                mutableMap["branchId"] = branchId
                mutableMap["syncedAt"] = System.currentTimeMillis()
                db.collection(collectionName).document("${branchId}_$docId").set(mutableMap)
                    .addOnSuccessListener {
                        _syncState.value = SyncState.Synced
                        _lastSyncedTime.value = System.currentTimeMillis()
                    }
                    .addOnFailureListener { e ->
                        _syncState.value = SyncState.Error(e.localizedMessage ?: "Sync Write Blocked")
                    }
            } catch (e: Exception) {
                _syncState.value = SyncState.Error(e.localizedMessage ?: "Connection Timeout")
                e.printStackTrace()
            }
        }
    }

    private fun syncCustomerToBranch(customer: com.example.data.Customer) {
        val map = hashMapOf(
            "id" to customer.id,
            "name" to customer.name,
            "phoneNumber" to customer.phoneNumber,
            "email" to customer.email,
            "notes" to customer.notes,
            "loyaltyPoints" to customer.loyaltyPoints,
            "refillStreak" to customer.refillStreak,
            "dateAdded" to customer.dateAdded,
            "age" to customer.age,
            "gender" to customer.gender,
            "state" to customer.state,
            "lga" to customer.lga,
            "city" to customer.city,
            "consentPrescriptionTracking" to customer.consentPrescriptionTracking,
            "consentSmsRefills" to customer.consentSmsRefills,
            "consentCloudSync" to customer.consentCloudSync,
            "consentLastUpdated" to customer.consentLastUpdated,
            "consentChannel" to customer.consentChannel
        )
        syncEntityToFirestore("branch_customers", customer.id.toString(), map)
    }

    private fun syncCustomerMedicationToBranch(med: com.example.data.CustomerMedication) {
        val map = hashMapOf(
            "id" to med.id,
            "customerId" to med.customerId,
            "inventoryItemId" to med.inventoryItemId,
            "medicationName" to med.medicationName,
            "customDosage" to med.customDosage,
            "cost" to med.cost,
            "cycleDays" to med.cycleDays,
            "nextRefillDate" to med.nextRefillDate
        )
        syncEntityToFirestore("branch_customer_medications", med.id.toString(), map)
    }

    private fun syncClinicalInterventionToBranch(inter: com.example.data.ClinicalIntervention) {
        val map = hashMapOf(
            "id" to inter.id,
            "customerId" to inter.customerId,
            "presentation" to inter.presentation,
            "testResults" to inter.testResults,
            "recommendation" to inter.recommendation,
            "currentStatus" to inter.currentStatus,
            "followUpDay3Sent" to inter.followUpDay3Sent,
            "followUpDay7Sent" to inter.followUpDay7Sent,
            "followUpDay14Sent" to inter.followUpDay14Sent,
            "dateAdded" to inter.dateAdded
        )
        syncEntityToFirestore("branch_interventions", inter.id.toString(), map)
    }

    fun deleteEntityFromFirestore(collectionName: String, docId: String) {
        val branchId = _currentPharmacistBranchId.value ?: return
        viewModelScope.launch {
            try {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                db.collection(collectionName).document("${branchId}_$docId").delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun logAuditTrail(action: String, details: String, affectedId: String = "") {
        val branchId = _currentPharmacistBranchId.value ?: "Self"
        val userName = _currentPharmacistName.value ?: "Staff Pharmacist"
        val userRole = _currentPharmacistRole.value ?: "Pharmacist"
        val userUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "LocalNode"
        
        viewModelScope.launch {
            try {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val auditMap = hashMapOf(
                    "branchId" to branchId,
                    "uid" to userUid,
                    "displayName" to userName,
                    "role" to userRole,
                    "action" to action,
                    "timestamp" to System.currentTimeMillis(),
                    "details" to details,
                    "affectedId" to affectedId
                )
                db.collection("branch_audit_logs").add(auditMap)
                
                repository.insertAdminAuditLog(
                    com.example.data.AdminAuditLog(
                        adminName = "$userName ($userRole)",
                        actionPerformed = action,
                        timestamp = System.currentTimeMillis(),
                        affectedNodeId = affectedId,
                        affectedNodeModel = "Branch: ${_currentPharmacistBranchName.value ?: "Careflux"}",
                        reason = details
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- Branch Stock Control & Compliance Core Actions ---

    fun performBranchTransfer(item: com.example.data.InventoryItem, quantity: Int, destinationBranch: String, reason: String) {
        viewModelScope.launch {
            if (item.stockQuantity < quantity) {
                android.widget.Toast.makeText(getApplication(), "Transfer Failed: Insufficient stock available", android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }
            val newQty = item.stockQuantity - quantity
            val updated = item.copy(stockQuantity = newQty, lastUpdated = System.currentTimeMillis())
            repository.insertInventoryItem(updated)
            
            val map = mapOf(
                "id" to updated.id,
                "name" to updated.name,
                "dosage" to updated.dosage,
                "stockQuantity" to updated.stockQuantity,
                "minRequiredStock" to updated.minRequiredStock,
                "category" to updated.category,
                "price" to updated.price,
                "expiryDate" to updated.expiryDate,
                "batchNumber" to updated.batchNumber,
                "supplier" to updated.supplier,
                "unitForm" to updated.unitForm,
                "lastSoldDate" to updated.lastSoldDate,
                "totalSoldQuantity" to updated.totalSoldQuantity,
                "brand" to updated.brand,
                "salesStrategy" to updated.salesStrategy,
                "lastUpdated" to updated.lastUpdated
            )
            syncEntityToFirestore("branch_inventory", updated.id.toString(), map)
            
            logAuditTrail(
                action = "TRANSFER",
                details = "Transferred $quantity units of ${item.name} (${item.dosage}) to branch: '$destinationBranch'. Reason: $reason",
                affectedId = item.id.toString()
            )

            // Automate double-ended Task insertion for destination branch to confirm and verify receipt
            try {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val transferTaskId = (100000..999999).random()
                val transferTaskMap = mapOf(
                    "id" to transferTaskId,
                    "title" to "INCOMING STOCK TRANSFER",
                    "description" to "ITEM: ${item.name} | DOSAGE: ${item.dosage} | QTY: $quantity | FROM: ${_currentPharmacistBranchName.value ?: "Source Branch"} | REASON: $reason",
                    "urgency" to "High",
                    "category" to "Stock Transfer",
                    "isCompleted" to false,
                    "createdAt" to System.currentTimeMillis(),
                    "branchId" to destinationBranch.trim(),
                    "assignedToName" to "Branch Manager",
                    "assignedToUid" to "",
                    "isApproved" to false,
                    "approvedBy" to "",
                    "approvedAt" to 0L,
                    "approvalNotes" to ""
                )
                db.collection("branch_operation_tasks").document(transferTaskId.toString()).set(transferTaskMap)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            android.widget.Toast.makeText(getApplication(), "Stock Transfer registered, logged, and transit task created", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun verifyAndReceiveStockTransfer(
        task: OperationTask,
        notes: String,
        onFinished: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val updaterName = _currentPharmacistName.value ?: "Staff Pharmacist"
            val descriptionText = task.description
            if (!descriptionText.contains("ITEM: ")) {
                onFinished(false, "Invalid stock transfer format")
                return@launch
            }
            
            val itemName = descriptionText.substringAfter("ITEM: ").substringBefore(" | DOSAGE: ").trim()
            val itemDosage = descriptionText.substringAfter("DOSAGE: ").substringBefore(" | QTY: ").trim()
            val itemQty = descriptionText.substringAfter("QTY: ").substringBefore(" | FROM: ").trim().toIntOrNull() ?: 0
            val fromBranch = descriptionText.substringAfter("FROM: ").substringBefore(" | REASON: ").trim()
            
            if (itemQty <= 0) {
                onFinished(false, "Invalid quantity received")
                return@launch
            }
            
            // Look up local stock
            val existing = repository.allInventoryItems.first().find { 
                it.name.equals(itemName, ignoreCase = true) && it.dosage.equals(itemDosage, ignoreCase = true)
            }
            
            val updatedQty = (existing?.stockQuantity ?: 0) + itemQty
            val updatedItem = if (existing != null) {
                existing.copy(stockQuantity = updatedQty, lastUpdated = System.currentTimeMillis())
            } else {
                com.example.data.InventoryItem(
                    id = (100000..999999).random(),
                    name = itemName,
                    dosage = itemDosage,
                    stockQuantity = itemQty,
                    minRequiredStock = 5,
                    category = "Transfer Received",
                    price = 0.0,
                    expiryDate = System.currentTimeMillis() + (365L * 24L * 60L * 60L * 1000L), // 1 year default
                    batchNumber = "TX-RECEIVED",
                    supplier = fromBranch,
                    lastUpdated = System.currentTimeMillis()
                )
            }
            
            repository.insertInventoryItem(updatedItem)
            
            // Sync inventory item to Firestore
            val itemMap = mapOf(
                "id" to updatedItem.id,
                "name" to updatedItem.name,
                "dosage" to updatedItem.dosage,
                "stockQuantity" to updatedItem.stockQuantity,
                "minRequiredStock" to updatedItem.minRequiredStock,
                "category" to updatedItem.category,
                "price" to updatedItem.price,
                "expiryDate" to updatedItem.expiryDate,
                "batchNumber" to updatedItem.batchNumber,
                "supplier" to updatedItem.supplier,
                "unitForm" to updatedItem.unitForm,
                "lastSoldDate" to updatedItem.lastSoldDate,
                "totalSoldQuantity" to updatedItem.totalSoldQuantity,
                "brand" to updatedItem.brand,
                "salesStrategy" to updatedItem.salesStrategy,
                "lastUpdated" to updatedItem.lastUpdated
            )
            val branchId = _currentPharmacistBranchId.value ?: ""
            syncEntityToFirestore("branch_inventory", updatedItem.id.toString(), itemMap)
            
            // Complete task
            val updatedTask = task.copy(
                isCompleted = true,
                isApproved = true,
                verifiedBy = updaterName,
                verifiedAt = System.currentTimeMillis(),
                verificationNotes = "Received and verified successfully. Notes: $notes",
                verificationChannel = "Transfer Sync",
                verificationCustomerName = "Source: $fromBranch",
                approvedBy = updaterName,
                approvedAt = System.currentTimeMillis(),
                approvalNotes = "Confirmed receipt of $itemQty units of $itemName."
            )
            repository.updateOperationTask(updatedTask)
            
            if (branchId.isNotEmpty()) {
                val taskMap = mapOf(
                    "id" to updatedTask.id,
                    "title" to updatedTask.title,
                    "description" to updatedTask.description,
                    "urgency" to updatedTask.urgency,
                    "category" to updatedTask.category,
                    "isCompleted" to updatedTask.isCompleted,
                    "createdAt" to updatedTask.createdAt,
                    "branchId" to branchId,
                    "assignedToName" to (updatedTask.assignedToName ?: ""),
                    "assignedToUid" to (updatedTask.assignedToUid ?: ""),
                    "verifiedBy" to (updatedTask.verifiedBy ?: ""),
                    "verificationNotes" to (updatedTask.verificationNotes ?: ""),
                    "verificationChannel" to (updatedTask.verificationChannel ?: ""),
                    "verificationCustomerName" to (updatedTask.verificationCustomerName ?: ""),
                    "verifiedAt" to (updatedTask.verifiedAt ?: 0L),
                    "isApproved" to updatedTask.isApproved,
                    "approvedBy" to (updatedTask.approvedBy ?: ""),
                    "approvedAt" to (updatedTask.approvedAt ?: 0L),
                    "approvalNotes" to (updatedTask.approvalNotes ?: "")
                )
                syncEntityToFirestore("branch_operation_tasks", updatedTask.id.toString(), taskMap)
            }
            
            logAuditTrail(
                action = "TRANSFER_RECEIVED",
                details = "Successfully verified and received stock transfer of $itemQty units of $itemName. Notes: $notes",
                affectedId = updatedItem.id.toString()
            )
            
            onFinished(true, "Successfully received stock and finalized transfer.")
        }
    }

    fun performBulkBranchTransfer(transfers: List<Pair<com.example.data.InventoryItem, Int>>, destinationBranch: String, reason: String) {
        viewModelScope.launch {
            var successCount = 0
            var failCount = 0
            val stringBuilder = StringBuilder()
            
            transfers.forEach { (item, quantity) ->
                if (item.stockQuantity < quantity) {
                    failCount++
                    stringBuilder.append("• ${item.name}: Insufficient stock\n")
                    return@forEach
                }
                
                val newQty = item.stockQuantity - quantity
                val updated = item.copy(stockQuantity = newQty, lastUpdated = System.currentTimeMillis())
                repository.insertInventoryItem(updated)
                
                val map = mapOf(
                    "id" to updated.id,
                    "name" to updated.name,
                    "dosage" to updated.dosage,
                    "stockQuantity" to updated.stockQuantity,
                    "minRequiredStock" to updated.minRequiredStock,
                    "category" to updated.category,
                    "price" to updated.price,
                    "expiryDate" to updated.expiryDate,
                    "batchNumber" to updated.batchNumber,
                    "supplier" to updated.supplier,
                    "unitForm" to updated.unitForm,
                    "lastSoldDate" to updated.lastSoldDate,
                    "totalSoldQuantity" to updated.totalSoldQuantity,
                    "brand" to updated.brand,
                    "salesStrategy" to updated.salesStrategy,
                    "lastUpdated" to updated.lastUpdated
                )
                syncEntityToFirestore("branch_inventory", updated.id.toString(), map)
                
                logAuditTrail(
                    action = "BULK_TRANSFER",
                    details = "Bulk Transferred $quantity units of ${item.name} (${item.dosage}) to branch: '$destinationBranch'. Reason: $reason",
                    affectedId = item.id.toString()
                )
                successCount++
            }
            
            val message = if (failCount == 0) {
                "Bulk transfer of $successCount items completed successfully!"
            } else {
                "Bulk transfer completed: $successCount successful, $failCount failed.\n$stringBuilder"
            }
            android.widget.Toast.makeText(getApplication(), message, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    fun performReturn(item: com.example.data.InventoryItem, quantity: Int, customerName: String, reason: String) {
        viewModelScope.launch {
            val newQty = item.stockQuantity + quantity
            val updated = item.copy(stockQuantity = newQty, lastUpdated = System.currentTimeMillis())
            repository.insertInventoryItem(updated)
            
            val map = mapOf(
                "id" to updated.id,
                "name" to updated.name,
                "dosage" to updated.dosage,
                "stockQuantity" to updated.stockQuantity,
                "minRequiredStock" to updated.minRequiredStock,
                "category" to updated.category,
                "price" to updated.price,
                "expiryDate" to updated.expiryDate,
                "batchNumber" to updated.batchNumber,
                "supplier" to updated.supplier,
                "unitForm" to updated.unitForm,
                "lastSoldDate" to updated.lastSoldDate,
                "totalSoldQuantity" to updated.totalSoldQuantity,
                "brand" to updated.brand,
                "salesStrategy" to updated.salesStrategy,
                "lastUpdated" to updated.lastUpdated
            )
            syncEntityToFirestore("branch_inventory", updated.id.toString(), map)
            
            logAuditTrail(
                action = "RETURN",
                details = "Customer '$customerName' returned $quantity units of ${item.name} (${item.dosage}). Reason: $reason. Restored to inventory.",
                affectedId = item.id.toString()
            )
            android.widget.Toast.makeText(getApplication(), "Product return logged. Stock restored.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun performExpiryWriteOff(item: com.example.data.InventoryItem, quantity: Int, reason: String) {
        viewModelScope.launch {
            if (item.stockQuantity < quantity) {
                android.widget.Toast.makeText(getApplication(), "Write-off Failed: Insufficient stock", android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }
            val newQty = item.stockQuantity - quantity
            val updated = item.copy(stockQuantity = newQty, lastUpdated = System.currentTimeMillis())
            repository.insertInventoryItem(updated)
            
            val map = mapOf(
                "id" to updated.id,
                "name" to updated.name,
                "dosage" to updated.dosage,
                "stockQuantity" to updated.stockQuantity,
                "minRequiredStock" to updated.minRequiredStock,
                "category" to updated.category,
                "price" to updated.price,
                "expiryDate" to updated.expiryDate,
                "batchNumber" to updated.batchNumber,
                "supplier" to updated.supplier,
                "unitForm" to updated.unitForm,
                "lastSoldDate" to updated.lastSoldDate,
                "totalSoldQuantity" to updated.totalSoldQuantity,
                "brand" to updated.brand,
                "salesStrategy" to updated.salesStrategy,
                "lastUpdated" to updated.lastUpdated
            )
            syncEntityToFirestore("branch_inventory", updated.id.toString(), map)
            
            logAuditTrail(
                action = "EXPIRY_WRITE_OFF",
                details = "Wrote-off $quantity units of ${item.name} (${item.dosage}) as expired/damaged. Reason: $reason.",
                affectedId = item.id.toString()
            )
            android.widget.Toast.makeText(getApplication(), "Expired inventory successfully written off & logged", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Role-Based Team Management - Allowed only for Branch Managers
    fun updateStaffRoleOrApproval(staffUid: String, newRole: String, isApproved: Boolean) {
        viewModelScope.launch {
            try {
                if (_currentPharmacistRole.value != "Branch Manager") {
                    android.widget.Toast.makeText(getApplication(), "Access Denied: Only Branch Managers can configure staff roles", android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                db.collection("registered_pharmacists").document(staffUid)
                    .update(
                        mapOf(
                            "role" to newRole,
                            "isApproved" to isApproved
                        )
                    ).addOnSuccessListener {
                        logAuditTrail("MANAGE_STAFF", "Configured credentials of staff Member uid: $staffUid. Role set to '$newRole', Active state: $isApproved")
                        android.widget.Toast.makeText(getApplication(), "Staff credentials configured successfully", android.widget.Toast.LENGTH_SHORT).show()
                    }.addOnFailureListener {
                        android.widget.Toast.makeText(getApplication(), "Failed to update staff credentials", android.widget.Toast.LENGTH_SHORT).show()
                    }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- Ad-hoc Profile Branch Enrollment and Setup ---
    fun joinBranch(branchCode: String, onFinished: (Boolean, String) -> Unit) {
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        val user = auth.currentUser
        if (user == null) {
            onFinished(false, "Authentication Error: No active user session.")
            return
        }
        val cleanCode = branchCode.trim().uppercase()
        if (cleanCode.isBlank()) {
            onFinished(false, "Please enter a non-empty Branch Code.")
            return
        }
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        db.collection("branches").document(cleanCode).get()
            .addOnCompleteListener { task ->
                if (task.isSuccessful && task.result != null && task.result.exists()) {
                    val branchName = task.result.getString("name") ?: "Careflux Pharmacy"
                    
                    db.collection("registered_pharmacists").document(user.uid).get()
                        .addOnCompleteListener { pTask ->
                            val existingData = if (pTask.isSuccessful && pTask.result != null && pTask.result.exists()) {
                                pTask.result.data ?: mapOf()
                            } else {
                                mapOf()
                            }
                            
                            val updateMap = hashMapOf<String, Any>()
                            updateMap.putAll(existingData)
                            
                            updateMap["uid"] = user.uid
                            updateMap["email"] = user.email.orEmpty()
                            if (updateMap["displayName"] == null || (updateMap["displayName"] as? String).isNullOrBlank()) {
                                updateMap["displayName"] = user.displayName ?: user.email?.substringBefore("@") ?: "Staff Pharmacist"
                            }
                            updateMap["deviceId"] = deviceId
                            updateMap["deviceModel"] = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
                            updateMap["branchId"] = cleanCode
                            updateMap["branchName"] = branchName
                            
                            if (updateMap["role"] == null) {
                                updateMap["role"] = "Pharmacist"
                            }
                            if (updateMap["isApproved"] == null) {
                                updateMap["isApproved"] = true
                            }
                            if (updateMap["phoneNumber"] == null || (updateMap["phoneNumber"] as? String).isNullOrBlank()) {
                                updateMap["phoneNumber"] = user.phoneNumber ?: "+2348000000000"
                            }
                            if (updateMap["registeredAt"] == null) {
                                updateMap["registeredAt"] = System.currentTimeMillis()
                            }
                            updateMap["lastLoginAt"] = System.currentTimeMillis()
                            
                            db.collection("registered_pharmacists").document(user.uid)
                                .set(updateMap)
                                .addOnSuccessListener {
                                    val finalRole = updateMap["role"] as? String ?: "Pharmacist"
                                    _currentPharmacistBranchId.value = cleanCode
                                    _currentPharmacistBranchName.value = branchName
                                    _currentPharmacistRole.value = finalRole
                                    setupBranchRealtimeSync(cleanCode)
                                    onFinished(true, "Successfully joined branch: $branchName ($cleanCode).")
                                    logAuditTrail("JOIN_BRANCH", "User joined branch '$branchName' with code $cleanCode.")
                                }
                                .addOnFailureListener { e ->
                                    onFinished(false, "Failed to update profile: ${e.localizedMessage}")
                                }
                        }
                } else {
                    onFinished(false, "Branch with Code '$cleanCode' does not exist in our database.")
                }
            }
    }

    fun registerBranch(name: String, lga: String, state: String, onFinished: (Boolean, String) -> Unit) {
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        val user = auth.currentUser
        if (user == null) {
            onFinished(false, "Authentication Error: No active user session.")
            return
        }
        val cleanName = name.trim()
        val cleanLga = lga.trim().ifBlank { "Ikeja" }
        val cleanState = state.trim().ifBlank { "Lagos" }
        
        if (cleanName.isBlank()) {
            onFinished(false, "Please enter a non-empty Branch Name.")
            return
        }
        
        val randomCode = "CF-" + (100000..999999).random().toString()
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        
        val branchMap = mapOf(
            "id" to randomCode,
            "name" to cleanName,
            "lga" to cleanLga,
            "state" to cleanState,
            "createdBy" to user.uid,
            "createdAt" to System.currentTimeMillis()
        )
        
        db.collection("branches").document(randomCode).set(branchMap)
            .addOnSuccessListener {
                db.collection("registered_pharmacists").document(user.uid).get()
                    .addOnCompleteListener { pTask ->
                        val existingData = if (pTask.isSuccessful && pTask.result != null && pTask.result.exists()) {
                            pTask.result.data ?: mapOf()
                        } else {
                            mapOf()
                        }
                        
                        val updateMap = hashMapOf<String, Any>()
                        updateMap.putAll(existingData)
                        
                        updateMap["uid"] = user.uid
                        updateMap["email"] = user.email.orEmpty()
                        if (updateMap["displayName"] == null || (updateMap["displayName"] as? String).isNullOrBlank()) {
                            updateMap["displayName"] = user.displayName ?: user.email?.substringBefore("@") ?: "Staff Pharmacist"
                        }
                        updateMap["deviceId"] = deviceId
                        updateMap["deviceModel"] = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
                        updateMap["branchId"] = randomCode
                        updateMap["branchName"] = cleanName
                        updateMap["role"] = "Branch Manager"
                        updateMap["isApproved"] = true
                        
                        if (updateMap["phoneNumber"] == null || (updateMap["phoneNumber"] as? String).isNullOrBlank()) {
                            updateMap["phoneNumber"] = user.phoneNumber ?: "+2348000000000"
                        }
                        if (updateMap["registeredAt"] == null) {
                            updateMap["registeredAt"] = System.currentTimeMillis()
                        }
                        updateMap["lastLoginAt"] = System.currentTimeMillis()
                        
                        db.collection("registered_pharmacists").document(user.uid)
                            .set(updateMap)
                            .addOnSuccessListener {
                                _currentPharmacistBranchId.value = randomCode
                                _currentPharmacistBranchName.value = cleanName
                                _currentPharmacistRole.value = "Branch Manager"
                                setupBranchRealtimeSync(randomCode)
                                onFinished(true, "Branch registered successfully! Store Code: $randomCode")
                                logAuditTrail("CREATE_BRANCH", "Created branch '$cleanName' and self-assigned as Branch Manager under $randomCode")
                            }
                            .addOnFailureListener { e ->
                                onFinished(false, "Branch created successfully ($randomCode) but failed to link profile: ${e.localizedMessage}")
                            }
                    }
            }
            .addOnFailureListener { e ->
                onFinished(false, "Failed to register branch: ${e.localizedMessage}")
            }
    }

    fun deleteBranch(branchId: String, onFinished: (Boolean, String) -> Unit) {
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        val user = auth.currentUser
        if (user == null) {
            onFinished(false, "Authentication required.")
            return
        }
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        db.collection("branches").document(branchId).delete()
            .addOnSuccessListener {
                db.collection("registered_pharmacists")
                    .whereEqualTo("branchId", branchId)
                    .get()
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful && task.result != null) {
                            val batch = db.batch()
                            for (doc in task.result.documents) {
                                val docRef = db.collection("registered_pharmacists").document(doc.id)
                                batch.update(docRef, mapOf(
                                    "branchId" to "",
                                    "branchName" to ""
                                ))
                            }
                            batch.commit()
                        }
                        logAuditTrail("DELETE_BRANCH", "Deleted branch $branchId from director")
                        if (_currentPharmacistBranchId.value == branchId) {
                            _currentPharmacistBranchId.value = ""
                            _currentPharmacistBranchName.value = "Not Configured"
                        }
                        onFinished(true, "Branch deleted successfully.")
                    }
            }
            .addOnFailureListener { e ->
                onFinished(false, "Failed to delete branch: ${e.localizedMessage}")
            }
    }

    fun appointManager(branchId: String, branchName: String, pharmacistUid: String, pharmacistName: String, pharmacistEmail: String, onFinished: (Boolean, String) -> Unit) {
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val batch = db.batch()
        
        // 1. Update the pharmacist's document to make them Branch Manager and link them to this branch
        val pharmacistRef = db.collection("registered_pharmacists").document(pharmacistUid)
        batch.update(pharmacistRef, mapOf(
            "branchId" to branchId,
            "branchName" to branchName,
            "role" to "Branch Manager",
            "isApproved" to true
        ))
        
        // 2. Update the branch document to store the manager details
        val branchRef = db.collection("branches").document(branchId)
        batch.update(branchRef, mapOf(
            "managerId" to pharmacistUid,
            "managerName" to pharmacistName,
            "managerEmail" to pharmacistEmail
        ))
        
        // 3. Demote other pharmacists currently assigned as "Branch Manager" at this branch to "Pharmacist"
        db.collection("registered_pharmacists")
            .whereEqualTo("branchId", branchId)
            .whereEqualTo("role", "Branch Manager")
            .get()
            .addOnCompleteListener { task ->
                if (task.isSuccessful && task.result != null) {
                    for (doc in task.result.documents) {
                        if (doc.id != pharmacistUid) {
                            val otherRef = db.collection("registered_pharmacists").document(doc.id)
                            batch.update(otherRef, mapOf(
                                "role" to "Pharmacist"
                            ))
                        }
                    }
                }
                
                // Commit batch
                batch.commit()
                    .addOnSuccessListener {
                        logAuditTrail("APPOINT_MANAGER", "Appointed $pharmacistName as Manager of $branchName ($branchId)")
                        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                        val currentUser = auth.currentUser
                        if (currentUser != null && currentUser.uid == pharmacistUid) {
                            _currentPharmacistBranchId.value = branchId
                            _currentPharmacistBranchName.value = branchName
                            _currentPharmacistRole.value = "Branch Manager"
                        }
                        onFinished(true, "Successfully appointed $pharmacistName as Manager of $branchName!")
                    }
                    .addOnFailureListener { e ->
                        onFinished(false, "Failed to appoint manager: ${e.localizedMessage}")
                    }
            }
    }

    fun updateBranchDetails(branchId: String, newName: String, newLga: String, newState: String, onFinished: (Boolean, String) -> Unit) {
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        val user = auth.currentUser
        if (user == null) {
            onFinished(false, "Authentication required.")
            return
        }
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val branchRef = db.collection("branches").document(branchId)
        
        db.runTransaction { transaction ->
            val snapshot = transaction.get(branchRef)
            if (!snapshot.exists()) {
                throw com.google.firebase.firestore.FirebaseFirestoreException(
                    "Branch not found",
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.NOT_FOUND
                )
            }
            transaction.update(branchRef, mapOf(
                "name" to newName.trim(),
                "lga" to newLga.trim(),
                "state" to newState.trim()
            ))
        }.addOnSuccessListener {
            logAuditTrail("UPDATE_BRANCH", "Updated details of branch $branchId to Name: $newName, LGA: $newLga, State: $newState")
            onFinished(true, "Branch details updated successfully.")
        }.addOnFailureListener { e ->
            onFinished(false, "Failed to update branch details: ${e.localizedMessage}")
        }
    }

    fun updatePharmacistProfile(newName: String, newPhone: String, onFinished: (Boolean, String) -> Unit) {
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        val user = auth.currentUser
        if (user == null) {
            onFinished(false, "Authentication required.")
            return
        }
        val cleanName = newName.trim()
        val cleanPhone = newPhone.trim().replace(Regex("[^+\\d]"), "")
        if (cleanName.isBlank()) {
            onFinished(false, "Full Name cannot be empty.")
            return
        }
        if (cleanPhone.isBlank()) {
            onFinished(false, "Phone Number cannot be empty.")
            return
        }

        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val userRef = db.collection("registered_pharmacists").document(user.uid)
        
        userRef.update(mapOf(
            "displayName" to cleanName,
            "phoneNumber" to cleanPhone
        ))
            .addOnSuccessListener {
                _currentPharmacistName.value = cleanName
                _currentPharmacistPhone.value = cleanPhone
                onFinished(true, "Profile updated successfully.")
                logAuditTrail("UPDATE_PROFILE", "User updated profile name to '$cleanName' and phone to '$cleanPhone'.")
            }
            .addOnFailureListener { e ->
                onFinished(false, "Failed to update profile: ${e.localizedMessage}")
            }
    }

    sealed class SyncState {
        object Synced : SyncState()
        object Syncing : SyncState()
        data class Error(val message: String) : SyncState()
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun initializeBranchWorkspaceData(onFinished: (Boolean, String) -> Unit) {
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        val user = auth.currentUser
        val bId = _currentPharmacistBranchId.value
        if (user == null || bId.isNullOrBlank()) {
            onFinished(false, "Authentication or Branch registration required before initializing baseline reference datasets.")
            return
        }

        viewModelScope.launch {
            try {
                // 1. Clear existing local database tables to avoid duplicates and ensure a pristine experience
                repository.clearAllData()

                val now = System.currentTimeMillis()

                // 2. Populate 10 realistic medicines (with batch, alert levels, pricing, suppliers, etc.)
                val medicines = listOf(
                    com.example.data.InventoryItem(
                        id = generateUniqueId(),
                        name = "Aldomet",
                        dosage = "250mg",
                        stockQuantity = 23,
                        minRequiredStock = 11,
                        category = "Antihypertensive",
                        price = 2000.0,
                        expiryDate = now + (365L * 24 * 60 * 60 * 1000), // 1 year expiry
                        batchNumber = "ALD-0982-A",
                        supplier = "Aspen Port Elizabeth",
                        unitForm = "Tablet",
                        brand = "Aspen",
                        salesStrategy = "FIFO",
                        lastUpdated = now
                    ),
                    com.example.data.InventoryItem(
                        id = generateUniqueId(),
                        name = "Amartem Soft Gel",
                        dosage = "20/120mg",
                        stockQuantity = 45,
                        minRequiredStock = 10,
                        category = "Antimalarial",
                        price = 1500.0,
                        expiryDate = now + (240L * 24 * 60 * 60 * 1000), // 8 months
                        batchNumber = "AMT-7741-K",
                        supplier = "Fidson Healthcare PLC",
                        unitForm = "Capsule",
                        brand = "Fidson",
                        salesStrategy = "FEFO",
                        lastUpdated = now
                    ),
                    com.example.data.InventoryItem(
                        id = generateUniqueId(),
                        name = "Glucophage",
                        dosage = "500mg",
                        stockQuantity = 5, // Alert state!
                        minRequiredStock = 15,
                        category = "Antidiabetic",
                        price = 1200.0,
                        expiryDate = now + (180L * 24 * 60 * 60 * 1000),
                        batchNumber = "GLU-1029-B",
                        supplier = "Merck Group",
                        unitForm = "Tablet",
                        brand = "Merck",
                        salesStrategy = "FEFO",
                        lastUpdated = now
                    ),
                    com.example.data.InventoryItem(
                        id = generateUniqueId(),
                        name = "Ventolin Inhaler",
                        dosage = "100mcg",
                        stockQuantity = 18,
                        minRequiredStock = 8,
                        category = "Bronchodilator",
                        price = 3500.0,
                        expiryDate = now + (500L * 24 * 60 * 60 * 1000),
                        batchNumber = "VEN-8832-I",
                        supplier = "GlaxoSmithKline (GSK)",
                        unitForm = "Inhaler",
                        brand = "GSK",
                        salesStrategy = "FIFO",
                        lastUpdated = now
                    ),
                    com.example.data.InventoryItem(
                        id = generateUniqueId(),
                        name = "Panadol Optizorb",
                        dosage = "500mg",
                        stockQuantity = 120,
                        minRequiredStock = 20,
                        category = "Analgesic",
                        price = 800.0,
                        expiryDate = now + (720L * 24 * 60 * 60 * 1000),
                        batchNumber = "PAN-5011-L",
                        supplier = "GSK Nigeria",
                        unitForm = "Tablet",
                        brand = "Panadol",
                        salesStrategy = "FEFO",
                        lastUpdated = now
                    ),
                    com.example.data.InventoryItem(
                        id = generateUniqueId(),
                        name = "Co-Diovan",
                        dosage = "80/12.5mg",
                        stockQuantity = 2, // Alert state!
                        minRequiredStock = 10,
                        category = "Antihypertensive",
                        price = 6500.0,
                        expiryDate = now + (120L * 24 * 60 * 60 * 1000),
                        batchNumber = "COD-4491-X",
                        supplier = "Novartis Pharmaceuticals",
                        unitForm = "Tablet",
                        brand = "Novartis",
                        salesStrategy = "FIFO",
                        lastUpdated = now
                    ),
                    com.example.data.InventoryItem(
                        id = generateUniqueId(),
                        name = "Augmentin",
                        dosage = "625mg",
                        stockQuantity = 15,
                        minRequiredStock = 10,
                        category = "Antibiotic",
                        price = 5000.0,
                        expiryDate = now + (300L * 24 * 60 * 60 * 1000),
                        batchNumber = "AUG-2321-Y",
                        supplier = "GSK Commercial",
                        unitForm = "Tablet",
                        brand = "GSK",
                        salesStrategy = "FIFO",
                        lastUpdated = now
                    ),
                    com.example.data.InventoryItem(
                        id = generateUniqueId(),
                        name = "Lipitor",
                        dosage = "20mg",
                        stockQuantity = 30,
                        minRequiredStock = 10,
                        category = "Lipid-lowering agent",
                        price = 4800.0,
                        expiryDate = now + (450L * 24 * 60 * 60 * 1000),
                        batchNumber = "LIP-5521-Z",
                        supplier = "Pfizer Specialty",
                        unitForm = "Tablet",
                        brand = "Pfizer",
                        salesStrategy = "FEFO",
                        lastUpdated = now
                    ),
                    com.example.data.InventoryItem(
                        id = generateUniqueId(),
                        name = "Gaviscon Double Action",
                        dosage = "150ml",
                        stockQuantity = 8,
                        minRequiredStock = 5,
                        category = "Antacid",
                        price = 2400.0,
                        expiryDate = now + (90L * 24 * 60 * 60 * 1000), // Expiring soon!
                        batchNumber = "GAV-9032-M",
                        supplier = "Reckitt Benckiser",
                        unitForm = "Suspension",
                        brand = "Reckitt",
                        salesStrategy = "FEFO",
                        lastUpdated = now
                    ),
                    com.example.data.InventoryItem(
                        id = generateUniqueId(),
                        name = "Claritin (Loratadine)",
                        dosage = "10mg",
                        stockQuantity = 50,
                        minRequiredStock = 15,
                        category = "Antihistamine",
                        price = 1000.0,
                        expiryDate = now + (600L * 24 * 60 * 60 * 1000),
                        batchNumber = "CLA-1120-N",
                        supplier = "Bayer Healthcare",
                        unitForm = "Tablet",
                        brand = "Bayer",
                        salesStrategy = "FIFO",
                        lastUpdated = now
                    )
                )

                for (med in medicines) {
                    repository.insertInventoryItem(med)
                    
                    // Create and insert corresponding batch for tracking
                    val firstBatch = com.example.data.InventoryBatch(
                        inventoryItemId = med.id,
                        batchNumber = med.batchNumber,
                        stockQuantity = med.stockQuantity,
                        expiryDate = med.expiryDate,
                        price = med.price,
                        supplier = med.supplier
                    )
                    repository.insertInventoryBatch(firstBatch)

                    // Also inject some expired batches to demonstrate the proactive blocking mechanism
                    if (med.name == "Aldomet") {
                        val expiredBatch = com.example.data.InventoryBatch(
                            inventoryItemId = med.id,
                            batchNumber = "ALD-EXPIRED-TEST",
                            stockQuantity = 15,
                            expiryDate = now - (30L * 24 * 60 * 60 * 1000), // Expired 30 days ago!
                            price = med.price,
                            supplier = med.supplier
                        )
                        repository.insertInventoryBatch(expiredBatch)
                    } else if (med.name == "Gaviscon Double Action") {
                        val expiredBatch = com.example.data.InventoryBatch(
                            inventoryItemId = med.id,
                            batchNumber = "GAV-EXPIRED-TEST",
                            stockQuantity = 8,
                            expiryDate = now - (15L * 24 * 60 * 60 * 1000), // Expired 15 days ago!
                            price = med.price,
                            supplier = med.supplier
                        )
                        repository.insertInventoryBatch(expiredBatch)
                    }

                    // Dynamically recalculate total item stock
                    recalculateItemStockFromBatches(med.id)
                }

                // 3. Populate 5 customers (Chinedu, Fatima, Olumide, Amina, Emeka)
                val customerList = listOf(
                    com.example.data.Customer(
                        id = generateUniqueId(),
                        name = "Chinedu Okafor",
                        phoneNumber = "+2348031234567",
                        email = "chinedu.okafor@gmail.com",
                        notes = "Chronic hypertensive patient. Highly compliant.",
                        loyaltyPoints = 120,
                        refillStreak = 4,
                        dateAdded = now - (60L * 24 * 60 * 60 * 1000),
                        age = 42,
                        gender = "Male",
                        state = "Lagos",
                        lga = "Ikeja",
                        city = "Ikeja"
                    ),
                    com.example.data.Customer(
                        id = generateUniqueId(),
                        name = "Fatima Yusuf",
                        phoneNumber = "+2348057654321",
                        email = "fatima.y@yahoo.com",
                        notes = "First trimester prenatal customer. Responsive.",
                        loyaltyPoints = 30,
                        refillStreak = 1,
                        dateAdded = now - (15L * 24 * 60 * 60 * 1000),
                        age = 29,
                        gender = "Female",
                        state = "FCT",
                        lga = "Abuja Municipal",
                        city = "Garki"
                    ),
                    com.example.data.Customer(
                        id = generateUniqueId(),
                        name = "Olumide Adebayo",
                        phoneNumber = "+2348029876543",
                        email = "olumide.adebayo@outlook.com",
                        notes = "Type 2 Diabetic. Requires support on lifestyle adjustments.",
                        loyaltyPoints = 85,
                        refillStreak = 3,
                        dateAdded = now - (45L * 24 * 60 * 60 * 1000),
                        age = 55,
                        gender = "Male",
                        state = "Oyo",
                        lga = "Ibadan North",
                        city = "Ibadan"
                    ),
                    com.example.data.Customer(
                        id = generateUniqueId(),
                        name = "Amina Bello",
                        phoneNumber = "+2348091112223",
                        email = "amina.b@gmail.com",
                        notes = "Moderate persistent asthmatic. Uses salbutamol inhaler.",
                        loyaltyPoints = 50,
                        refillStreak = 2,
                        dateAdded = now - (30L * 24 * 60 * 60 * 1000),
                        age = 34,
                        gender = "Female",
                        state = "Kano",
                        lga = "Kano Municipal",
                        city = "Kano"
                    ),
                    com.example.data.Customer(
                        id = generateUniqueId(),
                        name = "Emeka Nwachukwu",
                        phoneNumber = "+2348123456789",
                        email = "emeka.n@careflux.com",
                        notes = "Elderly cardiac patient. Needs monthly delivery service.",
                        loyaltyPoints = 210,
                        refillStreak = 6,
                        dateAdded = now - (90L * 24 * 60 * 60 * 1000),
                        age = 61,
                        gender = "Male",
                        state = "Enugu",
                        lga = "Enugu North",
                        city = "Enugu"
                    )
                )

                for (cust in customerList) {
                    repository.insertCustomer(cust)
                    val custMap = mapOf(
                        "id" to cust.id,
                        "name" to cust.name,
                        "phoneNumber" to cust.phoneNumber,
                        "email" to cust.email,
                        "notes" to cust.notes,
                        "loyaltyPoints" to cust.loyaltyPoints,
                        "refillStreak" to cust.refillStreak,
                        "dateAdded" to cust.dateAdded,
                        "age" to cust.age,
                        "gender" to cust.gender,
                        "state" to cust.state,
                        "lga" to cust.lga,
                        "city" to cust.city
                    )
                    syncEntityToFirestore("branch_customers", cust.id.toString(), custMap)
                }

                // 4. Link Medications and active Clinical Interventions to the customers
                val chinedu = customerList[0]
                val fatima = customerList[1]
                val olumide = customerList[2]
                val amina = customerList[3]
                val emeka = customerList[4]

                val medAldomet = medicines[0]
                val medGlucophage = medicines[2]
                val medCoDiovan = medicines[5]

                // Insert Customer Medications
                val custMeds = listOf(
                    com.example.data.CustomerMedication(
                        id = generateUniqueId(),
                        customerId = chinedu.id,
                        inventoryItemId = medAldomet.id,
                        medicationName = "${medAldomet.name} ${medAldomet.dosage}",
                        customDosage = "Take 1 tablet twice daily",
                        cost = 2000.0,
                        cycleDays = 30,
                        nextRefillDate = now + (10L * 24 * 60 * 60 * 1000)
                    ),
                    com.example.data.CustomerMedication(
                        id = generateUniqueId(),
                        customerId = olumide.id,
                        inventoryItemId = medGlucophage.id,
                        medicationName = "${medGlucophage.name} ${medGlucophage.dosage}",
                        customDosage = "Take 1 tablet with meals twice daily",
                        cost = 1200.0,
                        cycleDays = 30,
                        nextRefillDate = now + (5L * 24 * 60 * 60 * 1000)
                    ),
                    com.example.data.CustomerMedication(
                        id = generateUniqueId(),
                        customerId = emeka.id,
                        inventoryItemId = medCoDiovan.id,
                        medicationName = "${medCoDiovan.name} ${medCoDiovan.dosage}",
                        customDosage = "Take 1 tablet daily in the morning",
                        cost = 6500.0,
                        cycleDays = 30,
                        nextRefillDate = now + (2L * 24 * 60 * 60 * 1000) // Refill very soon!
                    )
                )

                for (med in custMeds) {
                    repository.insertCustomerMedication(med)
                    val medMap = mapOf(
                        "id" to med.id,
                        "customerId" to med.customerId,
                        "inventoryItemId" to med.inventoryItemId,
                        "medicationName" to med.medicationName,
                        "customDosage" to med.customDosage,
                        "cost" to med.cost,
                        "cycleDays" to med.cycleDays,
                        "nextRefillDate" to med.nextRefillDate
                    )
                    syncEntityToFirestore("branch_customer_medications", med.id.toString(), medMap)
                }

                // Insert Clinical Interventions
                val interventions = listOf(
                    com.example.data.ClinicalIntervention(
                        id = generateUniqueId(),
                        customerId = chinedu.id,
                        presentation = "Patient complaining of mild fatigue and swelling in lower extremities (ankles) since starting Aldomet.",
                        testResults = "BP: 145/95 mmHg, HR: 72 bpm. Mild bilateral ankle edema noticed.",
                        recommendation = "Consulted prescribing physician to consider reducing dosage or adding low-dose thiazide. Advised sodium restriction and elevation of legs.",
                        currentStatus = "In Progress",
                        followUpDay3Sent = true,
                        followUpDay7Sent = false,
                        followUpDay14Sent = false,
                        dateAdded = now - (3L * 24 * 60 * 60 * 1000)
                    ),
                    com.example.data.ClinicalIntervention(
                        id = generateUniqueId(),
                        customerId = fatima.id,
                        presentation = "Unexplained mild urticarial skin rash developed after starting a new prenatal multivitamin.",
                        testResults = "No fever, localized rash on trunk. Normal vitals.",
                        recommendation = "Advised temporary cessation of multivitamin. Recommended cetirizine 10mg daily for rash and referral to gynecologist for pregnancy-safe alternatives.",
                        currentStatus = "Completed",
                        followUpDay3Sent = true,
                        followUpDay7Sent = true,
                        followUpDay14Sent = false,
                        dateAdded = now - (10L * 24 * 60 * 60 * 1000)
                    )
                )

                for (inter in interventions) {
                    repository.insertClinicalIntervention(inter)
                    val interMap = mapOf(
                        "id" to inter.id,
                        "customerId" to inter.customerId,
                        "presentation" to inter.presentation,
                        "testResults" to inter.testResults,
                        "recommendation" to inter.recommendation,
                        "currentStatus" to inter.currentStatus,
                        "followUpDay3Sent" to inter.followUpDay3Sent,
                        "followUpDay7Sent" to inter.followUpDay7Sent,
                        "followUpDay14Sent" to inter.followUpDay14Sent,
                        "dateAdded" to inter.dateAdded
                    )
                    syncEntityToFirestore("branch_interventions", inter.id.toString(), interMap)
                }

                // 5. Populate 10 medication sales and receipts over the past 7 days (to instantly hydrate analytics charts)
                val baseTime = now - (7L * 24 * 60 * 60 * 1000)
                val salesData = listOf(
                    com.example.data.MedicationSale(
                        id = generateUniqueId(),
                        productName = "Aldomet",
                        brand = "Aspen",
                        genericName = "Methyldopa",
                        category = "Antihypertensive",
                        quantitySold = 2,
                        dateSold = baseTime + (1L * 24 * 60 * 60 * 1000),
                        salePrice = 4000.0,
                        batchNumber = "ALD-0982-A",
                        patientAge = 42,
                        patientGender = "Male",
                        patientState = "Lagos",
                        patientLga = "Ikeja"
                    ),
                    com.example.data.MedicationSale(
                        id = generateUniqueId(),
                        productName = "Amartem Soft Gel",
                        brand = "Fidson",
                        genericName = "Artemether/Lumefantrine",
                        category = "Antimalarial",
                        quantitySold = 1,
                        dateSold = baseTime + (2L * 24 * 60 * 60 * 1000),
                        salePrice = 1500.0,
                        batchNumber = "AMT-7741-K",
                        patientAge = 25,
                        patientGender = "Female",
                        patientState = "Lagos",
                        patientLga = "Ikeja"
                    ),
                    com.example.data.MedicationSale(
                        id = generateUniqueId(),
                        productName = "Panadol Optizorb",
                        brand = "Panadol",
                        genericName = "Paracetamol",
                        category = "Analgesic",
                        quantitySold = 5,
                        dateSold = baseTime + (3L * 24 * 60 * 60 * 1000),
                        salePrice = 4000.0,
                        batchNumber = "PAN-5011-L",
                        patientAge = 35,
                        patientGender = "Male",
                        patientState = "Lagos",
                        patientLga = "Ikeja"
                    ),
                    com.example.data.MedicationSale(
                        id = generateUniqueId(),
                        productName = "Ventolin Inhaler",
                        brand = "GSK",
                        genericName = "Salbutamol",
                        category = "Bronchodilator",
                        quantitySold = 1,
                        dateSold = baseTime + (4L * 24 * 60 * 60 * 1000),
                        salePrice = 3500.0,
                        batchNumber = "VEN-8832-I",
                        patientAge = 14,
                        patientGender = "Female",
                        patientState = "Lagos",
                        patientLga = "Ikeja"
                    ),
                    com.example.data.MedicationSale(
                        id = generateUniqueId(),
                        productName = "Augmentin",
                        brand = "GSK",
                        genericName = "Amoxicillin/Clavulanate",
                        category = "Antibiotic",
                        quantitySold = 1,
                        dateSold = baseTime + (5L * 24 * 60 * 60 * 1000),
                        salePrice = 5000.0,
                        batchNumber = "AUG-2321-Y",
                        patientAge = 29,
                        patientGender = "Female",
                        patientState = "Lagos",
                        patientLga = "Ikeja"
                    ),
                    com.example.data.MedicationSale(
                        id = generateUniqueId(),
                        productName = "Lipitor",
                        brand = "Pfizer",
                        genericName = "Atorvastatin",
                        category = "Lipid-lowering agent",
                        quantitySold = 2,
                        dateSold = baseTime + (5L * 24 * 60 * 60 * 1000) + (12 * 60 * 60 * 1000),
                        salePrice = 9600.0,
                        batchNumber = "LIP-5521-Z",
                        patientAge = 58,
                        patientGender = "Male",
                        patientState = "Lagos",
                        patientLga = "Ikeja"
                    ),
                    com.example.data.MedicationSale(
                        id = generateUniqueId(),
                        productName = "Gaviscon Double Action",
                        brand = "Reckitt",
                        genericName = "Sodium Alginate",
                        category = "Antacid",
                        quantitySold = 2,
                        dateSold = baseTime + (6L * 24 * 60 * 60 * 1000),
                        salePrice = 4800.0,
                        batchNumber = "GAV-9032-M",
                        patientAge = 40,
                        patientGender = "Male",
                        patientState = "Lagos",
                        patientLga = "Ikeja"
                    ),
                    com.example.data.MedicationSale(
                        id = generateUniqueId(),
                        productName = "Claritin (Loratadine)",
                        brand = "Bayer",
                        genericName = "Loratadine",
                        category = "Antihistamine",
                        quantitySold = 3,
                        dateSold = baseTime + (6L * 24 * 60 * 60 * 1000) + (10 * 60 * 60 * 1000),
                        salePrice = 3000.0,
                        batchNumber = "CLA-1120-N",
                        patientAge = 31,
                        patientGender = "Female",
                        patientState = "Lagos",
                        patientLga = "Ikeja"
                    ),
                    com.example.data.MedicationSale(
                        id = generateUniqueId(),
                        productName = "Co-Diovan",
                        brand = "Novartis",
                        genericName = "Valsartan/HCTZ",
                        category = "Antihypertensive",
                        quantitySold = 1,
                        dateSold = now - (12 * 60 * 60 * 1000), // Today
                        salePrice = 6500.0,
                        batchNumber = "COD-4491-X",
                        patientAge = 65,
                        patientGender = "Male",
                        patientState = "Lagos",
                        patientLga = "Ikeja"
                    ),
                    com.example.data.MedicationSale(
                        id = generateUniqueId(),
                        productName = "Panadol Optizorb",
                        brand = "Panadol",
                        genericName = "Paracetamol",
                        category = "Analgesic",
                        quantitySold = 4,
                        dateSold = now - (4 * 60 * 60 * 1000), // Today
                        salePrice = 3200.0,
                        batchNumber = "PAN-5011-L",
                        patientAge = 22,
                        patientGender = "Female",
                        patientState = "Lagos",
                        patientLga = "Ikeja"
                    )
                )

                for (sale in salesData) {
                    repository.insertMedicationSale(sale)
                }

                // Insert 5 historical matching receipts
                val receiptsData = listOf(
                    com.example.data.Receipt(
                        id = generateUniqueId(),
                        timestamp = baseTime + (1L * 24 * 60 * 60 * 1000),
                        customerName = chinedu.name,
                        totalAmount = 4000.0,
                        imageFileName = "receipt_aldomet.pdf",
                        isInvoice = false,
                        paymentStatus = "Paid"
                    ),
                    com.example.data.Receipt(
                        id = generateUniqueId(),
                        timestamp = baseTime + (3L * 24 * 60 * 60 * 1000),
                        customerName = "Walk-in Patient",
                        totalAmount = 4000.0,
                        imageFileName = "receipt_panadol.pdf",
                        isInvoice = false,
                        paymentStatus = "Paid"
                    ),
                    com.example.data.Receipt(
                        id = generateUniqueId(),
                        timestamp = baseTime + (5L * 24 * 60 * 60 * 1000),
                        customerName = "Walk-in Patient",
                        totalAmount = 14600.0,
                        imageFileName = "receipt_multi.pdf",
                        isInvoice = false,
                        paymentStatus = "Paid"
                    ),
                    com.example.data.Receipt(
                        id = generateUniqueId(),
                        timestamp = baseTime + (6L * 24 * 60 * 60 * 1000),
                        customerName = amina.name,
                        totalAmount = 7800.0,
                        imageFileName = "receipt_amina.pdf",
                        isInvoice = false,
                        paymentStatus = "Paid"
                    ),
                    com.example.data.Receipt(
                        id = generateUniqueId(),
                        timestamp = now - (4 * 60 * 60 * 1000),
                        customerName = emeka.name,
                        totalAmount = 9700.0,
                        imageFileName = "invoice_emeka.pdf",
                        isInvoice = true,
                        paymentStatus = "Pending"
                    )
                )

                for (rec in receiptsData) {
                    repository.insertReceipt(rec)
                }

                // 6. Populate 5 realistic daily prescription volume entries (for analytics graphs)
                val volumesData = listOf(
                    com.example.data.DailyPrescriptionVolume(dateString = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(now - 4L * 24 * 60 * 60 * 1000)), volume = 45, notes = "Standard weekday patient intake"),
                    com.example.data.DailyPrescriptionVolume(dateString = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(now - 3L * 24 * 60 * 60 * 1000)), volume = 38, notes = "Heavy rain afternoon, lower walk-ins"),
                    com.example.data.DailyPrescriptionVolume(dateString = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(now - 2L * 24 * 60 * 60 * 1000)), volume = 52, notes = "Cooperative medical screening outreach"),
                    com.example.data.DailyPrescriptionVolume(dateString = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(now - 1L * 24 * 60 * 60 * 1000)), volume = 41, notes = "Midweek refills peak"),
                    com.example.data.DailyPrescriptionVolume(dateString = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(now)), volume = 48, notes = "Today's current logged prescriptions")
                )

                for (vol in volumesData) {
                    repository.insertPrescriptionVolume(vol)
                }

                // 7. Populate 5 operational tasks (with assignees and due states)
                val tasksData = listOf(
                    com.example.data.OperationTask(
                        id = generateUniqueId(),
                        title = "Verify Cold Chain Fridge Temperature",
                        description = "Check insulin and vaccine storage temperature logs. Ensure refrigerator remains strictly between 2°C and 8°C. Document reading in physical logbook.",
                        urgency = "High",
                        category = "Manual",
                        isCompleted = false,
                        assignedToName = "Staff Pharmacist"
                    ),
                    com.example.data.OperationTask(
                        id = generateUniqueId(),
                        title = "Audit Controlled Substances Ledger",
                        description = "Perform physical balance reconciliation for Class A prescription medications (Morphine, Fentanyl, Diazepam). Reconcile with digital prescription counts and register books.",
                        urgency = "High",
                        category = "Manual",
                        isCompleted = false,
                        assignedToName = "Branch Manager"
                    ),
                    com.example.data.OperationTask(
                        id = generateUniqueId(),
                        title = "Contact Chinedu Okafor for Refill Schedule",
                        description = "Patient Chinedu is due for Aldomet 250mg refill in 10 days. Reach out to confirm patient status, compliance, and schedule delivery.",
                        urgency = "Medium",
                        category = "Patient Follow-up",
                        isCompleted = false,
                        assignedToName = "Staff Pharmacist"
                    ),
                    com.example.data.OperationTask(
                        id = generateUniqueId(),
                        title = "Prepare quarterly PCN compliance file",
                        description = "Ensure all pharmacist licenses, premises permits, and waste disposal logs are correctly sorted and indexed for the upcoming Pharmacists Council of Nigeria (PCN) inspection.",
                        urgency = "Medium",
                        category = "Manual",
                        isCompleted = false,
                        assignedToName = "Branch Manager"
                    ),
                    com.example.data.OperationTask(
                        id = generateUniqueId(),
                        title = "Restock low-stock Glucophage 500mg",
                        description = "Glucophage inventory has fallen to 5 tablets, which is well below the minimum alert safety threshold of 15. Create procurement draft.",
                        urgency = "High",
                        category = "AI Insight",
                        isCompleted = false,
                        assignedToName = "Staff Pharmacist"
                    )
                )

                for (task in tasksData) {
                    repository.insertOperationTask(task)
                    val taskMap = mapOf(
                        "id" to task.id,
                        "title" to task.title,
                        "description" to task.description,
                        "urgency" to task.urgency,
                        "category" to task.category,
                        "isCompleted" to task.isCompleted,
                        "assignedToName" to task.assignedToName
                    )
                    syncEntityToFirestore("branch_operation_tasks", task.id.toString(), taskMap)
                }

                // Log a nice audit trail
                logAuditTrail("WORKSPACE_INITIALIZE", "Initialized baseline reference datasets and standard medicine catalog for branch operations.")

                onFinished(true, "Branch workspace successfully initialized with standard product catalog, client profiles, historical logs, and task baselines!")
            } catch (e: Exception) {
                e.printStackTrace()
                onFinished(false, "Workspace initialization failed: ${e.localizedMessage}")
            }
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PharmacyViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return PharmacyViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

data class PostDispatchConfirmData(
    val customer: com.example.data.Customer,
    val medication: com.example.data.CustomerMedication
)
