package com.yohanvelasquez.recordatorios.data.remote

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * Copia remota de los recordatorios en Cloud Firestore, en la ruta  users/{uid}/reminders/{reminderId}.
 *
 * - El id del documento es el clientId local: crear o actualizar es siempre un `set` sobre el mismo documento,
 *   así que reintentar nunca genera una copia.
 * - Solo la capa de datos conoce Firebase: aquí se traducen documentos a [RecordatorioDto] y las excepciones de
 *   Firestore a [ErrorRed].
 * - Room es la fuente local; Firestore solo se consulta con la red disponible (si no, se falla enseguida en vez
 *   de dejar la escritura esperando en la cola interna del SDK).
 */
class FirestoreFuenteRemota(
    private val firestore: FirebaseFirestore,
    private val uid: () -> String?,
    private val consultaRed: ConsultaRed,
    private val tiempoLimiteMs: Long = 15_000,
) : FuenteRemota {

    private fun coleccion(uid: String) = firestore.collection("users").document(uid).collection("reminders")

    override suspend fun listar(): ResultadoRed<List<RecordatorioDto>> = ejecutar { uid ->
        val documentos = coleccion(uid).get(Source.SERVER).await()
        // Un documento con campos de tipo inesperado se ignora en vez de romper toda la lectura.
        documentos.documents.mapNotNull { runCatching { it.aDto() }.getOrNull() }
    }

    override suspend fun crear(dto: RecordatorioDto): ResultadoRed<RecordatorioDto> = ejecutar { uid ->
        coleccion(uid).document(dto.clientId).set(dto.aMapa()).await()
        dto.copy(id = dto.clientId)
    }

    override suspend fun actualizar(id: String, dto: RecordatorioDto): ResultadoRed<RecordatorioDto> = ejecutar { uid ->
        coleccion(uid).document(id).set(dto.aMapa()).await()
        dto.copy(id = id)
    }

    override suspend fun eliminar(id: String): ResultadoRed<Unit> = ejecutar { uid ->
        coleccion(uid).document(id).delete().await() // borrar algo que no existe también es éxito
    }

    private suspend fun <T> ejecutar(bloque: suspend (uid: String) -> T): ResultadoRed<T> {
        val uidActual = uid() ?: return ResultadoRed.Fallo(ErrorRed.SesionInvalida)
        if (!consultaRed.estaConectado()) return ResultadoRed.Fallo(ErrorRed.SinConexion)
        return try {
            ResultadoRed.Exito(withTimeout(tiempoLimiteMs) { bloque(uidActual) })
        } catch (e: TimeoutCancellationException) {
            ResultadoRed.Fallo(ErrorRed.Timeout)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            ResultadoRed.Fallo(errorDeFirestore(e.code))
        } catch (e: Exception) {
            ResultadoRed.Fallo(ErrorRed.Desconocido(e.message ?: e::class.java.simpleName))
        }
    }
}

/** Traduce los códigos de Firestore a los errores de la app. */
fun errorDeFirestore(codigo: FirebaseFirestoreException.Code): ErrorRed = when (codigo) {
    FirebaseFirestoreException.Code.UNAVAILABLE -> ErrorRed.SinConexion
    FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> ErrorRed.Timeout
    FirebaseFirestoreException.Code.PERMISSION_DENIED -> ErrorRed.NoAutorizado
    FirebaseFirestoreException.Code.UNAUTHENTICATED -> ErrorRed.SesionInvalida
    FirebaseFirestoreException.Code.NOT_FOUND -> ErrorRed.NoEncontrado
    FirebaseFirestoreException.Code.INVALID_ARGUMENT,
    FirebaseFirestoreException.Code.FAILED_PRECONDITION,
    FirebaseFirestoreException.Code.OUT_OF_RANGE,
    FirebaseFirestoreException.Code.ALREADY_EXISTS -> ErrorRed.SolicitudInvalida(400)
    FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED,
    FirebaseFirestoreException.Code.ABORTED,
    FirebaseFirestoreException.Code.INTERNAL,
    FirebaseFirestoreException.Code.DATA_LOSS -> ErrorRed.Servidor(500)
    else -> ErrorRed.Desconocido(codigo.name)
}

/** DTO -> campos del documento. Los nulos se omiten: así `set` también borra un campo que la persona vació. */
fun RecordatorioDto.aMapa(): Map<String, Any> = buildMap {
    put("titulo", titulo)
    descripcion?.let { put("descripcion", it) }
    put("categoria", categoria)
    put("prioridad", prioridad)
    put("estado", estado)
    completadoEn?.let { put("completadoEn", it) }
    fechaLimite?.let { put("fechaLimite", it) }
    ubicacion?.let { put("ubicacion", it) }
    put("creadoEn", creadoEn)
    put("actualizadoEn", actualizadoEn)
}

/** Documento -> DTO. Devuelve null si le falta algún campo obligatorio o tiene un tipo inesperado. */
fun DocumentSnapshot.aDto(): RecordatorioDto? {
    val titulo = getString("titulo") ?: return null
    val categoria = getString("categoria") ?: return null
    val prioridad = getString("prioridad") ?: return null
    val estado = getString("estado") ?: return null
    return RecordatorioDto(
        id = id,
        clientId = id,
        titulo = titulo,
        descripcion = getString("descripcion"),
        categoria = categoria,
        prioridad = prioridad,
        estado = estado,
        completadoEn = getLong("completadoEn"),
        fechaLimite = getLong("fechaLimite"),
        ubicacion = getString("ubicacion"),
        creadoEn = getLong("creadoEn") ?: 0,
        actualizadoEn = getLong("actualizadoEn") ?: 0,
    )
}
