package com.yohanvelasquez.recordatorios.data.remote

/** Situaciones de red que se pueden forzar desde la app (menú del listado) para comprobar cada caso. */
enum class ModoSimulacion(val etiqueta: String) {
    NORMAL("Red normal"),
    SIN_CONEXION("Sin conexión"),
    TIMEOUT("Timeout"),
    ERROR_SERVIDOR("Error 500 del servidor"),
    SOLICITUD_INVALIDA("Solicitud inválida (400)"),
}

class ControlSimulacion(@Volatile var modo: ModoSimulacion = ModoSimulacion.NORMAL)

/** Consulta si el dispositivo tiene red. Es una interfaz para poder simularla en pruebas. */
fun interface ConsultaRed {
    fun estaConectado(): Boolean
}
