package com.yohanvelasquez.recordatorios.data

import androidx.compose.ui.graphics.Color
import com.yohanvelasquez.recordatorios.model.Categoria
import com.yohanvelasquez.recordatorios.model.EstadoRecordatorio
import com.yohanvelasquez.recordatorios.model.Prioridad
import com.yohanvelasquez.recordatorios.model.Recordatorio
import java.time.LocalDateTime

object DatosSimulados {

    /** Map: cada categoría asociada a su color de etiqueta. */
    val coloresPorCategoria: Map<Categoria, Color> = mapOf(
        Categoria.Trabajo to Color(0xFF1565C0),
        Categoria.Estudio to Color(0xFF6A1B9A),
        Categoria.Salud to Color(0xFF2E7D32),
        Categoria.Hogar to Color(0xFFEF6C00),
    )

    private val ahora = LocalDateTime.now()

    /** Datos iniciales del repositorio en memoria. */
    val recordatorios: List<Recordatorio> = listOf(
        Recordatorio(1, "Entregar informe trimestral", Categoria.Trabajo, Prioridad.Alta,
            descripcion = "Enviar al coordinador regional con los indicadores del trimestre.", fechaLimite = ahora.plusDays(2)),
        Recordatorio(2, "Estudiar Navigation Compose", Categoria.Estudio, Prioridad.Media,
            estado = EstadoRecordatorio.EnProgreso, descripcion = "Repasar rutas con argumentos y popBackStack."),
        Recordatorio(3, "Cita médica de control", Categoria.Salud, Prioridad.Alta,
            fechaLimite = ahora.plusDays(5), ubicacion = "Centro de salud norte"),
        Recordatorio(4, "Pagar servicios del hogar", Categoria.Hogar, Prioridad.Media,
            estado = EstadoRecordatorio.Completado(ahora.minusDays(1))),
        Recordatorio(5, "Reunión de equipo Android", Categoria.Trabajo, Prioridad.Baja, ubicacion = "Sala 2"),
        Recordatorio(6, "Comprar frutas y verduras", Categoria.Hogar, Prioridad.Baja),
    )
}
