package com.yohanvelasquez.recordatorios.ui.preferencias

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yohanvelasquez.recordatorios.data.preferencias.OrdenLista
import com.yohanvelasquez.recordatorios.data.preferencias.Preferencias
import com.yohanvelasquez.recordatorios.data.preferencias.TemaApp
import com.yohanvelasquez.recordatorios.ui.theme.RecordatoriosTheme

@Composable
fun PreferenciasRoute(
    onVolver: () -> Unit,
    viewModel: PreferenciasViewModel = viewModel(factory = PreferenciasViewModel.Factory),
) {
    val preferencias by viewModel.uiState.collectAsStateWithLifecycle()
    PreferenciasScreen(
        preferencias = preferencias,
        onTemaChange = viewModel::onTemaChange,
        onMostrarCompletadosChange = viewModel::onMostrarCompletadosChange,
        onOrdenChange = viewModel::onOrdenChange,
        onVolver = onVolver,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferenciasScreen(
    preferencias: Preferencias,
    onTemaChange: (TemaApp) -> Unit,
    onMostrarCompletadosChange: (Boolean) -> Unit,
    onOrdenChange: (OrdenLista) -> Unit,
    onVolver: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Preferencias") },
                navigationIcon = {
                    IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
                },
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier.widthIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Tema", style = MaterialTheme.typography.titleMedium)
                OpcionesRadio(TemaApp.entries, preferencias.tema, onTemaChange) { it.etiqueta }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                Text("Orden del listado", style = MaterialTheme.typography.titleMedium)
                OpcionesRadio(OrdenLista.entries, preferencias.orden, onOrdenChange) { it.etiqueta }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().toggleable(
                        value = preferencias.mostrarCompletados,
                        role = Role.Switch,
                        onValueChange = onMostrarCompletadosChange,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Mostrar recordatorios completados", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = preferencias.mostrarCompletados, onCheckedChange = null)
                }
                Text(
                    "Estas preferencias se guardan en el dispositivo y se conservan al cerrar la aplicación.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun <T> OpcionesRadio(opciones: List<T>, seleccionada: T, onSeleccion: (T) -> Unit, etiqueta: (T) -> String) {
    Column(Modifier.selectableGroup()) {
        opciones.forEach { opcion ->
            Row(
                modifier = Modifier.fillMaxWidth().selectable(
                    selected = opcion == seleccionada,
                    role = Role.RadioButton,
                    onClick = { onSeleccion(opcion) },
                ).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = opcion == seleccionada, onClick = null)
                Text(etiqueta(opcion), Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreferenciasScreenPreview() {
    RecordatoriosTheme { PreferenciasScreen(Preferencias(), {}, {}, {}, {}) }
}
