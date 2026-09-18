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

@Entity(tableName = "config")
data class ConfigEntity(
    @PrimaryKey val key: String,
    val value: String,
    val note: String = ""
)

@Entity(tableName = "farm")
data class FarmEntity(
    @PrimaryKey val id: Int = 1,
    val houseName: String = "House 3",
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
    val drinkerLineHoldL: Double = 250.0,
    val feederLines: Int = 4,
    val feederLineBags: Int = 3,
    val feedBagKg: Double = 60.0,
    val birdsPerNipple: Int = 12,
    val birdsPerPan: Int = 60,
    val baseFeedings: Int = 4,
    val feedDistDay: Int = 40,
    val feedDistMid: Int = 15,
    val feedDistNight: Int = 45,
    val season: String = "Monsoon",
    val weatherLat: Double = 21.16,
    val weatherLon: Double = 84.08,
    val weatherName: String = "Jujomura, Odisha",
    val densityCapDefault: Double = 39.0
)

@Entity(tableName = "flocks")
data class FlockEntity(
    @PrimaryKey val flockId: String,
    val name: String,
    val breed: String = "Ross308",
    val startDate: String, // ISO 8601 YYYY-MM-DD
    val birdsPlaced: Int,
    val receptionMort: Int = 0,
    val targetWeight: Double = 3200.0,
    val harvestAge: Int = 42,
    val season: String = "Monsoon",
    val status: String = "active", // active, closed
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "daily_data",
    primaryKeys = ["flockId", "dayNumber"],
    indices = [Index(value = ["flockId", "dayNumber"])]
)
data class DailyDataEntity(
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
    val birdsLifted: Int = 0,
    val weightLifted: Double = 0.0,
    val lameSeparated: Int = 0,

    // Deliveries
    val feedRecB1: Double = 0.0,
    val feedTypeB1: String = "",
    val feedRecB2: Double = 0.0,
    val feedTypeB2: String = "",
    val feedRecB3: Double = 0.0,
    val feedTypeB3: String = "",

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

    // App display columns
    val occupiedFt2: Double = 0.0,
    val barricadeFt: Int = 0,
    val ftPerBird: Double = 0.0,
    val minFtPerBird: Double = 0.0,
    val airspeedFtMin: Int = 0,
    val windChillTemp: Double? = null,
    val alertLevel: String = "ok", // ok, warn, crit
    val alertText: String = "All good"
)

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey val routineId: String,
    val flockId: String = "", // empty means template for all flocks
    val dayNumber: Int? = null, // null means recurring all days
    val type: String = "task", // task, med, note
    val title: String,
    val detail: String = "",
    val time: String = "", // HH:mm
    val alarmOn: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "dismissals")
data class DismissalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val flockId: String,
    val dateISO: String,
    val itemId: String,
    val dismissedAt: Long = System.currentTimeMillis()
)
