const { getIceServers } = require('./turn-credentials');
const { sendWakeCallPush } = require('./fcm');

// Map of familyId -> Map of deviceId -> WebSocket connection
const families = new Map();

function registerClient(familyId, deviceId, ws, metadata = {}) {
  if (!families.has(familyId)) {
    families.set(familyId, new Map());
  }
  const familyGroup = families.get(familyId);
  ws.deviceId = deviceId;
  ws.familyId = familyId;
  ws.metadata = metadata;
  ws.isAlive = true;

  familyGroup.set(deviceId, ws);
  console.log(`[Signaling] Device ${deviceId} joined family ${familyId} (Total active: ${familyGroup.size})`);

  // Broadcast presence update to family members
  broadcastToFamily(familyId, {
    type: 'presence_update',
    deviceId,
    status: 'available',
    timestamp: Date.now()
  }, deviceId);
}

function unregisterClient(ws) {
  const { familyId, deviceId } = ws;
  if (!familyId || !deviceId) return;

  const familyGroup = families.get(familyId);
  if (familyGroup && familyGroup.has(deviceId)) {
    familyGroup.delete(deviceId);
    console.log(`[Signaling] Device ${deviceId} left family ${familyId} (Remaining: ${familyGroup.size})`);

    broadcastToFamily(familyId, {
      type: 'presence_update',
      deviceId,
      status: 'offline',
      timestamp: Date.now()
    }, deviceId);

    if (familyGroup.size === 0) {
      families.delete(familyId);
    }
  }
}

function broadcastToFamily(familyId, message, excludeDeviceId = null) {
  const familyGroup = families.get(familyId);
  if (!familyGroup) return;

  const payload = JSON.stringify(message);
  for (const [id, socket] of familyGroup.entries()) {
    if (id !== excludeDeviceId && socket.readyState === 1) { // 1 = OPEN
      socket.send(payload);
    }
  }
}

function sendToDevice(familyId, targetDeviceId, message) {
  const familyGroup = families.get(familyId);
  if (!familyGroup) return false;

  const targetSocket = familyGroup.get(targetDeviceId);
  if (targetSocket && targetSocket.readyState === 1) {
    targetSocket.send(JSON.stringify(message));
    return true;
  }
  return false;
}

/**
 * Handle incoming WebSocket messages from Android clients
 */
async function handleMessage(ws, data) {
  let message;
  try {
    message = JSON.parse(data);
  } catch (err) {
    console.error('[Signaling] Failed to parse message:', err);
    return;
  }

  const { type, familyId, targetId } = message;

  switch (type) {
    case 'join': {
      registerClient(message.familyId, message.deviceId, ws, message.metadata);
      // Immediately send ICE servers configuration to the client
      const iceServers = await getIceServers();
      ws.send(JSON.stringify({
        type: 'ice_servers',
        iceServers
      }));
      break;
    }

    case 'get_ice_servers': {
      const iceServers = await getIceServers();
      ws.send(JSON.stringify({
        type: 'ice_servers',
        iceServers
      }));
      break;
    }

    case 'call_request': {
      // Direct call setup
      const isOnlineLocally = sendToDevice(familyId, targetId, {
        type: 'incoming_call',
        callId: message.callId,
        callerId: ws.deviceId,
        callerName: message.callerName || 'Family Member',
        mode: message.mode || 'ptt', // 'ptt' or 'duplex'
        timestamp: Date.now()
      });

      if (!isOnlineLocally) {
        console.log(`[Signaling] Target ${targetId} not active on WebSocket, triggering wake push...`);
        if (message.targetFcmToken) {
          await sendWakeCallPush(message.targetFcmToken, {
            type: 'incoming_call',
            call_id: message.callId,
            caller_id: ws.deviceId,
            caller_name: message.callerName || 'Family Member',
            timestamp: String(Date.now())
          });
        }
      }
      break;
    }

    case 'sdp_offer': {
      // Forward SDP offer to callee
      sendToDevice(familyId, targetId, {
        type: 'sdp_offer',
        callId: message.callId,
        senderId: ws.deviceId,
        sdp: message.sdp
      });
      break;
    }

    case 'sdp_answer': {
      // Forward SDP answer to caller
      sendToDevice(familyId, targetId, {
        type: 'sdp_answer',
        callId: message.callId,
        senderId: ws.deviceId,
        sdp: message.sdp
      });
      break;
    }

    case 'ice_candidate': {
      // Trickle ICE forwarding
      sendToDevice(familyId, targetId, {
        type: 'ice_candidate',
        callId: message.callId,
        senderId: ws.deviceId,
        candidate: message.candidate
      });
      break;
    }

    case 'floor_control': {
      // PTT Floor events: 'take' or 'release'
      sendToDevice(familyId, targetId, {
        type: 'floor_control',
        action: message.action, // 'take' or 'release'
        senderId: ws.deviceId
      });
      break;
    }

    case 'broadcast_all': {
      // Intercom blast to all family members ("Everyone" mode)
      broadcastToFamily(familyId, {
        type: 'broadcast_all',
        callId: message.callId,
        senderId: ws.deviceId,
        senderName: message.senderName,
        action: message.action // 'start' or 'stop'
      }, ws.deviceId);
      break;
    }

    case 'end_call': {
      sendToDevice(familyId, targetId, {
        type: 'end_call',
        callId: message.callId,
        senderId: ws.deviceId
      });
      break;
    }

    case 'pong': {
      ws.isAlive = true;
      break;
    }

    default:
      console.warn(`[Signaling] Unhandled message type: ${type}`);
  }
}

/**
 * Configure keepalive interval to prevent Cloudflare 100-second idle disconnect
 */
function setupKeepAlive(wss) {
  const interval = setInterval(() => {
    wss.clients.forEach((ws) => {
      if (ws.isAlive === false) {
        console.log(`[KeepAlive] Terminating unresponsive client: ${ws.deviceId || 'unknown'}`);
        return ws.terminate();
      }
      ws.isAlive = false;
      ws.ping();
    });
  }, 40000); // Ping every 40 seconds

  wss.on('close', () => {
    clearInterval(interval);
  });
}

module.exports = {
  handleMessage,
  unregisterClient,
  setupKeepAlive
};
