package com.yohanvelasquez.recordatorios.ui.detalle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yohanvelasquez.recordatorios.data.DatosSimulados
import com.yohanvelasquez.recordatorios.model.EstadoRecordatorio
import com.yohanvelasquez.recordatorios.model.EstadoSync
import com.yohanvelasquez.recordatorios.model.Recordatorio
import com.yohanvelasquez.recordatorios.ui.componentes.EtiquetaCategoria
import com.yohanvelasquez.recordatorios.ui.componentes.EtiquetaPrioridad
import com.yohanvelasquez.recordatorios.ui.componentes.EtiquetaSync
import com.yohanvelasquez.recordatorios.ui.componentes.IndicadorCarga
import com.yohanvelasquez.recordatorios.ui.componentes.MensajeError
import com.yohanvelasquez.recordatorios.ui.theme.RecordatoriosTheme
import java.time.format.DateTimeFormatter

@Composable
fun DetalleRoute(
    onVolver: () -> Unit,
    onEditar: (Int) -> Unit,
    viewModel: DetalleViewModel = viewModel(factory = DetalleViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uiState) {
        if (uiState is DetalleUiState.Eliminado) onVolver()
    }
    DetalleScreen(
        uiState = uiState,
        onVolver = onVolver,
        onEditar = onEditar,
        onCambiarCompletado = viewModel::onCambiarCompletado,
        onPedirEliminar = viewModel::onPedirEliminar,
        onCancelarEliminar = viewModel::onCancelarEliminar,
        onConfirmarEliminar = viewModel::onConfirmarEliminar,
        onReintentarSincronizacion = viewModel::onReintentarSincronizacion,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetalleScreen(
    uiState: DetalleUiState,
    onVolver: () -> Unit,
    onEditar: (Int) -> Unit,
    onCambiarCompletado: () -> Unit,
    onPedirEliminar: () -> Unit,
    onCancelarEliminar: () -> Unit,
    onConfirmarEliminar: () -> Unit,
    onReintentarSincronizacion: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (uiState is DetalleUiState.Encontrado) {
                        IconButton(onClick = { onEditar(uiState.recordatorio.id) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Editar")
                        }
                        IconButton(onClick = onPedirEliminar) {
                            Icon(Icons.Filled.Delete, contentDescription = "Eliminar")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        val contenido = Modifier.padding(innerPadding)
        when (uiState) {
            DetalleUiState.Cargando, DetalleUiState.Eliminado -> IndicadorCarga(contenido, mensaje = "Cargando recordatorio…")
            // Si no existe ya no hay nada que reintentar: el botón vuelve al listado.
            is DetalleUiState.Error -> MensajeError(uiState.mensaje, onVolver, contenido, textoAccion = "Volver al listado")
            is DetalleUiState.Encontrado -> {
                DetalleContenido(uiState.recordatorio, onCambiarCompletado, onReintentarSincronizacion, contenido)
                if (uiState.confirmandoEliminacion) {
                    AlertDialog(
                        onDismissRequest = onCancelarEliminar,
                        title = { Text("¿Eliminar recordatorio?") },
                        text = { Text("«${uiState.recordatorio.titulo}» se eliminará de este dispositivo y del servicio.") },
                        confirmButton = { TextButton(onClick = onConfirmarEliminar) { Text("Eliminar") } },
                        dismissButton = { TextButton(onClick = onCancelarEliminar) { Text("Cancelar") } },
                    )
                }
            }
        }
    }
}

private val formatoFecha = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

@Composable
private fun DetalleContenido(
    recordatorio: Recordatorio,
    onCambiarCompletado: () -> Unit,
    onReintentarSincronizacion: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val completado = recordatorio.estado is EstadoRecordatorio.Completado
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.widthIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(recordatorio.titulo, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                EtiquetaPrioridad(recordatorio.prioridad)
                EtiquetaCategoria(recordatorio.categoria)
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Dato("Descripción", recordatorio.descripcion ?: "Sin descripción")
                    HorizontalDivider()
                    Dato("Estado", recordatorio.estado.etiqueta)
                    (recordatorio.estado as? EstadoRecordatorio.Completado)?.let {
                        Dato("Completado el", it.fecha.format(formatoFecha))
                    }
                    Dato("Creado", recordatorio.creado.format(formatoFecha))
                    Dato("Fecha límite", recordatorio.fechaLimite?.format(formatoFecha) ?: "Sin fecha límite")
                    Dato("Ubicación", recordatorio.ubicacion ?: "Sin ubicación")
                    Dato("Identificador", "#${recordatorio.id}")
                    HorizontalDivider()
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Sincronización", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        EtiquetaSync(recordatorio.sincronizacion)
                        recordatorio.errorSincronizacion?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            if (recordatorio.sincronizacion == EstadoSync.ERROR) {
                OutlinedButton(onClick = onReintentarSincronizacion, modifier = Modifier.fillMaxWidth()) { Text("Reintentar sincronización") }
            }
            Button(onClick = onCambiarCompletado, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Text(if (completado) "Marcar como pendiente" else "Marcar como completado", Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun Dato(etiqueta: String, valor: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(etiqueta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor, style = MaterialTheme.typography.bodyLarge)
    }
}

@Preview(showBackground = true)
@Composable
private fun DetalleScreenPreview() {
    RecordatoriosTheme {
        DetalleScreen(DetalleUiState.Encontrado(DatosSimulados.recordatorios.first()), {}, {}, {}, {}, {}, {}, {})
    }
}
