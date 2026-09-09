package com.walktalk.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.walktalk.data.firebase.FamilyLocation

@Composable
fun FamilyMapScreen(
    familyLocations: List<FamilyLocation>,
    onTriggerSos: () -> Unit
) {
    // Default location centered on first family member or default coordinate
    val defaultPos = if (familyLocations.isNotEmpty()) {
        LatLng(familyLocations[0].lat, familyLocations[0].lng)
    } else {
        LatLng(28.6139, 77.2090)
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultPos, 14f)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Google Map
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = true)
        ) {
            for (member in familyLocations) {
                Marker(
                    state = MarkerState(position = LatLng(member.lat, member.lng)),
                    title = member.name,
                    snippet = "Activity: ${member.activity} • 🔋 ${member.battery}%"
                )
            }
        }

        // Floating SOS Emergency Button (Top Right)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Button(
                onClick = onTriggerSos,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF1744)),
                shape = CircleShape,
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "SOS",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // Bottom Info Sheet: Family Members Status
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Family Members (${familyLocations.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                for (member in familyLocations) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "📍 ${member.name}",
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${member.activity.replaceFirstChar { it.uppercase() }} • 🔋 ${member.battery}%",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
