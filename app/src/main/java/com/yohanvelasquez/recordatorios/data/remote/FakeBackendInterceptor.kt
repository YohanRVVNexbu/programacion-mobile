package com.yohanvelasquez.recordatorios.data.remote

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.File
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Servicio remoto simulado. Se instala como interceptor de OkHttp, así las llamadas pasan por Retrofit,
 * por el cliente HTTP y por la conversión JSON igual que con un servidor real; solo la respuesta se
 * fabrica aquí. Se activa con `recordatorios.usarBackendSimulado=true` (ver local.properties.example).
 *
 * Se comporta como un servicio REST sencillo:
 *  - GET    /recordatorios        -> 200 con la lista
 *  - POST   /recordatorios        -> 201 con el registro creado (200 si ya existía ese clientId: no duplica)
 *  - PUT    /recordatorios/{id}   -> 200, o 404 si no existe
 *  - DELETE /recordatorios/{id}   -> 204, o 404 si no existe
 *
 * Los datos se guardan en [archivo] para que sobrevivan al cierre de la app, como en un servidor real.
 */
class FakeBackendInterceptor(
    private val simulacion: ControlSimulacion,
    private val consultaRed: ConsultaRed,
    private val archivo: File? = null,
    private val latenciaMs: Long = 600,
    private val reloj: () -> Long = System::currentTimeMillis,
) : Interceptor {

    private val gson = Gson()
    private val json = "application/json".toMediaType()
    private val registros = LinkedHashMap<String, RecordatorioDto>()
    private var siguienteId = 100

    init {
        cargar()
        if (registros.isEmpty()) sembrar()
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (latenciaMs > 0) Thread.sleep(latenciaMs)

        if (!consultaRed.estaConectado()) throw UnknownHostException("Dispositivo sin red")
        when (simulacion.modo) {
            ModoSimulacion.SIN_CONEXION -> throw UnknownHostException("Sin conexión simulada")
            ModoSimulacion.TIMEOUT -> throw SocketTimeoutException("Timeout simulado")
            ModoSimulacion.ERROR_SERVIDOR -> return respuesta(request, 500, """{"error":"Error interno simulado"}""")
            ModoSimulacion.SOLICITUD_INVALIDA ->
                if (request.method == "POST" || request.method == "PUT") {
                    return respuesta(request, 400, """{"error":"Solicitud inválida simulada"}""")
                }
            ModoSimulacion.NORMAL -> Unit
        }
        return synchronized(registros) { atender(request) }
    }

    private fun atender(request: Request): Response {
        val segmentos = request.url.pathSegments.filter { it.isNotEmpty() }
        if (segmentos.firstOrNull() != "recordatorios") return respuesta(request, 404, """{"error":"Ruta desconocida"}""")
        val id = segmentos.getOrNull(1)

        return when {
            request.method == "GET" && id == null ->
                respuesta(request, 200, gson.toJson(registros.values.toList()))

            request.method == "POST" && id == null -> {
                val dto = leerCuerpo(request) ?: return respuesta(request, 400, """{"error":"JSON inválido"}""")
                validar(dto)?.let { return respuesta(request, 400, """{"error":"$it"}""") }
                val existente = registros.values.firstOrNull { it.clientId == dto.clientId }
                if (existente != null) {
                    respuesta(request, 200, gson.toJson(existente)) // reintento: no se crea otro registro
                } else {
                    val nuevo = dto.copy(id = (siguienteId++).toString(), actualizadoEn = reloj())
                    registros[nuevo.id!!] = nuevo
                    guardar()
                    respuesta(request, 201, gson.toJson(nuevo))
                }
            }

            request.method == "PUT" && id != null -> {
                val actual = registros[id] ?: return respuesta(request, 404, """{"error":"No existe"}""")
                val dto = leerCuerpo(request) ?: return respuesta(request, 400, """{"error":"JSON inválido"}""")
                validar(dto)?.let { return respuesta(request, 400, """{"error":"$it"}""") }
                val nuevo = dto.copy(id = id, clientId = actual.clientId, actualizadoEn = reloj())
                registros[id] = nuevo
                guardar()
                respuesta(request, 200, gson.toJson(nuevo))
            }

            request.method == "DELETE" && id != null ->
                if (registros.remove(id) != null) {
                    guardar()
                    respuesta(request, 204, "")
                } else {
                    respuesta(request, 404, """{"error":"No existe"}""")
                }

            else -> respuesta(request, 405, """{"error":"Método no permitido"}""")
        }
    }

    private fun validar(dto: RecordatorioDto): String? = when {
        dto.titulo.trim().length < 4 -> "El título debe tener al menos 4 caracteres"
        dto.clientId.isBlank() -> "Falta clientId"
        else -> null
    }

    private fun leerCuerpo(request: Request): RecordatorioDto? = runCatching {
        val buffer = okio.Buffer()
        request.body?.writeTo(buffer)
        gson.fromJson(buffer.readUtf8(), RecordatorioDto::class.java)
    }.getOrNull()

    private fun respuesta(request: Request, codigo: Int, cuerpo: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(codigo)
            .message(if (codigo in 200..299) "OK" else "Error")
            .body(cuerpo.toResponseBody(json))
            .build()

    private fun sembrar() {
        val ahora = reloj()
        listOf(
            RecordatorioDto("1", "semilla-1", "Revisar presupuesto regional", "Documento compartido con finanzas",
                "Trabajo", "ALTA", "PENDIENTE", actualizadoEn = ahora),
            RecordatorioDto("2", "semilla-2", "Preparar clase de Kotlin", null,
                "Estudio", "MEDIA", "EN_PROGRESO", actualizadoEn = ahora),
        ).forEach { registros[it.id!!] = it }
        guardar()
    }

    private fun guardar() {
        archivo?.writeText(gson.toJson(registros.values.toList()))
    }

    private fun cargar() {
        val texto = archivo?.takeIf { it.exists() }?.readText() ?: return
        val lista: List<RecordatorioDto> = runCatching {
            gson.fromJson<List<RecordatorioDto>>(texto, object : TypeToken<List<RecordatorioDto>>() {}.type)
        }.getOrNull() ?: return
        lista.forEach { registros[it.id!!] = it }
        siguienteId = (lista.maxOfOrNull { it.id!!.toInt() } ?: 99).coerceAtLeast(99) + 1
    }
}
