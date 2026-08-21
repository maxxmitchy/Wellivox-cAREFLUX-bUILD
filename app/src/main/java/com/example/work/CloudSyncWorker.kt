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
            var branchId: String? = null
            if (currentUserUid != null) {
                try {
                    branchId = repository.getPharmacistBranchId(currentUserUid)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // ==========================================
            // 0. Drain Durable Outbox
            // ==========================================
            try {
                val pendingOutbox = dao.getPendingOutboxRecords()
                for (record in pendingOutbox) {
                    // Objective 7: Authorization Check for Outbox Record Branch
                    if (currentUserUid == null || (branchId != null && branchId != record.branchId)) {
                        android.util.Log.w(
                            "CloudSyncWorker",
                            "Outbox record ${record.id} branch (${record.branchId}) differs from active user branch ($branchId). Marking BLOCKED without forcing upload or modifying branchId."
                        )
                        dao.updateOutboxRecord(
                            record.copy(
                                status = "BLOCKED",
                                errorMessage = "User $currentUserUid not authorized for branch ${record.branchId}"
                            )
                        )
                        continue
                    }

                    // Process Outbox Record based on entityType
                    try {
                        var uploadResult: kotlin.Result<Unit> = kotlin.Result.failure(Exception("Unknown entityType"))
                        val payloadMap = try {
                            val jsonObject = org.json.JSONObject(record.payloadJson)
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

                        when (record.entityType) {
                            "SALE" -> {
                                val saleDocId = if (record.clientTransactionId.isNotBlank()) record.clientTransactionId else record.entityId
                                if (saleDocId.isNotBlank() && payloadMap.isNotEmpty()) {
                                    uploadResult = repository.upsertRemoteDocument("medication_sales", saleDocId, payloadMap)
                                }
                            }
                            "CUSTOMER" -> {
                                val docId = "${record.branchId}_${record.entityId}"
                                if (payloadMap.isNotEmpty()) {
                                    uploadResult = repository.upsertRemoteDocument("branch_customers", docId, payloadMap)
                                }
                            }
                            "INVENTORY" -> {
                                val docId = "${record.branchId}_${record.entityId}"
                                if (payloadMap.isNotEmpty()) {
                                    uploadResult = repository.upsertRemoteDocument("branch_inventory", docId, payloadMap)
                                }
                            }
                            "CUSTOMER_MEDICATION" -> {
                                val docId = "${record.branchId}_${record.entityId}"
                                if (payloadMap.isNotEmpty()) {
                                    uploadResult = repository.upsertRemoteDocument("branch_customer_medications", docId, payloadMap)
                                }
                            }
                            "INTERVENTION" -> {
                                val docId = "${record.branchId}_${record.entityId}"
                                if (payloadMap.isNotEmpty()) {
                                    uploadResult = repository.upsertRemoteDocument("branch_interventions", docId, payloadMap)
                                }
                            }
                            "TASK" -> {
                                val docId = "${record.branchId}_${record.entityId}"
                                if (payloadMap.isNotEmpty()) {
                                    uploadResult = repository.upsertRemoteDocument("branch_operation_tasks", docId, payloadMap)
                                }
                            }
                            "RECEIPT" -> {
                                val docId = "${record.branchId}_${record.entityId}"
                                if (payloadMap.isNotEmpty()) {
                                    uploadResult = repository.upsertRemoteDocument("branch_receipts", docId, payloadMap)
                                }
                            }
                        }

                        uploadResult.fold(
                            onSuccess = {
                                dao.updateOutboxRecord(
                                    record.copy(
                                        status = "SYNCED",
                                        lastAttemptAt = System.currentTimeMillis(),
                                        errorMessage = null
                                    )
                                )
                            },
                            onFailure = { err ->
                                dao.updateOutboxRecord(
                                    record.copy(
                                        status = "FAILED",
                                        retryCount = record.retryCount + 1,
                                        lastAttemptAt = System.currentTimeMillis(),
                                        errorMessage = err.localizedMessage
                                    )
                                )
                            }
                        )
                    } catch (ex: Exception) {
                        dao.updateOutboxRecord(
                            record.copy(
                                status = "FAILED",
                                retryCount = record.retryCount + 1,
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

                // Sync Local Customers to Cloud
                val localCustomers = dao.getAllCustomers().firstOrNull() ?: emptyList()
                for (c in localCustomers) {
                    val targetBranchId = c.branchId
                    if (targetBranchId.isBlank()) {
                        android.util.Log.w("CloudSyncWorker", "Skipping customer ${c.id} upload: missing branchId lineage.")
                        continue
                    }
                    val customerMap = hashMapOf(
                        "id" to c.id,
                        "name" to c.name,
                        "phoneNumber" to c.phoneNumber,
                        "email" to c.email,
                        "notes" to c.notes,
                        "loyaltyPoints" to c.loyaltyPoints,
                        "refillStreak" to c.refillStreak,
                        "dateAdded" to c.dateAdded,
                        "age" to c.age,
                        "gender" to c.gender,
                        "state" to c.state,
                        "lga" to c.lga,
                        "city" to c.city,
                        "syncedFromDevice" to deviceId,
                        "deviceModel" to deviceModel,
                        "lastSyncedAt" to syncTime,
                        "branchId" to targetBranchId,
                        "originatingUserUid" to c.originatingUserUid,
                        "consentPrescriptionTracking" to c.consentPrescriptionTracking,
                        "consentSmsRefills" to c.consentSmsRefills,
                        "consentCloudSync" to c.consentCloudSync,
                        "consentLastUpdated" to c.consentLastUpdated,
                        "consentChannel" to c.consentChannel
                    )
                    
                    val globalDocId = "${deviceId}_${c.id}"
                    repository.upsertRemoteDocument("customers", globalDocId, customerMap)
                    repository.upsertRemoteDocument("branch_customers", "${targetBranchId}_${c.id}", customerMap)
                }

                // ==========================================
                // 2. Bi-directional Customer Medications Sync
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

                // Sync Local Medications to Cloud
                val localMedications = dao.getAllCustomerMedications().firstOrNull() ?: emptyList()
                for (m in localMedications) {
                    val targetBranchId = m.branchId
                    if (targetBranchId.isBlank()) {
                        android.util.Log.w("CloudSyncWorker", "Skipping medication ${m.id} upload: missing branchId lineage.")
                        continue
                    }
                    val medMap = hashMapOf(
                        "id" to m.id,
                        "customerId" to m.customerId,
                        "inventoryItemId" to m.inventoryItemId,
                        "medicationName" to m.medicationName,
                        "customDosage" to m.customDosage,
                        "cost" to m.cost,
                        "cycleDays" to m.cycleDays,
                        "nextRefillDate" to m.nextRefillDate,
                        "syncedFromDevice" to deviceId,
                        "deviceModel" to deviceModel,
                        "lastSyncedAt" to syncTime,
                        "branchId" to targetBranchId,
                        "originatingUserUid" to m.originatingUserUid,
                        "globalCustomerDocId" to "${deviceId}_${m.customerId}"
                    )
                    
                    val globalMedDocId = "${deviceId}_${m.id}"
                    repository.upsertRemoteDocument("customer_medications", globalMedDocId, medMap)
                    repository.upsertRemoteDocument("branch_customer_medications", "${targetBranchId}_${m.id}", medMap)
                }

                // ==========================================
                // 3. Bi-directional Clinical Interventions Sync
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

                // Sync Local Interventions to Cloud
                val localInterventions = dao.getAllClinicalInterventions().firstOrNull() ?: emptyList()
                for (i in localInterventions) {
                    val targetBranchId = i.branchId
                    if (targetBranchId.isBlank()) {
                        android.util.Log.w("CloudSyncWorker", "Skipping intervention ${i.id} upload: missing branchId lineage.")
                        continue
                    }
                    val interventionMap = hashMapOf(
                        "id" to i.id,
                        "customerId" to i.customerId,
                        "presentation" to i.presentation,
                        "testResults" to i.testResults,
                        "recommendation" to i.recommendation,
                        "currentStatus" to i.currentStatus,
                        "followUpDay3Sent" to i.followUpDay3Sent,
                        "followUpDay7Sent" to i.followUpDay7Sent,
                        "followUpDay14Sent" to i.followUpDay14Sent,
                        "dateAdded" to i.dateAdded,
                        "syncedFromDevice" to deviceId,
                        "deviceModel" to deviceModel,
                        "lastSyncedAt" to syncTime,
                        "branchId" to targetBranchId,
                        "originatingUserUid" to i.originatingUserUid,
                        "globalCustomerDocId" to "${deviceId}_${i.customerId}"
                    )
                    
                    val globalIntDocId = "${deviceId}_${i.id}"
                    repository.upsertRemoteDocument("interventions", globalIntDocId, interventionMap)
                    repository.upsertRemoteDocument("branch_interventions", "${targetBranchId}_${i.id}", interventionMap)
                }

                // ==========================================
                // 4. Bi-directional Inventory (Products) Sync
                // ==========================================
                var remoteInvDocs: List<Map<String, Any>> = emptyList()
                try {
                    remoteInvDocs = repository.getRemoteDocumentsWhereEquals("branch_inventory", "branchId", branchId).getOrDefault(emptyList())
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

                // Sync Local Inventory Items to Cloud (if local metadata is newer; never overwrite stock unconditionally)
                val localInventory = dao.getAllInventoryItems().firstOrNull() ?: emptyList()
                val remoteDocsMap = remoteInvDocs.associateBy { (it["id"] as? Number)?.toInt() ?: 0 }

                for (item in localInventory) {
                    if (item.id == 0) continue // Skip corrupt placeholder
                    
                    val targetBranchId = item.branchId
                    if (targetBranchId.isBlank()) {
                        android.util.Log.w("CloudSyncWorker", "Skipping inventory item ${item.id} upload: missing branchId lineage.")
                        continue
                    }
                    val remoteDoc = remoteDocsMap[item.id]
                    if (remoteDoc == null) {
                        // New local item: push initial document with stock quantity
                        val invMap = hashMapOf(
                            "id" to item.id,
                            "name" to item.name,
                            "dosage" to item.dosage,
                            "stockQuantity" to item.stockQuantity,
                            "minRequiredStock" to item.minRequiredStock,
                            "category" to item.category,
                            "price" to item.price,
                            "expiryDate" to item.expiryDate,
                            "batchNumber" to item.batchNumber,
                            "supplier" to item.supplier,
                            "unitForm" to item.unitForm,
                            "lastSoldDate" to item.lastSoldDate,
                            "totalSoldQuantity" to item.totalSoldQuantity,
                            "brand" to item.brand,
                            "salesStrategy" to item.salesStrategy,
                            "lastUpdated" to item.lastUpdated,
                            "branchId" to targetBranchId,
                            "originatingUserUid" to item.originatingUserUid,
                            "imageUri" to (item.imageUri ?: "")
                        )
                        repository.upsertRemoteDocument("branch_inventory", "${targetBranchId}_${item.id}", invMap)
                    } else {
                        // Existing item: update metadata only if local is newer, preserving remote stock
                        val remoteLastUpdated = (remoteDoc["lastUpdated"] as? Number)?.toLong() ?: 0L
                        if (item.lastUpdated > remoteLastUpdated) {
                            val metadataMap = hashMapOf(
                                "id" to item.id,
                                "name" to item.name,
                                "dosage" to item.dosage,
                                "minRequiredStock" to item.minRequiredStock,
                                "category" to item.category,
                                "price" to item.price,
                                "expiryDate" to item.expiryDate,
                                "batchNumber" to item.batchNumber,
                                "supplier" to item.supplier,
                                "unitForm" to item.unitForm,
                                "brand" to item.brand,
                                "salesStrategy" to item.salesStrategy,
                                "lastUpdated" to item.lastUpdated,
                                "branchId" to targetBranchId,
                                "originatingUserUid" to item.originatingUserUid,
                                "imageUri" to (item.imageUri ?: "")
                            )
                            repository.upsertRemoteDocument("branch_inventory", "${targetBranchId}_${item.id}", metadataMap)
                        }
                    }
                }

                // ==========================================
                // 5. Bi-directional Medication Sales Sync
                // ==========================================
                try {
                    val pendingSales = dao.getAllMedicationSales().firstOrNull() ?: emptyList()
                    for (sale in pendingSales) {
                        if (sale.clientTransactionId.isBlank()) continue
                        val saleDoc = repository.getRemoteDocument("medication_sales", sale.clientTransactionId).getOrNull()
                        if (saleDoc == null) {
                            val targetBranchId = sale.branchId
                            if (targetBranchId.isBlank()) {
                                android.util.Log.w("CloudSyncWorker", "Skipping sale ${sale.clientTransactionId} upload: missing branchId lineage.")
                                continue
                            }
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
                                "clientTransactionId" to sale.clientTransactionId,
                                "branchId" to targetBranchId,
                                "originatingUserUid" to sale.originatingUserUid
                            )
                            repository.upsertRemoteDocument("medication_sales", sale.clientTransactionId, saleMap)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // ==========================================
                // 6. Bi-directional Operation Task Sync
                // ==========================================
                try {
                    val pendingTasks = dao.getAllOperationTasks().firstOrNull() ?: emptyList()
                    for (task in pendingTasks) {
                        val targetBranchId = task.branchId
                        if (targetBranchId.isBlank()) {
                            android.util.Log.w("CloudSyncWorker", "Skipping task ${task.id} upload: missing branchId lineage.")
                            continue
                        }
                        val docId = "${targetBranchId}_${task.id}"
                        val taskDoc = repository.getRemoteDocument("branch_operation_tasks", docId).getOrNull()
                        if (taskDoc == null) {
                            val taskMap = mapOf(
                                "id" to task.id,
                                "title" to task.title,
                                "description" to task.description,
                                "urgency" to task.urgency,
                                "category" to task.category,
                                "isCompleted" to task.isCompleted,
                                "createdAt" to task.createdAt,
                                "branchId" to targetBranchId,
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
                            repository.upsertRemoteDocument("branch_operation_tasks", docId, taskMap)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // ==========================================
                // 7. Bi-directional Receipt Sync
                // ==========================================
                try {
                    val pendingReceipts = dao.getAllReceipts().firstOrNull() ?: emptyList()
                    for (receipt in pendingReceipts) {
                        val targetBranchId = receipt.branchId
                        if (targetBranchId.isBlank()) {
                            android.util.Log.w("CloudSyncWorker", "Skipping receipt ${receipt.id} upload: missing branchId lineage.")
                            continue
                        }
                        val docId = "${targetBranchId}_${receipt.id}"
                        val receiptDoc = repository.getRemoteDocument("branch_receipts", docId).getOrNull()
                        if (receiptDoc == null) {
                            val receiptMap = mapOf(
                                "id" to receipt.id,
                                "timestamp" to receipt.timestamp,
                                "customerName" to receipt.customerName,
                                "totalAmount" to receipt.totalAmount,
                                "imageFileName" to receipt.imageFileName,
                                "isInvoice" to receipt.isInvoice,
                                "paymentStatus" to receipt.paymentStatus,
                                "orderId" to receipt.orderId,
                                "branchId" to targetBranchId,
                                "originatingUserUid" to receipt.originatingUserUid
                            )
                            repository.upsertRemoteDocument("branch_receipts", docId, receiptMap)
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
