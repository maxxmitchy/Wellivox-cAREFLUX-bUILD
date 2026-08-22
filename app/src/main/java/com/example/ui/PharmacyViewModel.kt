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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
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

data class CsvProductImportItem(
    val csvId: Int = 0,
    val name: String = "",
    val brand: String = "",
    val dosage: String = "",
    val category: String = "",
    val stockQuantity: Int = 0,
    val threshold: Int = 10,
    val price: Double = 0.0,
    val expiryDate: Long = 0L,
    val batchNumber: String = "",
    val supplier: String = "",
    val unitForm: String = ""
)

data class CsvProductDiscrepancy(
    val csvItem: CsvProductImportItem,
    val existingItem: InventoryItem
)

enum class CsvDiscrepancyAction {
    REPLACE,
    UPDATE_ADD_QTY,
    SKIP,
    REPLACE_ALL,
    UPDATE_ADD_QTY_ALL,
    SKIP_ALL
}

data class CsvImportSessionState(
    val discrepancies: List<CsvProductDiscrepancy> = emptyList(),
    val newItemsToImportDirectly: List<CsvProductImportItem> = emptyList(),
    val currentIndex: Int = 0,
    val replacedCount: Int = 0,
    val addedCount: Int = 0,
    val skippedCount: Int = 0,
    val directImportedCount: Int = 0,
    val isFinished: Boolean = false
)

data class WhatsAppTemplate(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val message: String
)

class PharmacyViewModel(application: Application) : AndroidViewModel(application) {

    val repository: PharmacyRepository = PharmacyRepository(PharmacyDatabase.getDatabase(application).pharmacyDao())
    val authRepository: com.example.data.auth.AuthRepository = com.example.data.auth.AuthRepository()
    val deviceRepository: com.example.data.device.DeviceRepository = com.example.data.device.DeviceRepository(application)
    private val prefs = application.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private var isFirstTaskSyncDone = false

    private val _areNotificationsEnabled = kotlinx.coroutines.flow.MutableStateFlow(prefs.getBoolean("notifications_enabled", true))
    val areNotificationsEnabled: StateFlow<Boolean> = _areNotificationsEnabled.asStateFlow()

    fun getNotificationsEnabled(): Boolean {
        return prefs.getBoolean("notifications_enabled", true)
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("notifications_enabled", enabled).apply()
        _areNotificationsEnabled.value = enabled
    }

    fun getNotificationPref(key: String, default: Boolean = true): Boolean {
        return prefs.getBoolean(key, default)
    }

    fun setNotificationPref(key: String, enabled: Boolean) {
        prefs.edit().putBoolean(key, enabled).apply()
    }

    private val _isProfileLoading = kotlinx.coroutines.flow.MutableStateFlow(prefs.getString("cached_branch_id", null) == null)
    val isProfileLoading: StateFlow<Boolean> = _isProfileLoading.asStateFlow()

    fun showLocalNotification(
        title: String,
        content: String,
        targetTab: String = "branch_team",
        targetSubTab: String? = "ops_task_board",
        targetTaskId: Long? = null,
        targetCustomerName: String? = null,
        urgency: com.example.util.NotificationUrgency = com.example.util.NotificationUrgency.STANDARD,
        targetRole: String? = null,
        targetBranchId: String? = null,
        dedupKey: String? = null
    ) {
        if (!getNotificationsEnabled()) return
        try {
            val context = getApplication<Application>().applicationContext
            com.example.util.SmartNotificationDispatcher.dispatchNotification(
                context = context,
                title = title,
                content = content,
                urgency = urgency,
                targetRole = targetRole,
                targetBranchId = targetBranchId,
                targetTab = targetTab,
                targetSubTab = targetSubTab,
                targetTaskId = targetTaskId,
                targetCustomerName = targetCustomerName,
                dedupKey = dedupKey
            )
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

    private val _activeHighlightTaskId = kotlinx.coroutines.flow.MutableStateFlow<Long?>(null)
    val activeHighlightTaskId: StateFlow<Long?> = _activeHighlightTaskId.asStateFlow()

    fun setHighlightTaskId(id: Long?) {
        _activeHighlightTaskId.value = id
    }

    fun clearHighlightTaskId() {
        _activeHighlightTaskId.value = null
    }

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
    }

    fun updateCartItemQuantity(itemId: Int, quantity: Int) {
        val currentCart = _cart.value
        currentCart.find { it.inventoryItem.id == itemId } ?: return
        if (quantity <= 0) {
            removeFromCart(itemId)
            return
        }
        _cart.value = _cart.value.map { if (it.inventoryItem.id == itemId) it.copy(quantity = quantity) else it }
    }

    fun updateCartItemNeedsRefill(itemId: Int, needsRefill: Boolean) {
        _cart.value = _cart.value.map { if (it.inventoryItem.id == itemId) it.copy(needsRefill = needsRefill) else it }
    }

    fun removeFromCart(itemId: Int) {
        val currentCart = _cart.value
        _cart.value = currentCart.filter { it.inventoryItem.id != itemId }
    }

    fun clearCart() {
        _cart.value = emptyList()
        _deliveryFeeString.value = ""
    }

    fun clearCartAndRestoreStock() {
        clearCart()
    }

    // Streams of data from room database
    val activePostDispatchConfirm = kotlinx.coroutines.flow.MutableStateFlow<PostDispatchConfirmData?>(null)
    val activePostDispatchConfirmAlert = kotlinx.coroutines.flow.MutableStateFlow<com.example.data.CustomerAlert?>(null)

    val operationTasks: StateFlow<List<OperationTask>>
    val receipts: StateFlow<List<Receipt>>
    val inventoryItems: StateFlow<List<InventoryItem>>
    val lowStockItems: StateFlow<List<InventoryItem>>
    val reconciled14DaysRatio: StateFlow<Float>
    val unreconciled14DaysCount: StateFlow<Int>
    val overdueReconciliationItems: StateFlow<List<InventoryItem>>
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
    val expiryAlertClaims: StateFlow<List<com.example.data.ExpiryAlertClaim>>
    val organization: StateFlow<Organization?>
    val allUsers: StateFlow<List<User>>
    val inventoryLedgerEntries: StateFlow<List<com.example.data.InventoryLedgerEntry>>
    
    private val _lastFailedSmsLog = kotlinx.coroutines.flow.MutableStateFlow<com.example.data.OutboundSmsLog?>(null)
    val lastFailedSmsLog: StateFlow<com.example.data.OutboundSmsLog?> = _lastFailedSmsLog.asStateFlow()
    private val _branchTransfers = kotlinx.coroutines.flow.MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val branchTransfers: StateFlow<List<Map<String, Any>>> = _branchTransfers.asStateFlow()

    private val _claimingTaskIds = kotlinx.coroutines.flow.MutableStateFlow<Set<Int>>(emptySet())
    val claimingTaskIds: StateFlow<Set<Int>> = _claimingTaskIds.asStateFlow()

    private val _claimingAlertItemIds = kotlinx.coroutines.flow.MutableStateFlow<Set<Int>>(emptySet())
    val claimingAlertItemIds: StateFlow<Set<Int>> = _claimingAlertItemIds.asStateFlow()

    // --- Dynamic Feature Flags State ---
    val deviceId: String
    private val _isAiContentEnabled = kotlinx.coroutines.flow.MutableStateFlow(true)
    val isAiContentEnabled: StateFlow<Boolean> = _isAiContentEnabled.asStateFlow()

    private val _isCarefluxAiEnabled = kotlinx.coroutines.flow.MutableStateFlow(true)
    val isCarefluxAiEnabled: StateFlow<Boolean> = _isCarefluxAiEnabled.asStateFlow()

    private val _isClinicalEnabled = kotlinx.coroutines.flow.MutableStateFlow(true)
    val isClinicalEnabled: StateFlow<Boolean> = _isClinicalEnabled.asStateFlow()

    private val _isMessagingEnabled = kotlinx.coroutines.flow.MutableStateFlow(true)
    val isMessagingEnabled: StateFlow<Boolean> = _isMessagingEnabled.asStateFlow()

    private val _isTriageEnabled = kotlinx.coroutines.flow.MutableStateFlow(true)
    val isTriageEnabled: StateFlow<Boolean> = _isTriageEnabled.asStateFlow()

    private val _isMarketplaceEnabled = kotlinx.coroutines.flow.MutableStateFlow(true)
    val isMarketplaceEnabled: StateFlow<Boolean> = _isMarketplaceEnabled.asStateFlow()

    private val _isProcurementEnabled = kotlinx.coroutines.flow.MutableStateFlow(true)
    val isProcurementEnabled: StateFlow<Boolean> = _isProcurementEnabled.asStateFlow()

    private val _isSuspended = kotlinx.coroutines.flow.MutableStateFlow(prefs.getBoolean("user_suspended", false))
    val isSuspended: StateFlow<Boolean> = _isSuspended.asStateFlow()

    private val _isInventoryLoading = kotlinx.coroutines.flow.MutableStateFlow(true)
    val isInventoryLoading: StateFlow<Boolean> = _isInventoryLoading.asStateFlow()

    // --- Branch Multi-User & Real-time Integration Engine ---
    private var userProfileJob: kotlinx.coroutines.Job? = null
    private val activeSyncJobs = mutableListOf<kotlinx.coroutines.Job>()
    private var activeSyncBranchId: String? = null

    private val _currentPharmacistBranchId = kotlinx.coroutines.flow.MutableStateFlow<String?>(prefs.getString("cached_branch_id", null))
    val currentPharmacistBranchId: StateFlow<String?> = _currentPharmacistBranchId.asStateFlow()

    val branchGenerationToken = java.util.concurrent.atomic.AtomicInteger(0)

    fun getActiveBranchId(): String {
        return _currentPharmacistBranchId.value?.takeIf { it.isNotBlank() }
            ?: prefs.getString("cached_branch_id", null)?.takeIf { it.isNotBlank() }
            ?: ""
    }

    fun getCurrentUserUid(): String {
        return authRepository.getCurrentUser()?.uid?.takeIf { it.isNotBlank() }
            ?: prefs.getString("cached_uid", null)?.takeIf { it.isNotBlank() }
            ?: ""
    }

    private val _currentPharmacistRole = kotlinx.coroutines.flow.MutableStateFlow<String?>(prefs.getString("cached_role", "Pharmacist"))
    val currentPharmacistRole: StateFlow<String?> = _currentPharmacistRole.asStateFlow()

    private val _currentPharmacistName = kotlinx.coroutines.flow.MutableStateFlow<String?>(prefs.getString("cached_name", "Staff Pharmacist"))
    val currentPharmacistName: StateFlow<String?> = _currentPharmacistName.asStateFlow()

    private val _currentPharmacistBranchName = kotlinx.coroutines.flow.MutableStateFlow<String?>(prefs.getString("cached_branch_name", "Careflux Branch"))
    val currentPharmacistBranchName: StateFlow<String?> = _currentPharmacistBranchName.asStateFlow()

    private val _currentPharmacistPhone = kotlinx.coroutines.flow.MutableStateFlow<String?>(prefs.getString("cached_phone", "+2348000000000"))
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

    private val _csvImportSession = kotlinx.coroutines.flow.MutableStateFlow<CsvImportSessionState?>(null)
    val csvImportSession: StateFlow<CsvImportSessionState?> = _csvImportSession.asStateFlow()

    fun prepareCsvProductImport(parsedItems: List<CsvProductImportItem>) {
        viewModelScope.launch {
            val currentInventory = repository.allInventoryItems.firstOrNull() ?: inventoryItems.value
            val discrepancies = mutableListOf<CsvProductDiscrepancy>()
            val newItems = mutableListOf<CsvProductImportItem>()

            for (parsedItem in parsedItems) {
                val trimmedName = parsedItem.name.trim()
                if (trimmedName.isBlank()) continue

                // 1. Check by CSV ID match if csvId > 0
                val matchById = if (parsedItem.csvId > 0) currentInventory.find { it.id == parsedItem.csvId } else null
                
                // 2. Check by Name + Dosage match
                val matchByNameDosage = currentInventory.find { 
                    it.name.trim().equals(trimmedName, ignoreCase = true) && 
                    (parsedItem.dosage.isBlank() || parsedItem.dosage.equals("N/A", ignoreCase = true) || it.dosage.trim().equals(parsedItem.dosage.trim(), ignoreCase = true))
                }

                // 3. Check by Name match
                val matchByName = currentInventory.find { 
                    it.name.trim().equals(trimmedName, ignoreCase = true) 
                }

                val existingMatch = matchById ?: matchByNameDosage ?: matchByName

                if (existingMatch != null) {
                    discrepancies.add(CsvProductDiscrepancy(parsedItem, existingMatch))
                } else {
                    newItems.add(parsedItem)
                }
            }

            // Import non-discrepancy items directly
            var directCount = 0
            for (newItem in newItems) {
                addOrUpdateInventory(
                    name = newItem.name,
                    dosage = newItem.dosage,
                    currentStock = newItem.stockQuantity,
                    minStock = newItem.threshold,
                    category = newItem.category.ifBlank { "General" },
                    price = newItem.price,
                    expiryDate = newItem.expiryDate,
                    batchNumber = newItem.batchNumber,
                    supplier = newItem.supplier,
                    unitForm = newItem.unitForm,
                    brand = newItem.brand,
                    reason = "CSV Direct Import"
                )
                directCount++
            }

            if (discrepancies.isEmpty()) {
                _csvImportSession.value = null
                Toast.makeText(getApplication(), "Successfully imported $directCount new products into inventory.", Toast.LENGTH_LONG).show()
            } else {
                _csvImportSession.value = CsvImportSessionState(
                    discrepancies = discrepancies,
                    newItemsToImportDirectly = newItems,
                    currentIndex = 0,
                    directImportedCount = directCount
                )
            }
        }
    }

    fun resolveCsvDiscrepancy(action: CsvDiscrepancyAction) {
        val session = _csvImportSession.value ?: return
        val discrepancies = session.discrepancies
        val idx = session.currentIndex

        viewModelScope.launch {
            var currentReplaced = session.replacedCount
            var currentAdded = session.addedCount
            var currentSkipped = session.skippedCount

            when (action) {
                CsvDiscrepancyAction.REPLACE -> {
                    if (idx in discrepancies.indices) {
                        val disc = discrepancies[idx]
                        applyReplaceCsvItem(disc)
                        currentReplaced++
                    }
                    val nextIdx = idx + 1
                    if (nextIdx >= discrepancies.size) {
                        finishCsvImportSession(session.copy(replacedCount = currentReplaced, addedCount = currentAdded, skippedCount = currentSkipped, isFinished = true))
                    } else {
                        _csvImportSession.value = session.copy(currentIndex = nextIdx, replacedCount = currentReplaced, addedCount = currentAdded, skippedCount = currentSkipped)
                    }
                }
                CsvDiscrepancyAction.UPDATE_ADD_QTY -> {
                    if (idx in discrepancies.indices) {
                        val disc = discrepancies[idx]
                        applyAddQuantityCsvItem(disc)
                        currentAdded++
                    }
                    val nextIdx = idx + 1
                    if (nextIdx >= discrepancies.size) {
                        finishCsvImportSession(session.copy(replacedCount = currentReplaced, addedCount = currentAdded, skippedCount = currentSkipped, isFinished = true))
                    } else {
                        _csvImportSession.value = session.copy(currentIndex = nextIdx, replacedCount = currentReplaced, addedCount = currentAdded, skippedCount = currentSkipped)
                    }
                }
                CsvDiscrepancyAction.SKIP -> {
                    currentSkipped++
                    val nextIdx = idx + 1
                    if (nextIdx >= discrepancies.size) {
                        finishCsvImportSession(session.copy(replacedCount = currentReplaced, addedCount = currentAdded, skippedCount = currentSkipped, isFinished = true))
                    } else {
                        _csvImportSession.value = session.copy(currentIndex = nextIdx, replacedCount = currentReplaced, addedCount = currentAdded, skippedCount = currentSkipped)
                    }
                }
                CsvDiscrepancyAction.REPLACE_ALL -> {
                    for (i in idx until discrepancies.size) {
                        applyReplaceCsvItem(discrepancies[i])
                        currentReplaced++
                    }
                    finishCsvImportSession(session.copy(replacedCount = currentReplaced, addedCount = currentAdded, skippedCount = currentSkipped, isFinished = true))
                }
                CsvDiscrepancyAction.UPDATE_ADD_QTY_ALL -> {
                    for (i in idx until discrepancies.size) {
                        applyAddQuantityCsvItem(discrepancies[i])
                        currentAdded++
                    }
                    finishCsvImportSession(session.copy(replacedCount = currentReplaced, addedCount = currentAdded, skippedCount = currentSkipped, isFinished = true))
                }
                CsvDiscrepancyAction.SKIP_ALL -> {
                    val remaining = discrepancies.size - idx
                    currentSkipped += remaining
                    finishCsvImportSession(session.copy(replacedCount = currentReplaced, addedCount = currentAdded, skippedCount = currentSkipped, isFinished = true))
                }
            }
        }
    }

    private fun applyReplaceCsvItem(disc: CsvProductDiscrepancy) {
        val csv = disc.csvItem
        val existing = disc.existingItem
        addOrUpdateInventory(
            id = existing.id,
            name = csv.name.ifBlank { existing.name },
            dosage = csv.dosage.ifBlank { existing.dosage },
            currentStock = csv.stockQuantity,
            minStock = if (csv.threshold > 0) csv.threshold else existing.minRequiredStock,
            category = csv.category.ifBlank { existing.category },
            price = if (csv.price > 0) csv.price else existing.price,
            expiryDate = if (csv.expiryDate > 0) csv.expiryDate else existing.expiryDate,
            batchNumber = csv.batchNumber.ifBlank { existing.batchNumber },
            supplier = csv.supplier.ifBlank { existing.supplier },
            unitForm = csv.unitForm.ifBlank { existing.unitForm },
            brand = csv.brand.ifBlank { existing.brand },
            reason = "CSV Import - Replaced Item"
        )
    }

    private fun applyAddQuantityCsvItem(disc: CsvProductDiscrepancy) {
        val csv = disc.csvItem
        val existing = disc.existingItem
        val combinedQty = existing.stockQuantity + csv.stockQuantity
        addOrUpdateInventory(
            id = existing.id,
            name = existing.name,
            dosage = if (existing.dosage.isNotBlank() && existing.dosage != "N/A") existing.dosage else csv.dosage,
            currentStock = combinedQty,
            minStock = if (csv.threshold > 0) csv.threshold else existing.minRequiredStock,
            category = existing.category.ifBlank { csv.category },
            price = if (csv.price > 0) csv.price else existing.price,
            expiryDate = if (csv.expiryDate > 0) csv.expiryDate else existing.expiryDate,
            batchNumber = csv.batchNumber.ifBlank { existing.batchNumber },
            supplier = csv.supplier.ifBlank { existing.supplier },
            unitForm = csv.unitForm.ifBlank { existing.unitForm },
            brand = csv.brand.ifBlank { existing.brand },
            reason = "CSV Import - Added Stock (+${csv.stockQuantity})"
        )
    }

    private fun finishCsvImportSession(finalState: CsvImportSessionState) {
        _csvImportSession.value = null
        val msg = "CSV Import Completed!\nNew Added: ${finalState.directImportedCount} | Replaced: ${finalState.replacedCount} | Quantities Added: ${finalState.addedCount} | Skipped: ${finalState.skippedCount}"
        Toast.makeText(getApplication(), msg, Toast.LENGTH_LONG).show()
    }

    fun dismissCsvImportSession() {
        _csvImportSession.value = null
    }

    fun generateUniqueId(): Int {
        return java.util.UUID.randomUUID().hashCode() and 0x7FFFFFFF
    }

    fun handleUserLoggedOut() {
        viewModelScope.launch {
            try {
                deviceRepository.handleUserLoggedOut()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun isCurrentUserAdmin(): Boolean {
        val role = _currentPharmacistRole.value?.lowercase() ?: ""
        return role == "admin" || role == "superadmin" || role == "systemadmin" || role == "system administrator" || role == "branch manager"
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

    private val _canonicalProductCatalog = kotlinx.coroutines.flow.MutableStateFlow<List<com.example.data.CanonicalProduct>>(emptyList())
    val canonicalProductCatalog: StateFlow<List<com.example.data.CanonicalProduct>> = _canonicalProductCatalog.asStateFlow()

    val defaultCanonicalProducts = listOf(
        com.example.data.CanonicalProduct(id = "CP-001", name = "Aldomet", dosage = "250mg", category = "Antihypertensive", unitForm = "Tablet", brand = "Aspen", defaultPrice = 2000.0, nafdacRegNumber = "A4-0123"),
        com.example.data.CanonicalProduct(id = "CP-002", name = "Amartem Soft Gel", dosage = "20/120mg", category = "Antimalarial", unitForm = "Capsule", brand = "Fidson", defaultPrice = 1500.0, nafdacRegNumber = "04-2391"),
        com.example.data.CanonicalProduct(id = "CP-003", name = "Glucophage", dosage = "500mg", category = "Antidiabetic", unitForm = "Tablet", brand = "Merck", defaultPrice = 1200.0, nafdacRegNumber = "04-5112"),
        com.example.data.CanonicalProduct(id = "CP-004", name = "Ventolin Inhaler", dosage = "100mcg", category = "Bronchodilator", unitForm = "Inhaler", brand = "GSK", defaultPrice = 3500.0, nafdacRegNumber = "04-0988"),
        com.example.data.CanonicalProduct(id = "CP-005", name = "Panadol Optizorb", dosage = "500mg", category = "Analgesic", unitForm = "Tablet", brand = "Panadol", defaultPrice = 800.0, nafdacRegNumber = "A4-8821"),
        com.example.data.CanonicalProduct(id = "CP-006", name = "Co-Diovan", dosage = "80/12.5mg", category = "Antihypertensive", unitForm = "Tablet", brand = "Novartis", defaultPrice = 6500.0, nafdacRegNumber = "04-1029"),
        com.example.data.CanonicalProduct(id = "CP-007", name = "Augmentin", dosage = "625mg", category = "Antibiotic", unitForm = "Tablet", brand = "GSK", defaultPrice = 5000.0, nafdacRegNumber = "04-4410"),
        com.example.data.CanonicalProduct(id = "CP-008", name = "Lipitor", dosage = "20mg", category = "Lipid-lowering agent", unitForm = "Tablet", brand = "Pfizer", defaultPrice = 4800.0, nafdacRegNumber = "04-3321"),
        com.example.data.CanonicalProduct(id = "CP-009", name = "Gaviscon Double Action", dosage = "150ml", category = "Antacid", unitForm = "Suspension", brand = "Reckitt", defaultPrice = 2400.0, nafdacRegNumber = "04-9011"),
        com.example.data.CanonicalProduct(id = "CP-010", name = "Claritin (Loratadine)", dosage = "10mg", category = "Antihistamine", unitForm = "Tablet", brand = "Bayer", defaultPrice = 1000.0, nafdacRegNumber = "04-1188"),
        com.example.data.CanonicalProduct(id = "CP-011", name = "Amlodipine", dosage = "5mg", category = "Antihypertensive", unitForm = "Tablet", brand = "Pfizer", defaultPrice = 1200.0, nafdacRegNumber = "04-5011"),
        com.example.data.CanonicalProduct(id = "CP-012", name = "Metformin XR", dosage = "1000mg", category = "Antidiabetic", unitForm = "Tablet", brand = "Sanofi", defaultPrice = 2200.0, nafdacRegNumber = "04-7723"),
        com.example.data.CanonicalProduct(id = "CP-013", name = "Omeprazole", dosage = "20mg", category = "Gastrointestinal", unitForm = "Capsule", brand = "AstraZeneca", defaultPrice = 1500.0, nafdacRegNumber = "04-3019"),
        com.example.data.CanonicalProduct(id = "CP-014", name = "Ciprofloxacin", dosage = "500mg", category = "Antibiotic", unitForm = "Tablet", brand = "Bayer", defaultPrice = 1800.0, nafdacRegNumber = "04-8832"),
        com.example.data.CanonicalProduct(id = "CP-015", name = "Paracetamol Extra", dosage = "500mg/65mg", category = "Analgesic", unitForm = "Tablet", brand = "Emzor", defaultPrice = 600.0, nafdacRegNumber = "A4-1002"),
        com.example.data.CanonicalProduct(id = "CP-016", name = "Ibuprofen Softgels", dosage = "400mg", category = "Analgesic", unitForm = "Capsule", brand = "May & Baker", defaultPrice = 900.0, nafdacRegNumber = "A4-2091"),
        com.example.data.CanonicalProduct(id = "CP-017", name = "Azithromycin", dosage = "500mg", category = "Antibiotic", unitForm = "Tablet", brand = "Pfizer", defaultPrice = 3200.0, nafdacRegNumber = "04-6102"),
        com.example.data.CanonicalProduct(id = "CP-018", name = "Coartem", dosage = "80/480mg", category = "Antimalarial", unitForm = "Tablet", brand = "Novartis", defaultPrice = 2800.0, nafdacRegNumber = "04-9912"),
        com.example.data.CanonicalProduct(id = "CP-019", name = "Lonart-DS", dosage = "80/480mg", category = "Antimalarial", unitForm = "Tablet", brand = "Bliss GVS", defaultPrice = 2500.0, nafdacRegNumber = "04-7781"),
        com.example.data.CanonicalProduct(id = "CP-020", name = "Salbutamol Syrup", dosage = "2mg/5ml", category = "Bronchodilator", unitForm = "Syrup", brand = "GSK", defaultPrice = 1100.0, nafdacRegNumber = "04-1044"),
        com.example.data.CanonicalProduct(id = "CP-021", name = "Zinc Sulfate Tablets", dosage = "20mg", category = "Vitamins/Supplements", unitForm = "Tablet", brand = "Fidson", defaultPrice = 700.0, nafdacRegNumber = "A4-3320"),
        com.example.data.CanonicalProduct(id = "CP-022", name = "ORS Sachet", dosage = "20.5g", category = "Gastrointestinal", unitForm = "Powder", brand = "Emzor", defaultPrice = 300.0, nafdacRegNumber = "A4-0012"),
        com.example.data.CanonicalProduct(id = "CP-023", name = "Vitamin C Effervescent", dosage = "1000mg", category = "Vitamins/Supplements", unitForm = "Tablet", brand = "Redoxon", defaultPrice = 1800.0, nafdacRegNumber = "04-8811"),
        com.example.data.CanonicalProduct(id = "CP-024", name = "Losartan Potassium", dosage = "50mg", category = "Antihypertensive", unitForm = "Tablet", brand = "Merck", defaultPrice = 2100.0, nafdacRegNumber = "04-6620"),
        com.example.data.CanonicalProduct(id = "CP-025", name = "Atorvastatin", dosage = "10mg", category = "Lipid-lowering agent", unitForm = "Tablet", brand = "Pfizer", defaultPrice = 3000.0, nafdacRegNumber = "04-4421"),
        com.example.data.CanonicalProduct(id = "CP-026", name = "Metoprolol Succinate", dosage = "50mg", category = "Antihypertensive", unitForm = "Tablet", brand = "AstraZeneca", defaultPrice = 3400.0, nafdacRegNumber = "04-1299"),
        com.example.data.CanonicalProduct(id = "CP-027", name = "Levofloxacin", dosage = "500mg", category = "Antibiotic", unitForm = "Tablet", brand = "Sanofi", defaultPrice = 3800.0, nafdacRegNumber = "04-8120"),
        com.example.data.CanonicalProduct(id = "CP-028", name = "Diclofenac Sodium SR", dosage = "100mg", category = "Analgesic", unitForm = "Tablet", brand = "Novartis", defaultPrice = 1400.0, nafdacRegNumber = "04-2201"),
        com.example.data.CanonicalProduct(id = "CP-029", name = "Prednisolone", dosage = "5mg", category = "Corticosteroid", unitForm = "Tablet", brand = "Pfizer", defaultPrice = 950.0, nafdacRegNumber = "04-3310"),
        com.example.data.CanonicalProduct(id = "CP-030", name = "Cetirizine Hydrochloride", dosage = "10mg", category = "Antihistamine", unitForm = "Tablet", brand = "UCB", defaultPrice = 1100.0, nafdacRegNumber = "04-7700")
    )

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
        deviceId = deviceRepository.getDeviceId()
        deviceRepository.retrieveInitialToken(viewModelScope)

        val cachedBId = prefs.getString("cached_branch_id", null)
        if (!cachedBId.isNullOrEmpty()) {
            setupBranchRealtimeSync(cachedBId)
        }
        viewModelScope.launch {
            kotlinx.coroutines.delay(1500)
            _isProfileLoading.value = false
        }

        // Connection Monitoring Network Callback
        try {
            val connectivityManager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            _isOnline.value = isNetworkAvailable(application)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                connectivityManager.registerDefaultNetworkCallback(object : android.net.ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: android.net.Network) {
                        val online = isNetworkAvailable(application)
                        _isOnline.value = online
                        if (online) {
                            triggerImmediateSync()
                            viewModelScope.launch {
                                try {
                                    deviceRepository.syncPendingRegistration()
                                } catch (e: Exception) {
                                    // Fail-safe
                                }
                            }
                        }
                    }
                    override fun onLost(network: android.net.Network) {
                        _isOnline.value = isNetworkAvailable(application)
                    }
                })
            } else {
                val networkRequest = android.net.NetworkRequest.Builder()
                    .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                connectivityManager.registerNetworkCallback(networkRequest, object : android.net.ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: android.net.Network) {
                        val online = isNetworkAvailable(application)
                        _isOnline.value = online
                        if (online) {
                            triggerImmediateSync()
                            viewModelScope.launch {
                                try {
                                    deviceRepository.syncPendingRegistration()
                                } catch (e: Exception) {
                                    // Fail-safe
                                }
                            }
                        }
                    }
                    override fun onLost(network: android.net.Network) {
                        _isOnline.value = isNetworkAvailable(application)
                    }
                })
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Start repository flows for dynamic feature flags, nodes, requests, branches, and canonical products
        viewModelScope.launch {
            repository.observeDeviceConfig(deviceId)
                .catch { e -> android.util.Log.w("PharmacyViewModel", "observeDeviceConfig failed: ${e.localizedMessage}") }
                .collect { map ->
                    if (map != null) {
                        val aiContent = map["aiContentEnabled"] as? Boolean ?: true
                        val carefluxAi = map["carefluxAiEnabled"] as? Boolean ?: true
                        val suspended = map["isSuspended"] as? Boolean ?: false
                        _isAiContentEnabled.value = aiContent
                        _isCarefluxAiEnabled.value = carefluxAi
                        _isSuspended.value = suspended
                        prefs.edit().putBoolean("user_suspended", suspended).apply()

                        val customGemini = map["customGeminiApiKey"] as? String
                        if (!customGemini.isNullOrBlank()) {
                            prefs.edit().putString("custom_api_key", customGemini.trim()).apply()
                        }
                    }
                }
        }

        viewModelScope.launch {
            repository.observeAllPharmacists()
                .catch { e -> android.util.Log.w("PharmacyViewModel", "observeAllPharmacists failed: ${e.localizedMessage}") }
                .collect { list ->
                    _registeredNodes.value = list
                }
        }

        viewModelScope.launch {
            repository.observeKeyCreationRequests()
                .catch { e -> android.util.Log.w("PharmacyViewModel", "observeKeyCreationRequests failed: ${e.localizedMessage}") }
                .collect { list ->
                    _keyRequests.value = list
                }
        }

        viewModelScope.launch {
            repository.observeAllBranches()
                .catch { e -> android.util.Log.w("PharmacyViewModel", "observeAllBranches failed: ${e.localizedMessage}") }
                .collect { list ->
                    _allBranches.value = list
                }
        }

        _canonicalProductCatalog.value = defaultCanonicalProducts
        viewModelScope.launch {
            repository.observeCanonicalProducts()
                .catch { e -> android.util.Log.w("PharmacyViewModel", "observeCanonicalProducts failed: ${e.localizedMessage}") }
                .collect { list ->
                    val remoteList = list.mapNotNull { itemMap ->
                        try {
                            val name = itemMap["name"] as? String ?: ""
                            val dosage = itemMap["dosage"] as? String ?: ""
                            if (name.isBlank()) return@mapNotNull null
                            com.example.data.CanonicalProduct(
                                id = itemMap["id"] as? String ?: "",
                                name = name,
                                dosage = dosage,
                                category = itemMap["category"] as? String ?: "General",
                                unitForm = itemMap["unitForm"] as? String ?: "Tablet",
                                brand = itemMap["brand"] as? String ?: "Generic",
                                defaultPrice = (itemMap["defaultPrice"] as? Number)?.toDouble() ?: 0.0,
                                minStockThreshold = (itemMap["minStockThreshold"] as? Number)?.toInt() ?: 10,
                                defaultSupplier = itemMap["defaultSupplier"] as? String ?: "Standard Wholesaler",
                                isCustomAdded = itemMap["isCustomAdded"] as? Boolean ?: true,
                                addedAt = (itemMap["addedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                            )
                        } catch (ex: Exception) {
                            null
                        }
                    }

                    val merged = defaultCanonicalProducts.toMutableList()
                    remoteList.forEach { remoteItem ->
                        val existingIndex = merged.indexOfFirst {
                            it.name.equals(remoteItem.name, ignoreCase = true) &&
                            it.dosage.equals(remoteItem.dosage, ignoreCase = true)
                        }
                        if (existingIndex >= 0) {
                            merged[existingIndex] = remoteItem
                        } else {
                            merged.add(0, remoteItem)
                        }
                    }
                    _canonicalProductCatalog.value = merged
                }
        }

        loadTemplates()

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

        receipts = @OptIn(ExperimentalCoroutinesApi::class) _currentPharmacistBranchId.flatMapLatest { branch ->
            val b = branch ?: ""
            if (b.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList()) else repository.getReceiptsForBranch(b)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        operationTasks = @OptIn(ExperimentalCoroutinesApi::class) _currentPharmacistBranchId.flatMapLatest { branch ->
            val b = branch ?: ""
            if (b.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList()) else repository.getOperationTasksForBranch(b)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        inventoryItems = @OptIn(ExperimentalCoroutinesApi::class) _currentPharmacistBranchId.flatMapLatest { branch ->
            val b = branch ?: ""
            if (b.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList()) else repository.getInventoryForBranch(b)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        lowStockItems = @OptIn(ExperimentalCoroutinesApi::class) _currentPharmacistBranchId.flatMapLatest { branch ->
            val b = branch ?: ""
            if (b.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList()) else repository.getLowStockItemsForBranch(b)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        val cutoff14Days = System.currentTimeMillis() - (14L * 24 * 60 * 60 * 1000L)
        reconciled14DaysRatio = inventoryItems.map { items ->
            if (items.isEmpty()) 1.0f
            else {
                val cutoff = System.currentTimeMillis() - (14L * 24 * 60 * 60 * 1000L)
                val reconciled = items.count { it.lastReconciledAt >= cutoff }
                reconciled.toFloat() / items.size.toFloat()
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

        unreconciled14DaysCount = inventoryItems.map { items ->
            val cutoff = System.currentTimeMillis() - (14L * 24 * 60 * 60 * 1000L)
            items.count { it.lastReconciledAt < cutoff }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

        overdueReconciliationItems = inventoryItems.map { items ->
            val cutoff = System.currentTimeMillis() - (14L * 24 * 60 * 60 * 1000L)
            items.filter { it.lastReconciledAt < cutoff }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

        customers = @OptIn(ExperimentalCoroutinesApi::class) _currentPharmacistBranchId.flatMapLatest { branch ->
            val b = branch ?: ""
            if (b.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList()) else repository.getCustomersForBranch(b)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        customerMedications = @OptIn(ExperimentalCoroutinesApi::class) _currentPharmacistBranchId.flatMapLatest { branch ->
            val b = branch ?: ""
            if (b.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList()) else repository.getCustomerMedicationsForBranch(b)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        clinicalInterventions = @OptIn(ExperimentalCoroutinesApi::class) _currentPharmacistBranchId.flatMapLatest { branch ->
            val b = branch ?: ""
            if (b.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList()) else repository.getClinicalInterventionsForBranch(b)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        medicationSales = @OptIn(ExperimentalCoroutinesApi::class) _currentPharmacistBranchId.flatMapLatest { branch ->
            val b = branch ?: ""
            if (b.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList()) else repository.getMedicationSalesForBranch(b)
        }.stateIn(
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

        expiryAlertClaims = repository.allExpiryAlertClaims.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        organization = repository.organization.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

        allUsers = repository.allUsers.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        inventoryLedgerEntries = @OptIn(ExperimentalCoroutinesApi::class) _currentPharmacistBranchId.flatMapLatest { branch ->
            val b = branch ?: ""
            if (b.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList()) else repository.getInventoryLedgerEntriesForBranch(b)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Real-time synchronization via repository flows
        viewModelScope.launch {
            repository.observeExpiryRescueListings()
                .catch { e -> android.util.Log.w("PharmacyViewModel", "observeExpiryRescueListings failed: ${e.localizedMessage}") }
                .collect { list ->
                try {
                    val activeFirestoreIds = list.mapNotNull { it["firestoreId"] as? String }.toSet()
                    
                    // Step 1: Prune local listings that are retracted/removed
                    val localListings = rescueListings.value
                    for (local in localListings) {
                        if (local.firestoreId.isNotEmpty() && !activeFirestoreIds.contains(local.firestoreId)) {
                            if (System.currentTimeMillis() - local.listedAt > 30000L) {
                                repository.deleteRescueListingByFirestoreId(local.firestoreId)
                            }
                        }
                    }

                    // Step 2: Insert or update newly fetched/updated documents
                    for (itemMap in list) {
                        val remoteId = itemMap["firestoreId"] as? String ?: itemMap["id"] as? String ?: continue
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

        viewModelScope.launch {
            repository.observeAdminAuditLogs()
                .catch { e -> android.util.Log.w("PharmacyViewModel", "observeAdminAuditLogs failed: ${e.localizedMessage}") }
                .collect { list ->
                for (data in list) {
                    val act = data["actionPerformed"] as? String ?: ""
                    val admin = data["adminName"] as? String ?: ""
                    val timestampVal = (data["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
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

        // Auto-refresh automated operational & expiry verification tasks on startup
        viewModelScope.launch {
            kotlinx.coroutines.delay(1500)
            dispatchAutomatedVerificationTasks()
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
        val now = System.currentTimeMillis()
        val itemBranchId: String
        val itemUserUid: String
        if (item.id == 0) {
            itemBranchId = item.branchId.ifBlank { getActiveBranchId() }
            itemUserUid = item.originatingUserUid.ifBlank { getCurrentUserUid() }
            if (itemBranchId.isBlank() || itemUserUid.isBlank()) {
                android.util.Log.e("PharmacyViewModel", "Cannot create inventory item: missing lineage. Failing closed.")
                return 0
            }
            // Tenant authorization verification: non-admins cannot create items for arbitrary external branches
            val activeBranch = getActiveBranchId()
            if (!isCurrentUserAdmin() && activeBranch.isNotBlank() && itemBranchId != activeBranch) {
                android.util.Log.e("PharmacyViewModel", "Tenant authorization breach attempt: User in branch $activeBranch tried creating inventory in branch $itemBranchId. Failing closed.")
                return 0
            }
        } else {
            if (item.branchId.isBlank() || item.originatingUserUid.isBlank()) {
                android.util.Log.e("PharmacyViewModel", "Cannot update inventory item ${item.id}: missing lineage. Failing closed.")
                return 0
            }
            itemBranchId = item.branchId
            itemUserUid = item.originatingUserUid
        }
        val updated = item.copy(
            lastUpdated = now,
            branchId = itemBranchId,
            originatingUserUid = itemUserUid
        )
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
            "lastUpdated" to updated.lastUpdated,
            "lastReconciledAt" to updated.lastReconciledAt,
            "branchId" to itemBranchId,
            "originatingUserUid" to itemUserUid,
            "imageUri" to (updated.imageUri ?: "")
        )
        val outboxRecord = com.example.data.sync.SyncOutboxRecord(
            branchId = itemBranchId,
            entityType = "INVENTORY",
            entityId = if (updated.id != 0) updated.id.toString() else "0",
            operationType = "UPSERT",
            payloadJson = org.json.JSONObject(map).toString(),
            originatingUserUid = itemUserUid
        )
        val finalId = if (updated.id == 0) {
            repository.insertInventoryItemAndOutbox(updated, outboxRecord).toInt()
        } else {
            repository.updateInventoryItemAndOutbox(updated, outboxRecord)
            updated.id
        }
        val finalItem = updated.copy(id = finalId)
        syncEntityToFirestore("branch_inventory", finalItem.id.toString(), map)
        return finalItem.id
    }

    fun recordDoubleEntryLedger(
        itemId: Int,
        itemName: String,
        batchNumber: String = "",
        transactionType: String,
        debitAccount: String,
        creditAccount: String,
        quantity: Int,
        unitPrice: Double = 0.0,
        totalValue: Double = 0.0,
        referenceId: String = "",
        notes: String = ""
    ) {
        viewModelScope.launch {
            val actor = _currentPharmacistName.value ?: "Pharmacist"
            val absQty = kotlin.math.abs(quantity)
            val computedValue = if (totalValue > 0.0) totalValue else (absQty * unitPrice)
            val entry = com.example.data.InventoryLedgerEntry(
                inventoryItemId = itemId,
                itemName = itemName,
                batchNumber = batchNumber,
                transactionType = transactionType,
                debitAccount = debitAccount,
                creditAccount = creditAccount,
                quantity = absQty,
                unitPrice = unitPrice,
                totalValue = computedValue,
                referenceId = referenceId,
                actorName = actor,
                notes = notes,
                timestamp = System.currentTimeMillis()
            )
            repository.insertInventoryLedgerEntry(entry)
        }
    }

    fun addCustomCanonicalProduct(
        name: String,
        dosage: String,
        category: String,
        unitForm: String = "Tablet",
        brand: String = "Generic",
        defaultPrice: Double = 0.0,
        minStockThreshold: Int = 10,
        supplier: String = "Standard Wholesaler",
        initialQty: Int = 20,
        batchNumber: String = "",
        onFinished: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val cleanName = name.trim()
        val cleanDosage = dosage.trim()
        val cleanCategory = category.trim().ifBlank { "General" }
        val newId = "CP-CUSTOM-${System.currentTimeMillis()}"

        val newCanonicalProduct = com.example.data.CanonicalProduct(
            id = newId,
            name = cleanName,
            dosage = cleanDosage,
            category = cleanCategory,
            unitForm = unitForm.ifBlank { "Tablet" },
            brand = brand.ifBlank { "Generic" },
            defaultPrice = defaultPrice,
            minStockThreshold = minStockThreshold,
            defaultSupplier = supplier.ifBlank { "Standard Wholesaler" },
            isCustomAdded = true,
            addedAt = System.currentTimeMillis()
        )

        // 1. Add to local memory flow
        val currentList = _canonicalProductCatalog.value.toMutableList()
        val existingIdx = currentList.indexOfFirst {
            it.name.equals(cleanName, ignoreCase = true) && it.dosage.equals(cleanDosage, ignoreCase = true)
        }
        if (existingIdx >= 0) {
            currentList[existingIdx] = newCanonicalProduct
        } else {
            currentList.add(0, newCanonicalProduct)
        }
        _canonicalProductCatalog.value = currentList

        // 2. Sync to remote master collection "canonical_products"
        viewModelScope.launch {
            val productMap = mapOf(
                "id" to newId,
                "name" to cleanName,
                "dosage" to cleanDosage,
                "category" to cleanCategory,
                "unitForm" to unitForm,
                "brand" to brand,
                "defaultPrice" to defaultPrice,
                "minStockThreshold" to minStockThreshold,
                "defaultSupplier" to supplier,
                "isCustomAdded" to true,
                "addedAt" to System.currentTimeMillis()
            )
            repository.upsertRemoteDocument("canonical_products", newId, productMap)
        }

        // 3. Immediately insert into active branch inventory
        addOrUpdateInventory(
            name = cleanName,
            dosage = cleanDosage,
            currentStock = initialQty,
            minStock = minStockThreshold,
            category = cleanCategory,
            price = defaultPrice,
            unitForm = unitForm,
            brand = brand,
            supplier = supplier,
            batchNumber = batchNumber.ifBlank { "BAT-${(10000..99999).random()}" },
            reason = "Custom Product Creation & Catalog Registration"
        )

        logAdminAction(
            admin = _currentPharmacistName.value ?: "Pharmacist",
            action = "ADD_CUSTOM_CANONICAL_PRODUCT",
            nodeId = deviceId,
            nodeModel = android.os.Build.MODEL,
            reason = "Added custom product '$cleanName $cleanDosage' to Universal Canonical Product Catalog and Active Branch Inventory."
        )

        onFinished(true, "Successfully added '$cleanName' to Universal Catalog & Branch Inventory!")
    }

    fun addProductsFromCanonicalCatalog(
        selectedItems: List<Triple<com.example.data.CanonicalProduct, Int, Double>>,
        defaultBatchNumber: String = "",
        defaultSupplier: String = "",
        onFinished: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        if (selectedItems.isEmpty()) {
            onFinished(false, "No items selected to import.")
            return
        }

        var addedCount = 0
        selectedItems.forEach { (canonProduct, qty, priceOverride) ->
            val itemPrice = if (priceOverride > 0.0) priceOverride else canonProduct.defaultPrice
            val batch = defaultBatchNumber.ifBlank { "BAT-${(10000..99999).random()}" }
            val supp = defaultSupplier.ifBlank { canonProduct.defaultSupplier }

            addOrUpdateInventory(
                name = canonProduct.name,
                dosage = canonProduct.dosage,
                currentStock = qty,
                minStock = canonProduct.minStockThreshold,
                category = canonProduct.category,
                price = itemPrice,
                unitForm = canonProduct.unitForm,
                brand = canonProduct.brand,
                supplier = supp,
                batchNumber = batch,
                reason = "Import from Universal Canonical Catalog"
            )
            addedCount++
        }

        logAdminAction(
            admin = _currentPharmacistName.value ?: "Pharmacist",
            action = "IMPORT_CANONICAL_CATALOG",
            nodeId = deviceId,
            nodeModel = android.os.Build.MODEL,
            reason = "Imported $addedCount product(s) from Universal Canonical Catalog into active branch inventory."
        )

        onFinished(true, "Successfully added $addedCount product(s) to your branch inventory!")
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
        reason: String = "Manual Adjustment",
        isFastMoving: Boolean? = null
    ) {
        viewModelScope.launch {
            var actualId = id
            var previousExisting: com.example.data.InventoryItem? = null
            
            if (actualId == 0) {
                // Find all existing items with the same name
                val existingItems = repository.getInventoryItemsByName(name.trim())
                
                // Strictly match dosage, unitForm, and batchNumber to treat each variant as an individual entity
                previousExisting = existingItems.find { 
                    it.dosage.trim().equals(dosage.trim(), ignoreCase = true) &&
                    (unitForm.isBlank() || it.unitForm.trim().equals(unitForm.trim(), ignoreCase = true)) &&
                    it.batchNumber.trim().equals(batchNumber.trim(), ignoreCase = true)
                }

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
            val finalFastMoving = isFastMoving ?: previousExisting?.isFastMoving ?: false

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
                brand = itemBrand,
                isFastMoving = finalFastMoving,
                branchId = previousExisting?.branchId ?: "",
                originatingUserUid = previousExisting?.originatingUserUid ?: ""
            )
            val finalSavedId = saveAndSyncInventoryItemDirectly(item)

            // Log stock adjustments for manual audits (exclude checkout reductions)
            if (delta != 0 && !updateStockStats) {
                val isManager = _currentPharmacistRole.value == "Branch Manager" || isCurrentUserAdmin()
                val userName = _currentPharmacistName.value ?: "Staff Pharmacist"
                val userRole = _currentPharmacistRole.value ?: "Pharmacist"
                val userUid = authRepository.getCurrentUser()?.uid ?: getCurrentUserUid()
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

                // Record Double-Entry Inventory Ledger
                val currentBranchName = _currentPharmacistBranchName.value ?: "Careflux"
                if (delta > 0) {
                    recordDoubleEntryLedger(
                        itemId = finalSavedId,
                        itemName = item.name,
                        batchNumber = item.batchNumber,
                        transactionType = if (previousExisting == null) "INITIAL_ACQUISITION" else "PURCHASE",
                        debitAccount = "BRANCH:$currentBranchName",
                        creditAccount = if (item.supplier.isNotBlank()) "SUPPLIER:${item.supplier}" else "SUPPLIER:Acquisitions",
                        quantity = delta,
                        unitPrice = item.price,
                        referenceId = "INTAKE_${finalSavedId}_${System.currentTimeMillis()}",
                        notes = finalReason
                    )
                } else if (delta < 0) {
                    recordDoubleEntryLedger(
                        itemId = finalSavedId,
                        itemName = item.name,
                        batchNumber = item.batchNumber,
                        transactionType = "STOCK_ADJUSTMENT",
                        debitAccount = "ADJUSTMENT:$finalReason",
                        creditAccount = "BRANCH:$currentBranchName",
                        quantity = kotlin.math.abs(delta),
                        unitPrice = item.price,
                        referenceId = "ADJ_${finalSavedId}_${System.currentTimeMillis()}",
                        notes = finalReason
                    )
                }
                
                try {
                    repository.addRemoteDocument("branch_audit_logs", auditMap)
                    
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
            if (item.branchId.isBlank() || item.originatingUserUid.isBlank()) {
                android.util.Log.e("PharmacyViewModel", "Cannot delete inventory item ${item.id}: missing lineage. Failing closed.")
                return@launch
            }
            val outboxRecord = com.example.data.sync.SyncOutboxRecord(
                branchId = item.branchId,
                entityType = "INVENTORY",
                entityId = item.id.toString(),
                operationType = "DELETE",
                payloadJson = "{}",
                originatingUserUid = item.originatingUserUid
            )
            repository.deleteInventoryItemAndOutbox(item, outboxRecord)

            val isManager = _currentPharmacistRole.value == "Branch Manager" || isCurrentUserAdmin()
            val userName = _currentPharmacistName.value ?: "Staff Pharmacist"
            val userRole = _currentPharmacistRole.value ?: "Pharmacist"
            val userUid = authRepository.getCurrentUser()?.uid ?: getCurrentUserUid()
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
                repository.addRemoteDocument("branch_audit_logs", auditMap)
                
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
                    saveAndSyncInventoryItemDirectly(updatedItem)
                    
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
                    "${it.name.trim().lowercase()}_${it.dosage.trim().lowercase()}_${it.unitForm.trim().lowercase()}_${it.batchNumber.trim().lowercase()}" 
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
            val now = System.currentTimeMillis()
            if (delta != 0) {
                saveAndSyncInventoryItemDirectly(item.copy(stockQuantity = newQuantity, lastReconciledAt = now, lastUpdated = now))
                
                val isManager = _currentPharmacistRole.value == "Branch Manager" || isCurrentUserAdmin()
                val userName = _currentPharmacistName.value ?: "Staff Pharmacist"
                val userRole = _currentPharmacistRole.value ?: "Pharmacist"
                val userUid = authRepository.getCurrentUser()?.uid ?: "LocalNode"
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
                    repository.addRemoteDocument("branch_audit_logs", auditMap)
                    
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

    fun reconcileItemStock(item: InventoryItem, verifiedQuantity: Int, reason: String = "14-Day Rolling Cycle Count Verification") {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val oldQty = item.stockQuantity
            val delta = verifiedQuantity - oldQty
            val updated = item.copy(
                stockQuantity = verifiedQuantity,
                lastReconciledAt = now,
                lastUpdated = now
            )
            saveAndSyncInventoryItemDirectly(updated)

            val userName = _currentPharmacistName.value ?: "Staff Pharmacist"
            val userRole = _currentPharmacistRole.value ?: "Pharmacist"
            val userUid = authRepository.getCurrentUser()?.uid ?: "LocalNode"
            val branchId = _currentPharmacistBranchId.value ?: "Self"

            val auditMap = hashMapOf(
                "branchId" to branchId,
                "uid" to userUid,
                "displayName" to userName,
                "role" to userRole,
                "action" to "CYCLE_COUNT_RECONCILIATION",
                "timestamp" to now,
                "details" to "Reconciled stock for ${item.name} (${item.dosage}) from $oldQty to $verifiedQuantity. Discrepancy: ${if (delta > 0) "+$delta" else delta}. Reason: $reason",
                "affectedId" to item.id.toString(),
                "medicationId" to item.id,
                "previousQty" to oldQty,
                "newQty" to verifiedQuantity,
                "verifiedBy" to "$userName ($userRole)"
            )

            try {
                repository.addRemoteDocument("branch_audit_logs", auditMap)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            logAuditTrail(
                action = "CYCLE_COUNT",
                details = "Physical count reconciled for ${item.name} (${item.dosage}): $oldQty -> $verifiedQuantity. Reason: $reason",
                affectedId = item.id.toString()
            )

            val currentBranchName = _currentPharmacistBranchName.value ?: "Careflux"
            if (delta > 0) {
                recordDoubleEntryLedger(
                    itemId = item.id,
                    itemName = item.name,
                    batchNumber = item.batchNumber,
                    transactionType = "RECONCILIATION_GAIN",
                    debitAccount = "BRANCH:$currentBranchName",
                    creditAccount = "SYSTEM:Reconciliation_Surplus",
                    quantity = delta,
                    unitPrice = item.price,
                    referenceId = "RECON_${item.id}_$now",
                    notes = reason
                )
            } else if (delta < 0) {
                recordDoubleEntryLedger(
                    itemId = item.id,
                    itemName = item.name,
                    batchNumber = item.batchNumber,
                    transactionType = "RECONCILIATION_LOSS",
                    debitAccount = "EXPENSE:Reconciliation_Discrepancy",
                    creditAccount = "BRANCH:$currentBranchName",
                    quantity = kotlin.math.abs(delta),
                    unitPrice = item.price,
                    referenceId = "RECON_${item.id}_$now",
                    notes = reason
                )
            }
        }
    }

    fun verifyStockAdjustment(logId: String) {
        val userName = _currentPharmacistName.value ?: "Staff Pharmacist"
        val userRole = _currentPharmacistRole.value ?: "Pharmacist"
        viewModelScope.launch {
            try {
                repository.upsertRemoteDocument(
                    "branch_audit_logs",
                    logId,
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

    fun generatePatientFirstAutomaticAlerts() {
        viewModelScope.launch {
            val allCustomers = customers.value
            val allMeds = customerMedications.value
            val lowStock = lowStockItems.value
            val currentAlerts = customerAlerts.value

            if (allCustomers.isEmpty()) return@launch

            // 1. STOCK SHORTAGE -> AFFECTED CUSTOMERS
            lowStock.forEach { stockItem ->
                val affectedMeds = allMeds.filter { it.inventoryItemId == stockItem.id }

                affectedMeds.forEach { med ->
                    val customer = allCustomers.find { it.id == med.customerId }
                    if (customer != null) {
                        val alreadyHasAlert = currentAlerts.any {
                            it.customerName.equals(customer.name, ignoreCase = true) &&
                            it.status == "Pending" &&
                            it.alertType == "Stock Shortage Warning" &&
                            it.medicationName.contains(stockItem.name, ignoreCase = true)
                        }

                        if (!alreadyHasAlert) {
                            val alert = CustomerAlert(
                                customerName = customer.name,
                                phoneNumber = customer.phoneNumber,
                                medicationName = "Stock Low: ${stockItem.name} (${med.customDosage})",
                                alertType = "Stock Shortage Warning",
                                status = "Pending",
                                scheduledTime = "Immediate Outreach",
                                timestamp = System.currentTimeMillis()
                            )
                            repository.insertCustomerAlert(alert)
                        }
                    }
                }
            }

            // 2. SILENT RADAR SCAN (Inactivity Scan)
            val thresholdTime = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000) // 30 days
            allCustomers.forEach { customer ->
                val customerMedsList = allMeds.filter { it.customerId == customer.id }
                
                val lastActivityTime = if (customerMedsList.isEmpty()) {
                    0L
                } else {
                    customerMedsList.maxOfOrNull { it.nextRefillDate - (it.cycleDays * 24L * 60 * 60 * 1000) } ?: 0L
                }

                val isSilent = customerMedsList.isEmpty() || lastActivityTime < thresholdTime

                if (isSilent) {
                    val alreadyHasRadarAlert = currentAlerts.any {
                        it.customerName.equals(customer.name, ignoreCase = true) &&
                        it.status == "Pending" &&
                        it.alertType == "Silent Radar"
                    }

                    if (!alreadyHasRadarAlert) {
                        val alert = CustomerAlert(
                            customerName = customer.name,
                            phoneNumber = customer.phoneNumber,
                            medicationName = "Inactivity Radar: No recent contact/refills.",
                            alertType = "Silent Radar",
                            status = "Pending",
                            scheduledTime = "Schedule Check-in",
                            timestamp = System.currentTimeMillis()
                        )
                        repository.insertCustomerAlert(alert)
                    }
                }
            }

            // 3. PRIORITY PRESCRIPTION REFILL SCAN
            val nowMs = System.currentTimeMillis()
            val sevenDaysOutMs = nowMs + (7L * 24 * 60 * 60 * 1000)
            val windowLabel = com.example.util.RefillNotificationSchedule.getWindowBadgeLabel(nowMs)

            allMeds.forEach { med ->
                val customer = allCustomers.find { it.id == med.customerId && it.name.isNotBlank() }
                if (customer == null) {
                    // Delete orphaned medication not assigned to any valid customer
                    repository.deleteCustomerMedication(med)
                } else if (med.cycleDays > 0 && med.nextRefillDate <= sevenDaysOutMs) {
                    val alreadyHasRefillAlert = currentAlerts.any {
                        it.customerName.equals(customer.name, ignoreCase = true) &&
                        it.status == "Pending" &&
                        it.medicationName.contains(med.medicationName, ignoreCase = true) &&
                        (it.alertType == "Refill Reminder" || it.alertType == "Priority Refill Alert")
                    }

                    if (!alreadyHasRefillAlert) {
                        val alertMsg = com.example.util.RefillNotificationSchedule.formatRefillMessage(
                            patientName = customer.name,
                            medicationName = med.medicationName,
                            dosage = med.customDosage,
                            phone = customer.phoneNumber
                        )
                        val alert = CustomerAlert(
                            customerName = customer.name,
                            phoneNumber = customer.phoneNumber,
                            medicationName = alertMsg,
                            alertType = "Priority Refill Alert",
                            status = "Pending",
                            scheduledTime = windowLabel,
                            timestamp = nowMs
                        )
                        repository.insertCustomerAlert(alert)
                    }
                }
            }
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
                val updatedCustomer = com.example.ui.PatientIntelligenceParser.appendTextNote(
                    customer = customer,
                    medications = emptyList(),
                    noteText = userNotes.trim()
                )
                if (updatedCustomer.branchId.isBlank() || updatedCustomer.originatingUserUid.isBlank()) {
                    android.util.Log.e("PharmacyViewModel", "Cannot update customer ${updatedCustomer.id}: missing lineage. Failing closed.")
                    onComplete(false, "Customer lineage missing")
                    return@launch
                }
                val custMap = mapOf(
                    "id" to updatedCustomer.id,
                    "name" to updatedCustomer.name,
                    "phoneNumber" to updatedCustomer.phoneNumber,
                    "email" to updatedCustomer.email,
                    "notes" to updatedCustomer.notes,
                    "loyaltyPoints" to updatedCustomer.loyaltyPoints,
                    "refillStreak" to updatedCustomer.refillStreak,
                    "dateAdded" to updatedCustomer.dateAdded,
                    "age" to updatedCustomer.age,
                    "gender" to updatedCustomer.gender,
                    "state" to updatedCustomer.state,
                    "lga" to updatedCustomer.lga,
                    "city" to updatedCustomer.city,
                    "branchId" to updatedCustomer.branchId,
                    "originatingUserUid" to updatedCustomer.originatingUserUid
                )
                val outboxRecord = com.example.data.sync.SyncOutboxRecord(
                    branchId = updatedCustomer.branchId,
                    entityType = "CUSTOMER",
                    entityId = updatedCustomer.id.toString(),
                    operationType = "UPSERT",
                    payloadJson = org.json.JSONObject(custMap).toString(),
                    originatingUserUid = updatedCustomer.originatingUserUid
                )
                repository.updateCustomerAndOutbox(updatedCustomer, outboxRecord)
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
                        dateAdded = System.currentTimeMillis(),
                        branchId = getActiveBranchId(),
                        originatingUserUid = getCurrentUserUid()
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
            val branchId = getActiveBranchId()
            val userUid = getCurrentUserUid()
            val task = OperationTask(
                id = localId, 
                title = title, 
                description = description, 
                urgency = urgency, 
                category = category, 
                isCompleted = false,
                assignedToName = assignedToName,
                assignedToUid = assignedToUid,
                branchId = branchId,
                originatingUserUid = userUid
            )
            val map = mapOf(
                "id" to localId,
                "title" to title,
                "description" to description,
                "urgency" to urgency,
                "category" to category,
                "isCompleted" to false,
                "createdAt" to task.createdAt,
                "branchId" to branchId,
                "originatingUserUid" to userUid,
                "assignedToName" to (assignedToName ?: ""),
                "assignedToUid" to (assignedToUid ?: "")
            )
            val outbox = com.example.data.sync.SyncOutboxRecord(
                branchId = branchId,
                entityType = "TASK",
                entityId = localId.toString(),
                operationType = "UPSERT",
                payloadJson = org.json.JSONObject(map).toString(),
                originatingUserUid = userUid
            )
            repository.insertOperationTaskAndOutbox(task, outbox)
            
            if (!assignedToName.isNullOrBlank() && assignedToName != "All Staff") {
                showLocalNotification(
                    title = com.example.util.RefillNotificationSchedule.formatTaskAssignedTitle(title),
                    content = com.example.util.RefillNotificationSchedule.formatTaskAssignedMessage(_currentPharmacistName.value ?: "Manager", title),
                    targetTab = "branch_team",
                    targetSubTab = "ops_task_board",
                    targetTaskId = task.id.toLong()
                )
            }
            
            syncEntityToFirestore("branch_operation_tasks", localId.toString(), map)
            logAuditTrail(
                action = "DELEGATE_TASK",
                details = "Delegated task '$title' to ${assignedToName ?: "Staff"} inside category '$category' ($urgency urgency level).",
                affectedId = localId.toString()
            )
        }
    }

    private suspend fun updateOperationTaskAndOutboxHelper(task: OperationTask) {
        if (task.branchId.isBlank() || task.originatingUserUid.isBlank()) {
            android.util.Log.e("PharmacyViewModel", "Cannot update task ${task.id}: missing lineage. Failing closed.")
            return
        }
        val map: Map<String, Any> = mapOf(
            "id" to task.id,
            "title" to task.title,
            "description" to task.description,
            "urgency" to task.urgency,
            "category" to task.category,
            "isCompleted" to task.isCompleted,
            "createdAt" to task.createdAt,
            "branchId" to task.branchId,
            "originatingUserUid" to task.originatingUserUid,
            "assignedToName" to (task.assignedToName ?: ""),
            "assignedToUid" to (task.assignedToUid ?: ""),
            "verifiedBy" to (task.verifiedBy ?: ""),
            "verificationNotes" to (task.verificationNotes ?: ""),
            "verificationChannel" to (task.verificationChannel ?: ""),
            "verificationCustomerName" to (task.verificationCustomerName ?: ""),
            "verifiedAt" to (task.verifiedAt ?: 0L),
            "isApproved" to task.isApproved,
            "approvedBy" to (task.approvedBy ?: ""),
            "approvedAt" to (task.approvedAt ?: 0L),
            "approvalNotes" to (task.approvalNotes ?: "")
        )
        val outbox = com.example.data.sync.SyncOutboxRecord(
            branchId = task.branchId,
            entityType = "TASK",
            entityId = task.id.toString(),
            operationType = "UPSERT",
            payloadJson = org.json.JSONObject(map).toString(),
            originatingUserUid = task.originatingUserUid
        )
        repository.updateOperationTaskAndOutbox(task, outbox)
    }

    fun verifiablyCompleteOperationTask(
        task: OperationTask,
        notes: String,
        channel: String,
        patientName: String,
        countedQuantity: Int? = null,
        onFinished: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val updaterName = _currentPharmacistName.value ?: "Staff Pharmacist"
            val now = System.currentTimeMillis()
            val updated = task.copy(
                isCompleted = true,
                verifiedBy = updaterName,
                verificationNotes = notes.trim(),
                verificationChannel = channel,
                verificationCustomerName = patientName.trim(),
                verifiedAt = now
            )
            // 1. Instantly update Room database state and Outbox
            updateOperationTaskAndOutboxHelper(updated)
            if (_activeHighlightTaskId.value == task.id.toLong()) {
                _activeHighlightTaskId.value = null
            }

            // 2. Immediately notify UI so modal closes in <50ms
            onFinished(true, "Task compliance check passed.")

            // 3. Asynchronously process inventory reconciliation, Firestore sync, and audit trail in background queue
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    syncTaskCompletionWithInventoryReconciliation(updated, countedQuantity)
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
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
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
            updateOperationTaskAndOutboxHelper(updated)
            
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

    fun claimOperationTask(task: OperationTask, staffName: String) {
        _claimingTaskIds.update { it + task.id }
        viewModelScope.launch {
            try {
                var updatedDesc = task.description
                if (task.title.contains("Expiry", ignoreCase = true) || task.category.contains("Expiry", ignoreCase = true) || updatedDesc.contains("30 days", ignoreCase = true)) {
                    val items = repository.allInventoryItems.first()
                    val combinedText = "${task.title} ${task.description}"
                    val itemIdRegex = Regex("""\[(?:Item\s*#|Item\s*ID:\s*|#)(\d+)\]|(?:Item\s*#|Item\s*ID:\s*)(\d+)""", RegexOption.IGNORE_CASE)
                    val extractedId = itemIdRegex.find(combinedText)?.let { match ->
                        (match.groupValues[1].ifEmpty { match.groupValues[2] }).toIntOrNull()
                    }

                    val matchedItem = if (extractedId != null) {
                        items.find { it.id == extractedId }
                    } else {
                        val productName = task.title.substringAfter("Expiry Shelf Audit:").replace("Perform FEFO check on", "", ignoreCase = true).trim()
                        val candidates = items.filter { 
                            it.name.equals(productName, ignoreCase = true) || 
                            task.title.contains(it.name, ignoreCase = true) || 
                            it.name.contains(productName, ignoreCase = true) ||
                            task.description.contains(it.name, ignoreCase = true)
                        }
                        if (candidates.size == 1) {
                            candidates.first()
                        } else if (candidates.size > 1) {
                            candidates.maxByOrNull { c ->
                                var score = 0
                                if (c.dosage.isNotBlank() && combinedText.contains(c.dosage, ignoreCase = true)) score += 50
                                if (c.unitForm.isNotBlank() && combinedText.contains(c.unitForm, ignoreCase = true)) score += 30
                                if (c.batchNumber.isNotBlank() && combinedText.contains(c.batchNumber, ignoreCase = true)) score += 40
                                score
                            }
                        } else null
                    }
                    
                    val smartDesc = if (matchedItem != null) {
                        val now = System.currentTimeMillis()
                        val sdf = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
                        val batch = matchedItem.batchNumber.ifBlank { "N/A" }
                        val dateStr = if (matchedItem.expiryDate > 0) sdf.format(java.util.Date(matchedItem.expiryDate)) else "Soon"
                        val daysLeft = if (matchedItem.expiryDate > 0) ((matchedItem.expiryDate - now) / (1000L * 60 * 60 * 24)).toInt() else 0
                        
                        when {
                            matchedItem.expiryDate <= 0 -> "Batch $batch expiring soon. Perform physical count & apply FEFO markdown."
                            daysLeft < 0 -> {
                                val absDays = Math.abs(daysLeft)
                                "EXPIRED $absDays day${if (absDays == 1) "" else "s"} ago on $dateStr (Batch $batch). Immediately quarantine stock or record disposal write-off."
                            }
                            daysLeft == 0 -> "EXPIRES TODAY ($dateStr, Batch $batch). Move to quarantine or apply clearance discount immediately."
                            else -> "Expires in $daysLeft day${if (daysLeft == 1) "" else "s"} on $dateStr (Batch $batch). Perform physical count & apply FEFO markdown."
                        }
                    } else {
                        if (updatedDesc.contains("expiring within 30 days", ignoreCase = true)) {
                            updatedDesc.replace("expiring within 30 days", "expiring batch - perform physical count & FEFO audit", ignoreCase = true)
                        } else {
                            "Perform physical stock count & apply FEFO markdown for expiring batch."
                        }
                    }

                    if (updatedDesc.startsWith("Assignee:")) {
                        val assigneePrefix = updatedDesc.substringBefore(" | instructions: ")
                        updatedDesc = "$assigneePrefix | instructions: $smartDesc"
                    } else {
                        updatedDesc = smartDesc
                    }
                }

                val updated = task.copy(assignedToName = staffName, description = updatedDesc)
                updateOperationTaskAndOutboxHelper(updated)
                if (_activeHighlightTaskId.value == task.id.toLong()) {
                    _activeHighlightTaskId.value = null
                }
                
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
                        "assignedToUid" to (updated.assignedToUid ?: "")
                    )
                    syncEntityToFirestore("branch_operation_tasks", updated.id.toString(), map)
                    logAuditTrail(
                        action = "CLAIM_TASK",
                        details = "Pharmacist $staffName claimed responsibility for task '${updated.title}'.",
                        affectedId = updated.id.toString()
                    )
                }
                android.widget.Toast.makeText(getApplication(), "Task claimed successfully by $staffName", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(getApplication(), "Error claiming task: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            } finally {
                _claimingTaskIds.update { it - task.id }
            }
        }
    }

    fun dispatchAutomatedVerificationTasks(onFinished: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val activeBranch = getActiveBranchId()
            val currentTasks = if (activeBranch.isBlank()) repository.allOperationTasks.first() else repository.getOperationTasksForBranch(activeBranch).first()
            val items = if (activeBranch.isBlank()) repository.allInventoryItems.first() else repository.getInventoryForBranch(activeBranch).first()
            val now = System.currentTimeMillis()
            val sdf = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
            val ninetyDaysMs = 90L * 24 * 60 * 60 * 1000L
            val cutoff7Days = now - (7L * 24 * 60 * 60 * 1000L)
            val cutoff14Days = now - (14L * 24 * 60 * 60 * 1000L)

            val activeTasks = currentTasks.filter { !it.isCompleted }
            val activeTitles = activeTasks.map { it.title.lowercase() }.toSet()

            var dispatchedCount = 0

            // 1. Low stock automatic verification tasks (with 7-day grace window check)
            items.filter { it.stockQuantity <= it.minRequiredStock }.forEach { item ->
                val variantDetails = listOfNotNull(
                    item.dosage.ifBlank { null },
                    item.unitForm.ifBlank { null }
                ).joinToString(" • ")
                val variantSuffix = if (variantDetails.isNotBlank()) " ($variantDetails)" else ""
                val itemTag = "[Item #${item.id}]"
                val expectedTitle = "Inventory Verification: ${item.name}$variantSuffix $itemTag".lowercase()
                val itemNameLower = item.name.lowercase()
                val recentTaskCompleted = currentTasks.any { task ->
                    task.isCompleted &&
                    (task.title.contains(itemTag, ignoreCase = true) || 
                     (task.title.lowercase().contains(itemNameLower) && (item.dosage.isBlank() || task.title.lowercase().contains(item.dosage.lowercase())))) &&
                    ((task.verifiedAt ?: task.createdAt) >= cutoff7Days)
                }
                val recentlyReconciled = item.lastReconciledAt >= cutoff7Days

                if (!activeTitles.contains(expectedTitle) && !recentTaskCompleted && !recentlyReconciled) {
                    addOperationTask(
                        title = "Inventory Verification: ${item.name}$variantSuffix $itemTag",
                        description = "Stock low (${item.stockQuantity} remaining, min ${item.minRequiredStock}). Verify shelf count & reconcile discrepancies for ${item.name}${if (item.dosage.isNotBlank()) " (${item.dosage})" else ""}${if (item.unitForm.isNotBlank()) " [${item.unitForm}]" else ""} [Item ID: ${item.id}].",
                        urgency = "High",
                        category = "Clinical Intelligence",
                        assignedToName = null
                    )
                    dispatchedCount++
                }
            }

            // 2. Comprehensive sweep & refresh for ALL active tasks in DB
            activeTasks.forEach { task ->
                val isExpiryTask = task.title.contains("Expiry", ignoreCase = true) || 
                                  task.category.contains("Expiry", ignoreCase = true) || 
                                  task.description.contains("30 days", ignoreCase = true)
                if (isExpiryTask) {
                    val combinedText = "${task.title} ${task.description}"
                    val itemIdRegex = Regex("""\[(?:Item\s*#|Item\s*ID:\s*|#)(\d+)\]|(?:Item\s*#|Item\s*ID:\s*)(\d+)""", RegexOption.IGNORE_CASE)
                    val extractedId = itemIdRegex.find(combinedText)?.let { match ->
                        (match.groupValues[1].ifEmpty { match.groupValues[2] }).toIntOrNull()
                    }

                    val matchedItem = if (extractedId != null) {
                        items.find { it.id == extractedId }
                    } else {
                        val productName = task.title.substringAfter("Expiry Shelf Audit:").replace("Perform FEFO check on", "", ignoreCase = true).trim()
                        val candidates = items.filter { 
                            it.name.equals(productName, ignoreCase = true) || 
                            task.title.contains(it.name, ignoreCase = true) || 
                            it.name.contains(productName, ignoreCase = true) ||
                            task.description.contains(it.name, ignoreCase = true)
                        }
                        if (candidates.size == 1) {
                            candidates.first()
                        } else if (candidates.size > 1) {
                            candidates.maxByOrNull { c ->
                                var score = 0
                                if (c.dosage.isNotBlank() && combinedText.contains(c.dosage, ignoreCase = true)) score += 50
                                if (c.unitForm.isNotBlank() && combinedText.contains(c.unitForm, ignoreCase = true)) score += 30
                                if (c.batchNumber.isNotBlank() && combinedText.contains(c.batchNumber, ignoreCase = true)) score += 40
                                score
                            }
                        } else null
                    }
                    
                    val smartDesc = if (matchedItem != null) {
                        val batch = matchedItem.batchNumber.ifBlank { "N/A" }
                        val dateStr = if (matchedItem.expiryDate > 0) sdf.format(java.util.Date(matchedItem.expiryDate)) else "Soon"
                        val daysLeft = if (matchedItem.expiryDate > 0) ((matchedItem.expiryDate - now) / (1000L * 60 * 60 * 24)).toInt() else 0
                        
                        when {
                            matchedItem.expiryDate <= 0 -> "Batch $batch expiring soon. Perform physical count & apply FEFO markdown for ${matchedItem.name} (${matchedItem.dosage}) [Item ID: ${matchedItem.id}]."
                            daysLeft < 0 -> {
                                val absDays = Math.abs(daysLeft)
                                "EXPIRED $absDays day${if (absDays == 1) "" else "s"} ago on $dateStr (Batch $batch). Immediately quarantine stock or record disposal write-off for ${matchedItem.name} (${matchedItem.dosage}) [Item ID: ${matchedItem.id}]."
                            }
                            daysLeft == 0 -> "EXPIRES TODAY ($dateStr, Batch $batch). Move to quarantine or apply clearance discount immediately for ${matchedItem.name} (${matchedItem.dosage}) [Item ID: ${matchedItem.id}]."
                            else -> "Expires in $daysLeft day${if (daysLeft == 1) "" else "s"} on $dateStr (Batch $batch). Perform physical count & apply FEFO markdown for ${matchedItem.name} (${matchedItem.dosage}) [Item ID: ${matchedItem.id}]."
                        }
                    } else {
                        if (task.description.contains("expiring within 30 days", ignoreCase = true)) {
                            task.description.replace("expiring within 30 days", "expiring batch - perform physical count & FEFO audit", ignoreCase = true)
                        } else {
                            "Perform physical stock count & apply FEFO markdown for expiring batch."
                        }
                    }
                    val daysLeftCalc = matchedItem?.let { if (it.expiryDate > 0) ((it.expiryDate - now) / (1000L * 60 * 60 * 24)).toInt() else 0 } ?: 14
                    val urgencyStr = if (daysLeftCalc <= 7) "High" else "Medium"

                    val targetDesc = if (task.description.startsWith("Assignee:")) {
                        val assigneePrefix = task.description.substringBefore(" | instructions: ")
                        "$assigneePrefix | instructions: $smartDesc"
                    } else {
                        smartDesc
                    }

                    if (task.description != targetDesc || task.urgency != urgencyStr) {
                        updateOperationTaskAndOutboxHelper(task.copy(description = targetDesc, urgency = urgencyStr))
                    }
                }
            }

            // 3. Near-expiry & expired automatic audit tasks (with 7-day grace window check)
            items.filter { (it.expiryDate in 1L..(now + ninetyDaysMs) || (it.expiryDate > 0 && it.expiryDate < now)) && it.stockQuantity > 0 }.forEach { item ->
                val variantDetails = listOfNotNull(
                    item.dosage.ifBlank { null },
                    item.unitForm.ifBlank { null }
                ).joinToString(" • ")
                val variantSuffix = if (variantDetails.isNotBlank()) " ($variantDetails)" else ""
                val itemTag = "[Item #${item.id}]"
                val expectedTitle = "Expiry Shelf Audit: ${item.name}$variantSuffix $itemTag".lowercase()
                val itemNameLower = item.name.lowercase()
                val recentTaskCompleted = currentTasks.any { task ->
                    task.isCompleted &&
                    (task.title.contains(itemTag, ignoreCase = true) || 
                     (task.title.lowercase().contains(itemNameLower) && (item.dosage.isBlank() || task.title.lowercase().contains(item.dosage.lowercase())))) &&
                    ((task.verifiedAt ?: task.createdAt) >= cutoff7Days)
                }
                val recentlyReconciled = item.lastReconciledAt >= cutoff7Days

                if (!activeTitles.contains(expectedTitle) && !recentTaskCompleted && !recentlyReconciled) {
                    val batch = item.batchNumber.ifBlank { "N/A" }
                    val dateStr = if (item.expiryDate > 0) sdf.format(java.util.Date(item.expiryDate)) else "Soon"
                    val daysLeft = if (item.expiryDate > 0) ((item.expiryDate - now) / (1000L * 60 * 60 * 24)).toInt() else 0
                    
                    val desc = when {
                        item.expiryDate <= 0 -> "Batch $batch expiring soon. Perform physical count & apply FEFO markdown for ${item.name}${if (item.dosage.isNotBlank()) " (${item.dosage})" else ""} [Item ID: ${item.id}]."
                        daysLeft < 0 -> {
                            val absDays = Math.abs(daysLeft)
                            "EXPIRED $absDays day${if (absDays == 1) "" else "s"} ago on $dateStr (Batch $batch). Immediately quarantine stock or record disposal write-off for ${item.name}${if (item.dosage.isNotBlank()) " (${item.dosage})" else ""} [Item ID: ${item.id}]."
                        }
                        daysLeft == 0 -> "EXPIRES TODAY ($dateStr, Batch $batch). Move to quarantine or apply clearance discount immediately for ${item.name}${if (item.dosage.isNotBlank()) " (${item.dosage})" else ""} [Item ID: ${item.id}]."
                        else -> "Expires in $daysLeft day${if (daysLeft == 1) "" else "s"} on $dateStr (Batch $batch). Perform physical count & apply FEFO markdown for ${item.name}${if (item.dosage.isNotBlank()) " (${item.dosage})" else ""} [Item ID: ${item.id}]."
                    }
                    val urgencyStr = if (daysLeft <= 7) "High" else "Medium"

                    addOperationTask(
                        title = "Expiry Shelf Audit: ${item.name}$variantSuffix $itemTag",
                        description = desc,
                        urgency = urgencyStr,
                        category = "Revenue & Retention",
                        assignedToName = null
                    )
                    dispatchedCount++
                }
            }

            // 4. Rolling Cycle Count automatic tasks (7-day for fast-moving, 14-day for standard items)
            val reconciledCount = items.count { item ->
                val cutoff = if (item.isFastMoving) cutoff7Days else cutoff14Days
                item.lastReconciledAt >= cutoff
            }
            val overdueItems = items.filter { item ->
                val cutoff = if (item.isFastMoving) cutoff7Days else cutoff14Days
                item.lastReconciledAt < cutoff && item.stockQuantity > 0
            }
            val cycleRatio = if (items.isNotEmpty()) reconciledCount.toFloat() / items.size.toFloat() else 1.0f
            val cycleRatioPctFormatted = String.format(java.util.Locale.US, "%.1f", cycleRatio * 100)

            if (cycleRatio < 0.80f) {
                // Synchronize live compliance stats on any existing active cycle count tasks
                currentTasks.filter { !it.isCompleted && (it.title.startsWith("14-Day Cycle Count:", ignoreCase = true) || it.title.startsWith("7-Day High-Velocity Cycle Count:", ignoreCase = true)) }.forEach { activeTask ->
                    val updatedDesc = activeTask.description.replace(
                        Regex("Rolling (?:14-day|7-day) cycle count compliance \\([^)]+\\)"),
                        "Rolling cycle count compliance ($reconciledCount/${items.size} reconciled, $cycleRatioPctFormatted%)"
                    )
                    if (updatedDesc != activeTask.description) {
                        val updatedTask = activeTask.copy(description = updatedDesc)
                        updateOperationTaskAndOutboxHelper(updatedTask)
                        val branchId = _currentPharmacistBranchId.value
                        if (!branchId.isNullOrBlank()) {
                            syncEntityToFirestore("operationTasks", updatedTask.id.toString(), mapOf(
                                "id" to updatedTask.id,
                                "description" to updatedTask.description,
                                "branchId" to branchId
                            ))
                        }
                    }
                }

                overdueItems.take(5).forEach { item ->
                    val variantDetails = listOfNotNull(
                        item.dosage.ifBlank { null },
                        item.unitForm.ifBlank { null }
                    ).joinToString(" • ")
                    val variantSuffix = if (variantDetails.isNotBlank()) " ($variantDetails)" else ""
                    val itemTag = "[Item #${item.id}]"
                    val cyclePrefix = if (item.isFastMoving) "7-Day High-Velocity Cycle Count:" else "14-Day Cycle Count:"
                    val cutoffForTask = if (item.isFastMoving) cutoff7Days else cutoff14Days
                    val expectedTitle = "$cyclePrefix ${item.name}$variantSuffix $itemTag".lowercase()
                    val itemNameLower = item.name.lowercase()
                    val recentTaskCompleted = currentTasks.any { task ->
                        task.isCompleted &&
                        (task.title.contains(itemTag, ignoreCase = true) || 
                         (task.title.lowercase().contains(itemNameLower) && (item.dosage.isBlank() || task.title.lowercase().contains(item.dosage.lowercase())))) &&
                        ((task.verifiedAt ?: task.createdAt) >= cutoffForTask)
                    }
                    val recentlyReconciled = item.lastReconciledAt >= cutoffForTask

                    if (!activeTitles.contains(expectedTitle) && !recentTaskCompleted && !recentlyReconciled) {
                        val lastRecStr = if (item.lastReconciledAt > 0) sdf.format(java.util.Date(item.lastReconciledAt)) else "Never"
                        val cycleLabel = if (item.isFastMoving) "7-day high-velocity" else "14-day"
                        addOperationTask(
                            title = "$cyclePrefix ${item.name}$variantSuffix $itemTag",
                            description = "Rolling $cycleLabel cycle count compliance ($reconciledCount/${items.size} reconciled, $cycleRatioPctFormatted%). Last counted: $lastRecStr. Verify physical shelf count of ${item.name}${if (item.dosage.isNotBlank()) " (${item.dosage})" else ""}${if (item.unitForm.isNotBlank()) " • ${item.unitForm}" else ""} [Item ID: ${item.id}].",
                            urgency = if (item.isFastMoving) "High" else "Medium",
                            category = "Clinical Intelligence",
                            assignedToName = null
                        )
                        dispatchedCount++
                    }
                }
            }
            onFinished(dispatchedCount)
        }
    }

    fun toggleOperationTask(task: OperationTask) {
        viewModelScope.launch {
            val updated = task.copy(isCompleted = !task.isCompleted)
            updateOperationTaskAndOutboxHelper(updated)
            if (updated.isCompleted) {
                syncTaskCompletionWithInventoryReconciliation(updated)
            }
            
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
            if (task.branchId.isBlank() || task.originatingUserUid.isBlank()) {
                android.util.Log.e("PharmacyViewModel", "Cannot delete task ${task.id}: missing lineage. Failing closed.")
                return@launch
            }
            val outbox = com.example.data.sync.SyncOutboxRecord(
                branchId = task.branchId,
                entityType = "TASK",
                entityId = task.id.toString(),
                operationType = "DELETE",
                payloadJson = "{}",
                originatingUserUid = task.originatingUserUid
            )
            repository.deleteOperationTaskAndOutbox(task, outbox)
            logAuditTrail(
                action = "DELETE_TASK",
                details = "Deleted task '${task.title}'",
                affectedId = task.id.toString()
            )
        }
    }

    private suspend fun syncTaskCompletionWithInventoryReconciliation(task: OperationTask, countedQuantity: Int? = null) {
        if (!task.isCompleted) return
        val targetBranch = task.branchId.ifBlank { getActiveBranchId() }
        val items = if (targetBranch.isBlank()) repository.allInventoryItems.first() else repository.getInventoryForBranch(targetBranch).first()
        if (items.isEmpty()) return
        val now = System.currentTimeMillis()
        val taskTime = if ((task.verifiedAt ?: 0L) > 0L) task.verifiedAt!! else now
        
        val taskFullText = "${task.title} ${task.description}"
        val taskTextLower = taskFullText.lowercase()

        // 1. Direct Target ID matching: Look for [Item #123] or [Item ID: 123] or Item #123 in title/description
        val itemIdRegex = Regex("""\[(?:Item\s*#|Item\s*ID:\s*|#)(\d+)\]|(?:Item\s*#|Item\s*ID:\s*)(\d+)""", RegexOption.IGNORE_CASE)
        val extractedId = itemIdRegex.find(taskFullText)?.let { match ->
            (match.groupValues[1].ifEmpty { match.groupValues[2] }).toIntOrNull()
        }

        val directItem = if (extractedId != null) {
            items.find { it.id == extractedId }
        } else null

        // 2. High-Precision Variant Matching Fallback (single entity resolution)
        val matchedItem: InventoryItem? = if (directItem != null) {
            directItem
        } else {
            // Find candidates that share the medicine brand / base name
            val candidates = items.filter { item ->
                val itemNameLower = item.name.lowercase().replace(Regex("\\(.*\\)"), "").trim()
                taskTextLower.contains(itemNameLower) || (item.brand.isNotBlank() && taskTextLower.contains(item.brand.lowercase()))
            }

            if (candidates.isEmpty()) {
                null
            } else if (candidates.size == 1) {
                candidates.first()
            } else {
                // Multi-variant case (e.g. Exforge 5/160 vs Exforge 10/160 vs Exforge 10/160/12.5)
                // Score by exact dosage, packaging form, and batch matches
                candidates.maxByOrNull { item ->
                    var score = 0
                    val dosageLower = item.dosage.lowercase().trim()
                    val unitFormLower = item.unitForm.lowercase().trim()
                    val batchLower = item.batchNumber.lowercase().trim()

                    if (dosageLower.isNotBlank() && taskTextLower.contains(dosageLower)) {
                        score += 50
                    }
                    if (unitFormLower.isNotBlank() && taskTextLower.contains(unitFormLower)) {
                        score += 30
                    }
                    if (batchLower.isNotBlank() && taskTextLower.contains(batchLower)) {
                        score += 40
                    }
                    if (taskTextLower.contains(item.name.lowercase())) {
                        score += 20
                    }
                    score
                }
            }
        }

        // 3. Strictly reconcile ONLY the single identified item entity
        if (matchedItem != null) {
            val updated = if (countedQuantity != null) {
                matchedItem.copy(stockQuantity = countedQuantity, lastReconciledAt = taskTime, lastUpdated = now)
            } else {
                matchedItem.copy(lastReconciledAt = taskTime, lastUpdated = now)
            }
            saveAndSyncInventoryItemDirectly(updated)
            if (countedQuantity != null) {
                logAuditTrail(
                    action = "STOCK_ADJUSTMENT",
                    details = "Audit count updated stock of ${matchedItem.name} (${matchedItem.dosage}${if (matchedItem.unitForm.isNotBlank()) " • ${matchedItem.unitForm}" else ""}) [Item #${matchedItem.id}] from ${matchedItem.stockQuantity} to $countedQuantity via audit task '${task.title}'",
                    affectedId = matchedItem.id.toString()
                )
            }
        }
    }

    // --- Receipt Actions ---
    fun addReceipt(customerName: String, totalAmount: Double, imageFileName: String, isInvoice: Boolean = false, paymentStatus: String = "Paid") {
        viewModelScope.launch {
            val branchId = getActiveBranchId()
            val userUid = getCurrentUserUid()
            val receipt = Receipt(
                customerName = customerName,
                totalAmount = totalAmount,
                imageFileName = imageFileName,
                isInvoice = isInvoice,
                paymentStatus = paymentStatus,
                branchId = branchId,
                originatingUserUid = userUid
            )
            val map = mapOf(
                "customerName" to customerName,
                "totalAmount" to totalAmount,
                "imageFileName" to imageFileName,
                "isInvoice" to isInvoice,
                "paymentStatus" to paymentStatus,
                "timestamp" to receipt.timestamp,
                "branchId" to branchId,
                "originatingUserUid" to userUid
            )
            val outbox = com.example.data.sync.SyncOutboxRecord(
                branchId = branchId,
                entityType = "RECEIPT",
                entityId = "0",
                operationType = "UPSERT",
                payloadJson = org.json.JSONObject(map).toString(),
                originatingUserUid = userUid
            )
            repository.insertReceiptAndOutbox(receipt, outbox)
        }
    }

    fun updateReceipt(receipt: Receipt) {
        viewModelScope.launch {
            if (receipt.branchId.isBlank() || receipt.originatingUserUid.isBlank()) {
                android.util.Log.w("PharmacyViewModel", "Cannot update receipt ${receipt.id}: missing immutable branchId or originatingUserUid lineage.")
                return@launch
            }
            val map = mapOf(
                "id" to receipt.id,
                "customerName" to receipt.customerName,
                "totalAmount" to receipt.totalAmount,
                "imageFileName" to receipt.imageFileName,
                "isInvoice" to receipt.isInvoice,
                "paymentStatus" to receipt.paymentStatus,
                "timestamp" to receipt.timestamp,
                "branchId" to receipt.branchId,
                "originatingUserUid" to receipt.originatingUserUid
            )
            val outbox = com.example.data.sync.SyncOutboxRecord(
                branchId = receipt.branchId,
                entityType = "RECEIPT",
                entityId = receipt.id.toString(),
                operationType = "UPSERT",
                payloadJson = org.json.JSONObject(map).toString(),
                originatingUserUid = receipt.originatingUserUid
            )
            repository.updateReceiptAndOutbox(receipt, outbox)
        }
    }

    fun deleteReceipt(receipt: Receipt) {
        viewModelScope.launch {
            if (receipt.branchId.isBlank() || receipt.originatingUserUid.isBlank()) {
                android.util.Log.e("PharmacyViewModel", "Cannot delete receipt ${receipt.id}: missing lineage. Failing closed.")
                return@launch
            }
            val outbox = com.example.data.sync.SyncOutboxRecord(
                branchId = receipt.branchId,
                entityType = "RECEIPT",
                entityId = receipt.id.toString(),
                operationType = "DELETE",
                payloadJson = "{}",
                originatingUserUid = receipt.originatingUserUid
            )
            repository.deleteReceiptAndOutbox(receipt, outbox)
        }
    }

    // --- Customer Actions ---
    fun triggerImmediateSync() {
        generatePatientFirstAutomaticAlerts()
        try {
            _syncState.value = SyncState.Syncing
            val currentTime = System.currentTimeMillis()
            
            val currentUser = authRepository.getCurrentUser()
            val currentBranchId = _currentPharmacistBranchId.value
            
            viewModelScope.launch {
                try {
                    // Perform ping with a 3-second timeout to handle high latency / offline queuing
                    kotlinx.coroutines.withTimeoutOrNull(3000) {
                        try {
                            val devModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
                            val payload = mutableMapOf<String, Any>(
                                "deviceId" to deviceId,
                                "deviceModel" to devModel,
                                "aiContentEnabled" to true,
                                "carefluxAiEnabled" to true,
                                "lastActive" to currentTime
                            )
                            if (currentUser != null) {
                                payload["ownerUid"] = currentUser.uid
                                payload["ownerEmail"] = currentUser.email.orEmpty()
                                payload["ownerName"] = (currentUser.displayName ?: currentUser.email?.substringBefore("@") ?: "Staff Pharmacist")
                            }
                            if (!currentBranchId.isNullOrBlank()) {
                                payload["branchId"] = currentBranchId
                            }
                            repository.upsertRemoteDocument("device_configs", deviceId, payload)
                        } catch (e: Exception) {
                            // ignore and proceed
                        }
                    }
                    _syncState.value = SyncState.Synced
                    _lastSyncedTime.value = currentTime
                } catch (e: Exception) {
                    _syncState.value = SyncState.Synced
                }
            }

            val syncRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.work.CloudSyncWorker>().build()
            androidx.work.WorkManager.getInstance(getApplication()).enqueue(syncRequest)
        } catch (e: Exception) {
            e.printStackTrace()
            _syncState.value = SyncState.Error(e.localizedMessage ?: "Sync Error")
        }
    }

    fun isCustomerPhoneUnique(phone: String, excludeId: Int = 0): Boolean {
        val normalizedNew = phone.replace(Regex("[^+\\d]"), "")
        if (normalizedNew.isEmpty()) return true
        return customers.value.none { 
            it.id != excludeId && it.phoneNumber.replace(Regex("[^+\\d]"), "").equals(normalizedNew, ignoreCase = true)
        }
    }

    fun checkAndAutoSyncExternalPatientByPhone(phone: String, onSyncComplete: () -> Unit = {}) {
        val cleanPhone = phone.trim().replace("[^0-9]".toRegex(), "")
        if (cleanPhone.length < 10) return

        viewModelScope.launch {
            // Check if already exists locally
            val localCust = customers.value.find { 
                it.phoneNumber.trim().replace("[^0-9]".toRegex(), "") == cleanPhone 
            }
            if (localCust != null) {
                // Already exists locally, no need to sync externally
                return@launch
            }

            try {
                val qRes = repository.getRemoteDocumentsWhereEquals("customers", "phoneNumber", phone.trim())
                val results = (qRes.getOrNull() ?: emptyList()).filter {
                    (it["syncedFromDevice"] as? String) != deviceId
                }

                if (results.isNotEmpty()) {
                    val targetCust = results.first()
                    val name = targetCust["name"] as? String ?: "Unknown Patient"
                    val pPhone = targetCust["phoneNumber"] as? String ?: phone
                    val email = targetCust["email"] as? String ?: ""
                    val notes = (targetCust["notes"] as? String ?: "") + " [Auto-imported from global registry]"
                    val age = (targetCust["age"] as? Long ?: 30L).toInt()
                    val gender = targetCust["gender"] as? String ?: "Male"
                    val state = targetCust["state"] as? String ?: "Lagos"
                    val lga = targetCust["lga"] as? String ?: "Ikeja"
                    val city = targetCust["city"] as? String ?: "Ikeja"

                    val activeBranch = getActiveBranchId()
                    val userUid = getCurrentUserUid()

                    val newCust = com.example.data.Customer(
                        name = name,
                        phoneNumber = pPhone,
                        email = email,
                        notes = notes,
                        age = age,
                        gender = gender,
                        state = state,
                        lga = lga,
                        city = city,
                        consentPrescriptionTracking = true,
                        consentSmsRefills = false,
                        consentCloudSync = true,
                        consentChannel = "Auto Handshake",
                        consentLastUpdated = System.currentTimeMillis(),
                        branchId = activeBranch,
                        originatingUserUid = userUid
                    )
                    val custMap = mapOf(
                        "name" to newCust.name,
                        "phoneNumber" to newCust.phoneNumber,
                        "email" to newCust.email,
                        "notes" to newCust.notes,
                        "age" to newCust.age,
                        "gender" to newCust.gender,
                        "state" to newCust.state,
                        "lga" to newCust.lga,
                        "city" to newCust.city,
                        "consentPrescriptionTracking" to newCust.consentPrescriptionTracking,
                        "consentSmsRefills" to newCust.consentSmsRefills,
                        "consentCloudSync" to newCust.consentCloudSync,
                        "consentChannel" to newCust.consentChannel,
                        "consentLastUpdated" to newCust.consentLastUpdated,
                        "branchId" to activeBranch,
                        "originatingUserUid" to userUid
                    )
                    val custOutbox = com.example.data.sync.SyncOutboxRecord(
                        branchId = activeBranch,
                        entityType = "CUSTOMER",
                        entityId = "0",
                        operationType = "UPSERT",
                        payloadJson = org.json.JSONObject(custMap).toString(),
                        originatingUserUid = userUid
                    )
                    val insertedId = repository.insertCustomerAndOutbox(newCust, custOutbox)
                    val resolvedLocalId = insertedId.toInt()

                    val targetId = targetCust["id"] as? String ?: ""

                    // Sync medications
                    val medRes = repository.getRemoteDocumentsWhereEquals("customer_medications", "globalCustomerDocId", targetId)
                    medRes.getOrNull()?.forEach { doc ->
                        val mName = doc["medicationName"] as? String ?: ""
                        val mDosage = doc["customDosage"] as? String ?: ""
                        val mCost = (doc["cost"] as? Number)?.toDouble() ?: 0.0
                        val mCycle = (doc["cycleDays"] as? Number)?.toInt() ?: 30
                        val mNext = (doc["nextRefillDate"] as? Number)?.toLong() ?: System.currentTimeMillis()

                        val newMed = com.example.data.CustomerMedication(
                            customerId = resolvedLocalId,
                            inventoryItemId = 0,
                            medicationName = mName,
                            customDosage = mDosage,
                            cost = mCost,
                            cycleDays = mCycle,
                            nextRefillDate = mNext,
                            branchId = activeBranch,
                            originatingUserUid = userUid
                        )
                        val medMap = mapOf(
                            "customerId" to newMed.customerId,
                            "inventoryItemId" to newMed.inventoryItemId,
                            "medicationName" to newMed.medicationName,
                            "customDosage" to newMed.customDosage,
                            "cost" to newMed.cost,
                            "cycleDays" to newMed.cycleDays,
                            "nextRefillDate" to newMed.nextRefillDate,
                            "branchId" to activeBranch,
                            "originatingUserUid" to userUid
                        )
                        val medOutbox = com.example.data.sync.SyncOutboxRecord(
                            branchId = activeBranch,
                            entityType = "CUSTOMER_MEDICATION",
                            entityId = "0",
                            operationType = "UPSERT",
                            payloadJson = org.json.JSONObject(medMap).toString(),
                            originatingUserUid = userUid
                        )
                        repository.insertCustomerMedicationAndOutbox(newMed, medOutbox)
                    }

                    // Sync interventions
                    val intRes = repository.getRemoteDocumentsWhereEquals("interventions", "globalCustomerDocId", targetId)
                    intRes.getOrNull()?.forEach { doc2 ->
                        val pres = doc2["presentation"] as? String ?: ""
                        val tRes = doc2["testResults"] as? String ?: ""
                        val rec = doc2["recommendation"] as? String ?: ""

                        val newInt = com.example.data.ClinicalIntervention(
                            customerId = resolvedLocalId,
                            presentation = pres,
                            testResults = tRes,
                            recommendation = rec,
                            currentStatus = doc2["currentStatus"] as? String ?: "Pending",
                            followUpDay3Sent = doc2["followUpDay3Sent"] as? Boolean ?: false,
                            followUpDay7Sent = doc2["followUpDay7Sent"] as? Boolean ?: false,
                            followUpDay14Sent = doc2["followUpDay14Sent"] as? Boolean ?: false,
                            dateAdded = (doc2["dateAdded"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                            branchId = activeBranch,
                            originatingUserUid = userUid
                        )
                        val intMap = mapOf(
                            "customerId" to newInt.customerId,
                            "presentation" to newInt.presentation,
                            "testResults" to newInt.testResults,
                            "recommendation" to newInt.recommendation,
                            "currentStatus" to newInt.currentStatus,
                            "branchId" to activeBranch,
                            "originatingUserUid" to userUid
                        )
                        val intOutbox = com.example.data.sync.SyncOutboxRecord(
                            branchId = activeBranch,
                            entityType = "INTERVENTION",
                            entityId = "0",
                            operationType = "UPSERT",
                            payloadJson = org.json.JSONObject(intMap).toString(),
                            originatingUserUid = userUid
                        )
                        repository.insertClinicalInterventionAndOutbox(newInt, intOutbox)
                    }

                    android.widget.Toast.makeText(
                        getApplication(),
                        "Global Network Registry Sync complete! Patient '$name' history successfully imported.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    onSyncComplete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
                consentLastUpdated = System.currentTimeMillis(),
                branchId = getActiveBranchId(),
                originatingUserUid = getCurrentUserUid()
            )
            val custMap = mapOf(
                "name" to newCust.name,
                "phoneNumber" to newCust.phoneNumber,
                "email" to newCust.email,
                "notes" to newCust.notes,
                "age" to newCust.age,
                "gender" to newCust.gender,
                "state" to newCust.state,
                "lga" to newCust.lga,
                "city" to newCust.city,
                "consentPrescriptionTracking" to newCust.consentPrescriptionTracking,
                "consentSmsRefills" to newCust.consentSmsRefills,
                "consentCloudSync" to newCust.consentCloudSync,
                "consentChannel" to newCust.consentChannel,
                "consentLastUpdated" to newCust.consentLastUpdated,
                "branchId" to newCust.branchId,
                "originatingUserUid" to newCust.originatingUserUid
            )
            val outboxRecord = com.example.data.sync.SyncOutboxRecord(
                branchId = newCust.branchId,
                entityType = "CUSTOMER",
                entityId = "0",
                operationType = "UPSERT",
                payloadJson = org.json.JSONObject(custMap).toString(),
                originatingUserUid = newCust.originatingUserUid
            )
            val insertedId = repository.insertCustomerAndOutbox(newCust, outboxRecord)
            val finalCust = newCust.copy(id = insertedId.toInt())
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
            if (customer.branchId.isBlank() || customer.originatingUserUid.isBlank()) {
                android.util.Log.w("PharmacyViewModel", "Cannot update customer ${customer.id}: missing immutable branchId or originatingUserUid lineage.")
                android.widget.Toast.makeText(getApplication(), "Cannot update record missing branch lineage", android.widget.Toast.LENGTH_LONG).show()
                return@launch
            }
            val custBranchId = customer.branchId
            val userUid = customer.originatingUserUid
            val custMap = mapOf(
                "id" to customer.id,
                "name" to customer.name,
                "phoneNumber" to customer.phoneNumber,
                "email" to customer.email,
                "notes" to customer.notes,
                "age" to customer.age,
                "gender" to customer.gender,
                "state" to customer.state,
                "lga" to customer.lga,
                "city" to customer.city,
                "consentPrescriptionTracking" to customer.consentPrescriptionTracking,
                "consentSmsRefills" to customer.consentSmsRefills,
                "consentCloudSync" to customer.consentCloudSync,
                "consentChannel" to customer.consentChannel,
                "consentLastUpdated" to customer.consentLastUpdated,
                "branchId" to custBranchId,
                "originatingUserUid" to userUid
            )
            val outboxRecord = com.example.data.sync.SyncOutboxRecord(
                branchId = custBranchId,
                entityType = "CUSTOMER",
                entityId = customer.id.toString(),
                operationType = "UPSERT",
                payloadJson = org.json.JSONObject(custMap).toString(),
                originatingUserUid = userUid
            )
            repository.updateCustomerAndOutbox(customer, outboxRecord) 
            parseCustomerNotesForAlerts(customer)
            syncCustomerToBranch(customer)
            triggerImmediateSync()
        }
    }
    
    fun deleteCustomer(customer: Customer) {
        viewModelScope.launch { 
            if (customer.branchId.isBlank() || customer.originatingUserUid.isBlank()) {
                android.util.Log.e("PharmacyViewModel", "Cannot delete customer ${customer.id}: missing lineage. Failing closed.")
                return@launch
            }
            val outboxRecord = com.example.data.sync.SyncOutboxRecord(
                branchId = customer.branchId,
                entityType = "CUSTOMER",
                entityId = customer.id.toString(),
                operationType = "DELETE",
                payloadJson = "{}",
                originatingUserUid = customer.originatingUserUid
            )
            repository.deleteCustomerAndOutbox(customer, outboxRecord)
            // Cleanup orphans since no ForeignKeys cascade
            customerMedications.value.filter { it.customerId == customer.id }.forEach { med ->
                if (med.branchId.isNotBlank() && med.originatingUserUid.isNotBlank()) {
                    val medOutbox = com.example.data.sync.SyncOutboxRecord(
                        branchId = med.branchId,
                        entityType = "CUSTOMER_MEDICATION",
                        entityId = med.id.toString(),
                        operationType = "DELETE",
                        payloadJson = "{}",
                        originatingUserUid = med.originatingUserUid
                    )
                    repository.deleteCustomerMedicationAndOutbox(med, medOutbox)
                }
            }
            clinicalInterventions.value.filter { it.customerId == customer.id }.forEach { inter ->
                if (inter.branchId.isNotBlank() && inter.originatingUserUid.isNotBlank()) {
                    val interOutbox = com.example.data.sync.SyncOutboxRecord(
                        branchId = inter.branchId,
                        entityType = "INTERVENTION",
                        entityId = inter.id.toString(),
                        operationType = "DELETE",
                        payloadJson = "{}",
                        originatingUserUid = inter.originatingUserUid
                    )
                    repository.deleteClinicalInterventionAndOutbox(inter, interOutbox)
                }
            }
            triggerImmediateSync()
        }
    }
    
    fun addCustomerMedication(customerId: Int, invItemId: Int, medName: String, customDosage: String, cost: Double, cycleDays: Int, nextRefill: Long, dateAdded: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            var finalName = medName.trim()
            if (invItemId > 0) {
                val invItem = inventoryItems.value.find { it.id == invItemId }
                if (invItem != null) {
                    val stockDosage = invItem.dosage.trim()
                    if (stockDosage.isNotBlank() && !stockDosage.equals("N/A", ignoreCase = true) && !finalName.contains(stockDosage, ignoreCase = true)) {
                        finalName = "$finalName $stockDosage"
                    }
                }
            }
            val medBranchId = getActiveBranchId()
            val userUid = getCurrentUserUid()
            val med = CustomerMedication(
                customerId = customerId,
                inventoryItemId = invItemId,
                medicationName = finalName,
                customDosage = customDosage,
                cost = cost,
                cycleDays = cycleDays,
                nextRefillDate = nextRefill,
                dateAdded = dateAdded,
                branchId = medBranchId,
                originatingUserUid = userUid
            )
            val medMap = mapOf(
                "customerId" to med.customerId,
                "inventoryItemId" to med.inventoryItemId,
                "medicationName" to med.medicationName,
                "customDosage" to med.customDosage,
                "cost" to med.cost,
                "cycleDays" to med.cycleDays,
                "nextRefillDate" to med.nextRefillDate,
                "branchId" to medBranchId,
                "originatingUserUid" to userUid
            )
            val outboxRecord = com.example.data.sync.SyncOutboxRecord(
                branchId = medBranchId,
                entityType = "CUSTOMER_MEDICATION",
                entityId = "0",
                operationType = "UPSERT",
                payloadJson = org.json.JSONObject(medMap).toString(),
                originatingUserUid = userUid
            )
            val insertedId = repository.insertCustomerMedicationAndOutbox(med, outboxRecord)
            syncCustomerMedicationToBranch(med.copy(id = insertedId.toInt()))
            triggerImmediateSync()
        }
    }
    
    fun updateCustomerMedication(med: CustomerMedication) {
        viewModelScope.launch { 
            if (med.branchId.isBlank() || med.originatingUserUid.isBlank()) {
                android.util.Log.w("PharmacyViewModel", "Cannot update medication ${med.id}: missing immutable branchId or originatingUserUid lineage.")
                android.widget.Toast.makeText(getApplication(), "Cannot update record missing branch lineage", android.widget.Toast.LENGTH_LONG).show()
                return@launch
            }
            val medBranchId = med.branchId
            val userUid = med.originatingUserUid
            val medMap = mapOf(
                "id" to med.id,
                "customerId" to med.customerId,
                "inventoryItemId" to med.inventoryItemId,
                "medicationName" to med.medicationName,
                "customDosage" to med.customDosage,
                "cost" to med.cost,
                "cycleDays" to med.cycleDays,
                "nextRefillDate" to med.nextRefillDate,
                "branchId" to medBranchId,
                "originatingUserUid" to userUid
            )
            val outboxRecord = com.example.data.sync.SyncOutboxRecord(
                branchId = medBranchId,
                entityType = "CUSTOMER_MEDICATION",
                entityId = med.id.toString(),
                operationType = "UPSERT",
                payloadJson = org.json.JSONObject(medMap).toString(),
                originatingUserUid = userUid
            )
            repository.updateCustomerMedicationAndOutbox(med, outboxRecord) 
            syncCustomerMedicationToBranch(med)
            triggerImmediateSync()
        }
    }

    fun deleteCustomerMedication(med: CustomerMedication) {
        viewModelScope.launch { 
            if (med.branchId.isBlank() || med.originatingUserUid.isBlank()) {
                android.util.Log.e("PharmacyViewModel", "Cannot delete medication ${med.id}: missing lineage. Failing closed.")
                return@launch
            }
            val outboxRecord = com.example.data.sync.SyncOutboxRecord(
                branchId = med.branchId,
                entityType = "CUSTOMER_MEDICATION",
                entityId = med.id.toString(),
                operationType = "DELETE",
                payloadJson = "{}",
                originatingUserUid = med.originatingUserUid
            )
            repository.deleteCustomerMedicationAndOutbox(med, outboxRecord)
            triggerImmediateSync()
        }
    }

    // --- Clinical Intervention Actions ---
    fun addClinicalIntervention(customerId: Int, presentation: String, testResults: String, recommendation: String) {
        viewModelScope.launch {
            val interBranchId = getActiveBranchId()
            val userUid = getCurrentUserUid()
            val inter = ClinicalIntervention(
                customerId = customerId,
                presentation = presentation,
                testResults = testResults,
                recommendation = recommendation,
                branchId = interBranchId,
                originatingUserUid = userUid
            )
            val interMap = mapOf(
                "customerId" to inter.customerId,
                "presentation" to inter.presentation,
                "testResults" to inter.testResults,
                "recommendation" to inter.recommendation,
                "currentStatus" to inter.currentStatus,
                "branchId" to interBranchId,
                "originatingUserUid" to userUid
            )
            val outboxRecord = com.example.data.sync.SyncOutboxRecord(
                branchId = interBranchId,
                entityType = "INTERVENTION",
                entityId = "0",
                operationType = "UPSERT",
                payloadJson = org.json.JSONObject(interMap).toString(),
                originatingUserUid = userUid
            )
            val insertedId = repository.insertClinicalInterventionAndOutbox(inter, outboxRecord)
            val finalInter = inter.copy(id = insertedId.toInt())
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
            if (intervention.branchId.isBlank() || intervention.originatingUserUid.isBlank()) {
                android.util.Log.w("PharmacyViewModel", "Cannot update intervention ${intervention.id}: missing immutable branchId or originatingUserUid lineage.")
                return@launch
            }
            val updated = intervention.copy(currentStatus = newStatus)
            val interMap = mapOf(
                "id" to updated.id,
                "customerId" to updated.customerId,
                "presentation" to updated.presentation,
                "testResults" to updated.testResults,
                "recommendation" to updated.recommendation,
                "currentStatus" to updated.currentStatus,
                "branchId" to updated.branchId,
                "originatingUserUid" to updated.originatingUserUid
            )
            val outboxRecord = com.example.data.sync.SyncOutboxRecord(
                branchId = updated.branchId,
                entityType = "INTERVENTION",
                entityId = updated.id.toString(),
                operationType = "UPSERT",
                payloadJson = org.json.JSONObject(interMap).toString(),
                originatingUserUid = updated.originatingUserUid
            )
            repository.updateClinicalInterventionAndOutbox(updated, outboxRecord)
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
                val activeBranch = getActiveBranchId()
                val userUid = getCurrentUserUid()

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
                            consentLastUpdated = System.currentTimeMillis(),
                            branchId = activeBranch,
                            originatingUserUid = userUid
                        )
                        val custMap = mapOf(
                            "name" to newCust.name,
                            "phoneNumber" to newCust.phoneNumber,
                            "email" to newCust.email,
                            "notes" to newCust.notes,
                            "age" to newCust.age,
                            "gender" to newCust.gender,
                            "state" to newCust.state,
                            "lga" to newCust.lga,
                            "city" to newCust.city,
                            "consentPrescriptionTracking" to newCust.consentPrescriptionTracking,
                            "consentSmsRefills" to newCust.consentSmsRefills,
                            "consentCloudSync" to newCust.consentCloudSync,
                            "consentChannel" to newCust.consentChannel,
                            "consentLastUpdated" to newCust.consentLastUpdated,
                            "branchId" to activeBranch,
                            "originatingUserUid" to userUid
                        )
                        val custOutbox = com.example.data.sync.SyncOutboxRecord(
                            branchId = activeBranch,
                            entityType = "CUSTOMER",
                            entityId = "0",
                            operationType = "UPSERT",
                            payloadJson = org.json.JSONObject(custMap).toString(),
                            originatingUserUid = userUid
                        )
                        val insertedId = repository.insertCustomerAndOutbox(newCust, custOutbox).toInt()
                        finalCustomerId = insertedId
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
                    recommendation = "Recommended Care Plan: $recommendedMed. Follow-up scheduled in $followUpDays days.",
                    branchId = activeBranch,
                    originatingUserUid = userUid
                )
                val interMap = mapOf(
                    "customerId" to inter.customerId,
                    "presentation" to inter.presentation,
                    "testResults" to inter.testResults,
                    "recommendation" to inter.recommendation,
                    "currentStatus" to inter.currentStatus,
                    "branchId" to activeBranch,
                    "originatingUserUid" to userUid
                )
                val interOutbox = com.example.data.sync.SyncOutboxRecord(
                    branchId = activeBranch,
                    entityType = "INTERVENTION",
                    entityId = "0",
                    operationType = "UPSERT",
                    payloadJson = org.json.JSONObject(interMap).toString(),
                    originatingUserUid = userUid
                )
                val insertedInterId = repository.insertClinicalInterventionAndOutbox(inter, interOutbox).toInt()
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
                        nextRefillDate = nextRefill,
                        branchId = activeBranch,
                        originatingUserUid = userUid
                    )
                    val medMap = mapOf(
                        "customerId" to med.customerId,
                        "inventoryItemId" to med.inventoryItemId,
                        "medicationName" to med.medicationName,
                        "customDosage" to med.customDosage,
                        "cost" to med.cost,
                        "cycleDays" to med.cycleDays,
                        "nextRefillDate" to med.nextRefillDate,
                        "branchId" to activeBranch,
                        "originatingUserUid" to userUid
                    )
                    val medOutbox = com.example.data.sync.SyncOutboxRecord(
                        branchId = activeBranch,
                        entityType = "CUSTOMER_MEDICATION",
                        entityId = "0",
                        operationType = "UPSERT",
                        payloadJson = org.json.JSONObject(medMap).toString(),
                        originatingUserUid = userUid
                    )
                    val insertedMedId = repository.insertCustomerMedicationAndOutbox(med, medOutbox).toInt()
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

    suspend fun completeCheckout(
        cartItems: List<CartItem>,
        customer: Customer?,
        overrideReason: String? = null,
        prescribingDoctor: String? = null,
        prescriptionRef: String? = null
    ): Result<Unit> {
        if (cartItems.isEmpty()) return Result.failure(Exception("Cart is empty"))
        for (item in cartItems) {
            val result = recordMedicationSale(item, customer, overrideReason, prescribingDoctor, prescriptionRef)
            if (result.isFailure) {
                return result
            }
        }
        return Result.success(Unit)
    }

    suspend fun recordMedicationSale(
        cartItem: CartItem,
        customer: Customer?,
        overrideReason: String? = null,
        prescribingDoctor: String? = null,
        prescriptionRef: String? = null
    ): Result<Unit> {
        return try {
            val inv = cartItem.inventoryItem
            val activeBranchId = _currentPharmacistBranchId.value ?: ""

            // Online atomic stock deduction check if online
            if (activeBranchId.isNotBlank()) {
                val onlineRes = repository.deductInventoryStockOnlineTransaction(activeBranchId, inv.id, cartItem.quantity)
                if (onlineRes.isFailure) {
                    val err = onlineRes.exceptionOrNull()
                    if (err is com.google.firebase.firestore.FirebaseFirestoreException && err.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.ABORTED) {
                        return Result.failure(Exception("Insufficient stock available in branch inventory."))
                    }
                }
            }

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

            val totalBatchStock = updatedBatches.sumOf { it.stockQuantity }
            val updatedItem = inv.copy(
                stockQuantity = totalBatchStock,
                totalSoldQuantity = inv.totalSoldQuantity + cartItem.quantity,
                lastSoldDate = System.currentTimeMillis()
            )

            val dateSoldMs = System.currentTimeMillis()
            val clientTxId = "SALE_${activeBranchId}_${dateSoldMs}_${java.util.UUID.randomUUID().toString().take(8)}"

            val sale = MedicationSale(
                productName = inv.name,
                brand = inv.brand,
                genericName = inv.name,
                category = inv.category,
                quantitySold = cartItem.quantity,
                dateSold = dateSoldMs,
                pharmacyNode = android.os.Build.MODEL,
                patientAge = age,
                patientGender = gender,
                patientState = state,
                patientLga = lga,
                patientCity = city,
                salePrice = inv.price * cartItem.quantity,
                batchNumber = inv.batchNumber,
                clientTransactionId = clientTxId,
                branchId = activeBranchId,
                originatingUserUid = getCurrentUserUid()
            )

            val branchName = _currentPharmacistBranchName.value ?: "Careflux"
            val ledgerEntry = InventoryLedgerEntry(
                inventoryItemId = inv.id,
                itemName = inv.name,
                batchNumber = inv.batchNumber,
                transactionType = "SALE",
                debitAccount = "CUSTOMER:POS_Checkout",
                creditAccount = "BRANCH:$branchName",
                quantity = cartItem.quantity,
                unitPrice = inv.price,
                totalValue = inv.price * cartItem.quantity,
                referenceId = clientTxId,
                notes = "Point of Sale Checkout for ${customer?.name ?: "Walk-in Patient"}",
                actorName = _currentPharmacistName.value ?: "Pharmacist",
                timestamp = dateSoldMs
            )

            val saleMap = mapOf(
                "productName" to sale.productName,
                "brand" to sale.brand,
                "genericName" to sale.genericName,
                "category" to sale.category,
                "quantitySold" to sale.quantitySold,
                "dateSold" to sale.dateSold,
                "pharmacyNode" to sale.pharmacyNode,
                "patientAge" to sale.patientAge,
                "patientGender" to sale.patientGender,
                "patientState" to sale.patientState,
                "patientLga" to sale.patientLga,
                "patientCity" to sale.patientCity,
                "salePrice" to sale.salePrice,
                "batchNumber" to sale.batchNumber,
                "clientTransactionId" to clientTxId,
                "branchId" to activeBranchId,
                "originatingUserUid" to getCurrentUserUid()
            )

            val payloadJson = org.json.JSONObject(saleMap).toString()
            val outboxRecord = com.example.data.sync.SyncOutboxRecord(
                branchId = activeBranchId,
                entityType = "SALE",
                entityId = clientTxId,
                operationType = "SALE_SYNC",
                payloadJson = payloadJson,
                clientTransactionId = clientTxId,
                originatingUserUid = getCurrentUserUid()
            )

            // Execute local checkout as ONE atomic Room transaction (sale + inventory + ledger + outbox)
            repository.executeCheckoutTransaction(updatedItem, updatedBatches, sale, ledgerEntry, outboxRecord)

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
                if (!overrideReason.isNullOrBlank()) auditMsg.append(" [Override: $overrideReason]")
                if (!prescribingDoctor.isNullOrBlank()) auditMsg.append(" [Doctor: $prescribingDoctor]")
                if (!prescriptionRef.isNullOrBlank()) auditMsg.append(" [RxRef: $prescriptionRef]")
                logAuditTrail(
                    action = "POS_CLINICAL_SALE",
                    details = auditMsg.toString(),
                    affectedId = inv.id.toString()
                )
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
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
                val firestoreId = "rescue_" + System.currentTimeMillis() + "_" + (1000..9999).random()

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

                // 2. Synchronize to Firestore
                val listingData = mapOf(
                    "firestoreId" to firestoreId,
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
                repository.upsertRemoteDocument("expiry_rescue_listings", firestoreId, listingData)
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

                // Trigger remote deletion
                repository.deleteRemoteDocument("expiry_rescue_listings", listing.firestoreId)
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
                val result = repository.claimRescueListing(listing.firestoreId, deviceId, deviceModel)
                result.fold(
                    onSuccess = { success ->
                        if (success) {
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
                    },
                    onFailure = { e ->
                        onFinished(false, "Network error: ${e.localizedMessage ?: "Could not verify claim status."}")
                    }
                )
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

                repository.upsertRemoteDocument("expiry_rescue_listings", listing.firestoreId, mapOf(
                    "quantity" to remainingQty,
                    "status" to newStatus,
                    "soldAt" to System.currentTimeMillis(),
                    "profitShareAmount" to (listing.profitShareAmount + rescuerCommission)
                ))

                // Double record medication sale for demographics analytics with correct attribution
                val deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
                val patientAge = customer?.age ?: 30
                val patientGen = customer?.gender ?: "Male"
                val patientSt = customer?.state ?: "Lagos"
                val patientLgaName = customer?.lga ?: "Ikeja"
                val patientCityVal = customer?.city ?: "Ikeja"

                val rescueActiveBranchId = _currentPharmacistBranchId.value ?: ""
                val rescueDateSold = System.currentTimeMillis()
                val rescueClientTxId = "SALE_${rescueActiveBranchId}_${rescueDateSold}_${java.util.UUID.randomUUID().toString().take(8)}"

                val sale = MedicationSale(
                    productName = listing.productName,
                    brand = "Rescued from ${listing.ownerDeviceModel}",
                    genericName = listing.productName,
                    category = "Rescue Marketplace",
                    quantitySold = qtyToSell,
                    dateSold = rescueDateSold,
                    pharmacyNode = deviceModel,
                    patientAge = patientAge,
                    patientGender = patientGen,
                    patientState = patientSt,
                    patientLga = patientLgaName,
                    patientCity = patientCityVal,
                    salePrice = totalSaleRevenue,
                    batchNumber = listing.batchNumber,
                    clientTransactionId = rescueClientTxId,
                    branchId = rescueActiveBranchId
                )
                val saleMap = mapOf(
                    "productName" to listing.productName,
                    "brand" to "Rescued from ${listing.ownerDeviceModel}",
                    "genericName" to listing.productName,
                    "category" to "Rescue Marketplace",
                    "quantitySold" to qtyToSell,
                    "dateSold" to rescueDateSold,
                    "pharmacyNode" to deviceModel,
                    "patientAge" to patientAge,
                    "patientGender" to patientGen,
                    "patientState" to patientSt,
                    "patientLga" to patientLgaName,
                    "patientCity" to patientCityVal,
                    "salePrice" to totalSaleRevenue,
                    "batchNumber" to listing.batchNumber,
                    "clientTransactionId" to rescueClientTxId,
                    "branchId" to rescueActiveBranchId,
                    "originatingUserUid" to getCurrentUserUid()
                )
                val payloadJson = org.json.JSONObject(saleMap).toString()
                val outboxRecord = com.example.data.sync.SyncOutboxRecord(
                    branchId = rescueActiveBranchId,
                    entityType = "SALE",
                    entityId = rescueClientTxId,
                    operationType = "SALE_SYNC",
                    payloadJson = payloadJson,
                    clientTransactionId = rescueClientTxId,
                    originatingUserUid = getCurrentUserUid()
                )
                repository.insertMedicationSaleAndOutbox(sale, outboxRecord)

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
                val map = mapOf(
                    "adminName" to admin,
                    "actionPerformed" to action,
                    "timestamp" to log.timestamp,
                    "affectedNodeId" to nodeId,
                    "affectedNodeModel" to nodeModel,
                    "reason" to reason
                )
                repository.addRemoteDocument("admin_audit_logs", map)
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
                val updateData = mapOf(
                    "isSuspended" to resolvedVal,
                    "statusReason" to reason,
                    "statusLastUpdated" to System.currentTimeMillis()
                )
                
                repository.upsertRemoteDocument("device_configs", nodeId, updateData)
                repository.upsertRemoteDocument("registered_pharmacists", nodeId, mapOf("isSuspended" to resolvedVal))

                logAdminAction(
                    admin = "Chinedu (Admin)",
                    action = if (resolvedVal) "SUSPEND_NODE" else "REACTIVATE_NODE",
                    nodeId = nodeId,
                    nodeModel = "Device $nodeId",
                    reason = reason
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun saveOrUpdateDeviceConfig() {
        viewModelScope.launch {
            try {
                val user = authRepository.getCurrentUser()
                val currentBranchId = _currentPharmacistBranchId.value
                val deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"

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
                if (!currentBranchId.isNullOrBlank()) {
                    dataMap["branchId"] = currentBranchId
                }
                if (user != null) {
                    dataMap["ownerEmail"] = user.email.orEmpty()
                    dataMap["ownerName"] = user.displayName.orEmpty()
                    dataMap["ownerUid"] = user.uid

                    try {
                        repository.upsertRemoteDocument(
                            "registered_pharmacists",
                            user.uid,
                            mapOf(
                                "uid" to user.uid,
                                "email" to user.email.orEmpty(),
                                "displayName" to (user.displayName ?: user.email?.substringBefore("@") ?: "Staff Pharmacist"),
                                "pharmacyName" to getPharmacyName(),
                                "lga" to getPharmacyLga(),
                                "state" to getPharmacyState()
                            )
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                repository.upsertRemoteDocument("device_configs", deviceId, dataMap)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearAllData() = viewModelScope.launch {
        repository.clearAllData()
        prefs.edit()
            .remove("cached_branch_id")
            .remove("cached_role")
            .remove("cached_user_name")
            .apply()
    }

    // --- Production Twilio Multi-Channel Messaging Gateway ---
    suspend fun sendTwilioMessage(
        phone: String,
        messageContent: String,
        messageType: String = "General",
        medicationIdOrKey: String = "general",
        forceOverrideQuietHours: Boolean = false,
        templateContentSid: String? = null
    ): com.example.util.TwilioMessagingManager.DispatchResult {
        return com.example.util.TwilioMessagingManager.dispatchMessage(
            context = getApplication(),
            dao = com.example.data.PharmacyDatabase.getDatabase(getApplication()).pharmacyDao(),
            rawPhone = phone,
            messageContent = messageContent,
            messageType = messageType,
            medicationIdOrKey = medicationIdOrKey,
            forceOverrideQuietHours = forceOverrideQuietHours,
            templateContentSid = templateContentSid
        )
    }

    suspend fun sendTwilioRefillReminder(
        patientName: String,
        phone: String,
        medicationName: String,
        dateStr: String,
        cost: Double,
        medicationId: Long = 0L,
        forceSend: Boolean = false
    ): com.example.util.TwilioMessagingManager.DispatchResult {
        val formattedCost = "%,.2f".format(cost)
        val message = "CareFlux Refill Notice:\nHello $patientName, your medication $medicationName is due for refill on $dateStr (Est. Cost: ₦$formattedCost). Stay consistent with your therapy! Reply or visit CareFlux Pharmacy to confirm."
        return sendTwilioMessage(
            phone = phone,
            messageContent = message,
            messageType = "Refill Reminder",
            medicationIdOrKey = medicationId.toString(),
            forceOverrideQuietHours = forceSend,
            templateContentSid = com.example.data.TwilioConstants.TEMPLATE_REFILL_SID
        )
    }

    suspend fun sendTwilioWelfareCheck(
        patientName: String,
        phone: String,
        wellnessQuestion: String
    ): com.example.util.TwilioMessagingManager.DispatchResult {
        val message = "CareFlux Health Check:\nHello $patientName, CareFlux Pharmacy checking up on your recovery! $wellnessQuestion Reply if you need any clinical guidance."
        return sendTwilioMessage(
            phone = phone,
            messageContent = message,
            messageType = "Welfare Check",
            medicationIdOrKey = "welfare_${System.currentTimeMillis()}"
        )
    }

    suspend fun sendTwilioDispenseConfirmation(
        patientName: String,
        phone: String,
        itemsSummary: String,
        amount: Double
    ): com.example.util.TwilioMessagingManager.DispatchResult {
        val formattedAmount = "%,.2f".format(amount)
        val message = "CareFlux Receipt Alert:\nDear $patientName, your prescription ($itemsSummary) was successfully dispensed. Total: ₦$formattedAmount. Thank you for choosing CareFlux Pharmacy!"
        return sendTwilioMessage(
            phone = phone,
            messageContent = message,
            messageType = "Dispense Receipt",
            medicationIdOrKey = "receipt_${System.currentTimeMillis()}",
            forceOverrideQuietHours = true // Immediate transactional receipt
        )
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
                val requestData = hashMapOf(
                    "deviceId" to deviceId,
                    "pharmacyName" to getPharmacyName(),
                    "lga" to getPharmacyLga(),
                    "state" to getPharmacyState(),
                    "requestedAt" to System.currentTimeMillis(),
                    "status" to "PENDING",
                    "geminiKey" to ""
                )
                repository.upsertRemoteDocument("key_creation_requests", deviceId, requestData)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun approveKeyRequest(targetDeviceId: String, gemini: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                repository.upsertRemoteDocument(
                    "key_creation_requests",
                    targetDeviceId,
                    mapOf(
                        "status" to "APPROVED",
                        "geminiKey" to gemini
                    )
                )
                
                repository.upsertRemoteDocument(
                    "device_configs",
                    targetDeviceId,
                    mapOf(
                        "customGeminiApiKey" to gemini
                    )
                )
                
                val logId = java.util.UUID.randomUUID().toString()
                repository.upsertRemoteDocument(
                    "admin_audit_logs",
                    logId,
                    mapOf(
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
                repository.upsertRemoteDocument(
                    "key_creation_requests",
                    targetDeviceId,
                    mapOf("status" to "REJECTED")
                )
                
                val logId = java.util.UUID.randomUUID().toString()
                repository.upsertRemoteDocument(
                    "admin_audit_logs",
                    logId,
                    mapOf(
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
                    repository.upsertRemoteDocument("registered_pharmacists", node["uid"].toString(), node)
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

                val seedBranchId = _currentPharmacistBranchId.value ?: ""
                for ((idx, sale) in sales.withIndex()) {
                    val dateSold = sale["dateSold"] as? Long ?: System.currentTimeMillis()
                    val clientTxId = "SALE_SEED_${seedBranchId}_${dateSold}_$idx"
                    val mutableSale = sale.toMutableMap()
                    mutableSale["branchId"] = seedBranchId
                    mutableSale["clientTransactionId"] = clientTxId
                    repository.upsertRemoteDocument("medication_sales", clientTxId, mutableSale)
                }

                android.util.Log.d("PharmacyViewModel", "Simulated cooperative nodes and sales successfully seeded!")

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- Branch Realtime Synchronization, Security & Auditing Logic ---
    
    fun handleUserAuthenticated(user: com.example.data.auth.AuthUser) {
        try {
            userProfileJob?.cancel()
            userProfileJob = viewModelScope.launch {
                repository.observePharmacist(user.uid)
                    .catch { e -> android.util.Log.w("PharmacyViewModel", "observePharmacist failed: ${e.localizedMessage}") }
                    .collect { snapshot ->
                    if (snapshot != null) {
                        val bId = snapshot["branchId"] as? String ?: ""
                        val bName = snapshot["branchName"] as? String ?: "Careflux Rx"
                        val role = snapshot["role"] as? String ?: "Pharmacist"
                        val displayName = snapshot["displayName"] as? String ?: user.displayName ?: "Staff Pharmacist"
                        val phoneNumber = snapshot["phoneNumber"] as? String ?: "+2348000000000"

                        _currentPharmacistBranchId.value = bId
                        _currentPharmacistBranchName.value = bName
                        _currentPharmacistRole.value = role
                        _currentPharmacistName.value = displayName
                        _currentPharmacistPhone.value = phoneNumber
                        _isProfileLoading.value = false

                        prefs.edit()
                            .putString("cached_branch_id", bId)
                            .putString("cached_branch_name", bName)
                            .putString("cached_role", role)
                            .putString("cached_name", displayName)
                            .putString("cached_phone", phoneNumber)
                            .apply()

                        // Register active device & FCM token binding
                        viewModelScope.launch {
                            try {
                                deviceRepository.handleUserAuthenticated(user.uid, role, bId)
                            } catch (e: Exception) {
                                // Fail-safe
                            }
                        }

                        if (bId.isNotEmpty() && bId != activeSyncBranchId) {
                            activeSyncBranchId = bId
                            setupBranchRealtimeSync(bId)
                            triggerImmediateSync()
                        }
                    } else {
                        _isProfileLoading.value = false
                        val devId = deviceId
                        val deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
                        val userEmail = user.email.orEmpty()

                        if (userEmail.isNotBlank()) {
                            val existingRes = repository.getRemoteDocumentsWhereEquals("registered_pharmacists", "email", userEmail)
                            val existingDoc = existingRes.getOrNull()?.firstOrNull()
                            val existingRole = existingDoc?.get("role") as? String ?: "Pharmacist"
                            val existingBranchId = existingDoc?.get("branchId") as? String ?: ""
                            val existingBranchName = existingDoc?.get("branchName") as? String ?: ""
                            val existingApproved = existingDoc?.get("isApproved") as? Boolean ?: true

                            val defaultMap = hashMapOf<String, Any?>(
                                "uid" to user.uid,
                                "email" to userEmail,
                                "displayName" to (user.displayName ?: userEmail.substringBefore("@") ?: "Staff Pharmacist"),
                                "deviceId" to devId,
                                "deviceModel" to deviceModel,
                                "phoneNumber" to "+2348000000000",
                                "registeredAt" to System.currentTimeMillis(),
                                "lastLoginAt" to System.currentTimeMillis(),
                                "branchId" to existingBranchId,
                                "branchName" to existingBranchName,
                                "role" to existingRole,
                                "isApproved" to existingApproved
                            )
                            _currentPharmacistRole.value = existingRole
                            if (existingBranchId.isNotBlank()) {
                                _currentPharmacistBranchId.value = existingBranchId
                                _currentPharmacistBranchName.value = existingBranchName
                            }
                            repository.upsertRemoteDocument("registered_pharmacists", user.uid, defaultMap)

                            // Register active device & FCM token binding
                            viewModelScope.launch {
                                try {
                                    deviceRepository.handleUserAuthenticated(user.uid, existingRole, existingBranchId)
                                } catch (e: Exception) {
                                    // Fail-safe
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupBranchRealtimeSync(userBranchId: String) {
        try {
            val currentGen = branchGenerationToken.get()
            // Run self-healing local deduplication immediately on sync setup
            deduplicateLocalInventory()

            // Step 1: Remove existing listen channels to prevent leakages
            activeSyncJobs.forEach { it.cancel() }
            activeSyncJobs.clear()

            // Job 0: Sync Branch Feature Toggles and Settings in realtime
            val job0 = viewModelScope.launch {
                repository.observeBranchSettings(userBranchId)
                    .catch { e -> android.util.Log.w("PharmacyViewModel", "observeBranchSettings failed: ${e.localizedMessage}") }
                    .collect { snapshot ->
                        if (branchGenerationToken.get() != currentGen) {
                            android.util.Log.d("PharmacyViewModel", "Stale generation ($currentGen vs ${branchGenerationToken.get()}), ignoring observeBranchSettings")
                            return@collect
                        }
                        if (snapshot != null) {
                            _isAiContentEnabled.value = snapshot["aiContentEnabled"] as? Boolean ?: true
                            _isCarefluxAiEnabled.value = snapshot["carefluxAiEnabled"] as? Boolean ?: true
                            _isClinicalEnabled.value = snapshot["clinicalEnabled"] as? Boolean ?: true
                            _isMessagingEnabled.value = snapshot["messagingEnabled"] as? Boolean ?: true
                            _isTriageEnabled.value = snapshot["triageEnabled"] as? Boolean ?: true
                            _isMarketplaceEnabled.value = snapshot["marketplaceEnabled"] as? Boolean ?: true
                            _isProcurementEnabled.value = snapshot["procurementEnabled"] as? Boolean ?: true
                        }
                    }
            }
            activeSyncJobs.add(job0)

            // Job 1: Sync staff members in the same branch in realtime (for Manager view)
            val job1 = viewModelScope.launch {
                repository.observeStaffMembers(userBranchId)
                    .catch { e -> android.util.Log.w("PharmacyViewModel", "observeStaffMembers failed: ${e.localizedMessage}") }
                    .collect { list ->
                        if (branchGenerationToken.get() != currentGen) {
                            android.util.Log.d("PharmacyViewModel", "Stale generation ($currentGen vs ${branchGenerationToken.get()}), ignoring observeStaffMembers")
                            return@collect
                        }
                        _branchStaffList.value = list
                    }
            }
            activeSyncJobs.add(job1)

            // Job 2: Real-time Branch Stock Coordination
            val job2 = viewModelScope.launch {
                repository.observeBranchInventory(userBranchId)
                    .catch { e -> android.util.Log.w("PharmacyViewModel", "observeBranchInventory failed: ${e.localizedMessage}") }
                    .collect { rawList ->
                        if (branchGenerationToken.get() != currentGen) {
                            android.util.Log.d("PharmacyViewModel", "Stale generation ($currentGen vs ${branchGenerationToken.get()}), ignoring observeBranchInventory")
                            return@collect
                        }
                    try {
                        val remoteItems = rawList.mapNotNull { data ->
                            val docBranchId = data["branchId"] as? String
                            if (docBranchId.isNullOrBlank() || docBranchId != userBranchId) return@mapNotNull null
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
                            val lastUpdated = (data["lastUpdated"] as? Number)?.toLong() ?: 0L
                            val lastReconciledAt = (data["lastReconciledAt"] as? Number)?.toLong() ?: 0L

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
                                lastUpdated = lastUpdated,
                                lastReconciledAt = lastReconciledAt,
                                branchId = docBranchId,
                                originatingUserUid = data["originatingUserUid"] as? String ?: ""
                            )
                        }

                        var needsDeduplicate = false
                        remoteItems.forEach { remote ->
                            val local = repository.getInventoryItemById(remote.id)
                            if (local == null || remote.lastUpdated > local.lastUpdated) {
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
            activeSyncJobs.add(job2)

            // Job 3: Realtime Branch Customers Sync
            val job3 = viewModelScope.launch {
                repository.observeBranchCustomers(userBranchId).collect { rawList ->
                    if (branchGenerationToken.get() != currentGen) {
                        android.util.Log.d("PharmacyViewModel", "Stale generation ($currentGen vs ${branchGenerationToken.get()}), ignoring observeBranchCustomers")
                        return@collect
                    }
                    try {
                        val remoteList = rawList.mapNotNull { data ->
                            val docBranchId = data["branchId"] as? String
                            if (docBranchId.isNullOrBlank() || docBranchId != userBranchId) return@mapNotNull null
                            val id = (data["id"] as? Number)?.toInt() ?: return@mapNotNull null
                            val name = data["name"] as? String ?: ""
                            val phoneNumber = data["phoneNumber"] as? String ?: ""
                            val email = data["email"] as? String ?: ""
                            val notes = data["notes"] as? String ?: ""
                            val loyaltyPoints = (data["loyaltyPoints"] as? Number)?.toInt() ?: 0
                            val refillStreak = (data["refillStreak"] as? Number)?.toInt() ?: 0
                            val dateAdded = (data["dateAdded"] as? Number)?.toLong() ?: 0L
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
                                dateAdded = if (dateAdded > 0L) dateAdded else System.currentTimeMillis(),
                                age = age,
                                gender = gender,
                                state = state,
                                lga = lga,
                                city = city,
                                branchId = docBranchId,
                                originatingUserUid = data["originatingUserUid"] as? String ?: ""
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
            activeSyncJobs.add(job3)

            // Job 3b: Realtime Branch Customer Medications Sync
            val job3b = viewModelScope.launch {
                repository.observeBranchCustomerMedications(userBranchId).collect { rawList ->
                    if (branchGenerationToken.get() != currentGen) {
                        android.util.Log.d("PharmacyViewModel", "Stale generation ($currentGen vs ${branchGenerationToken.get()}), ignoring observeBranchCustomerMedications")
                        return@collect
                    }
                    try {
                        rawList.forEach { data ->
                            val docBranchId = data["branchId"] as? String
                            if (docBranchId.isNullOrBlank() || docBranchId != userBranchId) return@forEach
                            val id = (data["id"] as? Number)?.toInt() ?: return@forEach
                            val customerId = (data["customerId"] as? Number)?.toInt() ?: return@forEach
                            val inventoryItemId = (data["inventoryItemId"] as? Number)?.toInt() ?: 0
                            val medicationName = data["medicationName"] as? String ?: ""
                            val customDosage = data["customDosage"] as? String ?: ""
                            val cost = (data["cost"] as? Number)?.toDouble() ?: 0.0
                            val cycleDays = (data["cycleDays"] as? Number)?.toInt() ?: 30
                            val nextRefillDate = (data["nextRefillDate"] as? Number)?.toLong() ?: 0L

                            val remoteMed = com.example.data.CustomerMedication(
                                id = id,
                                customerId = customerId,
                                inventoryItemId = inventoryItemId,
                                medicationName = medicationName,
                                customDosage = customDosage,
                                cost = cost,
                                cycleDays = cycleDays,
                                nextRefillDate = if (nextRefillDate > 0L) nextRefillDate else System.currentTimeMillis(),
                                branchId = docBranchId,
                                originatingUserUid = data["originatingUserUid"] as? String ?: ""
                            )
                            repository.insertCustomerMedication(remoteMed)
                        }
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                }
            }
            activeSyncJobs.add(job3b)

            // Job 3c: Realtime Branch Clinical Interventions Sync
            val job3c = viewModelScope.launch {
                repository.observeBranchInterventions(userBranchId).collect { rawList ->
                    if (branchGenerationToken.get() != currentGen) {
                        android.util.Log.d("PharmacyViewModel", "Stale generation ($currentGen vs ${branchGenerationToken.get()}), ignoring observeBranchInterventions")
                        return@collect
                    }
                    try {
                        rawList.forEach { data ->
                            val docBranchId = data["branchId"] as? String
                            if (docBranchId.isNullOrBlank() || docBranchId != userBranchId) return@forEach
                            val id = (data["id"] as? Number)?.toInt() ?: return@forEach
                            val customerId = (data["customerId"] as? Number)?.toInt() ?: return@forEach
                            val presentation = data["presentation"] as? String ?: ""
                            val testResults = data["testResults"] as? String ?: ""
                            val recommendation = data["recommendation"] as? String ?: ""
                            val currentStatus = data["currentStatus"] as? String ?: "Pending"
                            val followUpDay3Sent = data["followUpDay3Sent"] as? Boolean ?: false
                            val followUpDay7Sent = data["followUpDay7Sent"] as? Boolean ?: false
                            val followUpDay14Sent = data["followUpDay14Sent"] as? Boolean ?: false
                            val dateAdded = (data["dateAdded"] as? Number)?.toLong() ?: 0L

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
                                    dateAdded = if (dateAdded > 0L) dateAdded else System.currentTimeMillis(),
                                    branchId = docBranchId,
                                    originatingUserUid = data["originatingUserUid"] as? String ?: ""
                                )
                            )
                        }
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                }
            }
            activeSyncJobs.add(job3c)

            // Job 4: Realtime Branch Operations Tasks
            val job4 = viewModelScope.launch {
                repository.observeBranchOperationTasks(userBranchId).collect { rawList ->
                    if (branchGenerationToken.get() != currentGen) {
                        android.util.Log.d("PharmacyViewModel", "Stale generation ($currentGen vs ${branchGenerationToken.get()}), ignoring observeBranchOperationTasks")
                        return@collect
                    }
                    try {
                        val remoteList = rawList.mapNotNull { data ->
                            val id = (data["id"] as? Number)?.toInt() ?: return@mapNotNull null
                            val title = data["title"] as? String ?: ""
                            val description = data["description"] as? String ?: ""
                            val urgency = data["urgency"] as? String ?: "Medium"
                            val category = data["category"] as? String ?: "Manual"
                            val isCompleted = data["isCompleted"] as? Boolean ?: false
                            val createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L

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
                                createdAt = if (createdAt > 0L) createdAt else System.currentTimeMillis(),
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
                                assignedToUid = if (assignedToUid.isNullOrEmpty()) null else assignedToUid,
                                branchId = data["branchId"] as? String ?: userBranchId,
                                originatingUserUid = data["originatingUserUid"] as? String ?: ""
                            )
                        }

                        val currentUid = authRepository.getCurrentUser()?.uid
                        val isManager = _currentPharmacistRole.value == "Branch Manager" || isCurrentUserAdmin()

                        remoteList.forEach { remote ->
                            val local = repository.getOperationTaskById(remote.id)

                            if (isFirstTaskSyncDone) {
                                // 1. Notify pharmacist if task is assigned to them
                                if (remote.assignedToUid == currentUid && remote.assignedToUid?.isNotEmpty() == true) {
                                    if (local == null) {
                                        showLocalNotification(
                                            title = "New Task Assigned",
                                            content = "You have been assigned a new task: ${remote.title}.",
                                            targetTab = "branch_team",
                                            targetSubTab = "ops_task_board",
                                            targetTaskId = remote.id.toLong()
                                        )
                                    } else if (local.assignedToUid != remote.assignedToUid) {
                                        showLocalNotification(
                                            title = "Task Reassigned to You",
                                            content = "Task '${remote.title}' is now assigned to you.",
                                            targetTab = "branch_team",
                                            targetSubTab = "ops_task_board",
                                            targetTaskId = remote.id.toLong()
                                        )
                                    }
                                }

                                // 2. Notify manager if a task is completed
                                if (isManager) {
                                    if (remote.isCompleted && (local == null || !local.isCompleted)) {
                                        showLocalNotification(
                                            title = "Task Completed by Staff",
                                            content = "Task '${remote.title}' has been marked completed by ${remote.verifiedBy ?: "staff"}.",
                                            targetTab = "branch_team",
                                            targetSubTab = "ops_task_board",
                                            targetTaskId = remote.id.toLong()
                                        )
                                    }
                                }

                                // 3. Notify manager/staff about new incoming Stock Transfer or unassigned manager task
                                if (local == null) {
                                    if (remote.category == "Stock Transfer") {
                                        showLocalNotification(
                                            title = "Incoming Stock Transfer",
                                            content = remote.description,
                                            targetTab = "branch_team",
                                            targetSubTab = "ops_task_board",
                                            targetTaskId = remote.id.toLong()
                                        )
                                    } else if (isManager && remote.assignedToName == "Branch Manager" && remote.assignedToUid.isNullOrEmpty()) {
                                        showLocalNotification(
                                            title = "New Manager Task",
                                            content = "A new task requires your attention: ${remote.title}.",
                                            targetTab = "branch_team",
                                            targetSubTab = "ops_task_board",
                                            targetTaskId = remote.id.toLong()
                                        )
                                    }
                                }
                            }

                            if (local != null && local.isCompleted && !remote.isCompleted) {
                                // Task is already completed locally, but remote Firestore has stale uncompleted status.
                                // Preserve local completed state and push back to Firestore.
                                val taskMap = mapOf(
                                    "id" to local.id,
                                    "title" to local.title,
                                    "description" to local.description,
                                    "urgency" to local.urgency,
                                    "category" to local.category,
                                    "isCompleted" to true,
                                    "createdAt" to local.createdAt,
                                    "branchId" to userBranchId,
                                    "assignedToName" to (local.assignedToName ?: ""),
                                    "assignedToUid" to (local.assignedToUid ?: ""),
                                    "verifiedBy" to (local.verifiedBy ?: ""),
                                    "verificationNotes" to (local.verificationNotes ?: ""),
                                    "verificationChannel" to (local.verificationChannel ?: ""),
                                    "verificationCustomerName" to (local.verificationCustomerName ?: ""),
                                    "verifiedAt" to (local.verifiedAt ?: 0L),
                                    "isApproved" to local.isApproved,
                                    "approvedBy" to (local.approvedBy ?: ""),
                                    "approvedAt" to (local.approvedAt ?: 0L),
                                    "approvalNotes" to (local.approvalNotes ?: "")
                                )
                                syncEntityToFirestore("branch_operation_tasks", local.id.toString(), taskMap)
                            } else {
                                repository.insertOperationTask(remote)
                            }
                        }
                        isFirstTaskSyncDone = true
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                }
            }
            activeSyncJobs.add(job4)

            // Job 5: Realtime Branch Receipts Sync
            val job5 = viewModelScope.launch {
                repository.observeBranchReceipts(userBranchId).collect { rawList ->
                    if (branchGenerationToken.get() != currentGen) {
                        android.util.Log.d("PharmacyViewModel", "Stale generation ($currentGen vs ${branchGenerationToken.get()}), ignoring observeBranchReceipts")
                        return@collect
                    }
                    try {
                        rawList.forEach { data ->
                            val id = (data["id"] as? Number)?.toInt() ?: return@forEach
                            val timestamp = (data["timestamp"] as? Number)?.toLong() ?: 0L
                            val customerName = data["customerName"] as? String ?: ""
                            val totalAmount = (data["totalAmount"] as? Number)?.toDouble() ?: 0.0
                            val imageFileName = data["imageFileName"] as? String ?: ""
                            val isInvoice = data["isInvoice"] as? Boolean ?: false
                            val paymentStatus = data["paymentStatus"] as? String ?: "Paid"
                            val orderId = data["orderId"] as? String ?: ""

                            repository.insertReceipt(
                                com.example.data.Receipt(
                                    id = id,
                                    timestamp = if (timestamp > 0L) timestamp else System.currentTimeMillis(),
                                    customerName = customerName,
                                    totalAmount = totalAmount,
                                    imageFileName = imageFileName,
                                    isInvoice = isInvoice,
                                    paymentStatus = paymentStatus,
                                    orderId = orderId,
                                    branchId = data["branchId"] as? String ?: userBranchId,
                                    originatingUserUid = data["originatingUserUid"] as? String ?: ""
                                )
                            )
                        }
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                }
            }
            activeSyncJobs.add(job5)

            // Job 6: Realtime Branch Audit Logs Sync (Role-Based Partitioning)
            val isManagerOrAdmin = _currentPharmacistRole.value == "Branch Manager" || isCurrentUserAdmin()
            val auditBranch = if (isManagerOrAdmin) "" else userBranchId
            val job6 = viewModelScope.launch {
                repository.observeBranchAuditLogs(auditBranch).collect { logs ->
                    if (branchGenerationToken.get() != currentGen) {
                        android.util.Log.d("PharmacyViewModel", "Stale generation ($currentGen vs ${branchGenerationToken.get()}), ignoring observeBranchAuditLogs")
                        return@collect
                    }
                    _branchTransfers.value = logs
                }
            }
            activeSyncJobs.add(job6)

            // Job 7: Realtime Branch Medication Sales Sync (Tenant-Isolated)
            val salesBranch = if (isCurrentUserAdmin()) "" else userBranchId
            val job7 = viewModelScope.launch {
                repository.observeMedicationSales(salesBranch)
                    .catch { e -> android.util.Log.w("PharmacyViewModel", "observeMedicationSales failed: ${e.localizedMessage}") }
                    .collect { list ->
                        if (branchGenerationToken.get() != currentGen) {
                            android.util.Log.d("PharmacyViewModel", "Stale generation ($currentGen vs ${branchGenerationToken.get()}), ignoring observeMedicationSales")
                            return@collect
                        }
                        for (data in list) {
                            val name = data["productName"] as? String ?: ""
                            val brand = data["brand"] as? String ?: ""
                            val gen = data["genericName"] as? String ?: ""
                            val cat = data["category"] as? String ?: ""
                            val qty = (data["quantitySold"] as? Number)?.toInt() ?: 0
                            val dSold = (data["dateSold"] as? Number)?.toLong() ?: System.currentTimeMillis()
                            val node = data["pharmacyNode"] as? String ?: ""
                            val age = (data["patientAge"] as? Number)?.toInt() ?: 30
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
                                        batchNumber = batchNum,
                                        clientTransactionId = data["clientTransactionId"] as? String ?: "",
                                        branchId = data["branchId"] as? String ?: userBranchId,
                                        originatingUserUid = data["originatingUserUid"] as? String ?: ""
                                    )
                                )
                            }
                        }
                    }
            }
            activeSyncJobs.add(job7)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private var pendingSyncCount = java.util.concurrent.atomic.AtomicInteger(0)
    private var syncStateJob: kotlinx.coroutines.Job? = null

    fun syncEntityToFirestore(collectionName: String, docId: String, dataMap: Map<String, Any?>) {
        // No-op: Local mutations write to Room + sync_outbox. CloudSyncWorker drains outbox authoritatively.
    }

    private fun syncCustomerToBranch(customer: com.example.data.Customer) {
        // No-op: Handled via insertCustomerAndOutbox
    }

    private fun syncCustomerMedicationToBranch(med: com.example.data.CustomerMedication) {
        // No-op: Handled via insertCustomerMedicationAndOutbox
    }

    private fun syncClinicalInterventionToBranch(inter: com.example.data.ClinicalIntervention) {
        // No-op: Handled via insertClinicalInterventionAndOutbox
    }

    fun deleteEntityFromFirestore(collectionName: String, docId: String) {
        val branchId = _currentPharmacistBranchId.value ?: return
        viewModelScope.launch {
            try {
                repository.deleteRemoteDocument(collectionName, "${branchId}_$docId")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun logAuditTrail(action: String, details: String, affectedId: String = "") {
        val branchId = _currentPharmacistBranchId.value ?: "Self"
        val userName = _currentPharmacistName.value ?: "Staff Pharmacist"
        val userRole = _currentPharmacistRole.value ?: "Pharmacist"
        val userUid = authRepository.getCurrentUser()?.uid ?: "LocalNode"
        
        viewModelScope.launch {
            try {
                val auditMap = hashMapOf<String, Any?>(
                    "branchId" to branchId,
                    "uid" to userUid,
                    "displayName" to userName,
                    "role" to userRole,
                    "action" to action,
                    "timestamp" to System.currentTimeMillis(),
                    "details" to details,
                    "affectedId" to affectedId
                )
                repository.addRemoteDocument("branch_audit_logs", auditMap)
                
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
            saveAndSyncInventoryItemDirectly(updated)
            
            // Deduct batches at source branch (FEFO / lot deduction)
            try {
                val batches = repository.getBatchesForItem(item.id).firstOrNull() ?: emptyList()
                if (batches.isNotEmpty()) {
                    var remainingToDeduct = quantity
                    val sortedBatches = batches.sortedWith(compareBy({ it.expiryDate }, { it.id }))
                    for (batch in sortedBatches) {
                        if (remainingToDeduct <= 0) break
                        if (batch.stockQuantity <= 0) continue
                        val deductFromThis = minOf(batch.stockQuantity, remainingToDeduct)
                        val updatedBatch = batch.copy(stockQuantity = batch.stockQuantity - deductFromThis)
                        repository.updateInventoryBatch(updatedBatch)
                        remainingToDeduct -= deductFromThis
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

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
            
            val currentBranchName = _currentPharmacistBranchName.value ?: "Careflux"
            recordDoubleEntryLedger(
                itemId = item.id,
                itemName = item.name,
                batchNumber = item.batchNumber,
                transactionType = "BRANCH_TRANSFER",
                debitAccount = "BRANCH:$destinationBranch",
                creditAccount = "BRANCH:$currentBranchName",
                quantity = quantity,
                unitPrice = item.price,
                referenceId = "TRANSFER_${item.id}_${System.currentTimeMillis()}",
                notes = "Branch transfer to $destinationBranch. Reason: $reason"
            )

            logAuditTrail(
                action = "TRANSFER",
                details = "Transferred $quantity units of ${item.name} (${item.dosage}) to branch: '$destinationBranch'. Reason: $reason",
                affectedId = item.id.toString()
            )

            // Automate double-ended Task insertion with structured transfer payload
            val payload = com.example.util.StockTransferPayload(
                sourceGlobalId = item.globalId,
                sourceItemId = item.id,
                name = item.name,
                dosage = item.dosage,
                unitForm = item.unitForm,
                brand = item.brand,
                category = item.category,
                batchNumber = item.batchNumber,
                expiryDate = item.expiryDate,
                price = item.price,
                quantity = quantity,
                fromBranch = currentBranchName,
                destinationBranch = destinationBranch.trim(),
                reason = reason
            )

            try {
                val transferTaskId = (100000..999999).random()
                val transferTaskMap = mapOf(
                    "id" to transferTaskId,
                    "title" to "INCOMING STOCK TRANSFER",
                    "description" to payload.encodeToTaskDescription(),
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
                repository.upsertRemoteDocument("branch_operation_tasks", transferTaskId.toString(), transferTaskMap)

                val taskOutbox = com.example.data.sync.SyncOutboxRecord(
                    branchId = destinationBranch.trim(),
                    entityType = "TASK",
                    entityId = transferTaskId.toString(),
                    operationType = "UPSERT",
                    payloadJson = org.json.JSONObject(transferTaskMap).toString(),
                    originatingUserUid = getCurrentUserUid()
                )
                repository.insertOperationTaskAndOutbox(
                    com.example.data.OperationTask(
                        id = transferTaskId,
                        title = "INCOMING STOCK TRANSFER",
                        description = payload.encodeToTaskDescription(),
                        urgency = "High",
                        category = "Stock Transfer",
                        isCompleted = false,
                        createdAt = System.currentTimeMillis(),
                        branchId = destinationBranch.trim(),
                        assignedToName = "Branch Manager"
                    ),
                    taskOutbox
                )
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
        val updaterName = _currentPharmacistName.value ?: "Staff Pharmacist"
        val currentBranch = _currentPharmacistBranchId.value ?: _currentPharmacistBranchName.value ?: ""
        
        // Tenant boundary check: ensure user has rights to receive tasks for this branch
        if (task.branchId.isNotBlank() && currentBranch.isNotBlank() &&
            !task.branchId.equals(currentBranch, ignoreCase = true) &&
            !isCurrentUserAdmin()
        ) {
            onFinished(false, "Unauthorized: You do not have permission to receive stock for ${task.branchId}.")
            return
        }

        if (task.isCompleted) {
            onFinished(false, "Transfer task has already been processed and finalized.")
            return
        }

        // Decode and validate structured transfer payload
        val payload = com.example.util.StockTransferPayload.decodeFromDescription(task.description)
        if (payload == null || payload.quantity <= 0 || payload.name.isBlank()) {
            onFinished(false, "Invalid or unresolvable stock transfer payload.")
            return
        }

        // 1. Instantly update task in local Room database
        val updatedTask = task.copy(
            isCompleted = true,
            isApproved = true,
            verifiedBy = updaterName,
            verifiedAt = System.currentTimeMillis(),
            verificationNotes = "Received and verified successfully. Notes: $notes",
            verificationChannel = "Transfer Sync",
            verificationCustomerName = "Source: ${payload.fromBranch}",
            approvedBy = updaterName,
            approvedAt = System.currentTimeMillis(),
            approvalNotes = "Confirmed receipt of ${payload.quantity} units of ${payload.name} (${payload.dosage})."
        )

        viewModelScope.launch {
            updateOperationTaskAndOutboxHelper(updatedTask)
        }

        // 2. Immediately notify UI so modal dismisses instantly (<16ms)
        onFinished(true, "Successfully received stock and finalized transfer.")

        // 3. Process deterministic inventory resolution, batch mutation, ledger entry, Firestore sync, and audit trail
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val branchItems = repository.allInventoryItems.first().filter { 
                    it.branchId.isBlank() || it.branchId.equals(currentBranch, ignoreCase = true)
                }

                // Deterministic resolution: match exact variant identity
                val matchedItem = com.example.util.StockTransferPayload.resolveMatchingInventoryItem(branchItems, payload)
                
                val destinationItemId: Int
                val finalUpdatedItem: com.example.data.InventoryItem
                
                if (matchedItem != null) {
                    val updatedQty = matchedItem.stockQuantity + payload.quantity
                    finalUpdatedItem = matchedItem.copy(
                        stockQuantity = updatedQty,
                        lastUpdated = System.currentTimeMillis()
                    )
                    saveAndSyncInventoryItemDirectly(finalUpdatedItem)
                    destinationItemId = finalUpdatedItem.id
                } else {
                    val newItem = com.example.data.InventoryItem(
                        id = 0,
                        name = payload.name,
                        dosage = payload.dosage,
                        unitForm = if (payload.unitForm.isNotBlank()) payload.unitForm else "Tablet",
                        brand = if (payload.brand.isNotBlank()) payload.brand else "Standard",
                        category = if (payload.category.isNotBlank()) payload.category else "General",
                        price = if (payload.price > 0.0) payload.price else 0.0,
                        stockQuantity = payload.quantity,
                        minRequiredStock = 5,
                        batchNumber = if (payload.batchNumber.isNotBlank()) payload.batchNumber else "TX-${System.currentTimeMillis() % 10000}",
                        expiryDate = if (payload.expiryDate > 0L) payload.expiryDate else (System.currentTimeMillis() + (365L * 24L * 60L * 60L * 1000L)),
                        supplier = if (payload.fromBranch.isNotBlank()) payload.fromBranch else "Branch Transfer",
                        branchId = currentBranch,
                        globalId = java.util.UUID.randomUUID().toString(),
                        lastUpdated = System.currentTimeMillis()
                    )
                    destinationItemId = saveAndSyncInventoryItemDirectly(newItem)
                    finalUpdatedItem = newItem.copy(id = destinationItemId)
                }

                // Deterministic batch association: create or update InventoryBatch at destination
                val batches = repository.getBatchesForItem(destinationItemId).firstOrNull() ?: emptyList()
                val existingBatch = if (payload.batchNumber.isNotBlank()) {
                    batches.find { it.batchNumber.trim().equals(payload.batchNumber.trim(), ignoreCase = true) }
                } else null

                if (existingBatch != null) {
                    val updatedBatch = existingBatch.copy(
                        stockQuantity = existingBatch.stockQuantity + payload.quantity,
                        expiryDate = if (payload.expiryDate > 0L) payload.expiryDate else existingBatch.expiryDate
                    )
                    repository.updateInventoryBatch(updatedBatch)
                } else {
                    val newBatch = com.example.data.InventoryBatch(
                        id = 0,
                        inventoryItemId = destinationItemId,
                        batchNumber = if (payload.batchNumber.isNotBlank()) payload.batchNumber else "TX-RECEIVED",
                        stockQuantity = payload.quantity,
                        expiryDate = if (payload.expiryDate > 0L) payload.expiryDate else (System.currentTimeMillis() + (365L * 24L * 60L * 60L * 1000L)),
                        price = if (payload.price > 0.0) payload.price else finalUpdatedItem.price
                    )
                    repository.insertInventoryBatch(newBatch)
                }
                
                // Sync inventory item to Firestore
                val itemMap = mapOf(
                    "id" to finalUpdatedItem.id,
                    "name" to finalUpdatedItem.name,
                    "dosage" to finalUpdatedItem.dosage,
                    "stockQuantity" to finalUpdatedItem.stockQuantity,
                    "minRequiredStock" to finalUpdatedItem.minRequiredStock,
                    "category" to finalUpdatedItem.category,
                    "price" to finalUpdatedItem.price,
                    "expiryDate" to finalUpdatedItem.expiryDate,
                    "batchNumber" to finalUpdatedItem.batchNumber,
                    "supplier" to finalUpdatedItem.supplier,
                    "unitForm" to finalUpdatedItem.unitForm,
                    "lastSoldDate" to finalUpdatedItem.lastSoldDate,
                    "totalSoldQuantity" to finalUpdatedItem.totalSoldQuantity,
                    "brand" to finalUpdatedItem.brand,
                    "salesStrategy" to finalUpdatedItem.salesStrategy,
                    "lastUpdated" to finalUpdatedItem.lastUpdated,
                    "branchId" to currentBranch
                )
                syncEntityToFirestore("branch_inventory", finalUpdatedItem.id.toString(), itemMap)
                
                if (currentBranch.isNotEmpty()) {
                    val taskMap = mapOf(
                        "id" to updatedTask.id,
                        "title" to updatedTask.title,
                        "description" to updatedTask.description,
                        "urgency" to updatedTask.urgency,
                        "category" to updatedTask.category,
                        "isCompleted" to updatedTask.isCompleted,
                        "createdAt" to updatedTask.createdAt,
                        "branchId" to currentBranch,
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

                // Record double-entry ledger for receiving branch
                recordDoubleEntryLedger(
                    itemId = destinationItemId,
                    itemName = finalUpdatedItem.name,
                    batchNumber = if (payload.batchNumber.isNotBlank()) payload.batchNumber else finalUpdatedItem.batchNumber,
                    transactionType = "TRANSFER_RECEIPT",
                    debitAccount = "BRANCH:$currentBranch",
                    creditAccount = "BRANCH:${payload.fromBranch}",
                    quantity = payload.quantity,
                    unitPrice = finalUpdatedItem.price,
                    referenceId = "RECEIPT_${destinationItemId}_${task.id}_${System.currentTimeMillis()}",
                    notes = "Stock transfer receipt of ${payload.quantity} units from ${payload.fromBranch}. Notes: $notes"
                )
                
                logAuditTrail(
                    action = "TRANSFER_RECEIVED",
                    details = "Successfully verified and received stock transfer of ${payload.quantity} units of ${finalUpdatedItem.name} (${finalUpdatedItem.dosage} • ${finalUpdatedItem.unitForm}, Batch: ${payload.batchNumber}). Notes: $notes",
                    affectedId = destinationItemId.toString()
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun performBulkBranchTransfer(transfers: List<Pair<com.example.data.InventoryItem, Int>>, destinationBranch: String, reason: String) {
        viewModelScope.launch {
            var successCount = 0
            var failCount = 0
            val stringBuilder = StringBuilder()
            val currentBranchName = _currentPharmacistBranchName.value ?: "Careflux"
            
            transfers.forEach { (item, quantity) ->
                if (item.stockQuantity < quantity) {
                    failCount++
                    stringBuilder.append("• ${item.name}: Insufficient stock\n")
                    return@forEach
                }
                
                val newQty = item.stockQuantity - quantity
                val updated = item.copy(stockQuantity = newQty, lastUpdated = System.currentTimeMillis())
                saveAndSyncInventoryItemDirectly(updated)
                
                // Deduct batches at source
                try {
                    val batches = repository.getBatchesForItem(item.id).firstOrNull() ?: emptyList()
                    if (batches.isNotEmpty()) {
                        var remainingToDeduct = quantity
                        val sortedBatches = batches.sortedWith(compareBy({ it.expiryDate }, { it.id }))
                        for (batch in sortedBatches) {
                            if (remainingToDeduct <= 0) break
                            if (batch.stockQuantity <= 0) continue
                            val deductFromThis = minOf(batch.stockQuantity, remainingToDeduct)
                            val updatedBatch = batch.copy(stockQuantity = batch.stockQuantity - deductFromThis)
                            repository.updateInventoryBatch(updatedBatch)
                            remainingToDeduct -= deductFromThis
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
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
                
                recordDoubleEntryLedger(
                    itemId = item.id,
                    itemName = item.name,
                    batchNumber = item.batchNumber,
                    transactionType = "BRANCH_TRANSFER",
                    debitAccount = "BRANCH:$destinationBranch",
                    creditAccount = "BRANCH:$currentBranchName",
                    quantity = quantity,
                    unitPrice = item.price,
                    referenceId = "BULK_TX_${item.id}_${System.currentTimeMillis()}",
                    notes = "Bulk transfer to $destinationBranch. Reason: $reason"
                )

                logAuditTrail(
                    action = "BULK_TRANSFER",
                    details = "Bulk Transferred $quantity units of ${item.name} (${item.dosage}) to branch: '$destinationBranch'. Reason: $reason",
                    affectedId = item.id.toString()
                )

                // Automate double-ended Task insertion with structured transfer payload
                val payload = com.example.util.StockTransferPayload(
                    sourceGlobalId = item.globalId,
                    sourceItemId = item.id,
                    name = item.name,
                    dosage = item.dosage,
                    unitForm = item.unitForm,
                    brand = item.brand,
                    category = item.category,
                    batchNumber = item.batchNumber,
                    expiryDate = item.expiryDate,
                    price = item.price,
                    quantity = quantity,
                    fromBranch = currentBranchName,
                    destinationBranch = destinationBranch.trim(),
                    reason = reason
                )

                try {
                    val transferTaskId = (100000..999999).random()
                    val transferTaskMap = mapOf(
                        "id" to transferTaskId,
                        "title" to "INCOMING STOCK TRANSFER",
                        "description" to payload.encodeToTaskDescription(),
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
                    repository.upsertRemoteDocument("branch_operation_tasks", transferTaskId.toString(), transferTaskMap)

                    val taskOutbox = com.example.data.sync.SyncOutboxRecord(
                        branchId = destinationBranch.trim(),
                        entityType = "TASK",
                        entityId = transferTaskId.toString(),
                        operationType = "UPSERT",
                        payloadJson = org.json.JSONObject(transferTaskMap).toString(),
                        originatingUserUid = getCurrentUserUid()
                    )
                    repository.insertOperationTaskAndOutbox(
                        com.example.data.OperationTask(
                            id = transferTaskId,
                            title = "INCOMING STOCK TRANSFER",
                            description = payload.encodeToTaskDescription(),
                            urgency = "High",
                            category = "Stock Transfer",
                            isCompleted = false,
                            createdAt = System.currentTimeMillis(),
                            branchId = destinationBranch.trim(),
                            assignedToName = "Branch Manager"
                        ),
                        taskOutbox
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }

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
            saveAndSyncInventoryItemDirectly(updated)
            
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

            val currentBranchName = _currentPharmacistBranchName.value ?: "Careflux"
            recordDoubleEntryLedger(
                itemId = item.id,
                itemName = item.name,
                batchNumber = item.batchNumber,
                transactionType = "RETURN",
                debitAccount = "BRANCH:$currentBranchName",
                creditAccount = "CUSTOMER:${customerName.ifBlank { "Patient" }}",
                quantity = quantity,
                unitPrice = item.price,
                referenceId = "RETURN_${item.id}_${System.currentTimeMillis()}",
                notes = "Customer return: $reason"
            )
            
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
            saveAndSyncInventoryItemDirectly(updated)
            
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

            val currentBranchName = _currentPharmacistBranchName.value ?: "Careflux"
            recordDoubleEntryLedger(
                itemId = item.id,
                itemName = item.name,
                batchNumber = item.batchNumber,
                transactionType = "WRITE_OFF",
                debitAccount = "EXPENSE:ExpiryWriteOff",
                creditAccount = "BRANCH:$currentBranchName",
                quantity = quantity,
                unitPrice = item.price,
                referenceId = "WRITEOFF_${item.id}_${System.currentTimeMillis()}",
                notes = "Expiry/Damaged write-off: $reason"
            )
            
            logAuditTrail(
                action = "EXPIRY_WRITE_OFF",
                details = "Wrote-off $quantity units of ${item.name} (${item.dosage}) as expired/damaged. Reason: $reason.",
                affectedId = item.id.toString()
            )
            android.widget.Toast.makeText(getApplication(), "Expired inventory successfully written off & logged", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun claimExpiryAlert(item: com.example.data.InventoryItem, staffName: String) {
        viewModelScope.launch {
            val currentClaim = repository.allExpiryAlertClaims.first().find { it.inventoryItemId == item.id }
            val newClaim = com.example.data.ExpiryAlertClaim(
                inventoryItemId = item.id,
                medicationName = item.name,
                batchNumber = item.batchNumber,
                expiryDate = item.expiryDate,
                claimedByStaffName = staffName,
                claimTimestamp = System.currentTimeMillis(),
                status = "CLAIMED",
                actionTaken = currentClaim?.actionTaken ?: "",
                actionDetails = currentClaim?.actionDetails ?: "",
                actionTimestamp = currentClaim?.actionTimestamp ?: 0L
            )
            repository.insertExpiryAlertClaim(newClaim)
            logAuditTrail(
                action = "CLAIM_EXPIRY_ALERT",
                details = "Pharmacist $staffName claimed responsibility to act on expiring batch ${item.batchNumber.ifBlank { "N/A" }} of ${item.name}.",
                affectedId = item.id.toString()
            )
            android.widget.Toast.makeText(getApplication(), "Expiry alert claimed by $staffName", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun resolveExpiryAlert(item: com.example.data.InventoryItem, staffName: String, actionTaken: String, actionDetails: String) {
        viewModelScope.launch {
            if (actionTaken == "PRICE_DISCOUNT") {
                val newPrice = (item.price * 0.80).coerceAtLeast(0.0)
                val updatedItem = item.copy(
                    price = newPrice,
                    salesStrategy = "20% Near-Expiry Markdown Applied",
                    lastUpdated = System.currentTimeMillis()
                )
                saveAndSyncInventoryItemDirectly(updatedItem)
                val map = mapOf(
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
                syncEntityToFirestore("branch_inventory", updatedItem.id.toString(), map)
            } else if (actionTaken == "RESCUE_MARKETPLACE") {
                val discountedPrice = (item.price * 0.80).coerceAtLeast(0.0)
                createRescueListing(item, item.stockQuantity, discountedPrice, 10.0, 14)
            }

            val claim = com.example.data.ExpiryAlertClaim(
                inventoryItemId = item.id,
                medicationName = item.name,
                batchNumber = item.batchNumber,
                expiryDate = item.expiryDate,
                claimedByStaffName = staffName,
                claimTimestamp = System.currentTimeMillis(),
                status = "RESOLVED",
                actionTaken = actionTaken,
                actionDetails = actionDetails,
                actionTimestamp = System.currentTimeMillis()
            )
            repository.insertExpiryAlertClaim(claim)
            logAuditTrail(
                action = "RESOLVE_EXPIRY_ALERT",
                details = "Pharmacist $staffName resolved expiring batch ${item.batchNumber.ifBlank { "N/A" }} of ${item.name}. Action: $actionTaken ($actionDetails).",
                affectedId = item.id.toString()
            )
            android.widget.Toast.makeText(getApplication(), "Expiry alert marked as resolved by $staffName", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Role-Based Team Management - Allowed only for Branch Managers
    fun updateStaffRoleOrApproval(staffUid: String, newRole: String, isApproved: Boolean, staffEmail: String? = null) {
        viewModelScope.launch {
            try {
                if (_currentPharmacistRole.value != "Branch Manager" && !isCurrentUserAdmin()) {
                    android.widget.Toast.makeText(getApplication(), "Access Denied: Only Branch Managers can configure staff roles", android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }
                repository.updateStaffCredentials(staffUid, staffEmail, newRole, isApproved)

                // If currently logged-in user is the target staff member, update in-memory state immediately
                val currentAuthUser = authRepository.getCurrentUser()
                val targetEmail = staffEmail?.ifBlank { null } ?: if (staffUid.contains("@")) staffUid else null
                if (currentAuthUser != null &&
                    (currentAuthUser.uid == staffUid ||
                     (!targetEmail.isNullOrBlank() && currentAuthUser.email.equals(targetEmail, ignoreCase = true)))) {
                    _currentPharmacistRole.value = newRole
                    prefs.edit().putString("cached_role", newRole).apply()
                }

                logAuditTrail("MANAGE_STAFF", "Configured credentials of staff Member uid: $staffUid ($targetEmail). Role set to '$newRole', Active state: $isApproved")
                android.widget.Toast.makeText(getApplication(), "Staff credentials configured successfully", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- Ad-hoc Profile Branch Enrollment and Setup ---
    fun joinBranch(branchCode: String, onFinished: (Boolean, String) -> Unit) {
        val user = authRepository.getCurrentUser()
        if (user == null) {
            onFinished(false, "Authentication Error: No active user session.")
            return
        }
        val cleanCode = branchCode.trim().uppercase()
        if (cleanCode.isBlank()) {
            onFinished(false, "Please enter a non-empty Branch Code.")
            return
        }
        val deviceModelStr = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
        viewModelScope.launch {
            val result = repository.joinBranch(
                uid = user.uid,
                email = user.email.orEmpty(),
                displayName = user.displayName ?: user.email?.substringBefore("@") ?: "Staff Pharmacist",
                phoneNumber = _currentPharmacistPhone.value?.ifBlank { "+2348000000000" } ?: "+2348000000000",
                branchCode = cleanCode,
                deviceId = deviceId,
                deviceModel = deviceModelStr
            )
            result.fold(
                onSuccess = { (branchName, finalRole) ->
                    _currentPharmacistBranchId.value = cleanCode
                    _currentPharmacistBranchName.value = branchName
                    _currentPharmacistRole.value = finalRole
                    setupBranchRealtimeSync(cleanCode)
                    onFinished(true, "Successfully joined branch: $branchName ($cleanCode).")
                    logAuditTrail("JOIN_BRANCH", "User joined branch '$branchName' with code $cleanCode.")
                },
                onFailure = { e ->
                    onFinished(false, e.localizedMessage ?: "Failed to join branch.")
                }
            )
        }
    }

    fun registerBranch(name: String, lga: String, state: String, onFinished: (Boolean, String) -> Unit) {
        val user = authRepository.getCurrentUser()
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

        val deviceModelStr = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
        viewModelScope.launch {
            val result = repository.registerBranch(
                uid = user.uid,
                email = user.email.orEmpty(),
                displayName = user.displayName ?: user.email?.substringBefore("@") ?: "Staff Pharmacist",
                phoneNumber = _currentPharmacistPhone.value?.ifBlank { "+2348000000000" } ?: "+2348000000000",
                name = cleanName,
                lga = cleanLga,
                state = cleanState,
                deviceId = deviceId,
                deviceModel = deviceModelStr
            )
            result.fold(
                onSuccess = { randomCode ->
                    _currentPharmacistBranchId.value = randomCode
                    _currentPharmacistBranchName.value = cleanName
                    _currentPharmacistRole.value = "Branch Manager"
                    setupBranchRealtimeSync(randomCode)
                    onFinished(true, "Branch registered successfully! Store Code: $randomCode")
                    logAuditTrail("CREATE_BRANCH", "Created branch '$cleanName' and self-assigned as Branch Manager under $randomCode")
                },
                onFailure = { e ->
                    onFinished(false, e.localizedMessage ?: "Failed to register branch.")
                }
            )
        }
    }

    fun deleteBranch(branchId: String, onFinished: (Boolean, String) -> Unit) {
        val user = authRepository.getCurrentUser()
        if (user == null) {
            onFinished(false, "Authentication required.")
            return
        }
        viewModelScope.launch {
            val result = repository.deleteBranch(branchId)
            result.fold(
                onSuccess = {
                    logAuditTrail("DELETE_BRANCH", "Deleted branch $branchId from director")
                    if (_currentPharmacistBranchId.value == branchId) {
                        _currentPharmacistBranchId.value = ""
                        _currentPharmacistBranchName.value = "Not Configured"
                    }
                    onFinished(true, "Branch deleted successfully.")
                },
                onFailure = { e ->
                    onFinished(false, "Failed to delete branch: ${e.localizedMessage}")
                }
            )
        }
    }

    fun deleteDeviceConfig(nodeId: String, onFinished: (Boolean, String) -> Unit) {
        val user = authRepository.getCurrentUser()
        if (user == null) {
            onFinished(false, "Authentication required.")
            return
        }
        viewModelScope.launch {
            val result = repository.deleteDeviceNode(nodeId)
            result.fold(
                onSuccess = {
                    logAuditTrail("DELETE_NODE", "Deleted device node $nodeId from registry")
                    onFinished(true, "Device node deleted successfully.")
                },
                onFailure = { e ->
                    onFinished(false, "Failed to delete node: ${e.localizedMessage}")
                }
            )
        }
    }

    fun appointManager(branchId: String, branchName: String, pharmacistUid: String, pharmacistName: String, pharmacistEmail: String, onFinished: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = repository.appointManager(branchId, branchName, pharmacistUid, pharmacistName, pharmacistEmail)
            result.fold(
                onSuccess = {
                    logAuditTrail("APPOINT_MANAGER", "Appointed $pharmacistName as Manager of $branchName ($branchId)")
                    val currentUser = authRepository.getCurrentUser()
                    if (currentUser != null && currentUser.uid == pharmacistUid) {
                        _currentPharmacistBranchId.value = branchId
                        _currentPharmacistBranchName.value = branchName
                        _currentPharmacistRole.value = "Branch Manager"
                    }
                    onFinished(true, "Successfully appointed $pharmacistName as Manager of $branchName!")
                },
                onFailure = { e ->
                    onFinished(false, "Failed to appoint manager: ${e.localizedMessage}")
                }
            )
        }
    }

    fun switchActiveBranch(branchId: String, branchName: String, onFinished: ((Boolean, String) -> Unit)? = null) {
        if (branchId.isBlank()) {
            onFinished?.invoke(false, "Invalid branch code")
            return
        }

        val user = authRepository.getCurrentUser()
        
        _currentPharmacistBranchId.value = branchId
        _currentPharmacistBranchName.value = branchName
        
        prefs.edit()
            .putString("cached_branch_id", branchId)
            .putString("cached_branch_name", branchName)
            .apply()
            
        viewModelScope.launch {
            val gen = branchGenerationToken.incrementAndGet()
            try {
                // Cancel active listeners deterministically before switching branch sync scope
                val jobsToCancel = activeSyncJobs.toList()
                activeSyncJobs.clear()
                jobsToCancel.forEach { job ->
                    try {
                        job.cancelAndJoin()
                    } catch (e: Exception) {
                        // ignore cancellation exceptions
                    }
                }
                // Do NOT wipe local Room data — offline mutations across branches are preserved safely in Room
            } catch (e: Exception) {
                android.util.Log.w("PharmacyViewModel", "Data wipe on branch switch failed: ${e.localizedMessage}")
            }

            setupBranchRealtimeSync(branchId)
            triggerImmediateSync()

            if (user != null) {
                val result = repository.switchActiveBranch(user.uid, branchId, branchName)
                result.fold(
                    onSuccess = {
                        logAuditTrail("SWITCH_BRANCH", "User switched active branch context to '$branchName' ($branchId)")
                        try {
                            deviceRepository.updateBranchAssociation(branchId)
                        } catch (e: Exception) {
                            android.util.Log.w("PharmacyViewModel", "Device branch association sync failed: ${e.localizedMessage}")
                        }
                        onFinished?.invoke(true, "Switched active branch to $branchName")
                    },
                    onFailure = { e ->
                        logAuditTrail("SWITCH_BRANCH_LOCAL", "Switched active branch locally to '$branchName' (remote sync pending: ${e.localizedMessage})")
                        onFinished?.invoke(true, "Switched active branch locally to $branchName")
                    }
                )
            } else {
                logAuditTrail("SWITCH_BRANCH_LOCAL", "Switched active branch context to '$branchName' ($branchId)")
                onFinished?.invoke(true, "Switched active branch to $branchName")
            }
        }
    }

    fun updateBranchDetails(branchId: String, newName: String, newLga: String, newState: String, onFinished: (Boolean, String) -> Unit) {
        val user = authRepository.getCurrentUser()
        if (user == null) {
            onFinished(false, "Authentication required.")
            return
        }
        viewModelScope.launch {
            val result = repository.updateBranchDetails(branchId, newName, newLga, newState)
            result.fold(
                onSuccess = {
                    logAuditTrail("UPDATE_BRANCH", "Updated details of branch $branchId to Name: $newName, LGA: $newLga, State: $newState")
                    onFinished(true, "Branch details updated successfully.")
                },
                onFailure = { e ->
                    onFinished(false, "Failed to update branch details: ${e.localizedMessage}")
                }
            )
        }
    }

    fun updateBranchFeatures(branchId: String, features: Map<String, Boolean>, onFinished: (Boolean, String) -> Unit) {
        val user = authRepository.getCurrentUser()
        if (user == null) {
            onFinished(false, "Authentication required.")
            return
        }
        if (branchId.isBlank()) {
            onFinished(false, "Branch ID cannot be empty.")
            return
        }
        viewModelScope.launch {
            val result = repository.updateBranchFeatures(branchId, features)
            result.fold(
                onSuccess = {
                    logAuditTrail("UPDATE_BRANCH_FEATURES", "Updated feature toggles of branch $branchId: $features")
                    
                    val currentList = _allBranches.value.toMutableList()
                    val idx = currentList.indexOfFirst { (it["id"] as? String) == branchId || (it["code"] as? String) == branchId }
                    if (idx >= 0) {
                        val updatedBranch = currentList[idx].toMutableMap()
                        features.forEach { (k, v) -> updatedBranch[k] = v }
                        currentList[idx] = updatedBranch
                        _allBranches.value = currentList
                    }
                    
                    onFinished(true, "Branch features updated successfully.")
                },
                onFailure = { e ->
                    onFinished(false, "Failed to update branch features: ${e.localizedMessage}")
                }
            )
        }
    }

    fun deletePharmacist(pharmacistUid: String, pharmacistName: String, branchId: String?, role: String?, onFinished: (Boolean, String) -> Unit) {
        val user = authRepository.getCurrentUser()
        if (user == null) {
            onFinished(false, "Authentication required.")
            return
        }
        viewModelScope.launch {
            val result = repository.deletePharmacist(pharmacistUid, branchId, role)
            result.fold(
                onSuccess = {
                    logAuditTrail("DELETE_PHARMACIST", "Deleted pharmacist account: $pharmacistName ($pharmacistUid)")
                    onFinished(true, "Pharmacist account deleted successfully.")
                },
                onFailure = { e ->
                    onFinished(false, "Failed to delete pharmacist account: ${e.localizedMessage}")
                }
            )
        }
    }

    fun updatePharmacistProfile(newName: String, newPhone: String, onFinished: (Boolean, String) -> Unit) {
        val user = authRepository.getCurrentUser()
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

        _currentPharmacistName.value = cleanName
        _currentPharmacistPhone.value = cleanPhone

        viewModelScope.launch {
            try {
                authRepository.updateDisplayName(cleanName)
            } catch (e: Exception) {
                android.util.Log.e("PharmacyViewModel", "Auth profile display name update failed", e)
            }

            val updateData = mapOf(
                "displayName" to cleanName,
                "phoneNumber" to cleanPhone
            )
            val result = repository.upsertRemoteDocument("registered_pharmacists", user.uid, updateData)
            result.fold(
                onSuccess = {
                    onFinished(true, "Profile updated successfully.")
                    logAuditTrail("UPDATE_PROFILE", "User updated profile name to '$cleanName' and phone to '$cleanPhone'.")
                },
                onFailure = { e ->
                    onFinished(false, "Failed to update profile: ${e.localizedMessage}")
                }
            )
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
        val user = authRepository.getCurrentUser()
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
                        nextRefillDate = now + (10L * 24 * 60 * 60 * 1000),
                        dateAdded = now - (20L * 24 * 60 * 60 * 1000)
                    ),
                    com.example.data.CustomerMedication(
                        id = generateUniqueId(),
                        customerId = olumide.id,
                        inventoryItemId = medGlucophage.id,
                        medicationName = "${medGlucophage.name} ${medGlucophage.dosage}",
                        customDosage = "Take 1 tablet with meals twice daily",
                        cost = 1200.0,
                        cycleDays = 30,
                        nextRefillDate = now + (5L * 24 * 60 * 60 * 1000),
                        dateAdded = now - (25L * 24 * 60 * 60 * 1000)
                    ),
                    com.example.data.CustomerMedication(
                        id = generateUniqueId(),
                        customerId = emeka.id,
                        inventoryItemId = medCoDiovan.id,
                        medicationName = "${medCoDiovan.name} ${medCoDiovan.dosage}",
                        customDosage = "Take 1 tablet daily in the morning",
                        cost = 6500.0,
                        cycleDays = 30,
                        nextRefillDate = now + (2L * 24 * 60 * 60 * 1000),
                        dateAdded = now - (28L * 24 * 60 * 60 * 1000)
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
