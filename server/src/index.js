require('dotenv').config();
const http = require('http');
const { WebSocketServer } = require('ws');
const { handleMessage, unregisterClient, setupKeepAlive } = require('./signaling');
const { initFirebase, publishServerUrl, listenCallRequests } = require('./fcm');
const { startQuickTunnel, stopQuickTunnel } = require('./tunnel');
const { getIceServers } = require('./turn-credentials');

const PORT = parseInt(process.env.PORT || '8080', 10);
const FAMILY_ID = process.env.FAMILY_ID || 'demo_family';

// 1. Create HTTP server for health checks & ICE server endpoints
const server = http.createServer(async (req, res) => {
  // Enable CORS
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  if (req.url === '/health') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      status: 'ok',
      service: 'walktalk-signaling',
      uptime: process.uptime(),
      timestamp: Date.now()
    }));
    return;
  }

  if (req.url === '/ice-servers') {
    const iceServers = await getIceServers();
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ iceServers }));
    return;
  }

  res.writeHead(404);
  res.end('Not Found');
});

// 2. Initialize WebSocket Server
const wss = new WebSocketServer({ server });

wss.on('connection', (ws, req) => {
  const clientIp = req.socket.remoteAddress;
  console.log(`[WebSocket] New client connected from ${clientIp}`);

  ws.on('message', (data) => {
    handleMessage(ws, data);
  });

  ws.on('close', () => {
    unregisterClient(ws);
  });

  ws.on('error', (err) => {
    console.error(`[WebSocket] Client error: ${err.message}`);
    unregisterClient(ws);
  });

  ws.on('pong', () => {
    ws.isAlive = true;
  });
});

// 3. Keepalive to avoid Cloudflare 100-second idle disconnect
setupKeepAlive(wss);

// 4. Start Server
server.listen(PORT, '0.0.0.0', () => {
  console.log('====================================================');
  console.log(`WalkTalk Signaling Server running on port ${PORT}`);
  console.log(`Local LAN endpoint: ws://0.0.0.0:${PORT}`);
  console.log(`Health check: http://localhost:${PORT}/health`);
  console.log('====================================================');

  // Initialize Firebase Admin SDK & RTDB listeners
  initFirebase();
  listenCallRequests(FAMILY_ID);

  // Start Cloudflare Quick Tunnel if enabled
  startQuickTunnel(PORT, (tunnelUrl) => {
    // Automatically publish the tunnel URL to Firebase RTDB so Android clients find it
    publishServerUrl(FAMILY_ID, tunnelUrl);
  });
});

// 5. Graceful shutdown
function shutdown() {
  console.log('[Server] Shutting down gracefully...');
  stopQuickTunnel();
  wss.close(() => {
    server.close(() => {
      console.log('[Server] Terminated.');
      process.exit(0);
    });
  });
}

process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);
