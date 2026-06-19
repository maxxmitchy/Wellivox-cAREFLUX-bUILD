package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class TermiiSmsRequest(
    @Json(name = "to") val to: String,
    @Json(name = "from") val from: String,
    @Json(name = "sms") val sms: String,
    @Json(name = "type") val type: String = "plain",
    @Json(name = "channel") val channel: String = "dnd",
    @Json(name = "api_key") val apiKey: String
)

@JsonClass(generateAdapter = true)
data class TermiiSmsResponse(
    @Json(name = "code") val code: String? = null,
    @Json(name = "message_id") val messageId: String? = null,
    @Json(name = "message_id_str") val messageIdStr: String? = null,
    @Json(name = "message") val message: String? = null,
    @Json(name = "balance") val balance: Double? = null,
    @Json(name = "user") val user: String? = null
)

interface TermiiApiService {
    @POST("api/sms/send")
    suspend fun sendSms(
        @Body request: TermiiSmsRequest
    ): TermiiSmsResponse
}

object TermiiRetrofitClient {
    private const val BASE_URL = "https://v3.api.termii.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val service: TermiiApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
        retrofit.create(TermiiApiService::class.java)
    }
}
