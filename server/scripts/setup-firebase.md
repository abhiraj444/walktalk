# WalkTalk — Firebase Setup Guide (Free Spark Tier)

WalkTalk uses Firebase for:
1. **Presence & Fallback Signaling** (Realtime Database)
2. **Device Waking / Push Notifications** (Firebase Cloud Messaging)
3. **Location State Synchronization** (Realtime Database)

All of these operate 100% within the permanent **Free Spark Tier** (no credit card required).

---

## Step 1: Create a Firebase Project
1. Open the [Firebase Console](https://console.firebase.google.com/).
2. Click **Add project** (or Create a project).
3. Name your project (e.g. `walktalk-intercom`).
4. Google Analytics can be disabled (optional).
5. Click **Create project**.

---

## Step 2: Enable Firebase Realtime Database (RTDB)
1. In the left navigation menu, go to **Build** → **Realtime Database**.
2. Click **Create Database**.
3. Choose your nearest database location (e.g., `United States` or `Singapore`).
4. Start in **Test mode** (or Locked mode, then paste the rules from `firebase/database.rules.json`).
5. Copy your database URL:
   `https://<your-project-id>-default-rtdb.firebaseio.com/`
6. Paste this URL into `server/.env` under `FIREBASE_DATABASE_URL`.

---

## Step 3: Enable Anonymous Authentication
1. Go to **Build** → **Authentication**.
2. Click **Get started**.
3. Under the **Sign-in method** tab, click **Anonymous**.
4. Toggle **Enable** to ON and click **Save**.

---

## Step 4: Generate Service Account Key for the Local PC Server
1. Click the ⚙️ (Gear icon) next to **Project Overview** → **Project settings**.
2. Click the **Service accounts** tab.
3. Select **Firebase Admin SDK** (Node.js option).
4. Click **Generate new private key**, then confirm **Generate key**.
5. Save the downloaded `.json` file inside the `walktalk/server` directory as:
   `serviceAccountKey.json`
   *(Ensure this file is kept private and never committed to public repositories).*

---

## Step 5: Add Android App & Download `google-services.json`
1. In Project Settings → **General** tab, under "Your apps", click the **Android** icon.
2. Enter Android package name: `com.walktalk`
3. App nickname: `WalkTalk`
4. Click **Register app**.
5. Download `google-services.json`.
6. Place this file in `walktalk/app/google-services.json`.
