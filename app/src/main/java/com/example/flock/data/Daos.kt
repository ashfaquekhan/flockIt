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
interface FarmRegistryDao {
    @Query("SELECT * FROM farm_registry WHERE deleted = 0 ORDER BY lastOpened DESC")
    fun getAllFarmsFlow(): Flow<List<FarmRegistryEntity>>

    @Query("SELECT * FROM farm_registry WHERE deleted = 1 ORDER BY deletedAt DESC")
    fun getDeletedFarmsFlow(): Flow<List<FarmRegistryEntity>>

    @Query("UPDATE farm_registry SET deleted = :deleted, deletedAt = :at WHERE spreadsheetId = :spreadsheetId")
    suspend fun setFarmDeleted(spreadsheetId: String, deleted: Boolean, at: Long)

    @Query("SELECT * FROM farm_registry WHERE spreadsheetId = :spreadsheetId LIMIT 1")
    suspend fun getFarm(spreadsheetId: String): FarmRegistryEntity?

    @Query("SELECT * FROM farm_registry WHERE deleted = 0")
    suspend fun getAllFarmsOnce(): List<FarmRegistryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(farm: FarmRegistryEntity)

    @Query("UPDATE farm_registry SET syncStatus = :status, lastSyncedAt = :syncedAt WHERE spreadsheetId = :spreadsheetId")
    suspend fun updateSyncStatus(spreadsheetId: String, status: String, syncedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM farm_registry WHERE spreadsheetId = :spreadsheetId")
    suspend fun deleteFarm(spreadsheetId: String)
}

@Dao
interface FarmDao {
    @Query("SELECT * FROM farm WHERE spreadsheetId = :spreadsheetId LIMIT 1")
    fun getFarmFlow(spreadsheetId: String): Flow<FarmEntity?>

    @Query("SELECT * FROM farm WHERE spreadsheetId = :spreadsheetId LIMIT 1")
    suspend fun getFarm(spreadsheetId: String): FarmEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateFarm(farm: FarmEntity)

    @Query("DELETE FROM farm WHERE spreadsheetId = :spreadsheetId")
    suspend fun deleteFarm(spreadsheetId: String)
}

@Dao
interface ConfigDao {
    @Query("SELECT * FROM config WHERE spreadsheetId = :spreadsheetId LIMIT 1")
    fun getConfigFlow(spreadsheetId: String): Flow<ConfigEntity?>

    @Query("SELECT * FROM config WHERE spreadsheetId = :spreadsheetId LIMIT 1")
    suspend fun getConfig(spreadsheetId: String): ConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateConfig(config: ConfigEntity)

    @Query("DELETE FROM config WHERE spreadsheetId = :spreadsheetId")
    suspend fun deleteConfig(spreadsheetId: String)
}

@Dao
interface FeedTypeDao {
    @Query("SELECT * FROM feed_types WHERE spreadsheetId = :spreadsheetId ORDER BY sortOrder ASC, code ASC")
    fun getFeedTypesFlow(spreadsheetId: String): Flow<List<FeedTypeEntity>>

    @Query("SELECT * FROM feed_types WHERE spreadsheetId = :spreadsheetId ORDER BY sortOrder ASC, code ASC")
    suspend fun getFeedTypes(spreadsheetId: String): List<FeedTypeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeedType(feedType: FeedTypeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeedTypes(feedTypes: List<FeedTypeEntity>)

    @Query("DELETE FROM feed_types WHERE spreadsheetId = :spreadsheetId AND code = :code")
    suspend fun deleteFeedType(spreadsheetId: String, code: String)

    @Query("DELETE FROM feed_types WHERE spreadsheetId = :spreadsheetId")
    suspend fun deleteAllFeedTypes(spreadsheetId: String)
}

@Dao
interface FlockDao {
    @Query("SELECT * FROM flocks WHERE spreadsheetId = :spreadsheetId AND deleted = 0 ORDER BY createdAt DESC")
    fun getAllFlocks(spreadsheetId: String): Flow<List<FlockEntity>>

    @Query("SELECT * FROM flocks WHERE spreadsheetId = :spreadsheetId AND status = 'active' AND deleted = 0 ORDER BY createdAt DESC")
    fun getActiveFlocks(spreadsheetId: String): Flow<List<FlockEntity>>

    @Query("SELECT * FROM flocks WHERE deleted = 1 ORDER BY deletedAt DESC")
    fun getDeletedFlocksFlow(): Flow<List<FlockEntity>>

    @Query("UPDATE flocks SET deleted = :deleted, deletedAt = :at WHERE spreadsheetId = :spreadsheetId AND flockId = :flockId")
    suspend fun setFlockDeleted(spreadsheetId: String, flockId: String, deleted: Boolean, at: Long)

    @Query("SELECT * FROM flocks WHERE spreadsheetId = :spreadsheetId AND flockId = :flockId LIMIT 1")
    fun getFlockByIdFlow(spreadsheetId: String, flockId: String): Flow<FlockEntity?>

    @Query("SELECT * FROM flocks WHERE spreadsheetId = :spreadsheetId AND flockId = :flockId LIMIT 1")
    suspend fun getFlockById(spreadsheetId: String, flockId: String): FlockEntity?

    @Query("SELECT * FROM flocks WHERE spreadsheetId = :spreadsheetId AND deleted = 0")
    suspend fun getAllFlocksList(spreadsheetId: String): List<FlockEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlock(flock: FlockEntity)

    @Update
    suspend fun updateFlock(flock: FlockEntity)

    @Query("DELETE FROM flocks WHERE spreadsheetId = :spreadsheetId AND flockId = :flockId")
    suspend fun deleteFlock(spreadsheetId: String, flockId: String)

    @Query("DELETE FROM flocks WHERE spreadsheetId = :spreadsheetId")
    suspend fun deleteAllFlocks(spreadsheetId: String)
}

@Dao
interface DailyDataDao {
    @Query("SELECT * FROM daily_data WHERE spreadsheetId = :spreadsheetId AND flockId = :flockId ORDER BY dayNumber ASC")
    fun getDailyDataForFlock(spreadsheetId: String, flockId: String): Flow<List<DailyDataEntity>>

    @Query("SELECT * FROM daily_data WHERE spreadsheetId = :spreadsheetId AND flockId = :flockId AND dayNumber = :dayNumber LIMIT 1")
    fun getDayEntryFlow(spreadsheetId: String, flockId: String, dayNumber: Int): Flow<DailyDataEntity?>

    @Query("SELECT * FROM daily_data WHERE spreadsheetId = :spreadsheetId AND flockId = :flockId AND dayNumber = :dayNumber LIMIT 1")
    suspend fun getDayEntry(spreadsheetId: String, flockId: String, dayNumber: Int): DailyDataEntity?

    @Query("SELECT * FROM daily_data WHERE spreadsheetId = :spreadsheetId AND flockId = :flockId ORDER BY dayNumber ASC")
    suspend fun getDailyDataList(spreadsheetId: String, flockId: String): List<DailyDataEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyData(items: List<DailyDataEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateDay(data: DailyDataEntity)

    @Query("DELETE FROM daily_data WHERE spreadsheetId = :spreadsheetId AND flockId = :flockId")
    suspend fun deleteDailyDataForFlock(spreadsheetId: String, flockId: String)

    @Query("DELETE FROM daily_data WHERE spreadsheetId = :spreadsheetId")
    suspend fun deleteAllDailyData(spreadsheetId: String)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE spreadsheetId = :spreadsheetId AND flockId = :flockId ORDER BY time ASC")
    fun getTasksForFlock(spreadsheetId: String, flockId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE spreadsheetId = :spreadsheetId AND flockId = :flockId AND (everyDay = 1 OR dayNumber = :dayNumber) ORDER BY time ASC")
    fun getTasksForDay(spreadsheetId: String, flockId: String, dayNumber: Int): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE spreadsheetId = :spreadsheetId AND flockId = :flockId ORDER BY time ASC")
    suspend fun getTasksList(spreadsheetId: String, flockId: String): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE spreadsheetId = :spreadsheetId AND taskId = :taskId LIMIT 1")
    suspend fun getTaskById(spreadsheetId: String, taskId: String): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskEntity>)

    @Query("DELETE FROM tasks WHERE spreadsheetId = :spreadsheetId AND taskId = :taskId")
    suspend fun deleteTask(spreadsheetId: String, taskId: String)

    @Query("DELETE FROM tasks WHERE spreadsheetId = :spreadsheetId AND flockId = :flockId")
    suspend fun deleteTasksForFlock(spreadsheetId: String, flockId: String)

    @Query("DELETE FROM tasks WHERE spreadsheetId = :spreadsheetId")
    suspend fun deleteAllTasks(spreadsheetId: String)
}
