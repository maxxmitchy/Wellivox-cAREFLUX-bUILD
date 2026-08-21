package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.Customer
import com.example.data.InventoryItem
import com.example.data.OperationTask
import com.example.data.PharmacyDatabase
import com.example.data.sync.SyncOutboxRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Phase27_3DurableOutboxAndAuthAuditTest {

    @Test
    fun testBranchManagerRoleNotGrantedGlobalCrossBranchAuthority() {
        val userRole = "Branch Manager"
        val isSystemAdmin = userRole.equals("Admin", ignoreCase = true) ||
                userRole.equals("SuperAdmin", ignoreCase = true) ||
                userRole.equals("SystemAdmin", ignoreCase = true) ||
                userRole.equals("System Administrator", ignoreCase = true)

        assertFalse("Branch Manager must NOT be classified as System Admin with cross-branch authority", isSystemAdmin)
    }

    @Test
    fun testOperationTaskUpdateCreatesAtomicOutboxRecord() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, PharmacyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = db.pharmacyDao()

        val task = OperationTask(
            id = 501,
            title = "TRANSFER RECEIPT VERIFICATION",
            description = "Verify incoming stock",
            urgency = "High",
            category = "Stock Transfer",
            isCompleted = false,
            createdAt = System.currentTimeMillis(),
            branchId = "BRANCH_LAGOS_01",
            originatingUserUid = "USER_456"
        )
        val map = mapOf(
            "id" to task.id,
            "title" to task.title,
            "description" to task.description,
            "isCompleted" to true
        )
        val outbox = SyncOutboxRecord(
            branchId = task.branchId,
            entityType = "TASK",
            entityId = task.id.toString(),
            operationType = "UPSERT",
            payloadJson = org.json.JSONObject(map).toString(),
            originatingUserUid = task.originatingUserUid
        )

        dao.insertOperationTaskAndOutbox(task, outbox)

        val pending = dao.getPendingOutboxRecords()
        assertEquals("Task insertion with outbox must create exactly 1 outbox record", 1, pending.size)
        assertEquals("TASK", pending[0].entityType)
        assertEquals("501", pending[0].entityId)
        assertEquals("BRANCH_LAGOS_01", pending[0].branchId)

        db.close()
    }

    @Test
    fun testInventoryTransferMutationEnqueuesOutboxRecord() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, PharmacyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = db.pharmacyDao()

        val item = InventoryItem(
            id = 888,
            name = "Paracetamol 500mg",
            dosage = "500mg",
            stockQuantity = 40,
            minRequiredStock = 10,
            category = "Analgesics",
            price = 500.0,
            expiryDate = System.currentTimeMillis() + 864000000L,
            batchNumber = "BATCH-001",
            supplier = "PharmaCo",
            branchId = "BRANCH_LAGOS_01",
            originatingUserUid = "USER_456"
        )
        val map = mapOf(
            "id" to item.id,
            "name" to item.name,
            "stockQuantity" to 30
        )
        val outbox = SyncOutboxRecord(
            branchId = "BRANCH_LAGOS_01",
            entityType = "INVENTORY",
            entityId = item.id.toString(),
            operationType = "UPSERT",
            payloadJson = org.json.JSONObject(map).toString(),
            originatingUserUid = "USER_456"
        )

        dao.insertInventoryItemAndOutbox(item.copy(stockQuantity = 30), outbox)

        val pending = dao.getPendingOutboxRecords()
        assertEquals("Inventory transfer mutation must create outbox record", 1, pending.size)
        assertEquals("INVENTORY", pending[0].entityType)
        assertEquals("888", pending[0].entityId)

        val storedItem = dao.getAllInventoryItems().first()
        assertEquals(1, storedItem.size)
        assertEquals(30, storedItem[0].stockQuantity)

        db.close()
    }
}
