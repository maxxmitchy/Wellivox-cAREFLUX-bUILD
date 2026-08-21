package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.Customer
import com.example.data.PharmacyDatabase
import com.example.data.sync.SyncOutboxRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Phase27_2DurableOutboxVerificationTest {

    @Test
    fun testMalformedPayloadJSONCausesAtomicRollback() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, PharmacyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = db.pharmacyDao()

        val cust = Customer(
            id = 0,
            name = "Bad JSON Customer",
            phoneNumber = "08012345678",
            branchId = "BRANCH_LAGOS_01",
            originatingUserUid = "USER_123"
        )
        val malformedOutbox = SyncOutboxRecord(
            branchId = cust.branchId,
            entityType = "CUSTOMER",
            entityId = "0",
            operationType = "UPSERT",
            payloadJson = "{ invalid_json_here: ", // Malformed JSON
            originatingUserUid = cust.originatingUserUid
        )

        try {
            dao.insertCustomerAndOutbox(cust, malformedOutbox)
            fail("Inserting customer with malformed JSON outbox payload should throw JSONException")
        } catch (e: org.json.JSONException) {
            // Expected exception
        } catch (e: Exception) {
            // Room transaction wrap or JSON exception
            assertTrue("Exception should be JSON related", e.cause is org.json.JSONException || e is org.json.JSONException)
        }

        // Verify atomic rollback: neither customer nor outbox record must exist
        val customerList = dao.getCustomersForBranch("BRANCH_LAGOS_01").first()
        assertTrue("Customer insert must be rolled back", customerList.isEmpty())

        val pending = dao.getPendingOutboxRecords()
        assertTrue("Outbox insert must be rolled back", pending.isEmpty())

        db.close()
    }

    @Test
    fun testStuckInProgressOutboxRecordRecovery() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, PharmacyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = db.pharmacyDao()

        val now = System.currentTimeMillis()
        val oldTimestamp = now - (10 * 60 * 1000L) // 10 minutes ago (stuck)

        val stuckRecord = SyncOutboxRecord(
            id = 1,
            branchId = "BRANCH_LAGOS_01",
            entityType = "CUSTOMER",
            entityId = "101",
            operationType = "UPSERT",
            payloadJson = "{\"name\":\"Stuck Patient\"}",
            status = "IN_PROGRESS",
            lastAttemptAt = oldTimestamp,
            originatingUserUid = "USER_123"
        )
        dao.insertOutboxRecord(stuckRecord)

        val thresholdMs = now - (5 * 60 * 1000L) // 5 minutes threshold
        val eligibleForRetry = dao.getPendingOutboxRecords(stuckThresholdMs = thresholdMs)

        assertEquals("Stuck IN_PROGRESS record older than threshold must be eligible for retry", 1, eligibleForRetry.size)
        assertEquals(1, eligibleForRetry[0].id)
        assertEquals("IN_PROGRESS", eligibleForRetry[0].status)

        db.close()
    }

    @Test
    fun testBlockedOutboxRecordNotIncludedInPendingSync() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, PharmacyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = db.pharmacyDao()

        val blockedRecord = SyncOutboxRecord(
            id = 10,
            branchId = "BRANCH_LAGOS_02",
            entityType = "CUSTOMER",
            entityId = "102",
            operationType = "UPSERT",
            payloadJson = "{\"name\":\"Blocked Patient\"}",
            status = "BLOCKED",
            lastAttemptAt = System.currentTimeMillis(),
            errorMessage = "Unauthorized branch",
            originatingUserUid = "USER_123"
        )
        dao.insertOutboxRecord(blockedRecord)

        val pending = dao.getPendingOutboxRecords()
        assertTrue("BLOCKED records must NOT be fetched for normal sync drain", pending.none { it.status == "BLOCKED" })

        db.close()
    }

    @Test
    fun testUnauthenticatedUserFailsClosedWithoutLocalNodeFallback() = runBlocking {
        val authRepo = com.example.data.auth.AuthRepository(com.example.data.auth.FirebaseAuthDataSourceImpl())
        val currentUser = authRepo.getCurrentUser()
        assertNull("Unauthenticated user must be null", currentUser)
    }
}
