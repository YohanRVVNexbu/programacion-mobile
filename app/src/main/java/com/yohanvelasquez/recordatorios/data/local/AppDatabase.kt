package com.yohanvelasquez.recordatorios.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [RecordatorioEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordatorioDao(): RecordatorioDao

    companion object {
        const val NOMBRE = "recordatorios.db"

        /** La app crea una sola instancia (en RecordatoriosApp) y la comparte. */
        fun crear(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NOMBRE).build()
    }
}
