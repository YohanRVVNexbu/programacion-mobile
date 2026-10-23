package com.yohanvelasquez.recordatorios.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Todas las consultas que devuelven datos reciben el uid de la cuenta: nunca se mezclan registros de cuentas distintas. */
@Dao
interface RecordatorioDao {

    // --- Altas, bajas y cambios ---

    /** Devuelve el id nuevo, o -1 si esa cuenta ya tenía un registro con el mismo clientId. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertar(entidad: RecordatorioEntity): Long

    @Update
    suspend fun actualizar(entidad: RecordatorioEntity)

    @Delete
    suspend fun eliminar(entidad: RecordatorioEntity)

    @Query("DELETE FROM recordatorios WHERE id = :id")
    suspend fun eliminarDefinitivo(id: Int)

    /** Borra todo lo de una cuenta (cierre de sesión). Con uid vacío limpia los datos anteriores a las cuentas. */
    @Query("DELETE FROM recordatorios WHERE ownerUid = :uid")
    suspend fun eliminarTodoDe(uid: String)

    // --- Consultas para la interfaz (Flow: se actualizan solas cuando cambia la tabla) ---

    @Query("SELECT * FROM recordatorios WHERE ownerUid = :uid AND eliminado = 0 ORDER BY creadoEn DESC, id DESC")
    fun observarTodos(uid: String): Flow<List<RecordatorioEntity>>

    @Query("SELECT * FROM recordatorios WHERE ownerUid = :uid AND eliminado = 0 AND estado = :estado ORDER BY creadoEn DESC, id DESC")
    fun observarPorEstado(uid: String, estado: String): Flow<List<RecordatorioEntity>>

    @Query("SELECT * FROM recordatorios WHERE ownerUid = :uid AND eliminado = 0 AND categoria = :categoria ORDER BY creadoEn DESC, id DESC")
    fun observarPorCategoria(uid: String, categoria: String): Flow<List<RecordatorioEntity>>

    @Query("SELECT * FROM recordatorios WHERE ownerUid = :uid AND eliminado = 0 AND prioridad = :prioridad ORDER BY creadoEn DESC, id DESC")
    fun observarPorPrioridad(uid: String, prioridad: String): Flow<List<RecordatorioEntity>>

    @Query("SELECT * FROM recordatorios WHERE ownerUid = :uid AND id = :id AND eliminado = 0")
    fun observarPorId(uid: String, id: Int): Flow<RecordatorioEntity?>

    @Query("SELECT COUNT(*) FROM recordatorios WHERE ownerUid = :uid AND eliminado = 0")
    fun observarCantidad(uid: String): Flow<Int>

    /** Cambios que todavía no confirmó el servicio (pendientes o con error), incluidos los borrados lógicos. */
    @Query("SELECT COUNT(*) FROM recordatorios WHERE ownerUid = :uid AND syncEstado != 'SINCRONIZADO'")
    fun observarSinSincronizar(uid: String): Flow<Int>

    @Query("SELECT * FROM recordatorios WHERE ownerUid = :uid AND id = :id")
    suspend fun obtenerPorId(uid: String, id: Int): RecordatorioEntity?

    // --- Soporte de la sincronización ---

    @Query("SELECT * FROM recordatorios WHERE ownerUid = :uid AND syncEstado = 'PENDIENTE' ORDER BY actualizadoEn ASC, id ASC")
    suspend fun obtenerPendientes(uid: String): List<RecordatorioEntity>

    @Query("SELECT * FROM recordatorios WHERE ownerUid = :uid AND syncEstado = 'SINCRONIZADO'")
    suspend fun obtenerSincronizados(uid: String): List<RecordatorioEntity>

    @Query("SELECT * FROM recordatorios WHERE ownerUid = :uid AND clientId = :clientId LIMIT 1")
    suspend fun buscarPorClientId(uid: String, clientId: String): RecordatorioEntity?

    @Query("SELECT * FROM recordatorios WHERE ownerUid = :uid AND remoteId = :remoteId LIMIT 1")
    suspend fun buscarPorRemoteId(uid: String, remoteId: String): RecordatorioEntity?

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

    @Query("UPDATE recordatorios SET syncEstado = 'PENDIENTE', errorSync = NULL WHERE ownerUid = :uid AND syncEstado = 'ERROR'")
    suspend fun reintentarFallidos(uid: String)
}
