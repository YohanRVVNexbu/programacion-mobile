package com.yohanvelasquez.recordatorios.data

import androidx.compose.ui.graphics.Color
import com.yohanvelasquez.recordatorios.model.Categoria
import com.yohanvelasquez.recordatorios.model.EstadoRecordatorio
import com.yohanvelasquez.recordatorios.model.EstadoSync
import com.yohanvelasquez.recordatorios.model.Prioridad
import com.yohanvelasquez.recordatorios.model.Recordatorio
import java.time.LocalDateTime

/** Colores por categoría y datos de ejemplo para las vistas previas de Compose (la app real usa Room). */
object DatosSimulados {

    /** Map: cada categoría asociada a su color de etiqueta. */
    val coloresPorCategoria: Map<Categoria, Color> = mapOf(
        Categoria.Trabajo to Color(0xFF1565C0),
        Categoria.Estudio to Color(0xFF6A1B9A),
        Categoria.Salud to Color(0xFF2E7D32),
        Categoria.Hogar to Color(0xFFEF6C00),
    )

    private val ahora = LocalDateTime.now()

    val recordatorios: List<Recordatorio> = listOf(
        Recordatorio(1, "Entregar informe trimestral", Categoria.Trabajo, Prioridad.Alta,
            descripcion = "Enviar al coordinador regional.", sincronizacion = EstadoSync.PENDIENTE),
        Recordatorio(2, "Estudiar Room y DataStore", Categoria.Estudio, Prioridad.Media,
            estado = EstadoRecordatorio.EnProgreso),
        Recordatorio(3, "Cita médica de control", Categoria.Salud, Prioridad.Alta,
            sincronizacion = EstadoSync.ERROR, errorSincronizacion = "El servicio rechazó los datos enviados (código 400)."),
        Recordatorio(4, "Pagar servicios del hogar", Categoria.Hogar, Prioridad.Media,
            estado = EstadoRecordatorio.Completado(ahora.minusDays(1))),
    )
}
