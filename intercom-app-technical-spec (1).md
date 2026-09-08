# Family Intercom App — Technical Specification

## 1. Goal
One-tap, walkie-talkie-style calling between a fixed set of family devices: widget → ring → accept → talk. Works on local wifi first and extends over the internet, with an optional video upgrade mid-call. Priority: minimal user-facing complexity, and **no dependency on any home device (phone or PC) accepting inbound connections** — the household is behind CGNAT (no public IP), so nothing in the design can rely on port-forwarding or exposing a home device to the internet.

## 2. Architecture Summary
Three pieces, all cloud-managed — nothing self-hosted is required for the system to function:
1. **Android client app** — on each family member's phone.
2. **Firebase (Realtime Database + Cloud Messaging)** — handles call signaling, presence, and waking/ringing locked phones. Fully managed by Google; every device only makes *outbound* connections to it, so CGNAT is a non-issue.
3. **WebRTC + a managed TURN provider** — peer-to-peer audio/video, with a cloud TURN relay as fallback for the minority of calls where direct P2P fails.

Audio/video never passes through Firebase — it flows directly device-to-device (or via TURN relay as fallback), so the Firebase usage stays trivial (small free tier is enough for a 4-device family group).

```
[Widget tap] → App → Firebase Realtime Database (call_request node) → Cloud Function/FCM trigger → Target phone wakes
                                                                                    |
                                                            Full-screen incoming-call UI shown
                                                                                    |
                                                                          User accepts
                                                                                    |
                SDP offer/answer + ICE candidates exchanged via Realtime Database listeners
                                                                                    |
                                      Direct WebRTC media connection (P2P, audio+video)
                                                                                    |
                                    (falls back to managed TURN relay only if P2P fails)
```

## 3. Why This Design (No Self-Hosted Server, No Port-Forwarding)
- **CGNAT rules out any self-hosted server reachable from outside.** Your ISP doesn't hand out a public IP, so port-forwarding to a home PC (or a phone) for inbound calls simply isn't available. Any design that needs the PC or a phone to accept inbound traffic is a dead end here.
- **Firebase avoids the problem by design, not by tunneling around it.** Realtime Database and FCM are Google-hosted; every device — including the "target" phone being rung — only ever makes outbound connections to Google's infrastructure. There's nothing on your network that needs to be reachable.
- **Cloudflare Tunnel was solving the right problem in the wrong place.** It's built for exposing a *server you host*, which made sense when the plan was "phones/PC act as servers." Once nothing on your side needs to be a server, the tunnel isn't needed at all.
- **A locked phone still needs FCM to be woken/rung** — this remains true regardless of NAT situation; it's simply how Android app-wake works, not a CGNAT workaround.
- **WebRTC's ICE negotiation** still automatically prefers the local-network path when two phones share wifi, and falls back to a relay otherwise — same as before, no special-casing needed for "local vs. remote."

## 4. Components

### 4.1 Signaling & Presence (Firebase Realtime Database)
Purpose: call setup message exchange + online/offline status — replaces the need for any custom server entirely.
- **Structure**: a Realtime Database tree per family group, e.g. `/families/{familyId}/calls/{callId}` for the offer/answer/ICE exchange, and `/families/{familyId}/presence/{deviceId}` for online status.
- **Presence**: use RTDB's built-in `onDisconnect()` handler — it automatically flips a device to "offline" the moment its connection drops, no polling or heartbeat logic needed. Drives the widget's online/offline dot directly.
- **Auth**: Firebase Authentication with a simple setup (e.g. anonymous auth + a shared family invite code to join the right family group) — enough for a small closed family app, while still keeping the database rules scoped so only family members can read/write their group's data.
- **Cost**: free tier comfortably covers a 4-device family group; this only carries small JSON messages, never media.

### 4.2 Push / Ring (FCM)
- On a new `call_request` write, a small **Firebase Cloud Function** (triggered by the database write) sends an FCM **high-priority data message** to the target device's token, carrying `call_id`, caller name, and the video flag. (This Cloud Function is the only "server-side code," and it's serverless/managed too — no machine to keep running.)
- A data message can wake the app even if it's been killed/backgrounded, and lets the app draw a custom UI — a notification-only message can't do this.
- The app immediately shows a full-screen incoming-call UI over the lock screen:
  - **Android 12+**: use a `CallStyle` notification with `ConnectionService`/`TelecomManager` integration for the native phone-call look and lock-screen behavior.
  - **Pre-12 fallback**: a full-screen intent notification using the `USE_FULL_SCREEN_INTENT` permission.
- Manifest permissions needed: `USE_FULL_SCREEN_INTENT`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_PHONE_CALL` (Android 14+ service-type category), `POST_NOTIFICATIONS` (Android 13+), `RECORD_AUDIO`, `CAMERA` (for video), `ACCESS_NETWORK_STATE`.

### 4.3 WebRTC (Media)
- **Library**: Google's official `org.webrtc` Android SDK (or a wrapper like GetStream/LiveKit for less boilerplate — raw WebRTC keeps the setup closest to "no ongoing service cost").
- **STUN**: Google's public STUN servers are sufficient for public-IP discovery (`stun:stun.l.google.com:19302`).
- **TURN — use a managed provider, not self-hosted.** Because of CGNAT, self-hosting `coturn` at home isn't viable without real added complexity (tunneling raw UDP media reliably needs Cloudflare's Zero Trust/WARP setup, which is a lot of overhead for a fallback path). Use a free-tier managed TURN provider instead (e.g. Metered.ca's free tier) — it already has a public IP and exists specifically for this job. Most calls (same wifi, or typical home NAT on the other end) will connect P2P and never touch TURN at all; this only matters for the minority that can't.
- On the same wifi network, ICE finds a local host candidate and connects directly — lowest latency, true "intercom" feel, no data leaving the LAN.
- **Video**: add a video track to the same `PeerConnection`; capture via `CameraX` or WebRTC's `Camera2Enumerator`. Make video an in-call upgrade toggle rather than default-on, keeping the core experience audio-first and walkie-talkie-like.

### 4.4 Android Widget
- Implement as an `AppWidgetProvider` — one configurable widget instance per contact.
- Tap → `PendingIntent` → writes a `call_request` to the Realtime Database directly (or via a lightweight foreground service). No need to open the full app UI for the caller — keep it a true one-tap action.
- Widget shows the contact's name/photo plus an online/offline dot, driven by the RTDB presence node, so the user isn't tapping into a void.

### 4.5 Contact / Device Setup (kept simple for family members)
- First install: app signs in (anonymous Firebase Auth), joins the family group via a shared invite code, registers device ID + FCM token under that group.
- Adding a family member as a contact: show a QR code containing just the device ID — scanning it adds them as a contact within the shared family group. No IP addresses, no domains, nothing meaningful to the user beyond "who is this."
- No manual IP entry, no domain sharing, no reconnect logic exposed to the user at all — none of that complexity exists in this design in the first place.

## 5. Call Flow (Step by Step)
1. Mother taps the "Papa" widget.
2. App writes a `call_request` node to Firebase Realtime Database.
3. A Cloud Function trigger sends an FCM high-priority data message to Papa's device.
4. Papa's phone (even locked) shows a full-screen incoming-call UI.
5. Papa accepts → app writes `call_response: accepted` → SDP offer/answer and ICE candidates are exchanged via RTDB listeners.
6. WebRTC establishes a direct P2P audio (and optionally video) connection between the two phones — falling back to the managed TURN relay only if direct connection fails.
7. Either party ends the call → the call node is cleared/marked ended, connection torn down.

## 6. Resilience
- Firebase RTDB clients handle reconnect/backoff automatically — no custom logic needed.
- Because signaling is fully cloud-hosted, there's no "server going down" scenario to design around on the home-network side at all. Your PC being on or off never affects whether calls can be placed.

## 7. Suggested Build Order (Phased, to Manage Complexity)
1. **Phase 1**: Two devices, same wifi, Firebase project set up, basic WebRTC audio call triggered by an in-app button (not the widget yet). Prove the core call flow end to end.
2. **Phase 2**: Add the Cloud Function + FCM wake and the full-screen incoming-call UI; test across networks (wifi vs. mobile data, both without any port-forwarding).
3. **Phase 3**: Add the home-screen widget and the QR-code contact-pairing flow.
4. **Phase 4**: Add the video-calling toggle.
5. **Phase 5**: Add the managed TURN provider for the calls that need relay fallback; polish presence indicators, call history, etc.

## 8. Open Decisions to Make
- Which managed TURN provider to use for the fallback relay (Metered.ca free tier is a reasonable starting point; revisit if usage grows past the free limit).
- Auth model detail: anonymous Firebase Auth + invite code is simplest; upgrade to phone-number auth later if you want stronger identity per family member.
- Whether to keep the PC in the picture at all — under this design it isn't required for any part of the call path, so it's free to be used for anything else you'd like.
