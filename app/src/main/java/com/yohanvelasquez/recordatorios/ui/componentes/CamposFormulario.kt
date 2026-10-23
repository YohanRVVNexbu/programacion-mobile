package com.yohanvelasquez.recordatorios.ui.componentes

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Campo de texto controlado: el valor llega desde el estado y cada cambio se informa con [onValorChange].
 * El error se indica con isError, con un texto y con la propiedad semántica error (no solo con color).
 */
@Composable
fun CampoTexto(
    valor: String,
    onValorChange: (String) -> Unit,
    etiqueta: String,
    error: String?,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    maxCaracteres: Int? = null,
    lineasMinimas: Int = 1,
    opcionesTeclado: KeyboardOptions = KeyboardOptions.Default,
    transformacion: VisualTransformation = VisualTransformation.None,
    iconoFinal: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = onValorChange,
        label = { Text(etiqueta) },
        isError = error != null,
        enabled = habilitado,
        singleLine = lineasMinimas == 1,
        minLines = lineasMinimas,
        keyboardOptions = opcionesTeclado,
        visualTransformation = transformacion,
        trailingIcon = iconoFinal ?: if (error != null) {
            { Icon(Icons.Filled.Warning, contentDescription = null) }
        } else null,
        supportingText = {
            Row(Modifier.fillMaxWidth()) {
                Text(error ?: "", modifier = Modifier.weight(1f))
                if (maxCaracteres != null) {
                    Text("${valor.length}/$maxCaracteres", textAlign = TextAlign.End)
                }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .semantics { if (error != null) error(error) },
    )
}

/** Selector de una opción entre varias con FilterChip. La opción elegida muestra un ícono de verificación. */
@Composable
fun <T> SelectorOpciones(
    titulo: String,
    opciones: List<T>,
    seleccionada: T?,
    onSeleccion: (T) -> Unit,
    etiqueta: (T) -> String,
    error: String?,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
) {
    Column(
        modifier = modifier.semantics { if (error != null) error(error) },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(titulo, style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            opciones.forEach { opcion ->
                val elegida = opcion == seleccionada
                FilterChip(
                    selected = elegida,
                    onClick = { onSeleccion(opcion) },
                    enabled = habilitado,
                    label = { Text(etiqueta(opcion)) },
                    leadingIcon = if (elegida) {
                        { Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) }
                    } else null,
                )
            }
        }
        if (error != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
