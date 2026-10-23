package com.yohanvelasquez.recordatorios.data

import com.yohanvelasquez.recordatorios.data.ServidorFalso.Modo
import com.yohanvelasquez.recordatorios.data.remote.ErrorRed
import com.yohanvelasquez.recordatorios.data.sync.EstadoSincronizacion
import com.yohanvelasquez.recordatorios.data.sync.Sincronizador
import com.yohanvelasquez.recordatorios.model.EstadoSync
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SincronizadorTest {

    @Test
    fun `lo creado sin conexion queda pendiente y al volver la red se envia una sola vez`() = runTest {
        val dao = DaoEnMemoria(); val servidor = ServidorFalso()
        val sync = Sincronizador(dao, servidor, { UID }, backgroundScope)
        dao.insertar(entidad("a"))

        servidor.modo = Modo.SIN_CONEXION
        val resultado = sync.sincronizar()
        assertEquals(EstadoSincronizacion.Fallida(ErrorRed.SinConexion), resultado)
        assertEquals(EstadoSync.PENDIENTE, dao.filas.value.single().syncEstado)
        assertNull(dao.filas.value.single().remoteId)

        servidor.modo = Modo.OK
        sync.sincronizar()
        val fila = dao.filas.value.single()
        assertEquals(EstadoSync.SINCRONIZADO, fila.syncEstado)
        assertEquals("1", fila.remoteId)
        assertEquals(1, servidor.posts)
        assertEquals(1, servidor.registros.size)
    }

    @Test
    fun `varias sincronizaciones simultaneas no duplican`() = runTest {
        val dao = DaoEnMemoria(); val servidor = ServidorFalso()
        val sync = Sincronizador(dao, servidor, { UID }, backgroundScope)
        dao.insertar(entidad("a")); dao.insertar(entidad("b", "Llamar al banco"))

        List(6) { async { sync.sincronizar() } }.awaitAll()
        assertEquals(2, servidor.posts)
        assertEquals(2, servidor.registros.size)
        assertEquals(2, dao.filas.value.size)
    }

    @Test
    fun `muchas solicitudes seguidas se funden en pocas sincronizaciones`() = runTest {
        val dao = DaoEnMemoria(); val servidor = ServidorFalso()
        val sync = Sincronizador(dao, servidor, { UID }, backgroundScope)
        dao.insertar(entidad("a"))
        repeat(20) { sync.solicitar() }
        runCurrent() // ejecuta el consumidor que corre en backgroundScope
        assertEquals(1, servidor.posts)
        assertEquals(1, dao.filas.value.size)
    }

    @Test
    fun `si se pierde la respuesta de la creacion el reintento no crea otro registro`() = runTest {
        val dao = DaoEnMemoria(); val servidor = ServidorFalso()
        val sync = Sincronizador(dao, servidor, { UID }, backgroundScope)
        dao.insertar(entidad("a"))

        servidor.modo = Modo.PIERDE_RESPUESTA_AL_CREAR // el servidor crea pero el cliente recibe un timeout
        assertTrue(sync.sincronizar() is EstadoSincronizacion.Fallida)
        assertEquals(1, servidor.registros.size)
        assertEquals(EstadoSync.PENDIENTE, dao.filas.value.single().syncEstado)

        servidor.modo = Modo.OK
        sync.sincronizar()
        assertEquals(2, servidor.posts)           // se reenvió...
        assertEquals(1, servidor.registros.size)  // ...pero el servicio reconoció el clientId
        assertEquals(1, dao.filas.value.size)
        assertEquals(EstadoSync.SINCRONIZADO, dao.filas.value.single().syncEstado)
    }

    @Test
    fun `si el usuario edita mientras se envia el registro sigue pendiente y se vuelve a enviar`() = runTest {
        val dao = DaoEnMemoria(); val servidor = ServidorFalso()
        val sync = Sincronizador(dao, servidor, { UID }, backgroundScope)
        dao.insertar(entidad("a"))
        servidor.alCrear = {
            val actual = dao.filas.value.single()
            dao.filas.value = listOf(actual.copy(titulo = "Comprar pan integral", actualizadoEn = actual.actualizadoEn + 1))
        }
        sync.sincronizar()
        assertEquals(EstadoSync.PENDIENTE, dao.filas.value.single().syncEstado)
        assertEquals("1", dao.filas.value.single().remoteId)

        servidor.alCrear = {}
        sync.sincronizar()
        assertEquals(1, servidor.puts)
        assertEquals("Comprar pan integral", servidor.registros["1"]!!.titulo)
        assertEquals(EstadoSync.SINCRONIZADO, dao.filas.value.single().syncEstado)
        assertEquals(1, servidor.registros.size)
    }

    @Test
    fun `un rechazo 400 marca error solo en ese registro y no frena a los demas`() = runTest {
        val dao = DaoEnMemoria(); val servidor = ServidorFalso()
        val sync = Sincronizador(dao, servidor, { UID }, backgroundScope)
        dao.insertar(entidad("a"))

        servidor.modo = Modo.RECHAZA_400
        sync.sincronizar()
        val fila = dao.filas.value.single()
        assertEquals(EstadoSync.ERROR, fila.syncEstado)
        assertTrue(fila.errorSync!!.contains("400"))

        servidor.modo = Modo.OK
        dao.reintentarFallidos(UID)
        sync.sincronizar()
        assertEquals(EstadoSync.SINCRONIZADO, dao.filas.value.single().syncEstado)
    }

    @Test
    fun `error 500 y timeout son recuperables y conservan los datos locales`() = runTest {
        val dao = DaoEnMemoria(); val servidor = ServidorFalso()
        val sync = Sincronizador(dao, servidor, { UID }, backgroundScope)
        dao.insertar(entidad("a"))
        for (modo in listOf(Modo.ERROR_500, Modo.TIMEOUT)) {
            servidor.modo = modo
            val r = sync.sincronizar() as EstadoSincronizacion.Fallida
            assertTrue(r.recuperable)
            assertEquals(1, dao.filas.value.size)
            assertEquals(EstadoSync.PENDIENTE, dao.filas.value.single().syncEstado)
        }
    }

    @Test
    fun `eliminar un registro confirmado borra tambien en el servicio y uno nunca enviado se borra sin red`() = runTest {
        val dao = DaoEnMemoria(); val servidor = ServidorFalso()
        val sync = Sincronizador(dao, servidor, { UID }, backgroundScope)
        val idA = dao.insertar(entidad("a")).toInt()
        sync.sincronizar()
        assertEquals(1, servidor.registros.size)

        dao.marcarEliminado(idA, 5_000)
        servidor.modo = Modo.SIN_CONEXION
        sync.sincronizar()
        assertEquals(1, dao.filas.value.size) // sigue como borrado lógico hasta que el servicio confirme
        servidor.modo = Modo.OK
        sync.sincronizar()
        assertEquals(0, dao.filas.value.size)
        assertEquals(0, servidor.registros.size)

        val idB = dao.insertar(entidad("b")).toInt()
        dao.marcarEliminado(idB, 6_000)
        servidor.modo = Modo.SIN_CONEXION
        sync.sincronizar()
        assertEquals(0, dao.filas.value.size) // nunca llegó al servicio: no hace falta red
    }

    @Test
    fun `al traer cambios no duplica, respeta lo pendiente, borra lo eliminado y descarta datos invalidos`() = runTest {
        val dao = DaoEnMemoria(); val servidor = ServidorFalso()
        val sync = Sincronizador(dao, servidor, { UID }, backgroundScope)

        servidor.sembrar("x", "Registro remoto")
        servidor.sembrar("malo", titulo = "ab")                 // título demasiado corto
        servidor.sembrar("raro", categoria = "Inexistente")     // categoría fuera de las opciones
        var r = sync.sincronizar() as EstadoSincronizacion.Completada
        assertEquals(2, r.descartados)
        assertEquals(1, dao.filas.value.size)

        sync.sincronizar() // segunda vez: sigue habiendo una sola fila
        assertEquals(1, dao.filas.value.size)

        // Cambio local pendiente: el servicio no lo pisa.
        val local = dao.filas.value.single()
        dao.actualizar(local.copy(titulo = "Título local", actualizadoEn = local.actualizadoEn + 1, syncEstado = EstadoSync.PENDIENTE))
        servidor.modo = Modo.SIN_CONEXION; sync.sincronizar() // sin red: sigue pendiente
        assertEquals("Título local", dao.filas.value.single().titulo)
        assertEquals(EstadoSync.PENDIENTE, dao.filas.value.single().syncEstado)
        servidor.modo = Modo.OK

        // Cuando el cambio local ya se envió y el servicio borra el registro, el local desaparece en la siguiente sincronización.
        sync.sincronizar()
        assertEquals(EstadoSync.SINCRONIZADO, dao.filas.value.single().syncEstado)
        servidor.registros.clear()
        sync.sincronizar()
        assertEquals(0, dao.filas.value.size)
    }
}
