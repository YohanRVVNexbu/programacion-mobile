package com.yohanvelasquez.recordatorios.ui.formulario

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yohanvelasquez.recordatorios.RecordatoriosApp
import com.yohanvelasquez.recordatorios.data.RecordatorioRepository
import com.yohanvelasquez.recordatorios.model.Categoria
import com.yohanvelasquez.recordatorios.model.ErroresFormulario
import com.yohanvelasquez.recordatorios.model.Prioridad
import com.yohanvelasquez.recordatorios.model.validarFormulario
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

enum class Campo { TITULO, DESCRIPCION, CATEGORIA, PRIORIDAD }

/** Estado del envío: solo puede estar en una de estas situaciones a la vez. */
sealed interface EstadoEnvio {
    data object Editando : EstadoEnvio
    data object Enviando : EstadoEnvio
    data class Exitoso(val id: Int) : EstadoEnvio
}

data class FormularioUiState(
    val titulo: String = "",
    val descripcion: String = "",
    val categoria: Categoria? = null,
    val prioridad: Prioridad? = null,
    val errores: ErroresFormulario = validarFormulario("", "", null, null),
    /** Los errores de un campo se muestran recién cuando la persona lo modificó o intentó guardar. */
    val camposTocados: Set<Campo> = emptySet(),
    val envio: EstadoEnvio = EstadoEnvio.Editando,
    val errorGuardado: String? = null,
) {
    val enviando: Boolean get() = envio == EstadoEnvio.Enviando
    val puedeGuardar: Boolean get() = errores.esValido && envio == EstadoEnvio.Editando

    fun errorVisible(campo: Campo): String? = if (campo !in camposTocados) null else when (campo) {
        Campo.TITULO -> errores.titulo
        Campo.DESCRIPCION -> errores.descripcion
        Campo.CATEGORIA -> errores.categoria
        Campo.PRIORIDAD -> errores.prioridad
    }

    /** Texto que explica por qué el botón Guardar está deshabilitado. */
    val pendientes: List<String>
        get() = listOfNotNull(
            "título".takeIf { errores.titulo != null },
            "descripción".takeIf { errores.descripcion != null },
            "categoría".takeIf { errores.categoria != null },
            "prioridad".takeIf { errores.prioridad != null },
        )
}

class FormularioViewModel(private val repositorio: RecordatorioRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(FormularioUiState())
    val uiState: StateFlow<FormularioUiState> = _uiState.asStateFlow()

    fun onTituloChange(valor: String) = editar(Campo.TITULO) { it.copy(titulo = valor) }

    fun onDescripcionChange(valor: String) = editar(Campo.DESCRIPCION) { it.copy(descripcion = valor) }

    fun onCategoriaSeleccionada(categoria: Categoria) = editar(Campo.CATEGORIA) { it.copy(categoria = categoria) }

    fun onPrioridadSeleccionada(prioridad: Prioridad) = editar(Campo.PRIORIDAD) { it.copy(prioridad = prioridad) }

    /** Aplica el cambio, vuelve a validar y produce una nueva instancia del estado. */
    private fun editar(campo: Campo, cambio: (FormularioUiState) -> FormularioUiState) {
        _uiState.update { actual ->
            if (actual.enviando) return@update actual
            val nuevo = cambio(actual)
            nuevo.copy(
                errores = validarFormulario(nuevo.titulo, nuevo.descripcion, nuevo.categoria, nuevo.prioridad),
                camposTocados = nuevo.camposTocados + campo,
                errorGuardado = null,
            )
        }
    }

    fun onGuardar() {
        val estado = _uiState.value
        if (!estado.puedeGuardar) {
            _uiState.update { it.copy(camposTocados = Campo.entries.toSet()) }
            return
        }
        _uiState.update { it.copy(envio = EstadoEnvio.Enviando, errorGuardado = null) }
        viewModelScope.launch {
            try {
                val guardado = repositorio.guardarRecordatorio(
                    titulo = estado.titulo.trim(),
                    descripcion = estado.descripcion.trim().ifBlank { null },
                    categoria = checkNotNull(estado.categoria),
                    prioridad = checkNotNull(estado.prioridad),
                )
                _uiState.update { it.copy(envio = EstadoEnvio.Exitoso(guardado.id)) }
            } catch (e: IOException) {
                _uiState.update { it.copy(envio = EstadoEnvio.Editando, errorGuardado = e.message) }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { FormularioViewModel((this[APPLICATION_KEY] as RecordatoriosApp).repositorio) }
        }
    }
}
