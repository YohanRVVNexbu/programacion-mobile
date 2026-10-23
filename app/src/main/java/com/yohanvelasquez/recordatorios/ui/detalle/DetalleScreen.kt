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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.yohanvelasquez.recordatorios.model.Recordatorio
import com.yohanvelasquez.recordatorios.ui.componentes.EtiquetaCategoria
import com.yohanvelasquez.recordatorios.ui.componentes.EtiquetaPrioridad
import com.yohanvelasquez.recordatorios.ui.componentes.IndicadorCarga
import com.yohanvelasquez.recordatorios.ui.componentes.MensajeError
import com.yohanvelasquez.recordatorios.ui.theme.RecordatoriosTheme
import java.time.format.DateTimeFormatter

@Composable
fun DetalleRoute(
    onVolver: () -> Unit,
    viewModel: DetalleViewModel = viewModel(factory = DetalleViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DetalleScreen(uiState, onVolver = onVolver, onReintentar = viewModel::onReintentar)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetalleScreen(
    uiState: DetalleUiState,
    onVolver: () -> Unit,
    onReintentar: () -> Unit,
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
            )
        },
    ) { innerPadding ->
        val contenido = Modifier.padding(innerPadding)
        when (uiState) {
            DetalleUiState.Cargando -> IndicadorCarga(contenido, mensaje = "Cargando recordatorio…")
            is DetalleUiState.Error -> MensajeError(uiState.mensaje, onReintentar, contenido)
            is DetalleUiState.Encontrado -> DetalleContenido(uiState.recordatorio, contenido)
        }
    }
}

private val formatoFecha = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

@Composable
private fun DetalleContenido(recordatorio: Recordatorio, modifier: Modifier = Modifier) {
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
                    Dato("Prioridad", recordatorio.prioridad.etiqueta)
                    Dato("Categoría", recordatorio.categoria.nombre)
                    Dato("Fecha límite", recordatorio.fechaLimite?.format(formatoFecha) ?: "Sin fecha límite")
                    Dato("Ubicación", recordatorio.ubicacion ?: "Sin ubicación")
                    Dato("Identificador", "#${recordatorio.id}")
                }
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
        DetalleScreen(DetalleUiState.Encontrado(DatosSimulados.recordatorios.first()), {}, {})
    }
}
