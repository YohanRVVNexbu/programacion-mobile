package com.yohanvelasquez.recordatorios.ui.formulario

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yohanvelasquez.recordatorios.RecordatoriosApp
import com.yohanvelasquez.recordatorios.data.RecordatorioRepository
import com.yohanvelasquez.recordatorios.model.Categoria
import com.yohanvelasquez.recordatorios.model.ErroresFormulario
import com.yohanvelasquez.recordatorios.model.Prioridad
import com.yohanvelasquez.recordatorios.model.validarFormulario
import com.yohanvelasquez.recordatorios.ui.navegacion.Rutas
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    /** true cuando se edita un recordatorio existente (en vez de crear uno). */
    val esEdicion: Boolean = false,
    /** true mientras se leen de Room los datos del recordatorio a editar. */
    val cargando: Boolean = false,
) {
    val enviando: Boolean get() = envio == EstadoEnvio.Enviando
    val puedeGuardar: Boolean get() = !cargando && errores.esValido && envio == EstadoEnvio.Editando

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

/** Sirve para crear (sin id) y para editar (con el id del recordatorio, recibido por la navegación). */
class FormularioViewModel(
    private val repositorio: RecordatorioRepository,
    private val idEdicion: Int?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FormularioUiState(esEdicion = idEdicion != null, cargando = idEdicion != null))
    val uiState: StateFlow<FormularioUiState> = _uiState.asStateFlow()

    init {
        if (idEdicion != null) cargarExistente(idEdicion)
    }

    private fun cargarExistente(id: Int) {
        viewModelScope.launch {
            val existente = repositorio.obtenerRecordatorio(id)
            _uiState.update { actual ->
                if (existente == null) {
                    actual.copy(cargando = false, errorGuardado = "Este recordatorio ya no existe.")
                } else {
                    val descripcion = existente.descripcion.orEmpty()
                    actual.copy(
                        titulo = existente.titulo,
                        descripcion = descripcion,
                        categoria = existente.categoria,
                        prioridad = existente.prioridad,
                        errores = validarFormulario(existente.titulo, descripcion, existente.categoria, existente.prioridad),
                        cargando = false,
                    )
                }
            }
        }
    }

    fun onTituloChange(valor: String) = editar(Campo.TITULO) { it.copy(titulo = valor) }

    fun onDescripcionChange(valor: String) = editar(Campo.DESCRIPCION) { it.copy(descripcion = valor) }

    fun onCategoriaSeleccionada(categoria: Categoria) = editar(Campo.CATEGORIA) { it.copy(categoria = categoria) }

    fun onPrioridadSeleccionada(prioridad: Prioridad) = editar(Campo.PRIORIDAD) { it.copy(prioridad = prioridad) }

    /** Aplica el cambio, vuelve a validar y produce una nueva instancia del estado. */
    private fun editar(campo: Campo, cambio: (FormularioUiState) -> FormularioUiState) {
        _uiState.update { actual ->
            if (actual.enviando || actual.cargando) return@update actual
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
                val titulo = estado.titulo.trim()
                val descripcion = estado.descripcion.trim().ifBlank { null }
                val categoria = checkNotNull(estado.categoria)
                val prioridad = checkNotNull(estado.prioridad)
                val id = if (idEdicion == null) {
                    repositorio.crear(titulo, descripcion, categoria, prioridad)
                } else {
                    repositorio.actualizar(idEdicion, titulo, descripcion, categoria, prioridad)
                    idEdicion
                }
                _uiState.update { it.copy(envio = EstadoEnvio.Exitoso(id)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Solo falla la escritura local en Room; la red nunca bloquea el guardado.
                _uiState.update { it.copy(envio = EstadoEnvio.Editando, errorGuardado = "No se pudo guardar en el dispositivo. Intenta de nuevo.") }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val handle: SavedStateHandle = createSavedStateHandle()
                // En la ruta "crear" no hay argumento: get() devuelve null.
                FormularioViewModel((this[APPLICATION_KEY] as RecordatoriosApp).repositorio, handle.get<Int>(Rutas.ARG_ID))
            }
        }
    }
}
