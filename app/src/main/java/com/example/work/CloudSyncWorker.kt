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
            
            // If branchId is resolved, execute bi-directional synchronization
            if (!branchId.isNullOrBlank()) {
                
                // ==========================================
                // 1. Bi-directional Customer Sync
                // ==========================================
                try {
                    val remoteCustDocs = repository.getRemoteDocumentsWhereEquals("branch_customers", "branchId", branchId).getOrDefault(emptyList())
                    for (doc in remoteCustDocs) {
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
                                consentChannel = consentChannel
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
                        "branchId" to branchId,
                        "consentPrescriptionTracking" to c.consentPrescriptionTracking,
                        "consentSmsRefills" to c.consentSmsRefills,
                        "consentCloudSync" to c.consentCloudSync,
                        "consentLastUpdated" to c.consentLastUpdated,
                        "consentChannel" to c.consentChannel
                    )
                    
                    val globalDocId = "${deviceId}_${c.id}"
                    repository.upsertRemoteDocument("customers", globalDocId, customerMap)
                    repository.upsertRemoteDocument("branch_customers", "${branchId}_${c.id}", customerMap)
                }

                // ==========================================
                // 2. Bi-directional Customer Medications Sync
                // ==========================================
                try {
                    val remoteMedDocs = repository.getRemoteDocumentsWhereEquals("branch_customer_medications", "branchId", branchId).getOrDefault(emptyList())
                    for (doc in remoteMedDocs) {
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
                                nextRefillDate = if (nextRefillDate > 0L) nextRefillDate else syncTime
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
                        "branchId" to branchId,
                        "globalCustomerDocId" to "${deviceId}_${m.customerId}"
                    )
                    
                    val globalMedDocId = "${deviceId}_${m.id}"
                    repository.upsertRemoteDocument("customer_medications", globalMedDocId, medMap)
                    repository.upsertRemoteDocument("branch_customer_medications", "${branchId}_${m.id}", medMap)
                }

                // ==========================================
                // 3. Bi-directional Clinical Interventions Sync
                // ==========================================
                try {
                    val remoteIntDocs = repository.getRemoteDocumentsWhereEquals("branch_interventions", "branchId", branchId).getOrDefault(emptyList())
                    for (doc in remoteIntDocs) {
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
                                dateAdded = if (dateAdded > 0L) dateAdded else syncTime
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
                        "branchId" to branchId,
                        "globalCustomerDocId" to "${deviceId}_${i.customerId}"
                    )
                    
                    val globalIntDocId = "${deviceId}_${i.id}"
                    repository.upsertRemoteDocument("interventions", globalIntDocId, interventionMap)
                    repository.upsertRemoteDocument("branch_interventions", "${branchId}_${i.id}", interventionMap)
                }

                // ==========================================
                // 4. Bi-directional Inventory (Products) Sync
                // ==========================================
                try {
                    val remoteInvDocs = repository.getRemoteDocumentsWhereEquals("branch_inventory", "branchId", branchId).getOrDefault(emptyList())
                    for (doc in remoteInvDocs) {
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
                                lastUpdated = lastUpdated
                            )
                            dao.insertInventoryItem(newLocalItem)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Sync Local Inventory Items to Cloud (if local is newer)
                val localInventory = dao.getAllInventoryItems().firstOrNull() ?: emptyList()
                for (item in localInventory) {
                    if (item.id == 0) continue // Skip corrupt placeholder
                    
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
                        "branchId" to branchId,
                        "imageUri" to (item.imageUri ?: "")
                    )
                    
                    repository.upsertRemoteDocument("branch_inventory", "${branchId}_${item.id}", invMap)
                }
            }
            
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
