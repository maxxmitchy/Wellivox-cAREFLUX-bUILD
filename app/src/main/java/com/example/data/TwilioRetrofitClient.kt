package com.example.data

import android.util.Base64
import com.example.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object TwilioConstants {
    val ACCOUNT_SID: String
        get() = runCatching { BuildConfig.TWILIO_ACCOUNT_SID }.getOrNull()?.ifBlank { null }
            ?: ""

    val AUTH_TOKEN: String
        get() = runCatching { BuildConfig.TWILIO_AUTH_TOKEN }.getOrNull()?.ifBlank { null }
            ?: ""

    val API_KEY_SID: String
        get() = runCatching { BuildConfig.TWILIO_API_KEY_SID }.getOrNull()?.ifBlank { null }
            ?: ""

    val API_KEY_SECRET: String
        get() = runCatching { BuildConfig.TWILIO_API_KEY_SECRET }.getOrNull()?.ifBlank { null }
            ?: ""

    val SMS_NUMBER: String
        get() = runCatching { BuildConfig.TWILIO_SMS_NUMBER }.getOrNull()?.ifBlank { null }
            ?: "+17372212163"

    val WHATSAPP_SENDER: String
        get() = runCatching { BuildConfig.TWILIO_WHATSAPP_SENDER }.getOrNull()?.ifBlank { null }
            ?: "whatsapp:+17372212163"

    val TEMPLATE_REFILL_SID: String
        get() = runCatching { BuildConfig.TWILIO_TEMPLATE_REFILL_SID }.getOrNull()?.ifBlank { null }
            ?: "HXfe5ab5f00277942d4d4200328b4d403c"

    val TEMPLATE_NOTIF_SID: String
        get() = runCatching { BuildConfig.TWILIO_TEMPLATE_NOTIF_SID }.getOrNull()?.ifBlank { null }
            ?: "HXa9d0fd6215858003e64aae6f151ea1e7"

    fun getBasicAuthHeader(): String {
        val userPass = "$ACCOUNT_SID:$AUTH_TOKEN"
        val encoded = Base64.encodeToString(userPass.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "Basic $encoded"
    }
}

object TwilioRetrofitClient {
    private const val BASE_URL = "https://api.twilio.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    val service: TwilioApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(TwilioApiService::class.java)
    }
}
