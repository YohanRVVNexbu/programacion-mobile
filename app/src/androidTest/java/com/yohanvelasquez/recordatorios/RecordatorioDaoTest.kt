package com.yohanvelasquez.recordatorios

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yohanvelasquez.recordatorios.data.local.AppDatabase
import com.yohanvelasquez.recordatorios.data.local.RecordatorioDao
import com.yohanvelasquez.recordatorios.data.local.RecordatorioEntity
import com.yohanvelasquez.recordatorios.model.EstadoSync
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Prueba del DAO con Room de verdad (SQLite del emulador) sobre una base en memoria. */
@RunWith(AndroidJUnit4::class)
class RecordatorioDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: RecordatorioDao

    @Before
    fun abrir() {
        val contexto = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(contexto, AppDatabase::class.java).build()
        dao = db.recordatorioDao()
    }

    @After
    fun cerrar() = db.close()

    private fun nuevo(clientId: String, titulo: String = "Comprar pan", prioridad: String = "BAJA", estado: String = "PENDIENTE", t: Long = 1_000) =
        RecordatorioEntity(clientId = clientId, titulo = titulo, categoria = "Hogar", prioridad = prioridad, estado = estado, creadoEn = t, actualizadoEn = t)

    @Test
    fun insertarConsultarActualizarYEliminar() = runBlocking {
        val id = dao.insertar(nuevo("a")).toInt()
        assertEquals("Comprar pan", dao.observarPorId(id).first()!!.titulo)

        dao.actualizar(dao.obtenerPorId(id)!!.copy(titulo = "Comprar pan integral"))
        assertEquals("Comprar pan integral", dao.obtenerPorId(id)!!.titulo)

        dao.eliminar(dao.obtenerPorId(id)!!)
        assertNull(dao.obtenerPorId(id))
        assertEquals(0, dao.observarCantidad().first())
    }

    @Test
    fun consultasConFiltro() = runBlocking {
        dao.insertar(nuevo("a", "Uno", prioridad = "ALTA", estado = "PENDIENTE", t = 1))
        dao.insertar(nuevo("b", "Dos", prioridad = "BAJA", estado = "COMPLETADO", t = 2))
        dao.insertar(nuevo("c", "Tres", prioridad = "ALTA", estado = "COMPLETADO", t = 3))

        assertEquals(listOf("Tres", "Dos", "Uno"), dao.observarTodos().first().map { it.titulo }) // más recientes primero
        assertEquals(listOf("Tres", "Dos"), dao.observarPorEstado("COMPLETADO").first().map { it.titulo })
        assertEquals(listOf("Tres", "Uno"), dao.observarPorPrioridad("ALTA").first().map { it.titulo })
        assertEquals(3, dao.observarPorCategoria("Hogar").first().size)
    }

    @Test
    fun noPermiteDosRegistrosConElMismoClientId() = runBlocking {
        val primero = dao.insertar(nuevo("igual"))
        val segundo = dao.insertar(nuevo("igual", "Otro título"))
        assertEquals(-1L, segundo)
        assertEquals(1, dao.observarCantidad().first())
        assertNotNull(dao.buscarPorClientId("igual"))
        assertEquals(primero.toInt(), dao.buscarPorClientId("igual")!!.id)
    }

    @Test
    fun borradoLogicoYSincronizacion() = runBlocking {
        val id = dao.insertar(nuevo("a", t = 1_000)).toInt()
        // Se envía y el servicio confirma con la misma versión: queda sincronizado.
        dao.marcarTrasEnvio(id, "77", version = 1_000)
        assertEquals(EstadoSync.SINCRONIZADO, dao.obtenerPorId(id)!!.syncEstado)
        assertEquals("77", dao.buscarPorRemoteId("77")!!.remoteId)

        // Si cambió mientras se enviaba (otra versión), sigue pendiente.
        dao.actualizar(dao.obtenerPorId(id)!!.copy(syncEstado = EstadoSync.PENDIENTE, actualizadoEn = 2_000))
        dao.marcarTrasEnvio(id, "77", version = 1_000)
        assertEquals(EstadoSync.PENDIENTE, dao.obtenerPorId(id)!!.syncEstado)

        dao.marcarEliminado(id, 3_000)
        assertEquals(0, dao.observarTodos().first().size)      // ya no se ve
        assertEquals(1, dao.obtenerPendientes().size)          // pero falta confirmarlo con el servicio
        dao.eliminarDefinitivo(id)
        assertEquals(0, dao.obtenerPendientes().size)
    }
}
