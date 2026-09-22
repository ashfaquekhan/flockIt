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
    private fun List<Any>.d(i: Int): Double? = getOrNull(i)?.toString()?.trim()?.toDoubleOrNull()
    private fun List<Any>.i(i: Int): Int? =
        getOrNull(i)?.toString()?.trim()?.let { it.toIntOrNull() ?: it.toDoubleOrNull()?.toInt() }
    private fun List<Any>.l(i: Int): Long? =
        getOrNull(i)?.toString()?.trim()?.let { it.toLongOrNull() ?: it.toDoubleOrNull()?.toLong() }
    private fun List<Any>.b(i: Int): Boolean =
        getOrNull(i)?.toString()?.trim()?.let { it.equals("true", true) || it == "1" } ?: false

    // ---- Flocks tab ----
    private val FLOCK_HEADERS = listOf(
        "flockId", "name", "breed", "startDate", "startTime", "birdsPlaced", "receptionMort",
        "targetWeight", "harvestAge", "season", "status", "locked", "deleted", "createdAt"
    )
    private fun flockToRow(f: FlockEntity): List<String> = listOf(
        f.flockId, f.name, f.breed, f.startDate, f.startTime, sv(f.birdsPlaced), sv(f.receptionMort),
        sv(f.targetWeight), sv(f.harvestAge), f.season, f.status, sv(f.locked), sv(f.deleted), sv(f.createdAt)
    )
    private fun rowToFlock(spreadsheetId: String, r: List<Any>): FlockEntity? {
        val id = r.s(0); if (id.isBlank()) return null
        return FlockEntity(
            spreadsheetId = spreadsheetId, flockId = id,
            name = r.s(1).ifBlank { "House 1" },
            breed = r.s(2).ifBlank { "Ross308" },
            startDate = r.s(3).ifBlank { "2026-01-01" },
            startTime = r.s(4).ifBlank { "08:00" },
            birdsPlaced = r.i(5) ?: 0,
            receptionMort = r.i(6) ?: 0,
            targetWeight = r.d(7) ?: 3200.0,
            harvestAge = r.i(8) ?: 42,
            season = r.s(9).ifBlank { "Monsoon" },
            status = r.s(10).ifBlank { "active" },
            locked = r.b(11),
            deleted = r.b(12),
            createdAt = r.l(13) ?: System.currentTimeMillis()
        )
    }

    // ---- DailyData tab (authoritative INPUTS only; derived values recomputed) ----
    private val DAILY_HEADERS = listOf(
        "FlockId", "Day", "Date", "Locked", "SampleEntered",
        "W1", "N1", "W2", "N2", "W3", "N3", "W4", "N4", "W5", "N5",
        "Mortality", "FeedBagsUsed", "FeedUsedType", "BirdsLifted", "WeightLifted", "LameSeparated",
        "FeedRecB1", "FeedTypeB1", "FeedRecB2", "FeedTypeB2", "FeedRecB3", "FeedTypeB3",
        "BroodingLength", "ActualFans", "ActualFanTime", "OutTemp", "OutRH", "Notes",
        "WaterTempC", "WaterPh", "FeedMoisturePct", "MeasuredCo2", "MeasuredNh3", "MeasuredO2",
        "MeasuredPressure", "MeasuredAirspeed", "PadWetMin", "PadDryMin", "LuxPerFt2", "DieselCansUsed",
        "UpdatedAt", "UpdatedBy", "Committed", "FeedUsedBreakdown"
    )
    private fun dayToRow(d: DailyDataEntity): List<String> = listOf(
        d.flockId, sv(d.dayNumber), d.date, sv(d.locked), sv(d.sampleEntered),
        sv(d.w1), sv(d.n1), sv(d.w2), sv(d.n2), sv(d.w3), sv(d.n3), sv(d.w4), sv(d.n4), sv(d.w5), sv(d.n5),
        sv(d.mortality), sv(d.feedBagsUsed), d.feedUsedType, sv(d.birdsLifted), sv(d.weightLifted), sv(d.lameSeparated),
        sv(d.feedRecB1), d.feedTypeB1, sv(d.feedRecB2), d.feedTypeB2, sv(d.feedRecB3), d.feedTypeB3,
        sv(d.broodingLength), sv(d.actualFans), sv(d.actualFanTime), sv(d.outTemp), sv(d.outRH), d.notes,
        sv(d.waterTempC), sv(d.waterPh), sv(d.feedMoisturePct), sv(d.measuredCo2), sv(d.measuredNh3), sv(d.measuredO2),
        sv(d.measuredPressure), sv(d.measuredAirspeed), sv(d.padWetMin), sv(d.padDryMin), sv(d.luxPerFt2), sv(d.dieselCansUsed),
        sv(d.updatedAt), d.updatedBy, sv(d.committed), d.feedUsedBreakdown
    )
    private fun rowToDay(spreadsheetId: String, r: List<Any>): DailyDataEntity? {
        val fId = r.s(0); if (fId.isBlank()) return null
        val day = r.i(1) ?: return null
        return DailyDataEntity(
            spreadsheetId = spreadsheetId, flockId = fId, dayNumber = day,
            date = r.s(2), locked = r.b(3), sampleEntered = r.b(4),
            w1 = r.d(5), n1 = r.i(6), w2 = r.d(7), n2 = r.i(8), w3 = r.d(9), n3 = r.i(10),
            w4 = r.d(11), n4 = r.i(12), w5 = r.d(13), n5 = r.i(14),
            mortality = r.i(15) ?: 0, feedBagsUsed = r.d(16) ?: 0.0, feedUsedType = r.s(17).ifBlank { "B1" },
            birdsLifted = r.i(18) ?: 0, weightLifted = r.d(19) ?: 0.0, lameSeparated = r.i(20) ?: 0,
            feedRecB1 = r.d(21) ?: 0.0, feedTypeB1 = r.s(22).ifBlank { "B1" },
            feedRecB2 = r.d(23) ?: 0.0, feedTypeB2 = r.s(24).ifBlank { "B2" },
            feedRecB3 = r.d(25) ?: 0.0, feedTypeB3 = r.s(26).ifBlank { "B3" },
            broodingLength = r.d(27), actualFans = r.i(28), actualFanTime = r.i(29),
            outTemp = r.d(30), outRH = r.d(31), notes = r.s(32),
            waterTempC = r.d(33), waterPh = r.d(34), feedMoisturePct = r.d(35),
            measuredCo2 = r.d(36), measuredNh3 = r.d(37), measuredO2 = r.d(38),
            measuredPressure = r.d(39), measuredAirspeed = r.d(40),
            padWetMin = r.d(41), padDryMin = r.d(42), luxPerFt2 = r.d(43),
            dieselCansUsed = r.d(44) ?: 0.0,
            updatedAt = r.l(45) ?: System.currentTimeMillis(), updatedBy = r.s(46),
            committed = r.b(47), feedUsedBreakdown = r.s(48)
        )
    }

    // ---- Tasks tab ----
    private val TASK_HEADERS = listOf(
        "taskId", "flockId", "block", "label", "time", "everyDay", "dayNumber", "createdAt",
        "startDay", "endDay", "recurrence", "everyN", "alertEnabled", "completedDays", "kind"
    )
    private fun taskToRow(t: TaskEntity): List<String> = listOf(
        t.taskId, t.flockId, t.block, t.label, t.time, sv(t.everyDay), t.dayNumber?.toString() ?: "", sv(t.createdAt),
        sv(t.startDay), sv(t.endDay), t.recurrence, sv(t.everyN), sv(t.alertEnabled), t.completedDays, t.kind
    )
    private fun rowToTask(spreadsheetId: String, r: List<Any>): TaskEntity? {
        val id = r.s(0); if (id.isBlank()) return null
        return TaskEntity(
            spreadsheetId = spreadsheetId, taskId = id, flockId = r.s(1),
            block = r.s(2), label = r.s(3), time = r.s(4),
            everyDay = r.b(5), dayNumber = r.i(6), createdAt = r.l(7) ?: System.currentTimeMillis(),
            startDay = r.i(8) ?: -1, endDay = r.i(9) ?: -1, recurrence = r.s(10),
            everyN = r.i(11) ?: 1, alertEnabled = r.b(12), completedDays = r.s(13),
            kind = r.s(14).ifBlank { "task" }
        )
    }

    // ---- _Farm tab (key/value) ----
    private fun farmToKV(farm: FarmEntity): List<List<String>> = listOf(
        listOf("Key", "Value"),
        listOf("farmName", farm.farmName), listOf("farmId", farm.farmId), listOf("houseName", farm.houseName),
        listOf("timeZone", farm.timeZone), listOf("lengthFt", sv(farm.lengthFt)), listOf("widthFt", sv(farm.widthFt)),
        listOf("heightFt", sv(farm.heightFt)), listOf("usableLengthFt", sv(farm.usableLengthFt)),
        listOf("usableWidthFt", sv(farm.usableWidthFt)), listOf("broodDensity", sv(farm.broodDensity)),
        listOf("fanCount", sv(farm.fanCount)), listOf("fanRatedCfm", sv(farm.fanRatedCfm)),
        listOf("fanDerate", sv(farm.fanDerate)), listOf("heaterCount", sv(farm.heaterCount)),
        listOf("heaterKw", sv(farm.heaterKw)), listOf("hasEC", sv(farm.hasEC)),
        listOf("padAreaFt2", sv(farm.padAreaFt2)), listOf("padEffPct", sv(farm.padEffPct)),
        listOf("padCount", sv(farm.padCount)), listOf("drinkerLines", sv(farm.drinkerLines)),
        listOf("drinkTankL", sv(farm.drinkTankL)), listOf("drinkFillMin", sv(farm.drinkFillMin)),
        listOf("nippleLineHoldL", sv(farm.nippleLineHoldL)), listOf("feederLines", sv(farm.feederLines)),
        listOf("feederLineBags", sv(farm.feederLineBags)), listOf("feederMoveMin", sv(farm.feederMoveMin)),
        listOf("pansPerFeederLine", sv(farm.pansPerFeederLine)), listOf("dieselCanL", sv(farm.dieselCanL)),
        listOf("feedBagKg", sv(farm.feedBagKg)), listOf("baseFeedings", sv(farm.baseFeedings)),
        listOf("feedDistDay", sv(farm.feedDistDay)), listOf("feedDistMid", sv(farm.feedDistMid)),
        listOf("feedDistNight", sv(farm.feedDistNight)), listOf("season", farm.season),
        listOf("weatherLat", sv(farm.weatherLat)), listOf("weatherLon", sv(farm.weatherLon)),
        listOf("weatherName", farm.weatherName), listOf("densityCapDefault", sv(farm.densityCapDefault)),
        listOf("cutoffTime", farm.cutoffTime)
    )
    private fun kvToFarm(spreadsheetId: String, rows: List<List<Any>>, base: FarmEntity): FarmEntity {
        val m = HashMap<String, String>()
        for (row in rows) if (row.size >= 2) m[row[0].toString().trim()] = row[1].toString().trim()
        fun st(k: String, d: String) = m[k]?.takeIf { it.isNotBlank() && !it.equals("null", true) } ?: d
        fun db(k: String, d: Double) = m[k]?.toDoubleOrNull() ?: d
        fun it2(k: String, d: Int) = m[k]?.let { it.toIntOrNull() ?: it.toDoubleOrNull()?.toInt() } ?: d
        fun bl(k: String, d: Boolean) = m[k]?.let { it.equals("true", true) || it == "1" } ?: d
        return base.copy(
            spreadsheetId = spreadsheetId,
            farmName = st("farmName", base.farmName), farmId = st("farmId", base.farmId),
            houseName = st("houseName", base.houseName), timeZone = st("timeZone", base.timeZone),
            lengthFt = db("lengthFt", base.lengthFt), widthFt = db("widthFt", base.widthFt),
            heightFt = db("heightFt", base.heightFt), usableLengthFt = db("usableLengthFt", base.usableLengthFt),
            usableWidthFt = db("usableWidthFt", base.usableWidthFt), broodDensity = db("broodDensity", base.broodDensity),
            fanCount = it2("fanCount", base.fanCount), fanRatedCfm = db("fanRatedCfm", base.fanRatedCfm),
            fanDerate = db("fanDerate", base.fanDerate), heaterCount = it2("heaterCount", base.heaterCount),
            heaterKw = db("heaterKw", base.heaterKw), hasEC = bl("hasEC", base.hasEC),
            padAreaFt2 = db("padAreaFt2", base.padAreaFt2), padEffPct = db("padEffPct", base.padEffPct),
            padCount = it2("padCount", base.padCount), drinkerLines = it2("drinkerLines", base.drinkerLines),
            drinkTankL = db("drinkTankL", base.drinkTankL), drinkFillMin = db("drinkFillMin", base.drinkFillMin),
            nippleLineHoldL = db("nippleLineHoldL", base.nippleLineHoldL), feederLines = it2("feederLines", base.feederLines),
            feederLineBags = it2("feederLineBags", base.feederLineBags), feederMoveMin = db("feederMoveMin", base.feederMoveMin),
            pansPerFeederLine = it2("pansPerFeederLine", base.pansPerFeederLine), dieselCanL = db("dieselCanL", base.dieselCanL),
            feedBagKg = db("feedBagKg", base.feedBagKg), baseFeedings = it2("baseFeedings", base.baseFeedings),
            feedDistDay = it2("feedDistDay", base.feedDistDay), feedDistMid = it2("feedDistMid", base.feedDistMid),
            feedDistNight = it2("feedDistNight", base.feedDistNight), season = st("season", base.season),
            weatherLat = db("weatherLat", base.weatherLat), weatherLon = db("weatherLon", base.weatherLon),
            weatherName = st("weatherName", base.weatherName), densityCapDefault = db("densityCapDefault", base.densityCapDefault),
            cutoffTime = st("cutoffTime", base.cutoffTime)
        )
    }

    /** Verified RAW write of a 2D block anchored at the top-left cell of a tab. */
    private suspend fun putBlock(
        authHeader: String, spreadsheetId: String, tabName: String, values: List<List<String>>
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
        authHeader: String, spreadsheetId: String, tabName: String, rows: List<List<String>>
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
            try {
                val okFlock = appendRows(authHeader, spreadsheetId, "Flocks", listOf(flockToRow(flock)))
                val okDays = if (days.isEmpty()) true
                    else appendRows(authHeader, spreadsheetId, "DailyData", days.map { dayToRow(it) })
                logActivity(spreadsheetId, "CREATE_FLOCK", "Added flock ${flock.name} (${flock.flockId})")
                if (okFlock && okDays) Result.success(Unit)
                else Result.failure(Exception("Sheet write failed (flock=$okFlock, days=$okDays)"))
            } catch (e: Exception) { Result.failure(e) }
        }

    /** Upserts a single day's inputs into the DailyData tab (find row by FlockId+Day, else append). */
    suspend fun pushDayEntry(spreadsheetId: String, day: DailyDataEntity): Result<Unit> =
        withContext(Dispatchers.IO) {
            val authHeader = authManager.getAuthHeader() ?: return@withContext Result.success(Unit)
            try {
                val keyRes = GoogleApiClientProvider.sheetsApi.batchGet(
                    authHeader, spreadsheetId, listOf(appendRange("DailyData", "A2:B"))
                )
                val rows = keyRes.body()?.valueRanges?.getOrNull(0)?.values ?: emptyList()
                var rowNum = -1
                for ((idx, r) in rows.withIndex()) {
                    if (r.s(0) == day.flockId && r.i(1) == day.dayNumber) { rowNum = idx + 2; break }
                }
                val ok = if (rowNum > 0) {
                    val res = GoogleApiClientProvider.sheetsApi.batchUpdateValues(
                        authHeader, spreadsheetId,
                        BatchUpdateValuesRequest(
                            valueInputOption = RAW,
                            data = listOf(ValueRange(range = a1("DailyData", "A$rowNum"), values = listOf(dayToRow(day))))
                        )
                    )
                    res.isSuccessful
                } else {
                    appendRows(authHeader, spreadsheetId, "DailyData", listOf(dayToRow(day)))
                }
                if (ok) Result.success(Unit) else Result.failure(Exception("DailyData write failed"))
            } catch (e: Exception) { Result.failure(e) }
        }

    /** Upserts a task row by taskId (update in place, else append) so edits don't duplicate rows. */
    suspend fun pushTask(spreadsheetId: String, task: TaskEntity): Result<Unit> = withContext(Dispatchers.IO) {
        val authHeader = authManager.getAuthHeader() ?: return@withContext Result.success(Unit)
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
        try {
            val ok = putBlock(authHeader, spreadsheetId, "_Farm", farmToKV(farm))
            if (ok) { logActivity(spreadsheetId, "UPDATE_FARM", "Updated farm settings"); Result.success(Unit) }
            else Result.failure(Exception("_Farm write failed"))
        } catch (e: Exception) { Result.failure(e) }
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
                    Sheet(SheetProperties(title = "Flocks", gridProperties = GridProperties(rowCount = 50, columnCount = 15))),
                    Sheet(SheetProperties(title = "DailyData", gridProperties = GridProperties(rowCount = 200, columnCount = 80))),
                    Sheet(SheetProperties(title = "Tasks", gridProperties = GridProperties(rowCount = 100, columnCount = 10))),
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
                listOf("schemaVersion", "2"),
                listOf("createdByAppVersion", "2.0"),
                listOf("createdAt", System.currentTimeMillis().toString()),
                listOf("farmId", farm.farmId)
            )

            val configBlock = listOf(
                listOf("Key", "Value"),
                listOf("tempBand", sv(config.tempBand)), listOf("rhMin", sv(config.rhMin)),
                listOf("rhMax", sv(config.rhMax)), listOf("nh3Warn", sv(config.nh3Warn)),
                listOf("nh3Crit", sv(config.nh3Crit)), listOf("co2Warn", sv(config.co2Warn)),
                listOf("co2Crit", sv(config.co2Crit)), listOf("cvWarn", sv(config.cvWarn)),
                listOf("cvCrit", sv(config.cvCrit)), listOf("wfRatio", sv(config.wfRatio)),
                listOf("feedHeatK", sv(config.feedHeatK)), listOf("waterHeatK", sv(config.waterHeatK)),
                listOf("cFcrDivisor", sv(config.cFcrDivisor)), listOf("cycleSec", sv(config.cycleSec)),
                listOf("minOnSec", sv(config.minOnSec)), listOf("tunTrigYoung", sv(config.tunTrigYoung)),
                listOf("tunTrigBig", sv(config.tunTrigBig))
            )

            val feedTypeBlock = mutableListOf<List<String>>(listOf("code", "name", "bagKg", "phase", "sortOrder"))
            for (ft in feedTypes) feedTypeBlock.add(listOf(ft.code, ft.name, sv(ft.bagKg), ft.phase, sv(ft.sortOrder)))

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
            val okDailyHdr = putBlock(authHeader, spreadsheetId, "DailyData", listOf(DAILY_HEADERS))
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
                fields = "files(id, name, mimeType, modifiedTime, owners)"
            )
            if (!res.isSuccessful || res.body()?.files == null) {
                return@withContext Result.failure(Exception("Failed to query Drive: ${res.code()}"))
            }
            val files = res.body()!!.files!!
            var imported = 0
            val userEmail = authManager.authState.value.email

            for (file in files) {
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
     * Newer local edits (by UpdatedAt) are preserved so an open doesn't clobber
     * unsynced changes. Derived values are recomputed by the caller afterwards.
     */
    suspend fun pullFarmData(spreadsheetId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val authHeader = authManager.getAuthHeader() ?: return@withContext Result.success(true)
        try {
            val ranges = listOf(
                a1("_Farm", "A1:B80"),
                appendRange("Flocks", "A1:N1000"),
                appendRange("DailyData", "A1:AW5000"),
                appendRange("Tasks", "A1:O2000")
            )
            val res = GoogleApiClientProvider.sheetsApi.batchGet(authHeader, spreadsheetId, ranges)
            if (!res.isSuccessful || res.body()?.valueRanges == null) {
                return@withContext Result.failure(Exception("Unable to read farm data: ${res.code()}"))
            }
            val vr = res.body()!!.valueRanges!!

            // --- _Farm settings (merge onto existing/default; never blank out) ---
            val farmRows = vr.getOrNull(0)?.values ?: emptyList()
            val base = db.farmDao().getFarm(spreadsheetId) ?: FarmEntity(spreadsheetId = spreadsheetId)
            var resolvedFarm = kvToFarm(spreadsheetId, farmRows, base)
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
                if (reg.farmName != resolvedFarm.farmName) {
                    db.farmRegistryDao().insertOrUpdate(reg.copy(farmName = resolvedFarm.farmName))
                }
            }

            // --- Flocks ---
            val flockRows = vr.getOrNull(1)?.values ?: emptyList()
            for (i in 1 until flockRows.size) {
                rowToFlock(spreadsheetId, flockRows[i])?.let { db.flockDao().insertFlock(it) }
            }

            // --- DailyData (inputs, last-write-wins by UpdatedAt) ---
            val dayRows = vr.getOrNull(2)?.values ?: emptyList()
            for (i in 1 until dayRows.size) {
                val pulled = rowToDay(spreadsheetId, dayRows[i]) ?: continue
                val local = db.dailyDataDao().getDayEntry(spreadsheetId, pulled.flockId, pulled.dayNumber)
                if (local == null || pulled.updatedAt >= local.updatedAt) {
                    db.dailyDataDao().insertOrUpdateDay(pulled)
                }
            }

            // --- Tasks ---
            val taskRows = vr.getOrNull(3)?.values ?: emptyList()
            for (i in 1 until taskRows.size) {
                rowToTask(spreadsheetId, taskRows[i])?.let { db.taskDao().insertTask(it) }
            }

            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error pulling farm data for $spreadsheetId", e)
            Result.failure(e)
        }
    }
}
