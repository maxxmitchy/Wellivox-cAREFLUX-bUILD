package com.example.data

import com.example.BuildConfig
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val systemInstruction: Content? = null,
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val tools: List<Tool>? = null
)

@JsonClass(generateAdapter = true)
data class Tool(
    val googleSearch: GoogleSearch? = null
)

@JsonClass(generateAdapter = true)
data class GoogleSearch(
    val dummy: String? = null
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val responseType: String? = null,
    val responseMimeType: String? = null
)


@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>,
    val role: String? = null
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<Candidate>
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
    
    @POST("v1beta/models/gemini-3.1-pro-preview:generateContent")
    suspend fun generateContentPro(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val lock = java.util.concurrent.locks.ReentrantLock()
    private var lastCallTime = 0L

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            val urlStr = request.url.toString()
            
            // Only rate-limit requests to Gemini API
            if (!urlStr.contains("generativelanguage.googleapis.com")) {
                return@addInterceptor chain.proceed(request)
            }

            lock.lock()
            try {
                // Ensure at least 1500ms spacing between initializations of consecutive calls to prevent rapid parallel or duplicate bursts
                val now = System.currentTimeMillis()
                val diff = now - lastCallTime
                if (diff < 1500) {
                    try {
                        Thread.sleep(1500 - diff)
                    } catch (e: InterruptedException) {
                        // ignore
                    }
                }

                android.util.Log.d("RateLimitInterceptor", "Gemini API Call executing exclusively... URL: ${request.url.encodedPath}")
                val response = chain.proceed(request)
                lastCallTime = System.currentTimeMillis()
                response
            } finally {
                lock.unlock()
            }
        }
        .build()

    val service: GeminiApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
        retrofit.create(GeminiApiService::class.java)
    }
}
