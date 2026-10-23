package com.yohanvelasquez.recordatorios.data.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await

/**
 * Autenticación con Firebase Authentication (correo y contraseña).
 *
 * La app nunca guarda contraseñas ni tokens: la contraseña solo viaja a Firebase al iniciar sesión o registrarse,
 * y la sesión (token de actualización) la conserva el SDK en el almacenamiento privado de la app.
 */
class FirebaseAuthRepository(private val auth: FirebaseAuth) : AuthRepository {

    private val _sesion = MutableStateFlow<EstadoSesion>(EstadoSesion.Verificando)
    override val sesion: StateFlow<EstadoSesion> = _sesion.asStateFlow()

    private val _aviso = MutableStateFlow<String?>(null)
    override val aviso: StateFlow<String?> = _aviso.asStateFlow()

    /** true entre que la persona pide cerrar sesión y que el SDK lo confirma; distingue un cierre voluntario de uno forzado. */
    @Volatile private var cierreSolicitado = false

    private val _uid = MutableStateFlow<String?>(null)
    override val uid: StateFlow<String?> = _uid.asStateFlow()

    init {
        // Se invoca al registrar el listener (con la sesión guardada, si existe) y en cada inicio o cierre de sesión.
        auth.addAuthStateListener { firebase ->
            val usuario = firebase.currentUser?.aUsuario()
            val habiaSesion = _uid.value != null
            if (usuario == null) {
                // Si la sesión terminó sin que la persona lo pidiera (cuenta eliminada, deshabilitada o sesión revocada), se avisa.
                if (habiaSesion && !cierreSolicitado) _aviso.update { AVISO_SESION_INVALIDA }
                cierreSolicitado = false
            }
            _uid.value = usuario?.uid
            _sesion.value = usuario?.let { EstadoSesion.Activa(it) } ?: EstadoSesion.SinSesion
        }
    }

    override suspend fun registrar(correo: String, contrasena: String): ResultadoAuth =
        operar { auth.createUserWithEmailAndPassword(correo.trim(), contrasena).await().user }

    override suspend fun iniciarSesion(correo: String, contrasena: String): ResultadoAuth =
        operar { auth.signInWithEmailAndPassword(correo.trim(), contrasena).await().user }

    private suspend fun operar(bloque: suspend () -> FirebaseUser?): ResultadoAuth = try {
        val usuario = bloque()?.aUsuario()
        if (usuario == null) ResultadoAuth.Fallo(ErrorAuth.Desconocido("sin usuario")) else {
            _aviso.update { null }
            ResultadoAuth.Exito(usuario)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ResultadoAuth.Fallo(errorDeAuth(e))
    }

    override fun cerrarSesion(aviso: String?) {
        cierreSolicitado = true
        _aviso.update { aviso }
        auth.signOut()
    }

    override suspend fun sesionSigueValida(): Boolean {
        val usuario = auth.currentUser ?: return false
        return try {
            usuario.reload().await()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseNetworkException) {
            true // sin red no se puede comprobar; se sigue trabajando con lo local
        } catch (e: FirebaseAuthInvalidUserException) {
            false // la cuenta ya no existe, fue deshabilitada o su sesión fue revocada
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            false
        } catch (e: Exception) {
            true // un fallo que no demuestra que la sesión sea inválida no debe cerrarla
        }
    }

    override fun limpiarAviso() = _aviso.update { null }

    private fun FirebaseUser.aUsuario() = Usuario(uid, email)

    companion object {
        const val AVISO_SESION_INVALIDA = "Tu sesión ya no es válida. Inicia sesión de nuevo."
    }
}

/** Traduce las excepciones de Firebase Authentication a errores de la app. */
fun errorDeAuth(e: Throwable): ErrorAuth = when (e) {
    is FirebaseAuthWeakPasswordException -> ErrorAuth.ContrasenaDebil
    is FirebaseAuthUserCollisionException -> ErrorAuth.CorreoYaRegistrado
    is FirebaseAuthInvalidUserException -> ErrorAuth.CredencialesInvalidas
    is FirebaseAuthInvalidCredentialsException ->
        if (e.errorCode == "ERROR_INVALID_EMAIL") ErrorAuth.CorreoNoValido else ErrorAuth.CredencialesInvalidas
    is FirebaseTooManyRequestsException -> ErrorAuth.DemasiadosIntentos
    is FirebaseNetworkException -> ErrorAuth.SinConexion
    else -> ErrorAuth.Desconocido(e.message ?: e::class.java.simpleName)
}
