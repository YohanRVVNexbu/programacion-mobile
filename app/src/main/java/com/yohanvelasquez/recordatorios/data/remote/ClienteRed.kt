package com.yohanvelasquez.recordatorios.data.remote

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ClienteRed {
    /**
     * @param baseUrl URL base configurable (debe terminar en "/").
     * @param backendSimulado si no es null, se instala como interceptor en lugar de llamar a un servidor real.
     */
    fun crearApi(baseUrl: String, backendSimulado: FakeBackendInterceptor?): RecordatorioApi {
        val cliente = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .apply { backendSimulado?.let { addInterceptor(it) } }
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(cliente)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RecordatorioApi::class.java)
    }
}
