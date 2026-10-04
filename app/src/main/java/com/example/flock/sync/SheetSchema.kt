package com.example.flock.sync

import com.example.flock.data.ConfigEntity
import com.example.flock.data.DailyDataEntity
import com.example.flock.data.FarmEntity
import com.example.flock.data.FeedTypeEntity
import com.example.flock.data.FlockEntity
import com.example.flock.data.TaskEntity
import com.example.flock.data.filledSamples
import kotlin.math.min

/**
 * The farm spreadsheet's layout (schema [VERSION]) and how every older layout is read.
 *
 * Tables are read by their header names — case, spaces and underscores ignored, older names
 * accepted — never by column position, so a sheet written by any earlier app version (columns
 * added, moved or renamed, or no header row at all) loads into the right fields. Columns the app
 * doesn't know are kept at the right, under their own names, when a tab is rewritten.
 */
object SheetSchema {
    /**
     * 4: tables read by header, every tab present, flock deletedAt, feeder-layout farm keys (v34).
     * 5: numbers and true/false written as real cell values (no text with a leading '), one "Used <code>"
     *    column of bags per feed variety, individually weighed birds.
     * 6: DailyData laid out to be read by a person — the "Used <code>" columns sit beside FeedBagsUsed, any
     *    number of sample locations (W6 / N6 … beside W5 / N5) with a Locations count — and two report tabs
     *    the app keeps up to date: FeedLedger (received / used / in store by variety, with running totals)
     *    and DailySummary (birds, deaths, weight, feed and FCR with running totals).
     */
    const val VERSION = 6

    val FLOCK_HEADERS = listOf(
        "flockId", "name", "breed", "startDate", "startTime", "birdsPlaced", "receptionMort",
        "targetWeight", "harvestAge", "season", "status", "locked", "deleted", "createdAt", "deletedAt"
    )
    val DAILY_HEADERS = listOf(
        "FlockId", "Day", "Date", "Locked", "SampleEntered",
        "W1", "N1", "W2", "N2", "W3", "N3", "W4", "N4", "W5", "N5",
        "Mortality", "FeedBagsUsed", "FeedUsedType", "BirdsLifted", "WeightLifted", "LameSeparated",
        "FeedRecB1", "FeedTypeB1", "FeedRecB2", "FeedTypeB2", "FeedRecB3", "FeedTypeB3",
        "BroodingLength", "ActualFans", "ActualFanTime", "OutTemp", "OutRH", "Notes",
        "WaterTempC", "WaterPh", "FeedMoisturePct", "MeasuredCo2", "MeasuredNh3", "MeasuredO2",
        "MeasuredPressure", "MeasuredAirspeed", "PadWetMin", "PadDryMin", "LuxPerFt2", "DieselCansUsed",
        "UpdatedAt", "UpdatedBy", "Committed", "FeedUsedBreakdown", "SavedFields", "IndividualWeights", "Locations"
    )

    // ---- how DailyData is written (schema 6): related columns side by side, per-variety and extra-location columns in place
    private val LAYOUT_A = listOf("FlockId", "Day", "Date", "Locked", "SampleEntered", "Locations",
        "W1", "N1", "W2", "N2", "W3", "N3", "W4", "N4", "W5", "N5")
    private val LAYOUT_B = listOf("IndividualWeights", "Mortality", "BirdsLifted", "WeightLifted", "LameSeparated", "FeedBagsUsed")
    private val LAYOUT_C = listOf("FeedUsedType", "FeedUsedBreakdown",
        "FeedRecB1", "FeedTypeB1", "FeedRecB2", "FeedTypeB2", "FeedRecB3", "FeedTypeB3", "DieselCansUsed", "Notes",
        "BroodingLength", "ActualFans", "ActualFanTime", "OutTemp", "OutRH",
        "WaterTempC", "WaterPh", "FeedMoisturePct", "MeasuredCo2", "MeasuredNh3", "MeasuredO2",
        "MeasuredPressure", "MeasuredAirspeed", "PadWetMin", "PadDryMin", "LuxPerFt2",
        "UpdatedAt", "UpdatedBy", "Committed", "SavedFields")
    /** The DailyData header for these feed varieties and this many sample locations. */
    fun dailyHeader(codes: List<String>, locations: Int): List<String> =
        LAYOUT_A + (6..locations.coerceIn(5, com.example.flock.data.MAX_LOCATIONS)).flatMap { listOf("W$it", "N$it") } +
            LAYOUT_B + codes.map { usedHeader(it) } + LAYOUT_C

    /** "W6" / "N7" … → the location number (6 and up); null for anything else. */
    fun sampleIndex(name: String): Int? =
        Regex("^[wn](\\d{1,2})$").find(norm(name))?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 6..com.example.flock.data.MAX_LOCATIONS }
    private fun isSampleCol(name: String) = sampleIndex(name) != null
    /** A column the app writes that isn't one of the fixed fields. */
    fun isDynamicColumn(name: String) = isUsedCol(name) || isSampleCol(name)
    /** Bags of one feed variety used that day — one column per variety, after the fixed columns. */
    fun usedHeader(code: String) = "Used $code"
    fun isUsedColumn(name: String) = isUsedCol(name)
    private fun isUsedCol(name: String) = name.trim().startsWith("Used", ignoreCase = true) && norm(name).length > 4 && norm(name).startsWith("used") &&
        DAILY_HEADERS.none { norm(it) == norm(name) }
    private fun usedCode(name: String) = name.trim().drop(4).trim().trimStart('_', '-', ':').trim()
    val TASK_HEADERS = listOf(
        "taskId", "flockId", "block", "label", "time", "everyDay", "dayNumber", "createdAt",
        "startDay", "endDay", "recurrence", "everyN", "alertEnabled", "completedDays", "kind"
    )
    val FEED_HEADERS = listOf("code", "name", "bagKg", "phase", "sortOrder")
    val ACTIVITY_HEADERS = listOf("Timestamp", "UserEmail", "Action", "Details")
    /** Every tab a farm file has, in order. */
    val TABS = listOf("_Meta", "_Farm", "_Config", "_FeedTypes", "Flocks", "DailyData", "Tasks", "ActivityLog")

    fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /** Older or hand-typed names for a column (normalised). Only used when the exact name is absent. */
    private val ALIASES: Map<String, List<String>> = mapOf(
        "flockid" to listOf("flock", "batchid", "batch"),
        "day" to listOf("daynumber", "dayno", "ageday", "age"),
        "date" to listOf("entrydate", "daydate"),
        "sampleentered" to listOf("sample", "sampled"),
        "mortality" to listOf("mort", "deaths", "dead", "mortalitytoday"),
        "feedbagsused" to listOf("feedused", "bagsused", "feedbags", "feedusedbags"),
        "feedusedtype" to listOf("feedtype", "feedcode", "feedusedcode"),
        "birdslifted" to listOf("lifted", "birdssold", "sold"),
        "weightlifted" to listOf("liftedkg", "weightliftedkg", "liftedweight"),
        "lameseparated" to listOf("culls", "lame", "lameculls"),
        "notes" to listOf("note", "remarks", "comments"),
        "dieselcansused" to listOf("diesel", "dieselcans"),
        "updatedat" to listOf("updated", "lastupdated", "modifiedat"),
        "updatedby" to listOf("user", "useremail", "modifiedby"),
        "feedusedbreakdown" to listOf("feedbreakdown", "breakdown"),
        "savedfields" to listOf("saved", "lockedfields"),
        "birdsplaced" to listOf("placed", "chicksplaced", "chicks"),
        "receptionmort" to listOf("receptionmortality", "transitmort", "doa"),
        "startdate" to listOf("placementdate", "placedon"),
        "starttime" to listOf("placementtime"),
        "targetweight" to listOf("targetbw", "targetg"),
        "harvestage" to listOf("harvestday", "sellage"),
        "flockname" to listOf("name"),
        "name" to listOf("flockname", "feedname"),
        "taskid" to listOf("id"),
        "label" to listOf("task", "title"),
        "time" to listOf("at", "hhmm"),
        "bagkg" to listOf("bagweight", "kgperbag", "bagkgs"),
        "sortorder" to listOf("order", "sort")
    )

    /** A header row: finds a field's column by name (or an older name). */
    class Header(cells: List<Any?>) {
        val names: List<String> = cells.map { it?.toString()?.trim().orEmpty() }
        private val index = HashMap<String, Int>().also { m -> names.forEachIndexed { i, n -> val k = norm(n); if (k.isNotEmpty() && k !in m) m[k] = i } }
        fun col(field: String): Int? { val k = norm(field); return index[k] ?: ALIASES[k]?.firstNotNullOfOrNull { index[it] } }
        /** Columns that match none of [fields] (kept at the right when the tab is rewritten). */
        fun extras(fields: List<String>): List<Int> {
            val used = fields.mapNotNull { col(it) }.toSet()
            return names.indices.filter { it !in used && norm(names[it]).isNotEmpty() && !isUsedCol(names[it]) && !isSampleCol(names[it]) }
        }
    }

    /** One cell reader over a row, by field name. Numbers tolerate hand edits ("1,200", "12.5 kg"). */
    class Row(val cells: List<Any?>, private val h: Header) {
        fun raw(field: String): Any? = h.col(field)?.let { cells.getOrNull(it) }
        fun s(field: String): String = raw(field)?.toString()?.trim().orEmpty()
        fun d(field: String): Double? = num(raw(field))
        fun i(field: String): Int? = d(field)?.let { kotlin.math.round(it).toInt() }
        fun l(field: String): Long? = raw(field)?.toString()?.trim()?.let { it.toLongOrNull() ?: num(it)?.toLong() }
        fun b(field: String): Boolean = s(field).let { it.equals("true", true) || it == "1" || it.equals("yes", true) }
        fun cell(i: Int): String = cells.getOrNull(i)?.toString().orEmpty()
        fun cellRaw(i: Int): Any = cells.getOrNull(i) ?: ""
        /** Bags per feed variety from the "Used <code>" columns (empty when the sheet has none). */
        fun usedByCode(): Map<String, Double> = h.names.withIndex().filter { isUsedCol(it.value) }
            .mapNotNull { (i, n) -> num(cells.getOrNull(i))?.let { usedCode(n) to it } }.filter { it.first.isNotBlank() }.toMap()
        /** Sample locations 6 and up from the "W6" / "N6" … columns, in "w:n;w:n" form. */
        fun moreSamples(): String {
            val w = HashMap<Int, Double?>(); val n = HashMap<Int, Int?>()
            h.names.forEachIndexed { i, name ->
                val k = sampleIndex(name) ?: return@forEachIndexed
                val v = num(cells.getOrNull(i))
                if (norm(name).startsWith("w")) w[k] = v else n[k] = v?.let { kotlin.math.round(it).toInt() }
            }
            val top = (w.filterValues { it != null }.keys + n.filterValues { it != null }.keys).maxOrNull() ?: return ""
            return com.example.flock.data.formatMoreSamples((6..top).map { w[it] to n[it] })
        }
    }

    fun num(raw: Any?): Double? {
        if (raw is Number) return raw.toDouble()
        val t = raw?.toString()?.trim()?.replace(",", "")?.replace(" ", "") ?: return null
        if (t.isEmpty()) return null
        return t.toDoubleOrNull() ?: Regex("^-?\\d+(\\.\\d+)?").find(t)?.value?.toDoubleOrNull()
    }

    /** A table tab as read: its header and data rows. A tab whose first row isn't a header is read in the current column order. */
    class Tab(val header: Header, val rows: List<List<Any?>>, val hadHeader: Boolean) {
        fun rowsOf(): List<Row> = rows.map { Row(it, header) }
    }

    fun tab(values: List<List<Any?>>?, fields: List<String>): Tab {
        val v = values.orEmpty()
        if (v.isEmpty()) return Tab(Header(fields), emptyList(), false)
        val h = Header(v[0])
        val hits = fields.count { h.col(it) != null }
        return if (hits >= min(2, fields.size)) Tab(h, v.drop(1), true) else Tab(Header(fields), v, false)
    }

    // ------------------------------------------------------------------ rows ↔ entities

    fun flock(sid: String, r: Row): FlockEntity? {
        val id = r.s("flockId"); if (id.isBlank()) return null
        return FlockEntity(
            spreadsheetId = sid, flockId = id,
            name = r.s("name").ifBlank { "House 1" }, breed = r.s("breed").ifBlank { "Ross308" },
            startDate = r.s("startDate").ifBlank { "2026-01-01" }, startTime = r.s("startTime").ifBlank { "08:00" },
            birdsPlaced = r.i("birdsPlaced") ?: 0, receptionMort = r.i("receptionMort") ?: 0,
            targetWeight = r.d("targetWeight") ?: 3200.0, harvestAge = r.i("harvestAge") ?: 42,
            season = r.s("season").ifBlank { "Monsoon" }, status = r.s("status").ifBlank { "active" },
            locked = r.b("locked"), deleted = r.b("deleted"),
            createdAt = r.l("createdAt") ?: System.currentTimeMillis(), deletedAt = r.l("deletedAt") ?: 0L
        )
    }
    fun flockRow(f: FlockEntity): List<Any> = listOf(
        f.flockId, f.name, f.breed, f.startDate, f.startTime, f.birdsPlaced, f.receptionMort,
        f.targetWeight, f.harvestAge, f.season, f.status, f.locked, f.deleted, f.createdAt, f.deletedAt
    )

    fun day(sid: String, r: Row): DailyDataEntity? {
        val fId = r.s("FlockId"); if (fId.isBlank()) return null
        val dayNo = r.i("Day") ?: return null
        val d = DailyDataEntity(
            spreadsheetId = sid, flockId = fId, dayNumber = dayNo,
            date = r.s("Date"), locked = r.b("Locked"), sampleEntered = r.b("SampleEntered"),
            w1 = r.d("W1"), n1 = r.i("N1"), w2 = r.d("W2"), n2 = r.i("N2"), w3 = r.d("W3"), n3 = r.i("N3"),
            w4 = r.d("W4"), n4 = r.i("N4"), w5 = r.d("W5"), n5 = r.i("N5"),
            mortality = r.i("Mortality") ?: 0, feedBagsUsed = r.d("FeedBagsUsed") ?: 0.0, feedUsedType = r.s("FeedUsedType").ifBlank { "B1" },
            birdsLifted = r.i("BirdsLifted") ?: 0, weightLifted = r.d("WeightLifted") ?: 0.0, lameSeparated = r.i("LameSeparated") ?: 0,
            feedRecB1 = r.d("FeedRecB1") ?: 0.0, feedTypeB1 = r.s("FeedTypeB1").ifBlank { "B1" },
            feedRecB2 = r.d("FeedRecB2") ?: 0.0, feedTypeB2 = r.s("FeedTypeB2").ifBlank { "B2" },
            feedRecB3 = r.d("FeedRecB3") ?: 0.0, feedTypeB3 = r.s("FeedTypeB3").ifBlank { "B3" },
            broodingLength = r.d("BroodingLength"), actualFans = r.i("ActualFans"), actualFanTime = r.i("ActualFanTime"),
            outTemp = r.d("OutTemp"), outRH = r.d("OutRH"), notes = r.s("Notes"),
            waterTempC = r.d("WaterTempC"), waterPh = r.d("WaterPh"), feedMoisturePct = r.d("FeedMoisturePct"),
            measuredCo2 = r.d("MeasuredCo2"), measuredNh3 = r.d("MeasuredNh3"), measuredO2 = r.d("MeasuredO2"),
            measuredPressure = r.d("MeasuredPressure"), measuredAirspeed = r.d("MeasuredAirspeed"),
            padWetMin = r.d("PadWetMin"), padDryMin = r.d("PadDryMin"), luxPerFt2 = r.d("LuxPerFt2"),
            dieselCansUsed = r.d("DieselCansUsed") ?: 0.0,
            updatedAt = r.l("UpdatedAt") ?: 0L, updatedBy = r.s("UpdatedBy"),
            committed = r.b("Committed"), feedUsedBreakdown = r.s("FeedUsedBreakdown"), savedFields = r.s("SavedFields"),
            indivWeights = r.s("IndividualWeights"),
            moreSamples = r.moreSamples(), locCount = (r.i("Locations") ?: 0).coerceIn(0, com.example.flock.data.MAX_LOCATIONS)
        )
        // The per-variety columns are the bags record when the sheet has them: they set the split and the total.
        val perType = r.usedByCode().filterValues { it > 0 }
        if (perType.isNotEmpty()) return finishDay(d.copy(
            feedUsedBreakdown = perType.entries.joinToString(";") { "${it.key}=${it.value}" },
            feedBagsUsed = perType.values.sum(), feedUsedType = perType.maxByOrNull { it.value }!!.key))
        return finishDay(d)
    }

    private fun finishDay(d: DailyDataEntity): DailyDataEntity {
        // Hand edits in the sheet: if FeedBagsUsed was changed but the per-type breakdown wasn't, scale it to the new total.
        val parts = com.example.flock.data.parseFeedBreakdown(d.feedUsedBreakdown)
        val partSum = parts.sumOf { it.second }
        val breakdown = when {
            parts.isEmpty() -> d.feedUsedBreakdown
            d.feedBagsUsed <= 0.0 -> ""
            kotlin.math.abs(partSum - d.feedBagsUsed) < 0.001 -> d.feedUsedBreakdown
            partSum > 0 -> parts.joinToString(";") { (c, b) -> "$c=${b * d.feedBagsUsed / partSum}" }
            else -> "${d.feedUsedType}=${d.feedBagsUsed}"
        }
        val hasSample = d.filledSamples().isNotEmpty() || d.indivWeights.isNotBlank()
        return d.copy(feedUsedBreakdown = breakdown, sampleEntered = hasSample)
    }
    fun dayRow(d: DailyDataEntity): List<Any> = listOf(
        d.flockId, d.dayNumber, d.date, d.locked, d.sampleEntered,
        n(d.w1), n(d.n1), n(d.w2), n(d.n2), n(d.w3), n(d.n3), n(d.w4), n(d.n4), n(d.w5), n(d.n5),
        d.mortality, d.feedBagsUsed, d.feedUsedType, d.birdsLifted, d.weightLifted, d.lameSeparated,
        d.feedRecB1, d.feedTypeB1, d.feedRecB2, d.feedTypeB2, d.feedRecB3, d.feedTypeB3,
        n(d.broodingLength), n(d.actualFans), n(d.actualFanTime), n(d.outTemp), n(d.outRH), d.notes,
        n(d.waterTempC), n(d.waterPh), n(d.feedMoisturePct), n(d.measuredCo2), n(d.measuredNh3), n(d.measuredO2),
        n(d.measuredPressure), n(d.measuredAirspeed), n(d.padWetMin), n(d.padDryMin), n(d.luxPerFt2), d.dieselCansUsed,
        d.updatedAt, d.updatedBy, d.committed, d.feedUsedBreakdown, d.savedFields, d.indivWeights,
        if (d.locCount > 0) d.locCount else ""
    )
    /** Sample locations 6 and up as column name → value. */
    fun sampleColumns(d: DailyDataEntity): Map<String, Any> = buildMap {
        com.example.flock.data.parseMoreSamples(d.moreSamples).forEachIndexed { i, (wt, cnt) -> put("W${i + 6}", n(wt)); put("N${i + 6}", n(cnt)) }
    }
    /** How many sample locations a day row needs columns for (5, or up to its last extra location). */
    fun locationsOf(d: DailyDataEntity): Int = 5 + com.example.flock.data.parseMoreSamples(d.moreSamples).size
    /** Bags per variety a day row used (from its split, else its one type). */
    fun usedSplit(d: DailyDataEntity): Map<String, Double> {
        val b = com.example.flock.data.parseFeedBreakdown(d.feedUsedBreakdown)
        if (b.isNotEmpty()) return b.groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
        return if (d.feedBagsUsed > 0) mapOf(d.feedUsedType.ifBlank { "B1" } to d.feedBagsUsed) else emptyMap()
    }
    /** Every value of a day row keyed by its normalised column name, per-variety columns included. */
    fun dayValues(d: DailyDataEntity): Map<String, Any> =
        DAILY_HEADERS.map { norm(it) }.zip(dayRow(d)).toMap() + usedSplit(d).mapKeys { norm(usedHeader(it.key)) } +
            sampleColumns(d).mapKeys { norm(it.key) }
    /** A day row laid out for [header] (a column the row has no value for is left blank). */
    fun dayCells(d: DailyDataEntity, header: List<String>): List<Any> = dayValues(d).let { v -> header.map { v[norm(it)] ?: "" } }
    fun dayKey(d: DailyDataEntity) = d.flockId + "|" + d.dayNumber

    fun task(sid: String, r: Row): TaskEntity? {
        val id = r.s("taskId"); if (id.isBlank()) return null
        return TaskEntity(
            spreadsheetId = sid, taskId = id, flockId = r.s("flockId"),
            block = r.s("block"), label = r.s("label"), time = r.s("time"),
            everyDay = r.b("everyDay"), dayNumber = r.i("dayNumber"), createdAt = r.l("createdAt") ?: System.currentTimeMillis(),
            startDay = r.i("startDay") ?: -1, endDay = r.i("endDay") ?: -1, recurrence = r.s("recurrence"),
            everyN = r.i("everyN") ?: 1, alertEnabled = r.b("alertEnabled"), completedDays = r.s("completedDays"),
            kind = r.s("kind").ifBlank { "task" }
        )
    }
    fun taskRow(t: TaskEntity): List<Any> = listOf(
        t.taskId, t.flockId, t.block, t.label, t.time, t.everyDay, n(t.dayNumber), t.createdAt,
        t.startDay, t.endDay, t.recurrence, t.everyN, t.alertEnabled, t.completedDays, t.kind
    )

    fun feedType(sid: String, r: Row): FeedTypeEntity? {
        val code = r.s("code"); if (code.isBlank()) return null
        return FeedTypeEntity(spreadsheetId = sid, code = code, name = r.s("name").ifBlank { code },
            bagKg = r.d("bagKg") ?: 50.0, phase = r.s("phase").ifBlank { "custom" }, sortOrder = r.i("sortOrder") ?: 1)
    }
    fun feedRow(f: FeedTypeEntity): List<Any> = listOf(f.code, f.name, f.bagKg, f.phase, f.sortOrder)

    // ------------------------------------------------------------------ key/value tabs

    fun kv(rows: List<List<Any?>>?): Map<String, String> {
        val m = LinkedHashMap<String, String>()
        for (row in rows.orEmpty()) if (row.size >= 2) { val k = row[0]?.toString()?.trim().orEmpty(); if (k.isNotEmpty() && !k.equals("Key", true)) m[k] = row[1]?.toString()?.trim().orEmpty() }
        return m
    }

    fun farmToKV(farm: FarmEntity): List<List<Any>> = typedKV(farmToKVText(farm))
    private fun farmToKVText(farm: FarmEntity): List<List<String>> = listOf(
        listOf("Key", "Value"),
        listOf("farmName", farm.farmName), listOf("farmId", farm.farmId), listOf("houseName", farm.houseName),
        listOf("timeZone", farm.timeZone), listOf("lengthFt", sv(farm.lengthFt)), listOf("widthFt", sv(farm.widthFt)),
        listOf("heightFt", sv(farm.heightFt)), listOf("usableLengthFt", sv(farm.usableLengthFt)),
        listOf("usableWidthFt", sv(farm.usableWidthFt)), listOf("broodDensity", sv(farm.broodDensity)),
        listOf("fanCount", sv(farm.fanCount)), listOf("fanRatedCfm", sv(farm.fanRatedCfm)),
        listOf("fanDerate", sv(farm.fanDerate)), listOf("heaterCount", sv(farm.heaterCount)),
        listOf("heaterKw", sv(farm.heaterKw)), listOf("hasEC", sv(farm.hasEC)),
        listOf("padAreaFt2", sv(farm.padAreaFt2)), listOf("padEffPct", sv(farm.padEffPct)),
        listOf("padCount", sv(farm.padCount)), listOf("drinkerLines", sv(farm.drinkerLines)),
        listOf("drinkTankL", sv(farm.drinkTankL)), listOf("drinkFillMin", sv(farm.drinkFillMin)),
        listOf("nippleLineHoldL", sv(farm.nippleLineHoldL)), listOf("feederLines", sv(farm.feederLines)),
        listOf("feederLineBags", sv(farm.feederLineBags)), listOf("feederMoveMin", sv(farm.feederMoveMin)),
        listOf("pansPerFeederLine", sv(farm.pansPerFeederLine)), listOf("dieselCanL", sv(farm.dieselCanL)),
        listOf("feedBagKg", sv(farm.feedBagKg)), listOf("baseFeedings", sv(farm.baseFeedings)),
        listOf("feedDistDay", sv(farm.feedDistDay)), listOf("feedDistMid", sv(farm.feedDistMid)),
        listOf("feedDistNight", sv(farm.feedDistNight)), listOf("season", farm.season),
        listOf("weatherLat", sv(farm.weatherLat)), listOf("weatherLon", sv(farm.weatherLon)),
        listOf("weatherName", farm.weatherName), listOf("densityCapDefault", sv(farm.densityCapDefault)),
        listOf("cutoffTime", farm.cutoffTime),
        listOf("waterRefillFactor", sv(farm.waterRefillFactor)), listOf("minVentFactor", sv(farm.minVentFactor)),
        listOf("godownBags", sv(farm.godownBags)), listOf("manualFeeders", sv(farm.manualFeeders)),
        listOf("manualDrinkers", sv(farm.manualDrinkers)), listOf("nipplesPerLine", sv(farm.nipplesPerLine)),
        listOf("panLipCm", sv(farm.panLipCm)),
        listOf("lineFillBags", sv(farm.lineFillBags)), listOf("sensorPansPerLine", sv(farm.sensorPansPerLine)),
        listOf("panSpacingFt", sv(farm.panSpacingFt)), listOf("feederLineGapFt", sv(farm.feederLineGapFt))
    )

    /** Farm settings from the _Farm key/value tab, laid over [base]; a key the sheet lacks keeps the base value. Keys match case-insensitively. */
    fun kvToFarm(sid: String, rows: List<List<Any?>>?, base: FarmEntity): FarmEntity {
        val raw = kv(rows); val m = raw.mapKeys { norm(it.key) }
        fun g(k: String) = m[norm(k)]
        fun st(k: String, d: String) = g(k)?.takeIf { it.isNotBlank() && !it.equals("null", true) } ?: d
        fun db(k: String, d: Double) = num(g(k)) ?: d
        fun it2(k: String, d: Int) = num(g(k))?.let { kotlin.math.round(it).toInt() } ?: d
        fun bl(k: String, d: Boolean) = g(k)?.let { it.equals("true", true) || it == "1" } ?: d
        return base.copy(
            spreadsheetId = sid,
            farmName = st("farmName", base.farmName), farmId = st("farmId", base.farmId),
            houseName = st("houseName", base.houseName), timeZone = st("timeZone", base.timeZone),
            lengthFt = db("lengthFt", base.lengthFt), widthFt = db("widthFt", base.widthFt),
            heightFt = db("heightFt", base.heightFt), usableLengthFt = db("usableLengthFt", base.usableLengthFt),
            usableWidthFt = db("usableWidthFt", base.usableWidthFt), broodDensity = db("broodDensity", base.broodDensity),
            fanCount = it2("fanCount", base.fanCount), fanRatedCfm = db("fanRatedCfm", base.fanRatedCfm),
            fanDerate = db("fanDerate", base.fanDerate), heaterCount = it2("heaterCount", base.heaterCount),
            heaterKw = db("heaterKw", base.heaterKw), hasEC = bl("hasEC", base.hasEC),
            padAreaFt2 = db("padAreaFt2", base.padAreaFt2), padEffPct = db("padEffPct", base.padEffPct),
            padCount = it2("padCount", base.padCount), drinkerLines = it2("drinkerLines", base.drinkerLines),
            drinkTankL = db("drinkTankL", base.drinkTankL), drinkFillMin = db("drinkFillMin", base.drinkFillMin),
            nippleLineHoldL = db("nippleLineHoldL", base.nippleLineHoldL), feederLines = it2("feederLines", base.feederLines),
            feederLineBags = it2("feederLineBags", base.feederLineBags), feederMoveMin = db("feederMoveMin", base.feederMoveMin),
            pansPerFeederLine = it2("pansPerFeederLine", base.pansPerFeederLine), dieselCanL = db("dieselCanL", base.dieselCanL),
            feedBagKg = db("feedBagKg", base.feedBagKg), baseFeedings = it2("baseFeedings", base.baseFeedings),
            feedDistDay = it2("feedDistDay", base.feedDistDay), feedDistMid = it2("feedDistMid", base.feedDistMid),
            feedDistNight = it2("feedDistNight", base.feedDistNight), season = st("season", base.season),
            weatherLat = db("weatherLat", base.weatherLat), weatherLon = db("weatherLon", base.weatherLon),
            weatherName = st("weatherName", base.weatherName), densityCapDefault = db("densityCapDefault", base.densityCapDefault),
            cutoffTime = st("cutoffTime", base.cutoffTime),
            waterRefillFactor = db("waterRefillFactor", base.waterRefillFactor), minVentFactor = db("minVentFactor", base.minVentFactor),
            godownBags = db("godownBags", base.godownBags), manualFeeders = it2("manualFeeders", base.manualFeeders),
            manualDrinkers = it2("manualDrinkers", base.manualDrinkers), nipplesPerLine = it2("nipplesPerLine", base.nipplesPerLine),
            panLipCm = db("panLipCm", base.panLipCm),
            lineFillBags = num(g("lineFillBags")) ?: num(g("feederLineBags")) ?: base.lineFillBags,
            sensorPansPerLine = it2("sensorPansPerLine", base.sensorPansPerLine),
            panSpacingFt = db("panSpacingFt", base.panSpacingFt), feederLineGapFt = db("feederLineGapFt", base.feederLineGapFt)
        )
    }

    fun configToKV(c: ConfigEntity): List<List<Any>> = typedKV(configToKVText(c))
    private fun configToKVText(c: ConfigEntity): List<List<String>> = listOf(
        listOf("Key", "Value"),
        listOf("tempBand", sv(c.tempBand)), listOf("rhMin", sv(c.rhMin)), listOf("rhMax", sv(c.rhMax)),
        listOf("nh3Warn", sv(c.nh3Warn)), listOf("nh3Crit", sv(c.nh3Crit)), listOf("co2Warn", sv(c.co2Warn)),
        listOf("co2Crit", sv(c.co2Crit)), listOf("cvWarn", sv(c.cvWarn)), listOf("cvCrit", sv(c.cvCrit)),
        listOf("wfRatio", sv(c.wfRatio)), listOf("feedHeatK", sv(c.feedHeatK)), listOf("waterHeatK", sv(c.waterHeatK)),
        listOf("cFcrDivisor", sv(c.cFcrDivisor)), listOf("cycleSec", sv(c.cycleSec)), listOf("minOnSec", sv(c.minOnSec)),
        listOf("tunTrigYoung", sv(c.tunTrigYoung)), listOf("tunTrigBig", sv(c.tunTrigBig))
    )

    fun kvToConfig(sid: String, rows: List<List<Any?>>?, base: ConfigEntity): ConfigEntity {
        val m = kv(rows).mapKeys { norm(it.key) }
        fun db(k: String, d: Double) = num(m[norm(k)]) ?: d
        fun it2(k: String, d: Int) = num(m[norm(k)])?.let { kotlin.math.round(it).toInt() } ?: d
        return base.copy(
            spreadsheetId = sid, tempBand = db("tempBand", base.tempBand), rhMin = db("rhMin", base.rhMin), rhMax = db("rhMax", base.rhMax),
            nh3Warn = db("nh3Warn", base.nh3Warn), nh3Crit = db("nh3Crit", base.nh3Crit), co2Warn = db("co2Warn", base.co2Warn),
            co2Crit = db("co2Crit", base.co2Crit), cvWarn = db("cvWarn", base.cvWarn), cvCrit = db("cvCrit", base.cvCrit),
            wfRatio = db("wfRatio", base.wfRatio), feedHeatK = db("feedHeatK", base.feedHeatK), waterHeatK = db("waterHeatK", base.waterHeatK),
            cFcrDivisor = db("cFcrDivisor", base.cFcrDivisor), cycleSec = it2("cycleSec", base.cycleSec), minOnSec = it2("minOnSec", base.minOnSec),
            tunTrigYoung = db("tunTrigYoung", base.tunTrigYoung), tunTrigBig = db("tunTrigBig", base.tunTrigBig)
        )
    }

    /** Key/value rows with numbers and true/false as real cell values (text stays text). */
    private fun typedKV(rows: List<List<String>>): List<List<Any>> = rows.mapIndexed { i, r ->
        if (i == 0) r else listOf(r[0], typed(r[1]))
    }
    private fun typed(v: String): Any = when {
        v.equals("true", true) -> true
        v.equals("false", true) -> false
        Regex("^-?\\d+$").matches(v) && v.length < 16 -> v.toLong()
        Regex("^-?\\d*\\.\\d+([eE]-?\\d+)?$|^-?\\d+[eE]-?\\d+$").matches(v) -> v.toDouble()
        else -> v
    }

    // ------------------------------------------------------------------ whole files

    /** Everything read from one farm file, before parsing. Null lists mean the tab is missing. */
    data class RawFile(
        val titles: Set<String>,
        val meta: List<List<Any?>>? = null,
        val farm: List<List<Any?>>? = null,
        val config: List<List<Any?>>? = null,
        val feedTypes: List<List<Any?>>? = null,
        val flocks: List<List<Any?>>? = null,
        val days: List<List<Any?>>? = null,
        val tasks: List<List<Any?>>? = null,
        val sheetIds: Map<String, Int> = emptyMap(),
        /** tab → (rows, columns) of its grid */
        val grid: Map<String, Pair<Int, Int>> = emptyMap()
    ) {
        val metaMap: Map<String, String> get() = kv(meta)
        val schema: Int get() = metaMap["schemaVersion"]?.let { num(it)?.toInt() } ?: 0
    }

    /** Unknown columns of a table: their names and each row's values by key. */
    data class Extra(val names: List<String> = emptyList(), val byKey: Map<String, List<String>> = emptyMap()) {
        fun of(key: String) = byKey[key] ?: List(names.size) { "" }
    }

    /** A farm's data in app terms, plus the unknown columns to carry along. */
    data class Content(
        val farm: FarmEntity,
        val config: ConfigEntity?,
        val feedTypes: List<FeedTypeEntity>,
        val flocks: List<FlockEntity>,
        val days: List<DailyDataEntity>,
        val tasks: List<TaskEntity>,
        val flockExtra: Extra = Extra(),
        val dayExtra: Extra = Extra(),
        val taskExtra: Extra = Extra()
    )

    private fun <T> readTable(values: List<List<Any?>>?, fields: List<String>, parse: (Row) -> T?, key: (T) -> String): Triple<List<T>, Extra, Tab> {
        val t = tab(values, fields)
        val extraCols = if (t.hadHeader) t.header.extras(fields) else emptyList()
        val out = LinkedHashMap<String, T>()
        val extra = HashMap<String, List<String>>()
        for (r in t.rowsOf()) {
            val e = parse(r) ?: continue
            val k = key(e)
            out[k] = e                       // a key written twice: the lower row wins (it was written last)
            if (extraCols.isNotEmpty()) extra[k] = extraCols.map { r.cell(it) }
        }
        return Triple(out.values.toList(), Extra(extraCols.map { t.header.names[it] }, extra), t)
    }

    /** Parses a whole file; settings not in the file keep [baseFarm] / [baseConfig]. */
    fun parse(sid: String, raw: RawFile, baseFarm: FarmEntity, baseConfig: ConfigEntity?): Content {
        val (flocks, fx, _) = readTable(raw.flocks, FLOCK_HEADERS, { flock(sid, it) }, { it.flockId })
        val (days, dx, _) = readTable(raw.days, DAILY_HEADERS, { day(sid, it) }, { dayKey(it) })
        val (tasks, tx, _) = readTable(raw.tasks, TASK_HEADERS, { task(sid, it) }, { it.taskId })
        val (feeds, _, _) = readTable(raw.feedTypes, FEED_HEADERS, { feedType(sid, it) }, { it.code })
        val config = if (kv(raw.config).isNotEmpty()) kvToConfig(sid, raw.config, baseConfig ?: ConfigEntity(spreadsheetId = sid)) else baseConfig
        return Content(kvToFarm(sid, raw.farm, baseFarm), config, feeds, flocks, days, tasks, fx, dx, tx)
    }

    /** Why a file isn't in the current layout (empty = up to date). */
    fun upgradeReasons(raw: RawFile): List<String> {
        val out = mutableListOf<String>()
        if (raw.schema < VERSION) out += "schema ${raw.schema} → $VERSION"
        val missing = (TABS + SheetReports.TABS).filter { it !in raw.titles }
        if (missing.isNotEmpty()) out += "missing tabs: ${missing.joinToString()}"
        fun check(name: String, values: List<List<Any?>>?, fields: List<String>) {
            if (values == null) return
            val t = tab(values, fields)
            if (!t.hadHeader) { if (t.rows.isNotEmpty() || values.isEmpty()) out += "$name has no header row"; return }
            if (t.header.names.take(fields.size) != fields) out += "$name columns differ"
        }
        check("Flocks", raw.flocks, FLOCK_HEADERS)
        raw.days?.let { values ->
            val t = tab(values, DAILY_HEADERS)
            if (!t.hadHeader) { if (t.rows.isNotEmpty() || values.isEmpty()) out += "DailyData has no header row" }
            else {
                // the varieties and extra locations the file already has, in the order the current layout puts them
                val names = t.header.names
                val expected = dailyHeader(names.filter { isUsedCol(it) }.map { usedCode(it) }, names.mapNotNull { sampleIndex(it) }.maxOrNull() ?: 5)
                if (names.take(expected.size) != expected) out += "DailyData columns differ"
            }
        }
        check("Tasks", raw.tasks, TASK_HEADERS)
        check("_FeedTypes", raw.feedTypes, FEED_HEADERS)
        return out
    }

    /** The result of merging the live file, the phone and older backups. */
    data class Merged(val content: Content, val recoveredDays: Int, val recoveredFlocks: Int, val fromPhone: Int)

    /**
     * Merges, newest data winning row by row:
     *  - day rows: the latest UpdatedAt across the live file, the phone and every backup (ties: live, then phone, then backups);
     *  - flocks and feed types: every one found (live first, then phone, then backups);
     *  - tasks: live and phone only (a task deleted in the live file is not brought back from an old backup);
     *  - settings: the live file over the phone.
     * Unknown columns ride along with the live file's rows.
     */
    fun merge(live: Content, phone: Content?, backups: List<Content>): Merged {
        val sources = listOfNotNull(live, phone) + backups
        val flocks = LinkedHashMap<String, FlockEntity>()
        var recFlocks = 0
        sources.forEachIndexed { si, c -> c.flocks.forEach { if (it.flockId !in flocks) { flocks[it.flockId] = it.copy(spreadsheetId = live.farm.spreadsheetId); if (si >= (if (phone != null) 2 else 1)) recFlocks++ } } }
        val days = LinkedHashMap<String, Pair<DailyDataEntity, Int>>()
        sources.forEachIndexed { si, c ->
            c.days.forEach { d ->
                val k = dayKey(d); val cur = days[k]
                if (cur == null || d.updatedAt > cur.first.updatedAt) days[k] = d.copy(spreadsheetId = live.farm.spreadsheetId, dirty = false) to si
            }
        }
        val backupStart = if (phone != null) 2 else 1
        val recovered = days.values.count { it.second >= backupStart }
        val fromPhone = if (phone != null) days.values.count { it.second == 1 } else 0
        val tasks = LinkedHashMap<String, TaskEntity>()
        listOfNotNull(live, phone).forEach { c -> c.tasks.forEach { if (it.taskId !in tasks) tasks[it.taskId] = it.copy(spreadsheetId = live.farm.spreadsheetId) } }
        val feeds = LinkedHashMap<String, FeedTypeEntity>()
        sources.forEach { c -> c.feedTypes.forEach { if (it.code !in feeds) feeds[it.code] = it.copy(spreadsheetId = live.farm.spreadsheetId) } }
        val content = live.copy(
            config = live.config ?: phone?.config ?: backups.firstNotNullOfOrNull { it.config },
            feedTypes = feeds.values.sortedBy { it.sortOrder },
            flocks = flocks.values.sortedBy { it.createdAt },
            days = days.values.map { it.first }.sortedWith(compareBy({ it.flockId }, { it.dayNumber })),
            tasks = tasks.values.sortedWith(compareBy({ it.flockId }, { it.createdAt }))
        )
        return Merged(content, recovered, recFlocks, fromPhone)
    }

    /** Every tab's block in the current layout. [meta] is written as the _Meta key/value list. */
    /** Feed varieties that get a "Used" column: the farm's types in order, then any other code the days used. */
    fun usedCodes(c: Content): List<String> =
        (c.feedTypes.sortedBy { it.sortOrder }.map { it.code } + c.days.flatMap { usedSplit(it).keys }).filter { it.isNotBlank() }.distinct()

    fun blocks(c: Content, meta: List<Pair<String, String>>): LinkedHashMap<String, List<List<Any>>> {
        val out = LinkedHashMap<String, List<List<Any>>>()
        out["_Meta"] = typedKV(listOf(listOf("Key", "Value")) + meta.map { listOf(it.first, it.second) })
        out["_Farm"] = farmToKV(c.farm)
        out["_Config"] = c.config?.let { configToKV(it) } ?: listOf(listOf("Key", "Value"))
        out["_FeedTypes"] = listOf<List<Any>>(FEED_HEADERS) + c.feedTypes.map { feedRow(it) }
        out["Flocks"] = listOf<List<Any>>(FLOCK_HEADERS + c.flockExtra.names) + c.flocks.map { flockRow(it) + c.flockExtra.of(it.flockId) }
        val header = dailyHeader(usedCodes(c), c.days.maxOfOrNull { locationsOf(it) } ?: 5)
        out["DailyData"] = listOf<List<Any>>(header + c.dayExtra.names) + c.days.map { d -> dayCells(d, header) + c.dayExtra.of(dayKey(d)) }
        out["Tasks"] = listOf<List<Any>>(TASK_HEADERS + c.taskExtra.names) + c.tasks.map { taskRow(it) + c.taskExtra.of(it.taskId) }
        // report tabs: worked out from the rows above, never read back
        out[SheetReports.FEED_LEDGER] = SheetReports.feedLedger(c)
        out[SheetReports.DAILY_SUMMARY] = SheetReports.dailySummary(c)
        return out
    }

    /** What a read-back is missing compared with what was written (empty = verified). */
    fun verify(written: Content, back: RawFile): List<String> {
        val out = mutableListOf<String>()
        if (upgradeReasons(back).isNotEmpty()) out += upgradeReasons(back)
        val got = parse(written.farm.spreadsheetId, back, written.farm, written.config)
        val lostFlocks = written.flocks.map { it.flockId }.toSet() - got.flocks.map { it.flockId }.toSet()
        val lostDays = written.days.map { dayKey(it) }.toSet() - got.days.map { dayKey(it) }.toSet()
        val lostTasks = written.tasks.map { it.taskId }.toSet() - got.tasks.map { it.taskId }.toSet()
        if (lostFlocks.isNotEmpty()) out += "${lostFlocks.size} flock(s) missing"
        if (lostDays.isNotEmpty()) out += "${lostDays.size} day row(s) missing"
        if (lostTasks.isNotEmpty()) out += "${lostTasks.size} task(s) missing"
        return out
    }

    fun primaryMeta(farmId: String, fromSchema: Int, now: Long = System.currentTimeMillis()) = listOf(
        "app" to "FlockIt", "schemaVersion" to VERSION.toString(), "role" to "primary", "farmId" to farmId,
        "updatedAt" to now.toString(), "upgradedFrom" to fromSchema.toString()
    )
    fun backupMeta(farmId: String, mainId: String, now: Long = System.currentTimeMillis()) = listOf(
        "app" to "FlockIt", "schemaVersion" to VERSION.toString(), "role" to "backup", "backupOf" to mainId,
        "farmId" to farmId, "updatedAt" to now.toString()
    )

    /** 1 → A, 27 → AA */
    fun colLetter(n: Int): String { var x = n; val sb = StringBuilder(); while (x > 0) { val r = (x - 1) % 26; sb.insert(0, ('A' + r)); x = (x - 1) / 26 }; return sb.toString() }

    private fun sv(v: Any?): String = v?.toString() ?: ""
    /** An optional number as a real number, or a blank cell. */
    private fun n(v: Number?): Any = v ?: ""
}
