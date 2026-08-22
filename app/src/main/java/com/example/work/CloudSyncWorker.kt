package com.example.work

import android.content.Context
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.PharmacyDatabase
import com.example.data.PharmacyRepository
import kotlinx.coroutines.flow.firstOrNull

class CloudSyncWorker(
    appContext: Context, 
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val database = PharmacyDatabase.getDatabase(applicationContext)
            val dao = database.pharmacyDao()
            val repository = PharmacyRepository(dao)
            val deviceRepository = com.example.data.device.DeviceRepository(applicationContext)
            
            // Flush any pending device registration
            try {
                deviceRepository.syncPendingRegistration()
            } catch (e: Exception) {
                // Non-fatal
            }
            
            // Unique Device ID to partition or identify source device under a single global dataset
            val deviceId = deviceRepository.getDeviceId()
            val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
            val syncTime = System.currentTimeMillis()
            
            // Fetch current authenticated user's branchId via Repository
            val currentUserUid = repository.getCurrentUserUid()
                ?: try {
                    applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                        .getString("cached_uid", null)?.takeIf { it.isNotBlank() }
                } catch (e: Exception) {
                    null
                }

            var branchId: String? = null
            if (currentUserUid != null) {
                try {
                    branchId = repository.getPharmacistBranchId(currentUserUid)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            if (branchId.isNullOrBlank()) {
                branchId = try {
                    applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                        .getString("cached_branch_id", null)?.takeIf { it.isNotBlank() }
                } catch (e: Exception) {
                    null
                }
            }

            // ==========================================
            // 0. Drain Durable Outbox
            // ==========================================
            try {
                val stuckThreshold = System.currentTimeMillis() - (5 * 60 * 1000L)
                val pendingOutbox = dao.getPendingOutboxRecords(stuckThreshold)
                for (record in pendingOutbox) {
                    if (record.branchId.isBlank()) {
                        dao.updateOutboxRecord(
                            record.copy(
                                status = "BLOCKED",
                                errorMessage = "Outbox record missing branchId lineage"
                            )
                        )
                        continue
                    }
                    if (record.originatingUserUid.isBlank()) {
                        dao.updateOutboxRecord(
                            record.copy(
                                status = "BLOCKED",
                                errorMessage = "Outbox record missing originatingUserUid lineage"
                            )
                        )
                        continue
                    }
                    if (currentUserUid == null) {
                        // Skip without altering status until user authenticates
                        continue
                    }
                    val userRole = repository.getPharmacistRole(currentUserUid)
                        ?: try {
                            applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                                .getString("cached_role", null)?.takeIf { it.isNotBlank() }
                        } catch (e: Exception) {
                            null
                        }
                    val isSystemAdmin = userRole != null && (
                        userRole.equals("Admin", ignoreCase = true) ||
                        userRole.equals("SuperAdmin", ignoreCase = true) ||
                        userRole.equals("SystemAdmin", ignoreCase = true) ||
                        userRole.equals("System Administrator", ignoreCase = true)
                    )
                    if (!isSystemAdmin) {
                        if (branchId.isNullOrBlank()) {
                            android.util.Log.w(
                                "CloudSyncWorker",
                                "Outbox record ${record.id} cannot be processed by non-admin user ($currentUserUid) with missing branchId. Marking BLOCKED."
                            )
                            dao.updateOutboxRecord(
                                record.copy(
                                    status = "BLOCKED",
                                    errorMessage = "Non-admin user $currentUserUid authorized branch is unknown or missing"
                                )
                            )
                            continue
                        }
                        if (branchId != record.branchId) {
                            android.util.Log.w(
                                "CloudSyncWorker",
                                "Outbox record ${record.id} branch (${record.branchId}) differs from active user branch ($branchId). Marking BLOCKED."
                            )
                            dao.updateOutboxRecord(
                                record.copy(
                                    status = "BLOCKED",
                                    errorMessage = "User $currentUserUid not authorized for branch ${record.branchId}"
                                )
                            )
                            continue
                        }
                    }

                    // Mark status as IN_PROGRESS before attempting network operation
                    val inProgressRecord = record.copy(
                        status = "IN_PROGRESS",
                        lastAttemptAt = System.currentTimeMillis()
                    )
                    dao.updateOutboxRecord(inProgressRecord)

                    // Process Outbox Record based on entityType and operationType
                    try {
                        var uploadResult: kotlin.Result<Unit> = kotlin.Result.failure(Exception("Unknown entityType"))
                        val payloadMap = try {
                            val jsonObject = org.json.JSONObject(inProgressRecord.payloadJson)
                            val map = mutableMapOf<String, Any?>()
                            val keys = jsonObject.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                val value = jsonObject.get(key)
                                map[key] = if (value == org.json.JSONObject.NULL) null else value
                            }
                            map
                        } catch (ex: Exception) {
                            emptyMap<String, Any?>()
                        }

                        if (inProgressRecord.operationType == "DELETE") {
                            val targetCollection = when (inProgressRecord.entityType) {
                                "CUSTOMER" -> "branch_customers"
                                "INVENTORY" -> "branch_inventory"
                                "CUSTOMER_MEDICATION" -> "branch_customer_medications"
                                "INTERVENTION" -> "branch_interventions"
                                "TASK" -> "branch_operation_tasks"
                                "RECEIPT" -> "branch_receipts"
                                "SALE" -> "medication_sales"
                                else -> null
                            }
                            if (targetCollection != null) {
                                val docId = if (inProgressRecord.entityType == "SALE" && inProgressRecord.clientTransactionId.isNotBlank()) {
                                    inProgressRecord.clientTransactionId
                                } else {
                                    "${inProgressRecord.branchId}_${inProgressRecord.entityId}"
                                }
                                uploadResult = repository.deleteRemoteDocument(targetCollection, docId)
                            }
                        } else {
                            when (inProgressRecord.entityType) {
                                "SALE" -> {
                                    val saleDocId = if (inProgressRecord.clientTransactionId.isNotBlank()) inProgressRecord.clientTransactionId else inProgressRecord.entityId
                                    if (saleDocId.isNotBlank() && payloadMap.isNotEmpty()) {
                                        uploadResult = repository.upsertRemoteDocument("medication_sales", saleDocId, payloadMap)
                                    }
                                }
                                "CUSTOMER" -> {
                                    val docId = "${inProgressRecord.branchId}_${inProgressRecord.entityId}"
                                    if (payloadMap.isNotEmpty()) {
                                        uploadResult = repository.upsertRemoteDocument("branch_customers", docId, payloadMap)
                                    }
                                }
                                "INVENTORY" -> {
                                    val docId = "${inProgressRecord.branchId}_${inProgressRecord.entityId}"
                                    if (payloadMap.isNotEmpty()) {
                                        uploadResult = repository.upsertRemoteDocument("branch_inventory", docId, payloadMap)
                                    }
                                }
                                "CUSTOMER_MEDICATION" -> {
                                    val docId = "${inProgressRecord.branchId}_${inProgressRecord.entityId}"
                                    if (payloadMap.isNotEmpty()) {
                                        uploadResult = repository.upsertRemoteDocument("branch_customer_medications", docId, payloadMap)
                                    }
                                }
                                "INTERVENTION" -> {
                                    val docId = "${inProgressRecord.branchId}_${inProgressRecord.entityId}"
                                    if (payloadMap.isNotEmpty()) {
                                        uploadResult = repository.upsertRemoteDocument("branch_interventions", docId, payloadMap)
                                    }
                                }
                                "TASK" -> {
                                    val docId = "${inProgressRecord.branchId}_${inProgressRecord.entityId}"
                                    if (payloadMap.isNotEmpty()) {
                                        uploadResult = repository.upsertRemoteDocument("branch_operation_tasks", docId, payloadMap)
                                    }
                                }
                                "RECEIPT" -> {
                                    val docId = "${inProgressRecord.branchId}_${inProgressRecord.entityId}"
                                    if (payloadMap.isNotEmpty()) {
                                        uploadResult = repository.upsertRemoteDocument("branch_receipts", docId, payloadMap)
                                    }
                                }
                            }
                        }

                        uploadResult.fold(
                            onSuccess = {
                                dao.updateOutboxRecord(
                                    inProgressRecord.copy(
                                        status = "SYNCED",
                                        lastAttemptAt = System.currentTimeMillis(),
                                        errorMessage = null
                                    )
                                )
                            },
                            onFailure = { err ->
                                dao.updateOutboxRecord(
                                    inProgressRecord.copy(
                                        status = "FAILED",
                                        retryCount = inProgressRecord.retryCount + 1,
                                        lastAttemptAt = System.currentTimeMillis(),
                                        errorMessage = err.localizedMessage
                                    )
                                )
                            }
                        )
                    } catch (ex: Exception) {
                        dao.updateOutboxRecord(
                            inProgressRecord.copy(
                                status = "FAILED",
                                retryCount = inProgressRecord.retryCount + 1,
                                lastAttemptAt = System.currentTimeMillis(),
                                errorMessage = ex.localizedMessage
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            // If branchId is resolved, execute bi-directional synchronization
            if (!branchId.isNullOrBlank()) {
                
                // ==========================================
                // 1. Bi-directional Customer Sync
                // ==========================================
                try {
                    val remoteCustDocs = repository.getRemoteDocumentsWhereEquals("branch_customers", "branchId", branchId).getOrDefault(emptyList())
                    for (doc in remoteCustDocs) {
                        val docBranchId = doc["branchId"] as? String
                        if (docBranchId.isNullOrBlank() || docBranchId != branchId) {
                            android.util.Log.w("CloudSyncWorker", "Skipping customer doc ingestion: branchId mismatch ($docBranchId vs $branchId)")
                            continue
                        }
                        val id = (doc["id"] as? Number)?.toInt()
                            ?: (doc["id"] as? String)?.toIntOrNull()
                            ?: continue
                        val name = doc["name"] as? String ?: ""
                        val phoneNumber = doc["phoneNumber"] as? String ?: ""
                        val email = doc["email"] as? String ?: ""
                        val notes = doc["notes"] as? String ?: ""
                        val loyaltyPoints = (doc["loyaltyPoints"] as? Number)?.toInt() ?: 0
                        val refillStreak = (doc["refillStreak"] as? Number)?.toInt() ?: 0
                        val dateAdded = (doc["dateAdded"] as? Number)?.toLong() ?: syncTime
                        val age = (doc["age"] as? Number)?.toInt() ?: 30
                        val gender = doc["gender"] as? String ?: "Male"
                        val state = doc["state"] as? String ?: "Lagos"
                        val lga = doc["lga"] as? String ?: "Ikeja"
                        val city = doc["city"] as? String ?: "Ikeja"
                        val consentPrescriptionTracking = doc["consentPrescriptionTracking"] as? Boolean ?: true
                        val consentSmsRefills = doc["consentSmsRefills"] as? Boolean ?: false
                        val consentCloudSync = doc["consentCloudSync"] as? Boolean ?: false
                        val consentLastUpdated = (doc["consentLastUpdated"] as? Number)?.toLong() ?: dateAdded
                        val consentChannel = doc["consentChannel"] as? String ?: "Verbal Consent"

                        val localCust = dao.getCustomerById(id)
                        if (localCust == null || consentLastUpdated >= localCust.consentLastUpdated) {
                            val newLocalCust = com.example.data.Customer(
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
                                city = city,
                                consentPrescriptionTracking = consentPrescriptionTracking,
                                consentSmsRefills = consentSmsRefills,
                                consentCloudSync = consentCloudSync,
                                consentLastUpdated = consentLastUpdated,
                                consentChannel = consentChannel,
                                branchId = docBranchId,
                                originatingUserUid = doc["originatingUserUid"] as? String ?: ""
                            )
                            dao.insertCustomer(newLocalCust)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // ==========================================
                // 2. Bi-directional Customer Medications Sync (Remote Ingestion)
                // ==========================================
                try {
                    val remoteMedDocs = repository.getRemoteDocumentsWhereEquals("branch_customer_medications", "branchId", branchId).getOrDefault(emptyList())
                    for (doc in remoteMedDocs) {
                        val docBranchId = doc["branchId"] as? String
                        if (docBranchId.isNullOrBlank() || docBranchId != branchId) {
                            android.util.Log.w("CloudSyncWorker", "Skipping medication doc ingestion: branchId mismatch ($docBranchId vs $branchId)")
                            continue
                        }
                        val id = (doc["id"] as? Number)?.toInt() ?: continue
                        val customerId = (doc["customerId"] as? Number)?.toInt() ?: continue
                        val inventoryItemId = (doc["inventoryItemId"] as? Number)?.toInt() ?: 0
                        val medicationName = doc["medicationName"] as? String ?: ""
                        val customDosage = doc["customDosage"] as? String ?: ""
                        val cost = (doc["cost"] as? Number)?.toDouble() ?: 0.0
                        val cycleDays = (doc["cycleDays"] as? Number)?.toInt() ?: 30
                        val nextRefillDate = (doc["nextRefillDate"] as? Number)?.toLong() ?: 0L

                        val localMeds = dao.getAllCustomerMedications().firstOrNull() ?: emptyList()
                        val localExists = localMeds.any { it.id == id }
                        if (!localExists) {
                            val newLocalMed = com.example.data.CustomerMedication(
                                id = id,
                                customerId = customerId,
                                inventoryItemId = inventoryItemId,
                                medicationName = medicationName,
                                customDosage = customDosage,
                                cost = cost,
                                cycleDays = cycleDays,
                                nextRefillDate = if (nextRefillDate > 0L) nextRefillDate else syncTime,
                                branchId = docBranchId,
                                originatingUserUid = doc["originatingUserUid"] as? String ?: ""
                            )
                            dao.insertCustomerMedication(newLocalMed)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // ==========================================
                // 3. Bi-directional Clinical Interventions Sync (Remote Ingestion)
                // ==========================================
                try {
                    val remoteIntDocs = repository.getRemoteDocumentsWhereEquals("branch_interventions", "branchId", branchId).getOrDefault(emptyList())
                    for (doc in remoteIntDocs) {
                        val docBranchId = doc["branchId"] as? String
                        if (docBranchId.isNullOrBlank() || docBranchId != branchId) {
                            android.util.Log.w("CloudSyncWorker", "Skipping intervention doc ingestion: branchId mismatch ($docBranchId vs $branchId)")
                            continue
                        }
                        val id = (doc["id"] as? Number)?.toInt() ?: continue
                        val customerId = (doc["customerId"] as? Number)?.toInt() ?: continue
                        val presentation = doc["presentation"] as? String ?: ""
                        val testResults = doc["testResults"] as? String ?: ""
                        val recommendation = doc["recommendation"] as? String ?: ""
                        val currentStatus = doc["currentStatus"] as? String ?: "Pending"
                        val followUpDay3Sent = doc["followUpDay3Sent"] as? Boolean ?: false
                        val followUpDay7Sent = doc["followUpDay7Sent"] as? Boolean ?: false
                        val followUpDay14Sent = doc["followUpDay14Sent"] as? Boolean ?: false
                        val dateAdded = (doc["dateAdded"] as? Number)?.toLong() ?: 0L

                        val localInt = dao.getClinicalInterventionById(id)
                        if (localInt == null || dateAdded >= localInt.dateAdded) {
                            val newLocalInt = com.example.data.ClinicalIntervention(
                                id = id,
                                customerId = customerId,
                                presentation = presentation,
                                testResults = testResults,
                                recommendation = recommendation,
                                currentStatus = currentStatus,
                                followUpDay3Sent = followUpDay3Sent,
                                followUpDay7Sent = followUpDay7Sent,
                                followUpDay14Sent = followUpDay14Sent,
                                dateAdded = if (dateAdded > 0L) dateAdded else syncTime,
                                branchId = docBranchId,
                                originatingUserUid = doc["originatingUserUid"] as? String ?: ""
                            )
                            dao.insertClinicalIntervention(newLocalInt)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // ==========================================
                // 4. Bi-directional Inventory (Products) Sync (Remote Ingestion)
                // ==========================================
                try {
                    val remoteInvDocs = repository.getRemoteDocumentsWhereEquals("branch_inventory", "branchId", branchId).getOrDefault(emptyList())
                    for (doc in remoteInvDocs) {
                        val docBranchId = doc["branchId"] as? String
                        if (docBranchId.isNullOrBlank() || docBranchId != branchId) {
                            android.util.Log.w("CloudSyncWorker", "Skipping inventory doc ingestion: branchId mismatch ($docBranchId vs $branchId)")
                            continue
                        }
                        val id = (doc["id"] as? Number)?.toInt() ?: continue
                        if (id == 0) continue // Skip placeholder corrupt ID
                        val name = doc["name"] as? String ?: ""
                        val dosage = doc["dosage"] as? String ?: ""
                        val stockQuantity = (doc["stockQuantity"] as? Number)?.toInt() ?: 0
                        val minRequiredStock = (doc["minRequiredStock"] as? Number)?.toInt() ?: 0
                        val category = doc["category"] as? String ?: ""
                        val price = (doc["price"] as? Number)?.toDouble() ?: 0.0
                        val expiryDate = (doc["expiryDate"] as? Number)?.toLong() ?: 0L
                        val batchNumber = doc["batchNumber"] as? String ?: ""
                        val supplier = doc["supplier"] as? String ?: ""
                        val unitForm = doc["unitForm"] as? String ?: ""
                        val lastSoldDate = (doc["lastSoldDate"] as? Number)?.toLong() ?: 0L
                        val totalSoldQuantity = (doc["totalSoldQuantity"] as? Number)?.toInt() ?: 0
                        val imageUri = doc["imageUri"] as? String
                        val brand = doc["brand"] as? String ?: ""
                        val salesStrategy = doc["salesStrategy"] as? String ?: ""
                        val lastUpdated = (doc["lastUpdated"] as? Number)?.toLong() ?: 0L

                        val localItem = dao.getInventoryItemById(id)
                        if (localItem == null || lastUpdated > localItem.lastUpdated) {
                            val newLocalItem = com.example.data.InventoryItem(
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
                                branchId = docBranchId,
                                originatingUserUid = doc["originatingUserUid"] as? String ?: ""
                            )
                            dao.insertInventoryItem(newLocalItem)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // ==========================================
                // 5. Bi-directional Operation Task Sync (Remote Ingestion)
                // ==========================================
                try {
                    val remoteTaskDocs = repository.getRemoteDocumentsWhereEquals("branch_operation_tasks", "branchId", branchId).getOrDefault(emptyList())
                    for (doc in remoteTaskDocs) {
                        val docBranchId = doc["branchId"] as? String
                        if (docBranchId.isNullOrBlank() || docBranchId != branchId) continue
                        val id = (doc["id"] as? Number)?.toInt()
                            ?: (doc["id"] as? String)?.toIntOrNull()
                            ?: continue
                        val title = doc["title"] as? String ?: ""
                        val description = doc["description"] as? String ?: ""
                        val urgency = doc["urgency"] as? String ?: "Normal"
                        val category = doc["category"] as? String ?: "General"
                        val isCompleted = doc["isCompleted"] as? Boolean ?: false
                        val createdAt = (doc["createdAt"] as? Number)?.toLong() ?: syncTime

                        val localTasks = dao.getAllOperationTasks().firstOrNull() ?: emptyList()
                        val localTask = localTasks.find { it.id == id }
                        if (localTask == null) {
                            val newLocalTask = com.example.data.OperationTask(
                                id = id,
                                title = title,
                                description = description,
                                urgency = urgency,
                                category = category,
                                isCompleted = isCompleted,
                                createdAt = createdAt,
                                branchId = docBranchId,
                                originatingUserUid = doc["originatingUserUid"] as? String ?: "",
                                assignedToName = doc["assignedToName"] as? String,
                                assignedToUid = doc["assignedToUid"] as? String,
                                verifiedBy = doc["verifiedBy"] as? String,
                                verificationNotes = doc["verificationNotes"] as? String,
                                verificationChannel = doc["verificationChannel"] as? String,
                                verificationCustomerName = doc["verificationCustomerName"] as? String,
                                verifiedAt = (doc["verifiedAt"] as? Number)?.toLong(),
                                isApproved = doc["isApproved"] as? Boolean ?: false,
                                approvedBy = doc["approvedBy"] as? String,
                                approvedAt = (doc["approvedAt"] as? Number)?.toLong(),
                                approvalNotes = doc["approvalNotes"] as? String,
                                inventoryItemId = (doc["inventoryItemId"] as? Number)?.toInt(),
                                taskType = doc["taskType"] as? String,
                                dueTimestamp = (doc["dueTimestamp"] as? Number)?.toLong()
                            )
                            dao.insertOperationTask(newLocalTask)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // ==========================================
                // 6. Bi-directional Receipt Sync (Remote Ingestion)
                // ==========================================
                try {
                    val remoteReceiptDocs = repository.getRemoteDocumentsWhereEquals("branch_receipts", "branchId", branchId).getOrDefault(emptyList())
                    for (doc in remoteReceiptDocs) {
                        val docBranchId = doc["branchId"] as? String
                        if (docBranchId.isNullOrBlank() || docBranchId != branchId) continue
                        val id = (doc["id"] as? Number)?.toInt()
                            ?: (doc["id"] as? String)?.toIntOrNull()
                            ?: continue
                        val timestamp = (doc["timestamp"] as? Number)?.toLong() ?: syncTime
                        val customerName = doc["customerName"] as? String ?: ""
                        val totalAmount = (doc["totalAmount"] as? Number)?.toDouble() ?: 0.0
                        val imageFileName = doc["imageFileName"] as? String ?: ""
                        val isInvoice = doc["isInvoice"] as? Boolean ?: false
                        val paymentStatus = doc["paymentStatus"] as? String ?: "Paid"
                        val orderId = doc["orderId"] as? String ?: ""

                        val localReceipts = dao.getAllReceipts().firstOrNull() ?: emptyList()
                        val localReceipt = localReceipts.find { it.id == id }
                        if (localReceipt == null) {
                            val newLocalReceipt = com.example.data.Receipt(
                                id = id,
                                timestamp = timestamp,
                                customerName = customerName,
                                totalAmount = totalAmount,
                                imageFileName = imageFileName,
                                isInvoice = isInvoice,
                                paymentStatus = paymentStatus,
                                orderId = orderId,
                                branchId = docBranchId,
                                originatingUserUid = doc["originatingUserUid"] as? String ?: ""
                            )
                            dao.insertReceipt(newLocalReceipt)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
