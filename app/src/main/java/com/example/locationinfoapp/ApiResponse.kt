package com.example.locationinfoapp

import com.google.gson.annotations.SerializedName

data class PlacesResponse(
    val count: Int,
    val input: InputData,
    val places: List<PlaceDto>
)

data class InputData(
    val lat: Double,
    val lng: Double,
    val range: Int
)

data class PlaceDto(
    val type: String?,
    val placeName: String,
    val placeType: String,
    val country: String?,
    val state: String?,
    val region: String?,
    val district: String?,
    val pincode: String?,
    val latitude: Double,  // API returns this - NOT nullable
    val longitude: Double, // API returns this - NOT nullable
    val lokSabhaConstituency: String?,
    val vidhanSabhaConstituency: String?,
    val imageUrls: List<String>?,
    val wikipediaUrl: String?,
    @SerializedName("distance_km")
    val distanceKm: Double?,
    val pincodeInfo: PincodeInfo?
)

data class PincodeInfo(
    val officeName: String?,
    val divisionName: String?,
    val regionName: String?,
    val stateName: String?,
    val district: String?,
    val pincode: String?,
    val matchType: String?,
    val matchDistance: Int?
)