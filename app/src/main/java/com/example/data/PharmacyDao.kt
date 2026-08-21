package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PharmacyDao {
    // --- Manual/Operations Tasks ---
    @Query("SELECT * FROM operations_tasks ORDER BY isCompleted ASC, createdAt DESC")
    fun getAllOperationTasks(): Flow<List<OperationTask>>

    @Query("SELECT * FROM operations_tasks WHERE id = :id LIMIT 1")
    suspend fun getOperationTaskById(id: Int): OperationTask?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOperationTask(task: OperationTask)

    @Delete
    suspend fun deleteOperationTask(task: OperationTask)

    @Update
    suspend fun updateOperationTask(task: OperationTask)

    // --- Receipts ---
    @Query("SELECT * FROM receipts ORDER BY timestamp DESC")
    fun getAllReceipts(): Flow<List<Receipt>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceipt(receipt: Receipt)

    @Update
    suspend fun updateReceipt(receipt: Receipt)

    @Delete
    suspend fun deleteReceipt(receipt: Receipt)

    // --- Inventory Opers ---
    @Query("SELECT * FROM inventory_items ORDER BY name ASC")
    fun getAllInventoryItems(): Flow<List<InventoryItem>>

    @Query("SELECT * FROM inventory_items WHERE stockQuantity <= minRequiredStock")
    fun getLowStockItems(): Flow<List<InventoryItem>>

    @Query("SELECT * FROM inventory_items WHERE name = :name LIMIT 1")
    suspend fun getInventoryItemByName(name: String): InventoryItem?

    @Query("SELECT * FROM inventory_items WHERE name = :name COLLATE NOCASE")
    suspend fun getInventoryItemsByName(name: String): List<InventoryItem>

    @Query("SELECT * FROM inventory_items WHERE id = :id LIMIT 1")
    suspend fun getInventoryItemById(id: Int): InventoryItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInventoryItem(item: InventoryItem): Long

    @Update
    suspend fun updateInventoryItem(item: InventoryItem)

    @Delete
    suspend fun deleteInventoryItem(item: InventoryItem)

    @Query("DELETE FROM inventory_items WHERE id = :id")
    suspend fun deleteInventoryItemById(id: Int)


    // --- Daily Prescription Vol Opers ---
    @Query("SELECT * FROM prescription_volumes ORDER BY dateString DESC")
    fun getAllPrescriptionVolumes(): Flow<List<DailyPrescriptionVolume>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrescriptionVolume(volume: DailyPrescriptionVolume)

    @Delete
    suspend fun deletePrescriptionVolume(volume: DailyPrescriptionVolume)


    // --- Customer Alerts Opers (Legacy, can keep or remove, let's keep for simple alerts if needed) ---
    @Query("SELECT * FROM customer_alerts ORDER BY status DESC, timestamp DESC")
    fun getAllCustomerAlerts(): Flow<List<CustomerAlert>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomerAlert(alert: CustomerAlert)

    @Update
    suspend fun updateCustomerAlert(alert: CustomerAlert)

    @Delete
    suspend fun deleteCustomerAlert(alert: CustomerAlert)

    @Query("DELETE FROM customer_alerts WHERE id = :id")
    suspend fun deleteCustomerAlertById(id: Int)
    
    // --- Customers Opers ---
    @Query("SELECT * FROM customers ORDER BY name ASC")
    fun getAllCustomers(): Flow<List<Customer>>

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun getCustomerById(id: Int): Customer?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: Customer): Long

    @Update
    suspend fun updateCustomer(customer: Customer)

    @Delete
    suspend fun deleteCustomerInternal(customer: Customer)

    @Query("DELETE FROM customer_medications WHERE customerId = :customerId")
    suspend fun deleteMedicationsForCustomer(customerId: Int)

    @Query("DELETE FROM clinical_interventions WHERE customerId = :customerId")
    suspend fun deleteInterventionsForCustomer(customerId: Int)

    @Transaction
    suspend fun deleteCustomer(customer: Customer) {
        deleteMedicationsForCustomer(customer.id)
        deleteInterventionsForCustomer(customer.id)
        deleteCustomerInternal(customer)
    }

    // --- Customer Meds Opers ---
    @Query("SELECT * FROM customer_medications")
    fun getAllCustomerMedications(): Flow<List<CustomerMedication>>

    @Query("SELECT * FROM customer_medications WHERE customerId = :customerId")
    fun getMedicationsForCustomer(customerId: Int): Flow<List<CustomerMedication>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomerMedication(medication: CustomerMedication): Long

    @Update
    suspend fun updateCustomerMedication(medication: CustomerMedication)

    @Delete
    suspend fun deleteCustomerMedication(medication: CustomerMedication)

    // --- Clinical Interventions Opers ---
    @Query("SELECT * FROM clinical_interventions ORDER BY dateAdded DESC")
    fun getAllClinicalInterventions(): Flow<List<ClinicalIntervention>>

    @Query("SELECT * FROM clinical_interventions WHERE id = :id")
    suspend fun getClinicalInterventionById(id: Int): ClinicalIntervention?

    @Query("SELECT * FROM clinical_interventions WHERE customerId = :customerId ORDER BY dateAdded DESC")
    fun getClinicalInterventionsForCustomer(customerId: Int): Flow<List<ClinicalIntervention>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClinicalIntervention(intervention: ClinicalIntervention): Long

    @Update
    suspend fun updateClinicalIntervention(intervention: ClinicalIntervention)

    @Delete
    suspend fun deleteClinicalIntervention(intervention: ClinicalIntervention)

    // --- AI Carousels Opers ---
    @Query("SELECT * FROM ai_carousels ORDER BY createdAt DESC")
    fun getAllAICarousels(): Flow<List<AICarousel>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAICarousel(carousel: AICarousel): Long

    @Delete
    suspend fun deleteAICarousel(carousel: AICarousel)

    @Query("DELETE FROM operations_tasks")
    suspend fun clearOperationTasks()

    @Query("DELETE FROM receipts")
    suspend fun clearReceipts()
    
    @Query("DELETE FROM inventory_items")
    suspend fun clearInventoryItems()
    
    @Query("DELETE FROM prescription_volumes")
    suspend fun clearPrescriptionVolumes()
    
    @Query("DELETE FROM customer_alerts")
    suspend fun clearCustomerAlerts()
    
    @Query("DELETE FROM customers")
    suspend fun clearCustomers()
    
    @Query("DELETE FROM customer_medications")
    suspend fun clearCustomerMedications()
    
    @Query("DELETE FROM clinical_interventions")
    suspend fun clearClinicalInterventions()
    
    @Query("DELETE FROM ai_carousels")
    suspend fun clearAICarousels()

    // --- Pharmacy Triage Knowledge Base Opers ---
    @Query("SELECT * FROM triage_conditions ORDER BY isFavorite DESC, conditionName ASC")
    fun getAllTriageConditions(): Flow<List<TriageCondition>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTriageCondition(condition: TriageCondition): Long

    @Update
    suspend fun updateTriageCondition(condition: TriageCondition)

    @Delete
    suspend fun deleteTriageCondition(condition: TriageCondition)

    @Query("SELECT * FROM triage_conditions WHERE id = :id")
    suspend fun getTriageConditionById(id: Int): TriageCondition?

    @Query("DELETE FROM triage_conditions")
    suspend fun clearTriageConditions()

    // --- Medication Sales Opers ---
    @Query("SELECT * FROM medication_sales ORDER BY dateSold DESC")
    fun getAllMedicationSales(): Flow<List<MedicationSale>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedicationSale(sale: MedicationSale)

    @Query("DELETE FROM medication_sales")
    suspend fun clearMedicationSales()

    // --- Rescue Listings Opers ---
    @Query("SELECT * FROM rescue_listings ORDER BY listedAt DESC")
    fun getAllRescueListings(): Flow<List<RescueListing>>

    @Query("SELECT * FROM rescue_listings WHERE firestoreId = :firestoreId LIMIT 1")
    suspend fun getRescueListingByFirestoreId(firestoreId: String): RescueListing?

    @Query("DELETE FROM rescue_listings WHERE firestoreId = :firestoreId")
    suspend fun deleteRescueListingByFirestoreId(firestoreId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRescueListing(listing: RescueListing)

    @Update
    suspend fun updateRescueListing(listing: RescueListing)

    @Query("DELETE FROM rescue_listings")
    suspend fun clearRescueListings()

    // --- Admin Audit Logs Opers ---
    @Query("SELECT * FROM admin_audit_logs ORDER BY timestamp DESC")
    fun getAllAdminAuditLogs(): Flow<List<AdminAuditLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAdminAuditLog(log: AdminAuditLog)

    @Query("DELETE FROM admin_audit_logs")
    suspend fun clearAdminAuditLogs()

    // --- Inventory Batches Opers ---
    @Query("SELECT * FROM inventory_batches ORDER BY expiryDate ASC")
    fun getAllInventoryBatches(): Flow<List<InventoryBatch>>

    @Query("SELECT * FROM inventory_batches WHERE inventoryItemId = :itemId ORDER BY expiryDate ASC")
    fun getBatchesForItem(itemId: Int): Flow<List<InventoryBatch>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInventoryBatch(batch: InventoryBatch): Long

    @Update
    suspend fun updateInventoryBatch(batch: InventoryBatch)

    @Delete
    suspend fun deleteInventoryBatch(batch: InventoryBatch)

    @Query("DELETE FROM inventory_batches WHERE id = :id")
    suspend fun deleteInventoryBatchById(id: Int)

    @Query("DELETE FROM inventory_batches")
    suspend fun clearInventoryBatches()

    // --- Outbound SMS Logs Opers ---
    @Query("SELECT * FROM outbound_sms_logs ORDER BY timestamp DESC")
    fun getAllSmsLogs(): Flow<List<OutboundSmsLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSmsLog(log: OutboundSmsLog): Long

    @Update
    suspend fun updateSmsLog(log: OutboundSmsLog)

    @Query("DELETE FROM outbound_sms_logs")
    suspend fun clearSmsLogs()

    // --- Expiry Alert Claims Opers ---
    @Query("SELECT * FROM expiry_alert_claims")
    fun getAllExpiryAlertClaims(): Flow<List<ExpiryAlertClaim>>

    @Query("SELECT * FROM expiry_alert_claims WHERE inventoryItemId = :itemId LIMIT 1")
    suspend fun getExpiryAlertClaimByItemId(itemId: Int): ExpiryAlertClaim?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpiryAlertClaim(claim: ExpiryAlertClaim)

    @Query("DELETE FROM expiry_alert_claims")
    suspend fun clearExpiryAlertClaims()

    // --- Organizations Opers ---
    @Query("SELECT * FROM organizations LIMIT 1")
    fun getOrganization(): Flow<Organization?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrganization(org: Organization): Long

    // --- Users & Branch Access Opers ---
    @Query("SELECT * FROM users ORDER BY fullName ASC")
    fun getAllUsers(): Flow<List<User>>

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: Int): User?

    @Query("SELECT * FROM users WHERE phoneNumber = :phone LIMIT 1")
    suspend fun getUserByPhone(phone: String): User?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User): Long

    @Update
    suspend fun updateUser(user: User)

    @Query("SELECT * FROM user_branch_access WHERE userId = :userId")
    fun getUserBranchAccess(userId: Int): Flow<List<UserBranchAccess>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserBranchAccess(access: UserBranchAccess): Long

    // --- Customer Branch Opers ---
    @Query("SELECT * FROM customer_branches WHERE customerId = :customerId")
    fun getCustomerBranches(customerId: Int): Flow<List<CustomerBranch>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomerBranch(cb: CustomerBranch): Long

    // --- Double-Entry Inventory Ledger Opers ---
    @Query("SELECT * FROM inventory_ledger_entries ORDER BY timestamp DESC")
    fun getAllInventoryLedgerEntries(): Flow<List<InventoryLedgerEntry>>

    @Query("SELECT * FROM inventory_ledger_entries WHERE inventoryItemId = :itemId ORDER BY timestamp DESC")
    fun getLedgerEntriesByItem(itemId: Int): Flow<List<InventoryLedgerEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInventoryLedgerEntry(entry: InventoryLedgerEntry): Long

    @Query("DELETE FROM inventory_ledger_entries")
    suspend fun clearInventoryLedgerEntries()

    @Transaction
    suspend fun executeCheckoutTransaction(
        updatedItem: InventoryItem,
        updatedBatches: List<InventoryBatch>,
        sale: MedicationSale,
        ledgerEntry: InventoryLedgerEntry,
        outboxRecord: com.example.data.sync.SyncOutboxRecord? = null
    ) {
        for (batch in updatedBatches) {
            if (batch.id == 0) {
                insertInventoryBatch(batch)
            } else {
                updateInventoryBatch(batch)
            }
        }
        updateInventoryItem(updatedItem)
        insertMedicationSale(sale)
        insertInventoryLedgerEntry(ledgerEntry)
        if (outboxRecord != null) {
            insertOutboxRecord(outboxRecord)
        }
    }

    @Transaction
    suspend fun insertMedicationSaleAndOutbox(sale: MedicationSale, outbox: com.example.data.sync.SyncOutboxRecord) {
        if (outbox.payloadJson.isNotBlank()) org.json.JSONObject(outbox.payloadJson)
        insertMedicationSale(sale)
        insertOutboxRecord(outbox)
    }

    @Transaction
    suspend fun insertCustomerAndOutbox(customer: Customer, outbox: com.example.data.sync.SyncOutboxRecord): Long {
        val jsonObj = org.json.JSONObject(outbox.payloadJson)
        val id = insertCustomer(customer)
        jsonObj.put("id", id)
        val updatedOutbox = outbox.copy(
            entityId = if (outbox.entityId.isBlank() || outbox.entityId == "0") id.toString() else outbox.entityId,
            payloadJson = jsonObj.toString()
        )
        insertOutboxRecord(updatedOutbox)
        return id
    }

    @Transaction
    suspend fun updateCustomerAndOutbox(customer: Customer, outbox: com.example.data.sync.SyncOutboxRecord) {
        if (outbox.payloadJson.isNotBlank()) org.json.JSONObject(outbox.payloadJson)
        updateCustomer(customer)
        insertOutboxRecord(outbox)
    }

    @Transaction
    suspend fun deleteCustomerAndOutbox(customer: Customer, outbox: com.example.data.sync.SyncOutboxRecord) {
        if (outbox.payloadJson.isNotBlank()) org.json.JSONObject(outbox.payloadJson)
        deleteCustomer(customer)
        insertOutboxRecord(outbox)
    }

    @Transaction
    suspend fun insertCustomerMedicationAndOutbox(medication: CustomerMedication, outbox: com.example.data.sync.SyncOutboxRecord): Long {
        val jsonObj = org.json.JSONObject(outbox.payloadJson)
        val id = insertCustomerMedication(medication)
        jsonObj.put("id", id)
        val updatedOutbox = outbox.copy(
            entityId = if (outbox.entityId.isBlank() || outbox.entityId == "0") id.toString() else outbox.entityId,
            payloadJson = jsonObj.toString()
        )
        insertOutboxRecord(updatedOutbox)
        return id
    }

    @Transaction
    suspend fun updateCustomerMedicationAndOutbox(medication: CustomerMedication, outbox: com.example.data.sync.SyncOutboxRecord) {
        if (outbox.payloadJson.isNotBlank()) org.json.JSONObject(outbox.payloadJson)
        updateCustomerMedication(medication)
        insertOutboxRecord(outbox)
    }

    @Transaction
    suspend fun deleteCustomerMedicationAndOutbox(medication: CustomerMedication, outbox: com.example.data.sync.SyncOutboxRecord) {
        if (outbox.payloadJson.isNotBlank()) org.json.JSONObject(outbox.payloadJson)
        deleteCustomerMedication(medication)
        insertOutboxRecord(outbox)
    }

    @Transaction
    suspend fun insertClinicalInterventionAndOutbox(intervention: ClinicalIntervention, outbox: com.example.data.sync.SyncOutboxRecord): Long {
        val jsonObj = org.json.JSONObject(outbox.payloadJson)
        val id = insertClinicalIntervention(intervention)
        jsonObj.put("id", id)
        val updatedOutbox = outbox.copy(
            entityId = if (outbox.entityId.isBlank() || outbox.entityId == "0") id.toString() else outbox.entityId,
            payloadJson = jsonObj.toString()
        )
        insertOutboxRecord(updatedOutbox)
        return id
    }

    @Transaction
    suspend fun updateClinicalInterventionAndOutbox(intervention: ClinicalIntervention, outbox: com.example.data.sync.SyncOutboxRecord) {
        if (outbox.payloadJson.isNotBlank()) org.json.JSONObject(outbox.payloadJson)
        updateClinicalIntervention(intervention)
        insertOutboxRecord(outbox)
    }

    @Transaction
    suspend fun deleteClinicalInterventionAndOutbox(intervention: ClinicalIntervention, outbox: com.example.data.sync.SyncOutboxRecord) {
        if (outbox.payloadJson.isNotBlank()) org.json.JSONObject(outbox.payloadJson)
        deleteClinicalIntervention(intervention)
        insertOutboxRecord(outbox)
    }

    @Transaction
    suspend fun insertInventoryItemAndOutbox(item: InventoryItem, outbox: com.example.data.sync.SyncOutboxRecord): Long {
        val jsonObj = org.json.JSONObject(outbox.payloadJson)
        val id = insertInventoryItem(item)
        jsonObj.put("id", id)
        val updatedOutbox = outbox.copy(
            entityId = if (outbox.entityId.isBlank() || outbox.entityId == "0") id.toString() else outbox.entityId,
            payloadJson = jsonObj.toString()
        )
        insertOutboxRecord(updatedOutbox)
        return id
    }

    @Transaction
    suspend fun updateInventoryItemAndOutbox(item: InventoryItem, outbox: com.example.data.sync.SyncOutboxRecord) {
        if (outbox.payloadJson.isNotBlank()) org.json.JSONObject(outbox.payloadJson)
        updateInventoryItem(item)
        insertOutboxRecord(outbox)
    }

    @Transaction
    suspend fun deleteInventoryItemAndOutbox(item: InventoryItem, outbox: com.example.data.sync.SyncOutboxRecord) {
        if (outbox.payloadJson.isNotBlank()) org.json.JSONObject(outbox.payloadJson)
        deleteInventoryItem(item)
        insertOutboxRecord(outbox)
    }

    @Transaction
    suspend fun insertOperationTaskAndOutbox(task: OperationTask, outbox: com.example.data.sync.SyncOutboxRecord) {
        if (outbox.payloadJson.isNotBlank()) org.json.JSONObject(outbox.payloadJson)
        insertOperationTask(task)
        insertOutboxRecord(outbox)
    }

    @Transaction
    suspend fun updateOperationTaskAndOutbox(task: OperationTask, outbox: com.example.data.sync.SyncOutboxRecord) {
        if (outbox.payloadJson.isNotBlank()) org.json.JSONObject(outbox.payloadJson)
        updateOperationTask(task)
        insertOutboxRecord(outbox)
    }

    @Transaction
    suspend fun deleteOperationTaskAndOutbox(task: OperationTask, outbox: com.example.data.sync.SyncOutboxRecord) {
        if (outbox.payloadJson.isNotBlank()) org.json.JSONObject(outbox.payloadJson)
        deleteOperationTask(task)
        insertOutboxRecord(outbox)
    }

    @Transaction
    suspend fun insertReceiptAndOutbox(receipt: Receipt, outbox: com.example.data.sync.SyncOutboxRecord) {
        if (outbox.payloadJson.isNotBlank()) org.json.JSONObject(outbox.payloadJson)
        insertReceipt(receipt)
        insertOutboxRecord(outbox)
    }

    @Transaction
    suspend fun updateReceiptAndOutbox(receipt: Receipt, outbox: com.example.data.sync.SyncOutboxRecord) {
        if (outbox.payloadJson.isNotBlank()) org.json.JSONObject(outbox.payloadJson)
        updateReceipt(receipt)
        insertOutboxRecord(outbox)
    }

    @Transaction
    suspend fun deleteReceiptAndOutbox(receipt: Receipt, outbox: com.example.data.sync.SyncOutboxRecord) {
        if (outbox.payloadJson.isNotBlank()) org.json.JSONObject(outbox.payloadJson)
        deleteReceipt(receipt)
        insertOutboxRecord(outbox)
    }

    // --- Sync Outbox Operations ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutboxRecord(record: com.example.data.sync.SyncOutboxRecord): Long

    @Update
    suspend fun updateOutboxRecord(record: com.example.data.sync.SyncOutboxRecord)

    @Query("DELETE FROM sync_outbox WHERE id = :id")
    suspend fun deleteOutboxRecordById(id: Int)

    @Query("SELECT * FROM sync_outbox WHERE status = 'PENDING' OR status = 'FAILED' OR (status = 'IN_PROGRESS' AND lastAttemptAt < :stuckThresholdMs) ORDER BY createdAt ASC")
    suspend fun getPendingOutboxRecords(stuckThresholdMs: Long = 0L): List<com.example.data.sync.SyncOutboxRecord>

    @Query("SELECT * FROM sync_outbox WHERE branchId = :branchId ORDER BY createdAt ASC")
    fun getOutboxRecordsForBranch(branchId: String): Flow<List<com.example.data.sync.SyncOutboxRecord>>

    @Query("SELECT * FROM sync_outbox WHERE clientTransactionId = :clientTxId LIMIT 1")
    suspend fun getOutboxRecordByClientTxId(clientTxId: String): com.example.data.sync.SyncOutboxRecord?

    @Query("DELETE FROM sync_outbox")
    suspend fun clearSyncOutbox()

    // --- Branch-Scoped Queries ---
    @Query("SELECT * FROM customers WHERE branchId = :branchId ORDER BY name ASC")
    fun getCustomersForBranch(branchId: String): Flow<List<Customer>>

    @Query("SELECT * FROM inventory_items WHERE branchId = :branchId ORDER BY name ASC")
    fun getInventoryForBranch(branchId: String): Flow<List<InventoryItem>>

    @Query("SELECT * FROM inventory_items WHERE branchId = :branchId AND stockQuantity <= minRequiredStock")
    fun getLowStockItemsForBranch(branchId: String): Flow<List<InventoryItem>>

    @Query("SELECT * FROM operations_tasks WHERE branchId = :branchId ORDER BY isCompleted ASC, createdAt DESC")
    fun getOperationTasksForBranch(branchId: String): Flow<List<OperationTask>>

    @Query("SELECT * FROM receipts WHERE branchId = :branchId ORDER BY timestamp DESC")
    fun getReceiptsForBranch(branchId: String): Flow<List<Receipt>>

    @Query("SELECT * FROM medication_sales WHERE branchId = :branchId ORDER BY dateSold DESC")
    fun getMedicationSalesForBranch(branchId: String): Flow<List<MedicationSale>>

    @Query("SELECT * FROM medication_sales WHERE clientTransactionId = :clientTxId LIMIT 1")
    suspend fun getMedicationSaleByClientTransactionId(clientTxId: String): MedicationSale?

    @Query("SELECT * FROM clinical_interventions WHERE branchId = :branchId ORDER BY dateAdded DESC")
    fun getClinicalInterventionsForBranch(branchId: String): Flow<List<ClinicalIntervention>>

    @Query("SELECT * FROM customer_medications WHERE branchId = :branchId")
    fun getCustomerMedicationsForBranch(branchId: String): Flow<List<CustomerMedication>>

    @Query("SELECT * FROM inventory_ledger_entries WHERE branchId = :branchId ORDER BY timestamp DESC")
    fun getInventoryLedgerEntriesForBranch(branchId: String): Flow<List<InventoryLedgerEntry>>

    @Transaction
    suspend fun clearAllData() {
        clearOperationTasks()
        clearReceipts()
        clearInventoryItems()
        clearPrescriptionVolumes()
        clearCustomerAlerts()
        clearCustomers()
        clearCustomerMedications()
        clearClinicalInterventions()
        clearAICarousels()
        clearTriageConditions()
        clearMedicationSales()
        clearRescueListings()
        clearAdminAuditLogs()
        clearInventoryBatches()
        clearSmsLogs()
        clearExpiryAlertClaims()
        clearInventoryLedgerEntries()
    }
}
