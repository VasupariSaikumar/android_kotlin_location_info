package com.example.locationinfoapp

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import com.google.android.gms.maps.model.BitmapDescriptorFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationScreen(
    viewModel: LocationViewModel,
    onShowHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val latitudeText by viewModel.latitude.collectAsState()
    val longitudeText by viewModel.longitude.collectAsState()
    val places by viewModel.placesList
    val addressInfo by viewModel.addressDetails
    val currentLocation by viewModel.currentLocation.collectAsState()
    var markerPosition by remember { mutableStateOf<LatLng?>(null) }
    var isRequestingCurrentLocation by remember { mutableStateOf(false) }

    val isRecording by viewModel.isRecording.collectAsState()
    val signalLogs by viewModel.signalLogs.collectAsState(initial = emptyList())
    val isLoadingPlaces by viewModel.isLoadingPlaces.collectAsState()

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(17.3850, 78.4867), 15f)
    }

    LaunchedEffect(Unit) {
        viewModel.fetchPlaces()
    }

    LaunchedEffect(currentLocation) {
        if (isRequestingCurrentLocation && currentLocation != null) {
            val userLocation = currentLocation!!
            val newPos = LatLng(userLocation.latitude, userLocation.longitude)

            markerPosition = newPos
            cameraPositionState.position = CameraPosition.fromLatLngZoom(newPos, 15f)
            isRequestingCurrentLocation = false
        }
    }

    // Helper to choose marker color based on dBm
    fun getSignalColor(dbm: Int): Float {
        return when {
            dbm > -90 -> BitmapDescriptorFactory.HUE_GREEN  // Strong Signal
            dbm > -105 -> BitmapDescriptorFactory.HUE_YELLOW // Fair Signal
            else -> BitmapDescriptorFactory.HUE_RED          // Weak Signal
        }
    }
    Scaffold(
        modifier = modifier,
        topBar = { CenterAlignedTopAppBar(title = { Text("Location Finder") },
            actions = {
                IconButton(onClick = {
                    Toast.makeText(context , "Refreshing API... ", Toast.LENGTH_SHORT).show()
                    viewModel.fetchPlaces()
                }) {
                    Icon(Icons.Default.Refresh , contentDescription = "Refresh Icon")
                }
            }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End){
                ExtendedFloatingActionButton(
                    onClick = {
                        val newState = !isRecording
                        viewModel.toggleRecording(newState)
                        val msg = if (newState) "Recording Started" else "Recording Stopped"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    },
                    containerColor = if (isRecording) Color.Red else MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ) {
                    Icon(
                        if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                        contentDescription = "Record"
                    )
                    Spacer(modifier = Modifier.padding(8.dp))
                    Text(if (isRecording) "Stop Recording" else "Start Recording")
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState
            ) {
                places.forEach { place ->
                    Marker(
                        state = rememberMarkerState(position = LatLng(place.latitude, place.longitude)),
                        title = place.placeName,
                        snippet = place.placeType,
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                    )
                }
                signalLogs.forEach { log ->
                    Marker(
                        state = rememberMarkerState(position = LatLng(log.latitude , log.longitude)),
                        title = "${log.networkType}(${log.signalStrength}dBm)",
                        snippet = "Recorded at : ${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date(log.timestamp))}",
                        icon = BitmapDescriptorFactory.defaultMarker(getSignalColor(log.signalStrength)),
                        alpha = 0.8f
                    )
                }
                markerPosition?.let { pos ->
                    // Use key to force marker recreation when position changes
                    key(pos.latitude, pos.longitude) {
                        val markerState = rememberMarkerState(position = pos)

                        LaunchedEffect(addressInfo) {
                            if (addressInfo != null) markerState.showInfoWindow()
                        }

                        MarkerInfoWindowContent(
                            state = markerState,
                            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED),
                            zIndex = 1.0f,
                            onClick = {
                                viewModel.reverseGeoCode(context, pos.latitude, pos.longitude)
                                markerState.showInfoWindow()
                                false
                            }
                        ) {
                            AddressDetailsCard(addressInfo, pos.latitude, pos.longitude, currentLocation, viewModel)
                        }
                    }
                }
            }
            
            // Loading indicator overlay
            if (isLoadingPlaces) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Loading places...",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "(This may take 2-3 minutes)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp).align(Alignment.TopCenter),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = latitudeText,
                            onValueChange = { viewModel.updateCoordinates(it, longitudeText) },
                            label = { Text("Lat") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = longitudeText,
                            onValueChange = { viewModel.updateCoordinates(latitudeText, it) },
                            label = { Text("Long") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                viewModel.getCurrentLocation { geoPoint ->
                                    val newPos = LatLng(geoPoint.latitude, geoPoint.longitude)
                                    markerPosition = newPos
                                    coroutineScope.launch {
                                        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(newPos, 15f))
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Current")
                        }
                        Button(onClick = {
                            val lat = latitudeText.toDoubleOrNull()
                            val lng = longitudeText.toDoubleOrNull()
                            if (lat != null && lng != null) {
                                val target = LatLng(lat, lng)
                                markerPosition = target

                                coroutineScope.launch {
                                    cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(target, 15f))
                                    viewModel.reverseGeoCode(context, lat, lng)
                                }
                            }
                        },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Go To Location")
                        }
                    }
                }
            }

            // FABs in bottom-left corner to avoid overlapping map zoom controls
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                horizontalAlignment = Alignment.Start
            ) {
                FloatingActionButton(
                    onClick = {
                        Toast.makeText(context, "Saved Location!", Toast.LENGTH_SHORT).show()
                        coroutineScope.launch { viewModel.saveCurrentLocation(null) }
                    }
                ) {
                    Icon(Icons.Default.Save, contentDescription = "Save")
                }
                Spacer(modifier = Modifier.height(12.dp))
                FloatingActionButton(onClick = onShowHistory) {
                    Icon(Icons.Default.History, contentDescription = "History")
                }
            }
        }
    }
}

@Composable
fun AddressDetailsCard(
    addressInfo: AddressData?,
    lat: Double,
    lng: Double,
    currentLoc:org.osmdroid.util.GeoPoint?,
    viewModel: LocationViewModel
) {
    Card(
        modifier = Modifier.width(220.dp).padding(4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(6.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (addressInfo == null) {
                Text("Fetching Details...", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            } else {
                Text(text = "Name: ${addressInfo.placeName}", fontWeight = FontWeight.Bold)
                Text(text = "Type: ${addressInfo.placeType}", style = MaterialTheme.typography.labelSmall)
                Text(text = "Region: ${addressInfo.region}", style = MaterialTheme.typography.labelSmall)
                Text(text = "Address: ${addressInfo.fullAddress}", style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)

                currentLoc?.let { user ->
                    val dist = viewModel.calculateDistance(user.latitude, user.longitude, lat, lng)
                  Divider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(text = "Distance: $dist KM", color = Color.Blue, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}