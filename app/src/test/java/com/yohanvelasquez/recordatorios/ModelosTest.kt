package com.yohanvelasquez.recordatorios

import com.yohanvelasquez.recordatorios.data.DatosSimulados
import com.yohanvelasquez.recordatorios.model.Categoria
import com.yohanvelasquez.recordatorios.model.EstadoRecordatorio
import com.yohanvelasquez.recordatorios.model.Prioridad
import com.yohanvelasquez.recordatorios.model.ResultadoValidacion
import com.yohanvelasquez.recordatorios.model.crearRecordatorio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ModelosTest {

    @Test
    fun validacionRechazaDatosInvalidos() {
        val ahora = LocalDateTime.now()
        val r = crearRecordatorio(0, " ", Categoria("Salud"), Prioridad.Alta, fechaLimite = ahora.minusDays(1), ahora = ahora)
        assertTrue(r is ResultadoValidacion.Invalido)
        assertEquals(3, (r as ResultadoValidacion.Invalido).errores.size)
    }

    @Test
    fun coleccionesSimuladas() {
        assertEquals(7, DatosSimulados.recordatorios.size)
        assertEquals(2, DatosSimulados.rechazados.size)
        assertEquals(4, DatosSimulados.categorias.size) // Set sin repetidos
        assertEquals(setOf(Categoria("Trabajo")), setOf(Categoria("Trabajo"), Categoria("Trabajo")))
        val completados = DatosSimulados.filtrarPorEstado(DatosSimulados.recordatorios, EstadoRecordatorio.Completado::class.java)
        assertEquals(2, completados.size)
        assertEquals(3, DatosSimulados.filtrarPorPrioridad(DatosSimulados.recordatorios, Prioridad.Alta).size)
    }
}
