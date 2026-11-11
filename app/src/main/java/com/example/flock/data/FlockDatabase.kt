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

/** v12 → v13: godown + brooding-equipment farm settings, and the per-row "dirty" (unsynced) flag. */
val MIGRATION_12_13 = object : androidx.room.migration.Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE farm ADD COLUMN godownBags REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE farm ADD COLUMN manualFeeders INTEGER NOT NULL DEFAULT 150")
        db.execSQL("ALTER TABLE farm ADD COLUMN manualDrinkers INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE farm ADD COLUMN nipplesPerLine INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE farm ADD COLUMN panLipCm REAL NOT NULL DEFAULT 6")
        db.execSQL("ALTER TABLE daily_data ADD COLUMN dirty INTEGER NOT NULL DEFAULT 0")
    }
}

/** v13 → v14: feeder line layout — bags to fill a line (decimal), sensor pans, pan spacing, line gap. */
val MIGRATION_13_14 = object : androidx.room.migration.Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE farm ADD COLUMN lineFillBags REAL NOT NULL DEFAULT 3")
        db.execSQL("UPDATE farm SET lineFillBags = feederLineBags WHERE feederLineBags > 0")
        db.execSQL("ALTER TABLE farm ADD COLUMN sensorPansPerLine INTEGER NOT NULL DEFAULT 2")
        db.execSQL("ALTER TABLE farm ADD COLUMN panSpacingFt REAL NOT NULL DEFAULT 2.5")
        db.execSQL("ALTER TABLE farm ADD COLUMN feederLineGapFt REAL NOT NULL DEFAULT 0")
    }
}

/** v14 → v15: individually weighed birds (true CV) and the uniformity / location-spread results. */
val MIGRATION_14_15 = object : androidx.room.migration.Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE daily_data ADD COLUMN indivWeights TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE daily_data ADD COLUMN locSpreadPct REAL")
        db.execSQL("ALTER TABLE daily_data ADD COLUMN uniformityPct REAL")
    }
}

/** v15 → v16: sample locations beyond the fifth, and how many locations a day shows. */
/** v17: the time the day's weights were saved (the projections that move with the clock start from it). */
/** v19: the front barricade of each day, and the bags that fill a line with its pans shut (the tube alone). */
val MIGRATION_18_19 = object : androidx.room.migration.Migration(18, 19) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE daily_data ADD COLUMN penStartFt REAL")
        db.execSQL("ALTER TABLE farm ADD COLUMN lineFillBagsShut REAL NOT NULL DEFAULT 0")
    }
}

/** v20: the barricades a farm has, how its floor is set, how its pads sit; each day's outside weather (lowest, average, highest). */
val MIGRATION_19_20 = object : androidx.room.migration.Migration(19, 20) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE farm ADD COLUMN barricades INTEGER NOT NULL DEFAULT 2")
        db.execSQL("ALTER TABLE farm ADD COLUMN spaceBy TEXT NOT NULL DEFAULT 'auto'")
        db.execSQL("ALTER TABLE farm ADD COLUMN padLayout TEXT NOT NULL DEFAULT 'side'")
        listOf("outTempMin", "outTempAvg", "outTempMax", "outRhMin", "outRhAvg", "outRhMax").forEach { db.execSQL("ALTER TABLE daily_data ADD COLUMN $it REAL") }
    }
}

/** v21: what the sheet held when this phone changed a value, and clashes with another phone waiting for a choice. */
val MIGRATION_20_21 = object : androidx.room.migration.Migration(20, 21) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE daily_data ADD COLUMN dirtyBase TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE daily_data ADD COLUMN conflicts TEXT NOT NULL DEFAULT ''")
    }
}

/** v22 (v58): the event log — a new table, nothing else touched. */
val MIGRATION_21_22 = object : androidx.room.migration.Migration(21, 22) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `flock_events` (`id` TEXT NOT NULL, `spreadsheetId` TEXT NOT NULL, `flockId` TEXT NOT NULL, " +
            "`day` INTEGER NOT NULL, `kind` TEXT NOT NULL, `happenedAt` INTEGER NOT NULL, `enteredAt` INTEGER NOT NULL, `enteredBy` TEXT NOT NULL, " +
            "`touches` TEXT NOT NULL, `newValues` TEXT NOT NULL, `oldValues` TEXT NOT NULL, `sent` INTEGER NOT NULL, PRIMARY KEY(`id`))")
    }
}

/** v23 (v59): each day's liftings one by one, and the males in every 10 birds placed. */
val MIGRATION_22_23 = object : androidx.room.migration.Migration(22, 23) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE daily_data ADD COLUMN liftLaps TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE farm ADD COLUMN malesIn10 INTEGER NOT NULL DEFAULT 3")
    }
}

val MIGRATION_17_18 = object : androidx.room.migration.Migration(17, 18) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE daily_data ADD COLUMN liftedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE daily_data ADD COLUMN feedings TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE daily_data ADD COLUMN dirtyFields TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_16_17 = object : androidx.room.migration.Migration(16, 17) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE daily_data ADD COLUMN weighedAt INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_15_16 = object : androidx.room.migration.Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE daily_data ADD COLUMN moreSamples TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE daily_data ADD COLUMN locCount INTEGER NOT NULL DEFAULT 0")
    }
}

@Database(
    entities = [
        StandardsEntity::class,
        FarmRegistryEntity::class,
        FarmEntity::class,
        ConfigEntity::class,
        FeedTypeEntity::class,
        FlockEntity::class,
        DailyDataEntity::class,
        TaskEntity::class,
        FlockEventEntity::class
    ],
    version = 23,
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
    abstract fun flockEventDao(): FlockEventDao

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
                    .addMigrations(MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23)
                    .fallbackToDestructiveMigration()
                    .addCallback(FlockDatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    /** v13 adds columns only, so existing flocks are kept (no wipe / re-pull). */
    private class FlockDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    seedReferenceData(database)
                }
            }
        }
    }
}

/**
 * Seeds ONLY the breed reference curves (Ross 308 / Cobb 500). No demo farm, flock or
 * tasks are created — the app starts empty so the user signs in and creates their own
 * farms and flocks. (This removes the old auto-loaded "Maa Tarini" farm that jumped to Day 21.)
 */
suspend fun seedReferenceData(db: FlockDatabase) {
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
}
