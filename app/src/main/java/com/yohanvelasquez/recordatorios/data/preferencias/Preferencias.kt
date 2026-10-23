package com.yohanvelasquez.recordatorios.data.preferencias

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.yohanvelasquez.recordatorios.model.Recordatorio
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class TemaApp(val etiqueta: String) {
    SISTEMA("Seguir el sistema"),
    CLARO("Claro"),
    OSCURO("Oscuro"),
}

enum class OrdenLista(val etiqueta: String) {
    PRIORIDAD("Por prioridad"),
    FECHA_CREACION("Más recientes primero"),
    TITULO("Por título (A-Z)");

    /** Comparador que aplica este criterio sobre los recordatorios ya cargados. */
    val comparador: Comparator<Recordatorio>
        get() = when (this) {
            PRIORIDAD -> compareByDescending<Recordatorio> { it.prioridad.peso }.thenByDescending { it.creado }
            FECHA_CREACION -> compareByDescending { it.creado }
            TITULO -> compareBy { it.titulo.lowercase() }
        }
}

/** Modelo propio de preferencias. Los valores por defecto se usan cuando no hay dato guardado o es inválido. */
data class Preferencias(
    val tema: TemaApp = TemaApp.SISTEMA,
    val mostrarCompletados: Boolean = true,
    val orden: OrdenLista = OrdenLista.PRIORIDAD,
)

/** Claves tipadas. Solo guardan configuraciones simples; los recordatorios van en Room. */
internal object ClavesPreferencias {
    val TEMA = stringPreferencesKey("tema")
    val MOSTRAR_COMPLETADOS = booleanPreferencesKey("mostrar_completados")
    val ORDEN = stringPreferencesKey("orden")
}

/** Única instancia de DataStore de la app (delegado a nivel de archivo, como pide la documentación). */
val Context.preferenciasDataStore: DataStore<Preferences> by preferencesDataStore(name = "preferencias")

class PreferenciasRepository(private val dataStore: DataStore<Preferences>) {

    /**
     * Si el archivo no se puede leer (IOException) se emiten los valores por defecto en vez de fallar.
     * Un valor desconocido o de otro tipo también cae en el valor por defecto.
     */
    val preferencias: Flow<Preferencias> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.aPreferencias() }
        .distinctUntilChanged()

    suspend fun guardarTema(tema: TemaApp) = dataStore.edit { it[ClavesPreferencias.TEMA] = tema.name }

    suspend fun guardarMostrarCompletados(mostrar: Boolean) =
        dataStore.edit { it[ClavesPreferencias.MOSTRAR_COMPLETADOS] = mostrar }

    suspend fun guardarOrden(orden: OrdenLista) = dataStore.edit { it[ClavesPreferencias.ORDEN] = orden.name }
}

private fun Preferences.aPreferencias(): Preferencias {
    val porDefecto = Preferencias()
    return Preferencias(
        tema = leerSeguro { this[ClavesPreferencias.TEMA] }?.let { n -> TemaApp.entries.firstOrNull { it.name == n } }
            ?: porDefecto.tema,
        mostrarCompletados = leerSeguro { this[ClavesPreferencias.MOSTRAR_COMPLETADOS] } ?: porDefecto.mostrarCompletados,
        orden = leerSeguro { this[ClavesPreferencias.ORDEN] }?.let { n -> OrdenLista.entries.firstOrNull { it.name == n } }
            ?: porDefecto.orden,
    )
}

/** Si una clave contiene un tipo distinto al esperado, la lectura lanza ClassCastException; se trata como "sin dato". */
private inline fun <T> leerSeguro(lectura: () -> T?): T? = try {
    lectura()
} catch (e: ClassCastException) {
    null
}
