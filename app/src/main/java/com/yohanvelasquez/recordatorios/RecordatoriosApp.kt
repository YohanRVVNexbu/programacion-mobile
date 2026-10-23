package com.yohanvelasquez.recordatorios

import android.app.Application
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.memoryCacheSettings
import com.yohanvelasquez.recordatorios.data.RecordatorioRepository
import com.yohanvelasquez.recordatorios.data.RecordatorioRepositoryLocalFirst
import com.yohanvelasquez.recordatorios.data.auth.AuthRepository
import com.yohanvelasquez.recordatorios.data.auth.EstadoSesion
import com.yohanvelasquez.recordatorios.data.auth.FirebaseAuthRepository
import com.yohanvelasquez.recordatorios.data.local.AppDatabase
import com.yohanvelasquez.recordatorios.data.preferencias.PreferenciasRepository
import com.yohanvelasquez.recordatorios.data.preferencias.preferenciasDataStore
import com.yohanvelasquez.recordatorios.data.remote.ControlSimulacion
import com.yohanvelasquez.recordatorios.data.remote.ErrorRed
import com.yohanvelasquez.recordatorios.data.remote.FirestoreFuenteRemota
import com.yohanvelasquez.recordatorios.data.remote.FuenteRemota
import com.yohanvelasquez.recordatorios.data.remote.FuenteRemotaConSimulacion
import com.yohanvelasquez.recordatorios.data.remote.MonitorRed
import com.yohanvelasquez.recordatorios.data.sync.EstadoSincronizacion
import com.yohanvelasquez.recordatorios.data.sync.Sincronizador
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * Crea una sola instancia de cada pieza de la capa de datos (Room, DataStore, Firebase y repositorio).
 * Es el único lugar que conoce las implementaciones concretas.
 */
class RecordatoriosApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val simulacion = ControlSimulacion()
    val monitorRed by lazy { MonitorRed(this) }
    val preferencias by lazy { PreferenciasRepository(preferenciasDataStore) }
    val auth: AuthRepository by lazy { FirebaseAuthRepository(FirebaseAuth.getInstance()) }

    private val baseDatos by lazy { AppDatabase.crear(this) }

    val repositorio: RecordatorioRepository by lazy {
        val firestore = FirestoreFuenteRemota(FirebaseFirestore.getInstance(), { auth.uid.value }, monitorRed)
        // El menú de simulación de fallos solo existe en la variante dev.
        val remota: FuenteRemota = if (BuildConfig.PERMITE_SIMULACION) FuenteRemotaConSimulacion(firestore, simulacion) else firestore
        val sincronizador = Sincronizador(baseDatos.recordatorioDao(), remota, { auth.uid.value }, appScope)
        RecordatorioRepositoryLocalFirst(baseDatos.recordatorioDao(), preferencias, sincronizador, auth.uid)
    }

    override fun onCreate() {
        super.onCreate()
        configurarFirebase()
        coordinarSesion()
    }

    /** Debe ejecutarse antes de cualquier uso de Firebase. */
    private fun configurarFirebase() {
        val firestore = FirebaseFirestore.getInstance()
        // Room es la copia local; el caché propio de Firestore se deja solo en memoria para no tener dos copias en disco.
        firestore.firestoreSettings = firestoreSettings { setLocalCacheSettings(memoryCacheSettings { }) }
        if (BuildConfig.USAR_EMULADOR_FIREBASE) {
            FirebaseAuth.getInstance().useEmulator(BuildConfig.EMULADOR_HOST, PUERTO_AUTH_EMULADOR)
            firestore.useEmulator(BuildConfig.EMULADOR_HOST, PUERTO_FIRESTORE_EMULADOR)
        }
    }

    /**
     * Reglas que unen sesión y sincronización:
     *  - al iniciar sesión se descartan los datos locales sin dueño y se sincroniza;
     *  - al abrir la app y al volver la red se comprueba que la sesión siga siendo válida;
     *  - si el servicio responde que no hay sesión o permiso, también se comprueba.
     */
    private fun coordinarSesion() {
        appScope.launch {
            auth.sesion.collect { sesion ->
                if (sesion is EstadoSesion.Activa) {
                    repositorio.descartarDatosSinCuenta()
                    repositorio.solicitarSincronizacion()
                    verificarSesion()
                }
            }
        }
        // Cada vez que una cuenta deja de tener sesión (cierre voluntario, sesión inválida o cambio de cuenta),
        // se borran del dispositivo sus datos. Así nada de una cuenta queda disponible para la siguiente.
        appScope.launch {
            var anterior: String? = null
            auth.uid.collect { actual ->
                anterior?.let { if (it != actual) repositorio.limpiarDatosDe(it) }
                anterior = actual
            }
        }
        appScope.launch {
            monitorRed.conectado.filter { it }.collect {
                if (auth.uid.value != null) {
                    verificarSesion()
                    repositorio.solicitarSincronizacion()
                }
            }
        }
        appScope.launch {
            repositorio.estadoSincronizacion.collect { estado ->
                if (estado is EstadoSincronizacion.Fallida &&
                    (estado.error == ErrorRed.SesionInvalida || estado.error == ErrorRed.NoAutorizado)
                ) verificarSesion()
            }
        }
    }

    /** Si la cuenta ya no existe o fue deshabilitada, se borran los datos locales y se vuelve al acceso con un aviso. */
    private suspend fun verificarSesion() {
        if (auth.uid.value == null || auth.sesionSigueValida()) return
        repositorio.limpiarDatosLocales()
        auth.cerrarSesion(aviso = FirebaseAuthRepository.AVISO_SESION_INVALIDA)
    }

    private companion object {
        const val PUERTO_AUTH_EMULADOR = 9099
        const val PUERTO_FIRESTORE_EMULADOR = 8080
    }
}
