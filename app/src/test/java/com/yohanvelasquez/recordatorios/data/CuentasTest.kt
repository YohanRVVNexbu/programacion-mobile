package com.yohanvelasquez.recordatorios.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.yohanvelasquez.recordatorios.data.ServidorFalso.Modo
import com.yohanvelasquez.recordatorios.data.preferencias.PreferenciasRepository
import com.yohanvelasquez.recordatorios.data.remote.ErrorRed
import com.yohanvelasquez.recordatorios.data.sync.EstadoSincronizacion
import com.yohanvelasquez.recordatorios.data.sync.Sincronizador
import com.yohanvelasquez.recordatorios.model.Categoria
import com.yohanvelasquez.recordatorios.model.EstadoSync
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
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Aislamiento entre cuentas: lo de una cuenta nunca lo ve ni lo modifica otra, aunque compartan el dispositivo. */
class CuentasTest {
    @get:Rule val carpeta = TemporaryFolder()

    private class Entorno(val dao: DaoEnMemoria, val servidor: ServidorFalso, val repo: RecordatorioRepositoryLocalFirst, val uid: MutableStateFlow<String?>)

    private fun TestScope.entorno(): Entorno {
        val dao = DaoEnMemoria(); val servidor = ServidorFalso()
        val prefs = PreferenciasRepository(PreferenceDataStoreFactory.create(scope = backgroundScope) { File(carpeta.root, "p.preferences_pb") })
        val uid = MutableStateFlow<String?>(UID)
        var tic = 1_000L
        val repo = RecordatorioRepositoryLocalFirst(
            dao, prefs, Sincronizador(dao, servidor, { uid.value }, backgroundScope), uid, reloj = { tic++ }, nuevoClientId = { "c${tic}" },
        )
        servidor.modo = Modo.SIN_CONEXION // sin red: así se ve solo lo local
        return Entorno(dao, servidor, repo, uid)
    }

    @Test
    fun `una cuenta nueva no ve los recordatorios de la anterior`() = runTest(UnconfinedTestDispatcher()) {
        val e = entorno()
        val idDeAna = e.repo.crear("Llamar al banco", null, Categoria.Trabajo, Prioridad.Alta)
        assertEquals(1, e.repo.observarRecordatorios().first().items.size)

        e.uid.value = OTRO_UID
        val listaBeto = e.repo.observarRecordatorios().first()
        assertTrue(listaBeto.items.isEmpty())
        assertFalse(listaBeto.hayRegistros)
        assertNull(e.repo.obtenerRecordatorio(idDeAna))
        assertNull(e.repo.observarRecordatorio(idDeAna).first())
        assertEquals(0, e.repo.observarSinSincronizar().first())
    }

    @Test
    fun `una cuenta no puede modificar ni borrar registros de otra`() = runTest(UnconfinedTestDispatcher()) {
        val e = entorno()
        val idDeAna = e.repo.crear("Llamar al banco", null, Categoria.Trabajo, Prioridad.Alta)

        e.uid.value = OTRO_UID
        e.repo.actualizar(idDeAna, "Cambiado por Beto", null, Categoria.Hogar, Prioridad.Baja)
        e.repo.cambiarCompletado(idDeAna, true)
        e.repo.eliminar(idDeAna)

        e.uid.value = UID
        val intacto = e.repo.obtenerRecordatorio(idDeAna)!!
        assertEquals("Llamar al banco", intacto.titulo)
        assertEquals(Prioridad.Alta, intacto.prioridad)
        assertEquals(0, e.dao.filas.value.count { it.eliminado })
    }

    @Test
    fun `cerrar sesion borra solo los datos de esa cuenta`() = runTest(UnconfinedTestDispatcher()) {
        val e = entorno()
        e.repo.crear("De Ana uno", null, Categoria.Trabajo, Prioridad.Alta)
        e.repo.crear("De Ana dos", null, Categoria.Hogar, Prioridad.Baja)
        e.uid.value = OTRO_UID
        e.repo.crear("De Beto", null, Categoria.Salud, Prioridad.Media)

        e.repo.limpiarDatosLocales() // cierra la cuenta de Beto
        assertEquals(listOf(UID), e.dao.filas.value.map { it.ownerUid }.distinct())
        assertEquals(2, e.dao.filas.value.size)
    }

    @Test
    fun `sin sesion no se puede crear y el listado queda vacio`() = runTest(UnconfinedTestDispatcher()) {
        val e = entorno()
        e.uid.value = null
        assertFalse(e.repo.observarRecordatorios().first().hayRegistros)
        try {
            e.repo.crear("Sin cuenta", null, Categoria.Hogar, Prioridad.Baja)
            fail("debía lanzar SinSesionException")
        } catch (ex: SinSesionException) { /* esperado */ }
        assertEquals(0, e.dao.filas.value.size)
    }

    @Test
    fun `los datos anteriores a las cuentas se descartan`() = runTest(UnconfinedTestDispatcher()) {
        val e = entorno()
        e.dao.insertar(entidad("viejo", uid = ""))
        e.repo.crear("De Ana", null, Categoria.Trabajo, Prioridad.Alta)
        e.repo.descartarDatosSinCuenta()
        assertEquals(listOf(UID), e.dao.filas.value.map { it.ownerUid })
    }

    @Test
    fun `el sincronizador solo envia lo de la cuenta activa y sin cuenta no hace nada`() = runTest {
        val dao = DaoEnMemoria(); val servidor = ServidorFalso()
        var cuenta: String? = UID
        val sync = Sincronizador(dao, servidor, { cuenta }, backgroundScope)
        dao.insertar(entidad("a", uid = UID))
        dao.insertar(entidad("b", uid = OTRO_UID))

        sync.sincronizar()
        assertEquals(1, servidor.posts)
        assertEquals(EstadoSync.SINCRONIZADO, dao.filas.value.first { it.clientId == "a" }.syncEstado)
        assertEquals(EstadoSync.PENDIENTE, dao.filas.value.first { it.clientId == "b" }.syncEstado)

        cuenta = null
        assertEquals(EstadoSincronizacion.Inactivo, sync.sincronizar())
        assertEquals(1, servidor.posts)
    }

    @Test
    fun `el traer cambios no mezcla datos de otra cuenta con el mismo clientId`() = runTest {
        val dao = DaoEnMemoria(); val servidor = ServidorFalso()
        val sync = Sincronizador(dao, servidor, { UID }, backgroundScope)
        dao.insertar(entidad("mismo", titulo = "De Beto", uid = OTRO_UID, sync = EstadoSync.SINCRONIZADO).copy(remoteId = "mismo"))
        servidor.sembrar("mismo", "De Ana en el servidor")

        sync.sincronizar()
        assertEquals("De Beto", dao.filas.value.first { it.ownerUid == OTRO_UID }.titulo)
        assertEquals("De Ana en el servidor", dao.filas.value.first { it.ownerUid == UID }.titulo)
        assertEquals(2, dao.filas.value.size)
    }

    @Test
    fun `una sesion invalida corta el ciclo sin marcar error en los registros`() = runTest {
        val dao = DaoEnMemoria(); val servidor = ServidorFalso()
        val sync = Sincronizador(dao, servidor, { UID }, backgroundScope)
        dao.insertar(entidad("a")); dao.insertar(entidad("b", "Otro registro"))

        servidor.modo = Modo.SIN_SESION
        val r = sync.sincronizar() as EstadoSincronizacion.Fallida
        assertEquals(ErrorRed.SesionInvalida, r.error)
        assertTrue(dao.filas.value.all { it.syncEstado == EstadoSync.PENDIENTE }) // nada se marca como error
    }

    @Test
    fun `un rechazo por permisos marca error en ese registro y lo informa`() = runTest {
        val dao = DaoEnMemoria(); val servidor = ServidorFalso()
        val sync = Sincronizador(dao, servidor, { UID }, backgroundScope)
        dao.insertar(entidad("a"))

        servidor.modo = Modo.SIN_PERMISO
        val r = sync.sincronizar()
        val fila = dao.filas.value.single()
        assertEquals(EstadoSync.ERROR, fila.syncEstado) // no es recuperable: no se reintenta solo
        assertEquals(ErrorRed.NoAutorizado.mensaje, fila.errorSync)
        assertEquals(EstadoSincronizacion.Fallida(ErrorRed.NoAutorizado), r) // el listado remoto también fue rechazado
    }
}
