package com.yohanvelasquez.recordatorios.data

import com.yohanvelasquez.recordatorios.data.local.RecordatorioDao
import com.yohanvelasquez.recordatorios.data.local.RecordatorioEntity
import com.yohanvelasquez.recordatorios.data.remote.ErrorRed
import com.yohanvelasquez.recordatorios.data.remote.FuenteRemota
import com.yohanvelasquez.recordatorios.data.remote.RecordatorioDto
import com.yohanvelasquez.recordatorios.data.remote.ResultadoRed
import com.yohanvelasquez.recordatorios.model.EstadoSync
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** DAO en memoria con el mismo comportamiento que las consultas de Room (la versión real se prueba en androidTest). */
class DaoEnMemoria : RecordatorioDao {
    val filas = MutableStateFlow<List<RecordatorioEntity>>(emptyList())
    private var siguiente = 1

    private fun visibles() = filas.map { l ->
        l.filter { !it.eliminado }.sortedWith(compareByDescending<RecordatorioEntity> { it.creadoEn }.thenByDescending { it.id })
    }

    override suspend fun insertar(entidad: RecordatorioEntity): Long {
        if (filas.value.any { it.clientId == entidad.clientId }) return -1
        val id = if (entidad.id == 0) siguiente++ else entidad.id.also { siguiente = maxOf(siguiente, it + 1) }
        filas.update { it + entidad.copy(id = id) }
        return id.toLong()
    }

    override suspend fun actualizar(entidad: RecordatorioEntity) = filas.update { l -> l.map { if (it.id == entidad.id) entidad else it } }
    override suspend fun eliminar(entidad: RecordatorioEntity) = eliminarDefinitivo(entidad.id)
    override suspend fun eliminarDefinitivo(id: Int) = filas.update { l -> l.filter { it.id != id } }

    override fun observarTodos(): Flow<List<RecordatorioEntity>> = visibles()
    override fun observarPorEstado(estado: String) = visibles().map { l -> l.filter { it.estado == estado } }
    override fun observarPorCategoria(categoria: String) = visibles().map { l -> l.filter { it.categoria == categoria } }
    override fun observarPorPrioridad(prioridad: String) = visibles().map { l -> l.filter { it.prioridad == prioridad } }
    override fun observarPorId(id: Int) = visibles().map { l -> l.firstOrNull { it.id == id } }
    override fun observarCantidad() = visibles().map { it.size }
    override suspend fun obtenerPorId(id: Int) = filas.value.firstOrNull { it.id == id }

    override suspend fun obtenerPendientes() =
        filas.value.filter { it.syncEstado == EstadoSync.PENDIENTE }.sortedWith(compareBy({ it.actualizadoEn }, { it.id }))
    override suspend fun obtenerSincronizados() = filas.value.filter { it.syncEstado == EstadoSync.SINCRONIZADO }
    override suspend fun buscarPorClientId(clientId: String) = filas.value.firstOrNull { it.clientId == clientId }
    override suspend fun buscarPorRemoteId(remoteId: String) = filas.value.firstOrNull { it.remoteId == remoteId }

    override suspend fun marcarTrasEnvio(id: Int, remoteId: String, version: Long) = filas.update { l ->
        l.map {
            if (it.id != id) it
            else it.copy(remoteId = remoteId, errorSync = null, syncEstado = if (it.actualizadoEn == version) EstadoSync.SINCRONIZADO else it.syncEstado)
        }
    }

    override suspend fun marcarError(id: Int, mensaje: String, version: Long) = filas.update { l ->
        l.map { if (it.id == id && it.actualizadoEn == version) it.copy(syncEstado = EstadoSync.ERROR, errorSync = mensaje) else it }
    }

    override suspend fun limpiarRemoteId(id: Int) = filas.update { l -> l.map { if (it.id == id) it.copy(remoteId = null) else it } }

    override suspend fun marcarEliminado(id: Int, ahora: Long) = filas.update { l ->
        l.map { if (it.id == id) it.copy(eliminado = true, syncEstado = EstadoSync.PENDIENTE, errorSync = null, actualizadoEn = ahora) else it }
    }

    override suspend fun reintentarFallidos() = filas.update { l ->
        l.map { if (it.syncEstado == EstadoSync.ERROR) it.copy(syncEstado = EstadoSync.PENDIENTE, errorSync = null) else it }
    }
}

/** Servicio falso que reconoce reenvíos por clientId, como el servicio real. */
class ServidorFalso : FuenteRemota {
    enum class Modo { OK, SIN_CONEXION, TIMEOUT, ERROR_500, RECHAZA_400, PIERDE_RESPUESTA_AL_CREAR }

    var modo = Modo.OK
    val registros = LinkedHashMap<String, RecordatorioDto>()
    var posts = 0
    var puts = 0
    var deletes = 0
    var alCrear: () -> Unit = {}
    private var siguienteId = 1

    private fun falla(): ErrorRed? = when (modo) {
        Modo.SIN_CONEXION -> ErrorRed.SinConexion
        Modo.TIMEOUT -> ErrorRed.Timeout
        Modo.ERROR_500 -> ErrorRed.Servidor(500)
        else -> null
    }

    override suspend fun listar() = falla()?.let { ResultadoRed.Fallo(it) } ?: ResultadoRed.Exito(registros.values.toList())

    override suspend fun crear(dto: RecordatorioDto): ResultadoRed<RecordatorioDto> {
        falla()?.let { return ResultadoRed.Fallo(it) }
        if (modo == Modo.RECHAZA_400) return ResultadoRed.Fallo(ErrorRed.SolicitudInvalida(400))
        posts++
        alCrear()
        val existente = registros.values.firstOrNull { it.clientId == dto.clientId }
        val guardado = existente ?: dto.copy(id = (siguienteId++).toString(), actualizadoEn = 1_000).also { registros[it.id!!] = it }
        if (modo == Modo.PIERDE_RESPUESTA_AL_CREAR) return ResultadoRed.Fallo(ErrorRed.Timeout) // el servidor sí creó
        return ResultadoRed.Exito(guardado)
    }

    override suspend fun actualizar(id: String, dto: RecordatorioDto): ResultadoRed<RecordatorioDto> {
        falla()?.let { return ResultadoRed.Fallo(it) }
        if (modo == Modo.RECHAZA_400) return ResultadoRed.Fallo(ErrorRed.SolicitudInvalida(400))
        puts++
        if (id !in registros) return ResultadoRed.Fallo(ErrorRed.NoEncontrado)
        return ResultadoRed.Exito(dto.copy(id = id).also { registros[id] = it })
    }

    override suspend fun eliminar(id: String): ResultadoRed<Unit> {
        falla()?.let { return ResultadoRed.Fallo(it) }
        deletes++
        return if (registros.remove(id) != null) ResultadoRed.Exito(Unit) else ResultadoRed.Fallo(ErrorRed.NoEncontrado)
    }

    fun sembrar(clientId: String, titulo: String = "Registro remoto", categoria: String = "Trabajo", prioridad: String = "ALTA"): RecordatorioDto {
        val dto = RecordatorioDto((siguienteId++).toString(), clientId, titulo, null, categoria, prioridad, "PENDIENTE", actualizadoEn = 2_000)
        registros[dto.id!!] = dto
        return dto
    }
}

fun entidad(clientId: String, titulo: String = "Comprar pan", ahora: Long = 1_000, sync: EstadoSync = EstadoSync.PENDIENTE) =
    RecordatorioEntity(clientId = clientId, titulo = titulo, categoria = "Hogar", prioridad = "BAJA", creadoEn = ahora, actualizadoEn = ahora, syncEstado = sync)
