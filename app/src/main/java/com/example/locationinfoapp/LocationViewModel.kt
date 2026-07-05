package com.example.locationinfoapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.io.File
import java.io.FileOutputStream

class LocationViewModel(
    private val context: Context,
    private val locationDao: LocationDao
) : ViewModel() {
    private val _currentLocation = MutableStateFlow<GeoPoint?>(null)
    val currentLocation: StateFlow<GeoPoint?> = _currentLocation.asStateFlow()

    private val _latitude = MutableStateFlow("")
    val latitude: StateFlow<String> = _latitude.asStateFlow()

    private val _longitude = MutableStateFlow("")
    val longitude: StateFlow<String> = _longitude.asStateFlow()

    private val _mapCenter = MutableStateFlow<GeoPoint?>(null)
    val mapCenter: StateFlow<GeoPoint?> = _mapCenter.asStateFlow()

    private val _isMapMoving = MutableStateFlow(false)
    val isMapMoving: StateFlow<Boolean> = _isMapMoving.asStateFlow()

    private val _isManualUpdate = MutableStateFlow(false)
    val isManualUpdate: StateFlow<Boolean> = _isManualUpdate.asStateFlow()

    private val  fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    private val locationRequest: LocationRequest by lazy {
        LocationRequest.create().apply {
            interval = 10000
            fastestInterval = 5000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
    }

    val placesList : MutableState<List<SavedLocation>> = mutableStateOf(emptyList())
    var addressDetails = mutableStateOf<AddressData?>(null)
    
    // Loading state for API calls
    private val _isLoadingPlaces = MutableStateFlow(false)
    val isLoadingPlaces: StateFlow<Boolean> = _isLoadingPlaces.asStateFlow()

    private val signalManager = SignalManager(context)

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private var recordingJob: kotlinx.coroutines.Job? = null

    val signalLogs = locationDao.getAllSignalLogs()

    fun getRealCurrentLocation(onLocationFound : (GeoPoint) -> Unit) {
        if (hasLocationPermission()) {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let {
                        val geoPoint = GeoPoint(it.latitude, it.longitude)
                         _currentLocation.value = geoPoint
                        _latitude.value = it.latitude.toString()
                        _longitude.value = it.longitude.toString()
                        onLocationFound(geoPoint)
                        reverseGeoCode(context, it.latitude, it.longitude)
                    }
                }.addOnFailureListener { e ->
                    Log.e("Location", "Error getting location", e)
                }
            } catch (e: SecurityException) {
                Log.e("Location", "Permission Error", e)
            }
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { location ->
                _currentLocation.value = GeoPoint(location.latitude,
                    location.longitude)
                _latitude.value = location.latitude.toString()
                _longitude.value = location.longitude.toString()
            }
        }
    }

    fun updateCoordinates(lat: String, lon: String) {
        _latitude.value = lat
        _longitude.value = lon
    }

    fun setMapMoving(isMoving: Boolean) {
        _isMapMoving.value = isMoving
        if (!isMoving && _mapCenter.value != null) {
            updateFromMapCenter(_mapCenter.value!!)
        }
    }

    fun updateMapCenter(point: GeoPoint) {
        _mapCenter.value = point
        // Optionally, you can update the latitude and longitude here for real-time updates during movement:
        _latitude.value = "%.6f".format(point.latitude)
        _longitude.value = "%.6f".format(point.longitude)
    }

    private fun updateFromMapCenter(point: GeoPoint) {
        _currentLocation.value = point
        _latitude.value = "%.6f".format(point.latitude)
        _longitude.value = "%.6f".format(point.longitude)
    }

    fun onMapIdle(center: GeoPoint) {
        _isMapMoving.value = false
        updateFromMapCenter(center)
    }

    fun startLocationUpdates() {
        if (hasLocationPermission()) {
            try {
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    null
                )
            } catch (e: SecurityException) {
                // Handle permission exception
            }
        }
    }

    fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    fun getCurrentLocation(onLocationFound: (GeoPoint) -> Unit) {
        if (hasLocationPermission()) {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let {
                        val point = GeoPoint(it.latitude, it.longitude)
                        _currentLocation.value = point // Update flow

                        _latitude.value = it.latitude.toString()
                        _longitude.value = it.longitude.toString()
                        onLocationFound(point)

                        reverseGeoCode(context, it.latitude, it.longitude)
                    }
                }
            } catch (e: SecurityException) {
                Log.e("Location", "Permission Error", e)
            }
        }
    }

    private fun isValidCoordinate(lat: Double, lon: Double): Boolean {
        return lat in -90.0..90.0 &&
                lon in -180.0..180.0 &&
                !lat.isNaN() &&
                !lon.isNaN()
    }

    fun goToSpecifiedLocation() {
        try {
            // Clean and validate input
            val cleanLat = latitude.value
                .replace(',', '.')  // Handle both decimal separators
                .trim()
            val cleanLon = longitude.value
                .replace(',', '.')
                .trim()

            if (cleanLat.isBlank() || cleanLon.isBlank()) return

            val lat = cleanLat.toDouble()
            val lon = cleanLon.toDouble()

            // Validate coordinate ranges
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) {
                Log.e("Location", "Coordinates out of range: ($lat, $lon)")
                return
            }

            // Create and verify GeoPoint
            val newLocation = GeoPoint(lat, lon)
            if (newLocation.latitude != lat || newLocation.longitude != lon) {
                Log.e("Location", "GeoPoint conversion failed")
                return
            }

            _currentLocation.value = newLocation
            _mapCenter.value = newLocation
            Log.d("Location", "Going to: ($lat, $lon)")

        } catch (e: Exception) {
            Log.e("Location", "Failed to parse coordinates", e)
        }
    }

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onCleared() {
        super.onCleared()
        stopLocationUpdates()
    }

    suspend fun saveCurrentLocation(mapView: MapView?, address: String? = null) {
        val lat = _latitude.value.toDoubleOrNull() ?: 0.0
        val lng = _longitude.value.toDoubleOrNull() ?: 0.0
        val details = addressDetails.value

        val location = SavedLocation(
            latitude = lat,
            longitude = lng,
            thumbnailPath = null,
            address = details?.fullAddress ?: "Marked Location",
            fullAddress = details?.fullAddress ?: "Marked Location",
            range = 15f,
           placeName = details?.placeName ?: "Marked Location",
            placeType = details?.placeType ?: "Point of Interest",
            region = details?.region ?: "Unknown Region"
        )
        locationDao.insert(location)
    }

    fun getAllSavedLocations() = locationDao.getAllLocations()


    suspend fun deleteLocation(location: SavedLocation) {
        locationDao.delete(location)
        // Delete thumbnail file if exists
        location.thumbnailPath?.let { path ->
            File(path).delete()
        }
    }

    private suspend fun captureMapThumbnail(point: GeoPoint, mapView: MapView?): String? {
        return try {
            val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
            mapView?.draw(Canvas(bitmap))
            val file = File(context.cacheDir, "thumb_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            Log.e("Thumbnail", "Failed to capture thumbnail", e)
            null
        }
    }
    fun fetchPlaces() {
        val currentLat = _latitude.value.toDoubleOrNull() ?: 17.3850
        val currentLng = _longitude.value.toDoubleOrNull() ?: 78.4867

        Log.d("Location", "Fetching places for lat=$currentLat, lng=$currentLng")
        _isLoadingPlaces.value = true

        viewModelScope.launch(Dispatchers.IO) {
           try {
                val response = RetrofitInstance.api.getNearbyPlaces(currentLat, currentLng)
                Log.d("Location", "API SUCCESS! Returned ${response.places.size} places")
                
                // Log first place to verify data
                if (response.places.isNotEmpty()) {
                    val first = response.places.first()
                    Log.d("Location", "First place: ${first.placeName} at (${first.latitude}, ${first.longitude})")
                }

                // Map API places to SavedLocation - API returns actual coordinates!
                val mappedList = response.places.map { place ->
                    SavedLocation(
                        latitude = place.latitude,
                        longitude = place.longitude,
                        placeName = place.placeName,
                        placeType = place.placeType,
                        region = "${place.region ?: "Unknown"}, ${place.district ?: "Unknown"}",
                        fullAddress = "${place.placeName}, ${place.district ?: ""}, ${place.state ?: ""}",
                        range = (place.distanceKm ?: 0.0).toFloat(),
                        thumbnailPath = null
                    )
                }
                
                Log.d("Location", "Mapped ${mappedList.size} places with REAL coordinates")
                
                // Update on Main thread
                withContext(Dispatchers.Main) {
                    _isLoadingPlaces.value = false
                    placesList.value = mappedList
                    android.widget.Toast.makeText(
                        context,
                        "Loaded ${mappedList.size} places from API",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                Log.e("Location", "API FAILED: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _isLoadingPlaces.value = false
                    android.widget.Toast.makeText(
                        context,
                        "API Error: ${e.message}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    fun calculateDistance(userLat: Double, userLong: Double, placeLat: Double, placeLong: Double): String {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(userLat, userLong, placeLat, placeLong, results)
        val distanceInKm = results[0] / 1000
        return String.format("%.2f KM", distanceInKm)
    }
    fun reverseGeoCode(context: Context, latitude: Double, longitude: Double){
        addressDetails.value = null
        viewModelScope.launch(Dispatchers.IO){
            try
            {
                val geocoder = android.location.Geocoder(context , java.util.Locale.getDefault())
                if (latitude in -90.0..90.0 && longitude in -180.0..180.0) {
                    val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val address = addresses[0]
                        withContext(Dispatchers.IO) {
                            addressDetails.value = AddressData(
                                placeName = address.featureName ?: "Selected Location",
                                placeType = if (address.subThoroughfare != null) "Residential/Business" else "Area",
                                region = address.locality ?: address.adminArea ?: "Unknown",
                                fullAddress = address.getAddressLine(0) ?: "Unknown Address"

                            )
                        }
                    }
                }
            }catch (e: Exception){
                Log.e("Location" , "Error in reverse geocoding")
            }
        }
    }
    fun toggleRecording(isStart: Boolean) {
        if (isStart) {
            startRecording()
            startRecording()
        } else {
            stopRecording()
            stopLocationUpdates()
        }
    }

    private fun startRecording() {
        if (_isRecording.value) return
        _isRecording.value = true

        recordingJob = viewModelScope.launch(Dispatchers.IO) {
            while (_isRecording.value) {
                try {
                    val signal = signalManager.getSignalStrength()
                    val loc = _currentLocation.value

                    // LOGIC CHECK: Is the GPS awake yet?
                    if (loc == null) {
                        Log.w("Recorder", "Waiting for GPS...")
                    } else if (signal == null) {
                        Log.w("Recorder", "Signal is null (Sim missing?)")
                    } else {
                        val log = SignalLog(
                            latitude = loc.latitude,
                            longitude = loc.longitude,
                            signalStrength = signal.dbm,
                            networkType = signal.type
                        )
                        locationDao.insertSignalLog(log)
                        Log.d("Recorder", "Saved: ${log.networkType} ${log.signalStrength}dBm")
                    }
                } catch (e: Exception) {
                    Log.e("Recorder", "Error saving log", e)
                }
                delay(5000)
            }
        }
    }
    private fun stopRecording() {
        _isRecording.value = false
        recordingJob?.cancel()
        recordingJob = null
        Log.d("Recorder", "Recording Stopped")
    }

}