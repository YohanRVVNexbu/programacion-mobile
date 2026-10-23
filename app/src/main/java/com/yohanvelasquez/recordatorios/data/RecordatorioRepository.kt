package com.yohanvelasquez.recordatorios.data

import com.yohanvelasquez.recordatorios.data.sync.EstadoSincronizacion
import com.yohanvelasquez.recordatorios.model.Categoria
import com.yohanvelasquez.recordatorios.model.FiltroEstado
import com.yohanvelasquez.recordatorios.model.ListadoRecordatorios
import com.yohanvelasquez.recordatorios.model.Prioridad
import com.yohanvelasquez.recordatorios.model.Recordatorio
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Lo que la app necesita sobre los recordatorios. Los ViewModels solo conocen esta interfaz:
 * no saben si los datos vienen de Room, del servicio o de ambos.
 */
interface RecordatorioRepository {
    fun observarRecordatorios(filtro: FiltroEstado = FiltroEstado.TODOS): Flow<ListadoRecordatorios>
    fun observarRecordatorio(id: Int): Flow<Recordatorio?>
    suspend fun obtenerRecordatorio(id: Int): Recordatorio?

    suspend fun crear(titulo: String, descripcion: String?, categoria: Categoria, prioridad: Prioridad): Int
    suspend fun actualizar(id: Int, titulo: String, descripcion: String?, categoria: Categoria, prioridad: Prioridad)
    suspend fun cambiarCompletado(id: Int, completado: Boolean)
    suspend fun eliminar(id: Int)

    val estadoSincronizacion: StateFlow<EstadoSincronizacion>
    fun solicitarSincronizacion()
    suspend fun reintentarFallidos()
}
