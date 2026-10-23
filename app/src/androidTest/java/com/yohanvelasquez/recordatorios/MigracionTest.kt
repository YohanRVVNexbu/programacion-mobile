package com.yohanvelasquez.recordatorios

import android.database.sqlite.SQLiteConstraintException
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yohanvelasquez.recordatorios.data.local.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Prueba la migración de la base v1 (Actividad 3, sin cuentas) a la v2 (con ownerUid) usando los esquemas exportados. */
@RunWith(AndroidJUnit4::class)
class MigracionTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun migraDeLaVersion1ALa2ConservandoLosDatos() {
        helper.createDatabase("migracion-test", 1).apply {
            execSQL(
                "INSERT INTO recordatorios (clientId, titulo, categoria, prioridad, estado, creadoEn, actualizadoEn, syncEstado, eliminado) " +
                    "VALUES ('c1', 'Comprar pan', 'Hogar', 'BAJA', 'PENDIENTE', 1, 1, 'PENDIENTE', 0)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate("migracion-test", 2, true, AppDatabase.MIGRACION_1_2)

        db.query("SELECT ownerUid, titulo, clientId FROM recordatorios").use {
            assertEquals(1, it.count)
            it.moveToFirst()
            assertEquals("", it.getString(0))              // sin dueño: ninguna cuenta lo ve
            assertEquals("Comprar pan", it.getString(1))   // el dato se conserva
            assertEquals("c1", it.getString(2))
        }

        // El índice nuevo es (ownerUid, clientId): se repite el id en otra cuenta pero no dentro de la misma.
        val insertar = "INSERT INTO recordatorios (ownerUid, clientId, titulo, categoria, prioridad, estado, creadoEn, actualizadoEn, syncEstado, eliminado) " +
            "VALUES (?, 'c1', 'Otro', 'Hogar', 'BAJA', 'PENDIENTE', 1, 1, 'PENDIENTE', 0)"
        db.execSQL(insertar, arrayOf("uid-ana"))
        try {
            db.execSQL(insertar, arrayOf("uid-ana"))
            fail("debía rechazar un clientId repetido dentro de la misma cuenta")
        } catch (e: SQLiteConstraintException) { /* esperado */ }
        db.close()
    }
}
