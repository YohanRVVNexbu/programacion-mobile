package com.yohanvelasquez.recordatorios.data

import com.yohanvelasquez.recordatorios.model.Categoria
import com.yohanvelasquez.recordatorios.model.Prioridad
import com.yohanvelasquez.recordatorios.model.Recordatorio
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import java.io.IOException

/** Operaciones que la app necesita sobre los recordatorios, sin importar de dónde vengan los datos. */
interface RecordatorioRepository {
    fun observarRecordatorios(): Flow<List<Recordatorio>>
    suspend fun obtenerRecordatorio(id: Int): Recordatorio?
    suspend fun guardarRecordatorio(
        titulo: String,
        descripcion: String?,
        categoria: Categoria,
        prioridad: Prioridad,
    ): Recordatorio
}

/** Situaciones que se pueden forzar desde la app para comprobar cada estado de la interfaz. */
enum class ModoSimulacion(val etiqueta: String) {
    NORMAL("Datos normales"),
    VACIO("Lista vacía"),
    ERROR_CARGA("Error al cargar"),
    ERROR_GUARDADO("Error al guardar"),
}

class ControlSimulacion(var modo: ModoSimulacion = ModoSimulacion.NORMAL)

/** Implementación en memoria: los datos se pierden al cerrar la app. Las demoras simulan una red lenta. */
class RecordatorioRepositoryEnMemoria(
    iniciales: List<Recordatorio>,
    private val simulacion: ControlSimulacion,
    private val demoraMs: Long = 1_200,
) : RecordatorioRepository {

    private val datos = MutableStateFlow(iniciales)

    override fun observarRecordatorios(): Flow<List<Recordatorio>> = flow {
        delay(demoraMs)
        when (simulacion.modo) {
            ModoSimulacion.ERROR_CARGA -> throw IOException("No se pudieron cargar los recordatorios. Revisa la conexión e intenta de nuevo.")
            ModoSimulacion.VACIO -> emit(emptyList())
            else -> emitAll(datos)
        }
    }

    override suspend fun obtenerRecordatorio(id: Int): Recordatorio? {
        delay(demoraMs / 2)
        if (simulacion.modo == ModoSimulacion.ERROR_CARGA) {
            throw IOException("No se pudo cargar el recordatorio. Intenta de nuevo.")
        }
        return datos.value.find { it.id == id }
    }

    override suspend fun guardarRecordatorio(
        titulo: String,
        descripcion: String?,
        categoria: Categoria,
        prioridad: Prioridad,
    ): Recordatorio {
        delay(demoraMs)
        if (simulacion.modo == ModoSimulacion.ERROR_GUARDADO) {
            throw IOException("No se pudo guardar el recordatorio. Intenta de nuevo en unos segundos.")
        }
        lateinit var nuevo: Recordatorio
        datos.update { lista ->
            nuevo = Recordatorio(
                id = (lista.maxOfOrNull { it.id } ?: 0) + 1,
                titulo = titulo,
                categoria = categoria,
                prioridad = prioridad,
                descripcion = descripcion,
            )
            lista + nuevo
        }
        return nuevo
    }
}
