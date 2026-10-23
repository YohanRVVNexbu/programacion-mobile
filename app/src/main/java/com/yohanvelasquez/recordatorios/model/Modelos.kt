package com.yohanvelasquez.recordatorios.model

import java.time.LocalDateTime

/** Categoría de un recordatorio. Al ser data class, dos categorías con el mismo nombre son iguales (útil en un Set). */
data class Categoria(val nombre: String) {
    companion object {
        val Trabajo = Categoria("Trabajo")
        val Estudio = Categoria("Estudio")
        val Salud = Categoria("Salud")
        val Hogar = Categoria("Hogar")

        /** Únicas categorías válidas en la aplicación. */
        val disponibles: List<Categoria> = listOf(Trabajo, Estudio, Salud, Hogar)
    }
}

/** Prioridades posibles: jerarquía cerrada, no se pueden crear valores fuera de estas tres. */
sealed class Prioridad(val etiqueta: String, val peso: Int) {
    data object Alta : Prioridad("Alta", 3)
    data object Media : Prioridad("Media", 2)
    data object Baja : Prioridad("Baja", 1)

    companion object {
        val todas: List<Prioridad> get() = listOf(Alta, Media, Baja)
    }
}

/** Estado de un recordatorio. Completado guarda cuándo se terminó. */
sealed class EstadoRecordatorio(val etiqueta: String) {
    data object Pendiente : EstadoRecordatorio("Pendiente")
    data object EnProgreso : EstadoRecordatorio("En progreso")
    data class Completado(val fecha: LocalDateTime) : EstadoRecordatorio("Completado")
}

/** Estado de sincronización de un recordatorio con el servicio remoto. */
enum class EstadoSync(val etiqueta: String) {
    SINCRONIZADO("Sincronizado"),
    PENDIENTE("Pendiente de sincronizar"),
    ERROR("Error al sincronizar"),
}

/** Filtro del listado por estado. */
enum class FiltroEstado(val etiqueta: String) {
    TODOS("Todos"),
    PENDIENTE("Pendiente"),
    EN_PROGRESO("En progreso"),
    COMPLETADO("Completado");

    fun incluye(estado: EstadoRecordatorio): Boolean = when (this) {
        TODOS -> true
        PENDIENTE -> estado is EstadoRecordatorio.Pendiente
        EN_PROGRESO -> estado is EstadoRecordatorio.EnProgreso
        COMPLETADO -> estado is EstadoRecordatorio.Completado
    }
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
    val creado: LocalDateTime = LocalDateTime.now(),
    val sincronizacion: EstadoSync = EstadoSync.SINCRONIZADO,
    val errorSincronizacion: String? = null,
) {
    init {
        // Última barrera: nunca puede existir un recordatorio en estado inválido.
        require(id > 0) { "El id debe ser positivo" }
        require(titulo.trim().length >= TITULO_MIN) { "El título debe tener al menos $TITULO_MIN caracteres" }
        require(titulo.length <= TITULO_MAX) { "El título no puede superar $TITULO_MAX caracteres" }
        require((descripcion?.length ?: 0) <= DESCRIPCION_MAX) { "La descripción no puede superar $DESCRIPCION_MAX caracteres" }
    }

    companion object {
        const val TITULO_MIN = 4
        const val TITULO_MAX = 60
        const val DESCRIPCION_MAX = 200
    }
}

/** Errores de cada campo del formulario; null significa que el campo es válido. */
data class ErroresFormulario(
    val titulo: String? = null,
    val descripcion: String? = null,
    val categoria: String? = null,
    val prioridad: String? = null,
) {
    val esValido: Boolean get() = titulo == null && descripcion == null && categoria == null && prioridad == null
}

/** Reglas de validación del formulario de creación. */
fun validarFormulario(
    titulo: String,
    descripcion: String,
    categoria: Categoria?,
    prioridad: Prioridad?,
): ErroresFormulario {
    val tituloLimpio = titulo.trim()
    return ErroresFormulario(
        titulo = when {
            tituloLimpio.length < Recordatorio.TITULO_MIN -> "El título debe tener al menos ${Recordatorio.TITULO_MIN} caracteres"
            tituloLimpio.length > Recordatorio.TITULO_MAX -> "El título no puede superar ${Recordatorio.TITULO_MAX} caracteres"
            else -> null
        },
        descripcion = if (descripcion.length > Recordatorio.DESCRIPCION_MAX) {
            "La descripción no puede superar ${Recordatorio.DESCRIPCION_MAX} caracteres"
        } else null,
        categoria = if (categoria !in Categoria.disponibles) "Selecciona una categoría de la lista" else null,
        prioridad = if (prioridad == null) "Selecciona una prioridad" else null,
    )
}

/** Lo que recibe el listado: los recordatorios ya filtrados y ordenados, y si existe algún registro en la base. */
data class ListadoRecordatorios(
    val items: List<Recordatorio>,
    val hayRegistros: Boolean,
)
