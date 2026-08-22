package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.InventoryItem
import com.example.data.PharmacyDatabase
import com.example.ui.PharmacyViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Phase27_5ArchitectureVerificationTest {

    private lateinit var context: Context
    private lateinit var db: PharmacyDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val inMemoryDb = androidx.room.Room.inMemoryDatabaseBuilder(context, PharmacyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        PharmacyDatabase.setTestInstance(inMemoryDb)
        db = inMemoryDb
    }

    @Test
    fun testExistingInventoryItemMissingLineageFailsClosed() = runBlocking {
        val dao = db.pharmacyDao()

        // Existing item in Room with lineage
        val existing = InventoryItem(
            id = 501,
            name = "Paracetamol 500mg",
            dosage = "500mg",
            stockQuantity = 100,
            minRequiredStock = 20,
            category = "Analgesic",
            branchId = "BRANCH_ALPHA",
            originatingUserUid = "USER_ORIGIN"
        )
        dao.insertInventoryItem(existing)

        // Attempt to update item passing blank lineage
        val corruptedUpdate = existing.copy(
            stockQuantity = 80,
            branchId = "", // Missing lineage
            originatingUserUid = ""
        )

        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .edit()
            .putString("cached_branch_id", "BRANCH_ALPHA")
            .putString("cached_role", "Pharmacist")
            .putString("cached_uid", "USER_ORIGIN")
            .apply()

        val vm = PharmacyViewModel(ApplicationProvider.getApplicationContext())
        vm.insertAndSyncInventoryItem(corruptedUpdate)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        // Verify Room item quantity was NOT modified (failed closed)
        val currentItem = dao.getInventoryItemById(501)
        assertNotNull(currentItem)
        assertEquals(100, currentItem?.stockQuantity)
        assertEquals("BRANCH_ALPHA", currentItem?.branchId)
    }

    @Test
    fun testCrossBranchTenantCreationByNonAdminFailsClosed() = runBlocking {
        val dao = db.pharmacyDao()
        
        // Cache branch as BRANCH_A
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .edit()
            .putString("cached_branch_id", "BRANCH_A")
            .putString("cached_role", "Pharmacist")
            .putString("cached_uid", "USER_1")
            .apply()

        val vm = PharmacyViewModel(ApplicationProvider.getApplicationContext())

        // Non-admin attempting to create new item for BRANCH_B
        val newItemForOtherBranch = InventoryItem(
            id = 0,
            name = "Ibuprofen 400mg",
            dosage = "400mg",
            stockQuantity = 50,
            minRequiredStock = 10,
            category = "NSAID",
            branchId = "BRANCH_B", // Forged target branch
            originatingUserUid = "USER_1"
        )

        vm.insertAndSyncInventoryItem(newItemForOtherBranch)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        // Verify item was NOT created in Room
        val itemsInBranchB = dao.getInventoryForBranch("BRANCH_B").first()
        assertTrue("Creation across tenant boundary by non-admin must fail closed", itemsInBranchB.isEmpty())
    }

    @Test
    fun testAtomicRoomMutationAndOutboxCreationForInventory() = runBlocking {
        val dao = db.pharmacyDao()

        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .edit()
            .putString("cached_branch_id", "BRANCH_A")
            .putString("cached_role", "Pharmacist")
            .putString("cached_uid", "USER_100")
            .apply()

        val vm = PharmacyViewModel(ApplicationProvider.getApplicationContext())

        val newItem = InventoryItem(
            id = 0,
            name = "Amoxicillin 250mg",
            dosage = "250mg",
            stockQuantity = 30,
            minRequiredStock = 5,
            category = "Antibiotics",
            branchId = "BRANCH_A",
            originatingUserUid = "USER_100"
        )

        vm.insertAndSyncInventoryItem(newItem)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        // Verify Room inventory item was inserted
        val items = dao.getInventoryForBranch("BRANCH_A").first()
        assertEquals(1, items.size)
        val created = items[0]
        assertEquals("Amoxicillin 250mg", created.name)

        // Verify corresponding outbox record was atomically generated with PENDING status
        val outboxRecords = dao.getOutboxRecordsForBranch("BRANCH_A").first()
        assertEquals(1, outboxRecords.size)
        val outbox = outboxRecords[0]
        assertEquals("INVENTORY", outbox.entityType)
        assertEquals(created.id.toString(), outbox.entityId)
        assertEquals("PENDING", outbox.status)
        assertEquals("BRANCH_A", outbox.branchId)
        assertEquals("USER_100", outbox.originatingUserUid)
    }
}
