package com.yohanvelasquez.recordatorios.ui.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Estado de carga a pantalla completa. */
@Composable
fun IndicadorCarga(modifier: Modifier = Modifier, mensaje: String = "Cargando…") {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(Modifier.semantics { contentDescription = mensaje })
            Text(mensaje, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** Estado vacío: explica la situación y ofrece crear el primer recordatorio. */
@Composable
fun MensajeVacio(onCrearRecordatorio: () -> Unit, modifier: Modifier = Modifier) {
    MensajeConAccion(
        icono = Icons.Filled.Info,
        titulo = "Todavía no hay recordatorios",
        mensaje = "Crea tu primer recordatorio para empezar a organizar tus tareas.",
        textoAccion = "Crear recordatorio",
        iconoAccion = Icons.Filled.Add,
        onAccion = onCrearRecordatorio,
        modifier = modifier,
    )
}

/** Estado de error: mensaje comprensible y opción de reintento. */
@Composable
fun MensajeError(mensaje: String, onReintentar: () -> Unit, modifier: Modifier = Modifier) {
    MensajeConAccion(
        icono = Icons.Filled.Warning,
        titulo = "Algo salió mal",
        mensaje = mensaje,
        textoAccion = "Reintentar",
        iconoAccion = Icons.Filled.Refresh,
        onAccion = onReintentar,
        colorIcono = MaterialTheme.colorScheme.error,
        modifier = modifier,
    )
}

@Composable
private fun MensajeConAccion(
    icono: ImageVector,
    titulo: String,
    mensaje: String,
    textoAccion: String,
    iconoAccion: ImageVector,
    onAccion: () -> Unit,
    modifier: Modifier = Modifier,
    colorIcono: Color = MaterialTheme.colorScheme.primary,
) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            // El lector de pantalla anuncia el mensaje cuando aparece.
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Icon(icono, contentDescription = null, tint = colorIcono, modifier = Modifier.size(56.dp))
            Text(titulo, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(mensaje, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Button(onClick = onAccion) {
                Icon(iconoAccion, contentDescription = null)
                Text(textoAccion, Modifier.padding(start = 8.dp))
            }
        }
    }
}
