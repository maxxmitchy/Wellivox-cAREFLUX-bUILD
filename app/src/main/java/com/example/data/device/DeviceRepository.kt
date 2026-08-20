package com.example.data.device

import android.content.Context
import android.content.SharedPreferences
import com.example.data.remote.FirestoreRemoteDataSourceImpl
import com.example.data.remote.RemoteDataSource
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

class DeviceRepository(
    private val context: Context,
    private val remoteDataSource: RemoteDataSource = FirestoreRemoteDataSourceImpl()
) {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val syncMutex = Mutex()

    companion object {
        private const val PREFS_NAME = "careflux_prefs"
        private const val KEY_DEVICE_UUID = "device_uuid"
        private const val KEY_CACHED_FCM_TOKEN = "cached_fcm_token"
        private const val KEY_DEVICE_SYNC_PENDING = "device_sync_pending"
        private const val KEY_CACHED_UID = "cached_uid"
        private const val KEY_CACHED_ROLE = "cached_role"
        private const val KEY_CACHED_BRANCH_ID = "cached_branch_id"
        private const val KEY_IS_DEVICE_ACTIVE = "is_device_active"
    }

    fun getDeviceId(): String {
        var id = prefs.getString(KEY_DEVICE_UUID, null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_UUID, id).apply()
        }
        return id
    }

    fun getCachedFcmToken(): String? = prefs.getString(KEY_CACHED_FCM_TOKEN, null)

    fun isSyncPending(): Boolean = prefs.getBoolean(KEY_DEVICE_SYNC_PENDING, false)

    private fun setSyncPending(pending: Boolean) {
        prefs.edit().putBoolean(KEY_DEVICE_SYNC_PENDING, pending).apply()
    }

    fun isDeviceActive(): Boolean = prefs.getBoolean(KEY_IS_DEVICE_ACTIVE, false)

    fun getCachedUid(): String? = prefs.getString(KEY_CACHED_UID, null)

    fun getCachedRole(): String? = prefs.getString(KEY_CACHED_ROLE, null)

    fun getCachedBranchId(): String? = prefs.getString(KEY_CACHED_BRANCH_ID, null)

    suspend fun handleTokenRefreshed(token: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Token is empty"))

        prefs.edit()
            .putString(KEY_CACHED_FCM_TOKEN, cleanToken)
            .putBoolean(KEY_DEVICE_SYNC_PENDING, true)
            .apply()

        syncPendingRegistration()
    }

    suspend fun handleUserAuthenticated(
        uid: String,
        role: String,
        branchId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(KEY_CACHED_UID, uid)
            .putString(KEY_CACHED_ROLE, role)
            .putString(KEY_CACHED_BRANCH_ID, branchId)
            .putBoolean(KEY_IS_DEVICE_ACTIVE, true)
            .putBoolean(KEY_DEVICE_SYNC_PENDING, true)
            .apply()

        syncPendingRegistration()
    }

    suspend fun updateBranchAssociation(branchId: String): Result<Unit> = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(KEY_CACHED_BRANCH_ID, branchId)
            .putBoolean(KEY_DEVICE_SYNC_PENDING, true)
            .apply()

        syncPendingRegistration()
    }

    suspend fun handleUserLoggedOut(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            FirebaseMessaging.getInstance().deleteToken()
        } catch (e: Exception) {
            // Ignore if offline
        }
        prefs.edit()
            .remove(KEY_CACHED_UID)
            .remove(KEY_CACHED_ROLE)
            .remove(KEY_CACHED_BRANCH_ID)
            .remove(KEY_CACHED_FCM_TOKEN)
            .putBoolean(KEY_IS_DEVICE_ACTIVE, false)
            .putBoolean(KEY_DEVICE_SYNC_PENDING, true)
            .apply()

        syncPendingRegistration()
    }

    suspend fun syncPendingRegistration(): Result<Unit> = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val deviceId = getDeviceId()
            val token = getCachedFcmToken()
            val isActive = isDeviceActive()
            val uid = getCachedUid()
            val role = getCachedRole()
            val branchId = getCachedBranchId()

            try {
                val result = remoteDataSource.registerDevice(
                    deviceId = deviceId,
                    fcmToken = token,
                    currentUid = uid,
                    currentRole = role,
                    currentBranchId = branchId,
                    isActive = isActive
                )
                if (result.isSuccess) {
                    setSyncPending(false)
                }
                result
            } catch (e: Exception) {
                // Keep sync_pending as true for retry
                Result.failure(e)
            }
        }
    }

    fun retrieveInitialToken(scope: CoroutineScope = CoroutineScope(Dispatchers.IO)) {
        try {
            FirebaseMessaging.getInstance().token
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val token = task.result
                        if (!token.isNullOrBlank()) {
                            scope.launch {
                                handleTokenRefreshed(token)
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            // Gracefully ignore if Play Services or FCM is unavailable
        }
    }
}
