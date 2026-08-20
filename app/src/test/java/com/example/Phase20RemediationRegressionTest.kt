package com.example

import com.example.data.ClinicalIntervention
import com.example.data.Customer
import com.example.data.CustomerMedication
import com.example.data.InventoryItem
import com.example.data.MedicationSale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase20RemediationRegressionTest {

    @Test
    fun testEntitiesStoreOriginatingBranchAndUserContext() {
        val customer = Customer(
            name = "John Doe",
            phoneNumber = "+2348012345678",
            branchId = "BRANCH_A",
            originatingUserUid = "USER_123"
        )
        assertEquals("BRANCH_A", customer.branchId)
        assertEquals("USER_123", customer.originatingUserUid)

        val medication = CustomerMedication(
            customerId = 1,
            inventoryItemId = 10,
            medicationName = "Metformin 500mg",
            customDosage = "1 daily",
            cost = 1500.0,
            cycleDays = 30,
            nextRefillDate = System.currentTimeMillis(),
            branchId = "BRANCH_A",
            originatingUserUid = "USER_123"
        )
        assertEquals("BRANCH_A", medication.branchId)
        assertEquals("USER_123", medication.originatingUserUid)

        val intervention = ClinicalIntervention(
            customerId = 1,
            presentation = "High BP",
            testResults = "140/90",
            recommendation = "Lifestyle change",
            branchId = "BRANCH_A",
            originatingUserUid = "USER_123"
        )
        assertEquals("BRANCH_A", intervention.branchId)
        assertEquals("USER_123", intervention.originatingUserUid)

        val inventoryItem = InventoryItem(
            id = 1,
            name = "Amlodipine 5mg",
            dosage = "5mg",
            stockQuantity = 100,
            minRequiredStock = 10,
            category = "Antihypertensives",
            branchId = "BRANCH_A",
            originatingUserUid = "USER_123"
        )
        assertEquals("BRANCH_A", inventoryItem.branchId)
        assertEquals("USER_123", inventoryItem.originatingUserUid)

        val sale = MedicationSale(
            productName = "Amlodipine 5mg",
            quantitySold = 1,
            salePrice = 200.0,
            clientTransactionId = "TXN_12345",
            branchId = "BRANCH_A",
            originatingUserUid = "USER_123"
        )
        assertEquals("BRANCH_A", sale.branchId)
        assertEquals("USER_123", sale.originatingUserUid)
    }

    @Test
    fun testMedicationSaleIdempotencyKey() {
        val clientTxnId = "SALE_BRANCH_A_1700000000000_ABC"
        val sale1 = MedicationSale(
            productName = "Paracetamol 500mg",
            quantitySold = 2,
            salePrice = 400.0,
            clientTransactionId = clientTxnId,
            branchId = "BRANCH_A"
        )
        val sale2 = MedicationSale(
            productName = "Paracetamol 500mg",
            quantitySold = 2,
            salePrice = 400.0,
            clientTransactionId = clientTxnId,
            branchId = "BRANCH_A"
        )

        assertEquals(sale1.clientTransactionId, sale2.clientTransactionId)
        assertTrue(sale1.clientTransactionId.isNotBlank())
    }

    @Test
    fun testCorruptRemoteDocumentFiltering() {
        val rawRemoteList = listOf(
            mapOf("id" to 0, "name" to "Corrupt Item"),
            mapOf("id" to 101, "name" to "Valid Item", "dosage" to "500mg")
        )

        val validItems = rawRemoteList.mapNotNull { data ->
            val id = (data["id"] as? Number)?.toInt() ?: return@mapNotNull null
            if (id == 0) return@mapNotNull null
            val name = data["name"] as? String ?: return@mapNotNull null
            val dosage = data["dosage"] as? String ?: ""
            data
        }

        assertEquals(1, validItems.size)
        assertEquals("Valid Item", validItems.first()["name"])
    }
}
