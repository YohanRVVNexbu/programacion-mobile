package com.yohanvelasquez.recordatorios.ui.preferencias

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yohanvelasquez.recordatorios.RecordatoriosApp
import com.yohanvelasquez.recordatorios.data.RecordatorioRepository
import com.yohanvelasquez.recordatorios.data.auth.AuthRepository
import com.yohanvelasquez.recordatorios.data.auth.EstadoSesion
import com.yohanvelasquez.recordatorios.data.preferencias.OrdenLista
import com.yohanvelasquez.recordatorios.data.preferencias.Preferencias
import com.yohanvelasquez.recordatorios.data.preferencias.PreferenciasRepository
import com.yohanvelasquez.recordatorios.data.preferencias.TemaApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Todo lo que muestra la pantalla de preferencias y cuenta. */
data class PreferenciasUiState(
    val preferencias: Preferencias = Preferencias(),
    val correo: String? = null,
    /** Cambios de esta cuenta que el servicio todavía no confirmó: se perderían al cerrar sesión. */
    val sinSincronizar: Int = 0,
    val confirmandoCierre: Boolean = false,
)

class PreferenciasViewModel(
    private val preferenciasRepo: PreferenciasRepository,
    private val recordatorios: RecordatorioRepository,
    private val auth: AuthRepository,
) : ViewModel() {

    private val confirmando = MutableStateFlow(false)

    val uiState: StateFlow<PreferenciasUiState> = kotlinx.coroutines.flow.combine(
        preferenciasRepo.preferencias,
        auth.sesion.map { (it as? EstadoSesion.Activa)?.usuario?.correo },
        recordatorios.observarSinSincronizar(),
        confirmando,
    ) { prefs, correo, pendientes, dialogo -> PreferenciasUiState(prefs, correo, pendientes, dialogo) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PreferenciasUiState())

    // Las escrituras son funciones suspendidas lanzadas en viewModelScope: no bloquean el hilo principal.
    fun onTemaChange(tema: TemaApp) {
        viewModelScope.launch { preferenciasRepo.guardarTema(tema) }
    }

    fun onMostrarCompletadosChange(mostrar: Boolean) {
        viewModelScope.launch { preferenciasRepo.guardarMostrarCompletados(mostrar) }
    }

    fun onOrdenChange(orden: OrdenLista) {
        viewModelScope.launch { preferenciasRepo.guardarOrden(orden) }
    }

    fun onCerrarSesion() = confirmando.update { true }
    fun onCancelarCierre() = confirmando.update { false }

    /** Borra los datos de esta cuenta del dispositivo y cierra la sesión (en ese orden: al cerrar, la pantalla se destruye). */
    fun onConfirmarCierre() {
        viewModelScope.launch {
            recordatorios.limpiarDatosLocales()
            auth.cerrarSesion()
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as RecordatoriosApp
                PreferenciasViewModel(app.preferencias, app.repositorio, app.auth)
            }
        }
    }
}
