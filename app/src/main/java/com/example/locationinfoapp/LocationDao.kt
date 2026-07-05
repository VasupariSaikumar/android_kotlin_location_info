package com.example.locationinfoapp

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {
    @Insert
    suspend fun insert(location: SavedLocation)

    @Query("SELECT * FROM saved_locations ORDER BY timestamp DESC")
    fun getAllLocations(): Flow<List<SavedLocation>>

    @Delete
    suspend fun delete(location: SavedLocation)

    @Insert
    suspend fun insertSignalLog(log: SignalLog)

    @Query("SELECT * FROM signal_logs ORDER BY timestamp DESC")
    fun getAllSignalLogs(): kotlinx.coroutines.flow.Flow<List<SignalLog>>

    @Query("DELETE FROM signal_logs")
    suspend fun clearSignalLogs()
}