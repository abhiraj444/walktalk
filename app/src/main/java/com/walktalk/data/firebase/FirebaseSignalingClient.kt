package com.walktalk.data.firebase

import android.util.Log
import com.google.firebase.database.*
import com.walktalk.data.signaling.SignalingChannel
import com.walktalk.data.signaling.SignalingListener
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription

class FirebaseSignalingClient(
    private val familyId: String,
    private val deviceId: String,
    private val listener: SignalingListener
) : SignalingChannel {

    private val db = FirebaseDatabase.getInstance().reference
    private val callsRef = db.child("families").child(familyId).child("calls")
    private var connectedListener: ValueEventListener? = null
    private var callsListener: ChildEventListener? = null
    private var activeCallListener: ValueEventListener? = null
    private var currentActiveCallId: String? = null
    private var isConnected = false

    private val defaultIceServers: List<PeerConnection.IceServer> by lazy {
        listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun.cloudflare.com:3478").createIceServer()
        )
    }

    override fun connect() {
        val connectedRef = FirebaseDatabase.getInstance().getReference(".info/connected")
        connectedListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected) {
                    isConnected = true
                    Log.d("FirebaseSignaling", "Connected to Firebase RTDB serverless signaling")
                    listener.onConnected()
                    listener.onIceServersReceived(defaultIceServers)
                } else {
                    isConnected = false
                    listener.onDisconnected()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                isConnected = false
                listener.onDisconnected()
            }
        }
        connectedRef.addValueEventListener(connectedListener as ValueEventListener)

        // Listen for incoming calls
        startListeningForCalls()
    }

    private fun startListeningForCalls() {
        callsListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val callId = snapshot.key ?: return
                val targetId = snapshot.child("targetId").getValue(String::class.java)
                val callerId = snapshot.child("callerId").getValue(String::class.java) ?: ""
                val callerName = snapshot.child("callerName").getValue(String::class.java) ?: "Family Member"
                val mode = snapshot.child("mode").getValue(String::class.java) ?: "ptt"
                val status = snapshot.child("status").getValue(String::class.java) ?: "calling"

                // Check if call is for this device or everyone, and not from self
                if ((targetId == deviceId || targetId == "everyone") && callerId != deviceId && status == "calling") {
                    Log.d("FirebaseSignaling", "Incoming call detected via Firebase RTDB: $callId from $callerName")
                    currentActiveCallId = callId
                    attachCallDetailListener(callId)
                    listener.onIncomingCall(callId, callerId, callerName, mode)
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }

        // Only listen to calls created in the last 2 minutes
        val recentQuery = callsRef.orderByChild("timestamp").startAt(System.currentTimeMillis() - 120000.0)
        recentQuery.addChildEventListener(callsListener as ChildEventListener)
    }

    private fun attachCallDetailListener(callId: String) {
        detachCallDetailListener()
        val callRef = callsRef.child(callId)

        activeCallListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.child("status").getValue(String::class.java)
                if (status == "ended") {
                    listener.onCallEnded(callId)
                    detachCallDetailListener()
                    return
                }

                // Check for SDP Offer
                val offerSnapshot = snapshot.child("offer")
                if (offerSnapshot.exists()) {
                    val sdpString = offerSnapshot.child("sdp").getValue(String::class.java)
                    val senderId = offerSnapshot.child("senderId").getValue(String::class.java) ?: ""
                    if (!sdpString.isNullOrEmpty() && senderId != deviceId) {
                        val sdp = SessionDescription(SessionDescription.Type.OFFER, sdpString)
                        listener.onSdpOfferReceived(callId, senderId, sdp)
                    }
                }

                // Check for SDP Answer
                val answerSnapshot = snapshot.child("answer")
                if (answerSnapshot.exists()) {
                    val sdpString = answerSnapshot.child("sdp").getValue(String::class.java)
                    val senderId = answerSnapshot.child("senderId").getValue(String::class.java) ?: ""
                    if (!sdpString.isNullOrEmpty() && senderId != deviceId) {
                        val sdp = SessionDescription(SessionDescription.Type.ANSWER, sdpString)
                        listener.onSdpAnswerReceived(callId, senderId, sdp)
                    }
                }

                // Check for ICE Candidates
                val candidatesSnapshot = snapshot.child("candidates")
                for (senderChild in candidatesSnapshot.children) {
                    val senderId = senderChild.key ?: continue
                    if (senderId != deviceId) {
                        for (candChild in senderChild.children) {
                            val sdpMid = candChild.child("sdpMid").getValue(String::class.java) ?: ""
                            val sdpMLineIndex = candChild.child("sdpMLineIndex").getValue(Int::class.java) ?: 0
                            val sdp = candChild.child("sdp").getValue(String::class.java) ?: ""
                            if (sdp.isNotEmpty()) {
                                listener.onIceCandidateReceived(callId, senderId, IceCandidate(sdpMid, sdpMLineIndex, sdp))
                            }
                        }
                    }
                }

                // Check for PTT Floor Control
                val floorSnapshot = snapshot.child("floor")
                if (floorSnapshot.exists()) {
                    val action = floorSnapshot.child("action").getValue(String::class.java) ?: ""
                    val senderId = floorSnapshot.child("senderId").getValue(String::class.java) ?: ""
                    if (action.isNotEmpty() && senderId != deviceId) {
                        listener.onFloorControl(action, senderId)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        callRef.addValueEventListener(activeCallListener as ValueEventListener)
    }

    private fun detachCallDetailListener() {
        activeCallListener?.let {
            currentActiveCallId?.let { callId ->
                callsRef.child(callId).removeEventListener(it)
            }
            activeCallListener = null
        }
    }

    override fun disconnect() {
        detachCallDetailListener()
        connectedListener?.let {
            FirebaseDatabase.getInstance().getReference(".info/connected").removeEventListener(it)
        }
        callsListener?.let {
            callsRef.removeEventListener(it)
        }
        isConnected = false
    }

    override fun isConnected(): Boolean = isConnected

    override fun sendCallRequest(targetId: String, callId: String, callerName: String, mode: String) {
        currentActiveCallId = callId
        attachCallDetailListener(callId)

        val callData = mapOf(
            "callId" to callId,
            "callerId" to deviceId,
            "callerName" to callerName,
            "targetId" to targetId,
            "mode" to mode,
            "status" to "calling",
            "timestamp" to ServerValue.TIMESTAMP
        )
        callsRef.child(callId).setValue(callData)
    }

    override fun sendSdpOffer(targetId: String, callId: String, sdp: SessionDescription) {
        val offerData = mapOf(
            "senderId" to deviceId,
            "sdp" to sdp.description,
            "type" to "offer",
            "timestamp" to ServerValue.TIMESTAMP
        )
        callsRef.child(callId).child("offer").setValue(offerData)
    }

    override fun sendSdpAnswer(targetId: String, callId: String, sdp: SessionDescription) {
        val answerData = mapOf(
            "senderId" to deviceId,
            "sdp" to sdp.description,
            "type" to "answer",
            "timestamp" to ServerValue.TIMESTAMP
        )
        callsRef.child(callId).child("answer").setValue(answerData)
    }

    override fun sendIceCandidate(targetId: String, callId: String, candidate: IceCandidate) {
        val candidateData = mapOf(
            "sdpMid" to candidate.sdpMid,
            "sdpMLineIndex" to candidate.sdpMLineIndex,
            "sdp" to candidate.sdp
        )
        callsRef.child(callId).child("candidates").child(deviceId).push().setValue(candidateData)
    }

    override fun sendFloorControl(targetId: String, action: String) {
        val floorData = mapOf(
            "action" to action,
            "senderId" to deviceId,
            "timestamp" to ServerValue.TIMESTAMP
        )
        currentActiveCallId?.let { callId ->
            callsRef.child(callId).child("floor").setValue(floorData)
        }
    }

    override fun sendEndCall(targetId: String, callId: String) {
        callsRef.child(callId).child("status").setValue("ended")
        detachCallDetailListener()
        currentActiveCallId = null
    }
}
