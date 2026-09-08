# Cloudflare Realtime TURN Setup Guide (1 TB / Month Free)

Cloudflare provides **1,000 GB (1 TB)** of free TURN relay bandwidth every month on their free plan without requiring paid add-ons. TURN relay is utilized as an automatic fallback when devices cannot establish a direct P2P connection (e.g. mobile 4G/5G behind symmetric NAT).

---

## Step 1: Sign up or Log in to Cloudflare
1. Go to [Cloudflare Dashboard](https://dash.cloudflare.com/).
2. Sign up for a free account (no credit card required for free features).

---

## Step 2: Generate a TURN Key
1. In the left navigation menu, expand **Calls** (or **Realtime**).
2. Click **TURN Keys**.
3. Click the **Create TURN Key** button.
4. Name your key (e.g. `walktalk-turn-key`).
5. Copy the two generated values:
   - **TURN Key ID** (e.g., `8f7b...`)
   - **API Token** (Bearer token secret)

---

## Step 3: Configure the Local Server `.env`
Open `walktalk/server/.env` and paste your credentials:

```env
CLOUDFLARE_TURN_KEY_ID=your_turn_key_id_here
CLOUDFLARE_TURN_API_TOKEN=your_turn_api_token_here
```

When WalkTalk starts, `server/src/turn-credentials.js` calls Cloudflare's API to mint ephemeral 24-hour tokens and serves them securely to your family's Android devices.
