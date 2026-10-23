package com.yohanvelasquez.recordatorios

import android.app.Application
import com.yohanvelasquez.recordatorios.data.RecordatorioRepository
import com.yohanvelasquez.recordatorios.data.RecordatorioRepositoryLocalFirst
import com.yohanvelasquez.recordatorios.data.local.AppDatabase
import com.yohanvelasquez.recordatorios.data.preferencias.PreferenciasRepository
import com.yohanvelasquez.recordatorios.data.preferencias.preferenciasDataStore
import com.yohanvelasquez.recordatorios.data.remote.ClienteRed
import com.yohanvelasquez.recordatorios.data.remote.ControlSimulacion
import com.yohanvelasquez.recordatorios.data.remote.FakeBackendInterceptor
import com.yohanvelasquez.recordatorios.data.remote.MonitorRed
import com.yohanvelasquez.recordatorios.data.remote.RecordatorioRemoteDataSource
import com.yohanvelasquez.recordatorios.data.sync.Sincronizador
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.io.File

/**
 * Crea una sola instancia de cada pieza de la capa de datos (base de datos, DataStore, red y repositorio).
 * Es el único lugar que conoce las implementaciones concretas.
 */
class RecordatoriosApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val simulacion = ControlSimulacion()
    val monitorRed by lazy { MonitorRed(this) }
    val preferencias by lazy { PreferenciasRepository(preferenciasDataStore) }

    private val baseDatos by lazy { AppDatabase.crear(this) }

    private val api by lazy {
        val simulado = if (BuildConfig.USAR_BACKEND_SIMULADO) {
            FakeBackendInterceptor(simulacion, monitorRed, File(filesDir, "backend_simulado.json"))
        } else null
        ClienteRed.crearApi(BuildConfig.API_BASE_URL, simulado)
    }

    val repositorio: RecordatorioRepository by lazy {
        val sincronizador = Sincronizador(baseDatos.recordatorioDao(), RecordatorioRemoteDataSource(api), appScope)
        RecordatorioRepositoryLocalFirst(baseDatos.recordatorioDao(), preferencias, sincronizador)
    }

    override fun onCreate() {
        super.onCreate()
        // Al abrir la app y cada vez que vuelve la conexión se pide sincronizar. Las peticiones simultáneas se funden en una.
        appScope.launch {
            monitorRed.conectado.filter { it }.collect { repositorio.solicitarSincronizacion() }
        }
    }
}
