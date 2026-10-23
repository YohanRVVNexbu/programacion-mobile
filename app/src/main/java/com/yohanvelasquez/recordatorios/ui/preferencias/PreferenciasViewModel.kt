package com.yohanvelasquez.recordatorios.ui.preferencias

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yohanvelasquez.recordatorios.RecordatoriosApp
import com.yohanvelasquez.recordatorios.data.preferencias.OrdenLista
import com.yohanvelasquez.recordatorios.data.preferencias.Preferencias
import com.yohanvelasquez.recordatorios.data.preferencias.PreferenciasRepository
import com.yohanvelasquez.recordatorios.data.preferencias.TemaApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PreferenciasViewModel(private val repositorio: PreferenciasRepository) : ViewModel() {

    val uiState: StateFlow<Preferencias> = repositorio.preferencias
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Preferencias())

    // Las escrituras son funciones suspendidas lanzadas en viewModelScope: no bloquean el hilo principal.
    fun onTemaChange(tema: TemaApp) {
        viewModelScope.launch { repositorio.guardarTema(tema) }
    }

    fun onMostrarCompletadosChange(mostrar: Boolean) {
        viewModelScope.launch { repositorio.guardarMostrarCompletados(mostrar) }
    }

    fun onOrdenChange(orden: OrdenLista) {
        viewModelScope.launch { repositorio.guardarOrden(orden) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { PreferenciasViewModel((this[APPLICATION_KEY] as RecordatoriosApp).preferencias) }
        }
    }
}
