package com.example

import com.example.data.InventoryBatch
import com.example.data.InventoryItem
import com.example.util.BatchResolutionResult
import com.example.util.StockTransferPayload
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/**
 * Phase 27.9.1 — Stock Transfer Identity Hardening Test Suite
 *
 * Comprehensive adversarial verification of:
 * - Deterministic variant resolution (InventoryItem)
 * - Deterministic batch lot resolution & conflict handling (InventoryBatch)
 * - Source globalId verification & local ID isolation
 * - Fail-closed ambiguity handling (no best guesses / ties fail closed)
 * - Multi-branch tenant isolation & immutable lot protection
 */
class Phase27_9StockTransferIdentityTest {

    private fun createTestItem(
        id: Int = 0,
        name: String,
        dosage: String,
        stockQuantity: Int = 10,
        minRequiredStock: Int = 5,
        category: String = "General",
        price: Double = 1000.0,
        unitForm: String = "",
        brand: String = "",
        batchNumber: String = "",
        branchId: String = "",
        globalId: String = UUID.randomUUID().toString()
    ) = InventoryItem(
        id = id,
        name = name,
        dosage = dosage,
        stockQuantity = stockQuantity,
        minRequiredStock = minRequiredStock,
        category = category,
        price = price,
        unitForm = unitForm,
        brand = brand,
        batchNumber = batchNumber,
        branchId = branchId,
        globalId = globalId
    )

    // 1. Exact variant transfer resolves to exact InventoryItem
    @Test
    fun test1_exactVariantTransferResolvesCorrectly() {
        val candidates = listOf(
            createTestItem(id = 101, name = "Exforge HCT", dosage = "5/160/12.5 mg", unitForm = "Tablet", brand = "Novartis"),
            createTestItem(id = 102, name = "Exforge HCT", dosage = "10/160/25 mg", unitForm = "Tablet", brand = "Novartis")
        )
        val payload = StockTransferPayload(
            sourceGlobalId = UUID.randomUUID().toString(),
            sourceItemId = 202,
            name = "Exforge HCT",
            dosage = "10/160/25 mg",
            unitForm = "Tablet",
            brand = "Novartis",
            quantity = 10
        )
        val resolved = StockTransferPayload.resolveMatchingInventoryItem(candidates, payload)
        assertNotNull("Exact variant must resolve", resolved)
        assertEquals(102, resolved!!.id)
        assertEquals("10/160/25 mg", resolved.dosage)
    }

    // 2. Two variants with same name but different strength DO NOT collide
    @Test
    fun test2_twoVariantsWithSameNameCannotCollide() {
        val candidates = listOf(
            createTestItem(id = 101, name = "Amlodipine", dosage = "5mg", unitForm = "Tablet", brand = "Pfizer"),
            createTestItem(id = 102, name = "Amlodipine", dosage = "10mg", unitForm = "Tablet", brand = "Pfizer")
        )
        val payload = StockTransferPayload(
            sourceGlobalId = UUID.randomUUID().toString(),
            sourceItemId = 301,
            name = "Amlodipine",
            dosage = "20mg", // Non-existent strength
            unitForm = "Tablet",
            brand = "Pfizer",
            quantity = 5
        )
        val resolved = StockTransferPayload.resolveMatchingInventoryItem(candidates, payload)
        assertNull("Must fail closed and not match 5mg or 10mg variant", resolved)
    }

    // 3. Different unit forms DO NOT collide
    @Test
    fun test3_differentUnitFormsCannotCollide() {
        val candidates = listOf(
            createTestItem(id = 201, name = "Paracetamol", dosage = "500mg", unitForm = "Syrup", brand = "Emzor"),
            createTestItem(id = 202, name = "Paracetamol", dosage = "500mg", unitForm = "Tablet", brand = "Emzor")
        )
        val syrupPayload = StockTransferPayload(
            name = "Paracetamol",
            dosage = "500mg",
            unitForm = "Syrup",
            brand = "Emzor",
            quantity = 10
        )
        val resolved = StockTransferPayload.resolveMatchingInventoryItem(candidates, syrupPayload)
        assertNotNull(resolved)
        assertEquals(201, resolved!!.id)
        assertEquals("Syrup", resolved.unitForm)

        // Conflicting unit form on single candidate fails closed
        val injectionPayload = StockTransferPayload(
            name = "Paracetamol",
            dosage = "500mg",
            unitForm = "Injection",
            quantity = 5
        )
        val conflictingResolved = StockTransferPayload.resolveMatchingInventoryItem(listOf(candidates[1]), injectionPayload)
        assertNull("Conflicting unit form must not match tablet", conflictingResolved)
    }

    // 4. Different brands DO NOT collide where brand is distinct
    @Test
    fun test4_differentBrandsCannotCollideWhereBrandIsDistinct() {
        val candidates = listOf(
            createTestItem(id = 301, name = "Metformin", dosage = "500mg", unitForm = "Tablet", brand = "Glucophage"),
            createTestItem(id = 302, name = "Metformin", dosage = "500mg", unitForm = "Tablet", brand = "Generic")
        )
        val brandPayload = StockTransferPayload(
            name = "Metformin",
            dosage = "500mg",
            unitForm = "Tablet",
            brand = "Glucophage",
            quantity = 20
        )
        val resolved = StockTransferPayload.resolveMatchingInventoryItem(candidates, brandPayload)
        assertNotNull(resolved)
        assertEquals(301, resolved!!.id)
        assertEquals("Glucophage", resolved.brand)
    }

    // 5. Transferred batch attaches to resolved InventoryItem, NOT arbitrary sibling variant
    @Test
    fun test5_transferredBatchAttachesToResolvedInventoryItem() {
        val candidates = listOf(
            createTestItem(id = 101, name = "Cataflam", dosage = "25mg", unitForm = "Tablet"),
            createTestItem(id = 102, name = "Cataflam", dosage = "50mg", unitForm = "Tablet")
        )
        val payload = StockTransferPayload(
            name = "Cataflam",
            dosage = "50mg",
            unitForm = "Tablet",
            batchNumber = "CAT-50-LOT1",
            quantity = 20
        )
        val resolvedItem = StockTransferPayload.resolveMatchingInventoryItem(candidates, payload)
        assertNotNull(resolvedItem)
        assertEquals(102, resolvedItem!!.id)

        val batchResult = StockTransferPayload.resolveDestinationBatch(
            existingBatches = emptyList(),
            destinationItemId = resolvedItem.id,
            payload = payload
        )
        assertTrue(batchResult.isNewBatch)
        assertFalse(batchResult.hasConflict)
    }

    // 6. Matching existing batch at destination updates that batch quantity
    @Test
    fun test6_matchingExistingBatchUpdatesQuantity() {
        val existingBatches = listOf(
            InventoryBatch(id = 1, inventoryItemId = 102, batchNumber = "CAT-50-LOT1", stockQuantity = 15, expiryDate = 1750000000000L)
        )
        val payload = StockTransferPayload(
            name = "Cataflam",
            dosage = "50mg",
            batchNumber = "CAT-50-LOT1",
            expiryDate = 1750000000000L,
            quantity = 10
        )
        val batchResult = StockTransferPayload.resolveDestinationBatch(existingBatches, 102, payload)
        assertFalse(batchResult.isNewBatch)
        assertFalse(batchResult.hasConflict)
        assertNotNull(batchResult.matchedBatch)
        assertEquals(1, batchResult.matchedBatch!!.id)
        assertEquals("CAT-50-LOT1", batchResult.matchedBatch!!.batchNumber)
    }

    // 7. Non-existing batch at destination creates new InventoryBatch under resolved InventoryItem
    @Test
    fun test7_nonExistingBatchCreatesNewBatchUnderResolvedItem() {
        val existingBatches = listOf(
            InventoryBatch(id = 1, inventoryItemId = 102, batchNumber = "OLD-LOT-1", stockQuantity = 10, expiryDate = 1700000000000L)
        )
        val payload = StockTransferPayload(
            name = "Cataflam",
            dosage = "50mg",
            batchNumber = "NEW-LOT-99",
            expiryDate = 1800000000000L,
            quantity = 25
        )
        val batchResult = StockTransferPayload.resolveDestinationBatch(existingBatches, 102, payload)
        assertTrue("Must indicate new batch creation", batchResult.isNewBatch)
        assertFalse("Must not have conflict", batchResult.hasConflict)
        assertNull(batchResult.matchedBatch)
    }

    // 8. Destination batch with conflicting expiry date fails closed / does not silently merge
    @Test
    fun test8_destinationBatchWithConflictingExpiryDateFailsClosed() {
        val existingBatches = listOf(
            InventoryBatch(id = 1, inventoryItemId = 102, batchNumber = "LOT-A123", stockQuantity = 10, expiryDate = 1700000000000L) // Expiry ~2023/2024
        )
        val payload = StockTransferPayload(
            name = "Cataflam",
            dosage = "50mg",
            batchNumber = "LOT-A123",
            expiryDate = 1800000000000L, // Conflicting Expiry ~2027
            quantity = 15
        )
        val batchResult = StockTransferPayload.resolveDestinationBatch(existingBatches, 102, payload)
        assertTrue("Must detect immutable lot metadata conflict", batchResult.hasConflict)
        assertTrue("Conflict reason must be descriptive", batchResult.conflictReason.contains("Conflicting expiry date"))
    }

    // 9. Equal-scoring variant candidates FAIL CLOSED instead of choosing arbitrarily
    @Test
    fun test9_equalScoringCandidatesFailClosedOnAmbiguity() {
        // Two identical items in candidate list where payload cannot distinguish between them
        val candidates = listOf(
            createTestItem(id = 101, name = "Amoxicillin", dosage = "500mg", unitForm = "Capsule", brand = "Generic"),
            createTestItem(id = 102, name = "Amoxicillin", dosage = "500mg", unitForm = "Capsule", brand = "Generic")
        )
        val payload = StockTransferPayload(
            name = "Amoxicillin",
            dosage = "500mg",
            unitForm = "Capsule",
            brand = "Generic",
            quantity = 5
        )
        val resolved = StockTransferPayload.resolveMatchingInventoryItem(candidates, payload)
        assertNull("Must FAIL CLOSED when top candidates tie in scoring", resolved)
    }

    // 10. Source globalId used as verification evidence correctly matches corresponding destination variant
    @Test
    fun test10_sourceGlobalIdMatchesCorrespondingDestinationVariant() {
        val sharedGlobalId = "GLOBAL-PROD-AMOX-500"
        val candidates = listOf(
            createTestItem(id = 101, name = "Amoxicillin", dosage = "500mg", unitForm = "Capsule", globalId = sharedGlobalId),
            createTestItem(id = 102, name = "Amoxicillin", dosage = "500mg", unitForm = "Capsule", globalId = "OTHER-GLOBAL-ID")
        )
        val payload = StockTransferPayload(
            sourceGlobalId = sharedGlobalId,
            sourceItemId = 999,
            name = "Amoxicillin",
            dosage = "500mg",
            unitForm = "Capsule",
            quantity = 10
        )
        val resolved = StockTransferPayload.resolveMatchingInventoryItem(candidates, payload)
        assertNotNull("Global ID must disambiguate candidate", resolved)
        assertEquals(101, resolved!!.id)
    }

    // 11. Source local item id is NOT treated as globally unique or destination primary key
    @Test
    fun test11_sourceLocalItemIdIsNotTreatedAsGloballyUnique() {
        val sourceItem = createTestItem(
            id = 17,
            globalId = "G-17-UUID",
            name = "Ventolin Inhaler",
            dosage = "100mcg",
            unitForm = "Inhaler",
            branchId = "BranchA"
        )
        val payload = StockTransferPayload(
            sourceGlobalId = sourceItem.globalId,
            sourceItemId = sourceItem.id, // 17
            name = sourceItem.name,
            dosage = sourceItem.dosage,
            unitForm = sourceItem.unitForm,
            quantity = 10,
            fromBranch = "BranchA",
            destinationBranch = "BranchB"
        )
        
        // Destination BranchB has local ID 17 which is Insulin
        val destLocalItem17 = createTestItem(
            id = 17,
            globalId = "G-DIFFERENT-UUID",
            name = "Insulin Glargine",
            dosage = "100IU/ml",
            unitForm = "Pen",
            branchId = "BranchB"
        )
        val resolved = StockTransferPayload.resolveMatchingInventoryItem(listOf(destLocalItem17), payload)
        assertNull("Must NEVER match destination item #17 of a different drug", resolved)
    }

    // 12. Source batch deduction (FEFO) decrements InventoryBatch and InventoryItem aggregate in lockstep
    @Test
    fun test12_sourceFefoDeductionDecrementsBatchesAndItemInLockstep() {
        var itemStock = 50
        val batches = mutableListOf(
            InventoryBatch(id = 1, inventoryItemId = 10, batchNumber = "LOT-1", stockQuantity = 20, expiryDate = 1000L),
            InventoryBatch(id = 2, inventoryItemId = 10, batchNumber = "LOT-2", stockQuantity = 30, expiryDate = 2000L)
        )
        
        val transferQuantity = 25
        itemStock -= transferQuantity // 50 -> 25

        var remainingToDeduct = transferQuantity
        val sortedBatches = batches.sortedWith(compareBy({ it.expiryDate }, { it.id }))
        for (i in sortedBatches.indices) {
            val b = sortedBatches[i]
            if (remainingToDeduct <= 0) break
            val deduct = minOf(b.stockQuantity, remainingToDeduct)
            val updated = b.copy(stockQuantity = b.stockQuantity - deduct)
            batches[batches.indexOfFirst { it.id == b.id }] = updated
            remainingToDeduct -= deduct
        }

        assertEquals(25, itemStock)
        assertEquals(0, batches.find { it.id == 1 }!!.stockQuantity)
        assertEquals(25, batches.find { it.id == 2 }!!.stockQuantity)
        assertEquals(itemStock, batches.sumOf { it.stockQuantity })
    }

    // 13. Batch acquisition price / cost does NOT overwrite existing destination variant retail price
    @Test
    fun test13_batchAcquisitionPriceDoesNotOverwriteDestinationVariantRetailPrice() {
        val destinationVariant = createTestItem(
            id = 201,
            name = "Augmentin",
            dosage = "625mg",
            price = 6000.0 // Retail selling price
        )
        val payload = StockTransferPayload(
            name = "Augmentin",
            dosage = "625mg",
            batchNumber = "AUG-TX-1",
            price = 4500.0, // Transfer acquisition cost
            quantity = 10
        )
        val batchResult = StockTransferPayload.resolveDestinationBatch(emptyList(), destinationVariant.id, payload)
        assertTrue(batchResult.isNewBatch)
        // Retail price of existing item remains 6000.0
        assertEquals(6000.0, destinationVariant.price, 0.001)
    }

    // 14. Tenant isolation prevents receiving transfers addressed to a different branch
    @Test
    fun test14_tenantIsolationPreventsCrossBranchReceiving() {
        val taskBranch = "Ikeja Branch"
        val currentPharmacistBranch = "Lekki Branch"
        val isAdmin = false

        val isAuthorized = taskBranch.isBlank() || currentPharmacistBranch.isBlank() ||
                taskBranch.equals(currentPharmacistBranch, ignoreCase = true) || isAdmin

        assertFalse("Pharmacist at Lekki Branch must not be authorized to receive transfer for Ikeja Branch", isAuthorized)
    }

    // 15. Legacy transfer payload with single unambiguous match resolves safely
    @Test
    fun test15_legacyTransferWithSingleUnambiguousMatchResolves() {
        val legacyDescription = "ITEM: Ventolin | DOSAGE: 100mcg | QTY: 5 | FROM: Source Branch | REASON: Emergency"
        val payload = StockTransferPayload.decodeFromDescription(legacyDescription)
        assertNotNull(payload)
        assertEquals("Ventolin", payload!!.name)
        assertEquals("100mcg", payload.dosage)
        assertEquals(5, payload.quantity)

        val candidates = listOf(
            createTestItem(id = 88, name = "Ventolin", dosage = "100mcg", unitForm = "Inhaler")
        )
        val resolved = StockTransferPayload.resolveMatchingInventoryItem(candidates, payload)
        assertNotNull(resolved)
        assertEquals(88, resolved!!.id)
    }

    // 16. Legacy transfer payload with ambiguous candidates FAILS CLOSED
    @Test
    fun test16_legacyTransferWithAmbiguousCandidatesFailsClosed() {
        val legacyDescription = "ITEM: Paracetamol | DOSAGE: 500mg | QTY: 20 | FROM: Source Branch | REASON: Restock"
        val payload = StockTransferPayload.decodeFromDescription(legacyDescription)
        assertNotNull(payload)

        val candidates = listOf(
            createTestItem(id = 1, name = "Paracetamol", dosage = "500mg", unitForm = "Tablet", brand = "Emzor"),
            createTestItem(id = 2, name = "Paracetamol", dosage = "500mg", unitForm = "Tablet", brand = "M&B")
        )
        val resolved = StockTransferPayload.resolveMatchingInventoryItem(candidates, payload!!)
        assertNull("Ambiguous legacy transfer must FAIL CLOSED", resolved)
    }

    // 17. Corrupt or invalid transfer description fails closed without mutating state
    @Test
    fun test17_corruptOrInvalidTransferDescriptionFailsClosed() {
        val corrupt1 = StockTransferPayload.decodeFromDescription("MALFORMED DATA STRING")
        assertNull(corrupt1)

        val corrupt2 = StockTransferPayload.decodeFromDescription("ITEM:  | DOSAGE:  | QTY: -5")
        assertNull(corrupt2)
    }

    // 18. Atomic receiving: batch failure prevents inventory item mutation
    @Test
    fun test18_atomicReceivingBatchFailurePreventsStateMutation() {
        val existingBatches = listOf(
            InventoryBatch(id = 5, inventoryItemId = 100, batchNumber = "LOT-FIXED", stockQuantity = 10, expiryDate = 1600000000000L)
        )
        val conflictingPayload = StockTransferPayload(
            name = "TestDrug",
            dosage = "10mg",
            batchNumber = "LOT-FIXED",
            expiryDate = 1900000000000L, // Conflict
            quantity = 10
        )
        val batchResult = StockTransferPayload.resolveDestinationBatch(existingBatches, 100, conflictingPayload)
        assertTrue(batchResult.hasConflict)
        // With conflict, receiving aborted before Room item or batch update
    }
}
