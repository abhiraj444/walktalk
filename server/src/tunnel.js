const { spawn } = require('child_process');

let tunnelProcess = null;
let currentTunnelUrl = null;

/**
 * Start Cloudflare Quick Tunnel and extract the public trycloudflare.com URL
 * @param {number} localPort The local port to expose (default 8080)
 * @param {Function} onUrlDiscovered Callback when URL is found
 */
function startQuickTunnel(localPort = 8080, onUrlDiscovered) {
  if (process.env.ENABLE_QUICK_TUNNEL !== 'true') {
    console.log('[Tunnel] Quick Tunnel is disabled in .env');
    return;
  }

  const cloudflaredCmd = process.env.CLOUDFLARED_PATH || 'cloudflared';
  console.log(`[Tunnel] Launching Cloudflare Quick Tunnel for port ${localPort}...`);

  try {
    tunnelProcess = spawn(cloudflaredCmd, [
      'tunnel',
      '--url',
      `http://localhost:${localPort}`,
      '--no-autoupdate'
    ]);

    const urlRegex = /https:\/\/[a-zA-Z0-9-]+\.trycloudflare\.com/;

    const handleOutput = (data) => {
      const text = data.toString();
      const match = text.match(urlRegex);
      if (match && match[0]) {
        const foundUrl = match[0];
        if (foundUrl !== currentTunnelUrl) {
          currentTunnelUrl = foundUrl;
          console.log('====================================================');
          console.log(`[Tunnel] Public Cloudflare Tunnel URL established:`);
          console.log(`>>> ${currentTunnelUrl} <<<`);
          console.log('====================================================');
          if (onUrlDiscovered) {
            onUrlDiscovered(currentTunnelUrl);
          }
        }
      }
    };

    tunnelProcess.stdout.on('data', handleOutput);
    tunnelProcess.stderr.on('data', handleOutput);

    tunnelProcess.on('error', (err) => {
      console.error(`[Tunnel] Failed to start cloudflared: ${err.message}. Make sure cloudflared is installed and in PATH.`);
    });

    tunnelProcess.on('close', (code) => {
      console.warn(`[Tunnel] cloudflared process exited with code ${code}.`);
      currentTunnelUrl = null;
    });

  } catch (err) {
    console.error('[Tunnel] Exception starting tunnel:', err.message);
  }
}

function stopQuickTunnel() {
  if (tunnelProcess) {
    console.log('[Tunnel] Terminating cloudflared...');
    tunnelProcess.kill();
    tunnelProcess = null;
    currentTunnelUrl = null;
  }
}

function getTunnelUrl() {
  return currentTunnelUrl;
}

module.exports = {
  startQuickTunnel,
  stopQuickTunnel,
  getTunnelUrl
};
