package com.example.flock.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StandardsDao {
    @Query("SELECT * FROM standards ORDER BY day ASC")
    fun getAllStandards(): Flow<List<StandardsEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStandards(standards: List<StandardsEntity>)

    @Query("SELECT * FROM standards WHERE day = :day LIMIT 1")
    suspend fun getStandardForDay(day: Int): StandardsEntity?
}

@Dao
interface ConfigDao {
    @Query("SELECT * FROM config")
    fun getAllConfig(): Flow<List<ConfigEntity>>

    @Query("SELECT value FROM config WHERE `key` = :key LIMIT 1")
    suspend fun getValue(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(items: List<ConfigEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setConfig(config: ConfigEntity)
}

@Dao
interface FarmDao {
    @Query("SELECT * FROM farm WHERE id = 1 LIMIT 1")
    fun getFarmFlow(): Flow<FarmEntity?>

    @Query("SELECT * FROM farm WHERE id = 1 LIMIT 1")
    suspend fun getFarm(): FarmEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateFarm(farm: FarmEntity)
}

@Dao
interface FlockDao {
    @Query("SELECT * FROM flocks ORDER BY createdAt DESC")
    fun getAllFlocks(): Flow<List<FlockEntity>>

    @Query("SELECT * FROM flocks WHERE status = 'active' ORDER BY createdAt DESC")
    fun getActiveFlocks(): Flow<List<FlockEntity>>

    @Query("SELECT * FROM flocks WHERE flockId = :flockId LIMIT 1")
    fun getFlockByIdFlow(flockId: String): Flow<FlockEntity?>

    @Query("SELECT * FROM flocks WHERE flockId = :flockId LIMIT 1")
    suspend fun getFlockById(flockId: String): FlockEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlock(flock: FlockEntity)

    @Update
    suspend fun updateFlock(flock: FlockEntity)

    @Query("DELETE FROM flocks WHERE flockId = :flockId")
    suspend fun deleteFlock(flockId: String)
}

@Dao
interface DailyDataDao {
    @Query("SELECT * FROM daily_data WHERE flockId = :flockId ORDER BY dayNumber ASC")
    fun getDailyDataForFlock(flockId: String): Flow<List<DailyDataEntity>>

    @Query("SELECT * FROM daily_data WHERE flockId = :flockId AND dayNumber = :dayNumber LIMIT 1")
    fun getDayEntryFlow(flockId: String, dayNumber: Int): Flow<DailyDataEntity?>

    @Query("SELECT * FROM daily_data WHERE flockId = :flockId AND dayNumber = :dayNumber LIMIT 1")
    suspend fun getDayEntry(flockId: String, dayNumber: Int): DailyDataEntity?

    @Query("SELECT * FROM daily_data WHERE flockId = :flockId ORDER BY dayNumber ASC")
    suspend fun getDailyDataList(flockId: String): List<DailyDataEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyData(items: List<DailyDataEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateDay(data: DailyDataEntity)

    @Query("DELETE FROM daily_data WHERE flockId = :flockId")
    suspend fun deleteDailyDataForFlock(flockId: String)
}

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines ORDER BY time ASC")
    fun getAllRoutines(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines WHERE (flockId = '' OR flockId = :flockId) AND (dayNumber IS NULL OR dayNumber = :dayNumber) ORDER BY time ASC")
    fun getRoutinesForDay(flockId: String, dayNumber: Int): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines WHERE (flockId = '' OR flockId = :flockId) AND (dayNumber IS NULL OR dayNumber = :dayNumber) ORDER BY time ASC")
    suspend fun getRoutinesForDayList(flockId: String, dayNumber: Int): List<RoutineEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutine(routine: RoutineEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutines(routines: List<RoutineEntity>)

    @Query("UPDATE routines SET alarmOn = :alarmOn WHERE routineId = :routineId")
    suspend fun setAlarm(routineId: String, alarmOn: Boolean)

    @Query("DELETE FROM routines WHERE routineId = :routineId")
    suspend fun deleteRoutine(routineId: String)
}

@Dao
interface DismissalDao {
    @Query("SELECT * FROM dismissals WHERE flockId = :flockId AND dateISO = :dateIso")
    fun getDismissals(flockId: String, dateIso: String): Flow<List<DismissalEntity>>

    @Query("SELECT * FROM dismissals WHERE flockId = :flockId AND dateISO = :dateIso")
    suspend fun getDismissalsList(flockId: String, dateIso: String): List<DismissalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDismissal(dismissal: DismissalEntity)

    @Query("DELETE FROM dismissals WHERE flockId = :flockId AND dateISO = :dateIso AND itemId = :itemId")
    suspend fun deleteDismissal(flockId: String, dateIso: String, itemId: String)
}
