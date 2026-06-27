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

        // Seed some demo records if database is launched empty
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

    private suspend fun saveAndSyncInventoryItemDirectly(item: InventoryItem) {
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
    }

    fun addOrUpdateInventory(name: String, dosage: String, currentStock: Int, minStock: Int, category: String, price: Double = 0.0, id: Int = 0, updateStockStats: Boolean = false, addedQty: Int = 0, expiryDate: Long? = null, batchNumber: String = "", supplier: String = "", imageUri: String? = null, unitForm: String = "", brand: String = "") {
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
            saveAndSyncInventoryItemDirectly(item)
        }
    }

    fun deleteInventory(item: InventoryItem) {
        viewModelScope.launch {
            repository.deleteInventoryItem(item)
            deleteEntityFromFirestore("branch_inventory", item.id.toString())
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

    fun updateStockLevel(item: InventoryItem, newQuantity: Int) {
        viewModelScope.launch {
            saveAndSyncInventoryItemDirectly(item.copy(stockQuantity = newQuantity, lastUpdated = System.currentTimeMillis()))
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
        city: String = "Ikeja"
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
            repository.insertCustomer(
                Customer(
                    name = name.trim(),
                    phoneNumber = phone.trim(),
                    email = email.trim(),
                    notes = notes.trim(),
                    age = age,
                    gender = gender,
                    state = state.trim(),
                    lga = lga.trim(),
                    city = city.trim()
                )
            )
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
            triggerImmediateSync()
        }
    }
    
    fun deleteCustomer(customer: Customer) {
        viewModelScope.launch { 
            repository.deleteCustomer(customer)
            // Cleanup orphans since no ForeignKeys cascade
            customerMedications.value.filter { it.customerId == customer.id }.forEach { med ->
                repository.deleteCustomerMedication(med)
                try {
                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("customer_medications").document("${deviceId}_${med.id}").delete()
                } catch (e: Exception) { e.printStackTrace() }
            }
            clinicalInterventions.value.filter { it.customerId == customer.id }.forEach { inter ->
                repository.deleteClinicalIntervention(inter)
                try {
                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("interventions").document("${deviceId}_${inter.id}").delete()
                } catch (e: Exception) { e.printStackTrace() }
            }
            try {
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("customers").document("${deviceId}_${customer.id}").delete()
            } catch (e: Exception) { e.printStackTrace() }
            triggerImmediateSync()
        }
    }
    
    fun addCustomerMedication(customerId: Int, invItemId: Int, medName: String, customDosage: String, cost: Double, cycleDays: Int, nextRefill: Long) {
        viewModelScope.launch {
            repository.insertCustomerMedication(
                CustomerMedication(
                    customerId = customerId,
                    inventoryItemId = invItemId,
                    medicationName = medName,
                    customDosage = customDosage,
                    cost = cost,
                    cycleDays = cycleDays,
                    nextRefillDate = nextRefill
                )
            )
            triggerImmediateSync()
        }
    }
    
    fun updateCustomerMedication(med: CustomerMedication) {
        viewModelScope.launch { 
            repository.updateCustomerMedication(med) 
            triggerImmediateSync()
        }
    }

    fun deleteCustomerMedication(med: CustomerMedication) {
        viewModelScope.launch { 
            repository.deleteCustomerMedication(med) 
            triggerImmediateSync()
        }
    }

    // --- Clinical Intervention Actions ---
    fun addClinicalIntervention(customerId: Int, presentation: String, testResults: String, recommendation: String) {
        viewModelScope.launch {
            repository.insertClinicalIntervention(
                ClinicalIntervention(
                    customerId = customerId,
                    presentation = presentation,
                    testResults = testResults,
                    recommendation = recommendation
                )
            )
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
            repository.updateClinicalIntervention(intervention.copy(currentStatus = newStatus))
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

    fun recordMedicationSale(cartItem: CartItem, customer: Customer?) {
        viewModelScope.launch {
            val inv = cartItem.inventoryItem
            val age = customer?.age ?: 30
            val gender = customer?.gender ?: "Male"
            val state = customer?.state ?: "Lagos"
            val lga = customer?.lga ?: "Ikeja"
            val city = customer?.city ?: "Ikeja"

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
        }
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

    fun acceptRescueListing(listing: RescueListing) {
        viewModelScope.launch {
            val deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
            if (listing.firestoreId.isEmpty()) return@launch

            try {
                // Update local Room record immediately
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

                // Update firestore asynchronously
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val docRef = db.collection("expiry_rescue_listings").document(listing.firestoreId)
                docRef.update(
                    mapOf(
                        "status" to "Accepted",
                        "acceptedByDeviceId" to deviceId,
                        "acceptedByDeviceModel" to deviceModel,
                        "acceptedAt" to System.currentTimeMillis()
                    )
                ).await()
            } catch (e: Exception) {
                e.printStackTrace()
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
        return try {
            val apiKey = getTermiiApiKey()
            val senderId = getTermiiSenderId()
            if (apiKey.isBlank() || apiKey == "YOUR_TERMII_API_KEY" || apiKey == "TERMII_API_KEY_DEFAULT_VALUE") {
                android.util.Log.w("PharmacyViewModel", "Termii API Key is not configured or using default placeholder.")
                return false
            }

            // Normalise phone number format for standard Nigerian network delivery (e.g., prefix 234)
            var cleanPhone = to.trim().replace("[^0-9]".toRegex(), "")
            if (cleanPhone.startsWith("0") && cleanPhone.length == 11) {
                cleanPhone = "234" + cleanPhone.substring(1)
            } else if (!cleanPhone.startsWith("234") && cleanPhone.length == 10) {
                cleanPhone = "234" + cleanPhone
            }

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
            android.util.Log.d("PharmacyViewModel", "Termii SMS Sent to $cleanPhone. Success: $isSuccess. Code: ${response.code}. Message: ${response.message}")
            isSuccess
        } catch (e: Exception) {
            e.printStackTrace()
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
    }

    private fun setupBranchRealtimeSync(userBranchId: String) {
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
                    if (act == "TRANSFER" || act == "BULK_TRANSFER") {
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
    }

    fun syncEntityToFirestore(collectionName: String, docId: String, dataMap: Map<String, Any?>) {
        val branchId = _currentPharmacistBranchId.value ?: return
        viewModelScope.launch {
            try {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val mutableMap = dataMap.toMutableMap()
                mutableMap["branchId"] = branchId
                mutableMap["syncedAt"] = System.currentTimeMillis()
                db.collection(collectionName).document("${branchId}_$docId").set(mutableMap)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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
            android.widget.Toast.makeText(getApplication(), "Stock Transfer registered and logged safely", android.widget.Toast.LENGTH_SHORT).show()
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
