package com.walktalk.data.signaling

import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription

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

interface SignalingChannel {
    fun connect()
    fun disconnect()
    fun isConnected(): Boolean
    fun sendCallRequest(targetId: String, callId: String, callerName: String, mode: String = "ptt")
    fun sendSdpOffer(targetId: String, callId: String, sdp: SessionDescription)
    fun sendSdpAnswer(targetId: String, callId: String, sdp: SessionDescription)
    fun sendIceCandidate(targetId: String, callId: String, candidate: IceCandidate)
    fun sendFloorControl(targetId: String, action: String)
    fun sendEndCall(targetId: String, callId: String)
}
