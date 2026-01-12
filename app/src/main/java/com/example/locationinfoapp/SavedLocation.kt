package com.example.locationinfoapp

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_locations")
data class SavedLocation(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val latitude: Double,
    val longitude: Double,
    val range : Float,
    val placeName: String,
    val placeType: String,
    val region: String,
    val fullAddress: String,
    val thumbnailPath: String?,
    val timestamp: Long = System.currentTimeMillis(),
    val address: String? = null
)
data class AddressData(
    val placeName: String,
    val placeType: String,
    val region: String,
    val fullAddress: String
)