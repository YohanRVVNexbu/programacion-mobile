package com.yohanvelasquez.recordatorios.ui.formulario

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yohanvelasquez.recordatorios.model.Categoria
import com.yohanvelasquez.recordatorios.model.Prioridad
import com.yohanvelasquez.recordatorios.model.Recordatorio
import com.yohanvelasquez.recordatorios.ui.componentes.CampoTexto
import com.yohanvelasquez.recordatorios.ui.componentes.SelectorOpciones
import com.yohanvelasquez.recordatorios.ui.theme.RecordatoriosTheme

@Composable
fun FormularioRoute(
    onCancelar: () -> Unit,
    onVolver: () -> Unit,
    viewModel: FormularioViewModel = viewModel(factory = FormularioViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Cuando el guardado termina bien, se vuelve al listado.
    LaunchedEffect(uiState.envio) {
        if (uiState.envio is EstadoEnvio.Exitoso) onVolver()
    }
    FormularioScreen(
        uiState = uiState,
        onTituloChange = viewModel::onTituloChange,
        onDescripcionChange = viewModel::onDescripcionChange,
        onCategoriaSeleccionada = viewModel::onCategoriaSeleccionada,
        onPrioridadSeleccionada = viewModel::onPrioridadSeleccionada,
        onGuardar = viewModel::onGuardar,
        onCancelar = onCancelar,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormularioScreen(
    uiState: FormularioUiState,
    onTituloChange: (String) -> Unit,
    onDescripcionChange: (String) -> Unit,
    onCategoriaSeleccionada: (Categoria) -> Unit,
    onPrioridadSeleccionada: (Prioridad) -> Unit,
    onGuardar: () -> Unit,
    onCancelar: () -> Unit,
) {
    val habilitado = !uiState.enviando
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nuevo recordatorio") },
                navigationIcon = {
                    IconButton(onClick = onCancelar, enabled = habilitado) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancelar")
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CampoTexto(
                    valor = uiState.titulo,
                    onValorChange = onTituloChange,
                    etiqueta = "Título *",
                    error = uiState.errorVisible(Campo.TITULO),
                    habilitado = habilitado,
                    maxCaracteres = Recordatorio.TITULO_MAX,
                )
                CampoTexto(
                    valor = uiState.descripcion,
                    onValorChange = onDescripcionChange,
                    etiqueta = "Descripción (opcional)",
                    error = uiState.errorVisible(Campo.DESCRIPCION),
                    habilitado = habilitado,
                    maxCaracteres = Recordatorio.DESCRIPCION_MAX,
                    lineasMinimas = 3,
                )
                SelectorOpciones(
                    titulo = "Categoría *",
                    opciones = Categoria.disponibles,
                    seleccionada = uiState.categoria,
                    onSeleccion = onCategoriaSeleccionada,
                    etiqueta = { it.nombre },
                    error = uiState.errorVisible(Campo.CATEGORIA),
                    habilitado = habilitado,
                )
                SelectorOpciones(
                    titulo = "Prioridad *",
                    opciones = Prioridad.todas,
                    seleccionada = uiState.prioridad,
                    onSeleccion = onPrioridadSeleccionada,
                    etiqueta = { it.etiqueta },
                    error = uiState.errorVisible(Campo.PRIORIDAD),
                    habilitado = habilitado,
                )

                uiState.errorGuardado?.let { ErrorGuardado(it) }

                if (uiState.pendientes.isNotEmpty() && !uiState.enviando) {
                    Text(
                        "Para guardar falta completar: ${uiState.pendientes.joinToString(", ")}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                ) {
                    OutlinedButton(onClick = onCancelar, enabled = habilitado) { Text("Cancelar") }
                    Button(onClick = onGuardar, enabled = uiState.puedeGuardar) {
                        if (uiState.enviando) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("Guardando…", Modifier.padding(start = 8.dp))
                        } else {
                            Text("Guardar")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorGuardado(mensaje: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Assertive },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Text("Error al guardar: $mensaje", color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FormularioScreenPreview() {
    RecordatoriosTheme {
        FormularioScreen(FormularioUiState(titulo = "Co", camposTocados = setOf(Campo.TITULO)), {}, {}, {}, {}, {}, {})
    }
}
