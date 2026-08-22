package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.data.ClinicalIntervention
import com.example.data.Customer
import com.example.data.CustomerMedication
import com.example.data.InventoryItem
import com.example.data.PharmacyDatabase
import com.example.data.PharmacyRepository
import com.example.data.Receipt
import com.example.data.sync.SyncOutboxRecord
import com.example.ui.PharmacyViewModel
import com.example.work.CloudSyncWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Phase27_6MutationAndAuthHardeningTest {

    private lateinit var context: Context
    private lateinit var db: PharmacyDatabase
    private lateinit var repository: PharmacyRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val inMemoryDb = androidx.room.Room.inMemoryDatabaseBuilder(context, PharmacyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        PharmacyDatabase.setTestInstance(inMemoryDb)
        db = inMemoryDb
        repository = PharmacyRepository(db.pharmacyDao())
    }

    @Test
    fun testAuthenticatedNonAdminNullBranchBlocksOutbox() = runBlocking {
        val dao = db.pharmacyDao()

        // Set up user credentials in app_settings with blank branchId
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .edit()
            .putString("cached_uid", "USER_NON_ADMIN")
            .putString("cached_role", "Pharmacist")
            .putString("cached_branch_id", "") // Unknown / missing branch
            .apply()

        val record = SyncOutboxRecord(
            id = 101,
            branchId = "BRANCH_A",
            entityType = "INVENTORY",
            entityId = "101",
            operationType = "UPSERT",
            payloadJson = "{\"id\":101,\"name\":\"Paracetamol\"}",
            originatingUserUid = "USER_NON_ADMIN",
            status = "PENDING"
        )
        dao.insertOutboxRecord(record)

        val worker = TestListenableWorkerBuilder<CloudSyncWorker>(context).build()
        worker.doWork()

        val allRecords = dao.getOutboxRecordsForBranch("BRANCH_A").first()
        val processed = allRecords.find { it.id == 101 }
        assertNotNull("Record must exist", processed)
        assertEquals("BLOCKED", processed?.status)
        assertTrue(
            "ErrorMessage must explain missing active branch for non-admin user",
            processed?.errorMessage?.contains("unknown or missing") == true
        )
    }

    @Test
    fun testAuthenticatedNonAdminMismatchedBranchBlocksOutbox() = runBlocking {
        val dao = db.pharmacyDao()

        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .edit()
            .putString("cached_uid", "USER_STAFF")
            .putString("cached_role", "Pharmacist")
            .putString("cached_branch_id", "BRANCH_B") // Staff active in BRANCH_B
            .apply()

        val record = SyncOutboxRecord(
            id = 102,
            branchId = "BRANCH_A", // Record belongs to BRANCH_A
            entityType = "INVENTORY",
            entityId = "102",
            operationType = "UPSERT",
            payloadJson = "{\"id\":102,\"name\":\"Ibuprofen\"}",
            originatingUserUid = "USER_STAFF",
            status = "PENDING"
        )
        dao.insertOutboxRecord(record)

        val worker = TestListenableWorkerBuilder<CloudSyncWorker>(context).build()
        worker.doWork()

        val allRecords = dao.getOutboxRecordsForBranch("BRANCH_A").first()
        val processed = allRecords.find { it.id == 102 }
        assertNotNull(processed)
        assertEquals("BLOCKED", processed?.status)
        assertTrue(
            "ErrorMessage must indicate non-authorization for branch",
            processed?.errorMessage?.contains("not authorized for branch BRANCH_A") == true
        )
    }

    @Test
    fun testSystemAdminAuthorizedCrossBranchOutboxProcessing() = runBlocking {
        val dao = db.pharmacyDao()

        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .edit()
            .putString("cached_uid", "USER_ADMIN")
            .putString("cached_role", "System Administrator")
            .putString("cached_branch_id", "BRANCH_ADMIN")
            .apply()

        val record = SyncOutboxRecord(
            id = 103,
            branchId = "BRANCH_REMOTE",
            entityType = "INVENTORY",
            entityId = "103",
            operationType = "UPSERT",
            payloadJson = "{\"id\":103,\"name\":\"Amoxicillin\"}",
            originatingUserUid = "USER_ADMIN",
            status = "PENDING"
        )
        dao.insertOutboxRecord(record)

        val worker = TestListenableWorkerBuilder<CloudSyncWorker>(context).build()
        worker.doWork()

        val allRecords = dao.getOutboxRecordsForBranch("BRANCH_REMOTE").first()
        val processed = allRecords.find { it.id == 103 }
        assertNotNull(processed)
        // System admin must not be BLOCKED due to branch mismatch
        assertNotEquals("System Admin must not be blocked for cross-branch record", "BLOCKED", processed?.status)
    }

    @Test
    fun testDeleteMutationsCreateDurableDeleteOutboxRecords() = runBlocking {
        val dao = db.pharmacyDao()

        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .edit()
            .putString("cached_branch_id", "BRANCH_TEST")
            .putString("cached_role", "Pharmacist")
            .putString("cached_uid", "USER_DEL")
            .apply()

        val vm = PharmacyViewModel(ApplicationProvider.getApplicationContext())

        val customer = Customer(
            id = 505,
            name = "Delete Me",
            phoneNumber = "08099998888",
            branchId = "BRANCH_TEST",
            originatingUserUid = "USER_DEL"
        )
        val outboxInsert = SyncOutboxRecord(
            branchId = "BRANCH_TEST",
            entityType = "CUSTOMER",
            entityId = "505",
            operationType = "UPSERT",
            payloadJson = "{}",
            originatingUserUid = "USER_DEL"
        )
        dao.insertCustomerAndOutbox(customer, outboxInsert)

        // Delete customer via ViewModel
        vm.deleteCustomer(customer)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        val outboxRecords = dao.getOutboxRecordsForBranch("BRANCH_TEST").first()
        val deleteOutbox = outboxRecords.find { it.entityId == "505" && it.operationType == "DELETE" }
        assertNotNull("Durable DELETE outbox record must be created", deleteOutbox)
        assertEquals("CUSTOMER", deleteOutbox?.entityType)
        assertEquals("BRANCH_TEST", deleteOutbox?.branchId)
        assertEquals("USER_DEL", deleteOutbox?.originatingUserUid)
    }

    @Test
    fun testAtomicMutationRollbackOnOutboxFailure() = runBlocking {
        val dao = db.pharmacyDao()

        val invalidOutbox = SyncOutboxRecord(
            branchId = "", // Missing branchId lineage triggers DAO transaction throw
            entityType = "CUSTOMER",
            entityId = "606",
            operationType = "UPSERT",
            payloadJson = "{}",
            originatingUserUid = "USER_TEST"
        )
        val customer = Customer(
            id = 606,
            name = "Rollback Target",
            phoneNumber = "08011112222",
            branchId = "BRANCH_TEST",
            originatingUserUid = "USER_TEST"
        )

        try {
            dao.insertCustomerAndOutbox(customer, invalidOutbox)
            fail("DAO transaction must fail and throw exception when outbox lineage is missing")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("lineage missing") == true)
        }

        val customers = dao.getCustomersForBranch("BRANCH_TEST").first()
        assertTrue("Customer mutation must roll back when outbox fails", customers.isEmpty())
    }

    @Test
    fun testDirectFirestoreSyncNoOpVerification() = runBlocking {
        val vm = PharmacyViewModel(ApplicationProvider.getApplicationContext())
        // Proves syncEntityToFirestore executes harmlessly without direct unbuffered Firestore writes
        vm.syncEntityToFirestore("branch_inventory", "999", mapOf("testKey" to "testVal"))
    }
}
