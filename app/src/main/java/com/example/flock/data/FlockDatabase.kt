package com.example.flock.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.flock.engine.PhysiologicalEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@Database(
    entities = [
        StandardsEntity::class,
        FarmRegistryEntity::class,
        FarmEntity::class,
        ConfigEntity::class,
        FeedTypeEntity::class,
        FlockEntity::class,
        DailyDataEntity::class,
        TaskEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class FlockDatabase : RoomDatabase() {

    abstract fun standardsDao(): StandardsDao
    abstract fun farmRegistryDao(): FarmRegistryDao
    abstract fun farmDao(): FarmDao
    abstract fun configDao(): ConfigDao
    abstract fun feedTypeDao(): FeedTypeDao
    abstract fun flockDao(): FlockDao
    abstract fun dailyDataDao(): DailyDataDao
    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile
        private var INSTANCE: FlockDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): FlockDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FlockDatabase::class.java,
                    "flockit_database"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(FlockDatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class FlockDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    seedDatabase(database)
                }
            }
        }
    }
}

suspend fun seedDatabase(db: FlockDatabase) {
    val defaultSpreadsheetId = "local_default"

    // 1. Seed Standards (Ross 308 & Cobb 500)
    val standards = PhysiologicalEngine.STANDARDS.map {
        StandardsEntity(
            day = it.day,
            bwRoss = it.bwRoss,
            bwCobb = it.bwCobb,
            dFeedRoss = it.dFeedRoss,
            dFeedCobb = it.dFeedCobb,
            cumFeedRoss = it.cumFeedRoss,
            cumFeedCobb = it.cumFeedCobb,
            fcrRoss = it.fcrRoss,
            fcrCobb = it.fcrCobb
        )
    }
    db.standardsDao().insertStandards(standards)

    // 2. Seed Farm Registry
    val registry = FarmRegistryEntity(
        spreadsheetId = defaultSpreadsheetId,
        farmName = "Maa Tarini Broiler Farm",
        role = "Owner",
        isOwner = true,
        ownerEmail = "",
        lastOpened = System.currentTimeMillis(),
        syncStatus = "synced",
        lastSyncedAt = System.currentTimeMillis()
    )
    db.farmRegistryDao().insertOrUpdate(registry)

    // 3. Seed Farm Profile
    val farm = FarmEntity(
        spreadsheetId = defaultSpreadsheetId,
        farmId = "farm_1",
        farmName = "Maa Tarini Broiler Farm",
        houseName = "House 1",
        timeZone = "Asia/Kolkata",
        lengthFt = 320.0,
        widthFt = 40.0,
        heightFt = 7.5,
        usableLengthFt = 299.0,
        usableWidthFt = 39.0,
        broodDensity = 3.7,
        fanCount = 10,
        fanRatedCfm = 25000.0,
        fanDerate = 0.20,
        heaterCount = 4,
        heaterKw = 25.0,
        hasEC = true,
        padAreaFt2 = 720.0,
        padEffPct = 80.0,
        drinkerLines = 5,
        drinkTankL = 2000.0,
        drinkFillMin = 20.0,
        feederLines = 4,
        feederLineBags = 3,
        feedBagKg = 60.0,
        baseFeedings = 4,
        feedDistDay = 40,
        feedDistMid = 15,
        feedDistNight = 45,
        season = "Monsoon",
        weatherLat = 21.16,
        weatherLon = 84.08,
        weatherName = "Jujomura, Odisha",
        densityCapDefault = 39.0,
        cutoffTime = "11:00"
    )
    db.farmDao().insertOrUpdateFarm(farm)

    // 4. Seed Config
    val config = ConfigEntity(
        spreadsheetId = defaultSpreadsheetId,
        tempBand = 1.5,
        rhMin = 50.0,
        rhMax = 70.0,
        nh3Warn = 10.0,
        nh3Crit = 20.0,
        co2Warn = 3000.0,
        co2Crit = 3500.0,
        cvWarn = 10.0,
        cvCrit = 12.0,
        wfRatio = 1.8,
        feedHeatK = 0.012,
        waterHeatK = 0.06,
        cFcrDivisor = 0.25,
        cycleSec = 300,
        minOnSec = 30,
        tunTrigYoung = 4.5,
        tunTrigBig = 3.0
    )
    db.configDao().insertOrUpdateConfig(config)

    // 5. Seed User-Defined Feed Types (_FeedTypes)
    val feedTypes = listOf(
        FeedTypeEntity(defaultSpreadsheetId, "B1", "Pre-starter", 50.0, "starter", 1),
        FeedTypeEntity(defaultSpreadsheetId, "B2", "Starter", 50.0, "grower", 2),
        FeedTypeEntity(defaultSpreadsheetId, "B3", "Finisher", 50.0, "finisher", 3)
    )
    db.feedTypeDao().insertFeedTypes(feedTypes)

    // 6. Seed Demo Tasks (6-block planner)
    val demoFlockId = "FL2609010001"
    val tasks = listOf(
        TaskEntity(defaultSpreadsheetId, "tsk_1", demoFlockId, "Morning 05:00–08:30", "Check drinkers and flush lines", "06:00", true, null),
        TaskEntity(defaultSpreadsheetId, "tsk_2", demoFlockId, "Morning 05:00–08:30", "Shed perimeter walk & cull count", "07:00", true, null),
        TaskEntity(defaultSpreadsheetId, "tsk_3", demoFlockId, "Morning 08:30–12:00", "Sample 5 locations & record weights", "09:30", true, null),
        TaskEntity(defaultSpreadsheetId, "tsk_4", demoFlockId, "Evening 12:00–16:00", "Check evaporative cooling pad & water flow", "13:00", true, null),
        TaskEntity(defaultSpreadsheetId, "tsk_5", demoFlockId, "Evening 16:00–20:00", "Feed distribution & pan leveling", "17:30", true, null),
        TaskEntity(defaultSpreadsheetId, "tsk_6", demoFlockId, "Night 20:00–00:00", "Verify night min-vent timers and set temp", "21:00", true, null)
    )
    db.taskDao().insertTasks(tasks)

    // 7. Seed Demo Flock placed 21 days ago
    val tz = TimeZone.getTimeZone("Asia/Kolkata")
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = tz }
    val cal = Calendar.getInstance(tz)
    cal.add(Calendar.DAY_OF_YEAR, -21)
    val startDateStr = sdf.format(cal.time)

    val demoFlock = FlockEntity(
        spreadsheetId = defaultSpreadsheetId,
        flockId = demoFlockId,
        name = "Batch #14 · House 1",
        breed = "Ross308",
        startDate = startDateStr,
        birdsPlaced = 15784,
        receptionMort = 0,
        targetWeight = 3200.0,
        harvestAge = 42,
        season = "Monsoon",
        status = "active"
    )
    db.flockDao().insertFlock(demoFlock)

    // Pre-create Days 0 to 42 for this flock
    val dayRows = mutableListOf<DailyDataEntity>()
    val calDay = Calendar.getInstance(tz).apply { time = cal.time }

    for (d in 0..42) {
        val dStr = sdf.format(calDay.time)
        val isDayPassed = d < 21

        var w1: Double? = null
        var n1: Int? = null
        var w2: Double? = null
        var n2: Int? = null
        var w3: Double? = null
        var n3: Int? = null
        var w4: Double? = null
        var n4: Int? = null
        var w5: Double? = null
        var n5: Int? = null
        var mort = 0
        var feedBags = 0.0
        var feedTypeUsed = if (d <= 10) "B1" else if (d <= 24) "B2" else "B3"
        var temp: Double? = null
        var rh: Double? = null
        var recB1 = 0.0
        var recB2 = 0.0
        var recB3 = 0.0

        if (d == 0) recB1 = 80.0
        if (d == 8) recB2 = 120.0
        if (d == 20) recB3 = 180.0

        when (d) {
            1 -> { w1 = 630.0; n1 = 10; mort = 52; feedBags = 3.0; temp = 30.5; rh = 68.0 }
            3 -> { w1 = 900.0; n1 = 10; mort = 35; feedBags = 5.0; temp = 29.8; rh = 65.0 }
            7 -> { w1 = 2270.0; n1 = 10; mort = 16; feedBags = 6.0; temp = 28.0; rh = 62.0 }
            10 -> { w1 = 2710.0; n1 = 10; mort = 16; feedBags = 16.0; temp = 26.5; rh = 60.0 }
            14 -> { mort = 10; feedBags = 22.0; temp = 25.0; rh = 58.0 }
            16 -> { w1 = 5000.0; n1 = 8; mort = 18; feedBags = 22.0; temp = 24.2; rh = 57.0 }
            19 -> { w1 = 3672.0; n1 = 4; mort = 6; feedBags = 28.0; temp = 23.5; rh = 58.0 }
            21 -> {
                w1 = 6042.0; n1 = 6
                w2 = 5982.0; n2 = 6
                w3 = 5994.0; n3 = 6
                w4 = 6582.0; n4 = 6
                w5 = 6594.0; n5 = 6
                mort = 5
                feedBags = 34.0
                temp = 23.0
                rh = 60.0
            }
        }

        // Bug 4 Fix: SampleEntered is ONLY true when at least one location has BOTH weight > 0 AND count > 0
        val hasSample = ((w1 ?: 0.0) > 0 && (n1 ?: 0) > 0) ||
                ((w2 ?: 0.0) > 0 && (n2 ?: 0) > 0) ||
                ((w3 ?: 0.0) > 0 && (n3 ?: 0) > 0) ||
                ((w4 ?: 0.0) > 0 && (n4 ?: 0) > 0) ||
                ((w5 ?: 0.0) > 0 && (n5 ?: 0) > 0)

        dayRows.add(
            DailyDataEntity(
                spreadsheetId = defaultSpreadsheetId,
                flockId = demoFlockId,
                dayNumber = d,
                date = dStr,
                locked = isDayPassed,
                sampleEntered = hasSample,
                w1 = w1, n1 = n1,
                w2 = w2, n2 = n2,
                w3 = w3, n3 = n3,
                w4 = w4, n4 = n4,
                w5 = w5, n5 = n5,
                mortality = mort,
                feedBagsUsed = if (d == 0) 0.0 else feedBags,
                feedUsedType = feedTypeUsed,
                feedRecB1 = recB1,
                feedTypeB1 = "B1",
                feedRecB2 = recB2,
                feedTypeB2 = "B2",
                feedRecB3 = recB3,
                feedTypeB3 = "B3",
                outTemp = temp,
                outRH = rh
            )
        )
        calDay.add(Calendar.DAY_OF_YEAR, 1)
    }

    db.dailyDataDao().insertDailyData(dayRows)
}
