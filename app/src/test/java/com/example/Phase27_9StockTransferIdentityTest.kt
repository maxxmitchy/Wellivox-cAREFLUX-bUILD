package com.example

import com.example.data.InventoryBatch
import com.example.data.InventoryItem
import com.example.util.StockTransferPayload
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/**
 * Phase 27.9 — Stock Transfer Identity Hardening Test Suite
 *
 * Verifies that inter-branch stock transfers deterministically resolve
 * exact InventoryItem (Variant) and InventoryBatch identities, preserving
 * tenant boundaries, batch lot details, and failing closed on ambiguity.
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
        assertNotNull(resolved)
        assertEquals(102, resolved!!.id)
        assertEquals("10/160/25 mg", resolved.dosage)
    }

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
    }

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

    @Test
    fun test5_sameVariantWithDifferentBatchesPreservesBatchIdentity() {
        val payload = StockTransferPayload(
            sourceGlobalId = UUID.randomUUID().toString(),
            sourceItemId = 401,
            name = "Augmentin",
            dosage = "625mg",
            unitForm = "Tablet",
            brand = "GSK",
            batchNumber = "AUG-2027-X",
            expiryDate = 1800000000000L,
            price = 5500.0,
            quantity = 15,
            fromBranch = "Lekki Branch",
            destinationBranch = "Ikeja Branch",
            reason = "Stock balancing"
        )
        val encoded = payload.encodeToTaskDescription()
        val decoded = StockTransferPayload.decodeFromDescription(encoded)
        assertNotNull(decoded)
        assertEquals("AUG-2027-X", decoded!!.batchNumber)
        assertEquals(1800000000000L, decoded.expiryDate)
        assertEquals(5500.0, decoded.price, 0.001)
        assertEquals(15, decoded.quantity)
    }

    @Test
    fun test6_sourceGlobalIdentityIsPreserved() {
        val originalGlobalId = UUID.randomUUID().toString()
        val payload = StockTransferPayload(
            sourceGlobalId = originalGlobalId,
            sourceItemId = 999,
            name = "Cataflam",
            dosage = "50mg",
            unitForm = "Tablet",
            quantity = 5
        )
        val encoded = payload.encodeToTaskDescription()
        val decoded = StockTransferPayload.decodeFromDescription(encoded)
        assertNotNull(decoded)
        assertEquals(originalGlobalId, decoded!!.sourceGlobalId)
        assertEquals(999, decoded.sourceItemId)
    }

    @Test
    fun test7_receivingBranchCannotUseSourceLocalIntegerIdAsGlobalId() {
        val sourceItem = createTestItem(
            id = 17,
            globalId = "G-17-UUID",
            name = "Ventolin Inhaler",
            dosage = "100mcg",
            unitForm = "Inhaler",
            stockQuantity = 50,
            branchId = "BranchA"
        )
        val payload = StockTransferPayload(
            sourceGlobalId = sourceItem.globalId,
            sourceItemId = sourceItem.id,
            name = sourceItem.name,
            dosage = sourceItem.dosage,
            unitForm = sourceItem.unitForm,
            quantity = 10,
            fromBranch = "BranchA",
            destinationBranch = "BranchB"
        )
        
        // Destination branch BranchB has its own local item #17 which is an entirely different drug!
        val destLocalItem17 = createTestItem(
            id = 17,
            globalId = "G-DIFFERENT-UUID",
            name = "Insulin Glargine",
            dosage = "100IU/ml",
            unitForm = "Pen",
            stockQuantity = 5,
            branchId = "BranchB"
        )
        val resolved = StockTransferPayload.resolveMatchingInventoryItem(listOf(destLocalItem17), payload)
        // Must NEVER match destLocalItem17 just because sourceItemId == 17
        assertNull("Must not bind to destination item 17 of different drug", resolved)
    }

    @Test
    fun test8_missingOrUnresolvableIdentityFailsClosed() {
        val invalidPayload1 = StockTransferPayload.decodeFromDescription("Invalid non-structured string")
        assertNull("Corrupt string must return null payload", invalidPayload1)

        val invalidPayload2 = StockTransferPayload.decodeFromDescription("ITEM:  | DOSAGE: 10mg | QTY: 0")
        assertNull("Zero quantity must return null payload", invalidPayload2)
    }

    @Test
    fun test9_receivingDoesNotMutateUnrelatedExistingItem() {
        val candidates = listOf(
            createTestItem(id = 501, name = "Ciprofloxacin", dosage = "500mg", unitForm = "Tablet", brand = "Bayer", stockQuantity = 20),
            createTestItem(id = 502, name = "Amoxicillin", dosage = "500mg", unitForm = "Capsule", brand = "Beecham", stockQuantity = 30)
        )
        val payload = StockTransferPayload(
            name = "Azithromycin",
            dosage = "500mg",
            unitForm = "Tablet",
            quantity = 10
        )
        val resolved = StockTransferPayload.resolveMatchingInventoryItem(candidates, payload)
        assertNull(resolved)
    }

    @Test
    fun test10_batchLineagePreservesCorrectInventoryItemAssociation() {
        val targetItemId = 777
        val batch = InventoryBatch(
            id = 1,
            inventoryItemId = targetItemId,
            batchNumber = "BATCH-777-A",
            stockQuantity = 25,
            expiryDate = 1750000000000L,
            price = 1200.0
        )
        assertEquals(targetItemId, batch.inventoryItemId)
        assertEquals("BATCH-777-A", batch.batchNumber)
    }
}
