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
 * Lo que la app necesita sobre los recordatorios de la cuenta activa. Los ViewModels solo conocen esta interfaz:
 * no saben si los datos vienen de Room, de Firestore o de ambos.
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

    /** Cantidad de cambios de la cuenta activa que el servicio todavía no confirmó. */
    fun observarSinSincronizar(): Flow<Int>

    /** Borra del dispositivo todo lo de la cuenta activa. Se usa al cerrar sesión. */
    suspend fun limpiarDatosLocales()

    /** Borra del dispositivo todo lo de una cuenta concreta (por ejemplo, la que acaba de dejar de tener sesión). */
    suspend fun limpiarDatosDe(uid: String)

    /** Borra los registros locales que no pertenecen a ninguna cuenta (datos de versiones anteriores). */
    suspend fun descartarDatosSinCuenta()
}
