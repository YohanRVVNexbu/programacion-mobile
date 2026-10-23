package com.yohanvelasquez.recordatorios.ui

import com.yohanvelasquez.recordatorios.data.auth.AuthRepository
import com.yohanvelasquez.recordatorios.data.auth.ErrorAuth
import com.yohanvelasquez.recordatorios.data.auth.EstadoSesion
import com.yohanvelasquez.recordatorios.data.auth.ResultadoAuth
import com.yohanvelasquez.recordatorios.data.auth.Usuario
import com.yohanvelasquez.recordatorios.ui.auth.AuthViewModel
import com.yohanvelasquez.recordatorios.ui.auth.CampoAcceso
import com.yohanvelasquez.recordatorios.ui.auth.ModoAcceso
import com.yohanvelasquez.recordatorios.ui.auth.errorContrasena
import com.yohanvelasquez.recordatorios.ui.auth.errorCorreo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class AuthFalsa : AuthRepository {
    val sesionInterna = MutableStateFlow<EstadoSesion>(EstadoSesion.SinSesion)
    override val sesion: StateFlow<EstadoSesion> = sesionInterna
    override val aviso = MutableStateFlow<String?>(null)
    override val uid: StateFlow<String?> = MutableStateFlow(null)
    var respuesta = CompletableDeferred<ResultadoAuth>()
    var llamadas = 0
    var ultimoModo = ""

    override suspend fun registrar(correo: String, contrasena: String): ResultadoAuth { llamadas++; ultimoModo = "registrar"; return respuesta.await() }
    override suspend fun iniciarSesion(correo: String, contrasena: String): ResultadoAuth { llamadas++; ultimoModo = "iniciar"; return respuesta.await() }
    override fun cerrarSesion(aviso: String?) = Unit
    override suspend fun sesionSigueValida() = true
    override fun limpiarAviso() { aviso.value = null }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private lateinit var auth: AuthFalsa
    private lateinit var vm: AuthViewModel

    @Before fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        auth = AuthFalsa(); vm = AuthViewModel(auth)
    }
    @After fun cerrar() = Dispatchers.resetMain()

    @Test
    fun `las validaciones de correo y contrasena`() {
        assertNotNull(errorCorreo("")); assertNotNull(errorCorreo("ana")); assertNotNull(errorCorreo("ana@correo"))
        assertNull(errorCorreo("ana@correo.com")); assertNull(errorCorreo(" ana@correo.com "))
        assertNotNull(errorContrasena("12345")); assertNull(errorContrasena("123456"))
    }

    @Test
    fun `el boton esta deshabilitado hasta que el formulario es valido y los errores salen al tocar los campos`() {
        assertFalse(vm.uiState.value.puedeEnviar)
        assertNull(vm.uiState.value.errorVisible(CampoAcceso.CORREO)) // aún no se tocó
        vm.onCorreoChange("ana")
        assertNotNull(vm.uiState.value.errorVisible(CampoAcceso.CORREO))
        vm.onCorreoChange("ana@correo.com"); vm.onContrasenaChange("secreto1")
        assertTrue(vm.uiState.value.puedeEnviar)
    }

    @Test
    fun `al registrarse las contrasenas deben coincidir`() {
        vm.onCambiarModo(ModoAcceso.REGISTRAR)
        vm.onCorreoChange("ana@correo.com"); vm.onContrasenaChange("secreto1"); vm.onRepetirChange("otra123")
        assertFalse(vm.uiState.value.puedeEnviar)
        assertNotNull(vm.uiState.value.errorVisible(CampoAcceso.REPETIR))
        vm.onRepetirChange("secreto1")
        assertTrue(vm.uiState.value.puedeEnviar)
    }

    @Test
    fun `mientras se autentica el formulario se bloquea y un segundo envio se ignora`() {
        vm.onCorreoChange("ana@correo.com"); vm.onContrasenaChange("secreto1")
        vm.onEnviar()
        assertTrue(vm.uiState.value.autenticando)
        assertFalse(vm.uiState.value.puedeEnviar)
        vm.onEnviar(); vm.onCorreoChange("otro@correo.com")
        assertEquals(1, auth.llamadas)
        assertEquals("ana@correo.com", vm.uiState.value.correo) // no se puede editar mientras se autentica

        auth.respuesta.complete(ResultadoAuth.Exito(Usuario("u1", "ana@correo.com")))
        assertFalse(vm.uiState.value.autenticando)
        assertEquals("", vm.uiState.value.contrasena) // la contraseña no se conserva tras iniciar sesión
    }

    @Test
    fun `un error de autenticacion se muestra y desaparece al editar`() {
        vm.onCorreoChange("ana@correo.com"); vm.onContrasenaChange("equivocada")
        vm.onEnviar()
        auth.respuesta.complete(ResultadoAuth.Fallo(ErrorAuth.CredencialesInvalidas))
        assertEquals(ErrorAuth.CredencialesInvalidas, vm.uiState.value.error)
        assertFalse(vm.uiState.value.autenticando)
        vm.onContrasenaChange("equivocada2")
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `cada modo llama a la operacion que le corresponde`() {
        vm.onCorreoChange("ana@correo.com"); vm.onContrasenaChange("secreto1")
        auth.respuesta.complete(ResultadoAuth.Fallo(ErrorAuth.SinConexion))
        vm.onEnviar(); assertEquals("iniciar", auth.ultimoModo)
        vm.onCambiarModo(ModoAcceso.REGISTRAR)
        vm.onContrasenaChange("secreto1"); vm.onRepetirChange("secreto1")
        vm.onEnviar(); assertEquals("registrar", auth.ultimoModo)
    }

    @Test
    fun `al cambiar de modo se limpian las contrasenas pero se conserva el correo`() {
        vm.onCorreoChange("ana@correo.com"); vm.onContrasenaChange("secreto1")
        vm.onCambiarModo(ModoAcceso.REGISTRAR)
        assertEquals("ana@correo.com", vm.uiState.value.correo)
        assertEquals("", vm.uiState.value.contrasena)
    }

    @Test
    fun `al iniciar sesion el formulario se descarta por completo, tambien el correo`() {
        vm.onCorreoChange("ana@correo.com"); vm.onContrasenaChange("secreto1")
        auth.sesionInterna.value = EstadoSesion.Activa(Usuario("u1", "ana@correo.com"))
        assertEquals("", vm.uiState.value.correo)
        assertEquals("", vm.uiState.value.contrasena)
        assertTrue(vm.uiState.value.tocados.isEmpty())
    }
}
