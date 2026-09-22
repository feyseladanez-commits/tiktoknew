# Creator Donate — floating TikTok overlay + Chapa donations

This project has two parts:
- `backend/` — Node.js/Express API (auth, creator lookup, Chapa donation handling)
- `android/` — the Android app (floating bubble, accessibility service, donate UI)

Nothing here is "click one button and it's live" — a few things need your own
accounts and values filled in before this works end to end. Everything you need
to change is called out below.

## 1. Deploy the backend

1. `cd backend && npm install`
2. Copy `.env.example` to `.env` and fill in:
   - `CHAPA_SECRET_KEY` — from your Chapa merchant dashboard (https://dashboard.chapa.co).
     Use the **test** key first.
   - `CHAPA_CALLBACK_URL` / `CHAPA_RETURN_URL` — must be real HTTPS URLs once deployed.
     For local testing, use `ngrok http 3000` and put the ngrok URL here — Chapa needs
     to reach your server from the internet to send the webhook.
   - `JWT_SECRET` — generate with `node -e "console.log(require('crypto').randomBytes(48).toString('hex'))"`
3. `npm start` (or deploy to any Node host — Render, Railway, a small VPS, etc.)
4. Confirm it's alive: `GET https://your-domain.com/health` should return `{"ok":true}`.

### Backend endpoints
| Method | Path | Purpose |
|---|---|---|
| POST | `/api/auth/signup` | Create a fan or creator account |
| POST | `/api/auth/login` | Log in, get a JWT |
| GET | `/api/creators/:username` | Check if a TikTok username is registered |
| GET | `/api/creators/:username/totals` | Total successful donations received |
| POST | `/api/donations/initiate` | Start a Chapa checkout (auth required) |
| POST | `/api/donations/webhook` | Called by Chapa, not by your app |
| GET | `/api/donations/status/:tx_ref` | App polls this after checkout |

## 2. Configure and build the Android app

1. Open the `android/` folder in Android Studio (Hedgehog or newer).
2. In `app/build.gradle`, change this line to your deployed backend's URL:
   ```
   buildConfigField("String", "API_BASE_URL", "\"https://your-backend-domain.com/\"")
   ```
   Must end with a trailing slash.
3. Change `applicationId` in the same file if you want your own package name
   (recommended before publishing — `com.example.*` is a placeholder).
4. Build and install on a **real device** — overlay and accessibility services
   are unreliable or non-functional on most emulators.

## 2b. Build the APK without Android Studio (GitHub Actions)

1. Create a free GitHub account and a new **private** repository.
2. Upload the *contents* of this folder to it (so `android/`, `backend/` and
   `.github/` sit at the top level). `.gitignore` keeps `.env` out — never upload a real `.env`.
   If your upload skips the hidden `.github` folder, use **Add file → Create new file**,
   type `.github/workflows/build-apk.yml` as the name, and paste the workflow contents.
3. Open the **Actions** tab → **Build APK** → **Run workflow**. Type your deployed
   backend URL (with trailing slash) in the box, then run it.
4. When it goes green (about 5 minutes), open the run and download the
   **creator-donate-debug-apk** artifact. Unzip it and copy the `.apk` to your phone.
5. On the phone, allow "install unknown apps" for your file manager/browser and install it.

### First run on the device
1. Open the app → **Log in / sign up** → create an account (choose fan or creator;
   creators must enter their TikTok username).
2. Tap **1. Enable accessibility service** → find "Creator Donate" in the list → turn it on.
3. Tap **2. Enable floating bubble permission** → grant "display over other apps".
4. Tap **3. Start floating bubble**.
5. Open TikTok — the small bubble icon should appear. Tap it to expand into the
   donate panel.

## 3. The one thing you must fix before this actually works

`TikTokAccessibilityService.kt` currently guesses that TikTok's username text
starts with `"@"`. This is a placeholder, not a confirmed match against TikTok's
real UI — you need to verify it yourself:

1. Install `uiautomatorviewer` (comes with Android SDK command-line tools).
2. Open TikTok on a connected device, run `uiautomatorviewer`, and inspect the
   live view hierarchy while a video is open.
3. Find the actual node (its `resource-id` or `content-desc`) that holds the
   creator's username.
4. Update `findUsername()` in `TikTokAccessibilityService.kt` to match that
   pattern instead of the `"@"` guess.

TikTok updates its app periodically, which can silently break this matching —
budget time to re-check it occasionally, especially after TikTok app updates.

## 4. Before you launch publicly — do not skip these

- **Creator verification is not implemented yet.** Right now, anyone can sign up
  as a creator with any TikTok username, including someone else's. The schema
  has a `tiktok_verified` column ready for this, but you need to add an actual
  verification step (e.g. the creator posts a one-time code in their TikTok bio,
  and your backend checks for it) before letting creators receive real donations
  publicly. Skipping this means fans could unknowingly send money to an impostor.
- **Switch SQLite → PostgreSQL** if you expect concurrent traffic; `better-sqlite3`
  is great for an MVP but is a single-file, single-writer database.
- **Get a real Chapa production key** and test the full flow with small real
  transactions before opening this up to real users.
- **Read Chapa's and Google Play's current policies** before launch — payment
  and accessibility-service policies are the two areas most likely to have
  changed since this was written.
