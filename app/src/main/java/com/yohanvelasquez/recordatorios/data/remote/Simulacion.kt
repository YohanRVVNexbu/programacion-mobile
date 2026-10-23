package com.yohanvelasquez.recordatorios.data.remote

/** Situaciones de red que se pueden forzar desde la app (solo variante dev) para comprobar cada caso. */
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

/** Envuelve una fuente remota para forzar fallos según [control]. Solo se instala en la variante dev. */
class FuenteRemotaConSimulacion(
    private val real: FuenteRemota,
    private val control: ControlSimulacion,
) : FuenteRemota {

    private fun fallo(escritura: Boolean): ErrorRed? = when (control.modo) {
        ModoSimulacion.NORMAL -> null
        ModoSimulacion.SIN_CONEXION -> ErrorRed.SinConexion
        ModoSimulacion.TIMEOUT -> ErrorRed.Timeout
        ModoSimulacion.ERROR_SERVIDOR -> ErrorRed.Servidor(500)
        ModoSimulacion.SOLICITUD_INVALIDA -> if (escritura) ErrorRed.SolicitudInvalida(400) else null
    }

    override suspend fun listar() = fallo(false)?.let { ResultadoRed.Fallo(it) } ?: real.listar()
    override suspend fun crear(dto: RecordatorioDto) = fallo(true)?.let { ResultadoRed.Fallo(it) } ?: real.crear(dto)
    override suspend fun actualizar(id: String, dto: RecordatorioDto) = fallo(true)?.let { ResultadoRed.Fallo(it) } ?: real.actualizar(id, dto)
    override suspend fun eliminar(id: String) = fallo(false)?.let { ResultadoRed.Fallo(it) } ?: real.eliminar(id)
}
