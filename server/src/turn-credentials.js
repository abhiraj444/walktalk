const fetch = require('node-fetch');

let cachedIceServers = null;
let cacheExpiryTime = 0;

/**
 * Fetch or generate ICE server configurations.
 * If Cloudflare TURN credentials are set in .env, generates ephemeral ICE servers.
 * Otherwise, falls back to Google public STUN servers.
 */
async function getIceServers() {
  const now = Date.now();
  if (cachedIceServers && now < cacheExpiryTime) {
    return cachedIceServers;
  }

  const turnKeyId = process.env.CLOUDFLARE_TURN_KEY_ID;
  const apiToken = process.env.CLOUDFLARE_TURN_API_TOKEN;

  // Fallback if Cloudflare credentials are not provided
  if (!turnKeyId || !apiToken) {
    return [
      {
        urls: [
          'stun:stun.l.google.com:19302',
          'stun:stun1.l.google.com:19302'
        ]
      }
    ];
  }

  try {
    const response = await fetch(
      `https://rtc.live.cloudflare.com/v1/turn/keys/${turnKeyId}/credentials/generate-ice-servers`,
      {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${apiToken}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ ttl: 86400 }) // 24 hours
      }
    );

    if (!response.ok) {
      throw new Error(`Cloudflare TURN API error: ${response.status} ${response.statusText}`);
    }

    const data = await response.json();
    if (data && data.iceServers) {
      cachedIceServers = data.iceServers;
      // Cache for 12 hours to stay well inside the 24-hour TTL
      cacheExpiryTime = now + 12 * 60 * 60 * 1000;
      console.log('[TURN] Successfully refreshed Cloudflare ICE servers');
      return cachedIceServers;
    }
  } catch (error) {
    console.error('[TURN] Failed to generate Cloudflare TURN credentials, falling back to STUN:', error.message);
  }

  return [
    {
      urls: ['stun:stun.l.google.com:19302']
    }
  ];
}

module.exports = {
  getIceServers
};
