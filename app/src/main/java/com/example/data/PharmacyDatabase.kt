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
        ExpiryAlertClaim::class
    ],
    version = 27,
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

        fun getDatabase(context: Context): PharmacyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PharmacyDatabase::class.java,
                    "pharmacy_database"
                )
                .addMigrations(MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_17_18, MIGRATION_22_23, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
