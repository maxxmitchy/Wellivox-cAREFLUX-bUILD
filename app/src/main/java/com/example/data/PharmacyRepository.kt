package com.example.data

import com.example.data.remote.FirestoreRemoteDataSourceImpl
import com.example.data.remote.RemoteDataSource
import com.example.data.sync.SaleSyncRequest
import com.example.data.sync.SyncResult
import kotlinx.coroutines.flow.Flow

class PharmacyRepository(
    private val pharmacyDao: PharmacyDao,
    val remoteDataSource: RemoteDataSource = FirestoreRemoteDataSourceImpl()
) {

    // --- Remote Observations & Sync Operations ---
    fun getCurrentUserUid(): String? = remoteDataSource.getCurrentUserUid()
    fun getCurrentUserEmail(): String? = remoteDataSource.getCurrentUserEmail()

    suspend fun getPharmacistBranchId(uid: String): String? = remoteDataSource.getPharmacistBranchId(uid)

    fun observePharmacist(uid: String): Flow<Map<String, Any>?> = remoteDataSource.observePharmacist(uid)
    fun observeBranchSettings(branchId: String): Flow<Map<String, Any>?> = remoteDataSource.observeBranchSettings(branchId)
    fun observeStaffMembers(branchId: String = ""): Flow<List<Map<String, Any>>> = remoteDataSource.observeStaffMembers(branchId)
    fun observeBranchInventory(branchId: String = ""): Flow<List<Map<String, Any>>> = remoteDataSource.observeBranchInventory(branchId)
    fun observeBranchCustomers(branchId: String = ""): Flow<List<Map<String, Any>>> = remoteDataSource.observeBranchCustomers(branchId)
    fun observeBranchCustomerMedications(branchId: String = ""): Flow<List<Map<String, Any>>> = remoteDataSource.observeBranchCustomerMedications(branchId)
    fun observeBranchInterventions(branchId: String = ""): Flow<List<Map<String, Any>>> = remoteDataSource.observeBranchInterventions(branchId)
    fun observeBranchOperationTasks(branchId: String = ""): Flow<List<Map<String, Any>>> = remoteDataSource.observeBranchOperationTasks(branchId)
    fun observeBranchReceipts(branchId: String = ""): Flow<List<Map<String, Any>>> = remoteDataSource.observeBranchReceipts(branchId)
    fun observeBranchAuditLogs(branchId: String = ""): Flow<List<Map<String, Any>>> = remoteDataSource.observeBranchAuditLogs(branchId)
    fun observeAllPharmacists(): Flow<List<Map<String, Any>>> = remoteDataSource.observeAllPharmacists()
    fun observeAllBranches(): Flow<List<Map<String, Any>>> = remoteDataSource.observeAllBranches()
    fun observeDeviceConfigs(): Flow<List<Map<String, Any>>> = remoteDataSource.observeDeviceConfigs()
    fun observeDeviceConfig(deviceId: String): Flow<Map<String, Any>?> = remoteDataSource.observeDeviceConfig(deviceId)
    fun observeExpiryRescueListings(): Flow<List<Map<String, Any>>> = remoteDataSource.observeExpiryRescueListings()
    fun observeKeyCreationRequests(): Flow<List<Map<String, Any>>> = remoteDataSource.observeKeyCreationRequests()
    fun observeCanonicalProducts(): Flow<List<Map<String, Any>>> = remoteDataSource.observeCanonicalProducts()
    fun observeAdminAuditLogs(): Flow<List<Map<String, Any>>> = remoteDataSource.observeAdminAuditLogs()
    fun observeMedicationSales(branchId: String = ""): Flow<List<Map<String, Any>>> = remoteDataSource.observeMedicationSales(branchId)

    suspend fun getRemoteDocument(collection: String, documentId: String): Result<Map<String, Any>?> =
        remoteDataSource.getDocument(collection, documentId)

    suspend fun getRemoteDocumentsWhereEquals(collection: String, field: String, value: Any): Result<List<Map<String, Any>>> =
        remoteDataSource.getDocumentsWhereEquals(collection, field, value)

    suspend fun getAllRemoteDocuments(collection: String): Result<List<Map<String, Any>>> =
        remoteDataSource.getAllDocuments(collection)

    suspend fun upsertRemoteDocument(collection: String, documentId: String, data: Map<String, Any?>): Result<Unit> =
        remoteDataSource.upsertDocument(collection, documentId, data)

    suspend fun deductInventoryStockOnlineTransaction(branchId: String, itemId: Int, quantity: Int): Result<Unit> =
        remoteDataSource.deductInventoryStockOnlineTransaction(branchId, itemId, quantity)

    suspend fun executeCheckoutTransaction(
        updatedItem: InventoryItem,
        updatedBatches: List<InventoryBatch>,
        sale: MedicationSale,
        ledgerEntry: InventoryLedgerEntry,
        outboxRecord: com.example.data.sync.SyncOutboxRecord? = null
    ) {
        pharmacyDao.executeCheckoutTransaction(updatedItem, updatedBatches, sale, ledgerEntry, outboxRecord)
    }

    suspend fun insertCustomerAndOutbox(customer: Customer, outbox: com.example.data.sync.SyncOutboxRecord): Long =
        pharmacyDao.insertCustomerAndOutbox(customer, outbox)

    suspend fun updateCustomerAndOutbox(customer: Customer, outbox: com.example.data.sync.SyncOutboxRecord) =
        pharmacyDao.updateCustomerAndOutbox(customer, outbox)

    suspend fun insertCustomerMedicationAndOutbox(medication: CustomerMedication, outbox: com.example.data.sync.SyncOutboxRecord): Long =
        pharmacyDao.insertCustomerMedicationAndOutbox(medication, outbox)

    suspend fun updateCustomerMedicationAndOutbox(medication: CustomerMedication, outbox: com.example.data.sync.SyncOutboxRecord) =
        pharmacyDao.updateCustomerMedicationAndOutbox(medication, outbox)

    suspend fun insertClinicalInterventionAndOutbox(intervention: ClinicalIntervention, outbox: com.example.data.sync.SyncOutboxRecord): Long =
        pharmacyDao.insertClinicalInterventionAndOutbox(intervention, outbox)

    suspend fun updateClinicalInterventionAndOutbox(intervention: ClinicalIntervention, outbox: com.example.data.sync.SyncOutboxRecord) =
        pharmacyDao.updateClinicalInterventionAndOutbox(intervention, outbox)

    suspend fun insertInventoryItemAndOutbox(item: InventoryItem, outbox: com.example.data.sync.SyncOutboxRecord): Long =
        pharmacyDao.insertInventoryItemAndOutbox(item, outbox)

    suspend fun updateInventoryItemAndOutbox(item: InventoryItem, outbox: com.example.data.sync.SyncOutboxRecord) =
        pharmacyDao.updateInventoryItemAndOutbox(item, outbox)

    suspend fun insertOperationTaskAndOutbox(task: OperationTask, outbox: com.example.data.sync.SyncOutboxRecord) =
        pharmacyDao.insertOperationTaskAndOutbox(task, outbox)

    suspend fun updateOperationTaskAndOutbox(task: OperationTask, outbox: com.example.data.sync.SyncOutboxRecord) =
        pharmacyDao.updateOperationTaskAndOutbox(task, outbox)

    suspend fun insertReceiptAndOutbox(receipt: Receipt, outbox: com.example.data.sync.SyncOutboxRecord) =
        pharmacyDao.insertReceiptAndOutbox(receipt, outbox)

    suspend fun updateReceiptAndOutbox(receipt: Receipt, outbox: com.example.data.sync.SyncOutboxRecord) =
        pharmacyDao.updateReceiptAndOutbox(receipt, outbox)

    suspend fun addRemoteDocument(collection: String, data: Map<String, Any?>): Result<String> =
        remoteDataSource.addDocument(collection, data)

    suspend fun deleteRemoteDocument(collection: String, documentId: String): Result<Unit> =
        remoteDataSource.deleteDocument(collection, documentId)

    suspend fun claimRescueListing(listingId: String, deviceId: String, deviceModel: String): Result<Boolean> =
        remoteDataSource.claimRescueListing(listingId, deviceId, deviceModel)

    suspend fun updateStaffCredentials(staffUid: String, staffEmail: String?, newRole: String, isApproved: Boolean): Result<Unit> =
        remoteDataSource.updateStaffCredentials(staffUid, staffEmail, newRole, isApproved)

    suspend fun joinBranch(uid: String, email: String, displayName: String, phoneNumber: String, branchCode: String, deviceId: String, deviceModel: String): Result<Pair<String, String>> =
        remoteDataSource.joinBranch(uid, email, displayName, phoneNumber, branchCode, deviceId, deviceModel)

    suspend fun registerBranch(uid: String, email: String, displayName: String, phoneNumber: String, name: String, lga: String, state: String, deviceId: String, deviceModel: String): Result<String> =
        remoteDataSource.registerBranch(uid, email, displayName, phoneNumber, name, lga, state, deviceId, deviceModel)

    suspend fun deleteBranch(branchId: String): Result<Unit> =
        remoteDataSource.deleteBranch(branchId)

    suspend fun deleteDeviceNode(nodeId: String): Result<Unit> =
        remoteDataSource.deleteDeviceNode(nodeId)

    suspend fun appointManager(branchId: String, branchName: String, pharmacistUid: String, pharmacistName: String, pharmacistEmail: String): Result<Unit> =
        remoteDataSource.appointManager(branchId, branchName, pharmacistUid, pharmacistName, pharmacistEmail)

    suspend fun switchActiveBranch(uid: String, branchId: String, branchName: String): Result<Unit> =
        remoteDataSource.switchActiveBranch(uid, branchId, branchName)

    suspend fun updateBranchDetails(branchId: String, newName: String, newLga: String, newState: String): Result<Unit> =
        remoteDataSource.updateBranchDetails(branchId, newName, newLga, newState)

    suspend fun updateBranchFeatures(branchId: String, features: Map<String, Boolean>): Result<Unit> =
        remoteDataSource.updateBranchFeatures(branchId, features)

    suspend fun deletePharmacist(pharmacistUid: String, branchId: String?, role: String?): Result<Unit> =
        remoteDataSource.deletePharmacist(pharmacistUid, branchId, role)

    suspend fun syncSaleTransaction(request: SaleSyncRequest): SyncResult =
        remoteDataSource.syncSaleTransaction(request)

    // --- Outbox Operations ---
    suspend fun insertOutboxRecord(record: com.example.data.sync.SyncOutboxRecord): Long =
        pharmacyDao.insertOutboxRecord(record)

    suspend fun updateOutboxRecord(record: com.example.data.sync.SyncOutboxRecord) =
        pharmacyDao.updateOutboxRecord(record)

    suspend fun deleteOutboxRecordById(id: Int) =
        pharmacyDao.deleteOutboxRecordById(id)

    suspend fun getPendingOutboxRecords(): List<com.example.data.sync.SyncOutboxRecord> =
        pharmacyDao.getPendingOutboxRecords()

    fun getOutboxRecordsForBranch(branchId: String): Flow<List<com.example.data.sync.SyncOutboxRecord>> =
        pharmacyDao.getOutboxRecordsForBranch(branchId)

    suspend fun getOutboxRecordByClientTxId(clientTxId: String): com.example.data.sync.SyncOutboxRecord? =
        pharmacyDao.getOutboxRecordByClientTxId(clientTxId)

    // --- Branch-Scoped Data Operations ---
    fun getCustomersForBranch(branchId: String): Flow<List<Customer>> =
        pharmacyDao.getCustomersForBranch(branchId)

    fun getInventoryForBranch(branchId: String): Flow<List<InventoryItem>> =
        pharmacyDao.getInventoryForBranch(branchId)

    fun getLowStockItemsForBranch(branchId: String): Flow<List<InventoryItem>> =
        pharmacyDao.getLowStockItemsForBranch(branchId)

    fun getOperationTasksForBranch(branchId: String): Flow<List<OperationTask>> =
        pharmacyDao.getOperationTasksForBranch(branchId)

    fun getReceiptsForBranch(branchId: String): Flow<List<Receipt>> =
        pharmacyDao.getReceiptsForBranch(branchId)

    fun getMedicationSalesForBranch(branchId: String): Flow<List<MedicationSale>> =
        pharmacyDao.getMedicationSalesForBranch(branchId)

    suspend fun getMedicationSaleByClientTransactionId(clientTxId: String): MedicationSale? =
        pharmacyDao.getMedicationSaleByClientTransactionId(clientTxId)

    fun getClinicalInterventionsForBranch(branchId: String): Flow<List<ClinicalIntervention>> =
        pharmacyDao.getClinicalInterventionsForBranch(branchId)

    fun getCustomerMedicationsForBranch(branchId: String): Flow<List<CustomerMedication>> =
        pharmacyDao.getCustomerMedicationsForBranch(branchId)

    fun getInventoryLedgerEntriesForBranch(branchId: String): Flow<List<InventoryLedgerEntry>> =
        pharmacyDao.getInventoryLedgerEntriesForBranch(branchId)

    suspend fun logOutboundSms(logData: Map<String, Any?>): Result<Unit> =
        remoteDataSource.logOutboundSms(logData)

    // --- Operation Tasks ---
    val allOperationTasks: Flow<List<OperationTask>> = pharmacyDao.getAllOperationTasks()

    suspend fun insertOperationTask(task: OperationTask) {
        pharmacyDao.insertOperationTask(task)
    }

    suspend fun getOperationTaskById(id: Int): OperationTask? {
        return pharmacyDao.getOperationTaskById(id)
    }

    suspend fun updateOperationTask(task: OperationTask) {
        pharmacyDao.updateOperationTask(task)
    }

    suspend fun deleteOperationTask(task: OperationTask) {
        pharmacyDao.deleteOperationTask(task)
    }

    // --- Receipts ---
    val allReceipts: Flow<List<Receipt>> = pharmacyDao.getAllReceipts()

    suspend fun insertReceipt(receipt: Receipt) {
        pharmacyDao.insertReceipt(receipt)
    }

    suspend fun updateReceipt(receipt: Receipt) {
        pharmacyDao.updateReceipt(receipt)
    }

    suspend fun deleteReceipt(receipt: Receipt) {
        pharmacyDao.deleteReceipt(receipt)
    }

    // --- Inventory Operations ---
    val allInventoryItems: Flow<List<InventoryItem>> = pharmacyDao.getAllInventoryItems()
    val lowStockItems: Flow<List<InventoryItem>> = pharmacyDao.getLowStockItems()

    suspend fun getInventoryItemByName(name: String): InventoryItem? {
        return pharmacyDao.getInventoryItemByName(name)
    }

    suspend fun getInventoryItemsByName(name: String): List<InventoryItem> {
        return pharmacyDao.getInventoryItemsByName(name)
    }

    suspend fun getInventoryItemById(id: Int): InventoryItem? {
        return pharmacyDao.getInventoryItemById(id)
    }

    suspend fun insertInventoryItem(item: InventoryItem): Long {
        return pharmacyDao.insertInventoryItem(item)
    }

    suspend fun updateInventoryItem(item: InventoryItem) {
        pharmacyDao.updateInventoryItem(item)
    }

    suspend fun deleteInventoryItem(item: InventoryItem) {
        pharmacyDao.deleteInventoryItem(item)
    }

    suspend fun deleteInventoryItemById(id: Int) {
        pharmacyDao.deleteInventoryItemById(id)
    }


    // --- Daily Prescription Vol Operations ---
    val allPrescriptionVolumes: Flow<List<DailyPrescriptionVolume>> = pharmacyDao.getAllPrescriptionVolumes()

    suspend fun insertPrescriptionVolume(volume: DailyPrescriptionVolume) {
        pharmacyDao.insertPrescriptionVolume(volume)
    }

    suspend fun deletePrescriptionVolume(volume: DailyPrescriptionVolume) {
        pharmacyDao.deletePrescriptionVolume(volume)
    }


    // --- Customer Alerts Operations ---
    val allCustomerAlerts: Flow<List<CustomerAlert>> = pharmacyDao.getAllCustomerAlerts()

    suspend fun insertCustomerAlert(alert: CustomerAlert) {
        pharmacyDao.insertCustomerAlert(alert)
    }

    suspend fun updateCustomerAlert(alert: CustomerAlert) {
        pharmacyDao.updateCustomerAlert(alert)
    }

    suspend fun deleteCustomerAlert(alert: CustomerAlert) {
        pharmacyDao.deleteCustomerAlert(alert)
    }

    suspend fun deleteCustomerAlertById(id: Int) {
        pharmacyDao.deleteCustomerAlertById(id)
    }

    // --- Customers ---
    val allCustomers: Flow<List<Customer>> = pharmacyDao.getAllCustomers()

    suspend fun getCustomerById(id: Int): Customer? {
        return pharmacyDao.getCustomerById(id)
    }

    suspend fun insertCustomer(customer: Customer): Int {
        return pharmacyDao.insertCustomer(customer).toInt()
    }

    suspend fun updateCustomer(customer: Customer) {
        pharmacyDao.updateCustomer(customer)
    }

    suspend fun deleteCustomer(customer: Customer) {
        pharmacyDao.deleteCustomer(customer)
    }

    // --- Customer Meds ---
    val allCustomerMedications: Flow<List<CustomerMedication>> = pharmacyDao.getAllCustomerMedications()

    fun getMedicationsForCustomer(customerId: Int): Flow<List<CustomerMedication>> {
        return pharmacyDao.getMedicationsForCustomer(customerId)
    }

    suspend fun insertCustomerMedication(medication: CustomerMedication): Int {
        return pharmacyDao.insertCustomerMedication(medication).toInt()
    }

    suspend fun updateCustomerMedication(medication: CustomerMedication) {
        pharmacyDao.updateCustomerMedication(medication)
    }

    suspend fun deleteCustomerMedication(medication: CustomerMedication) {
        pharmacyDao.deleteCustomerMedication(medication)
    }

    // --- Clinical Interventions ---
    val allClinicalInterventions: Flow<List<ClinicalIntervention>> = pharmacyDao.getAllClinicalInterventions()

    fun getClinicalInterventionsForCustomer(customerId: Int): Flow<List<ClinicalIntervention>> {
        return pharmacyDao.getClinicalInterventionsForCustomer(customerId)
    }

    suspend fun getClinicalInterventionById(id: Int): ClinicalIntervention? {
        return pharmacyDao.getClinicalInterventionById(id)
    }

    suspend fun insertClinicalIntervention(intervention: ClinicalIntervention): Int {
        return pharmacyDao.insertClinicalIntervention(intervention).toInt()
    }

    suspend fun updateClinicalIntervention(intervention: ClinicalIntervention) {
        pharmacyDao.updateClinicalIntervention(intervention)
    }

    suspend fun deleteClinicalIntervention(intervention: ClinicalIntervention) {
        pharmacyDao.deleteClinicalIntervention(intervention)
    }
    
    // --- AI Carousels ---
    val allAICarousels: Flow<List<AICarousel>> = pharmacyDao.getAllAICarousels()

    suspend fun insertAICarousel(carousel: AICarousel): Long {
        return pharmacyDao.insertAICarousel(carousel)
    }

    suspend fun deleteAICarousel(carousel: AICarousel) {
        pharmacyDao.deleteAICarousel(carousel)
    }

    // --- Pharmacy Triage Knowledge Base ---
    val allTriageConditions: Flow<List<TriageCondition>> = pharmacyDao.getAllTriageConditions()

    suspend fun getTriageConditionById(id: Int): TriageCondition? {
        return pharmacyDao.getTriageConditionById(id)
    }

    suspend fun insertTriageCondition(condition: TriageCondition): Long {
        return pharmacyDao.insertTriageCondition(condition)
    }

    suspend fun updateTriageCondition(condition: TriageCondition) {
        pharmacyDao.updateTriageCondition(condition)
    }

    suspend fun deleteTriageCondition(condition: TriageCondition) {
        pharmacyDao.deleteTriageCondition(condition)
    }

    // --- Medication Sales ---
    val allMedicationSales: Flow<List<MedicationSale>> = pharmacyDao.getAllMedicationSales()

    suspend fun insertMedicationSale(sale: MedicationSale) {
        pharmacyDao.insertMedicationSale(sale)
    }

    // --- Rescue Listings ---
    val allRescueListings: Flow<List<RescueListing>> = pharmacyDao.getAllRescueListings()

    suspend fun getRescueListingByFirestoreId(firestoreId: String): RescueListing? {
        return pharmacyDao.getRescueListingByFirestoreId(firestoreId)
    }

    suspend fun deleteRescueListingByFirestoreId(firestoreId: String) {
        pharmacyDao.deleteRescueListingByFirestoreId(firestoreId)
    }

    suspend fun insertRescueListing(listing: RescueListing) {
        pharmacyDao.insertRescueListing(listing)
    }

    suspend fun updateRescueListing(listing: RescueListing) {
        pharmacyDao.updateRescueListing(listing)
    }

    // --- Admin Audit Logs ---
    val allAdminAuditLogs: Flow<List<AdminAuditLog>> = pharmacyDao.getAllAdminAuditLogs()

    suspend fun insertAdminAuditLog(log: AdminAuditLog) {
        pharmacyDao.insertAdminAuditLog(log)
    }

    // --- Inventory Batches ---
    val allInventoryBatches: Flow<List<InventoryBatch>> = pharmacyDao.getAllInventoryBatches()

    fun getBatchesForItem(itemId: Int): Flow<List<InventoryBatch>> {
        return pharmacyDao.getBatchesForItem(itemId)
    }

    suspend fun insertInventoryBatch(batch: InventoryBatch): Long {
        return pharmacyDao.insertInventoryBatch(batch)
    }

    suspend fun updateInventoryBatch(batch: InventoryBatch) {
        pharmacyDao.updateInventoryBatch(batch)
    }

    suspend fun deleteInventoryBatch(batch: InventoryBatch) {
        pharmacyDao.deleteInventoryBatch(batch)
    }

    suspend fun deleteInventoryBatchById(id: Int) {
        pharmacyDao.deleteInventoryBatchById(id)
    }

    // --- Outbound SMS Logs ---
    val allSmsLogs: Flow<List<OutboundSmsLog>> = pharmacyDao.getAllSmsLogs()

    suspend fun insertSmsLog(log: OutboundSmsLog): Long {
        return pharmacyDao.insertSmsLog(log)
    }

    suspend fun updateSmsLog(log: OutboundSmsLog) {
        pharmacyDao.updateSmsLog(log)
    }

    suspend fun clearSmsLogs() {
        pharmacyDao.clearSmsLogs()
    }

    // --- Expiry Alert Claims ---
    val allExpiryAlertClaims: Flow<List<ExpiryAlertClaim>> = pharmacyDao.getAllExpiryAlertClaims()

    suspend fun insertExpiryAlertClaim(claim: ExpiryAlertClaim) {
        pharmacyDao.insertExpiryAlertClaim(claim)
    }

    // --- Organizations ---
    val organization: Flow<Organization?> = pharmacyDao.getOrganization()

    suspend fun insertOrganization(org: Organization): Long {
        return pharmacyDao.insertOrganization(org)
    }

    // --- Users & Access ---
    val allUsers: Flow<List<User>> = pharmacyDao.getAllUsers()

    suspend fun getUserById(id: Int): User? = pharmacyDao.getUserById(id)

    suspend fun getUserByPhone(phone: String): User? = pharmacyDao.getUserByPhone(phone)

    suspend fun insertUser(user: User): Long = pharmacyDao.insertUser(user)

    suspend fun updateUser(user: User) = pharmacyDao.updateUser(user)

    fun getUserBranchAccess(userId: Int): Flow<List<UserBranchAccess>> = pharmacyDao.getUserBranchAccess(userId)

    suspend fun insertUserBranchAccess(access: UserBranchAccess): Long = pharmacyDao.insertUserBranchAccess(access)

    // --- Customer Branch ---
    fun getCustomerBranches(customerId: Int): Flow<List<CustomerBranch>> = pharmacyDao.getCustomerBranches(customerId)

    suspend fun insertCustomerBranch(cb: CustomerBranch): Long = pharmacyDao.insertCustomerBranch(cb)

    // --- Double-Entry Inventory Ledger ---
    val allInventoryLedgerEntries: Flow<List<InventoryLedgerEntry>> = pharmacyDao.getAllInventoryLedgerEntries()

    fun getLedgerEntriesByItem(itemId: Int): Flow<List<InventoryLedgerEntry>> = pharmacyDao.getLedgerEntriesByItem(itemId)

    suspend fun insertInventoryLedgerEntry(entry: InventoryLedgerEntry): Long = pharmacyDao.insertInventoryLedgerEntry(entry)

    suspend fun clearAllData() {
        pharmacyDao.clearAllData()
    }
}
