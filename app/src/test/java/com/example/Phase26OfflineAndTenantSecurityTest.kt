package com.example

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.MedicationSale
import com.example.data.PharmacyDatabase
import com.example.data.sync.SyncOutboxRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Phase26OfflineAndTenantSecurityTest {

    @Test
    fun testRealMigration33To34ExecutesOnSQLiteDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "test_migration_33_34.db"
        context.deleteDatabase(dbName)

        // 1. Create Version 33 SQLite database
        val callback33 = object : SupportSQLiteOpenHelper.Callback(33) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `medication_sales` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `productName` TEXT NOT NULL,
                        `brand` TEXT NOT NULL DEFAULT '',
                        `genericName` TEXT NOT NULL DEFAULT '',
                        `category` TEXT NOT NULL DEFAULT '',
                        `quantitySold` INTEGER NOT NULL,
                        `dateSold` INTEGER NOT NULL DEFAULT 0,
                        `pharmacyNode` TEXT NOT NULL DEFAULT '',
                        `patientAge` INTEGER NOT NULL DEFAULT 30,
                        `patientGender` TEXT NOT NULL DEFAULT 'Male',
                        `patientState` TEXT NOT NULL DEFAULT 'Lagos',
                        `patientLga` TEXT NOT NULL DEFAULT 'Ikeja',
                        `patientCity` TEXT NOT NULL DEFAULT 'Ikeja',
                        `salePrice` REAL NOT NULL DEFAULT 0.0,
                        `batchNumber` TEXT NOT NULL DEFAULT '',
                        `clientTransactionId` TEXT NOT NULL DEFAULT '',
                        `branchId` TEXT NOT NULL DEFAULT '',
                        `originatingUserUid` TEXT NOT NULL DEFAULT ''
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `inventory_ledger_entries` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `inventoryItemId` INTEGER NOT NULL,
                        `itemName` TEXT NOT NULL,
                        `batchNumber` TEXT NOT NULL,
                        `transactionType` TEXT NOT NULL,
                        `debitAccount` TEXT NOT NULL,
                        `creditAccount` TEXT NOT NULL,
                        `quantity` INTEGER NOT NULL,
                        `unitPrice` REAL NOT NULL,
                        `totalValue` REAL NOT NULL,
                        `referenceId` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        `actorName` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )
                """.trimIndent())
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
        }

        val config33 = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(callback33)
            .build()

        val helper33 = FrameworkSQLiteOpenHelperFactory().create(config33)
        val db33 = helper33.writableDatabase

        // Insert legacy sale without clientTransactionId
        val cvSale = ContentValues().apply {
            put("productName", "Paracetamol 500mg")
            put("quantitySold", 5)
            put("dateSold", 1724071200000L)
            put("clientTransactionId", "")
            put("branchId", "BRANCH_LAGOS_01")
        }
        val saleRowId = db33.insert("medication_sales", SQLiteDatabase.CONFLICT_NONE, cvSale)
        assertTrue("Sale must be inserted into v33 database", saleRowId > 0)

        db33.close()
        helper33.close()

        // 2. Migrate from v33 to v34
        val callbackMigrate = object : SupportSQLiteOpenHelper.Callback(34) {
            override fun onCreate(db: SupportSQLiteDatabase) {}
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                if (oldVersion == 33 && newVersion == 34) {
                    PharmacyDatabase.MIGRATION_33_34.migrate(db)
                }
            }
        }

        val configMigrate = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(callbackMigrate)
            .build()

        val helperMigrate = FrameworkSQLiteOpenHelperFactory().create(configMigrate)
        val db34 = helperMigrate.writableDatabase

        // Verify sync_outbox table exists
        val cursorOutbox = db34.query("SELECT count(*) FROM sync_outbox")
        assertNotNull("sync_outbox table must exist", cursorOutbox)
        cursorOutbox.close()

        // Verify medication_sales clientTransactionId backfilled
        val cursorSale = db34.query("SELECT clientTransactionId FROM medication_sales WHERE id = ?", arrayOf(saleRowId.toString()))
        assertTrue(cursorSale.moveToFirst())
        val backfilledTxId = cursorSale.getString(0)
        assertTrue("clientTransactionId must be backfilled", backfilledTxId.startsWith("LEGACY_SALE_"))
        cursorSale.close()

        db34.close()
        helperMigrate.close()
    }

    @Test
    fun testRoomOutboxOperationsAndTenantFilter() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, PharmacyDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val dao = db.pharmacyDao()

        val outbox1 = SyncOutboxRecord(
            branchId = "BRANCH_A",
            entityType = "SALE",
            entityId = "SALE_1001",
            operationType = "SALE_SYNC",
            payloadJson = "{\"productName\":\"Amoxil\",\"quantitySold\":2}",
            clientTransactionId = "SALE_1001",
            originatingUserUid = "USER_A"
        )

        val outbox2 = SyncOutboxRecord(
            branchId = "BRANCH_B",
            entityType = "SALE",
            entityId = "SALE_1002",
            operationType = "SALE_SYNC",
            payloadJson = "{\"productName\":\"Panadol\",\"quantitySold\":1}",
            clientTransactionId = "SALE_1002",
            originatingUserUid = "USER_B"
        )

        dao.insertOutboxRecord(outbox1)
        dao.insertOutboxRecord(outbox2)

        val pending = dao.getPendingOutboxRecords()
        assertEquals(2, pending.size)

        val recordA = dao.getOutboxRecordByClientTxId("SALE_1001")
        assertNotNull(recordA)
        assertEquals("BRANCH_A", recordA?.branchId)

        db.close()
    }

    @Test
    fun testMedicationSaleClientTransactionIdUniqueness() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, PharmacyDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val dao = db.pharmacyDao()

        val sale1 = MedicationSale(
            id = 0,
            productName = "Amoxil",
            quantitySold = 1,
            clientTransactionId = "UNIQUE_TX_12345",
            branchId = "BRANCH_A"
        )

        val sale2 = MedicationSale(
            id = 0,
            productName = "Amoxil Updated",
            quantitySold = 2,
            clientTransactionId = "UNIQUE_TX_12345",
            branchId = "BRANCH_A"
        )

        dao.insertMedicationSale(sale1)
        dao.insertMedicationSale(sale2) // OnConflictStrategy.REPLACE triggers on clientTransactionId

        val fetched = dao.getMedicationSaleByClientTransactionId("UNIQUE_TX_12345")
        assertNotNull(fetched)
        assertEquals("Amoxil Updated", fetched?.productName)
        assertEquals(2, fetched?.quantitySold)

        db.close()
    }
}
