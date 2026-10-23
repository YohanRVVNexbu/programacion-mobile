package com.yohanvelasquez.recordatorios.data

import com.yohanvelasquez.recordatorios.data.local.RecordatorioEntity
import com.yohanvelasquez.recordatorios.data.remote.RecordatorioDto
import com.yohanvelasquez.recordatorios.model.Categoria
import com.yohanvelasquez.recordatorios.model.EstadoRecordatorio
import com.yohanvelasquez.recordatorios.model.EstadoSync
import com.yohanvelasquez.recordatorios.model.Prioridad
import com.yohanvelasquez.recordatorios.model.Recordatorio
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/*
 * Mapeos explícitos entre las tres representaciones:
 *   DTO (contrato del servicio)  <->  Entity (esquema de Room)  <->  Recordatorio (dominio / interfaz)
 * Los DTO nunca llegan a la interfaz y la interfaz nunca ve entidades.
 */

internal fun Long.aFecha(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())
internal fun LocalDateTime.aMillis(): Long = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

internal fun Prioridad.aCodigo(): String = when (this) {
    Prioridad.Alta -> "ALTA"
    Prioridad.Media -> "MEDIA"
    Prioridad.Baja -> "BAJA"
}

internal fun codigoAPrioridad(codigo: String): Prioridad? = when (codigo) {
    "ALTA" -> Prioridad.Alta
    "MEDIA" -> Prioridad.Media
    "BAJA" -> Prioridad.Baja
    else -> null
}

// ---------- Entity -> dominio ----------

/** Lee una fila de Room. Si hay un valor inesperado (base dañada o de otra versión) usa un valor por defecto en vez de fallar. */
fun RecordatorioEntity.aDominio(): Recordatorio = Recordatorio(
    id = id,
    titulo = titulo.trim().padEnd(Recordatorio.TITULO_MIN, '.').take(Recordatorio.TITULO_MAX),
    categoria = Categoria(categoria),
    prioridad = codigoAPrioridad(prioridad) ?: Prioridad.Media,
    estado = when (estado) {
        RecordatorioEntity.ESTADO_EN_PROGRESO -> EstadoRecordatorio.EnProgreso
        RecordatorioEntity.ESTADO_COMPLETADO -> EstadoRecordatorio.Completado((completadoEn ?: actualizadoEn).aFecha())
        else -> EstadoRecordatorio.Pendiente
    },
    descripcion = descripcion?.take(Recordatorio.DESCRIPCION_MAX),
    fechaLimite = fechaLimite?.aFecha(),
    ubicacion = ubicacion,
    creado = creadoEn.aFecha(),
    sincronizacion = syncEstado,
    errorSincronizacion = errorSync,
)

// ---------- Dominio -> Entity ----------

fun Recordatorio.aEntidad(
    ownerUid: String,
    clientId: String,
    remoteId: String? = null,
    actualizadoEn: Long = System.currentTimeMillis(),
): RecordatorioEntity =
    RecordatorioEntity(
        id = id,
        ownerUid = ownerUid,
        clientId = clientId,
        remoteId = remoteId,
        titulo = titulo,
        descripcion = descripcion,
        categoria = categoria.nombre,
        prioridad = prioridad.aCodigo(),
        estado = estado.aCodigo(),
        completadoEn = (estado as? EstadoRecordatorio.Completado)?.fecha?.aMillis(),
        fechaLimite = fechaLimite?.aMillis(),
        ubicacion = ubicacion,
        creadoEn = creado.aMillis(),
        actualizadoEn = actualizadoEn,
        syncEstado = sincronizacion,
        errorSync = errorSincronizacion,
    )

internal fun EstadoRecordatorio.aCodigo(): String = when (this) {
    EstadoRecordatorio.Pendiente -> RecordatorioEntity.ESTADO_PENDIENTE
    EstadoRecordatorio.EnProgreso -> RecordatorioEntity.ESTADO_EN_PROGRESO
    is EstadoRecordatorio.Completado -> RecordatorioEntity.ESTADO_COMPLETADO
}

// ---------- Entity <-> DTO ----------

fun RecordatorioEntity.aDto(): RecordatorioDto = RecordatorioDto(
    id = remoteId,
    clientId = clientId,
    titulo = titulo,
    descripcion = descripcion,
    categoria = categoria,
    prioridad = prioridad,
    estado = estado,
    completadoEn = completadoEn,
    fechaLimite = fechaLimite,
    ubicacion = ubicacion,
    creadoEn = creadoEn,
    actualizadoEn = actualizadoEn,
)

/**
 * Convierte lo que devolvió el servicio en una fila de Room, ya marcada como sincronizada.
 * Devuelve null si los datos no son válidos (así nunca llegan a la base).
 * [local] es la fila existente, si la hay: se conservan su id, su fecha de creación y su clientId.
 */
fun RecordatorioDto.aEntidad(ownerUid: String, local: RecordatorioEntity?, ahora: Long = System.currentTimeMillis()): RecordatorioEntity? {
    val remoto = id ?: return null
    if (clientId.isBlank()) return null
    if (titulo.trim().length < Recordatorio.TITULO_MIN || titulo.length > Recordatorio.TITULO_MAX) return null
    if ((descripcion?.length ?: 0) > Recordatorio.DESCRIPCION_MAX) return null
    if (Categoria(categoria) !in Categoria.disponibles) return null
    if (codigoAPrioridad(prioridad) == null) return null
    if (estado !in setOf(RecordatorioEntity.ESTADO_PENDIENTE, RecordatorioEntity.ESTADO_EN_PROGRESO, RecordatorioEntity.ESTADO_COMPLETADO)) return null
    return RecordatorioEntity(
        id = local?.id ?: 0,
        ownerUid = ownerUid,
        clientId = local?.clientId ?: clientId,
        remoteId = remoto,
        titulo = titulo.trim(),
        descripcion = descripcion?.takeIf { it.isNotBlank() },
        categoria = categoria,
        prioridad = prioridad,
        estado = estado,
        completadoEn = completadoEn,
        fechaLimite = fechaLimite,
        ubicacion = ubicacion,
        creadoEn = local?.creadoEn ?: creadoEn.takeIf { it > 0 } ?: ahora,
        actualizadoEn = actualizadoEn.takeIf { it > 0 } ?: ahora,
        syncEstado = EstadoSync.SINCRONIZADO,
        errorSync = null,
        eliminado = false,
    )
}
