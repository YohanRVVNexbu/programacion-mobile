package com.yohanvelasquez.recordatorios

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yohanvelasquez.recordatorios.data.preferencias.Preferencias
import com.yohanvelasquez.recordatorios.data.preferencias.TemaApp
import com.yohanvelasquez.recordatorios.ui.RaizApp
import com.yohanvelasquez.recordatorios.ui.theme.RecordatoriosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as RecordatoriosApp
        setContent {
            // El tema sale de DataStore, así se conserva al cerrar y abrir la app.
            val preferencias by app.preferencias.preferencias.collectAsStateWithLifecycle(initialValue = Preferencias())
            val oscuro = when (preferencias.tema) {
                TemaApp.SISTEMA -> isSystemInDarkTheme()
                TemaApp.CLARO -> false
                TemaApp.OSCURO -> true
            }
            RecordatoriosTheme(darkTheme = oscuro) { RaizApp(app.auth) }
        }
    }
}
