package com.yohanvelasquez.recordatorios.data.auth

import kotlinx.coroutines.flow.StateFlow

/** Identidad de la persona con sesión. [uid] es el identificador estable que asocia sus datos. */
data class Usuario(val uid: String, val correo: String?)

sealed interface EstadoSesion {
    /** Todavía no se sabe si hay una sesión guardada (arranque de la app). */
    data object Verificando : EstadoSesion
    data object SinSesion : EstadoSesion
    data class Activa(val usuario: Usuario) : EstadoSesion
}

/** Errores de autenticación en términos de la app, sin exponer nada del proveedor. */
sealed class ErrorAuth(val mensaje: String) {
    /** Mensaje deliberadamente genérico: no revela si el correo existe. */
    data object CredencialesInvalidas : ErrorAuth("El correo o la contraseña no son correctos.")
    data object CorreoNoValido : ErrorAuth("El correo no tiene un formato válido.")
    data object ContrasenaDebil : ErrorAuth("La contraseña es demasiado débil. Usa al menos 6 caracteres.")
    data object CorreoYaRegistrado : ErrorAuth("Ya existe una cuenta con ese correo.")
    data object SinConexion : ErrorAuth("Sin conexión. Revisa tu internet e intenta de nuevo.")
    data object DemasiadosIntentos : ErrorAuth("Demasiados intentos. Espera un momento antes de volver a intentar.")
    data class Desconocido(val detalle: String) : ErrorAuth("No se pudo completar la operación. Intenta de nuevo.")
}

sealed interface ResultadoAuth {
    data class Exito(val usuario: Usuario) : ResultadoAuth
    data class Fallo(val error: ErrorAuth) : ResultadoAuth
}

/** Autenticación de la app. Los ViewModels solo conocen esta interfaz, no Firebase. */
interface AuthRepository {
    val sesion: StateFlow<EstadoSesion>

    /** Aviso para mostrar en la pantalla de acceso cuando la sesión terminó por una causa ajena a la persona. */
    val aviso: StateFlow<String?>

    /** uid de la sesión activa, o null. Asocia los datos locales y remotos con la cuenta. */
    val uid: StateFlow<String?>

    suspend fun registrar(correo: String, contrasena: String): ResultadoAuth
    suspend fun iniciarSesion(correo: String, contrasena: String): ResultadoAuth

    /** Cierra la sesión. Si [aviso] no es null, se muestra en la pantalla de acceso. */
    fun cerrarSesion(aviso: String? = null)

    /**
     * Comprueba con el proveedor que la sesión guardada sigue siendo válida (la cuenta existe y no fue deshabilitada).
     * Sin conexión devuelve true: no se expulsa a nadie por no tener red.
     */
    suspend fun sesionSigueValida(): Boolean

    fun limpiarAviso()
}
