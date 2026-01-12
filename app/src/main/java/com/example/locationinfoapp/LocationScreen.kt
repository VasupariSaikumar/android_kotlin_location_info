package com.example.locationinfoapp

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
           // cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(newPos, 15f))
            cameraPositionState.position = CameraPosition.fromLatLngZoom(newPos, 15f)
            isRequestingCurrentLocation = false
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
            ) }
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
                        snippet = place.placeType
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