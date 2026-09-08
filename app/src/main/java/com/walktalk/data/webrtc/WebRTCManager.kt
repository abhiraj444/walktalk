package com.walktalk.data.webrtc

import android.content.Context
import android.util.Log
import com.walktalk.WalkTalkApp
import com.walktalk.util.SdpUtils
import org.webrtc.*

class WebRTCManager(
    private val context: Context,
    private val listener: WebRtcListener
) {

    interface WebRtcListener {
        fun onLocalSdpCreated(sdp: SessionDescription)
        fun onIceCandidateGenerated(candidate: IceCandidate)
        fun onConnectionEstablished()
        fun onConnectionClosed()
    }

    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null

    var isMicTransmitting: Boolean = false
        private set

    var isHandsFreeLocked: Boolean = false
        private set

    init {
        initializeFactory()
        createAudioTrack()
    }

    private fun initializeFactory() {
        val options = PeerConnectionFactory.Options()
        factory = PeerConnectionFactory.builder()
            .setOptions(options)
            .createPeerConnectionFactory()
    }

    private fun createAudioTrack() {
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        }

        audioSource = factory?.createAudioSource(audioConstraints)
        localAudioTrack = factory?.createAudioTrack("ARDAMSa0", audioSource)?.apply {
            // Default to MUTED for Push-to-Talk (audio only transmits when button held)
            setEnabled(false)
        }
    }

    fun startPeerConnection(iceServers: List<PeerConnection.IceServer>) {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            iceCandidatePoolSize = 2 // Pre-warms STUN bindings
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            certificate = WalkTalkApp.cachedCertificate
        }

        val pcObserver = object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate != null) {
                    listener.onIceCandidateGenerated(candidate)
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d("WebRTCManager", "ICE Connection State changed to: $state")
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED -> listener.onConnectionEstablished()
                    PeerConnection.IceConnectionState.DISCONNECTED,
                    PeerConnection.IceConnectionState.FAILED,
                    PeerConnection.IceConnectionState.CLOSED -> listener.onConnectionClosed()
                    else -> {}
                }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                Log.d("WebRTCManager", "Remote audio track added")
            }
        }

        peerConnection = factory?.createPeerConnection(rtcConfig, pcObserver)

        // Add our local audio track
        localAudioTrack?.let { track ->
            peerConnection?.addTrack(track, listOf("walktalk_media_stream"))
        }
    }

    /**
     * Creates an SDP offer for caller
     */
    fun createOffer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) return
                // Optimize SDP to Opus-only
                val optimizedDesc = SessionDescription(
                    desc.type,
                    SdpUtils.optimizeSdpForVoice(desc.description)
                )
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        listener.onLocalSdpCreated(optimizedDesc)
                    }
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(err: String?) {
                        Log.e("WebRTCManager", "setLocalDescription failed: $err")
                    }
                }, optimizedDesc)
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(err: String?) {
                Log.e("WebRTCManager", "createOffer failed: $err")
            }
            override fun onSetFailure(err: String?) {}
        }, constraints)
    }

    /**
     * Creates an SDP answer for callee
     */
    fun handleRemoteOfferAndAnswer(remoteSdp: SessionDescription) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                val constraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
                }
                peerConnection?.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(desc: SessionDescription?) {
                        if (desc == null) return
                        val optimizedDesc = SessionDescription(
                            desc.type,
                            SdpUtils.optimizeSdpForVoice(desc.description)
                        )
                        peerConnection?.setLocalDescription(object : SdpObserver {
                            override fun onCreateSuccess(p0: SessionDescription?) {}
                            override fun onSetSuccess() {
                                listener.onLocalSdpCreated(optimizedDesc)
                            }
                            override fun onCreateFailure(p0: String?) {}
                            override fun onSetFailure(p0: String?) {}
                        }, optimizedDesc)
                    }

                    override fun onSetSuccess() {}
                    override fun onCreateFailure(err: String?) {}
                    override fun onSetFailure(err: String?) {}
                }, constraints)
            }

            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(err: String?) {
                Log.e("WebRTCManager", "setRemoteDescription failed: $err")
            }
        }, remoteSdp)
    }

    fun handleRemoteAnswer(remoteSdp: SessionDescription) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d("WebRTCManager", "Remote answer set successfully")
            }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(err: String?) {
                Log.e("WebRTCManager", "setRemoteDescription answer failed: $err")
            }
        }, remoteSdp)
    }

    fun addRemoteIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }

    /**
     * Toggle Push-To-Talk microphone state
     */
    fun setPttTransmitting(transmitting: Boolean) {
        if (isHandsFreeLocked) return // In lock mode, mic stays open
        isMicTransmitting = transmitting
        localAudioTrack?.setEnabled(transmitting)
    }

    /**
     * Toggle Hands-Free Full-Duplex Lock
     */
    fun toggleHandsFreeLock() {
        isHandsFreeLocked = !isHandsFreeLocked
        localAudioTrack?.setEnabled(isHandsFreeLocked)
        isMicTransmitting = isHandsFreeLocked
    }

    fun close() {
        try {
            localAudioTrack?.setEnabled(false)
            peerConnection?.close()
            peerConnection = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
