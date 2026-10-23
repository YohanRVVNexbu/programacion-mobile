package com.yohanvelasquez.recordatorios.data

import com.yohanvelasquez.recordatorios.data.local.RecordatorioDao
import com.yohanvelasquez.recordatorios.data.local.RecordatorioEntity
import com.yohanvelasquez.recordatorios.data.preferencias.PreferenciasRepository
import com.yohanvelasquez.recordatorios.data.sync.EstadoSincronizacion
import com.yohanvelasquez.recordatorios.data.sync.Sincronizador
import com.yohanvelasquez.recordatorios.model.Categoria
import com.yohanvelasquez.recordatorios.model.EstadoRecordatorio
import com.yohanvelasquez.recordatorios.model.EstadoSync
import com.yohanvelasquez.recordatorios.model.FiltroEstado
import com.yohanvelasquez.recordatorios.model.ListadoRecordatorios
import com.yohanvelasquez.recordatorios.model.Prioridad
import com.yohanvelasquez.recordatorios.model.Recordatorio
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Repositorio local-first: Room es la fuente de verdad de la interfaz.
 * Cada operación se aplica primero en Room y queda marcada como pendiente; el [sincronizador] la envía al servicio
 * cuando hay conexión. Las preferencias de DataStore (ocultar completados, orden) se aplican al listado aquí.
 */
class RecordatorioRepositoryLocalFirst(
    private val dao: RecordatorioDao,
    private val preferencias: PreferenciasRepository,
    private val sincronizador: Sincronizador,
    private val reloj: () -> Long = System::currentTimeMillis,
    private val nuevoClientId: () -> String = { UUID.randomUUID().toString() },
) : RecordatorioRepository {

    override fun observarRecordatorios(filtro: FiltroEstado): Flow<ListadoRecordatorios> {
        val filas = when (filtro) {
            FiltroEstado.TODOS -> dao.observarTodos()
            FiltroEstado.PENDIENTE -> dao.observarPorEstado(RecordatorioEntity.ESTADO_PENDIENTE)
            FiltroEstado.EN_PROGRESO -> dao.observarPorEstado(RecordatorioEntity.ESTADO_EN_PROGRESO)
            FiltroEstado.COMPLETADO -> dao.observarPorEstado(RecordatorioEntity.ESTADO_COMPLETADO)
        }
        return combine(filas, dao.observarCantidad(), preferencias.preferencias) { entidades, total, prefs ->
            val items = entidades.map { it.aDominio() }
                .filter { prefs.mostrarCompletados || it.estado !is EstadoRecordatorio.Completado }
                .sortedWith(prefs.orden.comparador)
            ListadoRecordatorios(items, hayRegistros = total > 0)
        }
    }

    override fun observarRecordatorio(id: Int): Flow<Recordatorio?> = dao.observarPorId(id).map { it?.aDominio() }

    override suspend fun obtenerRecordatorio(id: Int): Recordatorio? =
        dao.obtenerPorId(id)?.takeIf { !it.eliminado }?.aDominio()

    override suspend fun crear(titulo: String, descripcion: String?, categoria: Categoria, prioridad: Prioridad): Int {
        val ahora = reloj()
        val id = dao.insertar(
            RecordatorioEntity(
                clientId = nuevoClientId(),
                titulo = titulo.trim(),
                descripcion = descripcion?.trim()?.ifBlank { null },
                categoria = categoria.nombre,
                prioridad = prioridad.aCodigo(),
                creadoEn = ahora,
                actualizadoEn = ahora,
                syncEstado = EstadoSync.PENDIENTE,
            ),
        ).toInt()
        sincronizador.solicitar()
        return id
    }

    override suspend fun actualizar(id: Int, titulo: String, descripcion: String?, categoria: Categoria, prioridad: Prioridad) {
        modificar(id) {
            it.copy(
                titulo = titulo.trim(),
                descripcion = descripcion?.trim()?.ifBlank { null },
                categoria = categoria.nombre,
                prioridad = prioridad.aCodigo(),
            )
        }
    }

    override suspend fun cambiarCompletado(id: Int, completado: Boolean) {
        val ahora = reloj()
        modificar(id) {
            if (completado) it.copy(estado = RecordatorioEntity.ESTADO_COMPLETADO, completadoEn = ahora)
            else it.copy(estado = RecordatorioEntity.ESTADO_PENDIENTE, completadoEn = null)
        }
    }

    /** Aplica el cambio en Room, deja el registro pendiente con una versión nueva y pide sincronizar. */
    private suspend fun modificar(id: Int, cambio: (RecordatorioEntity) -> RecordatorioEntity) {
        val actual = dao.obtenerPorId(id)?.takeIf { !it.eliminado } ?: return
        val version = maxOf(reloj(), actual.actualizadoEn + 1) // siempre distinta a la anterior
        dao.actualizar(cambio(actual).copy(actualizadoEn = version, syncEstado = EstadoSync.PENDIENTE, errorSync = null))
        sincronizador.solicitar()
    }

    /** Borrado lógico: la fila desaparece de las consultas y el sincronizador confirma el borrado con el servicio. */
    override suspend fun eliminar(id: Int) {
        dao.marcarEliminado(id, reloj())
        sincronizador.solicitar()
    }

    override val estadoSincronizacion: StateFlow<EstadoSincronizacion> get() = sincronizador.estado

    override fun solicitarSincronizacion() = sincronizador.solicitar()

    override suspend fun reintentarFallidos() {
        dao.reintentarFallidos()
        sincronizador.solicitar()
    }
}
