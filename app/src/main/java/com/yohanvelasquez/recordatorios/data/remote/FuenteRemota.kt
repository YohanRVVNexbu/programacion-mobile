package com.yohanvelasquez.recordatorios.data.remote

import com.google.gson.JsonParseException
import com.google.gson.stream.MalformedJsonException
import kotlinx.coroutines.CancellationException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException

/** Lo que el sincronizador necesita del servicio. Permite reemplazarlo por otro en las pruebas. */
interface FuenteRemota {
    suspend fun listar(): ResultadoRed<List<RecordatorioDto>>
    suspend fun crear(dto: RecordatorioDto): ResultadoRed<RecordatorioDto>
    suspend fun actualizar(id: String, dto: RecordatorioDto): ResultadoRed<RecordatorioDto>
    suspend fun eliminar(id: String): ResultadoRed<Unit>
}

/** Implementación con Retrofit: traduce respuestas HTTP y excepciones de red a [ResultadoRed]. */
class RecordatorioRemoteDataSource(private val api: RecordatorioApi) : FuenteRemota {

    override suspend fun listar() = conCuerpo { api.listar() }
    override suspend fun crear(dto: RecordatorioDto) = conCuerpo { api.crear(dto) }
    override suspend fun actualizar(id: String, dto: RecordatorioDto) = conCuerpo { api.actualizar(id, dto) }

    /** Un borrado exitoso responde 204 sin cuerpo: Retrofit entrega body() == null, y eso no es un error. */
    override suspend fun eliminar(id: String): ResultadoRed<Unit> = when (val r = ejecutar { api.eliminar(id) }) {
        is ResultadoRed.Exito -> ResultadoRed.Exito(Unit)
        is ResultadoRed.Fallo -> r
    }

    private suspend fun <T : Any> conCuerpo(bloque: suspend () -> Response<T>): ResultadoRed<T> = when (val r = ejecutar(bloque)) {
        is ResultadoRed.Exito -> r.datos.body()?.let { ResultadoRed.Exito(it) } ?: ResultadoRed.Fallo(ErrorRed.Desconocido("respuesta vacía"))
        is ResultadoRed.Fallo -> r
    }

    private suspend fun <T> ejecutar(bloque: suspend () -> Response<T>): ResultadoRed<Response<T>> = try {
        val respuesta = bloque()
        if (respuesta.isSuccessful) ResultadoRed.Exito(respuesta) else ResultadoRed.Fallo(errorDeHttp(respuesta.code()))
    } catch (e: CancellationException) {
        throw e
    } catch (e: SocketTimeoutException) {
        ResultadoRed.Fallo(ErrorRed.Timeout)
    } catch (e: JsonParseException) {
        ResultadoRed.Fallo(ErrorRed.Desconocido("la respuesta del servicio no tiene el formato esperado"))
    } catch (e: MalformedJsonException) {
        ResultadoRed.Fallo(ErrorRed.Desconocido("la respuesta del servicio no tiene el formato esperado"))
    } catch (e: IOException) {
        // UnknownHostException, ConnectException, SSLException, etc.: no hubo comunicación con el servidor.
        ResultadoRed.Fallo(ErrorRed.SinConexion)
    } catch (e: Exception) {
        ResultadoRed.Fallo(ErrorRed.Desconocido(e.message ?: e::class.java.simpleName))
    }

    companion object {
        fun errorDeHttp(codigo: Int): ErrorRed = when (codigo) {
            404 -> ErrorRed.NoEncontrado
            408 -> ErrorRed.Timeout
            in 400..499 -> ErrorRed.SolicitudInvalida(codigo)
            in 500..599 -> ErrorRed.Servidor(codigo)
            else -> ErrorRed.Desconocido("código HTTP $codigo")
        }
    }
}
