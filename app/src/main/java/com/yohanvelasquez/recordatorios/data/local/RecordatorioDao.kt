package com.yohanvelasquez.recordatorios.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordatorioDao {

    // --- Altas, bajas y cambios ---

    /** Devuelve el id nuevo, o -1 si ya existía un registro con el mismo clientId. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertar(entidad: RecordatorioEntity): Long

    @Update
    suspend fun actualizar(entidad: RecordatorioEntity)

    @Delete
    suspend fun eliminar(entidad: RecordatorioEntity)

    @Query("DELETE FROM recordatorios WHERE id = :id")
    suspend fun eliminarDefinitivo(id: Int)

    // --- Consultas para la interfaz (Flow: se actualizan solas cuando cambia la tabla) ---

    @Query("SELECT * FROM recordatorios WHERE eliminado = 0 ORDER BY creadoEn DESC, id DESC")
    fun observarTodos(): Flow<List<RecordatorioEntity>>

    @Query("SELECT * FROM recordatorios WHERE eliminado = 0 AND estado = :estado ORDER BY creadoEn DESC, id DESC")
    fun observarPorEstado(estado: String): Flow<List<RecordatorioEntity>>

    @Query("SELECT * FROM recordatorios WHERE eliminado = 0 AND categoria = :categoria ORDER BY creadoEn DESC, id DESC")
    fun observarPorCategoria(categoria: String): Flow<List<RecordatorioEntity>>

    @Query("SELECT * FROM recordatorios WHERE eliminado = 0 AND prioridad = :prioridad ORDER BY creadoEn DESC, id DESC")
    fun observarPorPrioridad(prioridad: String): Flow<List<RecordatorioEntity>>

    @Query("SELECT * FROM recordatorios WHERE id = :id AND eliminado = 0")
    fun observarPorId(id: Int): Flow<RecordatorioEntity?>

    @Query("SELECT COUNT(*) FROM recordatorios WHERE eliminado = 0")
    fun observarCantidad(): Flow<Int>

    @Query("SELECT * FROM recordatorios WHERE id = :id")
    suspend fun obtenerPorId(id: Int): RecordatorioEntity?

    // --- Soporte de la sincronización ---

    @Query("SELECT * FROM recordatorios WHERE syncEstado = 'PENDIENTE' ORDER BY actualizadoEn ASC, id ASC")
    suspend fun obtenerPendientes(): List<RecordatorioEntity>

    @Query("SELECT * FROM recordatorios WHERE syncEstado = 'SINCRONIZADO'")
    suspend fun obtenerSincronizados(): List<RecordatorioEntity>

    @Query("SELECT * FROM recordatorios WHERE clientId = :clientId LIMIT 1")
    suspend fun buscarPorClientId(clientId: String): RecordatorioEntity?

    @Query("SELECT * FROM recordatorios WHERE remoteId = :remoteId LIMIT 1")
    suspend fun buscarPorRemoteId(remoteId: String): RecordatorioEntity?

    /**
     * Guarda el id remoto y solo marca como sincronizado si el registro no cambió mientras se enviaba
     * (comparando su versión). Si cambió, sigue pendiente y se enviará de nuevo con el id remoto.
     */
    @Query(
        "UPDATE recordatorios SET remoteId = :remoteId, errorSync = NULL, " +
            "syncEstado = CASE WHEN actualizadoEn = :version THEN 'SINCRONIZADO' ELSE syncEstado END WHERE id = :id",
    )
    suspend fun marcarTrasEnvio(id: Int, remoteId: String, version: Long)

    @Query("UPDATE recordatorios SET syncEstado = 'ERROR', errorSync = :mensaje WHERE id = :id AND actualizadoEn = :version")
    suspend fun marcarError(id: Int, mensaje: String, version: Long)

    @Query("UPDATE recordatorios SET remoteId = NULL WHERE id = :id")
    suspend fun limpiarRemoteId(id: Int)

    @Query("UPDATE recordatorios SET eliminado = 1, syncEstado = 'PENDIENTE', errorSync = NULL, actualizadoEn = :ahora WHERE id = :id")
    suspend fun marcarEliminado(id: Int, ahora: Long)

    @Query("UPDATE recordatorios SET syncEstado = 'PENDIENTE', errorSync = NULL WHERE syncEstado = 'ERROR'")
    suspend fun reintentarFallidos()
}
