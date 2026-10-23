package com.yohanvelasquez.recordatorios

import android.app.Application
import com.yohanvelasquez.recordatorios.data.ControlSimulacion
import com.yohanvelasquez.recordatorios.data.DatosSimulados
import com.yohanvelasquez.recordatorios.data.RecordatorioRepository
import com.yohanvelasquez.recordatorios.data.RecordatorioRepositoryEnMemoria

/** Crea una sola instancia del repositorio para toda la app. Para cambiar la fuente de datos basta con tocar esta clase. */
class RecordatoriosApp : Application() {
    val simulacion = ControlSimulacion()
    val repositorio: RecordatorioRepository by lazy {
        RecordatorioRepositoryEnMemoria(DatosSimulados.recordatorios, simulacion)
    }
}
