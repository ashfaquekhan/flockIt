package com.example.flock.sync

import android.util.Log
import com.example.flock.data.ConfigEntity
import com.example.flock.data.DailyDataEntity
import com.example.flock.data.FarmEntity
import com.example.flock.data.FarmRegistryEntity
import com.example.flock.data.FeedTypeEntity
import com.example.flock.data.FlockDatabase
import com.example.flock.data.FlockEntity
import com.example.flock.data.TaskEntity
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SheetsSyncManager(
    private val authManager: GoogleAuthManager,
    private val db: FlockDatabase
) {
    private val TAG = "SheetsSyncManager"
    private val FLOCKIT_FOLDER_NAME = "FlockIt Farms"
    private val INDEX_FILE_NAME = "flockit-index.json"

    private val moshi = Moshi.Builder().build()
    private val indexAdapter = moshi.adapter(FlockItIndex::class.java)

    /** True when a real Google account is signed in (cloud reads/writes are expected). */
    fun isOnline(): Boolean =
        authManager.authState.value.isSignedIn && !authManager.authState.value.isDemoMode

    // ---------------------------------------------------------------------
    // Control plane: per-user index in the hidden appDataFolder (syncs across
    // the user's devices). This is the source of truth for "which farms this
    // user has" — it does NOT depend on Drive files.list/drive.file, so a
    // fresh device login loads owned AND accepted-shared farms correctly.
    // ---------------------------------------------------------------------

    /** Finds the appDataFolder index file id, or null if it doesn't exist yet. */
    private suspend fun findIndexFileId(authHeader: String): String? {
        return try {
            val res = GoogleApiClientProvider.driveApi.listFiles(
                authHeader = authHeader,
                query = "name='$INDEX_FILE_NAME' and trashed=false",
                fields = "files(id, name)",
                spaces = "appDataFolder"
            )
            if (res.isSuccessful) res.body()?.files?.firstOrNull()?.id else null
        } catch (e: Exception) {
            Log.w(TAG, "findIndexFileId failed: ${e.message}")
            null
        }
    }

    /** Reads the FlockIt index from appDataFolder. Returns an empty index if none exists, null on error. */
    suspend fun loadIndex(): FlockItIndex? = withContext(Dispatchers.IO) {
        val authHeader = authManager.getAuthHeader() ?: return@withContext null
        try {
            val fileId = findIndexFileId(authHeader) ?: return@withContext FlockItIndex()
            val res = GoogleApiClientProvider.driveApi.downloadFileContent(authHeader, fileId)
            if (res.isSuccessful) {
                val json = res.body()?.string().orEmpty()
                if (json.isBlank()) FlockItIndex() else (indexAdapter.fromJson(json) ?: FlockItIndex())
            } else {
                Log.w(TAG, "loadIndex download failed: ${res.code()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadIndex failed", e)
            null
        }
    }

    /** Writes the FlockIt index to appDataFolder, creating the file if needed. */
    suspend fun saveIndex(index: FlockItIndex): Boolean = withContext(Dispatchers.IO) {
        val authHeader = authManager.getAuthHeader() ?: return@withContext false
        try {
            val json = indexAdapter.toJson(index)
            val body = json.toRequestBody("application/json".toMediaType())
            var fileId = findIndexFileId(authHeader)
            if (fileId == null) {
                val createRes = GoogleApiClientProvider.driveApi.createFile(
                    authHeader = authHeader,
                    request = CreateDriveFileRequest(
                        name = INDEX_FILE_NAME,
                        mimeType = "application/json",
                        parents = listOf("appDataFolder")
                    )
                )
                if (!createRes.isSuccessful || createRes.body() == null) {
                    Log.w(TAG, "saveIndex create failed: ${createRes.code()}")
                    return@withContext false
                }
                fileId = createRes.body()!!.id
            }
            val up = GoogleApiClientProvider.driveApi.uploadFileContent(authHeader, fileId, body = body)
            up.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "saveIndex failed", e)
            false
        }
    }

    /** Adds/updates a farm in the index (deduped by spreadsheetId). */
    suspend fun addFarmToIndex(spreadsheetId: String, name: String, role: String = "owner"): Boolean {
        val current = loadIndex() ?: FlockItIndex()
        val others = current.farms.filterNot { it.spreadsheetId == spreadsheetId }
        val updated = current.copy(farms = others + IndexFarm(spreadsheetId, name, role, deleted = false))
        return saveIndex(updated)
    }

    /** Soft-deletes (deleted=true) or restores a farm in the index. */
    suspend fun setFarmDeletedInIndex(spreadsheetId: String, deleted: Boolean): Boolean {
        val current = loadIndex() ?: return false
        val updated = current.copy(farms = current.farms.map {
            if (it.spreadsheetId == spreadsheetId) it.copy(deleted = deleted) else it
        })
        return saveIndex(updated)
    }

    /** Permanently removes a farm from the index (does NOT delete the owner's spreadsheet). */
    suspend fun removeFarmFromIndex(spreadsheetId: String): Boolean {
        val current = loadIndex() ?: return false
        val updated = current.copy(farms = current.farms.filterNot { it.spreadsheetId == spreadsheetId })
        return saveIndex(updated)
    }

    /**
     * Migration helper: ensures every farm already known locally (from a legacy
     * files.list discovery or a pre-index build) is present in the appDataFolder
     * index, in a single read+write. Safe to call repeatedly.
     */
    suspend fun backfillIndexFromRegistry(): Boolean = withContext(Dispatchers.IO) {
        authManager.getAuthHeader() ?: return@withContext false
        try {
            val local = db.farmRegistryDao().getAllFarmsOnce()
            if (local.isEmpty()) return@withContext true
            val current = loadIndex() ?: FlockItIndex()
            val known = current.farms.associateBy { it.spreadsheetId }.toMutableMap()
            var changed = false
            for (reg in local) {
                if (!known.containsKey(reg.spreadsheetId)) {
                    known[reg.spreadsheetId] = IndexFarm(
                        spreadsheetId = reg.spreadsheetId,
                        name = reg.farmName,
                        role = if (reg.isOwner) "owner" else "editor",
                        deleted = false
                    )
                    changed = true
                }
            }
            if (changed) saveIndex(current.copy(farms = known.values.toList())) else true
        } catch (e: Exception) {
            Log.w(TAG, "backfillIndexFromRegistry failed: ${e.message}")
            false
        }
    }

    /**
     * Cross-device discovery: read the index from appDataFolder and materialise each
     * non-deleted farm into the local Room registry, pulling its data via the
     * spreadsheets scope. Fast and correct on a fresh device because appDataFolder
     * syncs per-user (unlike files.list under drive.file).
     */
    suspend fun syncFromIndex(): Result<Int> = withContext(Dispatchers.IO) {
        authManager.getAuthHeader() ?: return@withContext Result.failure(Exception("Not authenticated"))
        val index = loadIndex() ?: return@withContext Result.failure(Exception("Could not read FlockIt index"))
        var imported = 0
        val userEmail = authManager.authState.value.email
        for (f in index.farms) {
            if (f.deleted) continue
            try {
                val existing = db.farmRegistryDao().getFarm(f.spreadsheetId)
                if (existing == null) {
                    db.farmRegistryDao().insertOrUpdate(
                        FarmRegistryEntity(
                            spreadsheetId = f.spreadsheetId,
                            farmName = f.name,
                            role = if (f.role == "owner") "Owner" else "Editor",
                            isOwner = f.role == "owner",
                            ownerEmail = userEmail,
                            lastOpened = System.currentTimeMillis(),
                            syncStatus = "synced",
                            lastSyncedAt = System.currentTimeMillis()
                        )
                    )
                    imported++
                }
                pullFarmData(f.spreadsheetId)
            } catch (e: Exception) {
                Log.w(TAG, "syncFromIndex: failed for ${f.spreadsheetId}: ${e.message}")
            }
        }
        Result.success(imported)
    }

    // =====================================================================
    // DATA PLANE — Google Sheets as the authoritative workspace.
    //
    // Design (first principles): the sheet stores the AUTHORITATIVE raw data
    // (farm settings, flock definitions, and each day's INPUTS). Every derived
    // value (targets, FCR, ventilation, area...) is recomputed locally by the
    // engine on each device, so there is nothing to conflict over. Room is only
    // an offline cache/mirror.
    //
    // Robustness rules that fix the "empty sheet" bug:
    //  - ONE canonical column order per tab, shared by writer + reader (no drift).
    //  - Writes are anchored at a single top-left cell (e.g. 'Tab'!A1) so an
    //    atomic batchUpdate can never 400 on a range/width mismatch.
    //  - valueInputOption = RAW and every cell is a String, so what we write is
    //    exactly what we read back (no locale/number/date/scientific surprises).
    //  - EVERY write checks res.isSuccessful and returns a Result.
    // =====================================================================

    private val RAW = "RAW"

    // Quoted A1 target for batchUpdate @Body ranges (safe for _underscore tabs).
    private fun a1(tabName: String, cell: String = "A1") =
        "'" + tabName.replace("'", "''") + "'!" + cell
    // Unquoted range for append @Path (only used on tabs with simple names).
    private fun appendRange(tabName: String, cell: String = "A1") = "$tabName!$cell"

    private fun sv(v: Any?): String = v?.toString() ?: ""
    private fun List<Any>.s(i: Int): String = getOrNull(i)?.toString()?.trim() ?: ""
    /** Number from a cell, tolerant of hand edits: "1,200", " 12.5 ", "12.5 kg" all read. */
    private fun num(raw: Any?): Double? {
        val t = raw?.toString()?.trim()?.replace(",", "")?.replace(" ", "") ?: return null
        if (t.isEmpty()) return null
        return t.toDoubleOrNull() ?: Regex("^-?\\d+(\\.\\d+)?").find(t)?.value?.toDoubleOrNull()
    }
    private fun List<Any>.d(i: Int): Double? = num(getOrNull(i))
    private fun List<Any>.i(i: Int): Int? = num(getOrNull(i))?.let { kotlin.math.round(it).toInt() }
    private fun List<Any>.l(i: Int): Long? =
        getOrNull(i)?.toString()?.trim()?.let { it.toLongOrNull() ?: it.toDoubleOrNull()?.toLong() }
    private fun List<Any>.b(i: Int): Boolean =
        getOrNull(i)?.toString()?.trim()?.let { it.equals("true", true) || it == "1" } ?: false

    // ---- Table layouts and parsing live in SheetSchema: tables are read by header name, so a sheet
    //      from any older app version loads into the right fields. ----
    private val FLOCK_HEADERS get() = SheetSchema.FLOCK_HEADERS
    private val DAILY_HEADERS get() = SheetSchema.DAILY_HEADERS
    private val TASK_HEADERS get() = SheetSchema.TASK_HEADERS
    private fun flockToRow(f: FlockEntity) = SheetSchema.flockRow(f)
    private fun dayToRow(d: DailyDataEntity) = SheetSchema.dayRow(d)
    private fun taskToRow(t: TaskEntity) = SheetSchema.taskRow(t)
    private fun farmToKV(farm: FarmEntity) = SheetSchema.farmToKV(farm)

    /** Verified RAW write of a 2D block anchored at the top-left cell of a tab. */
    private suspend fun putBlock(
        authHeader: String, spreadsheetId: String, tabName: String, values: List<List<Any>>
    ): Boolean {
        if (values.isEmpty()) return true
        return try {
            val res = GoogleApiClientProvider.sheetsApi.batchUpdateValues(
                authHeader, spreadsheetId,
                BatchUpdateValuesRequest(
                    valueInputOption = RAW,
                    data = listOf(ValueRange(range = a1(tabName), values = values))
                )
            )
            if (!res.isSuccessful) Log.e(TAG, "putBlock $tabName -> ${res.code()} ${res.errorBody()?.string()}")
            res.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "putBlock $tabName failed", e); false
        }
    }

    /** Append rows (RAW) to a simple-named tab. */
    private suspend fun appendRows(
        authHeader: String, spreadsheetId: String, tabName: String, rows: List<List<Any>>
    ): Boolean {
        if (rows.isEmpty()) return true
        return try {
            val res = GoogleApiClientProvider.sheetsApi.appendValues(
                authHeader = authHeader, spreadsheetId = spreadsheetId,
                range = appendRange(tabName), valueInputOption = RAW,
                request = ValueRange(range = appendRange(tabName), values = rows)
            )
            if (!res.isSuccessful) Log.e(TAG, "appendRows $tabName -> ${res.code()}")
            res.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "appendRows $tabName failed", e); false
        }
    }

    /** Writes a flock definition and its day rows. Appends (create) — Room dedupes locally. */
    suspend fun pushFlock(spreadsheetId: String, flock: FlockEntity, days: List<DailyDataEntity>): Result<Unit> =
        withContext(Dispatchers.IO) {
            val authHeader = authManager.getAuthHeader() ?: return@withContext Result.success(Unit)
            ensureLayout(authHeader, spreadsheetId)
            try {
                val okFlock = appendRows(authHeader, spreadsheetId, "Flocks", listOf(flockToRow(flock)))
                val okDays = if (days.isEmpty()) true
                    else appendRows(authHeader, spreadsheetId, "DailyData", days.map { dayToRow(it) })
                logActivity(spreadsheetId, "CREATE_FLOCK", "Added flock ${flock.name} (${flock.flockId})")
                if (okFlock && okDays) Result.success(Unit)
                else Result.failure(Exception("Sheet write failed (flock=$okFlock, days=$okDays)"))
            } catch (e: Exception) { Result.failure(e) }
        }

    /** Upserts a flock row by flockId (status, lock, counts), else appends it. */
    suspend fun upsertFlock(spreadsheetId: String, flock: FlockEntity): Result<Unit> =
        withContext(Dispatchers.IO) {
            val authHeader = authManager.getAuthHeader() ?: return@withContext Result.success(Unit)
            ensureLayout(authHeader, spreadsheetId)
            try {
                val keyRes = GoogleApiClientProvider.sheetsApi.batchGet(authHeader, spreadsheetId, listOf(appendRange("Flocks", "A2:A")))
                val rows = keyRes.body()?.valueRanges?.getOrNull(0)?.values ?: emptyList()
                val rowNum = rows.indexOfFirst { it.s(0) == flock.flockId }.let { if (it >= 0) it + 2 else -1 }
                val ok = if (rowNum > 0) {
                    GoogleApiClientProvider.sheetsApi.batchUpdateValues(
                        authHeader, spreadsheetId,
                        BatchUpdateValuesRequest(valueInputOption = RAW,
                            data = listOf(ValueRange(range = a1("Flocks", "A$rowNum"), values = listOf(flockToRow(flock)))))
                    ).isSuccessful
                } else appendRows(authHeader, spreadsheetId, "Flocks", listOf(flockToRow(flock)))
                if (ok) Result.success(Unit) else Result.failure(Exception("Flocks write failed"))
            } catch (e: Exception) { Result.failure(e) }
        }

    /** Upserts a single day's inputs into the DailyData tab (find row by FlockId+Day, else append). */
    suspend fun pushDayEntry(spreadsheetId: String, day: DailyDataEntity): Result<Unit> =
        withContext(Dispatchers.IO) {
            val authHeader = authManager.getAuthHeader() ?: return@withContext Result.success(Unit)
            ensureLayout(authHeader, spreadsheetId)
            try {
                val res = GoogleApiClientProvider.sheetsApi.batchGet(
                    authHeader, spreadsheetId, listOf(a1("DailyData", "1:1"), appendRange("DailyData", "A2:B"))
                )
                if (!res.isSuccessful) return@withContext Result.failure(Exception("DailyData read failed (${res.code()})"))
                val vr = res.body()?.valueRanges.orEmpty()
                var header: List<String> = vr.getOrNull(0)?.values?.firstOrNull()?.map { it.toString().trim() }?.takeIf { it.isNotEmpty() }
                    ?: SheetSchema.DAILY_HEADERS
                val rows = vr.getOrNull(1)?.values ?: emptyList()
                var rowNum = -1
                for ((idx, r) in rows.withIndex()) if (r.s(0) == day.flockId && r.i(1) == day.dayNumber) { rowNum = idx + 2; break }
                val values = SheetSchema.dayValues(day)
                val writes = mutableListOf<ValueRange>()
                // a feed variety with no column yet gets one at the end of the header
                val missing = SheetSchema.usedSplit(day).keys.map { SheetSchema.usedHeader(it) }
                    .filter { name -> header.none { SheetSchema.norm(it) == SheetSchema.norm(name) } }
                if (missing.isNotEmpty()) {
                    growColumns(authHeader, spreadsheetId, "DailyData", header.size + missing.size)
                    writes += ValueRange(range = a1("DailyData", "${SheetSchema.colLetter(header.size + 1)}1"), values = listOf(missing))
                    header = header + missing
                }
                val ok = if (rowNum > 0) {
                    // only the columns the app knows, in runs, so the farm's own columns keep their values
                    val known = header.indices.filter { SheetSchema.norm(header[it]) in values.keys || SheetSchema.isUsedColumn(header[it]) }
                    var i = 0
                    while (i < known.size) {
                        var j = i
                        while (j + 1 < known.size && known[j + 1] == known[j] + 1) j++
                        val cells = (known[i]..known[j]).map { values[SheetSchema.norm(header[it])] ?: "" }
                        writes += ValueRange(range = a1("DailyData", "${SheetSchema.colLetter(known[i] + 1)}$rowNum"), values = listOf(cells))
                        i = j + 1
                    }
                    GoogleApiClientProvider.sheetsApi.batchUpdateValues(authHeader, spreadsheetId,
                        BatchUpdateValuesRequest(valueInputOption = RAW, data = writes)).isSuccessful
                } else {
                    val headerOk = writes.isEmpty() || GoogleApiClientProvider.sheetsApi.batchUpdateValues(authHeader, spreadsheetId,
                        BatchUpdateValuesRequest(valueInputOption = RAW, data = writes)).isSuccessful
                    headerOk && appendRows(authHeader, spreadsheetId, "DailyData", listOf(header.map { values[SheetSchema.norm(it)] ?: "" }))
                }
                if (ok) Result.success(Unit) else Result.failure(Exception("DailyData write failed"))
            } catch (e: Exception) { Result.failure(e) }
        }

    /** Grows a tab to at least [cols] columns. */
    private suspend fun growColumns(authHeader: String, fileId: String, tab: String, cols: Int) {
        val g = gridOf(authHeader, fileId)?.get(tab) ?: return
        if (g.third >= cols) return
        try {
            GoogleApiClientProvider.sheetsApi.batchUpdateSpreadsheet(authHeader, fileId,
                BatchUpdateSpreadsheetRequest(listOf(SheetRequest(AppendDimensionRequest(g.first, "COLUMNS", cols - g.third + 5)))))
        } catch (e: Exception) { Log.w(TAG, "growColumns: ${e.message}") }
    }

    /** Upserts a task row by taskId (update in place, else append) so edits don't duplicate rows. */
    suspend fun pushTask(spreadsheetId: String, task: TaskEntity): Result<Unit> = withContext(Dispatchers.IO) {
        val authHeader = authManager.getAuthHeader() ?: return@withContext Result.success(Unit)
        ensureLayout(authHeader, spreadsheetId)
        try {
            val keyRes = GoogleApiClientProvider.sheetsApi.batchGet(
                authHeader, spreadsheetId, listOf(appendRange("Tasks", "A2:A"))
            )
            val rows = keyRes.body()?.valueRanges?.getOrNull(0)?.values ?: emptyList()
            var rowNum = -1
            for ((idx, r) in rows.withIndex()) if (r.s(0) == task.taskId) { rowNum = idx + 2; break }
            val ok = if (rowNum > 0) {
                val res = GoogleApiClientProvider.sheetsApi.batchUpdateValues(
                    authHeader, spreadsheetId,
                    BatchUpdateValuesRequest(valueInputOption = RAW,
                        data = listOf(ValueRange(range = a1("Tasks", "A$rowNum"), values = listOf(taskToRow(task)))))
                )
                res.isSuccessful
            } else {
                appendRows(authHeader, spreadsheetId, "Tasks", listOf(taskToRow(task)))
            }
            if (ok) Result.success(Unit) else Result.failure(Exception("Tasks write failed"))
        } catch (e: Exception) { Result.failure(e) }
    }

    /** Deletes a task row (blanks it) by taskId. Sheet keeps the empty row; Room deletes it. */
    suspend fun deleteTaskRow(spreadsheetId: String, taskId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val authHeader = authManager.getAuthHeader() ?: return@withContext Result.success(Unit)
        ensureLayout(authHeader, spreadsheetId)
        try {
            val keyRes = GoogleApiClientProvider.sheetsApi.batchGet(
                authHeader, spreadsheetId, listOf(appendRange("Tasks", "A2:A"))
            )
            val rows = keyRes.body()?.valueRanges?.getOrNull(0)?.values ?: emptyList()
            var rowNum = -1
            for ((idx, r) in rows.withIndex()) if (r.s(0) == taskId) { rowNum = idx + 2; break }
            if (rowNum > 0) {
                GoogleApiClientProvider.sheetsApi.batchUpdateValues(
                    authHeader, spreadsheetId,
                    BatchUpdateValuesRequest(valueInputOption = RAW,
                        data = listOf(ValueRange(range = a1("Tasks", "A$rowNum:O$rowNum"), values = listOf(List(15) { "" }))))
                )
            }
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    /** Rewrites the _Farm settings tab. */
    suspend fun pushFarmSettings(spreadsheetId: String, farm: FarmEntity): Result<Unit> = withContext(Dispatchers.IO) {
        val authHeader = authManager.getAuthHeader() ?: return@withContext Result.success(Unit)
        ensureLayout(authHeader, spreadsheetId)
        try {
            val ok = putBlock(authHeader, spreadsheetId, "_Farm", farmToKV(farm)) && trimBelow(authHeader, spreadsheetId, "_Farm", farmToKV(farm).size)
            if (ok) { logActivity(spreadsheetId, "UPDATE_FARM", "Updated farm settings"); Result.success(Unit) }
            else Result.failure(Exception("_Farm write failed"))
        } catch (e: Exception) { Result.failure(e) }
    }


    // ---------------------------------------------------------------------
    // Backups — tagged with appProperties so discovery never imports them as farms.
    // ---------------------------------------------------------------------
    private val BACKUP_KEY = "flockitBackupOf"
    private val BACKUP_PREFIX = "FlockIt backup"
    private val SNAPSHOT_PREFIX = "FlockIt pre-repair"

    /** True for any file this app made as a backup or safety snapshot (new tag or legacy name). */
    private fun isBackupFile(f: DriveFile): Boolean =
        f.appProperties?.containsKey(BACKUP_KEY) == true ||
            f.name.startsWith(BACKUP_PREFIX) || f.name.startsWith(SNAPSHOT_PREFIX)

    private suspend fun listBackupFiles(authHeader: String): List<DriveFile> {
        val q = "mimeType='application/vnd.google-apps.spreadsheet' and trashed=false and " +
            "(name contains '$BACKUP_PREFIX' or name contains '$SNAPSHOT_PREFIX')"
        val res = GoogleApiClientProvider.driveApi.listFiles(
            authHeader = authHeader, query = q,
            fields = "files(id, name, modifiedTime, appProperties)"
        )
        return res.body()?.files.orEmpty().filter { isBackupFile(it) }
    }

    private suspend fun readMeta(authHeader: String, spreadsheetId: String): Map<String, String> = try {
        GoogleApiClientProvider.sheetsApi.batchGet(authHeader, spreadsheetId, listOf(a1("_Meta", "A1:B20")))
            .body()?.valueRanges?.getOrNull(0)?.values.orEmpty()
            .associate { it.s(0) to it.s(1) }
    } catch (e: Exception) { emptyMap() }

    /** Backups belonging to one farm: tagged for it, or legacy-named copies carrying its farmId. */
    private suspend fun findBackupsOf(authHeader: String, spreadsheetId: String, farm: FarmEntity): List<DriveFile> =
        listBackupFiles(authHeader).filter { f ->
            if (f.id == spreadsheetId) return@filter false
            val tag = f.appProperties?.get(BACKUP_KEY)
            when {
                tag != null -> tag == spreadsheetId
                farm.farmId.isNotBlank() -> readMeta(authHeader, f.id)["farmId"] == farm.farmId
                else -> f.name.contains(farm.farmName)
            }
        }

    private fun quoted(tab: String) = "'" + tab.replace("'", "''") + "'"

    /** Each tab's (sheetId, rows, columns), from the file's own properties. */
    private suspend fun gridOf(authHeader: String, fileId: String): Map<String, Triple<Int, Int, Int>>? = try {
        val r = GoogleApiClientProvider.sheetsApi.getSpreadsheet(authHeader, fileId)
        if (!r.isSuccessful) null else r.body()?.sheets.orEmpty().mapNotNull { sh ->
            val p = sh.properties; val id = p.sheetId ?: return@mapNotNull null
            p.title to Triple(id, p.gridProperties?.rowCount ?: 0, p.gridProperties?.columnCount ?: 0)
        }.toMap()
    } catch (e: Exception) { Log.w(TAG, "gridOf $fileId: ${e.message}"); null }

    /**
     * Reads every FlockIt tab the file has, whole (a missing tab comes back null, so an older file with
     * fewer tabs still reads). Works for any schema version.
     */
    private suspend fun readRaw(authHeader: String, fileId: String): SheetSchema.RawFile? {
        val sp = try { GoogleApiClientProvider.sheetsApi.getSpreadsheet(authHeader, fileId) } catch (e: Exception) { Log.w(TAG, "readRaw $fileId: ${e.message}"); return null }
        if (!sp.isSuccessful) { Log.w(TAG, "readRaw $fileId -> ${sp.code()}"); return null }
        val props = sp.body()?.sheets.orEmpty().map { it.properties }
        val titles = props.map { it.title }.toSet()
        val want = SheetSchema.TABS.filter { it in titles && it != "ActivityLog" }
        val values = HashMap<String, List<List<Any?>>>()
        if (want.isNotEmpty()) {
            val res = try { GoogleApiClientProvider.sheetsApi.batchGet(authHeader, fileId, want.map { quoted(it) }) } catch (e: Exception) { Log.w(TAG, "readRaw values: ${e.message}"); return null }
            if (!res.isSuccessful) { Log.w(TAG, "readRaw values -> ${res.code()} ${res.errorBody()?.string()}"); return null }
            val vr = res.body()?.valueRanges.orEmpty()
            want.forEachIndexed { i, t -> values[t] = vr.getOrNull(i)?.values ?: emptyList() }
        }
        return SheetSchema.RawFile(
            titles = titles, meta = values["_Meta"], farm = values["_Farm"], config = values["_Config"],
            feedTypes = values["_FeedTypes"], flocks = values["Flocks"], days = values["DailyData"], tasks = values["Tasks"],
            sheetIds = props.mapNotNull { p -> p.sheetId?.let { p.title to it } }.toMap(),
            grid = props.associate { p -> p.title to ((p.gridProperties?.rowCount ?: 0) to (p.gridProperties?.columnCount ?: 0)) }
        )
    }

    /** Clears a key/value tab below its first [rows] rows (a rewrite with fewer keys leaves no stale ones). */
    private suspend fun trimBelow(authHeader: String, fileId: String, tab: String, rows: Int): Boolean {
        val g = gridOf(authHeader, fileId)?.get(tab) ?: return true
        if (g.second <= rows) return true
        return try {
            GoogleApiClientProvider.sheetsApi.batchClear(authHeader, fileId,
                BatchClearValuesRequest(listOf("${quoted(tab)}!A${rows + 1}:${SheetSchema.colLetter(maxOf(1, g.third))}${g.second}"))).isSuccessful
        } catch (e: Exception) { false }
    }

    /**
     * Writes [blocks] (tab → rows) into a file without ever leaving a tab empty: adds the tabs it lacks,
     * grows grids, writes every block from A1 in one call, then clears the old rows and columns beyond
     * each block. [addActivityLog] also adds the ActivityLog tab when missing.
     */
    private suspend fun writeBlocks(authHeader: String, fileId: String, blocks: Map<String, List<List<Any>>>, addActivityLog: Boolean): Boolean {
        try {
            var grid = gridOf(authHeader, fileId) ?: return false
            // 1) tabs the file lacks (older files), sized for their block
            val adds = mutableListOf<SheetRequest>()
            for ((t, v) in blocks) if (t !in grid) {
                adds += SheetRequest(addSheet = AddSheetRequest(SheetProperties(title = t,
                    gridProperties = GridProperties(rowCount = v.size + 100, columnCount = (v.maxOfOrNull { it.size } ?: 2) + 5))))
            }
            if (addActivityLog && "ActivityLog" !in grid)
                adds += SheetRequest(addSheet = AddSheetRequest(SheetProperties(title = "ActivityLog", gridProperties = GridProperties(rowCount = 500, columnCount = 6))))
            if (adds.isNotEmpty()) {
                val r = GoogleApiClientProvider.sheetsApi.batchUpdateSpreadsheet(authHeader, fileId, BatchUpdateSpreadsheetRequest(adds))
                if (!r.isSuccessful) { Log.w(TAG, "addSheet -> ${r.code()} ${r.errorBody()?.string()}"); return false }
                grid = gridOf(authHeader, fileId) ?: return false
                if (addActivityLog) putBlock(authHeader, fileId, "ActivityLog", listOf(SheetSchema.ACTIVITY_HEADERS))
            }
            // 2) grow grids so no block overflows
            val grow = mutableListOf<SheetRequest>()
            val size = HashMap<String, Pair<Int, Int>>()
            for ((t, v) in blocks) {
                val (id, rows, cols) = grid[t] ?: continue
                val needR = v.size; val needC = v.maxOfOrNull { it.size } ?: 1
                var r = rows; var c = cols
                if (rows < needR) { grow += SheetRequest(AppendDimensionRequest(id, "ROWS", needR - rows + 100)); r = needR + 100 }
                if (cols < needC) { grow += SheetRequest(AppendDimensionRequest(id, "COLUMNS", needC - cols + 5)); c = needC + 5 }
                size[t] = r to c
            }
            if (grow.isNotEmpty()) {
                val r = GoogleApiClientProvider.sheetsApi.batchUpdateSpreadsheet(authHeader, fileId, BatchUpdateSpreadsheetRequest(grow))
                if (!r.isSuccessful) { Log.w(TAG, "grow grid -> ${r.code()} ${r.errorBody()?.string()}"); return false }
            }
            // 3) every block in one write
            val w = GoogleApiClientProvider.sheetsApi.batchUpdateValues(authHeader, fileId,
                BatchUpdateValuesRequest(valueInputOption = RAW, data = blocks.map { (t, v) -> ValueRange(range = quoted(t) + "!A1", values = v) }))
            if (!w.isSuccessful) { Log.w(TAG, "write blocks -> ${w.code()} ${w.errorBody()?.string()}"); return false }
            // 4) clear whatever old rows / columns lie beyond each block
            val clears = mutableListOf<String>()
            for ((t, v) in blocks) {
                val (rows, cols) = size[t] ?: continue
                val n = v.size; val width = maxOf(1, v.maxOfOrNull { it.size } ?: 1)
                if (rows > n) clears += "${quoted(t)}!A${n + 1}:${SheetSchema.colLetter(cols)}$rows"
                if (cols > width && n > 0) clears += "${quoted(t)}!${SheetSchema.colLetter(width + 1)}1:${SheetSchema.colLetter(cols)}$n"
            }
            if (clears.isNotEmpty()) {
                val c = GoogleApiClientProvider.sheetsApi.batchClear(authHeader, fileId, BatchClearValuesRequest(clears))
                if (!c.isSuccessful) { Log.w(TAG, "clear leftovers -> ${c.code()} ${c.errorBody()?.string()}"); return false }
            }
            return true
        } catch (e: Exception) { Log.w(TAG, "writeBlocks $fileId failed: ${e.message}"); return false }
    }

    /** Writes [content] to a file in the current layout and reads it back; returns what's missing (empty = verified). */
    private suspend fun writeAndVerify(authHeader: String, fileId: String, content: SheetSchema.Content, meta: List<Pair<String, String>>): List<String> {
        if (!writeBlocks(authHeader, fileId, SheetSchema.blocks(content, meta), addActivityLog = true)) return listOf("could not be written")
        val back = readRaw(authHeader, fileId) ?: return listOf("could not be read back")
        return SheetSchema.verify(content, back)
    }

    private suspend fun canEdit(authHeader: String, fileId: String): Boolean = try {
        GoogleApiClientProvider.driveApi.getFile(authHeader, fileId).body()?.capabilities?.canEdit ?: true
    } catch (e: Exception) { true }

    private suspend fun tagBackup(authHeader: String, fileId: String, mainId: String, name: String?) {
        try {
            GoogleApiClientProvider.driveApi.updateFile(authHeader, fileId, DriveFileUpdate(
                appProperties = mapOf(BACKUP_KEY to mainId, "flockitKind" to "backup", "flockitSchema" to SheetSchema.VERSION.toString()), name = name))
        } catch (e: Exception) { Log.w(TAG, "tagBackup: ${e.message}") }
    }

    private fun isSnapshot(f: DriveFile) = f.appProperties?.get("flockitKind") == "snapshot" || f.name.startsWith(SNAPSHOT_PREFIX)

    /**
     * Copies a file as a backup (kind "backup") or safety snapshot (kind "snapshot"), tagged so it is never
     * imported as a farm. A backup of a current file gets full backup _Meta; a snapshot keeps the old file's
     * layout and says which schema it was.
     */
    private suspend fun copyAsBackup(authHeader: String, spreadsheetId: String, name: String, kind: String, schema: Int = SheetSchema.VERSION): String? = try {
        val r = GoogleApiClientProvider.driveApi.copyFile(
            authHeader, spreadsheetId,
            CopyFileRequest(name, mapOf(BACKUP_KEY to spreadsheetId, "flockitKind" to kind, "flockitSchema" to schema.toString()))
        )
        val id = r.body()?.id
        if (r.isSuccessful && id != null) {
            val farmId = db.farmDao().getFarm(spreadsheetId)?.farmId.orEmpty()
            val meta = if (kind == "backup") SheetSchema.backupMeta(farmId, spreadsheetId)
                else listOf("app" to "FlockIt", "schemaVersion" to schema.toString(), "role" to kind, "backupOf" to spreadsheetId,
                    "farmId" to farmId, "createdAt" to System.currentTimeMillis().toString())
            val block = listOf(listOf("Key", "Value")) + meta.map { listOf(it.first, it.second) }
            if (putBlock(authHeader, id, "_Meta", block)) trimBelow(authHeader, id, "_Meta", block.size)
            id
        } else {
            Log.w(TAG, "copyAsBackup -> ${r.code()}")
            null
        }
    } catch (e: Exception) { Log.w(TAG, "copyAsBackup failed: ${e.message}"); null }

    private suspend fun trashFile(authHeader: String, fileId: String): Boolean = try {
        GoogleApiClientProvider.driveApi.updateFile(authHeader, fileId, DriveFileUpdate(trashed = true)).isSuccessful
    } catch (e: Exception) { false }

    // ---------------------------------------------------------------------
    // Older layouts: brought up to date automatically, once per file
    // ---------------------------------------------------------------------
    /** Files known to be in the current layout this session (no need to check again before a write). */
    private val layoutOk = java.util.Collections.synchronizedSet(HashSet<String>())
    private val upgradeTried = java.util.Collections.synchronizedSet(HashSet<String>())

    /** Before a write: an older file is upgraded first, so rows go into the right columns. */
    private suspend fun ensureLayout(authHeader: String, spreadsheetId: String) {
        if (spreadsheetId in layoutOk) return
        val raw = readRaw(authHeader, spreadsheetId) ?: return
        if (SheetSchema.upgradeReasons(raw).isEmpty()) layoutOk += spreadsheetId else upgradeFarmFile(authHeader, spreadsheetId, raw)
    }

    /**
     * Rewrites an older farm file in the current layout from its own data (every column kept; ones the app
     * doesn't know move to the right), after copying the old file as a snapshot. Then does the same for
     * this farm's backups. Only for people who can edit the file; tried once per session.
     */
    private suspend fun upgradeFarmFile(authHeader: String, spreadsheetId: String, raw: SheetSchema.RawFile): Boolean {
        if (!upgradeTried.add(spreadsheetId)) return false
        if (!canEdit(authHeader, spreadsheetId)) return false
        val reasons = SheetSchema.upgradeReasons(raw)
        val farm = db.farmDao().getFarm(spreadsheetId) ?: FarmEntity(spreadsheetId = spreadsheetId)
        val config = db.configDao().getConfig(spreadsheetId)
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        copyAsBackup(authHeader, spreadsheetId, "$SNAPSHOT_PREFIX — ${farm.farmName} — $stamp", "snapshot", raw.schema)
        val content = SheetSchema.parse(spreadsheetId, raw, farm, config)
        val problems = writeAndVerify(authHeader, spreadsheetId, content, SheetSchema.primaryMeta(content.farm.farmId, raw.schema))
        if (problems.isNotEmpty()) {
            Log.w(TAG, "upgrade $spreadsheetId not verified: $problems")
            logActivity(spreadsheetId, "UPGRADE_FAILED", "Could not bring the sheet to schema ${SheetSchema.VERSION}: ${problems.joinToString()}; a snapshot of the old sheet is in Drive")
            return false
        }
        layoutOk += spreadsheetId
        var upgraded = 0
        for (b in findBackupsOf(authHeader, spreadsheetId, content.farm).filterNot { isSnapshot(it) }) {
            val br = readRaw(authHeader, b.id) ?: continue
            if (SheetSchema.upgradeReasons(br).isEmpty()) continue
            val bc = SheetSchema.parse(b.id, br, content.farm.copy(spreadsheetId = b.id), content.config)
            if (writeAndVerify(authHeader, b.id, bc, SheetSchema.backupMeta(content.farm.farmId, spreadsheetId)).isEmpty()) {
                tagBackup(authHeader, b.id, spreadsheetId, null); upgraded++
            }
        }
        logActivity(spreadsheetId, "UPGRADE", "Sheet brought to schema ${SheetSchema.VERSION} (${reasons.joinToString("; ")}) · $upgraded backup(s) upgraded · old sheet kept as a snapshot")
        return true
    }

    /**
     * Back up & repair, for a sheet of any age:
     *  1. read the live sheet, every backup of this farm and the phone's copy — all by header name, so
     *     older layouts read correctly — and merge them, newest row winning (nothing in the live sheet is
     *     dropped; rows only a backup or the phone has are brought back);
     *  2. copy the live sheet as a safety snapshot;
     *  3. rewrite the live sheet in the current layout (missing tabs added, unknown columns kept at the
     *     right, old leftover rows cleared) and read it back to verify every row is there;
     *  4. rewrite the newest backup the same way (or make one), and verify it too;
     *  5. only then move the other backups and the snapshot to the Drive trash (recoverable for 30 days).
     * If any check fails nothing is deleted.
     */
    suspend fun backupAndRepairFarm(spreadsheetId: String): Result<String> = withContext(Dispatchers.IO) {
        val authHeader = authManager.getAuthHeader()
            ?: return@withContext Result.failure(Exception("Sign in with Google to repair the sheet."))
        try {
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
            val localFarm = db.farmDao().getFarm(spreadsheetId) ?: FarmEntity(spreadsheetId = spreadsheetId)
            val localConfig = db.configDao().getConfig(spreadsheetId)
            if (!canEdit(authHeader, spreadsheetId))
                return@withContext Result.failure(Exception("You can view this farm but not edit its sheet; ask the owner to repair it."))

            // 1) Live sheet, phone, backups — merged.
            val liveRaw = readRaw(authHeader, spreadsheetId)
                ?: return@withContext Result.failure(Exception("Couldn't open the farm sheet. Check the connection and try again."))
            val fromSchema = liveRaw.schema
            val reasons = SheetSchema.upgradeReasons(liveRaw)
            val live = SheetSchema.parse(spreadsheetId, liveRaw, localFarm, localConfig)
            val flocksLocal = db.flockDao().getAllFlocksList(spreadsheetId)
            val phone = SheetSchema.Content(localFarm, localConfig, db.feedTypeDao().getFeedTypes(spreadsheetId), flocksLocal,
                flocksLocal.flatMap { db.dailyDataDao().getDailyDataList(spreadsheetId, it.flockId) },
                flocksLocal.flatMap { db.taskDao().getTasksList(spreadsheetId, it.flockId) })
            val backupFiles = findBackupsOf(authHeader, spreadsheetId, live.farm).sortedByDescending { it.modifiedTime ?: "" }
            val backupData = backupFiles.mapNotNull { f -> readRaw(authHeader, f.id)?.let { SheetSchema.parse(spreadsheetId, it, live.farm, live.config) } }
            val merged = SheetSchema.merge(live, phone, backupData)
            val content = merged.content

            // 2) Safety snapshot of the live sheet as it is now.
            val snapshotId = copyAsBackup(authHeader, spreadsheetId, "$SNAPSHOT_PREFIX — ${content.farm.farmName} — $stamp", "snapshot", fromSchema)

            // 3) Live sheet in the current layout, verified.
            val mainProblems = writeAndVerify(authHeader, spreadsheetId, content, SheetSchema.primaryMeta(content.farm.farmId, fromSchema))
            if (mainProblems.isNotEmpty()) {
                logActivity(spreadsheetId, "REPAIR_FAILED", "Rewrite not verified (${mainProblems.joinToString()}); backups and snapshot kept")
                return@withContext Result.failure(Exception("The sheet could not be fully rewritten (${mainProblems.joinToString()}). " +
                    "Nothing was deleted: your backups and a snapshot of the sheet before the repair are in Drive."))
            }
            layoutOk += spreadsheetId
            // The phone now matches the sheet.
            db.farmDao().insertOrUpdateFarm(content.farm)
            content.config?.let { db.configDao().insertOrUpdateConfig(it) }
            if (content.feedTypes.isNotEmpty()) db.feedTypeDao().insertFeedTypes(content.feedTypes)
            content.flocks.forEach { db.flockDao().insertFlock(it) }
            content.days.forEach { db.dailyDataDao().insertOrUpdateDay(it.copy(dirty = false)) }
            content.tasks.forEach { db.taskDao().insertTask(it) }

            // 4) One backup in the same layout: the newest existing backup is rewritten, or a new one is made.
            val backupName = "$BACKUP_PREFIX — ${content.farm.farmName} — $stamp"
            var backupId: String? = null
            var backupNote = ""
            val keep = backupFiles.firstOrNull { !isSnapshot(it) }
            if (keep != null) {
                val bContent = content.copy(farm = content.farm.copy(spreadsheetId = keep.id))
                val bProblems = writeAndVerify(authHeader, keep.id, bContent, SheetSchema.backupMeta(content.farm.farmId, spreadsheetId))
                if (bProblems.isEmpty()) { backupId = keep.id; tagBackup(authHeader, keep.id, spreadsheetId, backupName); backupNote = "backup updated" }
                else Log.w(TAG, "backup ${keep.id} not verified: $bProblems")
            }
            if (backupId == null) {
                backupId = copyAsBackup(authHeader, spreadsheetId, backupName, "backup")
                backupNote = if (backupId != null) "new backup made" else "backup could not be made (old backups kept)"
            }

            // 5) Trash the rest, only now that the sheet and the backup are both verified.
            var trashed = 0
            if (backupId != null) {
                for (f in backupFiles) if (f.id != backupId && trashFile(authHeader, f.id)) trashed++
                if (snapshotId != null && trashFile(authHeader, snapshotId)) trashed++
            }
            val from = if (reasons.isEmpty()) "already current" else "from schema $fromSchema"
            logActivity(spreadsheetId, "REPAIR",
                "Schema ${SheetSchema.VERSION} ($from) · ${content.days.size} day rows · ${merged.recoveredDays} rows recovered from ${backupData.size} backup(s) · " +
                    "${merged.fromPhone} from the phone · $backupNote · $trashed old file(s) to trash")
            Result.success(buildString {
                append(if (reasons.isEmpty()) "sheet checked" else "sheet upgraded from schema $fromSchema to ${SheetSchema.VERSION}")
                append(", ${content.days.size} day rows")
                if (merged.recoveredDays > 0) append(", ${merged.recoveredDays} newer rows recovered from backups")
                if (merged.fromPhone > 0) append(", ${merged.fromPhone} rows sent from the phone")
                append(", $backupNote")
                if (trashed > 0) append(", $trashed old file(s) moved to Drive trash")
            })
        } catch (e: Exception) {
            Log.e(TAG, "Repair failed", e)
            Result.failure(e)
        }
    }

    /**
     * Removes backup/snapshot spreadsheets that earlier builds imported as if they were farms
     * (the "duplicate farm" bug): drops them from the local cache and from the farm index.
     * Returns how many were removed.
     */
    suspend fun cleanupBackupFarms(): Int = withContext(Dispatchers.IO) {
        val authHeader = authManager.getAuthHeader() ?: return@withContext 0
        try {
            val backupIds = listBackupFiles(authHeader).map { it.id }.toMutableSet()
            // Also catch copies that lost their name/tag but say so in their _Meta.
            for (reg in db.farmRegistryDao().getAllFarmsOnce()) {
                if (reg.spreadsheetId in backupIds) continue
                val role = readMeta(authHeader, reg.spreadsheetId)["role"]
                if (role == "backup" || role == "snapshot") backupIds.add(reg.spreadsheetId)
            }
            var removed = 0
            for (id in backupIds) {
                if (db.farmRegistryDao().getFarm(id) != null) {
                    purgeLocalFarm(id)
                    removed++
                }
            }
            val index = loadIndex()
            if (index != null && index.farms.any { it.spreadsheetId in backupIds }) {
                saveIndex(index.copy(farms = index.farms.filterNot { it.spreadsheetId in backupIds }))
            }
            removed
        } catch (e: Exception) { Log.w(TAG, "cleanupBackupFarms failed: ${e.message}"); 0 }
    }

    private suspend fun purgeLocalFarm(spreadsheetId: String) {
        db.taskDao().deleteAllTasks(spreadsheetId)
        db.dailyDataDao().deleteAllDailyData(spreadsheetId)
        db.flockDao().deleteAllFlocks(spreadsheetId)
        db.feedTypeDao().deleteAllFeedTypes(spreadsheetId)
        db.configDao().deleteConfig(spreadsheetId)
        db.farmDao().deleteFarm(spreadsheetId)
        db.farmRegistryDao().deleteFarm(spreadsheetId)
    }

    /**
     * Finds or creates a dedicated "FlockIt Farms" folder in Google Drive.
     */
    suspend fun getOrCreateFlockItFolder(authHeader: String): String? = withContext(Dispatchers.IO) {
        try {
            val q = "mimeType='application/vnd.google-apps.folder' and name='$FLOCKIT_FOLDER_NAME' and trashed=false"
            val listRes = GoogleApiClientProvider.driveApi.listFiles(
                authHeader = authHeader,
                query = q,
                fields = "files(id, name)"
            )
            if (listRes.isSuccessful && !listRes.body()?.files.isNullOrEmpty()) {
                return@withContext listRes.body()!!.files!!.first().id
            }

            // Create folder
            val createRes = GoogleApiClientProvider.driveApi.createFile(
                authHeader = authHeader,
                request = CreateDriveFileRequest(
                    name = FLOCKIT_FOLDER_NAME,
                    mimeType = "application/vnd.google-apps.folder"
                )
            )
            if (createRes.isSuccessful && createRes.body() != null) {
                return@withContext createRes.body()!!.id
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting/creating FlockIt folder", e)
            null
        }
    }

    suspend fun createFarmSpreadsheet(
        farmName: String,
        farm: FarmEntity,
        config: ConfigEntity,
        feedTypes: List<FeedTypeEntity>,
        initialFlock: FlockEntity? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val authHeader = authManager.getAuthHeader()
        if (authHeader == null) {
            // Local / Demo mode: Create local farm in Room registry
            val localId = "farm_" + System.currentTimeMillis()
            val registry = FarmRegistryEntity(
                spreadsheetId = localId,
                farmName = farmName,
                role = "Owner",
                isOwner = true,
                ownerEmail = authManager.authState.value.email,
                lastOpened = System.currentTimeMillis(),
                syncStatus = "synced",
                lastSyncedAt = System.currentTimeMillis()
            )
            db.farmRegistryDao().insertOrUpdate(registry)
            db.farmDao().insertOrUpdateFarm(farm.copy(spreadsheetId = localId, farmName = farmName))
            db.configDao().insertOrUpdateConfig(config.copy(spreadsheetId = localId))
            db.feedTypeDao().insertFeedTypes(feedTypes.map { it.copy(spreadsheetId = localId) })
            if (initialFlock != null) {
                db.flockDao().insertFlock(initialFlock.copy(spreadsheetId = localId))
            }
            return@withContext Result.success(localId)
        }

        try {
            val req = CreateSpreadsheetRequest(
                properties = SpreadsheetProperties(title = "FlockIt - $farmName"),
                sheets = listOf(
                    Sheet(SheetProperties(title = "_Meta", gridProperties = GridProperties(rowCount = 20, columnCount = 5))),
                    Sheet(SheetProperties(title = "_Farm", gridProperties = GridProperties(rowCount = 50, columnCount = 5))),
                    Sheet(SheetProperties(title = "_Config", gridProperties = GridProperties(rowCount = 40, columnCount = 5))),
                    Sheet(SheetProperties(title = "_FeedTypes", gridProperties = GridProperties(rowCount = 50, columnCount = 10))),
                    Sheet(SheetProperties(title = "Flocks", gridProperties = GridProperties(rowCount = 50, columnCount = 20))),
                    Sheet(SheetProperties(title = "DailyData", gridProperties = GridProperties(rowCount = 200, columnCount = 80))),
                    Sheet(SheetProperties(title = "Tasks", gridProperties = GridProperties(rowCount = 200, columnCount = 20))),
                    Sheet(SheetProperties(title = "ActivityLog", gridProperties = GridProperties(rowCount = 200, columnCount = 5)))
                )
            )

            val res = GoogleApiClientProvider.sheetsApi.createSpreadsheet(authHeader, req)
            if (!res.isSuccessful || res.body() == null) {
                return@withContext Result.failure(Exception("Failed to create spreadsheet: ${res.code()} ${res.message()}"))
            }

            val spreadsheetId = res.body()!!.spreadsheetId

            // Move the spreadsheet into the dedicated "FlockIt Farms" folder
            val folderId = getOrCreateFlockItFolder(authHeader)
            if (folderId != null) {
                try {
                    GoogleApiClientProvider.driveApi.moveFileToFolder(
                        authHeader = authHeader,
                        fileId = spreadsheetId,
                        addParents = folderId
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Could not move file to folder: ${e.message}")
                }
            }

            // ---- Initial populate — verified RAW writes anchored at each tab's A1 ----
            val meta = listOf(
                listOf("Key", "Value"),
                listOf("app", "FlockIt"),
                listOf("schemaVersion", SheetSchema.VERSION.toString()),
                listOf("role", "primary"),
                listOf("createdAt", System.currentTimeMillis().toString()),
                listOf("farmId", farm.farmId)
            )

            val configBlock = SheetSchema.configToKV(config)

            val feedTypeBlock = mutableListOf<List<Any>>(SheetSchema.FEED_HEADERS)
            for (ft in feedTypes) feedTypeBlock.add(SheetSchema.feedRow(ft))

            val nowFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
            val activityBlock = listOf(
                listOf("Timestamp", "UserEmail", "Action", "Details"),
                listOf(nowFormatted, authManager.authState.value.email.ifBlank { "Owner" }, "CREATE_FARM", "Created farm $farmName")
            )

            val okMeta = putBlock(authHeader, spreadsheetId, "_Meta", meta)
            val okFarm = putBlock(authHeader, spreadsheetId, "_Farm", farmToKV(farm.copy(spreadsheetId = spreadsheetId, farmName = farmName)))
            putBlock(authHeader, spreadsheetId, "_Config", configBlock)
            putBlock(authHeader, spreadsheetId, "_FeedTypes", feedTypeBlock)
            val okFlocksHdr = putBlock(authHeader, spreadsheetId, "Flocks", listOf(FLOCK_HEADERS))
            val okDailyHdr = putBlock(authHeader, spreadsheetId, "DailyData",
                listOf(DAILY_HEADERS + feedTypes.sortedBy { it.sortOrder }.map { SheetSchema.usedHeader(it.code) }))
            putBlock(authHeader, spreadsheetId, "Tasks", listOf(TASK_HEADERS))
            putBlock(authHeader, spreadsheetId, "ActivityLog", activityBlock)

            if (initialFlock != null) {
                appendRows(authHeader, spreadsheetId, "Flocks",
                    listOf(flockToRow(initialFlock.copy(spreadsheetId = spreadsheetId))))
            }

            // Loud failure: if the authoritative tabs didn't take, don't pretend the farm synced.
            if (!(okMeta && okFarm && okFlocksHdr && okDailyHdr)) {
                return@withContext Result.failure(
                    Exception("Farm sheet created but could not be written. Check Google Sheets API access and that the app has the spreadsheets scope, then try again.")
                )
            }

            // Cache locally in Room
            val registry = FarmRegistryEntity(
                spreadsheetId = spreadsheetId,
                farmName = farmName,
                role = "Owner",
                isOwner = true,
                ownerEmail = authManager.authState.value.email,
                lastOpened = System.currentTimeMillis(),
                syncStatus = "synced",
                lastSyncedAt = System.currentTimeMillis()
            )
            db.farmRegistryDao().insertOrUpdate(registry)
            db.farmDao().insertOrUpdateFarm(farm.copy(spreadsheetId = spreadsheetId, farmName = farmName))
            db.configDao().insertOrUpdateConfig(config.copy(spreadsheetId = spreadsheetId))
            db.feedTypeDao().insertFeedTypes(feedTypes.map { it.copy(spreadsheetId = spreadsheetId) })
            if (initialFlock != null) {
                db.flockDao().insertFlock(initialFlock.copy(spreadsheetId = spreadsheetId))
            }

            // Control plane: record this farm in the appDataFolder index so it loads
            // on every device this user signs into.
            addFarmToIndex(spreadsheetId, farmName, role = "owner")

            Result.success(spreadsheetId)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating spreadsheet", e)
            Result.failure(e)
        }
    }

    suspend fun validateCompatibility(spreadsheetId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val authHeader = authManager.getAuthHeader() ?: return@withContext Result.success(true)
        try {
            // Primary: the _Meta app marker.
            val res = GoogleApiClientProvider.sheetsApi.batchGet(
                authHeader, spreadsheetId, listOf(a1("_Meta", "A1:B12"))
            )
            if (res.isSuccessful && !res.body()?.valueRanges.isNullOrEmpty()) {
                val rows = res.body()!!.valueRanges!![0].values ?: emptyList()
                for (row in rows) {
                    if (row.size >= 2 && row[0].toString().trim() == "app" &&
                        row[1].toString().trim().equals("FlockIt", ignoreCase = true)
                    ) return@withContext Result.success(true)
                }
            }

            // Fallback: recognise the FlockIt tab structure even if _Meta cells are blank
            // (e.g. a sheet created by an older/partial build).
            val sheetRes = GoogleApiClientProvider.sheetsApi.getSpreadsheet(authHeader, spreadsheetId)
            if (sheetRes.isSuccessful) {
                val titles = sheetRes.body()?.sheets?.mapNotNull { it.properties.title } ?: emptyList()
                if (titles.containsAll(listOf("_Meta", "DailyData", "Flocks"))) {
                    return@withContext Result.success(true)
                }
                if (sheetRes.body() == null) {
                    return@withContext Result.failure(Exception("Unable to open the spreadsheet (check access)."))
                }
            } else {
                return@withContext Result.failure(Exception("Unable to open the spreadsheet: ${sheetRes.code()}"))
            }

            Result.failure(Exception("This spreadsheet isn't a FlockIt farm."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Shares a farm with another user via Google Drive permission API.
     * Automatically triggers a Google Drive sharing notification email to the recipient.
     */
    suspend fun shareFarm(spreadsheetId: String, email: String, isEditor: Boolean): Result<Boolean> = withContext(Dispatchers.IO) {
        val authHeader = authManager.getAuthHeader()
        if (authHeader == null) {
            return@withContext Result.failure(Exception("Sign in with Google to share via Google Drive."))
        }
        try {
            val role = if (isEditor) "writer" else "reader"
            val res = GoogleApiClientProvider.driveApi.createPermission(
                authHeader = authHeader,
                fileId = spreadsheetId,
                sendNotificationEmail = true,
                request = CreatePermissionRequest(role = role, emailAddress = email)
            )
            if (res.isSuccessful) {
                logActivity(spreadsheetId, "SHARE_FARM", "Shared with $email ($role)")
                Result.success(true)
            } else {
                Result.failure(Exception("Drive sharing error: ${res.code()} ${res.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Appends an action log to the ActivityLog tab in the spreadsheet for multi-user audit trail.
     */
    suspend fun logActivity(spreadsheetId: String, action: String, details: String) = withContext(Dispatchers.IO) {
        val authHeader = authManager.getAuthHeader() ?: return@withContext
        try {
            val nowFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
            val userEmail = authManager.authState.value.email.ifBlank { "User" }
            val valueRange = ValueRange(
                range = "ActivityLog!A:D",
                values = listOf(listOf(nowFormatted, userEmail, action, details))
            )
            GoogleApiClientProvider.sheetsApi.appendValues(
                authHeader = authHeader,
                spreadsheetId = spreadsheetId,
                range = "ActivityLog!A:D",
                request = valueRange
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to append to ActivityLog: ${e.message}")
        }
    }

    /**
     * Fetches recent activity log items from the spreadsheet to show commits/updates.
     */
    suspend fun getRecentActivity(spreadsheetId: String): List<ActivityLogItem> = withContext(Dispatchers.IO) {
        val authHeader = authManager.getAuthHeader() ?: return@withContext emptyList()
        try {
            val res = GoogleApiClientProvider.sheetsApi.batchGet(
                authHeader,
                spreadsheetId,
                listOf("ActivityLog!A2:D50")
            )
            if (res.isSuccessful && !res.body()?.valueRanges.isNullOrEmpty()) {
                val rows = res.body()!!.valueRanges!![0].values ?: emptyList()
                return@withContext rows.mapNotNull { row ->
                    if (row.size >= 3) {
                        ActivityLogItem(
                            timestamp = row.getOrNull(0)?.toString() ?: "",
                            userEmail = row.getOrNull(1)?.toString() ?: "",
                            action = row.getOrNull(2)?.toString() ?: "",
                            details = row.getOrNull(3)?.toString() ?: ""
                        )
                    } else null
                }.reversed()
            }
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Automatically syncs and discovers all FlockIt farms accessible to the user on Google Drive.
     * Works both when logged out and logging in again.
     */
    suspend fun syncUserFarmsFromDrive(): Result<Int> = withContext(Dispatchers.IO) {
        val authHeader = authManager.getAuthHeader() ?: return@withContext Result.failure(Exception("Not authenticated"))
        try {
            val res = GoogleApiClientProvider.driveApi.listFiles(
                authHeader = authHeader,
                query = "mimeType='application/vnd.google-apps.spreadsheet' and trashed=false",
                fields = "files(id, name, mimeType, modifiedTime, owners, appProperties)"
            )
            if (!res.isSuccessful || res.body()?.files == null) {
                return@withContext Result.failure(Exception("Failed to query Drive: ${res.code()}"))
            }
            val files = res.body()!!.files!!
            var imported = 0
            val userEmail = authManager.authState.value.email

            for (file in files) {
                if (isBackupFile(file)) continue // backups are never farms
                val valResult = validateCompatibility(file.id)
                if (valResult.isSuccess && valResult.getOrNull() == true) {
                    val existing = db.farmRegistryDao().getFarm(file.id)
                    val isOwner = file.owners?.any { it.emailAddress.equals(userEmail, ignoreCase = true) } ?: true
                    val role = if (isOwner) "Owner" else "Editor"

                    // Clean the spreadsheet title if it contains the "FlockIt - " prefix
                    val resolvedName = file.name.removePrefix("FlockIt - ").trim().ifBlank { file.name }

                    if (existing == null) {
                        db.farmRegistryDao().insertOrUpdate(
                            FarmRegistryEntity(
                                spreadsheetId = file.id,
                                farmName = resolvedName,
                                role = role,
                                isOwner = isOwner,
                                ownerEmail = file.owners?.firstOrNull()?.emailAddress ?: userEmail,
                                lastOpened = System.currentTimeMillis(),
                                syncStatus = "synced",
                                lastSyncedAt = System.currentTimeMillis()
                            )
                        )
                        pullFarmData(file.id)
                        imported++
                    } else {
                        // Ensure farm data and flocks are pulled and refreshed
                        pullFarmData(file.id)
                    }
                }
            }
            Result.success(imported)
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing user farms from Drive", e)
            Result.failure(e)
        }
    }

    /**
     * Pulls the authoritative farm workspace from the spreadsheet into Room:
     * _Farm settings, all flock definitions, every day's INPUTS, and tasks.
     * The sheet is authoritative: every day row it holds replaces the local copy (so hand edits
     * in the sheet are never skipped), except rows with a local edit still waiting to be sent.
     * Derived values are recomputed by the caller afterwards.
     */
    suspend fun pullFarmData(spreadsheetId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val authHeader = authManager.getAuthHeader() ?: return@withContext Result.success(true)
        try {
            // Read by header name from whatever tabs the file has, so sheets from older app versions load correctly.
            val raw = readRaw(authHeader, spreadsheetId)
                ?: return@withContext Result.failure(Exception("Unable to read the farm sheet"))
            val base = db.farmDao().getFarm(spreadsheetId) ?: FarmEntity(spreadsheetId = spreadsheetId)
            val c = SheetSchema.parse(spreadsheetId, raw, base, db.configDao().getConfig(spreadsheetId))

            // --- _Farm settings (merged onto the phone's; never blanked out) ---
            var resolvedFarm = c.farm
            if (resolvedFarm.farmName.isBlank() || resolvedFarm.farmName == "Maa Tarini Farm") {
                // Fall back to the file title only if the sheet had no real name.
                val title = try {
                    GoogleApiClientProvider.sheetsApi.getSpreadsheet(authHeader, spreadsheetId)
                        .body()?.properties?.title?.removePrefix("FlockIt - ")?.trim()
                } catch (e: Exception) { null }
                val existingRegName = db.farmRegistryDao().getFarm(spreadsheetId)?.farmName
                val name = listOf(resolvedFarm.farmName.takeIf { it.isNotBlank() && it != "Maa Tarini Farm" },
                    existingRegName?.takeIf { it.isNotBlank() && !it.startsWith("Farm ") }, title)
                    .firstOrNull { !it.isNullOrBlank() } ?: ("Farm " + spreadsheetId.take(6))
                resolvedFarm = resolvedFarm.copy(farmName = name)
            }
            db.farmDao().insertOrUpdateFarm(resolvedFarm)
            db.farmRegistryDao().getFarm(spreadsheetId)?.let { reg ->
                if (reg.farmName != resolvedFarm.farmName) db.farmRegistryDao().insertOrUpdate(reg.copy(farmName = resolvedFarm.farmName))
            }
            // --- thresholds and feed types, when the sheet has them ---
            if (SheetSchema.kv(raw.config).isNotEmpty()) c.config?.let { db.configDao().insertOrUpdateConfig(it) }
            if (c.feedTypes.isNotEmpty()) db.feedTypeDao().insertFeedTypes(c.feedTypes)
            // --- flocks ---
            c.flocks.forEach { db.flockDao().insertFlock(it) }
            // --- day rows: the sheet wins for every row it has (hand edits are always picked up), except rows
            //     with a local edit not yet sent ---
            for (pulled in c.days) {
                val local = db.dailyDataDao().getDayEntry(spreadsheetId, pulled.flockId, pulled.dayNumber)
                if (local == null || !local.dirty) db.dailyDataDao().insertOrUpdateDay(pulled)
            }
            // --- tasks ---
            c.tasks.forEach { db.taskDao().insertTask(it) }

            // An older layout is brought up to date here, once (a snapshot of the old sheet stays in Drive).
            if (SheetSchema.upgradeReasons(raw).isEmpty()) layoutOk += spreadsheetId
            else runCatching { upgradeFarmFile(authHeader, spreadsheetId, raw) }
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error pulling farm data for $spreadsheetId", e)
            Result.failure(e)
        }
    }
}
