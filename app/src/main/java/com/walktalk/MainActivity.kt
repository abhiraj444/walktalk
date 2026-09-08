package com.walktalk

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.walktalk.data.firebase.FamilyLocation
import com.walktalk.data.firebase.LocationRepository
import com.walktalk.data.firebase.MemberPresence
import com.walktalk.data.firebase.PresenceManager
import com.walktalk.data.location.SosManager
import com.walktalk.data.webrtc.WebRTCManager
import com.walktalk.data.websocket.ConnectionManager
import com.walktalk.data.websocket.SignalingClient
import com.walktalk.service.CallService
import com.walktalk.service.LocationService
import com.walktalk.ui.map.FamilyMapScreen
import com.walktalk.ui.settings.SettingsScreen
import com.walktalk.ui.talk.PttCallScreen
import com.walktalk.ui.talk.TalkScreen
import com.walktalk.util.Constants

class MainActivity : ComponentActivity() {

    private lateinit var presenceManager: PresenceManager
    private lateinit var locationRepository: LocationRepository
    private lateinit var sosManager: SosManager
    private var connectionManager: ConnectionManager? = null
    private var webRtcManager: WebRTCManager? = null
    private var activeSignalingClient: SignalingClient? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            LocationService.start(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val familyId = prefs.getString(Constants.KEY_FAMILY_ID, Constants.DEFAULT_FAMILY_ID) ?: Constants.DEFAULT_FAMILY_ID
        val deviceId = prefs.getString(Constants.KEY_DEVICE_ID, Build.MODEL) ?: Build.MODEL
        val userName = prefs.getString(Constants.KEY_USER_NAME, Build.MODEL) ?: Build.MODEL

        // Initialize singletons
        presenceManager = PresenceManager(familyId, deviceId)
        presenceManager.startPresence(userName)

        locationRepository = LocationRepository(familyId, deviceId)
        sosManager = SosManager(this, familyId, deviceId)

        // Request runtime permissions
        requestRequiredPermissions()

        // Start Hybrid Connection Manager
        connectionManager = ConnectionManager(
            context = this,
            familyId = familyId,
            deviceId = deviceId,
            onConnectionReady = { client ->
                activeSignalingClient = client
            },
            onFallbackToRtdb = {
                // Fallback signaling active
            }
        )
        connectionManager?.start(ConnectionManager.Mode.HYBRID)

        setContent {
            WalkTalkAppUI(
                familyId = familyId,
                deviceId = deviceId,
                userName = userName,
                presenceManager = presenceManager,
                locationRepository = locationRepository,
                sosManager = sosManager
            )
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            LocationService.start(this)
        }
    }

    override fun onDestroy() {
        connectionManager?.destroy()
        webRtcManager?.close()
        super.onDestroy()
    }
}

@Composable
fun WalkTalkAppUI(
    familyId: String,
    deviceId: String,
    userName: String,
    presenceManager: PresenceManager,
    locationRepository: LocationRepository,
    sosManager: SosManager
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var activeCallContact by remember { mutableStateOf<MemberPresence?>(null) }

    val familyMembers by presenceManager.observeFamilyPresence().collectAsState(initial = emptyList())
    val familyLocations by locationRepository.observeFamilyLocations().collectAsState(initial = emptyList())

    if (activeCallContact != null) {
        val contact = activeCallContact!!
        var isHandsFreeLocked by remember { mutableStateOf(false) }

        PttCallScreen(
            contactName = contact.name,
            locationContext = "Online (${contact.status})",
            isHandsFreeLocked = isHandsFreeLocked,
            onPttPressStart = {
                // Unmute WebRTC microphone
            },
            onPttPressEnd = {
                // Mute WebRTC microphone
            },
            onToggleHandsFree = {
                isHandsFreeLocked = !isHandsFreeLocked
            },
            onQuickReactionSent = { _ -> },
            onEndCall = {
                activeCallContact = null
            }
        )
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF1E1E1E)) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Mic, contentDescription = "Talk") },
                        label = { Text("Intercom") },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Color(0xFF00E676))
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.Place, contentDescription = "Map") },
                        label = { Text("Map") },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Color(0xFF00E676))
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Color(0xFF00E676))
                    )
                }
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                when (selectedTab) {
                    0 -> TalkScreen(
                        familyMembers = familyMembers,
                        serverStatus = "Local LAN",
                        onMemberSelected = { member ->
                            activeCallContact = member
                        },
                        onBroadcastEveryone = {
                            activeCallContact = MemberPresence(name = "Everyone (All Family)")
                        }
                    )
                    1 -> FamilyMapScreen(
                        familyLocations = familyLocations,
                        onTriggerSos = {
                            sosManager.triggerSos(userName, 28.6139, 77.2090)
                        }
                    )
                    2 -> SettingsScreen(
                        currentServerMode = "Hybrid (Auto)",
                        familyId = familyId,
                        deviceId = deviceId,
                        tunnelUrl = "wss://rapid-cherry.trycloudflare.com",
                        isGhostMode = false,
                        onServerModeChanged = {},
                        onGhostModeToggled = {}
                    )
                }
            }
        }
    }
}
