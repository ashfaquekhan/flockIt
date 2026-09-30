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
    version = 14,
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
                    .addMigrations(MIGRATION_12_13, MIGRATION_13_14)
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
