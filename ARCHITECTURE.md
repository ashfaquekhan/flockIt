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

## 9. Code layout and rules for future changes (v38)
The app follows the standard Android architecture (UI → state holder → domain → data), with
unidirectional data flow: state flows down into composables, events flow up as lambdas.

```
com.example.flock
├─ ui/            Compose only: screens, components, theme. No maths, no I/O.
│  ├─ screens/    OutputScreen (page order) · OutputTopics (tab cards) · OutputUi (kit: KpiLine, RangeParam,
│  │              SlimCompareBar, ValueChip…) · OutputData (one read-only view model of a day) · InfoTopics (ⓘ texts)
│  ├─ components/ shared widgets (FlockLoader, dialogs, hold buttons)
│  └─ FlockViewModel  screen state (StateFlow) + user actions; survives rotation
├─ domain/        pure Kotlin rules with unit tests, no Android imports:
│                 DaySchedule (dark, feed, refill, walk times) · FeedCorrection (capped feed advice)
│                 IntakeForecast (Kalman filter on appetite → bags, range, chances) · GrowthForecast (target day,
│                 harvest weight) · FlockKpis (ADG, 7-day FCR, mortality windows, projection checks)
│                 BirdEnvironment (air → heat load → feed, water, growth, body temperature, risk; sensor-ready sources)
│                 BirdBehaviour (time budget and walking speed by size, heat and cold) · Projection (what was
│                 projected for each day before its entry; the clock's share of the day) · Uniformity (CV from groups)
│                 FeedingRhythm (when in the day birds eat) · FlockNeeds (how likely hungry, thirsty, panting)
├─ engine/        pure Kotlin biology and climate maths (PhysiologicalEngine, IbController, CompanyStandard)
├─ data/          Room entities, DAOs, migrations, FlockRepository · FlockCalc (the day-by-day calculation as one pure
│                 function, used by the phone's database and the sheet's report tabs alike)
├─ sync/          Google Sheets: SheetSchema (layout rules, read by header, upgrades) · SheetReports (report tabs —
│                 FeedLedger, DailySummary, Computed, Projections, Formulas — rebuilt from the input rows, never read back) · SheetsSyncManager · auth
├─ network/       weather client · WeatherStore (hourly weather of the flock's days kept on the phone) · ViewPrefs
└─ notify/        task alarms
```

Patterns in use, and where:
- **MVVM + repository**: composables read `StateFlow`s from `FlockViewModel`; the repository owns Room and
  the sheet; Room is what the UI reads (offline-first).
- **Pure domain objects** (`domain/`, `engine/`): every number shown on Output comes from a function that
  takes plain values and returns plain values, so it can be unit-tested without a phone. New rules go here
  first, with a test (`DayScheduleTest`, `FeedCorrectionTest`, `FeedPlanTest`, `IbControllerTest`).
- **Presentation model** (`OutputData`): derives everything a day's page shows from the stored day,
  the farm and the flock, once; cards only format it.
- **Schema versioning**: Room migrations (`MIGRATION_x_y`, tested in `MigrationTest`) and the sheet's
  schema number with automatic upgrade of older sheets and their backups (`SheetSchemaTest`). Never change
  a column or table without a migration and a test.
- **Design tokens** (`ui/theme`): the look follows Expo's design system (`@expo/styleguide`, dark theme, on
  Radix Colors). `Color.kt` holds the screen, panel and text colours (Radix slate), the one accent (blue) and
  the six value kinds (present, projected, ideal, commercial, min, max); `Surfaces.kt` holds the three
  surfaces — a card (`GlassBox`: see-through fill + hairline), a value (`softBox`: faint fill, no outline),
  the chosen one of several (`chosenBox`). Screens use these and the kit in `OutputUi`, never raw colours or
  their own borders, so the look changes in one place. No white outlines round values.
- **Explanations in one place**: every ⓘ reads `InfoTopics`; when a formula changes, its text changes in
  the same commit.

Rules added in v43:
- **Entered and projected never share a slot.** An entered value is shown as entered; a projection sits beside or
  under it, marked as projected (the kit does this: `KpiLine`, `RangeParam(projected = …)`, `TodayCard`, the
  charts' Projected line). Projections for today move with the clock (`OutputData.nowView`).
- **Basic and advanced.** A card that is only for the full view goes behind `if (!basic)`; detail inside a card
  goes behind `More(…)`. New numbers go to Advanced first.
- **Air comes from a source** (`BirdEnvironment.Source`: sensor → set by hand → weather → ideal). Anything that
  needs the house's air takes it from there; a sensor feed is one more source, nothing else changes.
- **Every worked-out value written to the sheet has a row in `SheetReports.formulas()`.**

Rules when adding a feature:
1. Rule or formula → `domain/` (or `engine/`) + unit test. 2. Stored value → entity + Room migration +
   sheet column (SheetSchema aliases for old names) + tests. 3. Display → `OutputData` field, then a card
   using the kit, with an ⓘ topic. 4. CHANGELOG.md and ISSUES.md in the same commit.

Planned next steps (not done yet, to keep each release installable over a live flock):
- Move `OutputData`'s derivations that are rules (feed plan, pan patterns, stock) into `domain/` use cases.
- Split `FlockRepository.recompute` into small use cases (weights, feed, water, ventilation).
- Dependency injection (Hilt) instead of `FlockViewModel` building the database and repository itself.
- Gradle modules (`:core:domain`, `:core:data`, `:feature:output`, `:feature:entry`) once the packages
  above have no cross-dependencies.

## 10. Security notes
- **Debug keystore** `flockit-debug.jks` is committed with password `flockit` on purpose (stable debug
  SHA-1 for Google sign-in). It must never sign a Play release; release signing reads `STORE_PASSWORD` /
  `KEY_PASSWORD` from the environment.
- **Access token** is cached in plain SharedPreferences (`user_access_token`). Move it to memory only (it
  lasts ~1 h and can be re-requested) or encrypted storage when the P6 auth change lands.
- **`android:allowBackup="true"`** lets Android back up the local database and preferences (including that
  token). Add `dataExtractionRules` that exclude the token, or turn backup off.
- Sheets data is protected by Google Drive sharing; the app never stores Google passwords.
- No secrets in the sheet; the user's email is used only to name who changed a row.

---
Sources: Android Identity / Credential Manager & Authorization guidance —
https://developer.android.com/identity/sign-in/credential-manager-siwg ·
https://developer.android.com/identity/authorization ·
https://developer.android.com/identity/sign-in/legacy-gsi-migration
