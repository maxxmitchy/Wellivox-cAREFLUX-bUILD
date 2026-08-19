package com.example

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.PharmacyDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RealRoomMigrationTest {

    @Test
    fun testRealMigration30To31ExecutesOnSQLiteDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "test_migration_30_31.db"
        context.deleteDatabase(dbName)

        // 1. Create Version 30 SQLite database
        val callback30 = object : SupportSQLiteOpenHelper.Callback(30) {
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
                        `batchNumber` TEXT NOT NULL DEFAULT ''
                    )
                """.trimIndent())
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
        }

        val config30 = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(callback30)
            .build()

        val helper30 = FrameworkSQLiteOpenHelperFactory().create(config30)
        val db30 = helper30.writableDatabase

        // 2. Insert realistic medication_sales row in Version 30 schema
        val cv = ContentValues().apply {
            put("productName", "Amoxicillin 500mg Capsule")
            put("brand", "Amoxil")
            put("genericName", "Amoxicillin")
            put("category", "Antibiotic")
            put("quantitySold", 10)
            put("dateSold", 1724071200000L)
            put("pharmacyNode", "MAIN_COUNTER")
            put("salePrice", 1500.0)
            put("batchNumber", "BATCH2026A")
        }
        val insertedRowId = db30.insert("medication_sales", SQLiteDatabase.CONFLICT_NONE, cv)
        assertTrue("Row must be inserted into v30 database", insertedRowId > 0)

        // 3. Close v30 database
        db30.close()
        helper30.close()

        // 4. Reopen and execute MIGRATION_30_31 against the database
        val callbackMigrate = object : SupportSQLiteOpenHelper.Callback(31) {
            override fun onCreate(db: SupportSQLiteDatabase) {}
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                if (oldVersion == 30 && newVersion == 31) {
                    PharmacyDatabase.MIGRATION_30_31.migrate(db)
                }
            }
        }

        val configMigrate = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(callbackMigrate)
            .build()

        val helperMigrate = FrameworkSQLiteOpenHelperFactory().create(configMigrate)
        val db31 = helperMigrate.writableDatabase

        // 5. Verify schema and data persistence in Version 31
        val cursor = db31.query("SELECT * FROM medication_sales WHERE id = ?", arrayOf(insertedRowId))
        assertTrue("Inserted row must exist after migration", cursor.moveToFirst())

        val nameCol = cursor.getColumnIndex("productName")
        val priceCol = cursor.getColumnIndex("salePrice")
        val qtyCol = cursor.getColumnIndex("quantitySold")
        val clientTxIdCol = cursor.getColumnIndex("clientTransactionId")
        val branchIdCol = cursor.getColumnIndex("branchId")

        assertTrue("clientTransactionId column must exist", clientTxIdCol >= 0)
        assertTrue("branchId column must exist", branchIdCol >= 0)

        assertEquals("Amoxicillin 500mg Capsule", cursor.getString(nameCol))
        assertEquals(1500.0, cursor.getDouble(priceCol), 0.001)
        assertEquals(10, cursor.getInt(qtyCol))
        assertEquals("", cursor.getString(clientTxIdCol)) // Default value
        assertEquals("", cursor.getString(branchIdCol))       // Default value

        cursor.close()
        db31.close()
        helperMigrate.close()

        // 6. Verify Room 31 Schema and DAO compatibility
        val roomDb = Room.inMemoryDatabaseBuilder(context, PharmacyDatabase::class.java).build()
        runBlocking {
            roomDb.pharmacyDao().insertMedicationSale(
                com.example.data.MedicationSale(
                    productName = "Amoxicillin 500mg Capsule",
                    quantitySold = 10,
                    salePrice = 1500.0,
                    clientTransactionId = "TX_123",
                    branchId = "BR_01"
                )
            )
            val sales = roomDb.pharmacyDao().getAllMedicationSales().first()
            assertNotNull("Sales list must not be null", sales)
            assertTrue("Room DAO must find the inserted sale record", sales.isNotEmpty())
            val sale = sales.first()
            assertEquals("Amoxicillin 500mg Capsule", sale.productName)
            assertEquals("TX_123", sale.clientTransactionId)
            assertEquals("BR_01", sale.branchId)
        }
        roomDb.close()
    }

    @Test
    fun testRealMigrationChain29To30To31Executes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "test_migration_chain.db"
        context.deleteDatabase(dbName)

        val callback29 = object : SupportSQLiteOpenHelper.Callback(29) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `medication_sales` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `productName` TEXT NOT NULL,
                        `quantitySold` INTEGER NOT NULL,
                        `salePrice` REAL NOT NULL DEFAULT 0.0
                    )
                """.trimIndent())
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
        }

        val config29 = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(callback29)
            .build()

        val helper29 = FrameworkSQLiteOpenHelperFactory().create(config29)
        val db29 = helper29.writableDatabase

        val cv = ContentValues().apply {
            put("productName", "Paracetamol Extra")
            put("quantitySold", 2)
            put("salePrice", 300.0)
        }
        val rowId = db29.insert("medication_sales", SQLiteDatabase.CONFLICT_NONE, cv)
        assertTrue(rowId > 0)
        db29.close()
        helper29.close()

        // Run MIGRATION_29_30 and MIGRATION_30_31 in chain
        val callbackChain = object : SupportSQLiteOpenHelper.Callback(31) {
            override fun onCreate(db: SupportSQLiteDatabase) {}
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                if (oldVersion < 30) {
                    PharmacyDatabase.MIGRATION_29_30.migrate(db)
                }
                if (oldVersion < 31) {
                    PharmacyDatabase.MIGRATION_30_31.migrate(db)
                }
            }
        }

        val configChain = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(callbackChain)
            .build()

        val helperChain = FrameworkSQLiteOpenHelperFactory().create(configChain)
        val dbChain = helperChain.writableDatabase

        // Verify ledger table created by 29->30
        val ledgerCursor = dbChain.query("SELECT name FROM sqlite_master WHERE type='table' AND name='inventory_ledger_entries'")
        assertTrue("inventory_ledger_entries table must exist after 29->30 migration", ledgerCursor.moveToFirst())
        ledgerCursor.close()

        // Verify medication_sales columns added by 30->31
        val saleCursor = dbChain.query("SELECT * FROM medication_sales WHERE id = ?", arrayOf(rowId))
        assertTrue(saleCursor.moveToFirst())
        assertTrue("clientTransactionId exists", saleCursor.getColumnIndex("clientTransactionId") >= 0)
        assertTrue("branchId exists", saleCursor.getColumnIndex("branchId") >= 0)
        saleCursor.close()

        dbChain.close()
        helperChain.close()
    }
}
