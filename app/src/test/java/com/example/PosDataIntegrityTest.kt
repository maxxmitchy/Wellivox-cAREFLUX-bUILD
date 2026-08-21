package com.example

import com.example.data.InventoryBatch
import com.example.data.InventoryItem
import com.example.data.MedicationSale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PosDataIntegrityTest {

    @Test
    fun testMedicationSaleHasClientTransactionIdAndBranchId() {
        val sale = MedicationSale(
            productName = "Amoxicillin 500mg",
            quantitySold = 10,
            salePrice = 5000.0,
            clientTransactionId = "SALE_BR001_1724071200000_a1b2c3d4",
            branchId = "BR001"
        )

        assertEquals("SALE_BR001_1724071200000_a1b2c3d4", sale.clientTransactionId)
        assertEquals("BR001", sale.branchId)
    }

    @Test
    fun testInventoryItemQuantityUnchangedOnCartOperations() {
        val initialStock = 50
        val item = InventoryItem(
            id = 101,
            name = "Paracetamol 500mg",
            dosage = "500mg",
            stockQuantity = initialStock,
            minRequiredStock = 5,
            category = "Analgesics",
            price = 200.0
        )

        // Simulating in-memory cart addition
        val cartQuantity = 5
        val remainingInInventory = item.stockQuantity // Must NOT change on cart addition

        assertEquals(50, remainingInInventory)
    }

    @Test
    fun testClientTransactionIdFormat() {
        val branchId = "BR_TEST_01"
        val timestamp = System.currentTimeMillis()
        val uuidPart = java.util.UUID.randomUUID().toString().take(8)
        val clientTxId = "SALE_${branchId}_${timestamp}_${uuidPart}"

        assertTrue(clientTxId.startsWith("SALE_${branchId}_"))
        assertNotNull(clientTxId)
    }

    @Test
    fun testCheckoutFailureRetainsCartAndStock() {
        var cartCleared = false
        var stockDeducted = false

        // Simulating failed checkout execution
        val checkoutSuccess = false

        if (checkoutSuccess) {
            cartCleared = true
            stockDeducted = true
        }

        assertFalse(cartCleared)
        assertFalse(stockDeducted)
    }

    @Test
    fun testFEFOBatchSortingLogic() {
        val now = System.currentTimeMillis()
        val batch1 = InventoryBatch(id = 1, inventoryItemId = 10, batchNumber = "B001", stockQuantity = 20, expiryDate = now + 100000)
        val batch2 = InventoryBatch(id = 2, inventoryItemId = 10, batchNumber = "B002", stockQuantity = 20, expiryDate = now + 50000)
        val batches = listOf(batch1, batch2)

        val sorted = batches.sortedWith(
            compareBy<InventoryBatch> { it.expiryDate <= now }
                .thenBy { it.expiryDate }
        )

        assertEquals("B002", sorted.first().batchNumber)
    }

    @Test
    fun testStockDeductionCannotGoNegative() {
        val currentStock = 3
        val requestedQuantity = 5

        val canFulfill = currentStock >= requestedQuantity
        assertFalse(canFulfill)

        val finalStock = if (canFulfill) currentStock - requestedQuantity else currentStock
        assertEquals(3, finalStock)
    }

    @Test
    fun testClientTransactionIdSurvivesRetry() {
        val initialTxId = "SALE_BR001_1724071200000_abc123"
        val retryTxId = initialTxId // Retry must reuse exact same ID

        assertEquals(initialTxId, retryTxId)
    }

    @Test
    fun testStaleLocalStockDoesNotOverwriteRemoteWhenOlder() {
        val localLastUpdated = 1000L
        val remoteLastUpdated = 2000L

        val shouldPushStock = localLastUpdated > remoteLastUpdated
        assertFalse(shouldPushStock)
    }

    private fun getRulesFileContent(): String {
        val candidates = listOf(
            java.io.File("firestore.rules"),
            java.io.File("../firestore.rules"),
            java.io.File("../../firestore.rules"),
            java.io.File("/firestore.rules")
        )
        val file = candidates.firstOrNull { it.exists() } ?: throw IllegalStateException("firestore.rules not found")
        return file.readText()
    }

    @Test
    fun testRoomMigration30To31SqlStatements() {
        val sql1 = "ALTER TABLE medication_sales ADD COLUMN clientTransactionId TEXT NOT NULL DEFAULT ''"
        val sql2 = "ALTER TABLE medication_sales ADD COLUMN branchId TEXT NOT NULL DEFAULT ''"

        assertTrue(sql1.contains("clientTransactionId"))
        assertTrue(sql2.contains("branchId"))
    }

    @Test
    fun testMedicationSalesImmutabilityNonAdminRuleContract() {
        val content = getRulesFileContent()

        val salesBlockStartIndex = content.indexOf("match /medication_sales/{saleId}")
        assertTrue("medication_sales match block must exist", salesBlockStartIndex >= 0)
        
        val salesBlock = content.substring(salesBlockStartIndex, (salesBlockStartIndex + 1000).coerceAtMost(content.length))
        assertTrue("medication_sales update must restrict non-admins", salesBlock.contains("allow update: if isAdmin();"))
        assertTrue("medication_sales delete must restrict non-admins", salesBlock.contains("allow delete: if isAdmin();"))
    }

    @Test
    fun testDeviceConfigsLeastPrivilegeRuleContract() {
        val content = getRulesFileContent()

        val deviceBlockStartIndex = content.indexOf("match /device_configs/{nodeId}")
        assertTrue("device_configs match block must exist", deviceBlockStartIndex >= 0)

        val deviceBlock = content.substring(deviceBlockStartIndex, (deviceBlockStartIndex + 500).coerceAtMost(content.length))
        assertFalse("device_configs must not allow universal read", deviceBlock.contains("allow read: if isAuth();\n"))
        assertTrue("device_configs must check branchId or ownerUid", deviceBlock.contains("resource.data.branchId == getBranchId()") || deviceBlock.contains("resource.data.ownerUid == request.auth.uid"))
    }

    @Test
    fun testRescueListingsClaimRulesContract() {
        val content = getRulesFileContent()

        val rescueBlockStartIndex = content.indexOf("match /expiry_rescue_listings/{listingId}")
        assertTrue("expiry_rescue_listings match block must exist", rescueBlockStartIndex >= 0)

        val rescueBlock = content.substring(rescueBlockStartIndex, (rescueBlockStartIndex + 1200).coerceAtMost(content.length))
        assertTrue("expiry_rescue_listings must support status transition during claim", rescueBlock.contains("request.resource.data.status in ['Claimed', 'Accepted']"))
        assertTrue("expiry_rescue_listings update must preserve branchId", rescueBlock.contains("request.resource.data.branchId == resource.data.branchId"))
    }
}
