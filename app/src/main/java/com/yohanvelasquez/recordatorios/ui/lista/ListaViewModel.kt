package com.yohanvelasquez.recordatorios.ui.lista

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yohanvelasquez.recordatorios.RecordatoriosApp
import com.yohanvelasquez.recordatorios.data.RecordatorioRepository
import com.yohanvelasquez.recordatorios.data.remote.ControlSimulacion
import com.yohanvelasquez.recordatorios.data.remote.ModoSimulacion
import com.yohanvelasquez.recordatorios.data.sync.EstadoSincronizacion
import com.yohanvelasquez.recordatorios.model.EstadoSync
import com.yohanvelasquez.recordatorios.model.FiltroEstado
import com.yohanvelasquez.recordatorios.model.Recordatorio
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estados mutuamente excluyentes del listado: nunca se muestran carga, datos y error a la vez. */
sealed interface ListaUiState {
    data object Cargando : ListaUiState
    data object Vacio : ListaUiState
    data class ConDatos(
        val recordatorios: List<Recordatorio>,
        val filtro: FiltroEstado = FiltroEstado.TODOS,
    ) : ListaUiState {
        val pendientes: Int get() = recordatorios.count { it.sincronizacion == EstadoSync.PENDIENTE }
        val conError: Int get() = recordatorios.count { it.sincronizacion == EstadoSync.ERROR }
    }
    data class Error(val mensaje: String) : ListaUiState
}

class ListaViewModel(
    private val repositorio: RecordatorioRepository,
    private val simulacion: ControlSimulacion,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ListaUiState>(ListaUiState.Cargando)
    val uiState: StateFlow<ListaUiState> = _uiState.asStateFlow()

    /** Estado de la sincronización con el servicio, aparte del contenido: los datos locales se muestran igual. */
    val sincronizacion: StateFlow<EstadoSincronizacion> get() = repositorio.estadoSincronizacion

    private val filtro = MutableStateFlow(FiltroEstado.TODOS)
    private var observacion: Job? = null

    init {
        observar()
    }

    /** Observa Room (ya filtrado por estado, sin mostrar completados si así se configuró y ordenado según las preferencias). */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observar() {
        observacion?.cancel()
        _uiState.update { ListaUiState.Cargando }
        observacion = viewModelScope.launch {
            filtro
                .flatMapLatest { f -> repositorio.observarRecordatorios(f).map { f to it } }
                .catch { e -> _uiState.update { ListaUiState.Error(e.message ?: "No se pudieron leer los recordatorios guardados.") } }
                .collect { (f, listado) ->
                    _uiState.update {
                        if (!listado.hayRegistros) ListaUiState.Vacio else ListaUiState.ConDatos(listado.items, f)
                    }
                }
        }
    }

    fun onReintentar() = observar()

    fun onFiltroSeleccionado(nuevo: FiltroEstado) {
        filtro.update { nuevo }
    }

    fun onSincronizar() = repositorio.solicitarSincronizacion()

    fun onReintentarSincronizacion() {
        viewModelScope.launch { repositorio.reintentarFallidos() }
    }

    fun onSimulacionSeleccionada(modo: ModoSimulacion) {
        simulacion.modo = modo
        repositorio.solicitarSincronizacion()
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
