package com.yohanvelasquez.recordatorios.ui.auth

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yohanvelasquez.recordatorios.ui.componentes.CampoTexto
import com.yohanvelasquez.recordatorios.ui.theme.RecordatoriosTheme

@Composable
fun AuthRoute(viewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory)) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val aviso by viewModel.aviso.collectAsStateWithLifecycle()
    AuthScreen(
        uiState = uiState,
        aviso = aviso,
        onCorreoChange = viewModel::onCorreoChange,
        onContrasenaChange = viewModel::onContrasenaChange,
        onRepetirChange = viewModel::onRepetirChange,
        onCambiarModo = viewModel::onCambiarModo,
        onEnviar = viewModel::onEnviar,
    )
}

@Composable
fun AuthScreen(
    uiState: AuthUiState,
    aviso: String?,
    onCorreoChange: (String) -> Unit,
    onContrasenaChange: (String) -> Unit,
    onRepetirChange: (String) -> Unit,
    onCambiarModo: (ModoAcceso) -> Unit,
    onEnviar: () -> Unit,
) {
    var verContrasena by remember { mutableStateOf(false) }
    val habilitado = !uiState.autenticando
    val registrando = uiState.modo == ModoAcceso.REGISTRAR
    val transformacion = if (verContrasena) VisualTransformation.None else PasswordVisualTransformation()
    val alternar: @Composable () -> Unit = {
        TextButton(onClick = { verContrasena = !verContrasena }) { Text(if (verContrasena) "Ocultar" else "Mostrar") }
    }

    Scaffold { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.widthIn(max = 480.dp).verticalScroll(rememberScrollState()).imePadding().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Recordatorios", style = MaterialTheme.typography.headlineMedium)
                Text(
                    if (registrando) "Crea una cuenta para guardar tus recordatorios y verlos en otros dispositivos."
                    else "Inicia sesión para ver tus recordatorios.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                if (aviso != null) Mensaje(aviso, esError = false)

                CampoTexto(
                    valor = uiState.correo,
                    onValorChange = onCorreoChange,
                    etiqueta = "Correo electrónico",
                    error = uiState.errorVisible(CampoAcceso.CORREO),
                    habilitado = habilitado,
                    opcionesTeclado = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                CampoTexto(
                    valor = uiState.contrasena,
                    onValorChange = onContrasenaChange,
                    etiqueta = "Contraseña",
                    error = uiState.errorVisible(CampoAcceso.CONTRASENA),
                    habilitado = habilitado,
                    opcionesTeclado = KeyboardOptions(keyboardType = KeyboardType.Password),
                    transformacion = transformacion,
                    iconoFinal = alternar,
                )
                if (registrando) {
                    CampoTexto(
                        valor = uiState.repetir,
                        onValorChange = onRepetirChange,
                        etiqueta = "Repite la contraseña",
                        error = uiState.errorVisible(CampoAcceso.REPETIR),
                        habilitado = habilitado,
                        opcionesTeclado = KeyboardOptions(keyboardType = KeyboardType.Password),
                        transformacion = transformacion,
                    )
                }

                uiState.error?.let { Mensaje(it.mensaje, esError = true) }

                Button(onClick = onEnviar, enabled = uiState.puedeEnviar, modifier = Modifier.fillMaxWidth()) {
                    if (uiState.autenticando) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(if (registrando) "Creando cuenta…" else "Iniciando sesión…", Modifier.padding(start = 8.dp))
                    } else {
                        Text(uiState.modo.accion)
                    }
                }
                TextButton(
                    onClick = { onCambiarModo(if (registrando) ModoAcceso.INICIAR else ModoAcceso.REGISTRAR) },
                    enabled = habilitado,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(if (registrando) "Ya tengo cuenta: iniciar sesión" else "No tengo cuenta: registrarme")
                }
            }
        }
    }
}

/** Aviso o error con ícono y texto (no depende solo del color); un lector de pantalla lo anuncia al aparecer. */
@Composable
private fun Mensaje(texto: String, esError: Boolean, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Assertive },
        colors = CardDefaults.cardColors(
            containerColor = if (esError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(if (esError) Icons.Filled.Warning else Icons.Filled.Info, contentDescription = null)
            Text(texto)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AuthScreenPreview() {
    RecordatoriosTheme {
        AuthScreen(AuthUiState(correo = "ana@correo", tocados = setOf(CampoAcceso.CORREO)), null, {}, {}, {}, {}, {})
    }
}
