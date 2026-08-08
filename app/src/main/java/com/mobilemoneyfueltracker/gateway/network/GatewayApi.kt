package com.mobilemoneyfueltracker.gateway.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

@Serializable
data class SmsUploadRequest(
    @SerialName("device_identifier") val deviceIdentifier: String,
    val sender: String,
    val body: String,
    @SerialName("received_at") val receivedAt: String,
)

@Serializable
data class SmsUploadResponse(
    val outcome: String,
    @SerialName("raw_sms_message_id") val rawSmsMessageId: String,
    @SerialName("transaction_id") val transactionId: String? = null,
    @SerialName("requires_review") val requiresReview: Boolean,
)

interface GatewayApi {
    @POST("api/v1/gateway/sms")
    suspend fun uploadSms(
        @Header("Authorization") authorization: String,
        @Body request: SmsUploadRequest,
    ): SmsUploadResponse
}
