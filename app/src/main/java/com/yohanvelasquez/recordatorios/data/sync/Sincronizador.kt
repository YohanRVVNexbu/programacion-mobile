package com.yohanvelasquez.recordatorios.data.sync

import com.yohanvelasquez.recordatorios.data.aEntidad
import com.yohanvelasquez.recordatorios.data.aDto
import com.yohanvelasquez.recordatorios.data.local.RecordatorioDao
import com.yohanvelasquez.recordatorios.data.local.RecordatorioEntity
import com.yohanvelasquez.recordatorios.data.remote.ErrorRed
import com.yohanvelasquez.recordatorios.data.remote.FuenteRemota
import com.yohanvelasquez.recordatorios.data.remote.ResultadoRed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Estado global de la sincronización, para mostrarlo en la interfaz. */
sealed interface EstadoSincronizacion {
    data object Inactivo : EstadoSincronizacion
    data object Sincronizando : EstadoSincronizacion
    data class Completada(val descartados: Int = 0) : EstadoSincronizacion
    /** [recuperable] indica que reintentar más tarde puede funcionar (sin red, timeout, error del servidor). */
    data class Fallida(val error: ErrorRed) : EstadoSincronizacion {
        val recuperable: Boolean get() = error.recuperable
    }
}

/**
 * Sincroniza Room con el servicio: primero envía lo pendiente y luego trae los cambios remotos.
 *
 * Cómo evita duplicados:
 *  1. Todas las solicitudes pasan por un canal CONFLATED: varias a la vez se funden en una sola pendiente.
 *  2. Una sola sincronización corre a la vez ([mutex]), aunque se llame directamente.
 *  3. Cada recordatorio lleva un clientId que el servicio usa para reconocer un reenvío.
 *  4. Room tiene un índice único sobre clientId, así la base tampoco admite dos filas iguales.
 */
class Sincronizador(
    private val dao: RecordatorioDao,
    private val remoto: FuenteRemota,
    /** uid de la cuenta activa; sin cuenta no hay nada que sincronizar. */
    private val uid: () -> String?,
    scope: CoroutineScope,
    private val reloj: () -> Long = System::currentTimeMillis,
) {
    private val solicitudes = Channel<Unit>(Channel.CONFLATED)
    private val mutex = Mutex()
    private val _estado = MutableStateFlow<EstadoSincronizacion>(EstadoSincronizacion.Inactivo)
    val estado: StateFlow<EstadoSincronizacion> = _estado.asStateFlow()

    init {
        scope.launch { for (pedido in solicitudes) sincronizar() }
    }

    /** Pide una sincronización sin esperar. Es seguro llamarla desde cualquier evento. */
    fun solicitar() {
        solicitudes.trySend(Unit)
    }

    /** Ejecuta una sincronización y devuelve cuando termina. Si ya hay una en curso, espera su turno. */
    suspend fun sincronizar(): EstadoSincronizacion = mutex.withLock {
        val cuenta = uid() ?: return@withLock EstadoSincronizacion.Inactivo
        _estado.update { EstadoSincronizacion.Sincronizando }
        val resultado = try {
            enviarPendientes(cuenta) ?: traerCambios(cuenta)
        } catch (e: kotlinx.coroutines.CancellationException) {
            _estado.update { EstadoSincronizacion.Inactivo }
            throw e
        }
        _estado.update { resultado }
        resultado
    }

    /** Devuelve un error si hay que cortar (sin red, timeout, error del servidor), o null si se pudo seguir. */
    private suspend fun enviarPendientes(cuenta: String): EstadoSincronizacion? {
        for (fila in dao.obtenerPendientes(cuenta)) {
            val error = enviar(fila)
            if (error != null) {
                // Sin red / timeout / 5xx el resto fallaría igual, y sin sesión válida ningún envío puede funcionar: se corta.
                if (error.recuperable || error == ErrorRed.SesionInvalida) return EstadoSincronizacion.Fallida(error)
                dao.marcarError(fila.id, error.mensaje, fila.actualizadoEn) // rechazo definitivo de este registro
            }
        }
        return null
    }

    private suspend fun enviar(fila: RecordatorioEntity): ErrorRed? {
        val version = fila.actualizadoEn
        val remoteId = fila.remoteId
        return when {
            fila.eliminado -> when {
                remoteId == null -> { dao.eliminarDefinitivo(fila.id); null }
                else -> when (val r = remoto.eliminar(remoteId)) {
                    is ResultadoRed.Exito -> { dao.eliminarDefinitivo(fila.id); null }
                    is ResultadoRed.Fallo ->
                        if (r.error == ErrorRed.NoEncontrado) { dao.eliminarDefinitivo(fila.id); null } else r.error
                }
            }
            remoteId == null -> when (val r = remoto.crear(fila.aDto())) {
                is ResultadoRed.Exito -> { r.datos.id?.let { dao.marcarTrasEnvio(fila.id, it, version) }; null }
                is ResultadoRed.Fallo -> r.error
            }
            else -> when (val r = remoto.actualizar(remoteId, fila.aDto())) {
                is ResultadoRed.Exito -> { dao.marcarTrasEnvio(fila.id, remoteId, version); null }
                is ResultadoRed.Fallo -> {
                    // El servicio ya no conoce el registro: se vuelve a crear en el próximo ciclo (mismo clientId).
                    if (r.error == ErrorRed.NoEncontrado) { dao.limpiarRemoteId(fila.id); null } else r.error
                }
            }
        }
    }

    private suspend fun traerCambios(cuenta: String): EstadoSincronizacion {
        val lista = when (val r = remoto.listar()) {
            is ResultadoRed.Exito -> r.datos
            is ResultadoRed.Fallo -> return EstadoSincronizacion.Fallida(r.error)
        }
        var descartados = 0
        val idsRemotos = HashSet<String>()
        for (dto in lista) {
            dto.id?.let { idsRemotos += it }
            val local = dao.buscarPorClientId(cuenta, dto.clientId) ?: dto.id?.let { dao.buscarPorRemoteId(cuenta, it) }
            // Lo que el usuario cambió y aún no se envió tiene prioridad sobre lo que dice el servicio.
            if (local != null && local.syncEstado != com.yohanvelasquez.recordatorios.model.EstadoSync.SINCRONIZADO) continue
            val nueva = dto.aEntidad(cuenta, local, reloj())
            if (nueva == null) { descartados++; continue }
            if (local == null) dao.insertar(nueva) else dao.actualizar(nueva)
        }
        // Lo que estaba sincronizado y el servicio ya no tiene, se borró desde otro lado.
        for (fila in dao.obtenerSincronizados(cuenta)) {
            if (fila.remoteId != null && fila.remoteId !in idsRemotos) dao.eliminarDefinitivo(fila.id)
        }
        return EstadoSincronizacion.Completada(descartados)
    }
}
