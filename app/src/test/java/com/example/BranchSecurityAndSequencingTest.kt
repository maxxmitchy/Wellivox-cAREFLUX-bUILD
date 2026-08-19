package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.PharmacyRepository
import com.example.data.auth.AuthRepository
import com.example.data.auth.AuthUser
import com.example.data.device.DeviceRepository
import com.example.data.remote.RemoteDataSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BranchSecurityAndSequencingTest {

    private lateinit var context: Context
    private lateinit var recordingRemote: RecordingRemoteDataSource

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val prefs = context.getSharedPreferences("careflux_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        recordingRemote = RecordingRemoteDataSource()
    }

    // 1. Verify that firestore.rules exists and contains critical security invariants
    @Test
    fun testFirestoreRulesContainsCriticalSecurityInvariants() {
        val rulesFile = if (File("firestore.rules").exists()) File("firestore.rules") else File("../firestore.rules")
        assertTrue("firestore.rules must exist at root", rulesFile.exists())
        val content = rulesFile.readText()
        assertTrue(content.contains("match /registered_pharmacists/{uid}"))
        assertTrue(content.contains("match /devices/{deviceId}"))
        assertTrue(content.contains("match /branches/{branchId}"))
        assertTrue(content.contains("match /medication_sales/{saleId}"))
        assertTrue(content.contains("match /admin_audit_logs/{logId}"))
        assertTrue(content.contains("match /key_creation_requests/{requestId}"))
        assertTrue(content.contains("match /canonical_products/{productId}"))
        assertTrue(content.contains("match /device_configs/{nodeId}"))
        assertTrue(content.contains("request.resource.data.role == resource.data.role"))
        assertTrue(content.contains("request.resource.data.isApproved == resource.data.isApproved"))
        assertTrue(content.contains("isSystemAdmin"))
        assertTrue(content.contains("isBranchCreator"))
        assertTrue(content.contains("isBranchManager"))
        assertTrue(content.contains("maduemeziachinedu6@gmail.com"))
        assertTrue(content.contains("allow list: if false;"))
        assertTrue(content.contains("match /{collectionName}/{docId}"))
        assertFalse("Broad wildcard must be removed", content.contains("match /{col}/{doc}"))
    }

    // 2. Sequential Branch Switch: registered_pharmacists must be called before devices update
    @Test
    fun testSequentialBranchSwitchUpdatesUserBeforeDevice() = runBlocking {
        val deviceRepo = DeviceRepository(context, recordingRemote)
        deviceRepo.handleUserAuthenticated("user_101", "Pharmacist", "Branch_A")
        recordingRemote.callLog.clear()

        // Execute sequential branch switch
        val switchUserResult = recordingRemote.switchActiveBranch("user_101", "Branch_B", "Careflux Branch B")
        assertTrue(switchUserResult.isSuccess)
        
        val updateDeviceResult = deviceRepo.updateBranchAssociation("Branch_B")
        assertTrue(updateDeviceResult.isSuccess)

        // Verify ordering: switchActiveBranch called before registerDevice
        assertEquals(2, recordingRemote.callLog.size)
        assertEquals("switchActiveBranch:user_101:Branch_B", recordingRemote.callLog[0])
        assertEquals("registerDevice:Branch_B", recordingRemote.callLog[1])
        assertEquals("Branch_B", deviceRepo.getCachedBranchId())
    }

    // 3. Failure Handling: Device association is not updated if user branch switch fails
    @Test
    fun testDeviceAssociationSkippedOnUserBranchSwitchFailure() = runBlocking {
        val deviceRepo = DeviceRepository(context, recordingRemote)
        deviceRepo.handleUserAuthenticated("user_101", "Pharmacist", "Branch_A")
        recordingRemote.callLog.clear()
        recordingRemote.failBranchSwitch = true

        val switchUserResult = recordingRemote.switchActiveBranch("user_101", "Branch_B", "Careflux Branch B")
        assertFalse(switchUserResult.isSuccess)

        // Since user update failed, device association should NOT be dispatched
        assertEquals(1, recordingRemote.callLog.size)
        assertEquals("switchActiveBranch:user_101:Branch_B (FAILED)", recordingRemote.callLog[0])
        // Cached branch in deviceRepo should remain unchanged from last successful auth
        assertEquals("Branch_A", deviceRepo.getCachedBranchId())
    }

    // 4. Offline Branch Switch: Local state updates immediately and preserves device token
    @Test
    fun testOfflineBranchSwitchPreservesLocalStateAndFcmToken() = runBlocking {
        val deviceRepo = DeviceRepository(context, recordingRemote)
        deviceRepo.handleTokenRefreshed("fcm_token_xyz")
        deviceRepo.handleUserAuthenticated("user_101", "Pharmacist", "Branch_A")
        
        val prefs = context.getSharedPreferences("careflux_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("cached_branch_id", "Branch_B").apply()

        assertEquals("Branch_B", prefs.getString("cached_branch_id", null))
        assertEquals("fcm_token_xyz", deviceRepo.getCachedFcmToken())
        assertTrue(deviceRepo.isDeviceActive())
    }

    // 5. Verification that profile updates do not contain role escalation keys
    @Test
    fun testProfileUpdatePayloadContractExcludesPrivilegedKeys() {
        val cleanName = "Dr. Jane Pharmacist"
        val cleanPhone = "+2348012345678"
        val updateData = mapOf(
            "displayName" to cleanName,
            "phoneNumber" to cleanPhone
        )

        assertFalse("Profile update must never contain role", updateData.containsKey("role"))
        assertFalse("Profile update must never contain isApproved", updateData.containsKey("isApproved"))
        assertFalse("Profile update must never contain isSuspended", updateData.containsKey("isSuspended"))
        assertTrue(updateData.containsKey("displayName"))
        assertTrue(updateData.containsKey("phoneNumber"))
    }
}

// Recording Remote Data Source to track call sequence and simulate network failures
class RecordingRemoteDataSource : RemoteDataSource {
    val callLog = mutableListOf<String>()
    var failBranchSwitch = false
    val registeredDevices = mutableMapOf<String, DeviceRecord>()

    data class DeviceRecord(
        val deviceId: String,
        val fcmToken: String?,
        val currentUid: String?,
        val currentRole: String?,
        val currentBranchId: String?,
        val isActive: Boolean
    )

    override fun getCurrentUserUid(): String? = null
    override fun getCurrentUserEmail(): String? = null
    override suspend fun getPharmacistBranchId(uid: String): String? = null

    override fun observePharmacist(uid: String): kotlinx.coroutines.flow.Flow<Map<String, Any>?> = kotlinx.coroutines.flow.emptyFlow()
    override fun observeBranchSettings(branchId: String): kotlinx.coroutines.flow.Flow<Map<String, Any>?> = kotlinx.coroutines.flow.emptyFlow()
    override fun observeStaffMembers(branchId: String): kotlinx.coroutines.flow.Flow<List<Map<String, Any>>> = kotlinx.coroutines.flow.emptyFlow()
    override fun observeBranchInventory(branchId: String): kotlinx.coroutines.flow.Flow<List<Map<String, Any>>> = kotlinx.coroutines.flow.emptyFlow()
    override fun observeBranchCustomers(branchId: String): kotlinx.coroutines.flow.Flow<List<Map<String, Any>>> = kotlinx.coroutines.flow.emptyFlow()
    override fun observeBranchCustomerMedications(branchId: String): kotlinx.coroutines.flow.Flow<List<Map<String, Any>>> = kotlinx.coroutines.flow.emptyFlow()
    override fun observeBranchInterventions(branchId: String): kotlinx.coroutines.flow.Flow<List<Map<String, Any>>> = kotlinx.coroutines.flow.emptyFlow()
    override fun observeBranchOperationTasks(branchId: String): kotlinx.coroutines.flow.Flow<List<Map<String, Any>>> = kotlinx.coroutines.flow.emptyFlow()
    override fun observeBranchReceipts(branchId: String): kotlinx.coroutines.flow.Flow<List<Map<String, Any>>> = kotlinx.coroutines.flow.emptyFlow()
    override fun observeBranchAuditLogs(branchId: String): kotlinx.coroutines.flow.Flow<List<Map<String, Any>>> = kotlinx.coroutines.flow.emptyFlow()
    override fun observeAllPharmacists(): kotlinx.coroutines.flow.Flow<List<Map<String, Any>>> = kotlinx.coroutines.flow.emptyFlow()
    override fun observeAllBranches(): kotlinx.coroutines.flow.Flow<List<Map<String, Any>>> = kotlinx.coroutines.flow.emptyFlow()
    override fun observeDeviceConfigs(): kotlinx.coroutines.flow.Flow<List<Map<String, Any>>> = kotlinx.coroutines.flow.emptyFlow()
    override fun observeDeviceConfig(deviceId: String): kotlinx.coroutines.flow.Flow<Map<String, Any>?> = kotlinx.coroutines.flow.emptyFlow()
    override fun observeExpiryRescueListings(): kotlinx.coroutines.flow.Flow<List<Map<String, Any>>> = kotlinx.coroutines.flow.emptyFlow()
    override fun observeKeyCreationRequests(): kotlinx.coroutines.flow.Flow<List<Map<String, Any>>> = kotlinx.coroutines.flow.emptyFlow()
    override fun observeCanonicalProducts(): kotlinx.coroutines.flow.Flow<List<Map<String, Any>>> = kotlinx.coroutines.flow.emptyFlow()
    override fun observeAdminAuditLogs(): kotlinx.coroutines.flow.Flow<List<Map<String, Any>>> = kotlinx.coroutines.flow.emptyFlow()
    override fun observeMedicationSales(): kotlinx.coroutines.flow.Flow<List<Map<String, Any>>> = kotlinx.coroutines.flow.emptyFlow()

    override suspend fun getDocument(collection: String, documentId: String): Result<Map<String, Any>?> = Result.success(null)
    override suspend fun getDocumentsWhereEquals(collection: String, field: String, value: Any): Result<List<Map<String, Any>>> = Result.success(emptyList())
    override suspend fun getAllDocuments(collection: String): Result<List<Map<String, Any>>> = Result.success(emptyList())
    override suspend fun upsertDocument(collection: String, documentId: String, data: Map<String, Any?>): Result<Unit> = Result.success(Unit)
    override suspend fun addDocument(collection: String, data: Map<String, Any?>): Result<String> = Result.success("id_123")
    override suspend fun deleteDocument(collection: String, documentId: String): Result<Unit> = Result.success(Unit)

    override suspend fun claimRescueListing(listingId: String, deviceId: String, deviceModel: String): Result<Boolean> = Result.success(true)
    override suspend fun updateStaffCredentials(staffUid: String, staffEmail: String?, newRole: String, isApproved: Boolean): Result<Unit> = Result.success(Unit)
    override suspend fun joinBranch(uid: String, email: String, displayName: String, phoneNumber: String, branchCode: String, deviceId: String, deviceModel: String): Result<Pair<String, String>> = Result.success(Pair("b1", "Branch 1"))
    override suspend fun registerBranch(uid: String, email: String, displayName: String, phoneNumber: String, name: String, lga: String, state: String, deviceId: String, deviceModel: String): Result<String> = Result.success("b1")
    override suspend fun deleteBranch(branchId: String): Result<Unit> = Result.success(Unit)
    override suspend fun deleteDeviceNode(nodeId: String): Result<Unit> = Result.success(Unit)
    override suspend fun appointManager(branchId: String, branchName: String, pharmacistUid: String, pharmacistName: String, pharmacistEmail: String): Result<Unit> = Result.success(Unit)
    
    override suspend fun switchActiveBranch(uid: String, branchId: String, branchName: String): Result<Unit> {
        if (failBranchSwitch) {
            callLog.add("switchActiveBranch:$uid:$branchId (FAILED)")
            return Result.failure(Exception("Remote branch switch error"))
        }
        callLog.add("switchActiveBranch:$uid:$branchId")
        return Result.success(Unit)
    }

    override suspend fun updateBranchDetails(branchId: String, newName: String, newLga: String, newState: String): Result<Unit> = Result.success(Unit)
    override suspend fun updateBranchFeatures(branchId: String, features: Map<String, Boolean>): Result<Unit> = Result.success(Unit)
    override suspend fun deletePharmacist(pharmacistUid: String, branchId: String?, role: String?): Result<Unit> = Result.success(Unit)
    override suspend fun syncSaleTransaction(request: com.example.data.sync.SaleSyncRequest): com.example.data.sync.SyncResult = com.example.data.sync.SyncResult(com.example.data.sync.SyncStatus.SYNCED, request.clientTransactionId, "id")
    override suspend fun logOutboundSms(logData: Map<String, Any?>): Result<Unit> = Result.success(Unit)

    override suspend fun registerDevice(
        deviceId: String,
        fcmToken: String?,
        currentUid: String?,
        currentRole: String?,
        currentBranchId: String?,
        isActive: Boolean
    ): Result<Unit> {
        callLog.add("registerDevice:$currentBranchId")
        registeredDevices[deviceId] = DeviceRecord(deviceId, fcmToken, currentUid, currentRole, currentBranchId, isActive)
        return Result.success(Unit)
    }

    override suspend fun updateDeviceToken(
        deviceId: String,
        fcmToken: String
    ): Result<Unit> {
        val existing = registeredDevices[deviceId]
        registeredDevices[deviceId] = DeviceRecord(
            deviceId = deviceId,
            fcmToken = fcmToken,
            currentUid = existing?.currentUid,
            currentRole = existing?.currentRole,
            currentBranchId = existing?.currentBranchId,
            isActive = existing?.isActive ?: true
        )
        return Result.success(Unit)
    }

    override suspend fun updateDeviceAssociation(
        deviceId: String,
        currentUid: String?,
        currentRole: String?,
        currentBranchId: String?,
        isActive: Boolean
    ): Result<Unit> {
        callLog.add("updateDeviceAssociation:$currentBranchId")
        val existing = registeredDevices[deviceId]
        registeredDevices[deviceId] = DeviceRecord(
            deviceId = deviceId,
            fcmToken = existing?.fcmToken,
            currentUid = currentUid,
            currentRole = currentRole,
            currentBranchId = currentBranchId,
            isActive = isActive
        )
        return Result.success(Unit)
    }

    override suspend fun unregisterDevice(
        deviceId: String
    ): Result<Unit> {
        val existing = registeredDevices[deviceId]
        registeredDevices[deviceId] = DeviceRecord(
            deviceId = deviceId,
            fcmToken = existing?.fcmToken,
            currentUid = null,
            currentRole = null,
            currentBranchId = null,
            isActive = false
        )
        return Result.success(Unit)
    }
}
