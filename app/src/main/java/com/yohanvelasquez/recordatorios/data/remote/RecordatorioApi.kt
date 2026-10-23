package com.yohanvelasquez.recordatorios.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/** Interfaz Retrofit. Devuelve Response para poder interpretar los códigos HTTP en la capa de datos. */
interface RecordatorioApi {
    @GET("recordatorios")
    suspend fun listar(): Response<List<RecordatorioDto>>

    @POST("recordatorios")
    suspend fun crear(@Body dto: RecordatorioDto): Response<RecordatorioDto>

    @PUT("recordatorios/{id}")
    suspend fun actualizar(@Path("id") id: String, @Body dto: RecordatorioDto): Response<RecordatorioDto>

    @DELETE("recordatorios/{id}")
    suspend fun eliminar(@Path("id") id: String): Response<Unit>
}
