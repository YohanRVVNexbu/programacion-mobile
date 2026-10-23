package com.yohanvelasquez.recordatorios.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yohanvelasquez.recordatorios.data.DatosSimulados
import com.yohanvelasquez.recordatorios.model.EstadoRecordatorio
import com.yohanvelasquez.recordatorios.model.EstadoUi
import com.yohanvelasquez.recordatorios.model.Prioridad
import com.yohanvelasquez.recordatorios.model.Recordatorio
import com.yohanvelasquez.recordatorios.ui.theme.RecordatoriosTheme
import java.time.format.DateTimeFormatter

private val filtros: List<Pair<String, Class<out EstadoRecordatorio>?>> = listOf(
    "Todos" to null,
    "Pendiente" to EstadoRecordatorio.Pendiente::class.java,
    "En progreso" to EstadoRecordatorio.EnProgreso::class.java,
    "Completado" to EstadoRecordatorio.Completado::class.java,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaRecordatorios() {
    var filtro by remember { mutableStateOf(filtros.first()) }
    val visibles = DatosSimulados.filtrarPorEstado(DatosSimulados.recordatorios, filtro.second)
        .sortedByDescending { it.prioridad.peso }
    val estado: EstadoUi = if (visibles.isEmpty()) EstadoUi.Vacio else EstadoUi.Exito(visibles)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mis recordatorios") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Resumen() }
            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    filtros.forEach { f ->
                        FilterChip(selected = f == filtro, onClick = { filtro = f }, label = { Text(f.first) })
                    }
                }
            }
            when (estado) {
                EstadoUi.Cargando -> item { CircularProgressIndicator() }
                EstadoUi.Vacio -> item { Text("No hay recordatorios con este estado.") }
                is EstadoUi.Error -> item { Text(estado.mensaje, color = MaterialTheme.colorScheme.error) }
                is EstadoUi.Exito -> items(estado.recordatorios, key = { it.id }) { TarjetaRecordatorio(it) }
            }
            if (DatosSimulados.rechazados.isNotEmpty()) {
                item { Rechazados(DatosSimulados.rechazados) }
            }
        }
    }
}

@Composable
private fun Resumen() {
    val conteo = DatosSimulados.contarPorPrioridad(DatosSimulados.recordatorios)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "${DatosSimulados.recordatorios.size} recordatorios · ${DatosSimulados.categorias.size} categorías",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Alta: ${conteo[Prioridad.Alta] ?: 0}  ·  Media: ${conteo[Prioridad.Media] ?: 0}  ·  Baja: ${conteo[Prioridad.Baja] ?: 0}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DatosSimulados.categorias.forEach { cat ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(DatosSimulados.coloresPorCategoria[cat] ?: Color.Gray, CircleShape))
                        Spacer(Modifier.width(4.dp))
                        Text(cat.nombre, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

private val formatoFecha = DateTimeFormatter.ofPattern("dd/MM HH:mm")

@Composable
fun TarjetaRecordatorio(recordatorio: Recordatorio) {
    val colorCategoria = DatosSimulados.coloresPorCategoria[recordatorio.categoria] ?: Color.Gray
    val completado = recordatorio.estado is EstadoRecordatorio.Completado
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (completado) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    recordatorio.titulo,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                EtiquetaPrioridad(recordatorio.prioridad)
            }
            recordatorio.descripcion?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = {},
                    label = { Text(recordatorio.categoria.nombre) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = colorCategoria.copy(alpha = 0.15f),
                        labelColor = colorCategoria,
                    ),
                )
                Text("Estado: ${recordatorio.estado.etiqueta}", style = MaterialTheme.typography.bodySmall)
            }
            val extras = listOfNotNull(
                recordatorio.fechaLimite?.let { "Vence: ${it.format(formatoFecha)}" },
                recordatorio.ubicacion?.let { "Lugar: $it" },
            )
            if (extras.isNotEmpty()) {
                Text(extras.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EtiquetaPrioridad(prioridad: Prioridad) {
    val color = when (prioridad) {
        Prioridad.Alta -> Color(0xFFC62828)
        Prioridad.Media -> Color(0xFFF9A825)
        Prioridad.Baja -> Color(0xFF558B2F)
    }
    Text(
        prioridad.etiqueta,
        color = Color.White,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.background(color, MaterialTheme.shapes.small).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun Rechazados(errores: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Datos rechazados por validación", style = MaterialTheme.typography.titleSmall)
            errores.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PantallaRecordatoriosPreview() {
    RecordatoriosTheme { PantallaRecordatorios() }
}
