package com.yohanvelasquez.recordatorios.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [RecordatorioEntity::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordatorioDao(): RecordatorioDao

    companion object {
        const val NOMBRE = "recordatorios.db"

        /**
         * v1 -> v2: cada registro pasa a tener dueño (ownerUid). Los datos de la versión 3 no tenían cuenta,
         * así que quedan con uid vacío: ninguna cuenta los ve y la app los elimina al iniciar sesión.
         */
        val MIGRACION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recordatorios ADD COLUMN ownerUid TEXT NOT NULL DEFAULT ''")
                db.execSQL("DROP INDEX IF EXISTS index_recordatorios_clientId")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_recordatorios_ownerUid_clientId ON recordatorios (ownerUid, clientId)")
            }
        }

        /** La app crea una sola instancia (en RecordatoriosApp) y la comparte. */
        fun crear(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NOMBRE)
                .addMigrations(MIGRACION_1_2)
                .build()
    }
}
