package com.yohanvelasquez.recordatorios.data

import com.yohanvelasquez.recordatorios.data.remote.ClienteRed
import com.yohanvelasquez.recordatorios.data.remote.ControlSimulacion
import com.yohanvelasquez.recordatorios.data.remote.ErrorRed
import com.yohanvelasquez.recordatorios.data.remote.FakeBackendInterceptor
import com.yohanvelasquez.recordatorios.data.remote.ModoSimulacion
import com.yohanvelasquez.recordatorios.data.remote.RecordatorioApi
import com.yohanvelasquez.recordatorios.data.remote.RecordatorioDto
import com.yohanvelasquez.recordatorios.data.remote.RecordatorioRemoteDataSource
import com.yohanvelasquez.recordatorios.data.remote.ResultadoRed
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/** Interpretación de códigos HTTP y fallos de red con un servidor HTTP real (MockWebServer) y Retrofit. */
class RemotoTest {
    private lateinit var servidor: MockWebServer
    private lateinit var remoto: RecordatorioRemoteDataSource

    private val json = """{"id":"7","clientId":"c1","titulo":"Comprar pan","categoria":"Hogar","prioridad":"BAJA","estado":"PENDIENTE","actualizadoEn":5}"""
    private val dto = RecordatorioDto(null, "c1", "Comprar pan", null, "Hogar", "BAJA", "PENDIENTE")

    @Before
    fun preparar() {
        servidor = MockWebServer().apply { start() }
        val cliente = OkHttpClient.Builder().readTimeout(300, TimeUnit.MILLISECONDS).connectTimeout(300, TimeUnit.MILLISECONDS).build()
        val api = Retrofit.Builder().baseUrl(servidor.url("/")).client(cliente)
            .addConverterFactory(GsonConverterFactory.create()).build().create(RecordatorioApi::class.java)
        remoto = RecordatorioRemoteDataSource(api)
    }

    @After
    fun cerrar() = runCatching { servidor.shutdown() }.let { }

    private fun error(r: ResultadoRed<*>) = (r as ResultadoRed.Fallo).error

    @Test
    fun `200 y 201 son exito y llegan convertidos`() = runBlocking {
        servidor.enqueue(MockResponse().setResponseCode(200).setBody("[$json]"))
        servidor.enqueue(MockResponse().setResponseCode(201).setBody(json))
        val lista = (remoto.listar() as ResultadoRed.Exito).datos
        assertEquals("7", lista.single().id)
        val creado = (remoto.crear(dto) as ResultadoRed.Exito).datos
        assertEquals("c1", creado.clientId)
        assertEquals("POST", servidor.takeRequest().let { servidor.takeRequest().method }) // el segundo pedido fue el POST
    }

    @Test
    fun `204 al eliminar es exito`() = runBlocking {
        servidor.enqueue(MockResponse().setResponseCode(204))
        assertTrue(remoto.eliminar("7") is ResultadoRed.Exito)
    }

    @Test
    fun `los codigos de error se distinguen`() = runBlocking {
        servidor.enqueue(MockResponse().setResponseCode(400))
        servidor.enqueue(MockResponse().setResponseCode(422))
        servidor.enqueue(MockResponse().setResponseCode(404))
        servidor.enqueue(MockResponse().setResponseCode(500))
        servidor.enqueue(MockResponse().setResponseCode(503))
        assertEquals(ErrorRed.SolicitudInvalida(400), error(remoto.crear(dto)))
        assertEquals(ErrorRed.SolicitudInvalida(422), error(remoto.crear(dto)))
        assertEquals(ErrorRed.NoEncontrado, error(remoto.actualizar("7", dto)))
        assertEquals(ErrorRed.Servidor(500), error(remoto.listar()))
        assertEquals(ErrorRed.Servidor(503), error(remoto.listar()))
    }

    @Test
    fun `un servidor que no responde es timeout`() = runBlocking {
        servidor.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        assertEquals(ErrorRed.Timeout, error(remoto.listar()))
    }

    @Test
    fun `un servidor caido es falta de conexion`() = runBlocking {
        servidor.shutdown()
        assertEquals(ErrorRed.SinConexion, error(remoto.listar()))
    }

    @Test
    fun `un JSON invalido no rompe la app`() = runBlocking {
        servidor.enqueue(MockResponse().setResponseCode(200).setBody("esto no es json"))
        assertTrue(error(remoto.listar()) is ErrorRed.Desconocido)
    }

    // ---- El servicio simulado de la app, usado a traves de Retrofit ----

    private fun simulado(simulacion: ControlSimulacion, conectado: Boolean = true) = RecordatorioRemoteDataSource(
        ClienteRed.crearApi("http://servicio.simulado/", FakeBackendInterceptor(simulacion, { conectado }, archivo = null, latenciaMs = 0)),
    )

    @Test
    fun `el servicio simulado no duplica un reenvio con el mismo clientId`() = runBlocking {
        val s = simulado(ControlSimulacion())
        val inicial = (s.listar() as ResultadoRed.Exito).datos.size
        val a = (s.crear(dto) as ResultadoRed.Exito).datos
        val b = (s.crear(dto) as ResultadoRed.Exito).datos
        assertEquals(a.id, b.id)
        assertEquals(inicial + 1, (s.listar() as ResultadoRed.Exito).datos.size)
        assertEquals(ErrorRed.NoEncontrado, error(s.actualizar("999", dto)))
        assertTrue(s.eliminar(a.id!!) is ResultadoRed.Exito)
        assertEquals(ErrorRed.NoEncontrado, error(s.eliminar(a.id!!)))
        assertEquals(ErrorRed.SolicitudInvalida(400), error(s.crear(dto.copy(clientId = "otro", titulo = "ab"))))
    }

    @Test
    fun `cada modo de simulacion produce el error esperado`() = runBlocking {
        val control = ControlSimulacion()
        val s = simulado(control)
        control.modo = ModoSimulacion.SIN_CONEXION; assertEquals(ErrorRed.SinConexion, error(s.listar()))
        control.modo = ModoSimulacion.TIMEOUT; assertEquals(ErrorRed.Timeout, error(s.listar()))
        control.modo = ModoSimulacion.ERROR_SERVIDOR; assertEquals(ErrorRed.Servidor(500), error(s.listar()))
        control.modo = ModoSimulacion.SOLICITUD_INVALIDA; assertEquals(ErrorRed.SolicitudInvalida(400), error(s.crear(dto)))
        control.modo = ModoSimulacion.NORMAL; assertTrue(s.listar() is ResultadoRed.Exito)
        assertEquals(ErrorRed.SinConexion, error(simulado(ControlSimulacion(), conectado = false).listar())) // sin red real
    }
}
