package com.example.data

import kotlinx.coroutines.flow.Flow

class PharmacyRepository(private val pharmacyDao: PharmacyDao) {

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

    suspend fun insertCustomerMedication(medication: CustomerMedication) {
        pharmacyDao.insertCustomerMedication(medication)
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

    suspend fun insertAICarousel(carousel: AICarousel) {
        pharmacyDao.insertAICarousel(carousel)
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

    suspend fun clearAllData() {
        pharmacyDao.clearAllData()
    }
}
