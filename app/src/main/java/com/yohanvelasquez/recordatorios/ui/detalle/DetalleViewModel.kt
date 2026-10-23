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
import com.yohanvelasquez.recordatorios.model.Recordatorio
import com.yohanvelasquez.recordatorios.ui.navegacion.Rutas
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

sealed interface DetalleUiState {
    data object Cargando : DetalleUiState
    data class Encontrado(val recordatorio: Recordatorio) : DetalleUiState
    data class Error(val mensaje: String) : DetalleUiState
}

class DetalleViewModel(
    savedStateHandle: SavedStateHandle,
    private val repositorio: RecordatorioRepository,
) : ViewModel() {

    /** Solo se recibe el identificador; el recordatorio se busca en el repositorio. */
    private val id: Int = checkNotNull(savedStateHandle[Rutas.ARG_ID])

    private val _uiState = MutableStateFlow<DetalleUiState>(DetalleUiState.Cargando)
    val uiState: StateFlow<DetalleUiState> = _uiState.asStateFlow()

    init {
        cargar()
    }

    fun onReintentar() = cargar()

    private fun cargar() {
        _uiState.update { DetalleUiState.Cargando }
        viewModelScope.launch {
            val nuevo = try {
                repositorio.obtenerRecordatorio(id)
                    ?.let { DetalleUiState.Encontrado(it) }
                    ?: DetalleUiState.Error("No existe un recordatorio con el identificador $id.")
            } catch (e: IOException) {
                DetalleUiState.Error(e.message ?: "Error desconocido")
            }
            _uiState.update { nuevo }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                DetalleViewModel(createSavedStateHandle(), (this[APPLICATION_KEY] as RecordatoriosApp).repositorio)
            }
        }
    }
}
