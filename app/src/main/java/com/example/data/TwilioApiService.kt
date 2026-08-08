package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

@JsonClass(generateAdapter = true)
data class TwilioMessageResponse(
    @Json(name = "sid") val sid: String? = null,
    @Json(name = "account_sid") val accountSid: String? = null,
    @Json(name = "status") val status: String? = null, // "queued", "sent", "failed", "delivered"
    @Json(name = "to") val to: String? = null,
    @Json(name = "from") val from: String? = null,
    @Json(name = "body") val body: String? = null,
    @Json(name = "error_code") val errorCode: Int? = null,
    @Json(name = "error_message") val errorMessage: String? = null,
    @Json(name = "date_created") val dateCreated: String? = null,
    @Json(name = "price") val price: String? = null
)

interface TwilioApiService {

    @FormUrlEncoded
    @POST("2010-04-01/Accounts/{accountSid}/Messages.json")
    suspend fun sendSms(
        @Path("accountSid") accountSid: String,
        @Header("Authorization") authHeader: String,
        @Field("To") to: String,
        @Field("From") from: String,
        @Field("Body") body: String
    ): TwilioMessageResponse

    @FormUrlEncoded
    @POST("2010-04-01/Accounts/{accountSid}/Messages.json")
    suspend fun sendWhatsAppTemplate(
        @Path("accountSid") accountSid: String,
        @Header("Authorization") authHeader: String,
        @Field("To") to: String,
        @Field("From") from: String,
        @Field("ContentSid") contentSid: String
    ): TwilioMessageResponse

    @FormUrlEncoded
    @POST("2010-04-01/Accounts/{accountSid}/Messages.json")
    suspend fun sendWhatsAppMessage(
        @Path("accountSid") accountSid: String,
        @Header("Authorization") authHeader: String,
        @Field("To") to: String,
        @Field("From") from: String,
        @Field("Body") body: String
    ): TwilioMessageResponse
}
