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
            
            // 1. Sync Customers
            val customers = dao.getAllCustomers().firstOrNull() ?: emptyList()
            for (c in customers) {
                // Map the customer data to a Firestore compatible hash map
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
                    "lastSyncedAt" to syncTime
                )
                
                // Composite key prefix prevents key conflicts across devices (e.g. DeviceA #1, DeviceB #1)
                val globalDocId = "${deviceId}_${c.id}"
                firestore.collection("customers").document(globalDocId)
                    .set(customerMap).await()
            }
            
            // 2. Sync Medications / Prescriptions per customer
            val medications = dao.getAllCustomerMedications().firstOrNull() ?: emptyList()
            for (m in medications) {
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
                    "globalCustomerDocId" to "${deviceId}_${m.customerId}" // relational link
                )
                
                val globalMedDocId = "${deviceId}_${m.id}"
                firestore.collection("customer_medications").document(globalMedDocId)
                    .set(medMap).await()
            }
            
            // 3. Sync Clinical Interventions
            val interventions = dao.getAllClinicalInterventions().firstOrNull() ?: emptyList()
            for (i in interventions) {
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
                    "globalCustomerDocId" to "${deviceId}_${i.customerId}" // relational link
                )
                
                val globalIntDocId = "${deviceId}_${i.id}"
                firestore.collection("interventions").document(globalIntDocId)
                    .set(interventionMap).await()
            }
            
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            // If offline or network fails, retry later
            Result.retry()
        }
    }
}
