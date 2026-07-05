package com.example.locationinfoapp

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface PlacesApiService {
    @GET("/api/v1/places/nearby/enhanced")
    suspend fun getNearbyPlaces(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("range") range: Int = 5
    ): PlacesResponse
}

object RetrofitInstance {
    // Your company's API
    private const val BASE_URL = "https://expressjs-api-gps-info.onrender.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(180, TimeUnit.SECONDS) // 3 minutes for slow free tier API
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    val api: PlacesApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PlacesApiService::class.java)
    }
}
