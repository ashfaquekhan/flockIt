# FlockIt — architecture & data-handling design

This is the target design for FlockIt done "properly" — offline-first, Google-account owned,
shareable. It documents where things live, how sync works, and how sharing/permissions/backups
should behave. Some of this is implemented; the cloud backbone (marked **P6**) is the remaining piece.

## 1. Layers (clean, testable)
```
UI (Compose screens)  →  ViewModel (state, actions)  →  Repository  →  ┬ Room (local cache = source of truth for the UI)
                                                                       └ SyncManager → Google Drive/Sheets (durable remote)
Engine (PhysiologicalEngine) is pure Kotlin, no Android — all the biology math, unit-testable.
```
- **Room is the single source of truth the UI reads.** The app always works offline.
- **Google Sheets/Drive is the durable, shareable remote.** Sync pushes/pulls between the two.

## 2. Identity & authorization (modern stack — **P6**)
Legacy `com.google.android.gms.auth.api.signin.GoogleSignIn` is **deprecated (removed 2025)**. Migrate to:
- **Sign in with Google via Credential Manager** (`androidx.credentials` + `googleid`) — identity only
  (name, email, photo). This is what powers the **Account/profile**.
- **AuthorizationClient** (Google Identity Services) — request the Drive/Sheets scopes *separately, only
  when the user first does something that needs them* (incremental auth). Returns a short‑lived (~1 h)
  **access token**; cache it and re-request on expiry. This token is the `Bearer` for Sheets/Drive REST.
- The current code mis-uses `idToken` as the bearer — that is why signed-in Sheets calls would 401. Fix:
  feed the AuthorizationClient access token into `SheetsSyncManager.getAuthHeader()`.
- **Error 10** at sign-in = the Android OAuth client's SHA-1 ≠ the installed APK's signing SHA-1. The app
  is now signed with a committed keystore: **SHA-1 `0D:4A:B1:61:1B:1A:0E:CF:83:C0:56:0A:71:11:2A:56:E3:B6:E0:DD`**,
  package `com.ashfaque.flockit`. Register exactly that + add yourself as a Test user + enable Sheets & Drive APIs.

## 3. Data model & where each farm lives
- **One farm = one Google Spreadsheet** in the creator's Drive (so it shows up in Google Sheets / Drive).
  Tabs: `_Meta`, `_Farm`, `_Config`, `_FeedTypes`, `Flocks`, `DailyData`, `Tasks` (already created by
  `SheetsSyncManager.createFarmSpreadsheet`).
- **Discovery (fixes "re-login/other device/shared farms don't show") — P6:** tag each spreadsheet with a
  Drive **`appProperties`** marker `{app: "FlockIt"}` at creation. On sign-in, list them with Drive:
  `files.list(q = "appProperties has { key='app' and value='FlockIt' } and trashed=false", spaces=drive)`
  and also rely on **"shared with me"** results — then upsert into the local `farm_registry`. This is what
  makes owned + shared farms reappear after logout/login and on a second device. (Right now farms are only
  in local Room, so nothing appears in Drive and nothing is rediscovered — the reported bug.)
- Local Room mirrors the sheet for offline use, keyed by `spreadsheetId`.

## 4. Sync (offline-first) — **P6**
- **Write:** edit Room immediately (instant UI); enqueue a change; `batchUpdate` the changed cells to the
  sheet when online. Never overwrite a whole row blindly — write only changed input cells + `UpdatedAt`/`UpdatedBy`.
- **Read/pull:** on farm open and pull-to-refresh, `batchGet` the tabs and merge into Room.
- **Conflict:** last-write-wins per row using `UpdatedAt`; if a row changed remotely since load, keep the
  newer and note it. Quotas: batch everything (~60 req/min/user).
- **Status:** the top-bar chip already shows synced / syncing / offline.

## 5. Sharing, links & QR — **P6**
- **Share by email:** Drive `permissions.create(fileId, {role: writer|reader, type: user, emailAddress})`.
  Requires a valid Drive access token (see §2) — the current "sign in with Drive to share … fails" is the
  missing token. Owners share; the invitee opens the app, signs in, and the farm appears via §3 discovery.
- **Share link:** the spreadsheet's `webViewLink` (from Drive). Anyone with access + the link opens it.
- **QR:** encode `webViewLink` (or a `flockit://open?sheetId=…` deep link) as a QR with ZXing
  (`com.google.zxing:core`) drawn to a Bitmap; **scan** with CameraX + ML Kit Barcode (needs CAMERA
  permission) to import a shared farm by id.
- **Permissions:** editors edit; viewers are read-only (already have farm/flock **lock**). A non-owner
  can't delete the *sheet* (Drive rule) but can **remove it from their own list** (unregister locally) —
  wire "Delete" for shared farms to unregister, not to attempt a Drive delete.

## 6. Recycle bin & backups (failsafe — implemented locally)
- **Recycle bin:** delete is a **soft delete** (`deleted=true`, `deletedAt`). A Recycle Bin screen lists
  trashed flocks/farms with **Restore** and **Delete forever**. Nothing is lost by a mis-tap.
- **Local backups:** one-tap **Export** writes the whole local DB (farms, flocks, daily data, tasks,
  settings) to a timestamped JSON file you can save/share; **Import** restores it. (Cloud copy of the
  backup can also live in Drive `appDataFolder` once P6 is in.)

## 7. Account / profile (implemented)
A profile sheet shows the signed-in Google account (name, email, avatar) or "Offline (local only)", with
sign out and app version. Reached from the avatar on the Farms screen.

## 8. Build/signing facts
Kotlin + Compose + Room; AGP 9.1.1 / Gradle 9.3.1; committed `flockit-debug.jks` for a stable debug SHA-1.
Build: `./gradlew :app:assembleDebug` (see repo README / memory for the isolated GRADLE_USER_HOME note).

---
Sources: Android Identity / Credential Manager & Authorization guidance —
https://developer.android.com/identity/sign-in/credential-manager-siwg ·
https://developer.android.com/identity/authorization ·
https://developer.android.com/identity/sign-in/legacy-gsi-migration
