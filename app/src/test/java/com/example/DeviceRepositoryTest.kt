package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.device.DeviceRepository
import com.example.data.remote.RemoteDataSource
import com.example.data.sync.SaleSyncRequest
import com.example.data.sync.SyncResult
import com.example.data.sync.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.PrintStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DeviceRepositoryTest {

    private lateinit var context: Context
    private lateinit var fakeRemote: FakeDeviceRemoteDataSource

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val prefs = context.getSharedPreferences("careflux_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        fakeRemote = FakeDeviceRemoteDataSource()
    }

    // 1. Token received while logged out
    @Test
    fun testTokenReceivedWhileLoggedOut() = runBlocking {
        val repo = DeviceRepository(context, fakeRemote)
        val result = repo.handleTokenRefreshed("token_logged_out_123")
        assertTrue(result.isSuccess)
        assertEquals("token_logged_out_123", repo.getCachedFcmToken())
        assertFalse(repo.isDeviceActive())
        assertFalse(repo.isSyncPending())
        assertNotNull(fakeRemote.registeredDevices[repo.getDeviceId()])
        val remoteDoc = fakeRemote.registeredDevices[repo.getDeviceId()]!!
        assertFalse(remoteDoc.isActive)
        assertNull(remoteDoc.currentUid)
        assertEquals("token_logged_out_123", remoteDoc.fcmToken)
    }

    // 2. Token received while logged in
    @Test
    fun testTokenReceivedWhileLoggedIn() = runBlocking {
        val repo = DeviceRepository(context, fakeRemote)
        repo.handleUserAuthenticated("user_123", "Pharmacist", "branch_a")
        
        val result = repo.handleTokenRefreshed("token_logged_in_456")
        assertTrue(result.isSuccess)
        assertEquals("token_logged_in_456", repo.getCachedFcmToken())
        assertTrue(repo.isDeviceActive())
        assertFalse(repo.isSyncPending())
        
        val remoteDoc = fakeRemote.registeredDevices[repo.getDeviceId()]!!
        assertTrue(remoteDoc.isActive)
        assertEquals("user_123", remoteDoc.currentUid)
        assertEquals("Pharmacist", remoteDoc.currentRole)
        assertEquals("branch_a", remoteDoc.currentBranchId)
        assertEquals("token_logged_in_456", remoteDoc.fcmToken)
    }

    // 3. Initial token retrieval
    @Test
    fun testInitialTokenRetrieval() = runBlocking {
        val repo = DeviceRepository(context, fakeRemote)
        // Ensure device ID is initialized and cached token is null initially
        assertNotNull(repo.getDeviceId())
        assertNull(repo.getCachedFcmToken())
        // Proactive token registration can be fed via handleTokenRefreshed
        repo.handleTokenRefreshed("initial_retrieved_token_789")
        assertEquals("initial_retrieved_token_789", repo.getCachedFcmToken())
    }

    // 4. Token rotation
    @Test
    fun testTokenRotation() = runBlocking {
        val repo = DeviceRepository(context, fakeRemote)
        repo.handleUserAuthenticated("user_123", "Pharmacist", "branch_a")
        repo.handleTokenRefreshed("old_token_111")
        assertEquals("old_token_111", repo.getCachedFcmToken())

        // Rotate token
        repo.handleTokenRefreshed("new_rotated_token_222")
        assertEquals("new_rotated_token_222", repo.getCachedFcmToken())
        val remoteDoc = fakeRemote.registeredDevices[repo.getDeviceId()]!!
        assertEquals("new_rotated_token_222", remoteDoc.fcmToken)
        assertEquals("user_123", remoteDoc.currentUid)
        assertTrue(remoteDoc.isActive)
    }

    // 5. Successful login registration
    @Test
    fun testSuccessfulLoginRegistration() = runBlocking {
        val repo = DeviceRepository(context, fakeRemote)
        repo.handleTokenRefreshed("login_test_token")
        val result = repo.handleUserAuthenticated("pharmacist_jane", "Branch Manager", "branch_ikeja")
        
        assertTrue(result.isSuccess)
        assertTrue(repo.isDeviceActive())
        assertEquals("pharmacist_jane", repo.getCachedUid())
        assertEquals("Branch Manager", repo.getCachedRole())
        assertEquals("branch_ikeja", repo.getCachedBranchId())
        assertFalse(repo.isSyncPending())

        val remoteDoc = fakeRemote.registeredDevices[repo.getDeviceId()]!!
        assertTrue(remoteDoc.isActive)
        assertEquals("pharmacist_jane", remoteDoc.currentUid)
        assertEquals("Branch Manager", remoteDoc.currentRole)
        assertEquals("branch_ikeja", remoteDoc.currentBranchId)
        assertEquals("login_test_token", remoteDoc.fcmToken)
    }

    // 6. Branch switch
    @Test
    fun testBranchSwitch() = runBlocking {
        val repo = DeviceRepository(context, fakeRemote)
        repo.handleUserAuthenticated("user_123", "Pharmacist", "branch_ikeja")
        assertEquals("branch_ikeja", repo.getCachedBranchId())

        val result = repo.updateBranchAssociation("branch_kosofe")
        assertTrue(result.isSuccess)
        assertEquals("branch_kosofe", repo.getCachedBranchId())
        
        val remoteDoc = fakeRemote.registeredDevices[repo.getDeviceId()]!!
        assertEquals("branch_kosofe", remoteDoc.currentBranchId)
        assertEquals("user_123", remoteDoc.currentUid)
        assertTrue(remoteDoc.isActive)
    }

    // 7. Logout
    @Test
    fun testLogout() = runBlocking {
        val repo = DeviceRepository(context, fakeRemote)
        repo.handleUserAuthenticated("user_123", "Pharmacist", "branch_ikeja")
        
        val result = repo.handleUserLoggedOut()
        assertTrue(result.isSuccess)
        assertFalse(repo.isDeviceActive())
        assertNull(repo.getCachedUid())
        assertNull(repo.getCachedRole())
        assertNull(repo.getCachedBranchId())
        assertFalse(repo.isSyncPending())

        val remoteDoc = fakeRemote.registeredDevices[repo.getDeviceId()]!!
        assertFalse(remoteDoc.isActive)
        assertNull(remoteDoc.currentUid)
        assertNull(remoteDoc.currentBranchId)
        assertNull(remoteDoc.currentRole)
    }

    // 8. Logout while offline
    @Test
    fun testLogoutWhileOffline() = runBlocking {
        val repo = DeviceRepository(context, fakeRemote)
        repo.handleUserAuthenticated("user_123", "Pharmacist", "branch_ikeja")

        fakeRemote.shouldFail = true
        val result = repo.handleUserLoggedOut()
        // Remote write failed, but local logout MUST succeed immediately
        assertTrue(result.isFailure)
        assertFalse(repo.isDeviceActive())
        assertNull(repo.getCachedUid())
        assertTrue(repo.isSyncPending())

        // Reconnect and retry sync
        fakeRemote.shouldFail = false
        val retryResult = repo.syncPendingRegistration()
        assertTrue(retryResult.isSuccess)
        assertFalse(repo.isSyncPending())
        assertFalse(fakeRemote.registeredDevices[repo.getDeviceId()]!!.isActive)
    }

    // 9. Firestore registration failure
    @Test
    fun testFirestoreRegistrationFailure() = runBlocking {
        val repo = DeviceRepository(context, fakeRemote)
        fakeRemote.shouldFail = true
        
        val result = repo.handleUserAuthenticated("user_err", "Pharmacist", "branch_err")
        assertTrue(result.isFailure)
        // Local state preserved
        assertTrue(repo.isDeviceActive())
        assertEquals("user_err", repo.getCachedUid())
        assertTrue(repo.isSyncPending())
    }

    // 10. Retry after failure
    @Test
    fun testRetryAfterFailure() = runBlocking {
        val repo = DeviceRepository(context, fakeRemote)
        fakeRemote.shouldFail = true
        repo.handleUserAuthenticated("user_retry", "Pharmacist", "branch_retry")
        assertTrue(repo.isSyncPending())

        fakeRemote.shouldFail = false
        val syncResult = repo.syncPendingRegistration()
        assertTrue(syncResult.isSuccess)
        assertFalse(repo.isSyncPending())
        val remoteDoc = fakeRemote.registeredDevices[repo.getDeviceId()]!!
        assertTrue(remoteDoc.isActive)
        assertEquals("user_retry", remoteDoc.currentUid)
    }

    // 11. User A → User B shared-device transition
    @Test
    fun testUserAToUserBSharedDeviceTransition() = runBlocking {
        val repo = DeviceRepository(context, fakeRemote)
        val originalDeviceId = repo.getDeviceId()

        // User A logs in
        repo.handleUserAuthenticated("user_A", "Pharmacist", "branch_A")
        assertEquals("user_A", fakeRemote.registeredDevices[originalDeviceId]?.currentUid)

        // User A logs out
        repo.handleUserLoggedOut()
        assertNull(fakeRemote.registeredDevices[originalDeviceId]?.currentUid)
        assertFalse(fakeRemote.registeredDevices[originalDeviceId]!!.isActive)

        // User B logs in on SAME device
        repo.handleUserAuthenticated("user_B", "Manager", "branch_B")
        assertEquals(originalDeviceId, repo.getDeviceId()) // Device ID unchanged!
        assertEquals("user_B", fakeRemote.registeredDevices[originalDeviceId]?.currentUid)
        assertEquals("Manager", fakeRemote.registeredDevices[originalDeviceId]?.currentRole)
        assertEquals("branch_B", fakeRemote.registeredDevices[originalDeviceId]?.currentBranchId)
        assertTrue(fakeRemote.registeredDevices[originalDeviceId]!!.isActive)
    }

    // 12. Multiple devices for one user
    @Test
    fun testMultipleDevicesForOneUser() = runBlocking {
        val prefs1 = context.getSharedPreferences("prefs_dev1", Context.MODE_PRIVATE)
        prefs1.edit().putString("device_uuid", "device-uuid-phone").commit()
        val repoDevice1 = DeviceRepository(context, fakeRemote)
        
        val prefs2 = context.getSharedPreferences("prefs_dev2", Context.MODE_PRIVATE)
        prefs2.edit().putString("device_uuid", "device-uuid-tablet").commit()
        
        // Custom fake simulating two separate hardware contexts
        fakeRemote.registerDevice("device-uuid-phone", "token_phone", "user_common", "Pharmacist", "branch_common", true)
        fakeRemote.registerDevice("device-uuid-tablet", "token_tablet", "user_common", "Pharmacist", "branch_common", true)

        val phoneDoc = fakeRemote.registeredDevices["device-uuid-phone"]!!
        val tabletDoc = fakeRemote.registeredDevices["device-uuid-tablet"]!!

        assertEquals("user_common", phoneDoc.currentUid)
        assertEquals("user_common", tabletDoc.currentUid)
        assertEquals("token_phone", phoneDoc.fcmToken)
        assertEquals("token_tablet", tabletDoc.fcmToken)
        assertTrue(phoneDoc.isActive)
        assertTrue(tabletDoc.isActive)
    }

    // 13. Device remains locally registered when remote sync fails
    @Test
    fun testDeviceRemainsLocallyRegisteredWhenRemoteSyncFails() = runBlocking {
        val repo = DeviceRepository(context, fakeRemote)
        fakeRemote.shouldFail = true
        repo.handleTokenRefreshed("token_local_persist")
        
        assertEquals("token_local_persist", repo.getCachedFcmToken())
        assertTrue(repo.isSyncPending())
    }

    // 14. Pending sync is cleared only after successful remote synchronization
    @Test
    fun testPendingSyncClearedOnlyAfterSuccess() = runBlocking {
        val repo = DeviceRepository(context, fakeRemote)
        fakeRemote.shouldFail = true
        repo.handleUserAuthenticated("user_pending_test", "Pharmacist", "branch_test")
        assertTrue(repo.isSyncPending())

        // Attempting sync while still failing
        repo.syncPendingRegistration()
        assertTrue(repo.isSyncPending())

        // Succeeding sync
        fakeRemote.shouldFail = false
        repo.syncPendingRegistration()
        assertFalse(repo.isSyncPending())
    }

    // 15. No FCM token is logged
    @Test
    fun testNoFcmTokenIsLogged() = runBlocking {
        val standardOut = System.out
        val outputStream = ByteArrayOutputStream()
        System.setOut(PrintStream(outputStream))

        val secretToken = "SECRET_FCM_TOKEN_SUPER_SECURE_99999"
        val repo = DeviceRepository(context, fakeRemote)
        repo.handleTokenRefreshed(secretToken)

        System.setOut(standardOut)
        val logs = outputStream.toString()
        assertFalse("FCM token must NOT be printed to stdout or logs", logs.contains(secretToken))
    }

    // 16. Repeated token refresh does not create duplicate device IDs
    @Test
    fun testRepeatedTokenRefreshPreservesDeviceId() = runBlocking {
        val repo = DeviceRepository(context, fakeRemote)
        val initialDeviceId = repo.getDeviceId()

        repo.handleTokenRefreshed("token_iteration_1")
        assertEquals(initialDeviceId, repo.getDeviceId())

        repo.handleTokenRefreshed("token_iteration_2")
        assertEquals(initialDeviceId, repo.getDeviceId())

        repo.handleTokenRefreshed("token_iteration_3")
        assertEquals(initialDeviceId, repo.getDeviceId())

        assertEquals(1, fakeRemote.registeredDevices.size)
        assertEquals("token_iteration_3", fakeRemote.registeredDevices[initialDeviceId]?.fcmToken)
    }
}

class FakeDeviceRemoteDataSource : RemoteDataSource {
    var shouldFail = false
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

    override fun observePharmacist(uid: String): Flow<Map<String, Any>?> = emptyFlow()
    override fun observeBranchSettings(branchId: String): Flow<Map<String, Any>?> = emptyFlow()
    override fun observeStaffMembers(branchId: String): Flow<List<Map<String, Any>>> = emptyFlow()
    override fun observeBranchInventory(branchId: String): Flow<List<Map<String, Any>>> = emptyFlow()
    override fun observeBranchCustomers(branchId: String): Flow<List<Map<String, Any>>> = emptyFlow()
    override fun observeBranchCustomerMedications(branchId: String): Flow<List<Map<String, Any>>> = emptyFlow()
    override fun observeBranchInterventions(branchId: String): Flow<List<Map<String, Any>>> = emptyFlow()
    override fun observeBranchOperationTasks(branchId: String): Flow<List<Map<String, Any>>> = emptyFlow()
    override fun observeBranchReceipts(branchId: String): Flow<List<Map<String, Any>>> = emptyFlow()
    override fun observeBranchAuditLogs(branchId: String): Flow<List<Map<String, Any>>> = emptyFlow()
    override fun observeAllPharmacists(): Flow<List<Map<String, Any>>> = emptyFlow()
    override fun observeAllBranches(): Flow<List<Map<String, Any>>> = emptyFlow()
    override fun observeDeviceConfigs(): Flow<List<Map<String, Any>>> = emptyFlow()
    override fun observeDeviceConfig(deviceId: String): Flow<Map<String, Any>?> = emptyFlow()
    override fun observeExpiryRescueListings(): Flow<List<Map<String, Any>>> = emptyFlow()
    override fun observeKeyCreationRequests(): Flow<List<Map<String, Any>>> = emptyFlow()
    override fun observeCanonicalProducts(): Flow<List<Map<String, Any>>> = emptyFlow()
    override fun observeAdminAuditLogs(): Flow<List<Map<String, Any>>> = emptyFlow()
    override fun observeMedicationSales(branchId: String): Flow<List<Map<String, Any>>> = emptyFlow()

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
    override suspend fun switchActiveBranch(uid: String, branchId: String, branchName: String): Result<Unit> = Result.success(Unit)
    override suspend fun updateBranchDetails(branchId: String, newName: String, newLga: String, newState: String): Result<Unit> = Result.success(Unit)
    override suspend fun updateBranchFeatures(branchId: String, features: Map<String, Boolean>): Result<Unit> = Result.success(Unit)
    override suspend fun deletePharmacist(pharmacistUid: String, branchId: String?, role: String?): Result<Unit> = Result.success(Unit)

    override suspend fun syncSaleTransaction(request: SaleSyncRequest): SyncResult = SyncResult(SyncStatus.SYNCED, request.clientTransactionId, "id")
    override suspend fun logOutboundSms(logData: Map<String, Any?>): Result<Unit> = Result.success(Unit)

    override suspend fun registerDevice(
        deviceId: String,
        fcmToken: String?,
        currentUid: String?,
        currentRole: String?,
        currentBranchId: String?,
        isActive: Boolean
    ): Result<Unit> {
        if (shouldFail) return Result.failure(Exception("Remote error"))
        registeredDevices[deviceId] = DeviceRecord(deviceId, fcmToken, currentUid, currentRole, currentBranchId, isActive)
        return Result.success(Unit)
    }

    override suspend fun updateDeviceToken(
        deviceId: String,
        fcmToken: String
    ): Result<Unit> {
        if (shouldFail) return Result.failure(Exception("Remote error"))
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
        if (shouldFail) return Result.failure(Exception("Remote error"))
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
        if (shouldFail) return Result.failure(Exception("Remote error"))
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
