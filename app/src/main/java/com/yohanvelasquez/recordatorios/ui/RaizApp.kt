package com.yohanvelasquez.recordatorios.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yohanvelasquez.recordatorios.data.auth.AuthRepository
import com.yohanvelasquez.recordatorios.data.auth.EstadoSesion
import com.yohanvelasquez.recordatorios.ui.auth.AuthRoute
import com.yohanvelasquez.recordatorios.ui.componentes.IndicadorCarga
import com.yohanvelasquez.recordatorios.ui.navegacion.RecordatoriosNavHost

/**
 * Decide qué se muestra según la sesión: comprobación inicial, acceso o la app.
 * La app se recompone con `key(uid)`: al cambiar de cuenta se destruye toda la navegación y sus ViewModels,
 * así la cuenta nueva nunca ve pantallas ni estados de la anterior.
 */
@Composable
fun RaizApp(auth: AuthRepository) {
    val sesion by auth.sesion.collectAsStateWithLifecycle()
    when (val estado = sesion) {
        EstadoSesion.Verificando -> IndicadorCarga(mensaje = "Verificando sesión…")
        EstadoSesion.SinSesion -> AuthRoute()
        is EstadoSesion.Activa -> key(estado.usuario.uid) { RecordatoriosNavHost() }
    }
}
