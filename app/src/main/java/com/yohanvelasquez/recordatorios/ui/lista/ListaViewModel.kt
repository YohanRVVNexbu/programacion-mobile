package com.yohanvelasquez.recordatorios.ui.lista

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yohanvelasquez.recordatorios.RecordatoriosApp
import com.yohanvelasquez.recordatorios.data.ControlSimulacion
import com.yohanvelasquez.recordatorios.data.ModoSimulacion
import com.yohanvelasquez.recordatorios.data.RecordatorioRepository
import com.yohanvelasquez.recordatorios.model.EstadoRecordatorio
import com.yohanvelasquez.recordatorios.model.Recordatorio
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FiltroEstado(val etiqueta: String) {
    TODOS("Todos"),
    PENDIENTE("Pendiente"),
    EN_PROGRESO("En progreso"),
    COMPLETADO("Completado");

    fun incluye(estado: EstadoRecordatorio): Boolean = when (this) {
        TODOS -> true
        PENDIENTE -> estado is EstadoRecordatorio.Pendiente
        EN_PROGRESO -> estado is EstadoRecordatorio.EnProgreso
        COMPLETADO -> estado is EstadoRecordatorio.Completado
    }
}

/** Estados mutuamente excluyentes del listado: nunca se muestran carga, datos y error a la vez. */
sealed interface ListaUiState {
    data object Cargando : ListaUiState
    data object Vacio : ListaUiState
    data class ConDatos(
        val recordatorios: List<Recordatorio>,
        val filtro: FiltroEstado = FiltroEstado.TODOS,
    ) : ListaUiState {
        val visibles: List<Recordatorio>
            get() = recordatorios.filter { filtro.incluye(it.estado) }.sortedByDescending { it.prioridad.peso }
    }
    data class Error(val mensaje: String) : ListaUiState
}

class ListaViewModel(
    private val repositorio: RecordatorioRepository,
    private val simulacion: ControlSimulacion,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ListaUiState>(ListaUiState.Cargando)
    val uiState: StateFlow<ListaUiState> = _uiState.asStateFlow()

    private var carga: Job? = null

    init {
        cargar()
    }

    private fun cargar() {
        carga?.cancel()
        _uiState.update { ListaUiState.Cargando }
        carga = viewModelScope.launch {
            repositorio.observarRecordatorios()
                .catch { e -> _uiState.update { ListaUiState.Error(e.message ?: "Error desconocido") } }
                .collect { lista ->
                    _uiState.update { actual ->
                        if (lista.isEmpty()) {
                            ListaUiState.Vacio
                        } else {
                            ListaUiState.ConDatos(lista, (actual as? ListaUiState.ConDatos)?.filtro ?: FiltroEstado.TODOS)
                        }
                    }
                }
        }
    }

    fun onReintentar() = cargar()

    fun onFiltroSeleccionado(filtro: FiltroEstado) {
        _uiState.update { actual -> if (actual is ListaUiState.ConDatos) actual.copy(filtro = filtro) else actual }
    }

    fun onSimulacionSeleccionada(modo: ModoSimulacion) {
        simulacion.modo = modo
        cargar()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as RecordatoriosApp
                ListaViewModel(app.repositorio, app.simulacion)
            }
        }
    }
}
