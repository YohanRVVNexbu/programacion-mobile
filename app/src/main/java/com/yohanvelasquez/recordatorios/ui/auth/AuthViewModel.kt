package com.yohanvelasquez.recordatorios.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yohanvelasquez.recordatorios.RecordatoriosApp
import com.yohanvelasquez.recordatorios.data.auth.AuthRepository
import com.yohanvelasquez.recordatorios.data.auth.ErrorAuth
import com.yohanvelasquez.recordatorios.data.auth.EstadoSesion
import com.yohanvelasquez.recordatorios.data.auth.ResultadoAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ModoAcceso(val titulo: String, val accion: String) {
    INICIAR("Iniciar sesión", "Iniciar sesión"),
    REGISTRAR("Crear cuenta", "Registrarme"),
}

enum class CampoAcceso { CORREO, CONTRASENA, REPETIR }

const val CONTRASENA_MINIMA = 6

private val FORMATO_CORREO = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+$")

fun errorCorreo(correo: String): String? = when {
    correo.isBlank() -> "Escribe tu correo"
    !FORMATO_CORREO.matches(correo.trim()) -> "El correo no tiene un formato válido"
    else -> null
}

fun errorContrasena(contrasena: String): String? =
    if (contrasena.length < CONTRASENA_MINIMA) "Debe tener al menos $CONTRASENA_MINIMA caracteres" else null

data class AuthUiState(
    val modo: ModoAcceso = ModoAcceso.INICIAR,
    val correo: String = "",
    val contrasena: String = "",
    val repetir: String = "",
    val tocados: Set<CampoAcceso> = emptySet(),
    /** Autenticación en curso: bloquea el formulario y el botón. */
    val autenticando: Boolean = false,
    /** Error devuelto por el servicio de autenticación (credenciales, red, cuenta repetida…). */
    val error: ErrorAuth? = null,
) {
    private val registrando get() = modo == ModoAcceso.REGISTRAR

    private val errCorreo get() = errorCorreo(correo)
    private val errContrasena get() = if (registrando) errorContrasena(contrasena) else if (contrasena.isEmpty()) "Escribe tu contraseña" else null
    private val errRepetir get() = if (registrando && repetir != contrasena) "Las contraseñas no coinciden" else null

    fun errorVisible(campo: CampoAcceso): String? = if (campo !in tocados) null else when (campo) {
        CampoAcceso.CORREO -> errCorreo
        CampoAcceso.CONTRASENA -> errContrasena
        CampoAcceso.REPETIR -> errRepetir
    }

    val puedeEnviar: Boolean get() = !autenticando && errCorreo == null && errContrasena == null && errRepetir == null
}

class AuthViewModel(private val auth: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    /** Aviso persistente de la sesión (por ejemplo, «tu sesión ya no es válida»). */
    val aviso: StateFlow<String?> get() = auth.aviso

    init {
        // Al iniciar sesión se descarta todo el formulario (incluido el correo). Este ViewModel vive tanto como la actividad,
        // así que sin esto la siguiente persona que use el dispositivo vería el correo de la anterior.
        viewModelScope.launch {
            auth.sesion.collect { if (it is EstadoSesion.Activa) _uiState.value = AuthUiState() }
        }
    }

    fun onCorreoChange(valor: String) = editar(CampoAcceso.CORREO) { it.copy(correo = valor) }
    fun onContrasenaChange(valor: String) = editar(CampoAcceso.CONTRASENA) { it.copy(contrasena = valor) }
    fun onRepetirChange(valor: String) = editar(CampoAcceso.REPETIR) { it.copy(repetir = valor) }

    private fun editar(campo: CampoAcceso, cambio: (AuthUiState) -> AuthUiState) {
        _uiState.update { actual ->
            if (actual.autenticando) actual else cambio(actual).copy(tocados = actual.tocados + campo, error = null)
        }
    }

    fun onCambiarModo(modo: ModoAcceso) {
        _uiState.update { if (it.autenticando) it else AuthUiState(modo = modo, correo = it.correo) }
    }

    fun onEnviar() {
        val estado = _uiState.value
        if (!estado.puedeEnviar) {
            _uiState.update { it.copy(tocados = CampoAcceso.entries.toSet()) }
            return
        }
        _uiState.update { it.copy(autenticando = true, error = null) }
        auth.limpiarAviso()
        viewModelScope.launch {
            val resultado = when (estado.modo) {
                ModoAcceso.INICIAR -> auth.iniciarSesion(estado.correo, estado.contrasena)
                ModoAcceso.REGISTRAR -> auth.registrar(estado.correo, estado.contrasena)
            }
            _uiState.update {
                when (resultado) {
                    // Con sesión iniciada la pantalla desaparece sola (la raíz observa la sesión); no se conserva la contraseña.
                    is ResultadoAuth.Exito -> it.copy(autenticando = false, contrasena = "", repetir = "")
                    is ResultadoAuth.Fallo -> it.copy(autenticando = false, error = resultado.error)
                }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { AuthViewModel((this[APPLICATION_KEY] as RecordatoriosApp).auth) }
        }
    }
}
