package com.safal207.androidreliabilitylab.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [IncidentEntity::class, PendingMutationEntity::class, MutationReceiptEntity::class], version = 3, exportSchema = false)
abstract class IncidentDatabase : RoomDatabase() {
    abstract fun incidentDao(): IncidentDao
    abstract fun pendingMutationDao(): PendingMutationDao

    companion object {
        const val FILE_NAME = "incidents.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS pending_mutations (
                    mutationId TEXT NOT NULL PRIMARY KEY,
                    incidentId TEXT NOT NULL,
                    targetStatus TEXT NOT NULL,
                    createdOrder INTEGER NOT NULL
                )""".trimIndent())
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS mutation_receipts (
                    actionId TEXT NOT NULL PRIMARY KEY,
                    incidentId TEXT NOT NULL,
                    targetStatus TEXT NOT NULL,
                    effectId TEXT NOT NULL,
                    receiptVersion INTEGER NOT NULL,
                    effectSequence INTEGER NOT NULL
                )""".trimIndent())
            }
        }

        fun open(context: Context, name: String = FILE_NAME): IncidentDatabase =
            Room.databaseBuilder(context.applicationContext, IncidentDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
