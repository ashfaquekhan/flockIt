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
    version = 9,
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
