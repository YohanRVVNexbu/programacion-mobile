package com.yohanvelasquez.recordatorios.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.yohanvelasquez.recordatorios.data.preferencias.OrdenLista
import com.yohanvelasquez.recordatorios.data.preferencias.Preferencias
import com.yohanvelasquez.recordatorios.data.preferencias.PreferenciasRepository
import com.yohanvelasquez.recordatorios.data.preferencias.TemaApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PreferenciasTest {
    @get:Rule val carpeta = TemporaryFolder()

    private fun abrir(archivo: File): Pair<PreferenciasRepository, CoroutineScope> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val ds = PreferenceDataStoreFactory.create(scope = scope) { archivo }
        return PreferenciasRepository(ds) to scope
    }

    @Test
    fun `sin datos guardados se usan los valores por defecto`() = runBlocking {
        val (repo, scope) = abrir(File(carpeta.root, "p.preferences_pb"))
        assertEquals(Preferencias(), repo.preferencias.first())
        scope.cancel()
    }

    @Test
    fun `las preferencias se conservan al cerrar y volver a abrir`() = runBlocking {
        val archivo = File(carpeta.root, "p.preferences_pb")
        val (repo1, scope1) = abrir(archivo)
        repo1.guardarTema(TemaApp.OSCURO)
        repo1.guardarMostrarCompletados(false)
        repo1.guardarOrden(OrdenLista.TITULO)
        scope1.cancel() // equivale a cerrar la app: se descarta la instancia de DataStore

        val (repo2, scope2) = abrir(archivo)
        assertEquals(Preferencias(TemaApp.OSCURO, false, OrdenLista.TITULO), repo2.preferencias.first())
        scope2.cancel()
    }

    @Test
    fun `una opcion desconocida o de otro tipo cae en el valor por defecto sin romper`() = runBlocking {
        val archivo = File(carpeta.root, "p.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val ds = PreferenceDataStoreFactory.create(scope = scope) { archivo }
        ds.edit {
            it[stringPreferencesKey("tema")] = "FUCSIA"              // opción que no existe
            it[booleanPreferencesKey("orden")] = true                // tipo equivocado
            it[booleanPreferencesKey("mostrar_completados")] = false // este sí es válido
        }
        assertEquals(Preferencias(TemaApp.SISTEMA, false, OrdenLista.PRIORIDAD), PreferenciasRepository(ds).preferencias.first())
        scope.cancel()
    }

    @Test
    fun `un archivo danado se lee como valores por defecto`() = runBlocking {
        val archivo = File(carpeta.root, "p.preferences_pb").apply { writeBytes(ByteArray(64) { (it * 7).toByte() }) }
        val (repo, scope) = abrir(archivo)
        assertEquals(Preferencias(), repo.preferencias.first())
        scope.cancel()
    }
}
