const admin = require('firebase-admin');
const fs = require('fs');
const path = require('path');

let initialized = false;
let db = null;
let messaging = null;

function initFirebase() {
  if (initialized) return { db, messaging };

  const serviceAccountPath = process.env.FIREBASE_SERVICE_ACCOUNT_PATH || './serviceAccountKey.json';
  const resolvedPath = path.resolve(process.cwd(), serviceAccountPath);
  const databaseUrl = process.env.FIREBASE_DATABASE_URL;

  if (!fs.existsSync(resolvedPath)) {
    console.warn(`[Firebase] Service account file not found at ${resolvedPath}. FCM & RTDB bridge will run in simulated mode.`);
    return { db: null, messaging: null };
  }

  try {
    const serviceAccount = require(resolvedPath);
    admin.initializeApp({
      credential: admin.credential.cert(serviceAccount),
      databaseURL: databaseUrl
    });

    db = admin.database();
    messaging = admin.messaging();
    initialized = true;
    console.log('[Firebase] Admin SDK initialized successfully');
  } catch (err) {
    console.error('[Firebase] Failed to initialize Admin SDK:', err.message);
  }

  return { db, messaging };
}

/**
 * Send an FCM high-priority data message to wake an Android device
 * @param {string} targetToken FCM Device Token
 * @param {object} payload Key-value pairs (must be strings)
 */
async function sendWakeCallPush(targetToken, payload) {
  const { messaging } = initFirebase();
  if (!messaging) {
    console.warn('[FCM] Push skipped: Firebase messaging not initialized');
    return false;
  }

  // Convert all payload values to strings for FCM data payload
  const stringifiedData = {};
  for (const [key, value] of Object.entries(payload)) {
    stringifiedData[key] = typeof value === 'string' ? value : JSON.stringify(value);
  }

  const message = {
    token: targetToken,
    data: stringifiedData,
    android: {
      priority: 'high',
      ttl: 60 * 1000 // 60 seconds
    }
  };

  try {
    const response = await messaging.send(message);
    console.log(`[FCM] High-priority wake message sent successfully: ${response}`);
    return true;
  } catch (error) {
    console.error('[FCM] Error sending wake message:', error);
    return false;
  }
}

/**
 * Publish the active tunnel or local server URL to Firebase RTDB
 */
async function publishServerUrl(familyId, url) {
  const { db } = initFirebase();
  if (!db) return;

  try {
    const ref = db.ref(`families/${familyId}/server_url`);
    await ref.set({
      url: url,
      updatedAt: admin.database.ServerValue.TIMESTAMP
    });
    console.log(`[Firebase] Published server URL to RTDB: ${url}`);
  } catch (err) {
    console.error('[Firebase] Failed to publish server URL to RTDB:', err.message);
  }
}

/**
 * Listen for call requests in RTDB and dispatch FCM wakeups
 */
function listenCallRequests(familyId) {
  const { db } = initFirebase();
  if (!db) return;

  const ref = db.ref(`families/${familyId}/call_requests`);
  console.log(`[Firebase] Listening for call_requests on families/${familyId}/call_requests`);

  ref.on('child_added', async (snapshot) => {
    const callRequest = snapshot.val();
    if (!callRequest || callRequest.processed) return;

    console.log(`[Firebase] New call request received: ${snapshot.key}`, callRequest);

    // Mark as processed to prevent duplicate dispatches
    await snapshot.ref.update({ processed: true, processedAt: Date.now() });

    // Look up target member's FCM token
    const memberSnapshot = await db.ref(`families/${familyId}/members/${callRequest.targetId}`).once('value');
    const member = memberSnapshot.val();

    if (member && member.fcmToken) {
      await sendWakeCallPush(member.fcmToken, {
        type: 'incoming_call',
        call_id: snapshot.key,
        caller_id: callRequest.callerId,
        caller_name: callRequest.callerName || 'Family Member',
        timestamp: String(callRequest.timestamp || Date.now())
      });
    } else {
      console.warn(`[Firebase] No FCM token found for target member: ${callRequest.targetId}`);
    }
  });
}

module.exports = {
  initFirebase,
  sendWakeCallPush,
  publishServerUrl,
  listenCallRequests
};
