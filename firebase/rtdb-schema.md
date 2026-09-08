# WalkTalk — Firebase Realtime Database Schema

This document details the data layout stored in Firebase Realtime Database. All nodes reside under `/families/{familyId}/`.

```
/families/{familyId}/
├── server_url
│   ├── url: "https://rapid-quiet-cherry.trycloudflare.com"
│   └── updatedAt: 1725811200000
│
├── members/
│   └── {deviceId}/
│       ├── name: "Papa"
│       ├── fcmToken: "eXampleFcmToken..."
│       ├── role: "parent"
│       └── registeredAt: 1725811000000
│
├── presence/
│   └── {deviceId}/
│       ├── online: true
│       ├── status: "available"   // "available" | "reachable" | "dnd" | "offline"
│       ├── battery: 84
│       ├── activity: "still"     // "still" | "walking" | "in_vehicle"
│       └── lastSeen: 1725811250000 (driven by onDisconnect())
│
├── live_locations/
│   └── {deviceId}/
│       ├── lat: 28.613939
│       ├── lng: 77.209021
│       ├── accuracy: 10.2
│       ├── speed: 0.0
│       ├── heading: 180.0
│       ├── battery: 84
│       ├── activity: "still"
│       └── timestamp: 1725811260000
│
├── geofences/
│   └── {fenceId}/
│       ├── name: "School"
│       ├── lat: 28.610000
│       ├── lng: 77.230000
│       ├── radius: 200
│       └── alerts/
│           └── {deviceId}/
│               ├── enter: true
│               └── exit: true
│
├── geofence_events/
│   └── {eventId}/
│       ├── deviceId: "sis_phone"
│       ├── fenceId: "fence_school"
│       ├── transition: "ENTER"    // "ENTER" | "EXIT" | "DWELL"
│       └── timestamp: 1725811300000
│
├── call_requests/
│   └── {callId}/
│       ├── callerId: "mama_phone"
│       ├── callerName: "Mama"
│       ├── targetId: "papa_phone"
│       ├── mode: "ptt"            // "ptt" or "duplex"
│       ├── timestamp: 1725811400000
│       ├── processed: true
│       └── processedAt: 1725811401000
│
├── calls/ (Fallback signaling if WebSocket unavailable)
│   └── {callId}/
│       ├── offer: { sdp: "...", type: "offer" }
│       ├── answer: { sdp: "...", type: "answer" }
│       └── ice_candidates/
│           ├── caller/
│           └── callee/
│
└── sos/
    └── {sosId}/
        ├── deviceId: "sis_phone"
        ├── lat: 28.6139
        ├── lng: 77.2090
        ├── active: true
        └── timestamp: 1725811500000
```
