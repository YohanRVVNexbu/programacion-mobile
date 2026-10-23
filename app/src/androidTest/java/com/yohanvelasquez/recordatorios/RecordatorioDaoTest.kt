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
    private val ana = "uid-ana"
    private val beto = "uid-beto"

    @Before
    fun abrir() {
        val contexto = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(contexto, AppDatabase::class.java).build()
        dao = db.recordatorioDao()
    }

    @After
    fun cerrar() = db.close()

    private fun nuevo(
        clientId: String, titulo: String = "Comprar pan", prioridad: String = "BAJA", estado: String = "PENDIENTE",
        t: Long = 1_000, uid: String = ana,
    ) = RecordatorioEntity(ownerUid = uid, clientId = clientId, titulo = titulo, categoria = "Hogar", prioridad = prioridad, estado = estado, creadoEn = t, actualizadoEn = t)

    @Test
    fun insertarConsultarActualizarYEliminar() = runBlocking {
        val id = dao.insertar(nuevo("a")).toInt()
        assertEquals("Comprar pan", dao.observarPorId(ana, id).first()!!.titulo)

        dao.actualizar(dao.obtenerPorId(ana, id)!!.copy(titulo = "Comprar pan integral"))
        assertEquals("Comprar pan integral", dao.obtenerPorId(ana, id)!!.titulo)

        dao.eliminar(dao.obtenerPorId(ana, id)!!)
        assertNull(dao.obtenerPorId(ana, id))
        assertEquals(0, dao.observarCantidad(ana).first())
    }

    @Test
    fun consultasConFiltro() = runBlocking {
        dao.insertar(nuevo("a", "Uno", prioridad = "ALTA", estado = "PENDIENTE", t = 1))
        dao.insertar(nuevo("b", "Dos", prioridad = "BAJA", estado = "COMPLETADO", t = 2))
        dao.insertar(nuevo("c", "Tres", prioridad = "ALTA", estado = "COMPLETADO", t = 3))

        assertEquals(listOf("Tres", "Dos", "Uno"), dao.observarTodos(ana).first().map { it.titulo }) // más recientes primero
        assertEquals(listOf("Tres", "Dos"), dao.observarPorEstado(ana, "COMPLETADO").first().map { it.titulo })
        assertEquals(listOf("Tres", "Uno"), dao.observarPorPrioridad(ana, "ALTA").first().map { it.titulo })
        assertEquals(3, dao.observarPorCategoria(ana, "Hogar").first().size)
    }

    @Test
    fun cadaCuentaSoloVeSusRegistros() = runBlocking {
        val idAna = dao.insertar(nuevo("a", "De Ana", uid = ana)).toInt()
        dao.insertar(nuevo("b", "De Beto", uid = beto))

        assertEquals(listOf("De Ana"), dao.observarTodos(ana).first().map { it.titulo })
        assertEquals(listOf("De Beto"), dao.observarTodos(beto).first().map { it.titulo })
        assertNull(dao.obtenerPorId(beto, idAna))       // el id existe, pero es de otra cuenta
        assertNull(dao.observarPorId(beto, idAna).first())
        assertEquals(0, dao.observarPorEstado(beto, "PENDIENTE").first().count { it.titulo == "De Ana" })

        dao.eliminarTodoDe(ana)                          // cerrar sesión de Ana
        assertEquals(0, dao.observarCantidad(ana).first())
        assertEquals(1, dao.observarCantidad(beto).first()) // lo de Beto no se toca
    }

    @Test
    fun elMismoClientIdPuedeExistirEnCuentasDistintasPeroNoEnLaMisma() = runBlocking {
        val primero = dao.insertar(nuevo("igual", uid = ana))
        val repetido = dao.insertar(nuevo("igual", "Otro título", uid = ana))
        val otraCuenta = dao.insertar(nuevo("igual", uid = beto))
        assertEquals(-1L, repetido)                      // no se duplica dentro de la cuenta
        assertNotNull(otraCuenta.takeIf { it > 0 })      // otra cuenta sí puede usar el mismo id de documento
        assertEquals(1, dao.observarCantidad(ana).first())
        assertEquals(primero.toInt(), dao.buscarPorClientId(ana, "igual")!!.id)
    }

    @Test
    fun borradoLogicoYSincronizacion() = runBlocking {
        val id = dao.insertar(nuevo("a", t = 1_000)).toInt()
        dao.marcarTrasEnvio(id, "77", version = 1_000)
        assertEquals(EstadoSync.SINCRONIZADO, dao.obtenerPorId(ana, id)!!.syncEstado)
        assertEquals("77", dao.buscarPorRemoteId(ana, "77")!!.remoteId)
        assertEquals(0, dao.observarSinSincronizar(ana).first())

        // Si cambió mientras se enviaba (otra versión), sigue pendiente.
        dao.actualizar(dao.obtenerPorId(ana, id)!!.copy(syncEstado = EstadoSync.PENDIENTE, actualizadoEn = 2_000))
        dao.marcarTrasEnvio(id, "77", version = 1_000)
        assertEquals(EstadoSync.PENDIENTE, dao.obtenerPorId(ana, id)!!.syncEstado)
        assertEquals(1, dao.observarSinSincronizar(ana).first())

        dao.marcarEliminado(id, 3_000)
        assertEquals(0, dao.observarTodos(ana).first().size)   // ya no se ve
        assertEquals(1, dao.obtenerPendientes(ana).size)       // pero falta confirmarlo con el servicio
        dao.eliminarDefinitivo(id)
        assertEquals(0, dao.obtenerPendientes(ana).size)
    }
}
