package com.example.locationinfoapp

import android.util.Log
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface PlacesApiService{
    @GET("/api/v1/neighborhoods/nearby")
    suspend fun getNearbyPlaces(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double
    ): PlacesResponse
}



object RetrofitInstance {
    private const val BASE_URL = "https://expressjs-api-gps-info.onrender.com/"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    val api : PlacesApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PlacesApiService::class.java)
    }
}