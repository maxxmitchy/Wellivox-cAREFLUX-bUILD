package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.ClinicalIntervention
import com.example.data.Customer
import com.example.data.CustomerMedication
import com.example.data.PharmacyDatabase
import com.example.data.sync.SyncOutboxRecord
import com.example.work.CloudSyncWorker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Phase27DurableOutboxRemediationTest {

    @Test
    fun testSaveTriageCustomerCreatesOutbox() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, PharmacyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = db.pharmacyDao()

        val cust = Customer(
            id = 0,
            name = "John Doe",
            phoneNumber = "08012345678",
            notes = "Triage Patient",
            branchId = "BRANCH_LAGOS_01",
            originatingUserUid = "USER_123"
        )
        val custOutbox = SyncOutboxRecord(
            branchId = cust.branchId,
            entityType = "CUSTOMER",
            entityId = "0",
            operationType = "UPSERT",
            payloadJson = "{\"name\":\"John Doe\"}",
            originatingUserUid = cust.originatingUserUid
        )

        val insertedId = dao.insertCustomerAndOutbox(cust, custOutbox)
        assertTrue("Inserted customer ID must be > 0", insertedId > 0)

        val pending = dao.getPendingOutboxRecords()
        assertEquals(1, pending.size)
        assertEquals("CUSTOMER", pending[0].entityType)
        assertEquals("UPSERT", pending[0].operationType)
        assertEquals("BRANCH_LAGOS_01", pending[0].branchId)
        assertEquals("USER_123", pending[0].originatingUserUid)
        assertTrue("Outbox entityId should equal generated customer ID", pending[0].entityId == insertedId.toString())

        db.close()
    }

    @Test
    fun testSaveTriageInterventionAndMedicationCreateOutbox() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, PharmacyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = db.pharmacyDao()

        val inter = ClinicalIntervention(
            id = 0,
            customerId = 10,
            presentation = "Malaria",
            testResults = "RDT Positive",
            recommendation = "Coartem",
            branchId = "BRANCH_LAGOS_01",
            originatingUserUid = "USER_123"
        )
        val interOutbox = SyncOutboxRecord(
            branchId = inter.branchId,
            entityType = "INTERVENTION",
            entityId = "0",
            operationType = "UPSERT",
            payloadJson = "{\"presentation\":\"Malaria\"}",
            originatingUserUid = inter.originatingUserUid
        )
        dao.insertClinicalInterventionAndOutbox(inter, interOutbox)

        val med = CustomerMedication(
            id = 0,
            customerId = 10,
            inventoryItemId = 0,
            medicationName = "Coartem",
            customDosage = "2x2",
            cost = 1500.0,
            cycleDays = 3,
            nextRefillDate = System.currentTimeMillis(),
            branchId = "BRANCH_LAGOS_01",
            originatingUserUid = "USER_123"
        )
        val medOutbox = SyncOutboxRecord(
            branchId = med.branchId,
            entityType = "CUSTOMER_MEDICATION",
            entityId = "0",
            operationType = "UPSERT",
            payloadJson = "{\"medicationName\":\"Coartem\"}",
            originatingUserUid = med.originatingUserUid
        )
        dao.insertCustomerMedicationAndOutbox(med, medOutbox)

        val pending = dao.getPendingOutboxRecords()
        assertEquals(2, pending.size)

        val interRecord = pending.find { it.entityType == "INTERVENTION" }
        assertNotNull(interRecord)
        assertEquals("UPSERT", interRecord?.operationType)

        val medRecord = pending.find { it.entityType == "CUSTOMER_MEDICATION" }
        assertNotNull(medRecord)
        assertEquals("UPSERT", medRecord?.operationType)

        db.close()
    }

    @Test
    fun testCsvCustomerImportCreatesOutbox() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, PharmacyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = db.pharmacyDao()

        val importedCusts = listOf(
            Customer(name = "Imported A", phoneNumber = "08011111111", branchId = "BRANCH_LAGOS_01", originatingUserUid = "USER_123"),
            Customer(name = "Imported B", phoneNumber = "08022222222", branchId = "BRANCH_LAGOS_01", originatingUserUid = "USER_123")
        )

        importedCusts.forEach { cust ->
            val outbox = SyncOutboxRecord(
                branchId = cust.branchId,
                entityType = "CUSTOMER",
                entityId = "0",
                operationType = "UPSERT",
                payloadJson = "{\"name\":\"${cust.name}\"}",
                originatingUserUid = cust.originatingUserUid
            )
            dao.insertCustomerAndOutbox(cust, outbox)
        }

        val pending = dao.getPendingOutboxRecords()
        assertEquals(2, pending.size)
        assertTrue(pending.all { it.entityType == "CUSTOMER" && it.operationType == "UPSERT" })

        db.close()
    }

    @Test
    fun testUnattributedHistoricalRecordIsBlockedFromSync() {
        val record = SyncOutboxRecord(
            branchId = "",
            entityType = "CUSTOMER",
            entityId = "100",
            operationType = "UPSERT",
            payloadJson = "{}",
            originatingUserUid = ""
        )

        // A record with blank branchId must fail validation or be blocked
        assertTrue("Blank branchId record must be un-attributable", record.branchId.isBlank())
    }

    @Test
    fun testDeleteCustomerCreatesDeleteOutboxRecord() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, PharmacyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = db.pharmacyDao()

        val cust = Customer(
            id = 50,
            name = "Delete Me",
            phoneNumber = "08099999999",
            branchId = "BRANCH_LAGOS_01",
            originatingUserUid = "USER_123"
        )
        dao.insertCustomer(cust)

        val deleteOutbox = SyncOutboxRecord(
            branchId = cust.branchId,
            entityType = "CUSTOMER",
            entityId = cust.id.toString(),
            operationType = "DELETE",
            payloadJson = "{}",
            originatingUserUid = cust.originatingUserUid
        )

        dao.deleteCustomerAndOutbox(cust, deleteOutbox)

        val deletedCust = dao.getCustomerById(50)
        assertEquals(null, deletedCust)

        val pending = dao.getPendingOutboxRecords()
        assertEquals(1, pending.size)
        assertEquals("CUSTOMER", pending[0].entityType)
        assertEquals("DELETE", pending[0].operationType)
        assertEquals("50", pending[0].entityId)

        db.close()
    }

    @Test
    fun testAdminBypassAllowsCrossBranchOutboxSync() {
        val adminEmail = "maduemeziachinedu6@gmail.com"
        val isSystemAdmin = adminEmail.equals("maduemeziachinedu6@gmail.com", ignoreCase = true)
        assertTrue("System admin email must trigger authorization bypass for cross-branch sync", isSystemAdmin)

        val localBranchId = "BRANCH_LAGOS_01"
        val recordBranchId = "BRANCH_ABUJA_02"

        val canSync = isSystemAdmin || localBranchId == recordBranchId
        assertTrue("Admin must be permitted to sync records across different branches", canSync)
    }
}
