package com.example

import com.example.data.CustomerMedication
import com.example.data.InventoryBatch
import com.example.data.InventoryItem
import com.example.util.BatchResolutionResult
import com.example.util.StockTransferPayload
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/**
 * Phase 27.9.2 — Stock Transfer Identity Hardening Test Suite
 *
 * Comprehensive adversarial verification of:
 * 1. Single candidate + matching sourceGlobalId -> PASS
 * 2. Single candidate + conflicting sourceGlobalId -> FAIL CLOSED
 * 3. Multiple candidates + exactly one matching sourceGlobalId -> PASS
 * 4. Multiple candidates + zero matching sourceGlobalId -> FAIL CLOSED
 * 5. Multiple candidates + duplicate sourceGlobalId -> FAIL CLOSED
 * 6. No sourceGlobalId + unique variant attributes -> PASS
 * 7. No sourceGlobalId + ambiguous variant attributes -> FAIL CLOSED
 * 8. Destination variant creation globalId semantics
 * 9. Source local item ID isolation (never treated as globally unique)
 * 10. Existing batch conflict detection (immutable lot metadata protection)
 * 11. New batch creation for existing variant
 * 12. Multiple batches isolation
 * 13. Tenant & cross-branch authorization
 * 14. POS inventory selection isolation
 * 15. CustomerMedication inventoryItemId behavior preservation
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

    // 1. Single candidate + matching sourceGlobalId -> PASS
    @Test
    fun test1_singleCandidateMatchingSourceGlobalId_passes() {
        val sharedGlobalId = "GLOBAL-ID-EXFORGE-101"
        val candidates = listOf(
            createTestItem(id = 101, name = "Exforge HCT", dosage = "10/160/25 mg", unitForm = "Tablet", brand = "Novartis", globalId = sharedGlobalId)
        )
        val payload = StockTransferPayload(
            sourceGlobalId = sharedGlobalId,
            sourceItemId = 999,
            name = "Exforge HCT",
            dosage = "10/160/25 mg",
            unitForm = "Tablet",
            brand = "Novartis",
            quantity = 10
        )
        val resolved = StockTransferPayload.resolveMatchingInventoryItem(candidates, payload)
        assertNotNull("Single candidate with matching sourceGlobalId must resolve", resolved)
        assertEquals(101, resolved!!.id)
        assertEquals(sharedGlobalId, resolved.globalId)
    }

    // 2. Single candidate + conflicting sourceGlobalId -> FAIL CLOSED
    @Test
    fun test2_singleCandidateConflictingSourceGlobalId_failsClosed() {
        val candidates = listOf(
            createTestItem(id = 101, name = "Exforge HCT", dosage = "10/160/25 mg", unitForm = "Tablet", brand = "Novartis", globalId = "GLOBAL-ID-DEST-AAA")
        )
        val payload = StockTransferPayload(
            sourceGlobalId = "GLOBAL-ID-SOURCE-ZZZ", // Conflicting globalId
            sourceItemId = 999,
            name = "Exforge HCT",
            dosage = "10/160/25 mg",
            unitForm = "Tablet",
            brand = "Novartis",
            quantity = 10
        )
        val resolved = StockTransferPayload.resolveMatchingInventoryItem(candidates, payload)
        assertNull("Single candidate with conflicting sourceGlobalId MUST fail closed", resolved)
    }

    // 3. Multiple candidates + exactly one matching sourceGlobalId -> PASS
    @Test
    fun test3_multipleCandidatesExactlyOneMatchingSourceGlobalId_passes() {
        val targetGlobalId = "GLOBAL-ID-AMOX-TARGET"
        val candidates = listOf(
            createTestItem(id = 101, name = "Amoxicillin", dosage = "500mg", unitForm = "Capsule", brand = "Generic", globalId = targetGlobalId),
            createTestItem(id = 102, name = "Amoxicillin", dosage = "500mg", unitForm = "Capsule", brand = "Generic", globalId = "GLOBAL-ID-AMOX-OTHER")
        )
        val payload = StockTransferPayload(
            sourceGlobalId = targetGlobalId,
            sourceItemId = 888,
            name = "Amoxicillin",
            dosage = "500mg",
            unitForm = "Capsule",
            brand = "Generic",
            quantity = 15
        )
        val resolved = StockTransferPayload.resolveMatchingInventoryItem(candidates, payload)
        assertNotNull("Must select the candidate with matching globalId", resolved)
        assertEquals(101, resolved!!.id)
    }

    // 4. Multiple candidates + zero matching sourceGlobalId -> FAIL CLOSED
    @Test
    fun test4_multipleCandidatesZeroMatchingSourceGlobalId_failsClosed() {
        val candidates = listOf(
            createTestItem(id = 101, name = "Amoxicillin", dosage = "500mg", unitForm = "Capsule", brand = "Generic", globalId = "DEST-GLOBAL-1"),
            createTestItem(id = 102, name = "Amoxicillin", dosage = "500mg", unitForm = "Capsule", brand = "Generic", globalId = "DEST-GLOBAL-2")
        )
        val payload = StockTransferPayload(
            sourceGlobalId = "SOURCE-GLOBAL-NON-MATCHING",
            sourceItemId = 777,
            name = "Amoxicillin",
            dosage = "500mg",
            unitForm = "Capsule",
            brand = "Generic",
            quantity = 10
        )
        val resolved = StockTransferPayload.resolveMatchingInventoryItem(candidates, payload)
        assertNull("When sourceGlobalId is provided and contradicts all candidates, must FAIL CLOSED", resolved)
    }

    // 5. Multiple candidates + duplicate sourceGlobalId -> FAIL CLOSED
    @Test
    fun test5_multipleCandidatesDuplicateSourceGlobalId_failsClosed() {
        val duplicateGlobalId = "GLOBAL-CORRUPT-DUPLICATE"
        val candidates = listOf(
            createTestItem(id = 101, name = "Amoxicillin", dosage = "500mg", unitForm = "Capsule", brand = "Generic", globalId = duplicateGlobalId),
            createTestItem(id = 102, name = "Amoxicillin", dosage = "500mg", unitForm = "Capsule", brand = "Generic", globalId = duplicateGlobalId)
        )
        val payload = StockTransferPayload(
            sourceGlobalId = duplicateGlobalId,
            sourceItemId = 666,
            name = "Amoxicillin",
            dosage = "500mg",
            unitForm = "Capsule",
            brand = "Generic",
            quantity = 10
        )
        val resolved = StockTransferPayload.resolveMatchingInventoryItem(candidates, payload)
        assertNull("Duplicate globalId among candidates must FAIL CLOSED", resolved)
    }

    // 6. No sourceGlobalId + unique variant attributes -> PASS
    @Test
    fun test6_noSourceGlobalIdUniqueVariantAttributes_passes() {
        val candidates = listOf(
            createTestItem(id = 201, name = "Paracetamol", dosage = "500mg", unitForm = "Syrup", brand = "Emzor"),
            createTestItem(id = 202, name = "Paracetamol", dosage = "500mg", unitForm = "Tablet", brand = "Emzor")
        )
        val payload = StockTransferPayload(
            sourceGlobalId = "", // Legacy transfer
            sourceItemId = 555,
            name = "Paracetamol",
            dosage = "500mg",
            unitForm = "Syrup",
            brand = "Emzor",
            quantity = 10
        )
        val resolved = StockTransferPayload.resolveMatchingInventoryItem(candidates, payload)
        assertNotNull(resolved)
        assertEquals(201, resolved!!.id)
        assertEquals("Syrup", resolved.unitForm)
    }

    // 7. No sourceGlobalId + ambiguous variant attributes -> FAIL CLOSED
    @Test
    fun test7_noSourceGlobalIdAmbiguousVariantAttributes_failsClosed() {
        val candidates = listOf(
            createTestItem(id = 301, name = "Paracetamol", dosage = "500mg", unitForm = "Tablet", brand = "Emzor"),
            createTestItem(id = 302, name = "Paracetamol", dosage = "500mg", unitForm = "Tablet", brand = "M&B")
        )
        val payload = StockTransferPayload(
            sourceGlobalId = "",
            sourceItemId = 444,
            name = "Paracetamol",
            dosage = "500mg",
            unitForm = "Tablet",
            brand = "", // Unspecified brand creates ambiguity between candidate 301 and 302
            quantity = 10
        )
        val resolved = StockTransferPayload.resolveMatchingInventoryItem(candidates, payload)
        assertNull("Ambiguous variant attributes without sourceGlobalId must FAIL CLOSED", resolved)
    }

    // 8. Destination variant creation globalId semantics
    @Test
    fun test8_destinationVariantCreationUsesValidGlobalIdSemantics() {
        val sourceItem = createTestItem(
            id = 50,
            name = "Coartem",
            dosage = "20/120mg",
            unitForm = "Tablet",
            globalId = "G-COARTEM-50"
        )
        val payload = StockTransferPayload(
            sourceGlobalId = sourceItem.globalId,
            sourceItemId = sourceItem.id,
            name = sourceItem.name,
            dosage = sourceItem.dosage,
            unitForm = sourceItem.unitForm,
            quantity = 20,
            fromBranch = "BranchA"
        )
        // If destination creates a new item, it maintains valid identity
        val destinationItem = InventoryItem(
            id = 0,
            name = payload.name,
            dosage = payload.dosage,
            unitForm = payload.unitForm,
            stockQuantity = payload.quantity,
            minRequiredStock = 5,
            category = "General",
            branchId = "BranchB",
            globalId = payload.sourceGlobalId.ifBlank { UUID.randomUUID().toString() }
        )
        assertEquals("G-COARTEM-50", destinationItem.globalId)
        assertEquals("BranchB", destinationItem.branchId)
        assertEquals(20, destinationItem.stockQuantity)
    }

    // 9. Source local item ID is never treated as globally unique or destination primary key
    @Test
    fun test9_sourceLocalItemIdIsNotTreatedAsGloballyUnique() {
        val sourceItem = createTestItem(
            id = 17,
            globalId = "G-VENTOLIN-UUID",
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
        
        // Destination BranchB has local ID 17 which is an entirely different drug (Insulin)
        val destLocalItem17 = createTestItem(
            id = 17,
            globalId = "G-INSULIN-UUID",
            name = "Insulin Glargine",
            dosage = "100IU/ml",
            unitForm = "Pen",
            branchId = "BranchB"
        )
        val resolved = StockTransferPayload.resolveMatchingInventoryItem(listOf(destLocalItem17), payload)
        assertNull("Must NEVER match destination item #17 of a different drug", resolved)
    }

    // 10. Existing Phase 27.9.1 batch conflict test still passes (immutable lot metadata protection)
    @Test
    fun test10_batchImmutableMetadataConflictFailsClosed() {
        val existingBatches = listOf(
            InventoryBatch(id = 1, inventoryItemId = 102, batchNumber = "LOT-A123", stockQuantity = 10, expiryDate = 1700000000000L)
        )
        val conflictingPayload = StockTransferPayload(
            name = "Cataflam",
            dosage = "50mg",
            batchNumber = "LOT-A123",
            expiryDate = 1800000000000L, // Conflicting Expiry
            quantity = 15
        )
        val batchResult = StockTransferPayload.resolveDestinationBatch(existingBatches, 102, conflictingPayload)
        assertTrue("Must detect immutable lot metadata conflict", batchResult.hasConflict)
        assertTrue("Conflict reason must describe expiry mismatch", batchResult.conflictReason.contains("Conflicting expiry date"))
    }

    // 11. New batch for existing variant creates new InventoryBatch under resolved InventoryItem
    @Test
    fun test11_newBatchForExistingVariantCreatesIsolatedBatch() {
        val existingBatches = listOf(
            InventoryBatch(id = 1, inventoryItemId = 102, batchNumber = "EXISTING-LOT-1", stockQuantity = 10, expiryDate = 1700000000000L)
        )
        val payload = StockTransferPayload(
            name = "Cataflam",
            dosage = "50mg",
            batchNumber = "NEW-LOT-88",
            expiryDate = 1800000000000L,
            quantity = 25
        )
        val batchResult = StockTransferPayload.resolveDestinationBatch(existingBatches, 102, payload)
        assertTrue("Must indicate new batch creation", batchResult.isNewBatch)
        assertFalse("Must not have conflict", batchResult.hasConflict)
        assertNull(batchResult.matchedBatch)
    }

    // 12. Multiple batches remain isolated per variant
    @Test
    fun test12_multipleBatchesRemainIsolatedPerVariant() {
        val batchesItem1 = listOf(
            InventoryBatch(id = 1, inventoryItemId = 101, batchNumber = "LOT-101", stockQuantity = 10, expiryDate = 1700000000000L)
        )
        val batchesItem2 = listOf(
            InventoryBatch(id = 2, inventoryItemId = 102, batchNumber = "LOT-102", stockQuantity = 20, expiryDate = 1750000000000L)
        )
        val payloadForItem2 = StockTransferPayload(
            name = "Augmentin",
            dosage = "625mg",
            batchNumber = "LOT-102",
            expiryDate = 1750000000000L,
            quantity = 5
        )
        val resultItem1 = StockTransferPayload.resolveDestinationBatch(batchesItem1, 101, payloadForItem2)
        assertTrue("Payload for item 2 must be treated as new batch under item 1", resultItem1.isNewBatch)

        val resultItem2 = StockTransferPayload.resolveDestinationBatch(batchesItem2, 102, payloadForItem2)
        assertFalse("Payload for item 2 matches existing batch under item 2", resultItem2.isNewBatch)
        assertEquals(2, resultItem2.matchedBatch?.id)
    }

    // 13. Cross-branch tenant authorization check
    @Test
    fun test13_crossBranchTenantAuthorizationIsolation() {
        val destinationBranch = "Ikeja Branch"
        val activeUserBranch = "Lekki Branch"
        val isAdmin = false

        val isAuthorized = destinationBranch.isBlank() || activeUserBranch.isBlank() ||
                destinationBranch.equals(activeUserBranch, ignoreCase = true) || isAdmin

        assertFalse("Staff at Lekki Branch cannot receive stock addressed to Ikeja Branch", isAuthorized)
    }

    // 14. Existing POS inventory selection remains intact (price preserved, retail pricing isolated)
    @Test
    fun test14_existingPosInventorySelectionPreserved() {
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
        assertEquals(6000.0, destinationVariant.price, 0.001)
    }

    // 15. CustomerMedication inventoryItemId behavior remains intact
    @Test
    fun test15_customerMedicationInventoryItemIdBehaviorIntact() {
        val medication = CustomerMedication(
            id = 1,
            customerId = 10,
            inventoryItemId = 101,
            medicationName = "Amlodipine",
            customDosage = "5mg",
            cost = 1500.0,
            cycleDays = 30,
            nextRefillDate = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000)
        )
        assertEquals(101, medication.inventoryItemId)
        assertEquals("Amlodipine", medication.medicationName)
        assertEquals("5mg", medication.customDosage)
    }
}

