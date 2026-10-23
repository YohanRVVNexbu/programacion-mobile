package com.yohanvelasquez.recordatorios.data.remote

/**
 * Lo que el sincronizador necesita del servicio remoto. El sincronizador no sabe si detrás hay Firestore,
 * una API REST (como en la Actividad 3) o un doble de pruebas.
 */
interface FuenteRemota {
    suspend fun listar(): ResultadoRed<List<RecordatorioDto>>
    suspend fun crear(dto: RecordatorioDto): ResultadoRed<RecordatorioDto>
    suspend fun actualizar(id: String, dto: RecordatorioDto): ResultadoRed<RecordatorioDto>
    suspend fun eliminar(id: String): ResultadoRed<Unit>
}
