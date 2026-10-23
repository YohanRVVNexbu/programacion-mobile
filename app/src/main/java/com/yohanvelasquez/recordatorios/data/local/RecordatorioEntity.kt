package com.yohanvelasquez.recordatorios.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yohanvelasquez.recordatorios.model.EstadoSync

/**
 * Esquema de Room. Es independiente del modelo de dominio y del DTO del servicio.
 *
 * - [id]: identificador estable local (el que viaja en la navegación).
 * - [clientId]: UUID generado al crear; el servicio lo usa para no duplicar si la operación se reintenta.
 * - [remoteId]: identificador asignado por el servicio; null mientras no se confirmó la creación.
 * - [eliminado]: borrado lógico hasta que el servicio confirme el borrado.
 */
@Entity(tableName = "recordatorios", indices = [Index(value = ["clientId"], unique = true)])
data class RecordatorioEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val clientId: String,
    val remoteId: String? = null,
    val titulo: String,
    val descripcion: String? = null,
    val categoria: String,
    val prioridad: String,
    val estado: String = ESTADO_PENDIENTE,
    val completadoEn: Long? = null,
    val fechaLimite: Long? = null,
    val ubicacion: String? = null,
    val creadoEn: Long,
    val actualizadoEn: Long,
    val syncEstado: EstadoSync = EstadoSync.PENDIENTE,
    val errorSync: String? = null,
    val eliminado: Boolean = false,
) {
    companion object {
        const val ESTADO_PENDIENTE = "PENDIENTE"
        const val ESTADO_EN_PROGRESO = "EN_PROGRESO"
        const val ESTADO_COMPLETADO = "COMPLETADO"
    }
}
