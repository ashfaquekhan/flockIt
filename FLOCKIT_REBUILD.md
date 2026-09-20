# FlockIt — first-principles data architecture (rebuild plan)

## The mental model
FlockIt is a **UI over Google Sheets**. Google Sheets/Drive is the database and the collaboration
layer; the app adds the things Sheets can't do on its own (day-locking, alarms, derived targets,
offline entry, recycle bin). A user signs in with Google; their data lives in **their own Drive**;
sharing a farm = Drive-sharing that farm's spreadsheet; multiple users collaborate live.

## Why the current build fails (diagnosis)
- It discovers farms with the Drive `files.list` API under the **`drive.file`** scope. Per Google,
  `drive.file` only returns files **your app created or the user picked** — it **never returns files
  merely shared with the user**, and it doesn't carry an app's created-files across to a *different
  account*. So on a second device/account the list is empty, and opening a shared sheet whose access
  the app can't see throws **"Unable to read _Meta tab."**
- There is no per-user cloud **index**, so a fresh login has nothing to load from.

Fixing this needs a scope change + an index, not another patch.

## Scopes (all usable in Testing without Google verification)
- **`https://www.googleapis.com/auth/spreadsheets`** — read/write **any** spreadsheet the user can
  access, including ones shared with them. This is the **data plane** for collaboration.
- **`https://www.googleapis.com/auth/drive.file`** — create the app's own farm spreadsheets and
  **share** them (`permissions.create`). (App-owned files only — that's fine; we don't rely on it to
  discover shared files.)
- **`https://www.googleapis.com/auth/drive.appdata`** — a hidden, **per-user, per-app folder that
  syncs across devices**. This is the **control plane** / "dedicated FlockIt space in my Drive."

(`spreadsheets` is a *sensitive* scope — fine in Testing; needs Google verification only to publish to
the general public. `drive.file` and `drive.appdata` are non-sensitive.)

## Two planes

### 1. Data plane — one spreadsheet per farm (shareable)
Each farm is one Google Spreadsheet in the owner's Drive (in a visible **"FlockIt Farms"** folder),
tabs: `_Meta, _Farm, _Config, _FeedTypes, Flocks, DailyData, Tasks, ActivityLog`. All reads/writes go
through the **Sheets API with the `spreadsheets` scope**, so the owner *and* any shared collaborator
(editor/viewer) work on the same sheet live. Sharing = `drive.file permissions.create(email, role)`.

### 2. Control plane — a private index in appDataFolder (fast cross-device load)
A single JSON file in the user's **appDataFolder**, e.g. `flockit-index.json`:
```json
{
  "version": 1,
  "farms": [
    {"spreadsheetId": "1AbC…", "name": "Maa Tarini", "role": "owner",  "addedAt": 0, "deleted": false},
    {"spreadsheetId": "1XyZ…", "name": "Partner Farm", "role": "editor","addedAt": 0, "deleted": false}
  ],
  "recycleBin": [ … soft-deleted farm/flock refs … ],
  "settings": { … app prefs … }
}
```
- **Login on any device →** read `flockit-index.json` from appDataFolder → the Farms list is populated
  instantly (owned **and** previously-accepted shared farms), because appDataFolder syncs across the
  user's devices. Farm sheets are opened on demand via the `spreadsheets` scope.
- **Create a farm →** create its spreadsheet, then append its id to the index.
- **Accept a shared farm →** owner shares the sheet + sends the link/ID/QR; the invitee opens it once,
  the app validates it (reads `_Meta` via `spreadsheets`), and appends `{id, role:"editor"}` to *their*
  index. From then on it loads automatically on all their devices.
- **Recycle bin / logs / settings** live in the index (control plane), not scattered in local-only Room.

This removes the dependency on `files.list`/`drive.file` for discovery entirely — the index is the
source of truth for "which farms this user has," which is what makes new-device login fast and correct.

## Offline-first
Room mirrors the **currently open** farm for instant, offline use. Writes update Room immediately, then
push changed cells to the sheet (`batchUpdate`) when online; pulls on open/refresh (`batchGet`). The
index is cached locally too and reconciled with appDataFolder on login. Conflict = last-write-wins per
row via `UpdatedAt`.

## The collaboration flow (end to end)
1. Owner (signed in) creates farm → spreadsheet in "FlockIt Farms" + entry in owner's index.
2. Owner shares → `permissions.create(email, writer|reader)` (Drive emails the invitee) + app offers a
   copy link / QR of `https://docs.google.com/spreadsheets/d/<id>/edit`.
3. Invitee (signed in, same or different account) opens the link/ID/QR in the app → app reads `_Meta`
   via `spreadsheets` → adds it to the invitee's appDataFolder index.
4. Both edit the same sheet live; each device stays in sync via pull/push; each keeps its own offline
   cache. Non-owners can remove a farm from *their* index (won't delete the owner's sheet).

## What to build (order)
1. **Auth/scopes:** add `drive.appdata` to sign-in + the token scope string.
2. **IndexRepository** (appDataFolder JSON read/write) — the new source of truth for the farm list,
   recycle bin, settings.
3. **Discovery via index** (not `files.list`) on login/init → populate Farms fast.
4. **Data R/W via `spreadsheets`** for owned + shared sheets (fixes "Unable to read _Meta").
5. **Open-shared** = validate + add to index. **Share** = permission + link/QR.
6. Keep the existing UI, engine, offline Room cache, recycle bin (now backed by the index).

## Testing reality
The cloud paths can only be verified with **real Google accounts on real devices** (sign-in, sharing
between two accounts, cross-device login). That testing loop has to happen where those accounts/devices
are — so each change needs a real-device check, not just a compile.

---
Sources: Drive `drive.file` limits — https://developers.google.com/workspace/drive/api/guides/api-specific-auth ·
appDataFolder cross-device — https://developers.google.com/workspace/drive/api/guides/appdata ·
Drive sharing — https://developers.google.com/drive/api/guides/manage-sharing
