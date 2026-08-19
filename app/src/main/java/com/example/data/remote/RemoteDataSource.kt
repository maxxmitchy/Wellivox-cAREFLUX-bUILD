package com.example.data.remote

import com.example.data.sync.SaleSyncRequest
import com.example.data.sync.SyncResult
import kotlinx.coroutines.flow.Flow

interface RemoteDataSource {
    fun getCurrentUserUid(): String?
    fun getCurrentUserEmail(): String?

    suspend fun getPharmacistBranchId(uid: String): String?

    // Real-time flow streams
    fun observePharmacist(uid: String): Flow<Map<String, Any>?>
    fun observeBranchSettings(branchId: String): Flow<Map<String, Any>?>
    fun observeStaffMembers(branchId: String = ""): Flow<List<Map<String, Any>>>
    fun observeBranchInventory(branchId: String = ""): Flow<List<Map<String, Any>>>
    fun observeBranchCustomers(branchId: String = ""): Flow<List<Map<String, Any>>>
    fun observeBranchCustomerMedications(branchId: String = ""): Flow<List<Map<String, Any>>>
    fun observeBranchInterventions(branchId: String = ""): Flow<List<Map<String, Any>>>
    fun observeBranchOperationTasks(branchId: String = ""): Flow<List<Map<String, Any>>>
    fun observeBranchReceipts(branchId: String = ""): Flow<List<Map<String, Any>>>
    fun observeBranchAuditLogs(branchId: String = ""): Flow<List<Map<String, Any>>>
    fun observeAllPharmacists(): Flow<List<Map<String, Any>>>
    fun observeAllBranches(): Flow<List<Map<String, Any>>>
    fun observeDeviceConfigs(): Flow<List<Map<String, Any>>>
    fun observeDeviceConfig(deviceId: String): Flow<Map<String, Any>?>
    fun observeExpiryRescueListings(): Flow<List<Map<String, Any>>>
    fun observeKeyCreationRequests(): Flow<List<Map<String, Any>>>
    fun observeCanonicalProducts(): Flow<List<Map<String, Any>>>
    fun observeAdminAuditLogs(): Flow<List<Map<String, Any>>>
    fun observeMedicationSales(branchId: String = ""): Flow<List<Map<String, Any>>>

    // Explicit Document Operations
    suspend fun getDocument(collection: String, documentId: String): Result<Map<String, Any>?>
    suspend fun getDocumentsWhereEquals(collection: String, field: String, value: Any): Result<List<Map<String, Any>>>
    suspend fun getAllDocuments(collection: String): Result<List<Map<String, Any>>>
    suspend fun upsertDocument(collection: String, documentId: String, data: Map<String, Any?>): Result<Unit>
    suspend fun addDocument(collection: String, data: Map<String, Any?>): Result<String>
    suspend fun deleteDocument(collection: String, documentId: String): Result<Unit>

    // Compound / Domain Remote Operations
    suspend fun claimRescueListing(listingId: String, deviceId: String, deviceModel: String): Result<Boolean>
    suspend fun updateStaffCredentials(staffUid: String, staffEmail: String?, newRole: String, isApproved: Boolean): Result<Unit>
    suspend fun joinBranch(uid: String, email: String, displayName: String, phoneNumber: String, branchCode: String, deviceId: String, deviceModel: String): Result<Pair<String, String>>
    suspend fun registerBranch(uid: String, email: String, displayName: String, phoneNumber: String, name: String, lga: String, state: String, deviceId: String, deviceModel: String): Result<String>
    suspend fun deleteBranch(branchId: String): Result<Unit>
    suspend fun deleteDeviceNode(nodeId: String): Result<Unit>
    suspend fun appointManager(branchId: String, branchName: String, pharmacistUid: String, pharmacistName: String, pharmacistEmail: String): Result<Unit>
    suspend fun switchActiveBranch(uid: String, branchId: String, branchName: String): Result<Unit>
    suspend fun updateBranchDetails(branchId: String, newName: String, newLga: String, newState: String): Result<Unit>
    suspend fun updateBranchFeatures(branchId: String, features: Map<String, Boolean>): Result<Unit>
    suspend fun deletePharmacist(pharmacistUid: String, branchId: String?, role: String?): Result<Unit>

    // Sale Sync & Outbound Operations
    suspend fun syncSaleTransaction(request: SaleSyncRequest): SyncResult
    suspend fun logOutboundSms(logData: Map<String, Any?>): Result<Unit>

    // Device Registration & FCM Token Operations
    suspend fun registerDevice(
        deviceId: String,
        fcmToken: String?,
        currentUid: String?,
        currentRole: String?,
        currentBranchId: String?,
        isActive: Boolean
    ): Result<Unit>

    suspend fun updateDeviceToken(
        deviceId: String,
        fcmToken: String
    ): Result<Unit>

    suspend fun updateDeviceAssociation(
        deviceId: String,
        currentUid: String?,
        currentRole: String?,
        currentBranchId: String?,
        isActive: Boolean
    ): Result<Unit>

    suspend fun unregisterDevice(
        deviceId: String
    ): Result<Unit>
}

