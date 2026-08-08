package com.mobilemoneyfueltracker.gateway.network

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object GatewayApiFactory {
    private val json = Json { ignoreUnknownKeys = true }

    fun create(baseUrl: String): GatewayApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(GatewayApi::class.java)
}
