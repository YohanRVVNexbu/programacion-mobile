package com.yohanvelasquez.recordatorios

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.yohanvelasquez.recordatorios.ui.navegacion.RecordatoriosNavHost
import com.yohanvelasquez.recordatorios.ui.theme.RecordatoriosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RecordatoriosTheme { RecordatoriosNavHost() }
        }
    }
}
