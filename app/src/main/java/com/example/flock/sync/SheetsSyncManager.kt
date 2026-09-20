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

            // Initial populate of tabs
            val metaValues = listOf(
                listOf("Key", "Value"),
                listOf("app", "FlockIt"),
                listOf("schemaVersion", 1),
                listOf("createdByAppVersion", "1.0"),
                listOf("createdAt", System.currentTimeMillis().toString()),
                listOf("farmId", farm.farmId)
            )

            val farmValues = listOf(
                listOf("Key", "Value"),
                listOf("farmName", farm.farmName),
                listOf("houseName", farm.houseName),
                listOf("timeZone", farm.timeZone),
                listOf("lengthFt", farm.lengthFt),
                listOf("widthFt", farm.widthFt),
                listOf("heightFt", farm.heightFt),
                listOf("usableLengthFt", farm.usableLengthFt),
                listOf("usableWidthFt", farm.usableWidthFt),
                listOf("broodDensity", farm.broodDensity),
                listOf("fanCount", farm.fanCount),
                listOf("fanRatedCfm", farm.fanRatedCfm),
                listOf("fanDerate", farm.fanDerate),
                listOf("heaterCount", farm.heaterCount),
                listOf("heaterKw", farm.heaterKw),
                listOf("hasEC", farm.hasEC),
                listOf("padAreaFt2", farm.padAreaFt2),
                listOf("padEffPct", farm.padEffPct),
                listOf("drinkerLines", farm.drinkerLines),
                listOf("drinkTankL", farm.drinkTankL),
                listOf("drinkFillMin", farm.drinkFillMin),
                listOf("feederLines", farm.feederLines),
                listOf("feederLineBags", farm.feederLineBags),
                listOf("feedBagKg", farm.feedBagKg),
                listOf("baseFeedings", farm.baseFeedings),
                listOf("feedDistDay", farm.feedDistDay),
                listOf("feedDistMid", farm.feedDistMid),
                listOf("feedDistNight", farm.feedDistNight),
                listOf("season", farm.season),
                listOf("weatherLat", farm.weatherLat),
                listOf("weatherLon", farm.weatherLon),
                listOf("weatherName", farm.weatherName),
                listOf("densityCapDefault", farm.densityCapDefault),
                listOf("cutoffTime", farm.cutoffTime)
            )

            val configValues = listOf(
                listOf("Key", "Value"),
                listOf("tempBand", config.tempBand),
                listOf("rhMin", config.rhMin),
                listOf("rhMax", config.rhMax),
                listOf("nh3Warn", config.nh3Warn),
                listOf("nh3Crit", config.nh3Crit),
                listOf("co2Warn", config.co2Warn),
                listOf("co2Crit", config.co2Crit),
                listOf("cvWarn", config.cvWarn),
                listOf("cvCrit", config.cvCrit),
                listOf("wfRatio", config.wfRatio),
                listOf("feedHeatK", config.feedHeatK),
                listOf("waterHeatK", config.waterHeatK),
                listOf("cFcrDivisor", config.cFcrDivisor),
                listOf("cycleSec", config.cycleSec),
                listOf("minOnSec", config.minOnSec),
                listOf("tunTrigYoung", config.tunTrigYoung),
                listOf("tunTrigBig", config.tunTrigBig)
            )

            val feedTypeValues = mutableListOf<List<Any>>()
            feedTypeValues.add(listOf("code", "name", "bagKg", "phase", "sortOrder"))
            for (ft in feedTypes) {
                feedTypeValues.add(listOf(ft.code, ft.name, ft.bagKg, ft.phase, ft.sortOrder))
            }

            val flocksHeaders = listOf(
                listOf("flockId", "name", "breed", "startDate", "birdsPlaced", "receptionMort", "targetWeight", "harvestAge", "status", "createdAt")
            )

            val dailyDataHeaders = listOf(
                listOf(
                    "FlockId", "Day", "Date", "Locked", "SampleEntered",
                    "W1", "N1", "W2", "N2", "W3", "N3", "W4", "N4", "W5", "N5",
                    "Mortality", "FeedBagsUsed", "FeedUsedType", "BirdsLifted", "WeightLifted", "LameSeparated",
                    "FeedRecB1", "FeedTypeB1", "FeedRecB2", "FeedTypeB2", "FeedRecB3", "FeedTypeB3",
                    "BroodingLength", "ActualFans", "ActualFanTime", "OutTemp", "OutRH", "Notes",
                    "AvgWeight", "CV", "WeightAge", "IdealWeight", "LiveBirds", "CumMort", "CumMortPct", "Livability", "DensityKgM2",
                    "SetTemp", "FeedPerBird", "TotalFeedKg", "FeedBags", "WaterPerBird", "TotalWaterL", "TankRefills",
                    "CfmPerBird", "FansToRun", "FanOnSec", "FanOffSec", "VentMode", "FCR", "cFCR", "Projected",
                    "TempMin", "TempIdeal", "TempMax", "RHMin", "RHIdeal", "RHMax", "CO2Max", "NH3Max", "Airspeed", "WindChill",
                    "LightHours", "MaxMortPct", "OccupiedFt2", "BarricadeFt", "FtPerBird", "MinFtPerBird",
                    "StockOnHand", "VentText", "CycleText", "AlertLevel", "AlertText", "UpdatedAt", "UpdatedBy"
                )
            )

            val tasksHeaders = listOf(
                listOf("taskId", "flockId", "block", "label", "time", "everyDay", "dayNumber", "createdAt")
            )

            val nowFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
            val activityHeaders = listOf(
                listOf("Timestamp", "UserEmail", "Action", "Details"),
                listOf(nowFormatted, authManager.authState.value.email.ifBlank { "Owner" }, "CREATE_FARM", "Created farm $farmName")
            )

            val updateReq = BatchUpdateValuesRequest(
                data = listOf(
                    ValueRange("_Meta!A1:B7", values = metaValues),
                    ValueRange("_Farm!A1:B34", values = farmValues),
                    ValueRange("_Config!A1:B19", values = configValues),
                    ValueRange("_FeedTypes!A1:E" + (feedTypes.size + 1), values = feedTypeValues),
                    ValueRange("Flocks!A1:J1", values = flocksHeaders),
                    ValueRange("DailyData!A1:BZ1", values = dailyDataHeaders),
                    ValueRange("Tasks!A1:H1", values = tasksHeaders),
                    ValueRange("ActivityLog!A1:D2", values = activityHeaders)
                )
            )

            GoogleApiClientProvider.sheetsApi.batchUpdateValues(authHeader, spreadsheetId, updateReq)

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
            val res = GoogleApiClientProvider.sheetsApi.batchGet(
                authHeader,
                spreadsheetId,
                listOf("_Meta!A1:B10")
            )
            if (!res.isSuccessful || res.body()?.valueRanges.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("Unable to read _Meta tab"))
            }

            val rows = res.body()!!.valueRanges!![0].values ?: emptyList()
            var isFlockIt = false
            for (row in rows) {
                if (row.size >= 2) {
                    val k = row[0].toString().trim()
                    val v = row[1].toString().trim()
                    if (k == "app" && v.equals("FlockIt", ignoreCase = true)) {
                        isFlockIt = true
                        break
                    }
                }
            }

            if (!isFlockIt) {
                Result.failure(Exception("This spreadsheet isn't a FlockIt farm."))
            } else {
                Result.success(true)
            }
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
     * Pulls farm details and flock records from the cloud spreadsheet into the local Room database.
     * Checks both _Farm and Farm_Info tabs for robust backwards compatibility.
     */
    suspend fun pullFarmData(spreadsheetId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val authHeader = authManager.getAuthHeader() ?: return@withContext Result.success(true)
        try {
            val ranges = listOf(
                "_Farm!A1:B35",
                "Farm_Info!A1:B35",
                "Flocks!A1:H50"
            )
            val res = GoogleApiClientProvider.sheetsApi.batchGet(authHeader, spreadsheetId, ranges)
            if (res.isSuccessful && res.body()?.valueRanges != null) {
                val valueRanges = res.body()!!.valueRanges!!
                val farmRows = (valueRanges.getOrNull(0)?.values?.takeIf { it.isNotEmpty() }
                    ?: valueRanges.getOrNull(1)?.values) ?: emptyList()

                // Look up existing registry entry or drive metadata before falling back
                val existingReg = db.farmRegistryDao().getFarm(spreadsheetId)
                val existingFarm = db.farmDao().getFarm(spreadsheetId)
                var resolvedFarmName = existingReg?.farmName?.takeIf { !it.startsWith("Farm ") }
                    ?: existingFarm?.farmName?.takeIf { !it.startsWith("Farm ") }

                for (row in farmRows) {
                    if (row.size >= 2 && row[0].toString().trim() == "farmName") {
                        val parsed = row[1].toString().trim()
                        if (parsed.isNotBlank() && !parsed.equals("null", ignoreCase = true)) {
                            resolvedFarmName = parsed
                        }
                    }
                }

                // If still not resolved, query spreadsheet properties title
                if (resolvedFarmName.isNullOrBlank() || resolvedFarmName.startsWith("Farm ")) {
                    try {
                        val sheetRes = GoogleApiClientProvider.sheetsApi.getSpreadsheet(authHeader, spreadsheetId)
                        val title = sheetRes.body()?.properties?.title
                        if (!title.isNullOrBlank()) {
                            resolvedFarmName = title.removePrefix("FlockIt - ").trim()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not fetch spreadsheet title: ${e.message}")
                    }
                }

                val finalFarmName = resolvedFarmName?.ifBlank { null } ?: ("Farm " + spreadsheetId.take(8))

                db.farmDao().insertOrUpdateFarm(FarmEntity(spreadsheetId = spreadsheetId, farmName = finalFarmName))
                if (existingReg != null && existingReg.farmName != finalFarmName) {
                    db.farmRegistryDao().insertOrUpdate(existingReg.copy(farmName = finalFarmName))
                }

                val flockRows = valueRanges.getOrNull(2)?.values ?: emptyList()
                if (flockRows.size > 1) {
                    for (i in 1 until flockRows.size) {
                        val r = flockRows[i]
                        if (r.isNotEmpty()) {
                            val fId = r.getOrNull(0)?.toString()?.trim() ?: continue
                            val fName = r.getOrNull(1)?.toString()?.trim() ?: "House 1"
                            val fBreed = r.getOrNull(2)?.toString()?.trim() ?: "Ross308"
                            val fStart = r.getOrNull(3)?.toString()?.trim() ?: "2026-01-01"
                            val fPlaced = r.getOrNull(4)?.toString()?.toIntOrNull() ?: 10000
                            val fTransit = r.getOrNull(5)?.toString()?.toIntOrNull() ?: 0
                            val fHarvest = r.getOrNull(6)?.toString()?.toIntOrNull() ?: 42
                            val fStatus = r.getOrNull(7)?.toString()?.trim() ?: "active"

                            db.flockDao().insertFlock(
                                FlockEntity(
                                    spreadsheetId = spreadsheetId,
                                    flockId = fId,
                                    name = fName,
                                    breed = fBreed,
                                    startDate = fStart,
                                    birdsPlaced = fPlaced,
                                    receptionMort = fTransit,
                                    harvestAge = fHarvest,
                                    status = fStatus
                                )
                            )
                        }
                    }
                }
            }
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error pulling farm data for $spreadsheetId", e)
            Result.failure(e)
        }
    }
}
