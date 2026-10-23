package com.yohanvelasquez.recordatorios.ui.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yohanvelasquez.recordatorios.data.remote.ErrorRed
import com.yohanvelasquez.recordatorios.data.sync.EstadoSincronizacion
import com.yohanvelasquez.recordatorios.model.EstadoSync

/** Indica si un recordatorio está confirmado por el servicio, pendiente o con error. Usa ícono y texto, no solo color. */
@Composable
fun EtiquetaSync(sync: EstadoSync, modifier: Modifier = Modifier) {
    val (icono, color) = when (sync) {
        EstadoSync.SINCRONIZADO -> Icons.Filled.Check to MaterialTheme.colorScheme.onSurfaceVariant
        EstadoSync.PENDIENTE -> Icons.Filled.Info to MaterialTheme.colorScheme.tertiary
        EstadoSync.ERROR -> Icons.Filled.Warning to MaterialTheme.colorScheme.error
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icono, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(sync.etiqueta, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/**
 * Resumen de la sincronización arriba del listado. Los fallos recuperables (sin red, timeout, 5xx) no bloquean
 * nada: los datos locales siguen disponibles y los cambios se envían cuando se pueda.
 */
@Composable
fun BannerSincronizacion(
    estado: EstadoSincronizacion,
    pendientes: Int,
    conError: Int,
    onReintentar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mensaje: String? = when {
        estado is EstadoSincronizacion.Sincronizando -> null
        estado is EstadoSincronizacion.Fallida && estado.recuperable ->
            "${descripcion(estado.error)} " +
                if (pendientes > 0) "$pendientes cambio(s) se enviarán cuando se pueda." else "Se mostrarán los datos guardados."
        conError > 0 -> "$conError recordatorio(s) no se pudieron sincronizar."
        pendientes > 0 -> "$pendientes cambio(s) pendiente(s) de sincronizar."
        else -> null
    }
    val hayProblema = estado is EstadoSincronizacion.Fallida || conError > 0
    Column(modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        if (estado is EstadoSincronizacion.Sincronizando) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Sincronizando…", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
        }
        if (mensaje != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (hayProblema) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Row(Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (hayProblema) Icons.Filled.Warning else Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(mensaje, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                    TextButton(onClick = onReintentar) { Text("Reintentar") }
                }
            }
        }
    }
}

private fun descripcion(error: ErrorRed): String = when (error) {
    ErrorRed.SinConexion -> "Sin conexión."
    ErrorRed.Timeout -> "El servidor tardó demasiado."
    is ErrorRed.Servidor -> "El servidor tuvo un problema (${error.codigo})."
    else -> error.mensaje
}
