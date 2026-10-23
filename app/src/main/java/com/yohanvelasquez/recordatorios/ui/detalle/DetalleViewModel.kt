package com.yohanvelasquez.recordatorios.ui.detalle

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yohanvelasquez.recordatorios.RecordatoriosApp
import com.yohanvelasquez.recordatorios.data.RecordatorioRepository
import com.yohanvelasquez.recordatorios.model.EstadoRecordatorio
import com.yohanvelasquez.recordatorios.model.Recordatorio
import com.yohanvelasquez.recordatorios.ui.navegacion.Rutas
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface DetalleUiState {
    data object Cargando : DetalleUiState
    data class Encontrado(val recordatorio: Recordatorio, val confirmandoEliminacion: Boolean = false) : DetalleUiState
    data class Error(val mensaje: String) : DetalleUiState
    /** Ya se eliminó: la pantalla vuelve al listado. */
    data object Eliminado : DetalleUiState
}

class DetalleViewModel(
    savedStateHandle: SavedStateHandle,
    private val repositorio: RecordatorioRepository,
) : ViewModel() {

    /** Solo se recibe el identificador; el recordatorio se lee de Room y la pantalla se actualiza sola si cambia. */
    private val id: Int = checkNotNull(savedStateHandle[Rutas.ARG_ID])

    private val _uiState = MutableStateFlow<DetalleUiState>(DetalleUiState.Cargando)
    val uiState: StateFlow<DetalleUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repositorio.observarRecordatorio(id)
                .catch { e -> _uiState.update { DetalleUiState.Error(e.message ?: "No se pudo leer el recordatorio.") } }
                .collect { r ->
                    _uiState.update { actual ->
                        when {
                            actual is DetalleUiState.Eliminado -> actual
                            r == null -> DetalleUiState.Error("Este recordatorio ya no existe.")
                            actual is DetalleUiState.Encontrado -> actual.copy(recordatorio = r)
                            else -> DetalleUiState.Encontrado(r)
                        }
                    }
                }
        }
    }

    fun onCambiarCompletado() {
        val actual = (_uiState.value as? DetalleUiState.Encontrado)?.recordatorio ?: return
        viewModelScope.launch { repositorio.cambiarCompletado(id, completado = actual.estado !is EstadoRecordatorio.Completado) }
    }

    fun onPedirEliminar() = confirmacion(true)
    fun onCancelarEliminar() = confirmacion(false)

    private fun confirmacion(visible: Boolean) {
        _uiState.update { if (it is DetalleUiState.Encontrado) it.copy(confirmandoEliminacion = visible) else it }
    }

    fun onConfirmarEliminar() {
        // Se pasa a Eliminado antes de borrar para que no aparezca un error cuando Room avise que ya no existe.
        _uiState.update { DetalleUiState.Eliminado }
        viewModelScope.launch { repositorio.eliminar(id) }
    }

    fun onReintentarSincronizacion() {
        viewModelScope.launch { repositorio.reintentarFallidos() }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                DetalleViewModel(createSavedStateHandle(), (this[APPLICATION_KEY] as RecordatoriosApp).repositorio)
            }
        }
    }
}
