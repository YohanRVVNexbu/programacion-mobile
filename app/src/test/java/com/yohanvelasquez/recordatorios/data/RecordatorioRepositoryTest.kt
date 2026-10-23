package com.yohanvelasquez.recordatorios.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.yohanvelasquez.recordatorios.data.preferencias.OrdenLista
import com.yohanvelasquez.recordatorios.data.preferencias.PreferenciasRepository
import com.yohanvelasquez.recordatorios.data.sync.Sincronizador
import com.yohanvelasquez.recordatorios.data.ServidorFalso.Modo
import com.yohanvelasquez.recordatorios.model.Categoria
import com.yohanvelasquez.recordatorios.model.EstadoRecordatorio
import com.yohanvelasquez.recordatorios.model.EstadoSync
import com.yohanvelasquez.recordatorios.model.FiltroEstado
import com.yohanvelasquez.recordatorios.model.Prioridad
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RecordatorioRepositoryTest {
    @get:Rule val carpeta = TemporaryFolder()

    private class Entorno(
        val dao: DaoEnMemoria, val servidor: ServidorFalso, val repo: RecordatorioRepositoryLocalFirst,
        val prefs: PreferenciasRepository, val uid: MutableStateFlow<String?>,
    )

    private fun TestScope.entorno(): Entorno {
        val dao = DaoEnMemoria(); val servidor = ServidorFalso()
        val prefs = PreferenciasRepository(PreferenceDataStoreFactory.create(scope = backgroundScope) { File(carpeta.root, "prefs.preferences_pb") })
        val uid = MutableStateFlow<String?>(UID)
        var tic = 1_000L
        val repo = RecordatorioRepositoryLocalFirst(
            dao, prefs, Sincronizador(dao, servidor, { uid.value }, backgroundScope), uid, reloj = { tic++ }, nuevoClientId = { "c${tic}" },
        )
        return Entorno(dao, servidor, repo, prefs, uid)
    }

    @Test
    fun `crear sin conexion guarda en Room como pendiente y la lista lo muestra`() = runTest(UnconfinedTestDispatcher()) {
        val e = entorno(); e.servidor.modo = Modo.SIN_CONEXION
        val id = e.repo.crear("Llamar al banco", "Pedir estado de cuenta", Categoria.Trabajo, Prioridad.Alta)
        val lista = e.repo.observarRecordatorios().first()
        assertEquals(listOf("Llamar al banco"), lista.items.map { it.titulo })
        assertEquals(EstadoSync.PENDIENTE, lista.items.single().sincronizacion)
        assertEquals(id, lista.items.single().id)
        assertTrue(lista.hayRegistros)
    }

    @Test
    fun `actualizar y cambiar completado dejan el registro pendiente`() = runTest(UnconfinedTestDispatcher()) {
        val e = entorno(); e.servidor.modo = Modo.SIN_CONEXION
        val id = e.repo.crear("Comprar pan", null, Categoria.Hogar, Prioridad.Baja)
        e.dao.marcarTrasEnvio(id, "9", e.dao.obtenerPorId(UID, id)!!.actualizadoEn) // simula que ya estaba sincronizado
        assertEquals(EstadoSync.SINCRONIZADO, e.repo.obtenerRecordatorio(id)!!.sincronizacion)

        e.repo.actualizar(id, "Comprar pan integral", "Sin gluten", Categoria.Hogar, Prioridad.Media)
        e.repo.cambiarCompletado(id, true)
        val r = e.repo.obtenerRecordatorio(id)!!
        assertEquals("Comprar pan integral", r.titulo)
        assertEquals(Prioridad.Media, r.prioridad)
        assertTrue(r.estado is EstadoRecordatorio.Completado)
        assertEquals(EstadoSync.PENDIENTE, r.sincronizacion)

        e.repo.cambiarCompletado(id, false)
        assertEquals(EstadoRecordatorio.Pendiente, e.repo.obtenerRecordatorio(id)!!.estado)
    }

    @Test
    fun `eliminar saca el registro de las consultas`() = runTest(UnconfinedTestDispatcher()) {
        val e = entorno(); e.servidor.modo = Modo.SIN_CONEXION
        val id = e.repo.crear("Comprar pan", null, Categoria.Hogar, Prioridad.Baja)
        e.repo.eliminar(id)
        assertNull(e.repo.obtenerRecordatorio(id))
        assertNull(e.repo.observarRecordatorio(id).first())
        assertFalse(e.repo.observarRecordatorios().first().hayRegistros)
    }

    @Test
    fun `las preferencias de DataStore afectan al listado`() = runTest(UnconfinedTestDispatcher()) {
        val e = entorno(); e.servidor.modo = Modo.SIN_CONEXION
        val a = e.repo.crear("Zeta tarea", null, Categoria.Hogar, Prioridad.Baja)
        e.repo.crear("Alfa tarea", null, Categoria.Hogar, Prioridad.Alta)
        e.repo.cambiarCompletado(a, true)

        assertEquals(listOf("Alfa tarea", "Zeta tarea"), e.repo.observarRecordatorios().first().items.map { it.titulo }) // por prioridad

        e.prefs.guardarOrden(OrdenLista.TITULO)
        e.prefs.guardarMostrarCompletados(false)
        val lista = e.repo.observarRecordatorios().first()
        assertEquals(listOf("Alfa tarea"), lista.items.map { it.titulo })
        assertTrue(lista.hayRegistros) // hay registros aunque estén ocultos

        assertEquals(1, e.repo.observarRecordatorios(FiltroEstado.PENDIENTE).first().items.size)
        assertEquals(0, e.repo.observarRecordatorios(FiltroEstado.COMPLETADO).first().items.size)
    }
}
