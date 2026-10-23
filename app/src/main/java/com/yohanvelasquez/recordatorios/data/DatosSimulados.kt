package com.yohanvelasquez.recordatorios.data

import androidx.compose.ui.graphics.Color
import com.yohanvelasquez.recordatorios.model.Categoria
import com.yohanvelasquez.recordatorios.model.EstadoRecordatorio
import com.yohanvelasquez.recordatorios.model.Prioridad
import com.yohanvelasquez.recordatorios.model.Recordatorio
import com.yohanvelasquez.recordatorios.model.ResultadoValidacion
import com.yohanvelasquez.recordatorios.model.crearRecordatorio
import java.time.LocalDateTime

object DatosSimulados {

    val trabajo = Categoria("Trabajo")
    val estudio = Categoria("Estudio")
    val salud = Categoria("Salud")
    val hogar = Categoria("Hogar")

    /** Map: cada categoría asociada a su color de etiqueta. */
    val coloresPorCategoria: Map<Categoria, Color> = mapOf(
        trabajo to Color(0xFF1565C0),
        estudio to Color(0xFF6A1B9A),
        salud to Color(0xFF2E7D32),
        hogar to Color(0xFFEF6C00),
    )

    private val ahora = LocalDateTime.now()

    /** Datos de entrada simulados; dos de ellos incumplen las reglas a propósito. */
    private val intentos: List<ResultadoValidacion> = listOf(
        crearRecordatorio(1, "Entregar informe trimestral", trabajo, Prioridad.Alta,
            descripcion = "Enviar al coordinador regional", fechaLimite = ahora.plusDays(2)),
        crearRecordatorio(2, "Estudiar Kotlin: sealed classes", estudio, Prioridad.Media,
            estado = EstadoRecordatorio.EnProgreso),
        crearRecordatorio(3, "Cita médica de control", salud, Prioridad.Alta,
            fechaLimite = ahora.plusDays(5), ubicacion = "Centro de salud norte"),
        crearRecordatorio(4, "Pagar servicios del hogar", hogar, Prioridad.Media,
            estado = EstadoRecordatorio.Completado(ahora.minusDays(1))),
        crearRecordatorio(5, "Reunión de equipo Android", trabajo, Prioridad.Baja,
            ubicacion = "Sala 2"),
        crearRecordatorio(6, "Comprar frutas y verduras", hogar, Prioridad.Baja),
        crearRecordatorio(7, "Repasar Jetpack Compose", estudio, Prioridad.Alta,
            estado = EstadoRecordatorio.Completado(ahora.minusHours(3))),
        // Inválidos: título vacío y fecha límite vencida.
        crearRecordatorio(8, "   ", salud, Prioridad.Media),
        crearRecordatorio(9, "Renovar licencia", trabajo, Prioridad.Alta, fechaLimite = ahora.minusDays(1)),
    )

    /** List: solo los recordatorios que pasaron la validación. */
    val recordatorios: List<Recordatorio> =
        intentos.filterIsInstance<ResultadoValidacion.Valido>().map { it.recordatorio }

    /** Errores de los datos rechazados, para mostrarlos en pantalla. */
    val rechazados: List<String> =
        intentos.filterIsInstance<ResultadoValidacion.Invalido>().flatMap { it.errores }

    /** Set: categorías en uso sin repetir, aunque varios recordatorios compartan categoría. */
    val categorias: Set<Categoria> = recordatorios.map { it.categoria }.toSet()

    fun filtrarPorEstado(lista: List<Recordatorio>, estado: Class<out EstadoRecordatorio>?): List<Recordatorio> =
        if (estado == null) lista else lista.filter { estado.isInstance(it.estado) }

    fun filtrarPorPrioridad(lista: List<Recordatorio>, prioridad: Prioridad): List<Recordatorio> =
        lista.filter { it.prioridad == prioridad }

    /** Map: cantidad de recordatorios por prioridad. */
    fun contarPorPrioridad(lista: List<Recordatorio>): Map<Prioridad, Int> =
        lista.groupingBy { it.prioridad }.eachCount()
}
