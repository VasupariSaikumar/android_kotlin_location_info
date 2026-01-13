package com.example.locationinfoapp

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "signal_logs")
data class SignalLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val latitude: Double,
    val longitude: Double,
    val signalStrength: Int, // e.g., -95 dBm
    val networkType: String, // e.g., "4G (LTE)"
    val timestamp: Long = System.currentTimeMillis()
)
