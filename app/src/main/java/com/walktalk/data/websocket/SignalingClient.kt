package com.walktalk.data.websocket

import android.util.Log
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import java.net.URI

class SignalingClient(
    private var serverUri: URI,
    private val familyId: String,
    private val deviceId: String,
    private val listener: SignalingListener
) {

    interface SignalingListener {
        fun onConnected()
        fun onDisconnected()
        fun onIceServersReceived(iceServers: List<PeerConnection.IceServer>)
        fun onIncomingCall(callId: String, callerId: String, callerName: String, mode: String)
        fun onSdpOfferReceived(callId: String, senderId: String, sdp: SessionDescription)
        fun onSdpAnswerReceived(callId: String, senderId: String, sdp: SessionDescription)
        fun onIceCandidateReceived(callId: String, senderId: String, candidate: IceCandidate)
        fun onFloorControl(action: String, senderId: String)
        fun onCallEnded(callId: String)
    }

    private var client: WebSocketClient? = null
    private var isIntentionalClose = false

    fun connect() {
        isIntentionalClose = false
        try {
            client = object : WebSocketClient(serverUri) {
                override fun onOpen(handshakedata: ServerHandshake?) {
                    Log.d("SignalingClient", "Connected to $serverUri")
                    // Register with family
                    val joinMsg = JSONObject().apply {
                        put("type", "join")
                        put("familyId", familyId)
                        put("deviceId", deviceId)
                    }
                    send(joinMsg.toString())
                    listener.onConnected()
                }

                override fun onMessage(message: String?) {
                    if (message == null) return
                    handleIncomingMessage(message)
                }

                override fun onClose(code: Int, reason: String?, remote: Boolean) {
                    Log.w("SignalingClient", "Connection closed: code=$code, reason=$reason, remote=$remote")
                    listener.onDisconnected()
                }

                override fun onError(ex: Exception?) {
                    Log.e("SignalingClient", "WebSocket error: ${ex?.message}")
                }
            }
            client?.connect()
        } catch (e: Exception) {
            Log.e("SignalingClient", "Failed to connect WebSocket: ${e.message}")
            listener.onDisconnected()
        }
    }

    fun updateServerUri(newUri: URI) {
        this.serverUri = newUri
        disconnect()
        connect()
    }

    fun disconnect() {
        isIntentionalClose = true
        client?.close()
        client = null
    }

    fun isConnected(): Boolean = client?.isOpen == true

    fun sendCallRequest(targetId: String, callId: String, callerName: String, mode: String = "ptt") {
        val json = JSONObject().apply {
            put("type", "call_request")
            put("familyId", familyId)
            put("targetId", targetId)
            put("callId", callId)
            put("callerName", callerName)
            put("mode", mode)
        }
        send(json)
    }

    fun sendSdpOffer(targetId: String, callId: String, sdp: SessionDescription) {
        val json = JSONObject().apply {
            put("type", "sdp_offer")
            put("familyId", familyId)
            put("targetId", targetId)
            put("callId", callId)
            put("sdp", sdp.description)
        }
        send(json)
    }

    fun sendSdpAnswer(targetId: String, callId: String, sdp: SessionDescription) {
        val json = JSONObject().apply {
            put("type", "sdp_answer")
            put("familyId", familyId)
            put("targetId", targetId)
            put("callId", callId)
            put("sdp", sdp.description)
        }
        send(json)
    }

    fun sendIceCandidate(targetId: String, callId: String, candidate: IceCandidate) {
        val candidateJson = JSONObject().apply {
            put("sdpMid", candidate.sdpMid)
            put("sdpMLineIndex", candidate.sdpMLineIndex)
            put("sdp", candidate.sdp)
        }
        val json = JSONObject().apply {
            put("type", "ice_candidate")
            put("familyId", familyId)
            put("targetId", targetId)
            put("callId", callId)
            put("candidate", candidateJson)
        }
        send(json)
    }

    fun sendFloorControl(targetId: String, action: String) { // "take" or "release"
        val json = JSONObject().apply {
            put("type", "floor_control")
            put("familyId", familyId)
            put("targetId", targetId)
            put("action", action)
        }
        send(json)
    }

    fun sendEndCall(targetId: String, callId: String) {
        val json = JSONObject().apply {
            put("type", "end_call")
            put("familyId", familyId)
            put("targetId", targetId)
            put("callId", callId)
        }
        send(json)
    }

    private fun send(json: JSONObject) {
        if (client?.isOpen == true) {
            client?.send(json.toString())
        }
    }

    private fun handleIncomingMessage(raw: String) {
        try {
            val json = JSONObject(raw)
            when (json.optString("type")) {
                "ice_servers" -> {
                    val iceList = mutableListOf<PeerConnection.IceServer>()
                    val serversArray = json.optJSONArray("iceServers") ?: JSONArray()
                    for (i in 0 until serversArray.length()) {
                        val serverObj = serversArray.getJSONObject(i)
                        val urlsArray = serverObj.optJSONArray("urls") ?: JSONArray()
                        val urls = mutableListOf<String>()
                        for (u in 0 until urlsArray.length()) {
                            urls.add(urlsArray.getString(u))
                        }
                        val username = serverObj.optString("username", null)
                        val credential = serverObj.optString("credential", null)

                        val builder = PeerConnection.IceServer.builder(urls)
                        if (username != null && credential != null) {
                            builder.setUsername(username)
                            builder.setPassword(credential)
                        }
                        iceList.add(builder.createIceServer())
                    }
                    listener.onIceServersReceived(iceList)
                }

                "incoming_call" -> {
                    listener.onIncomingCall(
                        callId = json.getString("callId"),
                        callerId = json.getString("callerId"),
                        callerName = json.optString("callerName", "Family Member"),
                        mode = json.optString("mode", "ptt")
                    )
                }

                "sdp_offer" -> {
                    val sdp = SessionDescription(SessionDescription.Type.OFFER, json.getString("sdp"))
                    listener.onSdpOfferReceived(
                        callId = json.getString("callId"),
                        senderId = json.getString("senderId"),
                        sdp = sdp
                    )
                }

                "sdp_answer" -> {
                    val sdp = SessionDescription(SessionDescription.Type.ANSWER, json.getString("sdp"))
                    listener.onSdpAnswerReceived(
                        callId = json.getString("callId"),
                        senderId = json.getString("senderId"),
                        sdp = sdp
                    )
                }

                "ice_candidate" -> {
                    val cObj = json.getJSONObject("candidate")
                    val candidate = IceCandidate(
                        cObj.getString("sdpMid"),
                        cObj.getInt("sdpMLineIndex"),
                        cObj.getString("sdp")
                    )
                    listener.onIceCandidateReceived(
                        callId = json.getString("callId"),
                        senderId = json.getString("senderId"),
                        candidate = candidate
                    )
                }

                "floor_control" -> {
                    listener.onFloorControl(
                        action = json.getString("action"),
                        senderId = json.getString("senderId")
                    )
                }

                "end_call" -> {
                    listener.onCallEnded(json.getString("callId"))
                }
            }
        } catch (e: Exception) {
            Log.e("SignalingClient", "Failed to parse incoming message: ${e.message}")
        }
    }
}
