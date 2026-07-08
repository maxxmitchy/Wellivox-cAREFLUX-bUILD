package com.example.work

import android.content.Context
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.PharmacyDatabase
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.tasks.await

class CloudSyncWorker(
    appContext: Context, 
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val database = PharmacyDatabase.getDatabase(applicationContext)
            val dao = database.pharmacyDao()
            
            // Unique Device ID to partition or identify source device under a single global dataset
            val sharedPrefs = applicationContext.getSharedPreferences("careflux_prefs", Context.MODE_PRIVATE)
            var deviceId = sharedPrefs.getString("device_uuid", null)
            if (deviceId == null) {
                deviceId = java.util.UUID.randomUUID().toString()
                sharedPrefs.edit().putString("device_uuid", deviceId).apply()
            }
            val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
            val syncTime = System.currentTimeMillis()
            
            // Get singleton firestore instance
            val firestore = FirebaseFirestore.getInstance()
            
            // Fetch current authenticated user's branchId
            val firebaseAuth = com.google.firebase.auth.FirebaseAuth.getInstance()
            val currentUser = firebaseAuth.currentUser
            var branchId: String? = null
            if (currentUser != null) {
                try {
                    val pharmacistDoc = firestore.collection("registered_pharmacists")
                        .document(currentUser.uid)
                        .get()
                        .await()
                    if (pharmacistDoc.exists()) {
                        branchId = pharmacistDoc.getString("branchId")
                    }
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
                    val remoteCustSnapshot = firestore.collection("branch_customers")
                        .whereEqualTo("branchId", branchId)
                        .get()
                        .await()
                    for (doc in remoteCustSnapshot.documents) {
                        val id = (doc.get("id") as? Number)?.toInt() ?: continue
                        val name = doc.getString("name") ?: ""
                        val phoneNumber = doc.getString("phoneNumber") ?: ""
                        val email = doc.getString("email") ?: ""
                        val notes = doc.getString("notes") ?: ""
                        val loyaltyPoints = (doc.get("loyaltyPoints") as? Number)?.toInt() ?: 0
                        val refillStreak = (doc.get("refillStreak") as? Number)?.toInt() ?: 0
                        val dateAdded = (doc.get("dateAdded") as? Number)?.toLong() ?: syncTime
                        val age = (doc.get("age") as? Number)?.toInt() ?: 30
                        val gender = doc.getString("gender") ?: "Male"
                        val state = doc.getString("state") ?: "Lagos"
                        val lga = doc.getString("lga") ?: "Ikeja"
                        val city = doc.getString("city") ?: "Ikeja"
                        val consentPrescriptionTracking = doc.getBoolean("consentPrescriptionTracking") ?: true
                        val consentSmsRefills = doc.getBoolean("consentSmsRefills") ?: false
                        val consentCloudSync = doc.getBoolean("consentCloudSync") ?: false
                        val consentLastUpdated = (doc.get("consentLastUpdated") as? Number)?.toLong() ?: dateAdded
                        val consentChannel = doc.getString("consentChannel") ?: "Verbal Consent"

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
                    firestore.collection("customers").document(globalDocId).set(customerMap)
                    firestore.collection("branch_customers").document("${branchId}_${c.id}").set(customerMap)
                }

                // ==========================================
                // 2. Bi-directional Customer Medications Sync
                // ==========================================
                try {
                    val remoteMedSnapshot = firestore.collection("branch_customer_medications")
                        .whereEqualTo("branchId", branchId)
                        .get()
                        .await()
                    for (doc in remoteMedSnapshot.documents) {
                        val id = (doc.get("id") as? Number)?.toInt() ?: continue
                        val customerId = (doc.get("customerId") as? Number)?.toInt() ?: continue
                        val inventoryItemId = (doc.get("inventoryItemId") as? Number)?.toInt() ?: 0
                        val medicationName = doc.getString("medicationName") ?: ""
                        val customDosage = doc.getString("customDosage") ?: ""
                        val cost = (doc.get("cost") as? Number)?.toDouble() ?: 0.0
                        val cycleDays = (doc.get("cycleDays") as? Number)?.toInt() ?: 30
                        val nextRefillDate = (doc.get("nextRefillDate") as? Number)?.toLong() ?: syncTime

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
                                nextRefillDate = nextRefillDate
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
                    firestore.collection("customer_medications").document(globalMedDocId).set(medMap)
                    firestore.collection("branch_customer_medications").document("${branchId}_${m.id}").set(medMap)
                }

                // ==========================================
                // 3. Bi-directional Clinical Interventions Sync
                // ==========================================
                try {
                    val remoteIntSnapshot = firestore.collection("branch_interventions")
                        .whereEqualTo("branchId", branchId)
                        .get()
                        .await()
                    for (doc in remoteIntSnapshot.documents) {
                        val id = (doc.get("id") as? Number)?.toInt() ?: continue
                        val customerId = (doc.get("customerId") as? Number)?.toInt() ?: continue
                        val presentation = doc.getString("presentation") ?: ""
                        val testResults = doc.getString("testResults") ?: ""
                        val recommendation = doc.getString("recommendation") ?: ""
                        val currentStatus = doc.getString("currentStatus") ?: "Pending"
                        val followUpDay3Sent = doc.getBoolean("followUpDay3Sent") ?: false
                        val followUpDay7Sent = doc.getBoolean("followUpDay7Sent") ?: false
                        val followUpDay14Sent = doc.getBoolean("followUpDay14Sent") ?: false
                        val dateAdded = (doc.get("dateAdded") as? Number)?.toLong() ?: syncTime

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
                                dateAdded = dateAdded
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
                    firestore.collection("interventions").document(globalIntDocId).set(interventionMap)
                    firestore.collection("branch_interventions").document("${branchId}_${i.id}").set(interventionMap)
                }

                // ==========================================
                // 4. Bi-directional Inventory (Products) Sync
                // ==========================================
                try {
                    val remoteInvSnapshot = firestore.collection("branch_inventory")
                        .whereEqualTo("branchId", branchId)
                        .get()
                        .await()
                    for (doc in remoteInvSnapshot.documents) {
                        val id = (doc.get("id") as? Number)?.toInt() ?: continue
                        if (id == 0) continue // Skip placeholder corrupt ID
                        val name = doc.getString("name") ?: ""
                        val dosage = doc.getString("dosage") ?: ""
                        val stockQuantity = (doc.get("stockQuantity") as? Number)?.toInt() ?: 0
                        val minRequiredStock = (doc.get("minRequiredStock") as? Number)?.toInt() ?: 0
                        val category = doc.getString("category") ?: ""
                        val price = (doc.get("price") as? Number)?.toDouble() ?: 0.0
                        val expiryDate = (doc.get("expiryDate") as? Number)?.toLong() ?: 0L
                        val batchNumber = doc.getString("batchNumber") ?: ""
                        val supplier = doc.getString("supplier") ?: ""
                        val unitForm = doc.getString("unitForm") ?: ""
                        val lastSoldDate = (doc.get("lastSoldDate") as? Number)?.toLong() ?: 0L
                        val totalSoldQuantity = (doc.get("totalSoldQuantity") as? Number)?.toInt() ?: 0
                        val imageUri = doc.getString("imageUri")
                        val brand = doc.getString("brand") ?: ""
                        val salesStrategy = doc.getString("salesStrategy") ?: ""
                        val lastUpdated = (doc.get("lastUpdated") as? Number)?.toLong() ?: syncTime

                        val localItem = dao.getInventoryItemById(id)
                        if (localItem == null || lastUpdated >= localItem.lastUpdated) {
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
                    
                    firestore.collection("branch_inventory")
                        .document("${branchId}_${item.id}")
                        .set(invMap)
                }
            }
            
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
