package com.example.flock.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "standards")
data class StandardsEntity(
    @PrimaryKey val day: Int,
    val bwRoss: Double,
    val bwCobb: Double,
    val dFeedRoss: Double,
    val dFeedCobb: Double,
    val cumFeedRoss: Double,
    val cumFeedCobb: Double,
    val fcrRoss: Double,
    val fcrCobb: Double
)

@Entity(tableName = "farm_registry")
data class FarmRegistryEntity(
    @PrimaryKey val spreadsheetId: String,
    val farmName: String,
    val role: String = "Owner", // "Owner", "Editor", "Viewer"
    val isOwner: Boolean = true,
    val ownerEmail: String = "",
    val lastOpened: Long = System.currentTimeMillis(),
    val syncStatus: String = "synced", // "synced", "syncing", "offline", "error"
    val lastSyncedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "farm")
data class FarmEntity(
    @PrimaryKey val spreadsheetId: String = "local_default",
    val farmId: String = "farm_1",
    val farmName: String = "Maa Tarini Farm",
    val houseName: String = "House 1",
    val timeZone: String = "Asia/Kolkata",
    val lengthFt: Double = 320.0,
    val widthFt: Double = 40.0,
    val heightFt: Double = 7.5,
    val usableLengthFt: Double = 299.0,
    val usableWidthFt: Double = 39.0,
    val broodDensity: Double = 3.7,
    val fanCount: Int = 10,
    val fanRatedCfm: Double = 25000.0,
    val fanDerate: Double = 0.20,
    val heaterCount: Int = 4,
    val heaterKw: Double = 25.0,
    val hasEC: Boolean = true,
    val padAreaFt2: Double = 720.0,
    val padEffPct: Double = 80.0,
    val drinkerLines: Int = 5,
    val drinkTankL: Double = 2000.0,
    val drinkFillMin: Double = 20.0,
    val feederLines: Int = 4,
    val feederLineBags: Int = 3,       // bags one feeder line holds before it moves
    val feederMoveMin: Double = 15.0,  // minutes a feeder line takes to run/move one pass
    val pansPerFeederLine: Int = 60,   // number of pans per feeder line
    val nippleLineHoldL: Double = 20.0,// litres one nipple/drinker line holds when filled
    val padCount: Int = 2,             // number of evaporative cooling pads
    val dieselCanL: Double = 20.0,     // approx litres one diesel can holds
    val feedBagKg: Double = 60.0,
    val baseFeedings: Int = 4,
    val feedDistDay: Int = 40,
    val feedDistMid: Int = 15,
    val feedDistNight: Int = 45,
    val season: String = "Monsoon",
    val weatherLat: Double = 21.16,
    val weatherLon: Double = 84.08,
    val weatherName: String = "Jujomura, Odisha",
    val densityCapDefault: Double = 39.0,
    val cutoffTime: String = "11:00"
)

@Entity(tableName = "config")
data class ConfigEntity(
    @PrimaryKey val spreadsheetId: String = "local_default",
    val tempBand: Double = 1.5,
    val rhMin: Double = 50.0,
    val rhMax: Double = 70.0,
    val nh3Warn: Double = 10.0,
    val nh3Crit: Double = 20.0,
    val co2Warn: Double = 3000.0,
    val co2Crit: Double = 3500.0,
    val cvWarn: Double = 10.0,
    val cvCrit: Double = 12.0,
    val wfRatio: Double = 1.8,
    val feedHeatK: Double = 0.012,
    val waterHeatK: Double = 0.06,
    val cFcrDivisor: Double = 0.25,
    val cycleSec: Int = 300,
    val minOnSec: Int = 30,
    val tunTrigYoung: Double = 4.5,
    val tunTrigBig: Double = 3.0
)

@Entity(
    tableName = "feed_types",
    primaryKeys = ["spreadsheetId", "code"]
)
data class FeedTypeEntity(
    val spreadsheetId: String = "local_default",
    val code: String, // "B1", "B2", "B3", "B4", etc.
    val name: String, // "Pre-starter", "Starter", "Finisher", etc.
    val bagKg: Double = 60.0,
    val phase: String = "starter", // "starter", "grower", "finisher", "custom"
    val sortOrder: Int = 1
)

@Entity(
    tableName = "flocks",
    primaryKeys = ["spreadsheetId", "flockId"]
)
data class FlockEntity(
    val spreadsheetId: String = "local_default",
    val flockId: String,
    val name: String,
    val breed: String = "Ross308",
    val startDate: String, // ISO 8601 YYYY-MM-DD
    val startTime: String = "08:00", // HH:mm the chicks were placed
    val birdsPlaced: Int,
    val receptionMort: Int = 0, // transit / reception mortality at placement
    val targetWeight: Double = 3200.0,
    val harvestAge: Int = 42,
    val season: String = "Monsoon",
    val status: String = "active", // "active", "closed"
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "daily_data",
    primaryKeys = ["spreadsheetId", "flockId", "dayNumber"],
    indices = [Index(value = ["spreadsheetId", "flockId", "dayNumber"])]
)
data class DailyDataEntity(
    val spreadsheetId: String = "local_default",
    val flockId: String,
    val dayNumber: Int,
    val date: String, // ISO 8601 YYYY-MM-DD
    val locked: Boolean = false,
    val sampleEntered: Boolean = false,

    // 5 location weight samples (total weight g + chicks counted)
    val w1: Double? = null,
    val n1: Int? = null,
    val w2: Double? = null,
    val n2: Int? = null,
    val w3: Double? = null,
    val n3: Int? = null,
    val w4: Double? = null,
    val n4: Int? = null,
    val w5: Double? = null,
    val n5: Int? = null,

    // Day inputs
    val mortality: Int = 0,
    val feedBagsUsed: Double = 0.0,
    val feedUsedType: String = "B1",
    val birdsLifted: Int = 0,
    val weightLifted: Double = 0.0,
    val lameSeparated: Int = 0,

    // Deliveries (up to 3 slots)
    val feedRecB1: Double = 0.0,
    val feedTypeB1: String = "B1",
    val feedRecB2: Double = 0.0,
    val feedTypeB2: String = "B2",
    val feedRecB3: Double = 0.0,
    val feedTypeB3: String = "B3",

    // Operational measurements
    val broodingLength: Double? = null,
    val actualFans: Int? = null,
    val actualFanTime: Int? = null,
    val outTemp: Double? = null,
    val outRH: Double? = null,
    val notes: String = "",

    // Derived values calculated by the engine
    val avgWeight: Double? = null,
    val cv: Double? = null,
    val weightAge: Double = 0.0,
    val idealWeight: Double = 0.0,
    val liveBirds: Int = 0,
    val cumMort: Int = 0,
    val cumMortPct: Double? = null,
    val livability: Double? = null,
    val densityKgM2: Double? = null,
    val setTemp: Double = 20.0,
    val feedPerBird: Double = 0.0,
    val totalFeedKg: Double = 0.0,
    val feedBags: Int = 0,
    val waterPerBird: Double = 0.0,
    val totalWaterL: Double = 0.0,
    val tankRefills: Int = 0,
    val cfmPerBird: Double = 0.0,
    val fansToRun: Int = 1,
    val fanOnSec: Int = 300,
    val fanOffSec: Int = 0,
    val ventMode: Int = 0,
    val fcr: Double? = null,
    val cFcr: Double? = null,
    val projected: Boolean = false,

    // Climate & space display columns
    val tempMin: Double = 18.5,
    val tempIdeal: Double = 20.0,
    val tempMax: Double = 21.5,
    val rhMin: Double = 50.0,
    val rhIdeal: Double = 60.0,
    val rhMax: Double = 70.0,
    val co2Max: Double = 3000.0,
    val nh3Max: Double = 10.0,
    val airspeed: Double = 0.0,
    val windChill: Double? = null,
    val lightHours: Double = 20.0,
    val maxMortPct: Double = 4.4,
    val occupiedFt2: Double = 0.0,
    val barricadeFt: Int = 0,
    val ftPerBird: Double = 0.0,
    val minFtPerBird: Double = 0.0,
    val stockOnHand: Double = 0.0,
    val ventText: String = "Minimum Ventilation",
    val cycleText: String = "Continuous",
    val alertLevel: String = "ok", // "ok", "warn", "crit"
    val alertText: String = "All targets nominal",
    val updatedAt: Long = System.currentTimeMillis(),
    val updatedBy: String = ""
)

@Entity(
    tableName = "tasks",
    primaryKeys = ["spreadsheetId", "taskId"]
)
data class TaskEntity(
    val spreadsheetId: String = "local_default",
    val taskId: String,
    val flockId: String,
    val block: String, // "Morning 05:00–08:30", "Morning 08:30–12:00", "Evening 12:00–16:00", "Evening 16:00–20:00", "Night 20:00–00:00", "Night 00:00–05:00"
    val label: String,
    val time: String, // HH:mm
    val everyDay: Boolean = true,
    val dayNumber: Int? = null, // null means every day
    val createdAt: Long = System.currentTimeMillis()
)
