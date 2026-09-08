# 🎙️ WalkTalk — Family Intercom & Command Center

WalkTalk is a private, lightning-fast family intercom and location companion for Android devices.

- **True Intercom (Push-to-Talk)**: Hold to talk, release to listen. Audio plays automatically through the recipient's speakerphone with sub-second latency.
- **Adaptive Family GPS**: Battery-efficient location state machine (< 1% battery/hour when stationary) with geofencing and instant SOS alerts that bypass Do Not Disturb.
- **Hybrid 24/7 Server**: Seamlessly routes over local home Wi-Fi via mDNS (< 80ms) and automatically traverses WAN/CGNAT using a free Cloudflare Quick Tunnel and Cloudflare Realtime TURN relay (1 TB/month free).

---

## 📱 Download the APK
Pre-built APKs are generated automatically on every release.
Head to the **[Releases](https://github.com/abhiraj444/walktalk/releases)** section to download `WalkTalk-release.apk`.

---

## 🏗️ Architecture

```
[Android Phone (App / Widget)]
         │
         ├─── (Home Wi-Fi) ───► Local PC Server (Node.js WebSocket :8080) ──► Direct LAN P2P WebRTC
         │
         └─── (Mobile 4G/5G) ──► Cloudflare Quick Tunnel ──► Local PC Server ──► Cloudflare TURN Relay
                                        │
                                        ▼
                               Firebase RTDB & FCM (Wake Locked Phones)
```

---

## 🚀 Running the Local Server (Windows 24/7)

1. Open PowerShell in `server/`:
   ```powershell
   cd server
   npm install
   npm start
   ```

2. To run 24/7 as an auto-starting Windows Service:
   ```powershell
   cd server/scripts
   # Run in PowerShell as Administrator:
   .\install-services.ps1
   ```

---

## ⚙️ Firebase & Cloudflare Configuration
See the step-by-step guides:
- [Firebase Setup Guide (Free Spark Tier)](server/scripts/setup-firebase.md)
- [Cloudflare TURN Setup Guide (1 TB Free/mo)](server/scripts/setup-cloudflare-turn.md)
