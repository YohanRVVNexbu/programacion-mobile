package com.yohanvelasquez.recordatorios.data

import com.yohanvelasquez.recordatorios.data.remote.RecordatorioDto
import com.yohanvelasquez.recordatorios.model.Categoria
import com.yohanvelasquez.recordatorios.model.EstadoRecordatorio
import com.yohanvelasquez.recordatorios.model.EstadoSync
import com.yohanvelasquez.recordatorios.model.Prioridad
import com.yohanvelasquez.recordatorios.model.validarFormulario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapeosTest {

    @Test
    fun `de entidad a dominio y a DTO conserva los datos`() {
        val e = entidad("c1", "Comprar pan").copy(id = 3, remoteId = "9", estado = "COMPLETADO", completadoEn = 4_000, prioridad = "ALTA")
        val d = e.aDominio()
        assertEquals(3, d.id)
        assertEquals(Prioridad.Alta, d.prioridad)
        assertTrue(d.estado is EstadoRecordatorio.Completado)
        assertEquals(EstadoSync.PENDIENTE, d.sincronizacion)

        val dto = e.aDto()
        assertEquals("9", dto.id)
        assertEquals("c1", dto.clientId)
        assertEquals("ALTA", dto.prioridad)
        assertEquals("COMPLETADO", dto.estado)
    }

    @Test
    fun `de DTO a entidad conserva el id local y queda sincronizada`() {
        val local = entidad("c1").copy(id = 5, creadoEn = 123)
        val dto = RecordatorioDto("9", "c1", "Comprar pan integral", null, "Hogar", "MEDIA", "EN_PROGRESO", actualizadoEn = 8_000)
        val e = dto.aEntidad(UID, local)!!
        assertEquals(5, e.id)
        assertEquals(UID, e.ownerUid)
        assertEquals(123, e.creadoEn)
        assertEquals("9", e.remoteId)
        assertEquals(EstadoSync.SINCRONIZADO, e.syncEstado)
        assertEquals("Comprar pan integral", e.titulo)
    }

    @Test
    fun `un DTO invalido no llega a la base`() {
        val ok = RecordatorioDto("9", "c1", "Comprar pan", null, "Hogar", "BAJA", "PENDIENTE")
        assertNotNull(ok.aEntidad(UID, null))
        assertNull(ok.copy(id = null).aEntidad(UID, null))
        assertNull(ok.copy(titulo = "ab").aEntidad(UID, null))
        assertNull(ok.copy(titulo = "x".repeat(61)).aEntidad(UID, null))
        assertNull(ok.copy(descripcion = "x".repeat(201)).aEntidad(UID, null))
        assertNull(ok.copy(categoria = "Otra").aEntidad(UID, null))
        assertNull(ok.copy(prioridad = "URGENTE").aEntidad(UID, null))
        assertNull(ok.copy(estado = "QUIZAS").aEntidad(UID, null))
    }

    @Test
    fun `una fila con valores inesperados se lee con valores por defecto en vez de fallar`() {
        val rara = entidad("c1", " ").copy(id = 1, prioridad = "???", estado = "???")
        val d = rara.aDominio()
        assertEquals(Prioridad.Media, d.prioridad)
        assertEquals(EstadoRecordatorio.Pendiente, d.estado)
        assertTrue(d.titulo.length >= 4)
    }

    @Test
    fun `validacion del formulario`() {
        val malo = validarFormulario("  ab  ", "x".repeat(201), Categoria("Otra"), null)
        assertNotNull(malo.titulo); assertNotNull(malo.descripcion); assertNotNull(malo.categoria); assertNotNull(malo.prioridad)
        assertTrue(validarFormulario("  Comprar pan ", "x".repeat(200), Categoria.Hogar, Prioridad.Baja).esValido)
        assertNull(validarFormulario("abcd", "", Categoria.Hogar, Prioridad.Baja).titulo)
    }
}
