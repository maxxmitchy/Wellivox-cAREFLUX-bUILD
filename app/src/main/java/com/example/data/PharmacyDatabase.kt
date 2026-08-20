package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        InventoryItem::class,
        DailyPrescriptionVolume::class,
        CustomerAlert::class,
        Customer::class,
        CustomerMedication::class,
        ClinicalIntervention::class,
        Receipt::class,
        OperationTask::class,
        AICarousel::class,
        TriageCondition::class,
        MedicationSale::class,
        RescueListing::class,
        AdminAuditLog::class,
        InventoryBatch::class,
        OutboundSmsLog::class,
        ExpiryAlertClaim::class,
        Organization::class,
        User::class,
        UserBranchAccess::class,
        CustomerBranch::class,
        InventoryLedgerEntry::class
    ],
    version = 33,
    exportSchema = false
)
abstract class PharmacyDatabase : RoomDatabase() {
    abstract fun pharmacyDao(): PharmacyDao

    companion object {
        @Volatile
        private var INSTANCE: PharmacyDatabase? = null

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE inventory_items ADD COLUMN brand TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `ai_carousels` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `topicTitle` TEXT NOT NULL, `caption` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `slidesJson` TEXT NOT NULL, `visualTheme` TEXT NOT NULL)")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE inventory_items ADD COLUMN salesStrategy TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Safely add the nullable imageUri column to prescription_volumes table to maintain patient volume data integrity
                database.execSQL("ALTER TABLE prescription_volumes ADD COLUMN imageUri TEXT")
            }
        }

        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE customer_medications ADD COLUMN dateAdded INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_operations_tasks_isCompleted` ON `operations_tasks` (`isCompleted`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_operations_tasks_createdAt` ON `operations_tasks` (`createdAt`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_operations_tasks_category` ON `operations_tasks` (`category`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_operations_tasks_assignedToUid` ON `operations_tasks` (`assignedToUid`)")
            }
        }

        val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE inventory_items ADD COLUMN lastReconciledAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE outbound_sms_logs ADD COLUMN channel TEXT NOT NULL DEFAULT 'SMS'")
                database.execSQL("ALTER TABLE outbound_sms_logs ADD COLUMN messageType TEXT NOT NULL DEFAULT 'General'")
                database.execSQL("ALTER TABLE outbound_sms_logs ADD COLUMN twilioSid TEXT")
                database.execSQL("ALTER TABLE outbound_sms_logs ADD COLUMN costEstimate TEXT NOT NULL DEFAULT '$0.0075'")
            }
        }

        val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE inventory_items ADD COLUMN isFastMoving INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE inventory_items ADD COLUMN globalId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE inventory_items ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'SYNCED'")
            }
        }

        val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Item 3: Multi-Branch Identity & Tenancy
                database.execSQL("CREATE TABLE IF NOT EXISTS `organizations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `globalId` TEXT NOT NULL, `name` TEXT NOT NULL, `code` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)")
                database.execSQL("CREATE TABLE IF NOT EXISTS `users` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `globalId` TEXT NOT NULL, `fullName` TEXT NOT NULL, `phoneNumber` TEXT NOT NULL, `passwordHash` TEXT NOT NULL, `role` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `lastLoginAt` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)")
                database.execSQL("CREATE TABLE IF NOT EXISTS `user_branch_access` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `userId` INTEGER NOT NULL, `branchId` TEXT NOT NULL, `isPrimary` INTEGER NOT NULL, `grantedAt` INTEGER NOT NULL)")

                // Item 4: Canonical Patient Care & Customer Branch
                database.execSQL("CREATE TABLE IF NOT EXISTS `customer_branches` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `customerId` INTEGER NOT NULL, `branchId` TEXT NOT NULL, `firstSeenAt` INTEGER NOT NULL, `lastInteractionAt` INTEGER NOT NULL)")
                database.execSQL("ALTER TABLE customers ADD COLUMN globalId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE customers ADD COLUMN allergies TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE customers ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE customers ADD COLUMN lastInteractionAt INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE customer_medications ADD COLUMN canonicalProductId TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_29_30 = object : Migration(29, 30) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `inventory_ledger_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `globalId` TEXT NOT NULL, `inventoryItemId` INTEGER NOT NULL, `itemName` TEXT NOT NULL, `batchNumber` TEXT NOT NULL, `transactionType` TEXT NOT NULL, `debitAccount` TEXT NOT NULL, `creditAccount` TEXT NOT NULL, `quantity` INTEGER NOT NULL, `unitPrice` REAL NOT NULL, `totalValue` REAL NOT NULL, `referenceId` TEXT NOT NULL, `actorName` TEXT NOT NULL, `notes` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL)")
            }
        }

        val MIGRATION_30_31 = object : Migration(30, 31) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE medication_sales ADD COLUMN clientTransactionId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE medication_sales ADD COLUMN branchId TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_31_32 = object : Migration(31, 32) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE customers ADD COLUMN branchId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE customers ADD COLUMN originatingUserUid TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE customer_medications ADD COLUMN branchId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE customer_medications ADD COLUMN originatingUserUid TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE clinical_interventions ADD COLUMN branchId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE clinical_interventions ADD COLUMN originatingUserUid TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE inventory_items ADD COLUMN branchId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE inventory_items ADD COLUMN originatingUserUid TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE medication_sales ADD COLUMN originatingUserUid TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_32_33 = object : Migration(32, 33) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE operations_tasks ADD COLUMN branchId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE operations_tasks ADD COLUMN originatingUserUid TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE receipts ADD COLUMN branchId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE receipts ADD COLUMN originatingUserUid TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getDatabase(context: Context): PharmacyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PharmacyDatabase::class.java,
                    "pharmacy_database"
                )
                .addMigrations(MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_17_18, MIGRATION_22_23, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28, MIGRATION_28_29, MIGRATION_29_30, MIGRATION_30_31, MIGRATION_31_32, MIGRATION_32_33)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
