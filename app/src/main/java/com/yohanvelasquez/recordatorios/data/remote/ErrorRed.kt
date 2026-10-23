package com.yohanvelasquez.recordatorios.data.remote

/** Qué salió mal al hablar con el servicio. Se resuelve en la capa de datos, no en las pantallas. */
sealed class ErrorRed(val mensaje: String, val recuperable: Boolean) {
    data object SinConexion : ErrorRed("Sin conexión a internet.", recuperable = true)
    data object Timeout : ErrorRed("El servidor tardó demasiado en responder.", recuperable = true)
    data class Servidor(val codigo: Int) : ErrorRed("El servidor tuvo un problema (código $codigo).", recuperable = true)
    data class SolicitudInvalida(val codigo: Int) : ErrorRed("El servicio rechazó los datos enviados (código $codigo).", recuperable = false)
    data object NoEncontrado : ErrorRed("El servicio no encontró el recurso (código 404).", recuperable = false)
    data class Desconocido(val detalle: String) : ErrorRed("Error inesperado: $detalle", recuperable = false)
}

sealed interface ResultadoRed<out T> {
    data class Exito<T>(val datos: T) : ResultadoRed<T>
    data class Fallo(val error: ErrorRed) : ResultadoRed<Nothing>
}
