package com.example.locationinfoapp

data class PlacesResponse(
    val count: Int,
    val places: List<PlaceDto>
)
data class PlaceDto(
    val placeName: String,
    val placeType: String,
    val region: String,
    val imageUrls: List<String>?
)