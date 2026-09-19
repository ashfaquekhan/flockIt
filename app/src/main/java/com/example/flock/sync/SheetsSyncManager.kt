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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SheetsSyncManager(
    private val authManager: GoogleAuthManager,
    private val db: FlockDatabase
) {
    private val TAG = "SheetsSyncManager"

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
                    Sheet(SheetProperties(title = "Tasks", gridProperties = GridProperties(rowCount = 100, columnCount = 10)))
                )
            )

            val res = GoogleApiClientProvider.sheetsApi.createSpreadsheet(authHeader, req)
            if (!res.isSuccessful || res.body() == null) {
                return@withContext Result.failure(Exception("Failed to create spreadsheet: ${res.code()} ${res.message()}"))
            }

            val spreadsheetId = res.body()!!.spreadsheetId

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

            val updateReq = BatchUpdateValuesRequest(
                data = listOf(
                    ValueRange("_Meta!A1:B7", values = metaValues),
                    ValueRange("_Farm!A1:B34", values = farmValues),
                    ValueRange("_Config!A1:B19", values = configValues),
                    ValueRange("_FeedTypes!A1:E" + (feedTypes.size + 1), values = feedTypeValues),
                    ValueRange("Flocks!A1:J1", values = flocksHeaders),
                    ValueRange("DailyData!A1:BZ1", values = dailyDataHeaders),
                    ValueRange("Tasks!A1:H1", values = tasksHeaders)
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

            Result.success(spreadsheetId)
        } catch (e: Exception) {
            // Cloud create failed — never lose the farm: persist it locally so it survives
            // re-login and shows in the Farms list. It syncs to Drive later (P6).
            Log.e(TAG, "Cloud create failed; saving farm locally", e)
            val localId = "farm_" + System.currentTimeMillis()
            db.farmRegistryDao().insertOrUpdate(
                FarmRegistryEntity(
                    spreadsheetId = localId,
                    farmName = farmName,
                    role = "Owner",
                    isOwner = true,
                    ownerEmail = authManager.authState.value.email,
                    lastOpened = System.currentTimeMillis(),
                    syncStatus = "offline",
                    lastSyncedAt = System.currentTimeMillis()
                )
            )
            db.farmDao().insertOrUpdateFarm(farm.copy(spreadsheetId = localId, farmName = farmName))
            db.configDao().insertOrUpdateConfig(config.copy(spreadsheetId = localId))
            db.feedTypeDao().insertFeedTypes(feedTypes.map { it.copy(spreadsheetId = localId) })
            if (initialFlock != null) db.flockDao().insertFlock(initialFlock.copy(spreadsheetId = localId))
            Result.success(localId)
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

    suspend fun shareFarm(spreadsheetId: String, email: String, isEditor: Boolean): Result<Boolean> = withContext(Dispatchers.IO) {
        val authHeader = authManager.getAuthHeader()
        if (authHeader == null) {
            return@withContext Result.failure(Exception("Sign in with Google to share via Google Drive."))
        }
        try {
            val role = if (isEditor) "writer" else "reader"
            val res = GoogleApiClientProvider.driveApi.createPermission(
                authHeader,
                spreadsheetId,
                CreatePermissionRequest(role = role, emailAddress = email)
            )
            if (res.isSuccessful) {
                Result.success(true)
            } else {
                Result.failure(Exception("Drive sharing error: ${res.code()} ${res.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
