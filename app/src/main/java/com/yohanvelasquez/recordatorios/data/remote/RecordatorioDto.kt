package com.yohanvelasquez.recordatorios.data.remote

/**
 * Contrato con el servicio remoto (Cloud Firestore). No se usa fuera de la capa de datos.
 * [id] es el identificador del documento y coincide con el clientId local: así un reintento sobrescribe el mismo documento.
 */
data class RecordatorioDto(
    val id: String? = null,
    val clientId: String,
    val titulo: String,
    val descripcion: String? = null,
    val categoria: String,
    val prioridad: String,
    val estado: String,
    val completadoEn: Long? = null,
    val fechaLimite: Long? = null,
    val ubicacion: String? = null,
    val creadoEn: Long = 0,
    val actualizadoEn: Long = 0,
)
