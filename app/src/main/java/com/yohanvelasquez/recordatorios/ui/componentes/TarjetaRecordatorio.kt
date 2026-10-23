package com.yohanvelasquez.recordatorios.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yohanvelasquez.recordatorios.data.DatosSimulados
import com.yohanvelasquez.recordatorios.model.Categoria
import com.yohanvelasquez.recordatorios.model.EstadoRecordatorio
import com.yohanvelasquez.recordatorios.model.Prioridad
import com.yohanvelasquez.recordatorios.model.Recordatorio

/** Tarjeta de un recordatorio en el listado. Solo informa el clic; no sabe nada de navegación. */
@Composable
fun TarjetaRecordatorio(
    recordatorio: Recordatorio,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val completado = recordatorio.estado is EstadoRecordatorio.Completado
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (completado) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    recordatorio.titulo,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                EtiquetaPrioridad(recordatorio.prioridad)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                EtiquetaCategoria(recordatorio.categoria)
                Text("Estado: ${recordatorio.estado.etiqueta}", style = MaterialTheme.typography.bodySmall)
            }
            EtiquetaSync(recordatorio.sincronizacion)
        }
    }
}

/** La prioridad se muestra con color y con texto, para no depender solo del color. */
@Composable
fun EtiquetaPrioridad(prioridad: Prioridad, modifier: Modifier = Modifier) {
    val color = when (prioridad) {
        Prioridad.Alta -> Color(0xFFC62828)
        Prioridad.Media -> Color(0xFF8D6E00)
        Prioridad.Baja -> Color(0xFF558B2F)
    }
    Text(
        prioridad.etiqueta,
        color = Color.White,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier
            .background(color, MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
fun EtiquetaCategoria(categoria: Categoria, modifier: Modifier = Modifier) {
    val color = DatosSimulados.coloresPorCategoria[categoria] ?: Color.Gray
    Text(
        categoria.nombre,
        color = color,
        style = MaterialTheme.typography.labelLarge,
        modifier = modifier
            .background(color.copy(alpha = 0.12f), MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
