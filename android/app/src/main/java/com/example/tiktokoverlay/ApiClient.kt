package com.example.tiktokoverlay

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {
    @POST("api/auth/signup")
    suspend fun signup(@Body body: SignupRequest): Response<AuthResponse>

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): Response<AuthResponse>

    @GET("api/creators/{username}")
    suspend fun lookupCreator(@Path("username") username: String): Response<CreatorLookupResponse>

    @POST("api/donations/initiate")
    suspend fun initiateDonation(
        @Header("Authorization") bearerToken: String,
        @Body body: InitiateDonationRequest
    ): Response<InitiateDonationResponse>

    @GET("api/donations/status/{tx_ref}")
    suspend fun donationStatus(
        @Header("Authorization") bearerToken: String,
        @Path("tx_ref") txRef: String
    ): Response<DonationStatusResponse>
}

object ApiClient {
    // Simple in-memory token holder + tiny persistence via SharedPreferences.
    // A production app should keep this in EncryptedSharedPreferences instead.
    var authToken: String? = null

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()

    val service: ApiService = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ApiService::class.java)

    fun bearer(): String = "Bearer ${authToken ?: ""}"
}
