package com.example.data.remote

import com.example.data.sync.SaleSyncRequest
import com.example.data.sync.SyncStatus
import com.example.data.sync.SyncResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreRemoteDataSourceImpl(
    private val customFirestore: FirebaseFirestore? = null,
    private val customAuth: FirebaseAuth? = null
) : RemoteDataSource {

    private val firestore: FirebaseFirestore
        get() = customFirestore ?: FirebaseFirestore.getInstance()

    private val auth: FirebaseAuth
        get() = customAuth ?: FirebaseAuth.getInstance()

    override fun getCurrentUserUid(): String? = try { auth.currentUser?.uid } catch (e: Exception) { null }

    override fun getCurrentUserEmail(): String? = try { auth.currentUser?.email } catch (e: Exception) { null }

    override suspend fun getPharmacistBranchId(uid: String): String? {
        return try {
            val doc = firestore.collection("registered_pharmacists").document(uid).get().await()
            doc.getString("branchId")
        } catch (e: Exception) {
            null
        }
    }

    override fun observePharmacist(uid: String): Flow<Map<String, Any>?> = callbackFlow {
        val fs = firestore
        if (fs == null) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        val listener = try {
            fs.collection("registered_pharmacists").document(uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        android.util.Log.w("FirestoreRemote", "observePharmacist listen error: ${error.localizedMessage}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        val data = snapshot.data?.toMutableMap() ?: mutableMapOf()
                        data["id"] = snapshot.id
                        trySend(data)
                    } else {
                        trySend(null)
                    }
                }
        } catch (e: Exception) {
            android.util.Log.w("FirestoreRemote", "observePharmacist registration error: ${e.localizedMessage}")
            null
        }
        awaitClose { listener?.remove() }
    }

    override fun observeBranchSettings(branchId: String): Flow<Map<String, Any>?> = callbackFlow {
        val fs = firestore
        if (fs == null || branchId.isBlank()) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        val listener = try {
            fs.collection("branches").document(branchId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        android.util.Log.w("FirestoreRemote", "observeBranchSettings listen error: ${error.localizedMessage}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        val data = snapshot.data?.toMutableMap() ?: mutableMapOf()
                        data["id"] = snapshot.id
                        trySend(data)
                    } else {
                        trySend(null)
                    }
                }
        } catch (e: Exception) {
            android.util.Log.w("FirestoreRemote", "observeBranchSettings registration error: ${e.localizedMessage}")
            null
        }
        awaitClose { listener?.remove() }
    }

    override fun observeStaffMembers(branchId: String): Flow<List<Map<String, Any>>> = callbackFlow {
        val fs = firestore
        if (fs == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val query = if (branchId.isBlank()) {
            fs.collection("registered_pharmacists")
        } else {
            fs.collection("registered_pharmacists").whereEqualTo("branchId", branchId)
        }
        val listener = try {
            query.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.w("FirestoreRemote", "observeStaffMembers listen error: ${error.localizedMessage}")
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.map { doc ->
                    val data = doc.data?.toMutableMap() ?: mutableMapOf()
                    data["id"] = doc.id
                    data["uid"] = doc.id
                    data
                } ?: emptyList()
                trySend(list)
            }
        } catch (e: Exception) {
            android.util.Log.w("FirestoreRemote", "observeStaffMembers registration error: ${e.localizedMessage}")
            null
        }
        awaitClose { listener?.remove() }
    }

    override fun observeBranchInventory(branchId: String): Flow<List<Map<String, Any>>> = callbackFlow {
        val fs = firestore
        if (fs == null || branchId.isBlank()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val query = fs.collection("branch_inventory").whereEqualTo("branchId", branchId)
        val listener = try {
            query.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.w("FirestoreRemote", "observeBranchInventory listen error: ${error.localizedMessage}")
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.map { doc ->
                    val data = doc.data?.toMutableMap() ?: mutableMapOf()
                    data["id"] = doc.id
                    data
                } ?: emptyList()
                trySend(list)
            }
        } catch (e: Exception) {
            android.util.Log.w("FirestoreRemote", "observeBranchInventory registration error: ${e.localizedMessage}")
            null
        }
        awaitClose { listener?.remove() }
    }

    override fun observeBranchCustomers(branchId: String): Flow<List<Map<String, Any>>> = callbackFlow {
        val fs = firestore
        if (fs == null || branchId.isBlank()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val query = fs.collection("branch_customers").whereEqualTo("branchId", branchId)
        val listener = try {
            query.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.w("FirestoreRemote", "observeBranchCustomers listen error: ${error.localizedMessage}")
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.map { doc ->
                    val data = doc.data?.toMutableMap() ?: mutableMapOf()
                    data["id"] = doc.id
                    data
                } ?: emptyList()
                trySend(list)
            }
        } catch (e: Exception) {
            android.util.Log.w("FirestoreRemote", "observeBranchCustomers registration error: ${e.localizedMessage}")
            null
        }
        awaitClose { listener?.remove() }
    }

    override fun observeBranchCustomerMedications(branchId: String): Flow<List<Map<String, Any>>> = callbackFlow {
        val fs = firestore
        if (fs == null || branchId.isBlank()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val query = fs.collection("branch_customer_medications").whereEqualTo("branchId", branchId)
        val listener = try {
            query.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.w("FirestoreRemote", "observeBranchCustomerMedications listen error: ${error.localizedMessage}")
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.map { doc ->
                    val data = doc.data?.toMutableMap() ?: mutableMapOf()
                    data["id"] = doc.id
                    data
                } ?: emptyList()
                trySend(list)
            }
        } catch (e: Exception) {
            android.util.Log.w("FirestoreRemote", "observeBranchCustomerMedications registration error: ${e.localizedMessage}")
            null
        }
        awaitClose { listener?.remove() }
    }

    override fun observeBranchInterventions(branchId: String): Flow<List<Map<String, Any>>> = callbackFlow {
        val fs = firestore
        if (fs == null || branchId.isBlank()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val query = fs.collection("branch_interventions").whereEqualTo("branchId", branchId)
        val listener = try {
            query.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.w("FirestoreRemote", "observeBranchInterventions listen error: ${error.localizedMessage}")
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.map { doc ->
                    val data = doc.data?.toMutableMap() ?: mutableMapOf()
                    data["id"] = doc.id
                    data
                } ?: emptyList()
                trySend(list)
            }
        } catch (e: Exception) {
            android.util.Log.w("FirestoreRemote", "observeBranchInterventions registration error: ${e.localizedMessage}")
            null
        }
        awaitClose { listener?.remove() }
    }

    override fun observeBranchOperationTasks(branchId: String): Flow<List<Map<String, Any>>> = callbackFlow {
        val fs = firestore
        if (fs == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val query = if (branchId.isBlank()) {
            fs.collection("branch_operation_tasks")
        } else {
            fs.collection("branch_operation_tasks").whereEqualTo("branchId", branchId)
        }
        val listener = try {
            query.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.w("FirestoreRemote", "observeBranchOperationTasks listen error: ${error.localizedMessage}")
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.map { doc ->
                    val data = doc.data?.toMutableMap() ?: mutableMapOf()
                    data["id"] = doc.id
                    data
                } ?: emptyList()
                trySend(list)
            }
        } catch (e: Exception) {
            android.util.Log.w("FirestoreRemote", "observeBranchOperationTasks registration error: ${e.localizedMessage}")
            null
        }
        awaitClose { listener?.remove() }
    }

    override fun observeBranchReceipts(branchId: String): Flow<List<Map<String, Any>>> = callbackFlow {
        val fs = firestore
        if (fs == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val query = if (branchId.isBlank()) {
            fs.collection("branch_receipts")
        } else {
            fs.collection("branch_receipts").whereEqualTo("branchId", branchId)
        }
        val listener = try {
            query.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.w("FirestoreRemote", "observeBranchReceipts listen error: ${error.localizedMessage}")
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.map { doc ->
                    val data = doc.data?.toMutableMap() ?: mutableMapOf()
                    data["id"] = doc.id
                    data
                } ?: emptyList()
                trySend(list)
            }
        } catch (e: Exception) {
            android.util.Log.w("FirestoreRemote", "observeBranchReceipts registration error: ${e.localizedMessage}")
            null
        }
        awaitClose { listener?.remove() }
    }

    override fun observeBranchAuditLogs(branchId: String): Flow<List<Map<String, Any>>> = callbackFlow {
        val fs = firestore
        if (fs == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val query = if (branchId.isBlank()) {
            fs.collection("branch_audit_logs").limit(200)
        } else {
            fs.collection("branch_audit_logs").whereEqualTo("branchId", branchId).limit(100)
        }
        val listener = try {
            query.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.w("FirestoreRemote", "observeBranchAuditLogs listen error: ${error.localizedMessage}")
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.map { doc ->
                    val data = doc.data?.toMutableMap() ?: mutableMapOf()
                    data["id"] = doc.id
                    data
                } ?: emptyList()
                trySend(list)
            }
        } catch (e: Exception) {
            android.util.Log.w("FirestoreRemote", "observeBranchAuditLogs registration error: ${e.localizedMessage}")
            null
        }
        awaitClose { listener?.remove() }
    }

    override fun observeAllPharmacists(): Flow<List<Map<String, Any>>> = callbackFlow {
        val fs = firestore
        if (fs == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val listener = try {
            fs.collection("registered_pharmacists")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        android.util.Log.w("FirestoreRemote", "observeAllPharmacists listen error: ${error.localizedMessage}")
                        return@addSnapshotListener
                    }
                    val list = snapshot?.documents?.map { doc ->
                        val data = doc.data?.toMutableMap() ?: mutableMapOf()
                        data["id"] = doc.id
                        data
                    } ?: emptyList()
                    trySend(list)
                }
        } catch (e: Exception) {
            android.util.Log.w("FirestoreRemote", "observeAllPharmacists registration error: ${e.localizedMessage}")
            null
        }
        awaitClose { listener?.remove() }
    }

    override fun observeKeyCreationRequests(): Flow<List<Map<String, Any>>> = callbackFlow {
        val fs = firestore
        if (fs == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val listener = try {
            fs.collection("key_creation_requests")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        android.util.Log.w("FirestoreRemote", "observeKeyCreationRequests listen error: ${error.localizedMessage}")
                        return@addSnapshotListener
                    }
                    val list = snapshot?.documents?.map { doc ->
                        val data = doc.data?.toMutableMap() ?: mutableMapOf()
                        data["id"] = doc.id
                        data
                    } ?: emptyList()
                    trySend(list)
                }
        } catch (e: Exception) {
            android.util.Log.w("FirestoreRemote", "observeKeyCreationRequests registration error: ${e.localizedMessage}")
            null
        }
        awaitClose { listener?.remove() }
    }

    override fun observeCanonicalProducts(): Flow<List<Map<String, Any>>> = callbackFlow {
        val fs = firestore
        if (fs == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val listener = try {
            fs.collection("canonical_products")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        android.util.Log.w("FirestoreRemote", "observeCanonicalProducts listen error: ${error.localizedMessage}")
                        return@addSnapshotListener
                    }
                    val list = snapshot?.documents?.map { doc ->
                        val data = doc.data?.toMutableMap() ?: mutableMapOf()
                        data["id"] = doc.id
                        data
                    } ?: emptyList()
                    trySend(list)
                }
        } catch (e: Exception) {
            android.util.Log.w("FirestoreRemote", "observeCanonicalProducts registration error: ${e.localizedMessage}")
            null
        }
        awaitClose { listener?.remove() }
    }

    override fun observeAdminAuditLogs(): Flow<List<Map<String, Any>>> = callbackFlow {
        val fs = firestore
        if (fs == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val listener = try {
            fs.collection("admin_audit_logs")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        android.util.Log.w("FirestoreRemote", "observeAdminAuditLogs listen error: ${error.localizedMessage}")
                        return@addSnapshotListener
                    }
                    val list = snapshot?.documents?.map { doc ->
                        val data = doc.data?.toMutableMap() ?: mutableMapOf()
                        data["id"] = doc.id
                        data
                    } ?: emptyList()
                    trySend(list)
                }
        } catch (e: Exception) {
            android.util.Log.w("FirestoreRemote", "observeAdminAuditLogs registration error: ${e.localizedMessage}")
            null
        }
        awaitClose { listener?.remove() }
    }

    override fun observeMedicationSales(branchId: String): Flow<List<Map<String, Any>>> = callbackFlow {
        val fs = firestore
        if (fs == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val query = if (branchId.isBlank()) {
            fs.collection("medication_sales")
        } else {
            fs.collection("medication_sales").whereEqualTo("branchId", branchId)
        }
        val listener = try {
            query.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.w("FirestoreRemote", "observeMedicationSales listen error: ${error.localizedMessage}")
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.map { doc ->
                    val data = doc.data?.toMutableMap() ?: mutableMapOf()
                    data["id"] = doc.id
                    data
                } ?: emptyList()
                trySend(list)
            }
        } catch (e: Exception) {
            android.util.Log.w("FirestoreRemote", "observeMedicationSales registration error: ${e.localizedMessage}")
            null
        }
        awaitClose { listener?.remove() }
    }

    override fun observeAllBranches(): Flow<List<Map<String, Any>>> = callbackFlow {
        val fs = firestore
        if (fs == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val listener = try {
            fs.collection("branches")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        android.util.Log.w("FirestoreRemote", "observeAllBranches listen error: ${error.localizedMessage}")
                        return@addSnapshotListener
                    }
                    val list = snapshot?.documents?.map { doc ->
                        val data = doc.data?.toMutableMap() ?: mutableMapOf()
                        data["id"] = doc.id
                        data
                    } ?: emptyList()
                    trySend(list)
                }
        } catch (e: Exception) {
            android.util.Log.w("FirestoreRemote", "observeAllBranches registration error: ${e.localizedMessage}")
            null
        }
        awaitClose { listener?.remove() }
    }

    override fun observeDeviceConfigs(): Flow<List<Map<String, Any>>> = callbackFlow {
        val fs = firestore
        if (fs == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val listener = try {
            fs.collection("device_configs")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        android.util.Log.w("FirestoreRemote", "observeDeviceConfigs listen error: ${error.localizedMessage}")
                        return@addSnapshotListener
                    }
                    val list = snapshot?.documents?.map { doc ->
                        val data = doc.data?.toMutableMap() ?: mutableMapOf()
                        data["id"] = doc.id
                        data
                    } ?: emptyList()
                    trySend(list)
                }
        } catch (e: Exception) {
            android.util.Log.w("FirestoreRemote", "observeDeviceConfigs registration error: ${e.localizedMessage}")
            null
        }
        awaitClose { listener?.remove() }
    }

    override fun observeDeviceConfig(deviceId: String): Flow<Map<String, Any>?> = callbackFlow {
        val fs = firestore
        if (fs == null || deviceId.isBlank()) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        val listener = try {
            fs.collection("device_configs").document(deviceId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        android.util.Log.w("FirestoreRemote", "observeDeviceConfig listen error: ${error.localizedMessage}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        val data = snapshot.data?.toMutableMap() ?: mutableMapOf()
                        data["id"] = snapshot.id
                        trySend(data)
                    } else {
                        trySend(null)
                    }
                }
        } catch (e: Exception) {
            android.util.Log.w("FirestoreRemote", "observeDeviceConfig registration error: ${e.localizedMessage}")
            null
        }
        awaitClose { listener?.remove() }
    }

    override fun observeExpiryRescueListings(): Flow<List<Map<String, Any>>> = callbackFlow {
        val fs = firestore
        if (fs == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val listener = try {
            fs.collection("expiry_rescue_listings")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        android.util.Log.w("FirestoreRemote", "observeExpiryRescueListings listen error: ${error.localizedMessage}")
                        return@addSnapshotListener
                    }
                    val list = snapshot?.documents?.map { doc ->
                        val data = doc.data?.toMutableMap() ?: mutableMapOf()
                        data["id"] = doc.id
                        data["firestoreId"] = doc.id
                        data
                    } ?: emptyList()
                    trySend(list)
                }
        } catch (e: Exception) {
            android.util.Log.w("FirestoreRemote", "observeExpiryRescueListings registration error: ${e.localizedMessage}")
            null
        }
        awaitClose { listener?.remove() }
    }

    override suspend fun claimRescueListing(listingId: String, deviceId: String, deviceModel: String): Result<Boolean> {
        return try {
            val docRef = firestore.collection("expiry_rescue_listings").document(listingId)
            val success = firestore.runTransaction { transaction ->
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
            Result.success(success)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateStaffCredentials(staffUid: String, staffEmail: String?, newRole: String, isApproved: Boolean): Result<Unit> {
        return try {
            val updates = mapOf(
                "role" to newRole,
                "isApproved" to isApproved
            )
            if (staffUid.isNotBlank()) {
                firestore.collection("registered_pharmacists").document(staffUid)
                    .set(updates, com.google.firebase.firestore.SetOptions.merge()).await()
                val snap = firestore.collection("registered_pharmacists").whereEqualTo("uid", staffUid).get().await()
                for (doc in snap.documents) {
                    doc.reference.set(updates, com.google.firebase.firestore.SetOptions.merge()).await()
                }
            }
            if (!staffEmail.isNullOrBlank()) {
                val snap = firestore.collection("registered_pharmacists").whereEqualTo("email", staffEmail).get().await()
                for (doc in snap.documents) {
                    doc.reference.set(updates, com.google.firebase.firestore.SetOptions.merge()).await()
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun joinBranch(
        uid: String,
        email: String,
        displayName: String,
        phoneNumber: String,
        branchCode: String,
        deviceId: String,
        deviceModel: String
    ): Result<Pair<String, String>> {
        return try {
            val branchDoc = firestore.collection("branches").document(branchCode).get().await()
            if (!branchDoc.exists()) {
                return Result.failure(IllegalArgumentException("Branch with Code '$branchCode' does not exist."))
            }
            val branchName = branchDoc.getString("name") ?: "Careflux Pharmacy"
            val pharmacistDoc = firestore.collection("registered_pharmacists").document(uid).get().await()
            val existingData = if (pharmacistDoc.exists()) pharmacistDoc.data ?: mapOf() else mapOf()

            val updateMap = hashMapOf<String, Any>()
            updateMap.putAll(existingData)
            updateMap["uid"] = uid
            updateMap["email"] = email
            if (updateMap["displayName"] == null || (updateMap["displayName"] as? String).isNullOrBlank()) {
                updateMap["displayName"] = displayName.ifBlank { email.substringBefore("@") }
            }
            updateMap["deviceId"] = deviceId
            updateMap["deviceModel"] = deviceModel
            updateMap["branchId"] = branchCode
            updateMap["branchName"] = branchName
            val role = (updateMap["role"] as? String) ?: "Pharmacist"
            updateMap["role"] = role
            if (updateMap["isApproved"] == null) {
                updateMap["isApproved"] = true
            }
            if (updateMap["phoneNumber"] == null || (updateMap["phoneNumber"] as? String).isNullOrBlank()) {
                updateMap["phoneNumber"] = phoneNumber.ifBlank { "+2348000000000" }
            }
            if (updateMap["registeredAt"] == null) {
                updateMap["registeredAt"] = System.currentTimeMillis()
            }
            updateMap["lastLoginAt"] = System.currentTimeMillis()

            firestore.collection("registered_pharmacists").document(uid).set(updateMap).await()
            Result.success(Pair(branchName, role))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun registerBranch(
        uid: String,
        email: String,
        displayName: String,
        phoneNumber: String,
        name: String,
        lga: String,
        state: String,
        deviceId: String,
        deviceModel: String
    ): Result<String> {
        return try {
            val randomCode = "CF-" + (100000..999999).random().toString()
            val branchMap = mapOf(
                "id" to randomCode,
                "name" to name,
                "lga" to lga,
                "state" to state,
                "createdBy" to uid,
                "createdAt" to System.currentTimeMillis(),
                "aiContentEnabled" to false,
                "carefluxAiEnabled" to false,
                "clinicalEnabled" to false,
                "messagingEnabled" to false,
                "triageEnabled" to false,
                "marketplaceEnabled" to false,
                "procurementEnabled" to false
            )
            firestore.collection("branches").document(randomCode).set(branchMap).await()

            val pharmacistDoc = firestore.collection("registered_pharmacists").document(uid).get().await()
            val existingData = if (pharmacistDoc.exists()) pharmacistDoc.data ?: mapOf() else mapOf()

            val updateMap = hashMapOf<String, Any>()
            updateMap.putAll(existingData)
            updateMap["uid"] = uid
            updateMap["email"] = email
            if (updateMap["displayName"] == null || (updateMap["displayName"] as? String).isNullOrBlank()) {
                updateMap["displayName"] = displayName.ifBlank { email.substringBefore("@") }
            }
            updateMap["deviceId"] = deviceId
            updateMap["deviceModel"] = deviceModel
            updateMap["branchId"] = randomCode
            updateMap["branchName"] = name
            updateMap["role"] = "Branch Manager"
            updateMap["isApproved"] = true
            if (updateMap["phoneNumber"] == null || (updateMap["phoneNumber"] as? String).isNullOrBlank()) {
                updateMap["phoneNumber"] = phoneNumber.ifBlank { "+2348000000000" }
            }
            if (updateMap["registeredAt"] == null) {
                updateMap["registeredAt"] = System.currentTimeMillis()
            }
            updateMap["lastLoginAt"] = System.currentTimeMillis()

            firestore.collection("registered_pharmacists").document(uid).set(updateMap).await()
            Result.success(randomCode)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteBranch(branchId: String): Result<Unit> {
        return try {
            firestore.collection("branches").document(branchId).delete().await()
            val staff = firestore.collection("registered_pharmacists").whereEqualTo("branchId", branchId).get().await()
            val batch = firestore.batch()
            for (doc in staff.documents) {
                batch.update(doc.reference, mapOf("branchId" to "", "branchName" to ""))
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteDeviceNode(nodeId: String): Result<Unit> {
        return try {
            firestore.collection("device_configs").document(nodeId).delete().await()
            val batch = firestore.batch()
            val directRef = firestore.collection("registered_pharmacists").document(nodeId)
            batch.update(directRef, mapOf("deviceId" to "", "deviceModel" to ""))
            val queried = firestore.collection("registered_pharmacists").whereEqualTo("deviceId", nodeId).get().await()
            for (doc in queried.documents) {
                batch.update(doc.reference, mapOf("deviceId" to "", "deviceModel" to ""))
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun appointManager(
        branchId: String,
        branchName: String,
        pharmacistUid: String,
        pharmacistName: String,
        pharmacistEmail: String
    ): Result<Unit> {
        return try {
            val batch = firestore.batch()
            val pharmacistRef = firestore.collection("registered_pharmacists").document(pharmacistUid)
            batch.update(pharmacistRef, mapOf(
                "branchId" to branchId,
                "branchName" to branchName,
                "role" to "Branch Manager",
                "isApproved" to true
            ))
            val branchRef = firestore.collection("branches").document(branchId)
            batch.update(branchRef, mapOf(
                "managerId" to pharmacistUid,
                "managerName" to pharmacistName,
                "managerEmail" to pharmacistEmail
            ))
            batch.commit().await()

            val otherManagers = firestore.collection("registered_pharmacists")
                .whereEqualTo("branchId", branchId)
                .whereEqualTo("role", "Branch Manager")
                .get().await()

            val demoteBatch = firestore.batch()
            var count = 0
            for (doc in otherManagers.documents) {
                if (doc.id != pharmacistUid) {
                    demoteBatch.update(doc.reference, mapOf("role" to "Pharmacist"))
                    count++
                }
            }
            if (count > 0) {
                demoteBatch.commit().await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun switchActiveBranch(uid: String, branchId: String, branchName: String): Result<Unit> {
        return try {
            if (uid.isNotBlank()) {
                firestore.collection("registered_pharmacists").document(uid)
                    .update(mapOf(
                        "branchId" to branchId,
                        "branchName" to branchName,
                        "lastLoginAt" to System.currentTimeMillis()
                    )).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateBranchDetails(branchId: String, newName: String, newLga: String, newState: String): Result<Unit> {
        return try {
            val branchRef = firestore.collection("branches").document(branchId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(branchRef)
                if (!snapshot.exists()) {
                    throw NoSuchElementException("Branch not found")
                }
                transaction.update(branchRef, mapOf(
                    "name" to newName.trim(),
                    "lga" to newLga.trim(),
                    "state" to newState.trim()
                ))
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateBranchFeatures(branchId: String, features: Map<String, Boolean>): Result<Unit> {
        return try {
            val branchRef = firestore.collection("branches").document(branchId)
            val updateMap = features.mapValues { it.value as Any }
            branchRef.set(updateMap, com.google.firebase.firestore.SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deletePharmacist(pharmacistUid: String, branchId: String?, role: String?): Result<Unit> {
        return try {
            val batch = firestore.batch()
            val docRef = firestore.collection("registered_pharmacists").document(pharmacistUid)
            batch.delete(docRef)
            if (!branchId.isNullOrBlank() && role == "Branch Manager") {
                val branchRef = firestore.collection("branches").document(branchId)
                batch.update(branchRef, mapOf(
                    "managerId" to "",
                    "managerName" to "",
                    "managerEmail" to ""
                ))
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getDocument(collection: String, documentId: String): Result<Map<String, Any>?> {
        return try {
            val doc = firestore.collection(collection).document(documentId).get().await()
            if (doc.exists()) {
                val data = doc.data?.toMutableMap() ?: mutableMapOf()
                data["id"] = doc.id
                Result.success(data)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getDocumentsWhereEquals(collection: String, field: String, value: Any): Result<List<Map<String, Any>>> {
        return try {
            val qSnap = firestore.collection(collection).whereEqualTo(field, value).get().await()
            val list = qSnap.documents.map { doc ->
                val data = doc.data?.toMutableMap() ?: mutableMapOf()
                data["id"] = doc.id
                data
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAllDocuments(collection: String): Result<List<Map<String, Any>>> {
        return try {
            val qSnap = firestore.collection(collection).get().await()
            val list = qSnap.documents.map { doc ->
                val data = doc.data?.toMutableMap() ?: mutableMapOf()
                data["id"] = doc.id
                data
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun upsertDocument(collection: String, documentId: String, data: Map<String, Any?>): Result<Unit> {
        return try {
            firestore.collection(collection).document(documentId).set(data).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun addDocument(collection: String, data: Map<String, Any?>): Result<String> {
        return try {
            val ref = firestore.collection(collection).add(data).await()
            Result.success(ref.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteDocument(collection: String, documentId: String): Result<Unit> {
        return try {
            firestore.collection(collection).document(documentId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun syncSaleTransaction(request: SaleSyncRequest): SyncResult {
        return try {
            val payload = hashMapOf<String, Any?>(
                "clientTransactionId" to request.clientTransactionId,
                "receiptNumber" to request.receiptNumber,
                "customerName" to request.customerName,
                "totalAmount" to request.totalAmount,
                "itemsSummary" to request.itemsSummary,
                "timestamp" to request.timestamp,
                "branchId" to request.branchId,
                "cashierUid" to request.cashierUid,
                "lineItems" to request.lineItems.map { item ->
                    mapOf(
                        "productId" to item.productId,
                        "batchId" to item.batchId,
                        "quantity" to item.quantity,
                        "unitPrice" to item.unitPrice,
                        "unitCost" to item.unitCost
                    )
                }
            )
            val existingDoc = firestore.collection("medication_sales")
                .document(request.clientTransactionId)
                .get()
                .await()

            if (!existingDoc.exists()) {
                firestore.collection("medication_sales")
                    .document(request.clientTransactionId)
                    .set(payload, com.google.firebase.firestore.SetOptions.merge())
                    .await()
            }

            SyncResult(
                status = SyncStatus.SYNCED,
                clientTransactionId = request.clientTransactionId,
                remoteId = request.clientTransactionId
            )
        } catch (e: Exception) {
            SyncResult(
                status = SyncStatus.FAILED,
                clientTransactionId = request.clientTransactionId,
                errorMessage = e.localizedMessage ?: "Firestore sync failed"
            )
        }
    }

    override suspend fun deductInventoryStockOnlineTransaction(branchId: String, itemId: Int, quantity: Int): Result<Unit> {
        return try {
            val docId = "${branchId}_${itemId}"
            val docRef = firestore.collection("branch_inventory").document(docId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                if (snapshot.exists()) {
                    val currentStock = (snapshot.getLong("stockQuantity") ?: 0L).toInt()
                    if (currentStock < quantity) {
                        throw com.google.firebase.firestore.FirebaseFirestoreException(
                            "Insufficient remote stock: available $currentStock, requested $quantity",
                            com.google.firebase.firestore.FirebaseFirestoreException.Code.ABORTED
                        )
                    }
                    val newStock = currentStock - quantity
                    transaction.update(docRef, mapOf(
                        "stockQuantity" to newStock,
                        "lastUpdated" to System.currentTimeMillis()
                    ))
                }
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun logOutboundSms(logData: Map<String, Any?>): Result<Unit> {
        return try {
            firestore.collection("branch_outbound_logs").add(logData).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun registerDevice(
        deviceId: String,
        fcmToken: String?,
        currentUid: String?,
        currentRole: String?,
        currentBranchId: String?,
        isActive: Boolean
    ): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()
            val docRef = firestore.collection("devices").document(deviceId)
            val existing = docRef.get().await()
            val registeredAt = if (existing.exists()) {
                (existing.get("registeredAt") as? Number)?.toLong() ?: now
            } else {
                now
            }
            val data = hashMapOf<String, Any?>(
                "deviceId" to deviceId,
                "fcmToken" to fcmToken,
                "currentUid" to currentUid,
                "currentRole" to currentRole,
                "currentBranchId" to currentBranchId,
                "platform" to "android",
                "isActive" to isActive,
                "registeredAt" to registeredAt,
                "updatedAt" to now,
                "lastSeenAt" to now
            )
            docRef.set(data, com.google.firebase.firestore.SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateDeviceToken(
        deviceId: String,
        fcmToken: String
    ): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()
            val docRef = firestore.collection("devices").document(deviceId)
            val data = mapOf(
                "deviceId" to deviceId,
                "fcmToken" to fcmToken,
                "platform" to "android",
                "updatedAt" to now,
                "lastSeenAt" to now
            )
            docRef.set(data, com.google.firebase.firestore.SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateDeviceAssociation(
        deviceId: String,
        currentUid: String?,
        currentRole: String?,
        currentBranchId: String?,
        isActive: Boolean
    ): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()
            val docRef = firestore.collection("devices").document(deviceId)
            val data = hashMapOf<String, Any?>(
                "deviceId" to deviceId,
                "currentUid" to currentUid,
                "currentRole" to currentRole,
                "currentBranchId" to currentBranchId,
                "isActive" to isActive,
                "updatedAt" to now,
                "lastSeenAt" to now
            )
            docRef.set(data, com.google.firebase.firestore.SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun unregisterDevice(
        deviceId: String
    ): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()
            val docRef = firestore.collection("devices").document(deviceId)
            val data = hashMapOf<String, Any?>(
                "deviceId" to deviceId,
                "isActive" to false,
                "currentUid" to null,
                "currentBranchId" to null,
                "currentRole" to null,
                "updatedAt" to now,
                "lastSeenAt" to now
            )
            docRef.set(data, com.google.firebase.firestore.SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
