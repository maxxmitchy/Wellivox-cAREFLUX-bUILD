package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.ui.PharmacyViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class Phase27_10BOperationsTaskIdentityTest {

    private lateinit var database: PharmacyDatabase
    private lateinit var viewModel: PharmacyViewModel
    private lateinit var application: Application

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        val prefs = application.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .putString("cached_branch_id", "MAIN_BRANCH")
            .putString("cached_uid", "TEST_USER_UID")
            .commit()

        database = PharmacyDatabase.getDatabase(application)
        runBlocking {
            val dao = database.pharmacyDao()
            dao.getAllOperationTasks().first().forEach { dao.deleteOperationTask(it) }
            dao.getAllInventoryItems().first().forEach { dao.deleteInventoryItem(it) }
        }
        viewModel = PharmacyViewModel(application)
    }

    @After
    fun tearDown() {
        runBlocking {
            val dao = database.pharmacyDao()
            dao.getAllOperationTasks().first().forEach { dao.deleteOperationTask(it) }
            dao.getAllInventoryItems().first().forEach { dao.deleteInventoryItem(it) }
        }
    }

    private fun createTestItem(
        id: Int = 0,
        name: String,
        dosage: String = "",
        stockQuantity: Int = 100,
        minRequiredStock: Int = 20,
        expiryDate: Long = System.currentTimeMillis() + 180L * 24 * 60 * 60 * 1000L,
        lastReconciledAt: Long = 0L,
        branchId: String = "MAIN_BRANCH",
        isFastMoving: Boolean = false,
        originatingUserUid: String = "TEST_USER_UID"
    ) = InventoryItem(
        id = id,
        name = name,
        dosage = dosage,
        stockQuantity = stockQuantity,
        minRequiredStock = minRequiredStock,
        category = "Prescription",
        price = 1500.0,
        expiryDate = expiryDate,
        lastReconciledAt = lastReconciledAt,
        branchId = branchId,
        isFastMoving = isFastMoving,
        originatingUserUid = originatingUserUid,
        globalId = UUID.randomUUID().toString()
    )

    private suspend fun waitUntilTasksDispatched(predicate: (List<OperationTask>) -> Boolean): List<OperationTask> {
        for (i in 0 until 50) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            kotlinx.coroutines.delay(50)
            val current = database.pharmacyDao().getAllOperationTasks().first()
            if (predicate(current)) {
                return current
            }
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        return database.pharmacyDao().getAllOperationTasks().first()
    }

    private suspend fun waitUntilItemUpdated(itemId: Int, predicate: (InventoryItem?) -> Boolean): InventoryItem? {
        for (i in 0 until 50) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            kotlinx.coroutines.delay(50)
            val item = database.pharmacyDao().getInventoryItemById(itemId)
            if (predicate(item)) {
                return item
            }
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        return database.pharmacyDao().getInventoryItemById(itemId)
    }

    @Test
    fun testLowStockTaskCreation_populatesAuthoritativeIdentity() = runBlocking {
        val lowStockItem = createTestItem(
            id = 101,
            name = "Amoxicillin",
            dosage = "500mg",
            stockQuantity = 5,
            minRequiredStock = 20,
            branchId = "MAIN_BRANCH"
        )
        database.pharmacyDao().insertInventoryItem(lowStockItem)

        var finished = false
        viewModel.dispatchAutomatedVerificationTasks { finished = true }

        val tasks = waitUntilTasksDispatched { list -> list.any { it.taskType == "LOW_STOCK_VERIFICATION" } }
        val lowStockTask = tasks.find { it.taskType == "LOW_STOCK_VERIFICATION" }

        assertNotNull("Low stock task should have been dispatched", lowStockTask)
        assertEquals("Task must have exact inventoryItemId", 101, lowStockTask!!.inventoryItemId)
        assertEquals("Task must have exact taskType", "LOW_STOCK_VERIFICATION", lowStockTask.taskType)
        assertNotNull("Task must have dueTimestamp", lowStockTask.dueTimestamp)
        assertTrue("dueTimestamp must be in the future", lowStockTask.dueTimestamp!! > System.currentTimeMillis())
    }

    @Test
    fun testExpiryAuditTaskCreation_populatesAuthoritativeIdentity() = runBlocking {
        val now = System.currentTimeMillis()
        val nearExpiryItem = createTestItem(
            id = 102,
            name = "Augmentin",
            dosage = "625mg",
            stockQuantity = 50,
            expiryDate = now + 15L * 24 * 60 * 60 * 1000L, // 15 days
            branchId = "MAIN_BRANCH"
        )
        database.pharmacyDao().insertInventoryItem(nearExpiryItem)

        var finished = false
        viewModel.dispatchAutomatedVerificationTasks { finished = true }

        val tasks = waitUntilTasksDispatched { list -> list.any { it.taskType == "EXPIRY_AUDIT" } }
        val expiryTask = tasks.find { it.taskType == "EXPIRY_AUDIT" }

        assertNotNull("Expiry task should have been dispatched", expiryTask)
        assertEquals("Task must have exact inventoryItemId", 102, expiryTask!!.inventoryItemId)
        assertEquals("Task must have exact taskType", "EXPIRY_AUDIT", expiryTask.taskType)
        assertEquals("Task dueTimestamp should match item expiryDate", nearExpiryItem.expiryDate, expiryTask.dueTimestamp)
    }

    @Test
    fun testCycleCountTaskCreation_populatesAuthoritativeIdentity() = runBlocking {
        val overdueItem = createTestItem(
            id = 103,
            name = "Metformin",
            dosage = "500mg",
            stockQuantity = 40,
            lastReconciledAt = 0L, // Never reconciled
            branchId = "MAIN_BRANCH"
        )
        database.pharmacyDao().insertInventoryItem(overdueItem)

        var finished = false
        viewModel.dispatchAutomatedVerificationTasks { finished = true }

        val tasks = waitUntilTasksDispatched { list -> list.any { it.taskType == "CYCLE_COUNT" } }
        val cycleCountTask = tasks.find { it.taskType == "CYCLE_COUNT" }

        assertNotNull("Cycle count task should have been dispatched", cycleCountTask)
        assertEquals("Task must have exact inventoryItemId", 103, cycleCountTask!!.inventoryItemId)
        assertEquals("Task must have exact taskType", "CYCLE_COUNT", cycleCountTask.taskType)
        assertNotNull("Task must have non-null dueTimestamp", cycleCountTask.dueTimestamp)
    }

    @Test
    fun testReconciliation_withAuthoritativeInventoryItemId_updatesItemSuccessfully() = runBlocking {
        val item = createTestItem(
            id = 201,
            name = "Paracetamol",
            dosage = "500mg",
            stockQuantity = 100,
            lastReconciledAt = 0L,
            branchId = "MAIN_BRANCH"
        )
        database.pharmacyDao().insertInventoryItem(item)

        val task = OperationTask(
            id = 501,
            title = "Inventory Verification: Paracetamol 500mg [Item #201]",
            description = "Audit shelf count",
            urgency = "High",
            category = "Clinical Intelligence",
            isCompleted = false,
            inventoryItemId = 201,
            taskType = "LOW_STOCK_VERIFICATION",
            branchId = "MAIN_BRANCH",
            originatingUserUid = "TEST_USER_UID"
        )
        database.pharmacyDao().insertOperationTask(task)

        // Complete with counted quantity = 95
        viewModel.verifiablyCompleteOperationTask(
            task = task,
            notes = "Physical count verified on shelf 3B",
            channel = "IN_PERSON",
            patientName = "Staff Pharmacist",
            countedQuantity = 95
        ) { success, _ -> assertTrue(success) }

        val updatedItem = waitUntilItemUpdated(201) { it != null && it.stockQuantity == 95 }
        assertNotNull(updatedItem)
        assertEquals("Stock quantity should be updated to 95", 95, updatedItem!!.stockQuantity)
        assertTrue("lastReconciledAt must be set to recent timestamp", updatedItem.lastReconciledAt > 0L)
    }

    @Test
    fun testReconciliation_failClosed_whenInventoryItemIdIsNull() = runBlocking {
        val item = createTestItem(
            id = 301,
            name = "Coartem",
            dosage = "20/120mg",
            stockQuantity = 80,
            lastReconciledAt = 0L,
            branchId = "MAIN_BRANCH"
        )
        database.pharmacyDao().insertInventoryItem(item)

        // Task mentions item in text, but has NO inventoryItemId (legacy / unlinked)
        val unlinkedTask = OperationTask(
            id = 601,
            title = "Inventory Verification: Coartem 20/120mg [Item #301]",
            description = "Stock low. Verify shelf count for Coartem [Item ID: 301].",
            urgency = "High",
            category = "Clinical Intelligence",
            isCompleted = false,
            inventoryItemId = null, // NULL IDENTITY
            taskType = "GENERAL",
            branchId = "MAIN_BRANCH",
            originatingUserUid = "test_user"
        )
        database.pharmacyDao().insertOperationTask(unlinkedTask)

        // Attempt completion with counted quantity 40
        viewModel.verifiablyCompleteOperationTask(
            task = unlinkedTask,
            notes = "Completed count",
            channel = "IN_PERSON",
            patientName = "Staff",
            countedQuantity = 40
        ) { _, _ -> }

        kotlinx.coroutines.delay(200)

        val itemAfter = database.pharmacyDao().getInventoryItemById(301)
        assertNotNull(itemAfter)
        assertEquals("Stock quantity MUST NOT change when inventoryItemId is null (Fail-Closed)", 80, itemAfter!!.stockQuantity)
        assertEquals("lastReconciledAt MUST NOT change when inventoryItemId is null", 0L, itemAfter.lastReconciledAt)
    }

    @Test
    fun testReconciliation_failClosed_whenTaskTypeIsLegacyUnresolved() = runBlocking {
        val item = createTestItem(
            id = 401,
            name = "Cataflam",
            dosage = "50mg",
            stockQuantity = 50,
            lastReconciledAt = 0L,
            branchId = "MAIN_BRANCH"
        )
        database.pharmacyDao().insertInventoryItem(item)

        val unresolvedTask = OperationTask(
            id = 701,
            title = "Expiry Shelf Audit: Cataflam 50mg",
            description = "Check shelf stock",
            urgency = "Medium",
            category = "Revenue & Retention",
            isCompleted = false,
            inventoryItemId = 401,
            taskType = "LEGACY_UNRESOLVED", // Explicitly unresolved
            branchId = "MAIN_BRANCH",
            originatingUserUid = "test_user"
        )
        database.pharmacyDao().insertOperationTask(unresolvedTask)

        viewModel.verifiablyCompleteOperationTask(
            task = unresolvedTask,
            notes = "Counted",
            channel = "IN_PERSON",
            patientName = "Staff",
            countedQuantity = 10
        ) { _, _ -> }

        kotlinx.coroutines.delay(200)

        val itemAfter = database.pharmacyDao().getInventoryItemById(401)
        assertNotNull(itemAfter)
        assertEquals("Stock quantity MUST NOT change for LEGACY_UNRESOLVED tasks", 50, itemAfter!!.stockQuantity)
        assertEquals("lastReconciledAt MUST NOT change for LEGACY_UNRESOLVED tasks", 0L, itemAfter.lastReconciledAt)
    }

    @Test
    fun testReconciliation_multiVariantIsolation_onlyUpdatesTargetVariant() = runBlocking {
        val variantA = createTestItem(
            id = 501,
            name = "Exforge",
            dosage = "5/160mg",
            stockQuantity = 100,
            lastReconciledAt = 0L,
            branchId = "MAIN_BRANCH"
        )
        val variantB = createTestItem(
            id = 502,
            name = "Exforge",
            dosage = "10/160mg",
            stockQuantity = 100,
            lastReconciledAt = 0L,
            branchId = "MAIN_BRANCH"
        )
        database.pharmacyDao().insertInventoryItem(variantA)
        database.pharmacyDao().insertInventoryItem(variantB)

        val taskA = OperationTask(
            id = 801,
            title = "Inventory Verification: Exforge (5/160mg)",
            description = "Count shelf stock",
            urgency = "High",
            category = "Clinical Intelligence",
            isCompleted = false,
            inventoryItemId = 501, // Points strictly to variant A
            taskType = "LOW_STOCK_VERIFICATION",
            branchId = "MAIN_BRANCH",
            originatingUserUid = "TEST_USER_UID"
        )
        database.pharmacyDao().insertOperationTask(taskA)

        viewModel.verifiablyCompleteOperationTask(
            task = taskA,
            notes = "Verified variant A",
            channel = "IN_PERSON",
            patientName = "Staff",
            countedQuantity = 65
        ) { _, _ -> }

        val updatedA = waitUntilItemUpdated(501) { it != null && it.stockQuantity == 65 }
        val updatedB = database.pharmacyDao().getInventoryItemById(502)

        assertNotNull(updatedA)
        assertEquals("Variant A stock must be updated to 65", 65, updatedA!!.stockQuantity)
        assertTrue("Variant A lastReconciledAt must be updated", updatedA.lastReconciledAt > 0L)

        assertEquals("Variant B stock must NOT be touched", 100, updatedB!!.stockQuantity)
        assertEquals("Variant B lastReconciledAt must remain 0", 0L, updatedB.lastReconciledAt)
    }

    @Test
    fun testReconciliation_branchIsolation_failClosedOnMismatchedBranch() = runBlocking {
        val branchAItem = createTestItem(
            id = 601,
            name = "Lipitor",
            dosage = "20mg",
            stockQuantity = 50,
            lastReconciledAt = 0L,
            branchId = "BRANCH_LAGOS"
        )
        database.pharmacyDao().insertInventoryItem(branchAItem)

        // Task is created under a different branch (BRANCH_ABUJA) but erroneously has inventoryItemId 601
        val crossBranchTask = OperationTask(
            id = 901,
            title = "Inventory Verification: Lipitor 20mg",
            description = "Audit count",
            urgency = "High",
            category = "Clinical Intelligence",
            isCompleted = false,
            inventoryItemId = 601,
            taskType = "LOW_STOCK_VERIFICATION",
            branchId = "BRANCH_ABUJA", // Mismatch
            originatingUserUid = "test_user"
        )
        database.pharmacyDao().insertOperationTask(crossBranchTask)

        viewModel.verifiablyCompleteOperationTask(
            task = crossBranchTask,
            notes = "Counted cross branch",
            channel = "IN_PERSON",
            patientName = "Staff",
            countedQuantity = 20
        ) { _, _ -> }

        kotlinx.coroutines.delay(200)

        val itemAfter = database.pharmacyDao().getInventoryItemById(601)
        assertEquals("Cross-branch reconciliation breach must fail closed without modifying item stock", 50, itemAfter!!.stockQuantity)
        assertEquals("Cross-branch reconciliation breach must fail closed without modifying lastReconciledAt", 0L, itemAfter.lastReconciledAt)
    }

    @Test
    fun testMigration_resolvesUnambiguousLegacyTasks() = runBlocking {
        val item = createTestItem(
            id = 701,
            name = "Ventolin Inhaler",
            dosage = "100mcg",
            stockQuantity = 20,
            branchId = "MAIN_BRANCH"
        )
        database.pharmacyDao().insertInventoryItem(item)

        val legacyTask = OperationTask(
            id = 1001,
            title = "Expiry Shelf Audit: Ventolin Inhaler (100mcg) [Item #701]",
            description = "Batch expiring soon. Reconcile shelf count [Item ID: 701].",
            urgency = "High",
            category = "Revenue & Retention",
            isCompleted = false,
            branchId = "MAIN_BRANCH",
            originatingUserUid = "test_user",
            inventoryItemId = null,
            taskType = null
        )
        database.pharmacyDao().insertOperationTask(legacyTask)

        val (resolved, unresolved) = viewModel.migrateLegacyOperationTasks()

        assertEquals("Should have resolved 1 task", 1, resolved)
        assertEquals("Should have 0 unresolved tasks", 0, unresolved)

        val migratedTask = database.pharmacyDao().getOperationTaskById(1001)
        assertNotNull(migratedTask)
        assertEquals("inventoryItemId should be populated with 701", 701, migratedTask!!.inventoryItemId)
        assertEquals("taskType should be inferred as EXPIRY_AUDIT", "EXPIRY_AUDIT", migratedTask.taskType)
    }

    @Test
    fun testMigration_marksAmbiguousOrCrossBranchLegacyTasks_asUnresolved() = runBlocking {
        // Task has no matching item in database for its ID
        val missingItemLegacyTask = OperationTask(
            id = 1002,
            title = "Expiry Shelf Audit: NonExistentMed [Item #999]",
            description = "Check shelf",
            urgency = "High",
            category = "Revenue & Retention",
            isCompleted = false,
            branchId = "MAIN_BRANCH",
            originatingUserUid = "test_user",
            inventoryItemId = null,
            taskType = null
        )
        database.pharmacyDao().insertOperationTask(missingItemLegacyTask)

        val (resolved, unresolved) = viewModel.migrateLegacyOperationTasks()

        assertEquals("Should have 1 unresolved task", 1, unresolved)

        val migratedTask = database.pharmacyDao().getOperationTaskById(1002)
        assertNotNull(migratedTask)
        assertEquals("taskType should be set to LEGACY_UNRESOLVED", "LEGACY_UNRESOLVED", migratedTask!!.taskType)
        assertNull("inventoryItemId must remain null", migratedTask.inventoryItemId)
    }
}
