package com.yohanvelasquez.recordatorios.data

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.yohanvelasquez.recordatorios.data.auth.ErrorAuth
import com.yohanvelasquez.recordatorios.data.auth.errorDeAuth
import com.yohanvelasquez.recordatorios.data.remote.ControlSimulacion
import com.yohanvelasquez.recordatorios.data.remote.ErrorRed
import com.yohanvelasquez.recordatorios.data.remote.FuenteRemotaConSimulacion
import com.yohanvelasquez.recordatorios.data.remote.ModoSimulacion
import com.yohanvelasquez.recordatorios.data.remote.RecordatorioDto
import com.yohanvelasquez.recordatorios.data.remote.ResultadoRed
import com.yohanvelasquez.recordatorios.data.remote.aMapa
import com.yohanvelasquez.recordatorios.data.remote.errorDeFirestore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ErroresTest {

    @Test
    fun `los codigos de Firestore se traducen a errores de la app`() {
        assertEquals(ErrorRed.SinConexion, errorDeFirestore(Code.UNAVAILABLE))
        assertEquals(ErrorRed.Timeout, errorDeFirestore(Code.DEADLINE_EXCEEDED))
        assertEquals(ErrorRed.NoAutorizado, errorDeFirestore(Code.PERMISSION_DENIED))
        assertEquals(ErrorRed.SesionInvalida, errorDeFirestore(Code.UNAUTHENTICATED))
        assertEquals(ErrorRed.NoEncontrado, errorDeFirestore(Code.NOT_FOUND))
        assertEquals(ErrorRed.SolicitudInvalida(400), errorDeFirestore(Code.INVALID_ARGUMENT))
        assertEquals(ErrorRed.Servidor(500), errorDeFirestore(Code.INTERNAL))
        assertTrue(errorDeFirestore(Code.UNKNOWN) is ErrorRed.Desconocido)
    }

    @Test
    fun `solo son recuperables los fallos de red, timeout y servidor`() {
        assertTrue(ErrorRed.SinConexion.recuperable && ErrorRed.Timeout.recuperable && ErrorRed.Servidor(500).recuperable)
        assertFalse(ErrorRed.NoAutorizado.recuperable)
        assertFalse(ErrorRed.SesionInvalida.recuperable)
        assertFalse(ErrorRed.SolicitudInvalida(400).recuperable)
    }

    @Test
    fun `las excepciones de autenticacion se traducen sin exponer el proveedor`() {
        assertEquals(ErrorAuth.ContrasenaDebil, errorDeAuth(FirebaseAuthWeakPasswordException("ERROR_WEAK_PASSWORD", "débil", "motivo")))
        assertEquals(ErrorAuth.CorreoYaRegistrado, errorDeAuth(FirebaseAuthUserCollisionException("ERROR_EMAIL_ALREADY_IN_USE", "en uso")))
        assertEquals(ErrorAuth.CorreoNoValido, errorDeAuth(FirebaseAuthInvalidCredentialsException("ERROR_INVALID_EMAIL", "mal")))
        assertEquals(ErrorAuth.CredencialesInvalidas, errorDeAuth(FirebaseAuthInvalidCredentialsException("ERROR_WRONG_PASSWORD", "mal")))
        // Un usuario inexistente da el mismo mensaje que una contraseña mala: no se revela si el correo existe.
        assertEquals(ErrorAuth.CredencialesInvalidas, errorDeAuth(FirebaseAuthInvalidUserException("ERROR_USER_NOT_FOUND", "no existe")))
        assertEquals(ErrorAuth.SinConexion, errorDeAuth(FirebaseNetworkException("sin red")))
        assertEquals(ErrorAuth.DemasiadosIntentos, errorDeAuth(FirebaseTooManyRequestsException("muchos")))
        assertTrue(errorDeAuth(IllegalStateException("x")) is ErrorAuth.Desconocido)
        assertEquals(ErrorAuth.CredencialesInvalidas.mensaje, errorDeAuth(FirebaseAuthInvalidUserException("ERROR_USER_NOT_FOUND", "x")).mensaje)
    }

    @Test
    fun `el documento de Firestore omite los nulos y usa los nombres del esquema`() {
        val dto = RecordatorioDto("c1", "c1", "Comprar pan", null, "Hogar", "BAJA", "PENDIENTE", null, null, null, 1_000, 2_000)
        val mapa = dto.aMapa()
        assertEquals(setOf("titulo", "categoria", "prioridad", "estado", "creadoEn", "actualizadoEn"), mapa.keys)
        assertEquals(1_000L, mapa["creadoEn"])
        val completo = dto.copy(descripcion = "x", completadoEn = 5, fechaLimite = 6, ubicacion = "casa").aMapa()
        assertTrue(completo.keys.containsAll(listOf("descripcion", "completadoEn", "fechaLimite", "ubicacion")))
        // El id del documento no va dentro del documento: lo da la ruta users/{uid}/reminders/{id}.
        assertFalse("id" in mapa || "clientId" in mapa)
    }

    @Test
    fun `la simulacion fuerza cada fallo y no toca la fuente real`() = runBlocking {
        val servidor = ServidorFalso(); val control = ControlSimulacion()
        val s = FuenteRemotaConSimulacion(servidor, control)
        val dto = RecordatorioDto(null, "c1", "Comprar pan", null, "Hogar", "BAJA", "PENDIENTE")

        control.modo = ModoSimulacion.SIN_CONEXION; assertEquals(ErrorRed.SinConexion, (s.listar() as ResultadoRed.Fallo).error)
        control.modo = ModoSimulacion.TIMEOUT; assertEquals(ErrorRed.Timeout, (s.crear(dto) as ResultadoRed.Fallo).error)
        control.modo = ModoSimulacion.ERROR_SERVIDOR; assertEquals(ErrorRed.Servidor(500), (s.eliminar("1") as ResultadoRed.Fallo).error)
        control.modo = ModoSimulacion.SOLICITUD_INVALIDA
        assertEquals(ErrorRed.SolicitudInvalida(400), (s.crear(dto) as ResultadoRed.Fallo).error)
        assertTrue(s.listar() is ResultadoRed.Exito) // la lectura no se rechaza
        assertEquals(0, servidor.posts)

        control.modo = ModoSimulacion.NORMAL
        assertTrue(s.crear(dto) is ResultadoRed.Exito)
        assertEquals(1, servidor.posts)
    }
}
