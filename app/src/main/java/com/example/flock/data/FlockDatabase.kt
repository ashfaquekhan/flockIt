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
import java.util.Date
import java.util.Locale

@Database(
    entities = [
        StandardsEntity::class,
        ConfigEntity::class,
        FarmEntity::class,
        FlockEntity::class,
        DailyDataEntity::class,
        RoutineEntity::class,
        DismissalEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class FlockDatabase : RoomDatabase() {

    abstract fun standardsDao(): StandardsDao
    abstract fun configDao(): ConfigDao
    abstract fun farmDao(): FarmDao
    abstract fun flockDao(): FlockDao
    abstract fun dailyDataDao(): DailyDataDao
    abstract fun routineDao(): RoutineDao
    abstract fun dismissalDao(): DismissalDao

    companion object {
        @Volatile
        private var INSTANCE: FlockDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): FlockDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FlockDatabase::class.java,
                    "flock_manager_database"
                )
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

    // 2. Seed Config
    val configs = listOf(
        ConfigEntity("cutoffTime", "11:00", "Hard-lock time for samples, mortality, and feed bags used"),
        ConfigEntity("wfRatio", "1.8", "Base water:feed ratio at 21°C"),
        ConfigEntity("feedHeatK", "0.012", "Feed intake falls fraction per °C above 20"),
        ConfigEntity("waterHeatK", "0.06", "Water rises fraction per °C above 20"),
        ConfigEntity("cFcrDivisor", "0.25", "cFCR slope: (2-CBW)*divisor + FCR"),
        ConfigEntity("tempBand", "1.5", "Comfort half-band (°C)"),
        ConfigEntity("tempRoc", "2", "Max temp change per day (°C)"),
        ConfigEntity("rhMin", "50", "RH min %"),
        ConfigEntity("rhMax", "70", "RH max %"),
        ConfigEntity("nh3Warn", "10", "ppm"),
        ConfigEntity("nh3Crit", "20", "ppm"),
        ConfigEntity("co2Warn", "3000", "ppm"),
        ConfigEntity("co2Crit", "3500", "ppm"),
        ConfigEntity("coWarn", "10", "ppm"),
        ConfigEntity("coCrit", "50", "ppm"),
        ConfigEntity("o2Min", "19.6", "%"),
        ConfigEntity("pressTarget", "25", "Pa"),
        ConfigEntity("pressBand", "10", "Pa"),
        ConfigEntity("cvWarn", "10", "%"),
        ConfigEntity("cvCrit", "12", "%"),
        ConfigEntity("cycleSec", "300", "Min-vent cycle seconds"),
        ConfigEntity("minOnSec", "30", "Min ON seconds"),
        ConfigEntity("tunTrigBig", "3", "Tunnel trigger over set-point, birds >= 1.5kg"),
        ConfigEntity("tunTrigYoung", "4.5", "Tunnel trigger, lighter birds"),
        ConfigEntity("waterTempIdeal", "20", "°C"),
        ConfigEntity("waterPhMin", "6.0", "pH"),
        ConfigEntity("waterPhMax", "7.5", "pH"),
        ConfigEntity("densityCapDefault", "39.0", "kg/m²")
    )
    db.configDao().insertConfig(configs)

    // 3. Seed Farm
    val defaultFarm = FarmEntity(
        id = 1,
        houseName = "House 3",
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
        drinkerLineHoldL = 250.0,
        feederLines = 4,
        feederLineBags = 3,
        feedBagKg = 60.0,
        birdsPerNipple = 12,
        birdsPerPan = 60,
        baseFeedings = 4,
        feedDistDay = 40,
        feedDistMid = 15,
        feedDistNight = 45,
        season = "Monsoon",
        weatherLat = 21.16,
        weatherLon = 84.08,
        weatherName = "Jujomura, Odisha",
        densityCapDefault = 39.0
    )
    db.farmDao().insertOrUpdateFarm(defaultFarm)

    // 4. Seed Routines
    val now = System.currentTimeMillis()
    val routines = listOf(
        RoutineEntity("rt_d_walk", "", null, "task", "Walk the house", "Cull, count mortality, check drinkers", "06:30", true, now),
        RoutineEntity("rt_d_weigh", "", null, "task", "Weigh 5 locations", "Zig-zag sample -> enter before cutoff", "08:30", true, now),
        RoutineEntity("rt_d_vit", "", null, "med", "Vitamin + electrolyte", "Heat-stress support in first drink", "07:00", true, now),
        RoutineEntity("rt_d10_exp", "", 10, "task", "Expand to full house", "Barricade removed by ~day 10-11", "09:00", true, now),
        RoutineEntity("rt_d12_pull", "", 12, "task", "Pull feed before peak", "Empty crops before midday heat", "10:00", false, now)
    )
    db.routineDao().insertRoutines(routines)

    // 5. Seed Demo Flock (House 3 · Monsoon) placed 21 days ago
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_YEAR, -21)
    val startDateStr = sdf.format(cal.time)

    val demoFlockId = "FL2609010001"
    val demoFlock = FlockEntity(
        flockId = demoFlockId,
        name = "House 3 · Monsoon",
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
    val calDay = Calendar.getInstance().apply { time = cal.time }

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
        var temp: Double? = null
        var rh: Double? = null

        // Populate realistic sample data for key days up to Day 21
        when (d) {
            1 -> { w1 = 630.0; n1 = 10; mort = 52; feedBags = 3.0; temp = 30.5; rh = 68.0 }
            3 -> { w1 = 900.0; n1 = 10; mort = 35; feedBags = 5.0; temp = 29.8; rh = 65.0 }
            7 -> { w1 = 2270.0; n1 = 10; mort = 16; feedBags = 6.0; temp = 28.0; rh = 62.0 }
            10 -> { w1 = 2710.0; n1 = 10; mort = 16; feedBags = 16.0; temp = 26.5; rh = 60.0 }
            14 -> { mort = 10; feedBags = 22.0; temp = 25.0; rh = 58.0 }
            16 -> { w1 = 5000.0; n1 = 8; mort = 18; feedBags = 22.0; temp = 24.2; rh = 57.0 }
            19 -> { w1 = 3672.0; n1 = 4; mort = 6; feedBags = 28.0; temp = 23.5; rh = 58.0 }
            21 -> {
                // 5 locations on Day 21
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

        val hasSample = (n1 ?: 0) > 0 || (n2 ?: 0) > 0 || (n3 ?: 0) > 0 || (n4 ?: 0) > 0 || (n5 ?: 0) > 0

        dayRows.add(
            DailyDataEntity(
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
                feedBagsUsed = feedBags,
                outTemp = temp,
                outRH = rh
            )
        )
        calDay.add(Calendar.DAY_OF_YEAR, 1)
    }

    db.dailyDataDao().insertDailyData(dayRows)
}
