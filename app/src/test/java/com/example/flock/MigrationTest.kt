package com.example.flock

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.flock.data.FlockDatabase
import com.example.flock.data.MIGRATION_12_13
import com.example.flock.data.MIGRATION_13_14
import com.example.flock.data.MIGRATION_14_15
import com.example.flock.data.MIGRATION_15_16
import com.example.flock.data.MIGRATION_16_17
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v12 → v13 → v14 must keep the live flock: build a v12-shaped database with a farm and a day row,
 * upgrade it with the real migrations (no destructive fallback) and check everything survived.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    private val newFarmCols = listOf("godownBags", "manualFeeders", "manualDrinkers", "nipplesPerLine", "panLipCm",
        "lineFillBags", "sensorPansPerLine", "panSpacingFt", "feederLineGapFt")

    @Test
    fun upgradeKeepsData() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        // 1. Let Room create a current (v13) database to learn the table SQL.
        val probeName = "probe.db"
        ctx.deleteDatabase(probeName)
        val probe = Room.databaseBuilder(ctx, FlockDatabase::class.java, probeName).allowMainThreadQueries().build()
        probe.openHelper.writableDatabase
        val creates = mutableListOf<String>()
        probe.openHelper.readableDatabase.query("SELECT sql FROM sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata' AND name != 'room_master_table' ORDER BY type DESC").use { c ->
            while (c.moveToNext()) creates += c.getString(0)
        }
        probe.close()

        // 2. Build the v12 file: same tables without the v13 columns, user_version 12, one farm + one day.
        val oldName = "old.db"
        ctx.deleteDatabase(oldName)
        val file = ctx.getDatabasePath(oldName).also { it.parentFile?.mkdirs() }
        val raw = SQLiteDatabase.openOrCreateDatabase(file, null)
        creates.forEach { sql ->
            var s = sql
            (newFarmCols + listOf("dirty", "indivWeights", "locSpreadPct", "uniformityPct", "moreSamples", "locCount", "weighedAt")).forEach { col -> s = s.replace(Regex(",\\s*`$col`\\s+\\w+(\\s+NOT NULL)?"), "") }
            raw.execSQL(s)
        }
        raw.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
        raw.execSQL("INSERT INTO room_master_table VALUES (42, 'v12')")
        raw.execSQL("INSERT INTO farm (spreadsheetId, farmId, farmName, houseName, timeZone, lengthFt, widthFt, heightFt, usableLengthFt, usableWidthFt, broodDensity, fanCount, fanRatedCfm, fanDerate, heaterCount, heaterKw, hasEC, padAreaFt2, padEffPct, drinkerLines, drinkTankL, drinkFillMin, feederLines, feederLineBags, feederMoveMin, pansPerFeederLine, nippleLineHoldL, padCount, dieselCanL, feedBagKg, baseFeedings, feedDistDay, feedDistMid, feedDistNight, season, weatherLat, weatherLon, weatherName, densityCapDefault, cutoffTime, waterRefillFactor, minVentFactor) " +
            "VALUES ('S1','farm_1','My Farm','House 1','Asia/Kolkata',320,40,7.5,299,39,3.7,10,25000,0.2,4,25,1,720,80,5,2000,20,4,3,15,60,20,2,20,50,4,40,15,45,'Monsoon',21.1,84.0,'X',39,'11:00',1,1)")
        data class Col(val name: String, val type: String, val notNull: Boolean)
        val dayCols = mutableListOf<Col>()
        raw.rawQuery("PRAGMA table_info(daily_data)", null).use { c -> while (c.moveToNext()) dayCols += Col(c.getString(1), c.getString(2), c.getInt(3) == 1) }
        val vals = dayCols.map { col ->
            when (col.name) {
                "spreadsheetId" -> "'S1'"; "flockId" -> "'F1'"; "dayNumber" -> "9"; "date" -> "'2026-09-29'"
                "mortality" -> "7"; "feedBagsUsed" -> "12.5"; "notes" -> "'- litter turned'"
                else -> if (!col.notNull) "NULL" else if (col.type.equals("TEXT", true)) "''" else "0"
            }
        }
        raw.execSQL("INSERT INTO daily_data (${dayCols.joinToString(",") { it.name }}) VALUES (${vals.joinToString(",")})")
        raw.version = 12
        raw.close()

        // 3. Upgrade with the real migration (no destructive fallback: a bad migration fails here).
        val db = Room.databaseBuilder(ctx, FlockDatabase::class.java, oldName)
            .addMigrations(MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17).allowMainThreadQueries().build()
        val farm = db.farmDao().getFarm("S1")
        assertNotNull("farm kept", farm)
        assertEquals("My Farm", farm!!.farmName)
        assertEquals(0.0, farm.godownBags, 0.0)
        assertEquals(150, farm.manualFeeders)
        assertEquals(6.0, farm.panLipCm, 0.0)
        assertEquals(3.0, farm.lineFillBags, 0.0)      // copied from the old whole-number setting
        assertEquals(2, farm.sensorPansPerLine)
        assertEquals(2.5, farm.panSpacingFt, 0.0)
        assertEquals(0.0, farm.feederLineGapFt, 0.0)
        val day = db.dailyDataDao().getDayEntry("S1", "F1", 9)
        assertNotNull("day row kept", day)
        assertEquals(7, day!!.mortality)
        assertEquals(12.5, day.feedBagsUsed, 0.0)
        assertFalse(day.dirty)
        assertEquals("", day.indivWeights)
        assertEquals("", day.moreSamples)               // v16: extra sample locations, none yet
        assertEquals(0, day.locCount)
        assertEquals(0L, day.weighedAt)                 // v17: when the weights were saved, not known for old rows
        db.close()
    }
}
