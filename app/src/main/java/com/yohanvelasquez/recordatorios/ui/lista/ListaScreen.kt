package com.yohanvelasquez.recordatorios.ui.lista

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yohanvelasquez.recordatorios.data.DatosSimulados
import com.yohanvelasquez.recordatorios.data.remote.ModoSimulacion
import com.yohanvelasquez.recordatorios.data.sync.EstadoSincronizacion
import com.yohanvelasquez.recordatorios.model.FiltroEstado
import com.yohanvelasquez.recordatorios.ui.componentes.BannerSincronizacion
import com.yohanvelasquez.recordatorios.ui.componentes.IndicadorCarga
import com.yohanvelasquez.recordatorios.ui.componentes.MensajeError
import com.yohanvelasquez.recordatorios.ui.componentes.MensajeVacio
import com.yohanvelasquez.recordatorios.ui.componentes.TarjetaRecordatorio
import com.yohanvelasquez.recordatorios.ui.theme.RecordatoriosTheme

/** Conecta la pantalla con su ViewModel. La pantalla en sí no conoce el ViewModel ni el NavController. */
@Composable
fun ListaRoute(
    onCrearRecordatorio: () -> Unit,
    onRecordatorioClick: (Int) -> Unit,
    onPreferencias: () -> Unit,
    viewModel: ListaViewModel = viewModel(factory = ListaViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sincronizacion by viewModel.sincronizacion.collectAsStateWithLifecycle()
    ListaScreen(
        uiState = uiState,
        sincronizacion = sincronizacion,
        onCrearRecordatorio = onCrearRecordatorio,
        onRecordatorioClick = onRecordatorioClick,
        onPreferencias = onPreferencias,
        onReintentar = viewModel::onReintentar,
        onFiltroSeleccionado = viewModel::onFiltroSeleccionado,
        onSincronizar = viewModel::onSincronizar,
        onReintentarSincronizacion = viewModel::onReintentarSincronizacion,
        onSimulacionSeleccionada = viewModel::onSimulacionSeleccionada,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListaScreen(
    uiState: ListaUiState,
    sincronizacion: EstadoSincronizacion,
    onCrearRecordatorio: () -> Unit,
    onRecordatorioClick: (Int) -> Unit,
    onPreferencias: () -> Unit,
    onReintentar: () -> Unit,
    onFiltroSeleccionado: (FiltroEstado) -> Unit,
    onSincronizar: () -> Unit,
    onReintentarSincronizacion: () -> Unit,
    onSimulacionSeleccionada: (ModoSimulacion) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recordatorios") },
                actions = {
                    IconButton(onClick = onSincronizar) { Icon(Icons.Filled.Refresh, contentDescription = "Sincronizar ahora") }
                    IconButton(onClick = onPreferencias) { Icon(Icons.Filled.Settings, contentDescription = "Preferencias") }
                    MenuSimulacion(onSimulacionSeleccionada)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCrearRecordatorio,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Nuevo") },
            )
        },
    ) { innerPadding ->
        val contenido = Modifier.padding(innerPadding)
        when (uiState) {
            ListaUiState.Cargando -> IndicadorCarga(contenido, mensaje = "Cargando recordatorios…")
            ListaUiState.Vacio -> MensajeVacio(onCrearRecordatorio, contenido)
            is ListaUiState.Error -> MensajeError(uiState.mensaje, onReintentar, contenido)
            is ListaUiState.ConDatos -> ListaRecordatorios(
                estado = uiState,
                sincronizacion = sincronizacion,
                onRecordatorioClick = onRecordatorioClick,
                onFiltroSeleccionado = onFiltroSeleccionado,
                onReintentarSincronizacion = onReintentarSincronizacion,
                modifier = contenido,
            )
        }
    }
}

@Composable
private fun ListaRecordatorios(
    estado: ListaUiState.ConDatos,
    sincronizacion: EstadoSincronizacion,
    onRecordatorioClick: (Int) -> Unit,
    onFiltroSeleccionado: (FiltroEstado) -> Unit,
    onReintentarSincronizacion: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            // En tablets el contenido no se estira de borde a borde.
            modifier = Modifier.widthIn(max = 640.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                BannerSincronizacion(sincronizacion, estado.pendientes, estado.conError, onReintentarSincronizacion)
            }
            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FiltroEstado.entries.forEach { filtro ->
                        FilterChip(
                            selected = filtro == estado.filtro,
                            onClick = { onFiltroSeleccionado(filtro) },
                            label = { Text(filtro.etiqueta) },
                        )
                    }
                }
            }
            if (estado.recordatorios.isEmpty()) {
                item { Text("No hay recordatorios para mostrar con los filtros y preferencias actuales.", Modifier.padding(top = 24.dp)) }
            }
            items(estado.recordatorios, key = { it.id }) { recordatorio ->
                TarjetaRecordatorio(recordatorio, onClick = { onRecordatorioClick(recordatorio.id) })
            }
        }
    }
}

/** Menú para forzar cada situación de red (sin conexión, timeout, error 500, 400) y comprobar cómo responde la app. */
@Composable
private fun MenuSimulacion(onSimulacionSeleccionada: (ModoSimulacion) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    IconButton(onClick = { abierto = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = "Simular red")
    }
    DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
        ModoSimulacion.entries.forEach { modo ->
            DropdownMenuItem(
                text = { Text("Simular: ${modo.etiqueta}") },
                onClick = {
                    abierto = false
                    onSimulacionSeleccionada(modo)
                },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ListaScreenPreview() {
    RecordatoriosTheme {
        ListaScreen(
            ListaUiState.ConDatos(DatosSimulados.recordatorios), EstadoSincronizacion.Inactivo,
            {}, {}, {}, {}, {}, {}, {}, {},
        )
    }
}
