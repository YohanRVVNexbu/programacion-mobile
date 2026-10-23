package com.yohanvelasquez.recordatorios.model

import java.time.LocalDateTime

/** Categoría de un recordatorio. Al ser data class, dos categorías con el mismo nombre son iguales (útil en un Set). */
data class Categoria(val nombre: String)

/** Prioridades posibles: jerarquía cerrada, no se pueden crear valores fuera de estas tres. */
sealed class Prioridad(val etiqueta: String, val peso: Int) {
    data object Alta : Prioridad("Alta", 3)
    data object Media : Prioridad("Media", 2)
    data object Baja : Prioridad("Baja", 1)
}

/** Estado de un recordatorio. Completado guarda cuándo se terminó. */
sealed class EstadoRecordatorio(val etiqueta: String) {
    data object Pendiente : EstadoRecordatorio("Pendiente")
    data object EnProgreso : EstadoRecordatorio("En progreso")
    data class Completado(val fecha: LocalDateTime) : EstadoRecordatorio("Completado")
}

/** Recordatorio inmutable. Los datos opcionales (descripción, fecha límite, ubicación) son nullables. */
data class Recordatorio(
    val id: Int,
    val titulo: String,
    val categoria: Categoria,
    val prioridad: Prioridad,
    val estado: EstadoRecordatorio = EstadoRecordatorio.Pendiente,
    val descripcion: String? = null,
    val fechaLimite: LocalDateTime? = null,
    val ubicacion: String? = null,
) {
    init {
        // Última barrera: nunca puede existir un recordatorio en estado inválido.
        require(id > 0) { "El id debe ser positivo" }
        require(titulo.isNotBlank()) { "El título es obligatorio" }
        require(titulo.length <= MAX_TITULO) { "El título no puede superar $MAX_TITULO caracteres" }
    }

    companion object {
        const val MAX_TITULO = 60
    }
}

/** Resultado de intentar crear un recordatorio a partir de datos de entrada. */
sealed interface ResultadoValidacion {
    data class Valido(val recordatorio: Recordatorio) : ResultadoValidacion
    data class Invalido(val errores: List<String>) : ResultadoValidacion
}

/** Estados de la pantalla principal. */
sealed interface EstadoUi {
    data object Cargando : EstadoUi
    data object Vacio : EstadoUi
    data class Exito(val recordatorios: List<Recordatorio>) : EstadoUi
    data class Error(val mensaje: String) : EstadoUi
}

/**
 * Valida los datos antes de construir el recordatorio y devuelve todos los errores juntos
 * en lugar de lanzar una excepción.
 */
fun crearRecordatorio(
    id: Int,
    titulo: String,
    categoria: Categoria,
    prioridad: Prioridad,
    estado: EstadoRecordatorio = EstadoRecordatorio.Pendiente,
    descripcion: String? = null,
    fechaLimite: LocalDateTime? = null,
    ubicacion: String? = null,
    ahora: LocalDateTime = LocalDateTime.now(),
): ResultadoValidacion {
    val errores = buildList {
        if (id <= 0) add("El id debe ser positivo")
        if (titulo.isBlank()) add("El título es obligatorio")
        if (titulo.length > Recordatorio.MAX_TITULO) add("El título no puede superar ${Recordatorio.MAX_TITULO} caracteres")
        if (categoria.nombre.isBlank()) add("La categoría es obligatoria")
        if (fechaLimite != null && estado !is EstadoRecordatorio.Completado && fechaLimite.isBefore(ahora)) {
            add("La fecha límite de un recordatorio pendiente no puede estar en el pasado")
        }
    }
    return if (errores.isEmpty()) {
        ResultadoValidacion.Valido(
            Recordatorio(id, titulo.trim(), categoria, prioridad, estado, descripcion?.takeIf { it.isNotBlank() }, fechaLimite, ubicacion?.takeIf { it.isNotBlank() })
        )
    } else {
        ResultadoValidacion.Invalido(errores)
    }
}
