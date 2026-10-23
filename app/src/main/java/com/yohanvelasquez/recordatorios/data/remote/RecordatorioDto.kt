package com.yohanvelasquez.recordatorios.data.remote

/** Contrato del servicio remoto. No se usa fuera de la capa de datos. */
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
    val actualizadoEn: Long = 0,
)
