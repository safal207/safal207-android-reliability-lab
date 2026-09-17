package com.safal207.androidreliabilitylab.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [IncidentEntity::class], version = 1, exportSchema = false)
abstract class IncidentDatabase : RoomDatabase() {
    abstract fun incidentDao(): IncidentDao

    companion object {
        const val FILE_NAME = "incidents.db"

        fun open(context: Context, name: String = FILE_NAME): IncidentDatabase =
            Room.databaseBuilder(context.applicationContext, IncidentDatabase::class.java, name)
                .build()
    }
}
