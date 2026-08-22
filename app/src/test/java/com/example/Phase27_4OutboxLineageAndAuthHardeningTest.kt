package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.data.Customer
import com.example.data.InventoryItem
import com.example.data.PharmacyDatabase
import com.example.data.sync.SyncOutboxRecord
import com.example.work.CloudSyncWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Phase27_4OutboxLineageAndAuthHardeningTest {

    private lateinit var context: Context
    private lateinit var db: PharmacyDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = PharmacyDatabase.getDatabase(context)
    }

    @Test
    fun testDaoTransactionRejectsOutboxMissingBranchIdLineage() = runBlocking {
        val dao = db.pharmacyDao()
        val customer = Customer(
            id = 101,
            name = "Test Patient",
            phoneNumber = "08012345678",
            branchId = "BRANCH_A",
            originatingUserUid = "USER_1"
        )
        val invalidOutbox = SyncOutboxRecord(
            branchId = "", // BLANK BRANCH ID
            entityType = "CUSTOMER",
            entityId = "101",
            operationType = "UPSERT",
            payloadJson = "{\"id\":101,\"name\":\"Test Patient\"}",
            originatingUserUid = "USER_1"
        )

        try {
            dao.insertCustomerAndOutbox(customer, invalidOutbox)
            fail("DAO transaction must fail and throw IllegalArgumentException if outbox record is missing branchId lineage")
        } catch (e: IllegalArgumentException) {
            assertTrue("Exception message must reference missing lineage", e.message?.contains("lineage missing") == true)
        }

        // Verify rollback: customer must NOT be inserted into Room
        val customers = dao.getCustomersForBranch("BRANCH_A").first()
        assertTrue("Database operation must roll back when outbox lineage is invalid", customers.isEmpty())
    }

    @Test
    fun testDaoTransactionRejectsOutboxMissingOriginatingUserUidLineage() = runBlocking {
        val dao = db.pharmacyDao()
        val item = InventoryItem(
            id = 202,
            name = "Amoxicillin 500mg",
            dosage = "500mg",
            stockQuantity = 50,
            minRequiredStock = 10,
            category = "Antibiotics",
            price = 1200.0,
            expiryDate = System.currentTimeMillis() + 864000000L,
            batchNumber = "AMX-001",
            supplier = "PharmaDist",
            branchId = "BRANCH_A",
            originatingUserUid = "USER_1"
        )
        val invalidOutbox = SyncOutboxRecord(
            branchId = "BRANCH_A",
            entityType = "INVENTORY",
            entityId = "202",
            operationType = "UPSERT",
            payloadJson = "{\"id\":202,\"name\":\"Amoxicillin 500mg\"}",
            originatingUserUid = "" // BLANK USER UID
        )

        try {
            dao.insertInventoryItemAndOutbox(item, invalidOutbox)
            fail("DAO transaction must fail and throw IllegalArgumentException if outbox record is missing originatingUserUid lineage")
        } catch (e: IllegalArgumentException) {
            assertTrue("Exception message must reference missing lineage", e.message?.contains("lineage missing") == true)
        }

        // Verify rollback
        val items = dao.getInventoryForBranch("BRANCH_A").first()
        assertTrue("Inventory insertion must roll back when outbox lineage is invalid", items.isEmpty())
    }

    @Test
    fun testWorkerBlocksOutboxRecordWithMissingBranchIdLineage() = runBlocking {
        val dao = db.pharmacyDao()
        
        // Outbox with missing branchId
        val recordNoBranch = SyncOutboxRecord(
            id = 1,
            branchId = "",
            entityType = "TASK",
            entityId = "10",
            operationType = "UPSERT",
            payloadJson = "{}",
            originatingUserUid = "USER_1",
            status = "PENDING"
        )
        dao.insertOutboxRecord(recordNoBranch)

        val worker = TestListenableWorkerBuilder<CloudSyncWorker>(context).build()
        worker.doWork()

        val pending = dao.getPendingOutboxRecords()
        assertTrue("Pending outbox list must be empty after worker processes invalid lineage record", pending.isEmpty())

        val allRecords = dao.getOutboxRecordsForBranch("").first()
        val blocked = allRecords.find { it.id == 1 }
        assertNotNull("Record 1 must exist in DB", blocked)
        assertEquals("BLOCKED", blocked?.status)
        assertTrue("ErrorMessage must mention lineage", blocked?.errorMessage?.contains("missing branchId lineage") == true)
    }

    @Test
    fun testWorkerBlocksOutboxRecordWithMissingUserUidLineage() = runBlocking {
        val dao = db.pharmacyDao()
        
        // Outbox with missing originatingUserUid
        val recordNoUser = SyncOutboxRecord(
            id = 2,
            branchId = "BRANCH_A",
            entityType = "TASK",
            entityId = "11",
            operationType = "UPSERT",
            payloadJson = "{}",
            originatingUserUid = "",
            status = "PENDING"
        )
        dao.insertOutboxRecord(recordNoUser)

        val worker = TestListenableWorkerBuilder<CloudSyncWorker>(context).build()
        worker.doWork()

        val pending = dao.getPendingOutboxRecords()
        assertTrue("Pending outbox list must be empty after worker processes invalid user lineage record", pending.isEmpty())

        val allRecords = dao.getOutboxRecordsForBranch("BRANCH_A").first()
        val blocked = allRecords.find { it.id == 2 }
        assertNotNull("Record 2 must exist in DB", blocked)
        assertEquals("BLOCKED", blocked?.status)
        assertTrue("ErrorMessage must mention lineage", blocked?.errorMessage?.contains("missing originatingUserUid lineage") == true)
    }

    @Test
    fun testUnauthenticatedWorkerExecutionLeavesOutboxPending() = runBlocking {
        val dao = db.pharmacyDao()

        val pendingOutbox = SyncOutboxRecord(
            id = 70,
            branchId = "BRANCH_MAIN",
            entityType = "CUSTOMER",
            entityId = "301",
            operationType = "UPSERT",
            payloadJson = "{\"id\":301,\"name\":\"John Doe\"}",
            originatingUserUid = "USER_1",
            status = "PENDING"
        )
        dao.insertOutboxRecord(pendingOutbox)

        // Run worker with no active Firebase authentication
        val worker = TestListenableWorkerBuilder<CloudSyncWorker>(context).build()
        worker.doWork()

        val records = dao.getOutboxRecordsForBranch("BRANCH_MAIN").first()
        val record = records.find { it.id == 70 }
        assertNotNull(record)
        assertEquals("PENDING", record?.status)
    }
}
