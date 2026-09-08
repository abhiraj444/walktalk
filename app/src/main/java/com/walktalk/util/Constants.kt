package com.walktalk.util

object Constants {
    const val DEFAULT_FAMILY_ID = "demo_family"
    const val DEFAULT_PORT = 8080
    const val MDNS_SERVICE_TYPE = "_walktalk._tcp."

    // Notification Channel IDs
    const val CALL_NOTIFICATION_CHANNEL_ID = "walktalk_calls_channel"
    const val SOS_NOTIFICATION_CHANNEL_ID = "walktalk_sos_channel"
    const val LOCATION_NOTIFICATION_CHANNEL_ID = "walktalk_location_channel"

    // Preferences Keys
    const val PREFS_NAME = "walktalk_prefs"
    const val KEY_DEVICE_ID = "device_id"
    const val KEY_USER_NAME = "user_name"
    const val KEY_FAMILY_ID = "family_id"
    const val KEY_SERVER_MODE = "server_mode" // "hybrid", "local_only", "cloud_only"
    const val KEY_CUSTOM_SERVER_URL = "custom_server_url"

    // WebRTC Opus Audio Configuration Constants
    const val OPUS_BITRATE = 32000 // 32 kbps voice optimized
    const val OPUS_SAMPLE_RATE = 16000 // 16kHz wideband voice
}
