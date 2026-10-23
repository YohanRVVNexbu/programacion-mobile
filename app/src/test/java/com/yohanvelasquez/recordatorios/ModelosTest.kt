package com.yohanvelasquez.recordatorios

import com.yohanvelasquez.recordatorios.data.ControlSimulacion
import com.yohanvelasquez.recordatorios.data.DatosSimulados
import com.yohanvelasquez.recordatorios.data.ModoSimulacion
import com.yohanvelasquez.recordatorios.data.RecordatorioRepositoryEnMemoria
import com.yohanvelasquez.recordatorios.model.Categoria
import com.yohanvelasquez.recordatorios.model.Prioridad
import com.yohanvelasquez.recordatorios.model.validarFormulario
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ModelosTest {

    @Test
    fun validacionDelFormulario() {
        val malo = validarFormulario("  ab  ", "x".repeat(201), Categoria("Otra"), null)
        assertNotNull(malo.titulo)       // menos de 4 caracteres sin contar espacios
        assertNotNull(malo.descripcion)  // más de 200 caracteres
        assertNotNull(malo.categoria)    // no pertenece a las opciones
        assertNotNull(malo.prioridad)    // sin seleccionar

        val ok = validarFormulario("  Comprar pan ", "x".repeat(200), Categoria.Hogar, Prioridad.Baja)
        assertTrue(ok.esValido)
        assertNull(validarFormulario("abcd", "", Categoria.Hogar, Prioridad.Baja).titulo)
    }

    @Test
    fun repositorioGuardaYSimulaFallos() = runTest {
        val simulacion = ControlSimulacion()
        val repo = RecordatorioRepositoryEnMemoria(DatosSimulados.recordatorios, simulacion)

        val nuevo = repo.guardarRecordatorio("Llamar al banco", null, Categoria.Trabajo, Prioridad.Alta)
        assertEquals(DatosSimulados.recordatorios.size + 1, nuevo.id)
        assertEquals(nuevo, repo.obtenerRecordatorio(nuevo.id))
        assertEquals(DatosSimulados.recordatorios.size + 1, repo.observarRecordatorios().first().size)

        simulacion.modo = ModoSimulacion.VACIO
        assertTrue(repo.observarRecordatorios().first().isEmpty())

        simulacion.modo = ModoSimulacion.ERROR_GUARDADO
        val error = runCatching { repo.guardarRecordatorio("Otro más", null, Categoria.Hogar, Prioridad.Baja) }
        assertTrue(error.exceptionOrNull() is IOException)
    }
}
