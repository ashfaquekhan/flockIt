package com.example.flock.graph

import com.example.flock.data.DailyDataEntity
import com.example.flock.data.FlockEventEntity
import com.example.flock.sync.SheetSchema
import org.json.JSONObject

/**
 * What can happen on the farm (v58). Each kind names the properties it changes ([changes], keys of
 * [FlockGraph.props]) and the things it touches ([touches]).
 */
enum class EventKind(val key: String, val label: String, val changes: List<String>, val touches: List<String>) {
    PLACED("placed", "Chicks placed", listOf("placed"), listOf("flock", "house")),
    WEIGHING("weighing", "Weighing", listOf("samples", "singles"), listOf("flock", "floor")),
    DEATHS("deaths", "Deaths counted", listOf("deaths"), listOf("flock")),
    CULLS("culls", "Culls", listOf("culls"), listOf("flock")),
    FEED_USED("feed_used", "Feed used", listOf("feedUsed"), listOf("flock", "godown")),
    FEED_RECEIVED("feed_received", "Feed received", listOf("received"), listOf("godown", "trucks")),
    FEEDING("feeding", "Feeding poured", listOf("pansFilled"), listOf("feederLines", "pans")),
    LIFTING("lifting", "Lifting", listOf("lifted", "liftedKg", "liftTime"), listOf("flock", "trucks")),
    BARRICADES("barricades", "Barricades moved", listOf("barricades"), listOf("barricades", "floor")),
    WEATHER("weather", "Weather kept", listOf("outTemp", "outRh"), listOf("house")),
    READING("reading", "House reading", listOf("sensors"), listOf("house")),
    NOTES("notes", "Notes", emptyList(), listOf("flock")),
    DIESEL("diesel", "Diesel used", emptyList(), listOf("house")),
    SETTINGS("settings", "Farm settings", listOf("house", "feederLines", "pans", "drinkerLines", "nipples", "fans", "pads", "tank"), listOf("house"));

    /** The event kind's point in the property graph. */
    val nodeKey: String get() = "ev:$key"

    companion object {
        fun of(key: String): EventKind? = values().firstOrNull { it.key == key }

        /** Which kind of event a changed day column (normalised name, see [SheetSchema.norm]) belongs to; null: none. */
        fun ofColumn(c: String): EventKind? = when {
            SheetSchema.isWeightColumn(c) || c == "locations" || c == "weighedat" -> WEIGHING
            c == "mortality" -> DEATHS
            c == "lameseparated" -> CULLS
            c == "feedbagsused" || c == "feedusedtype" || c == "feedusedbreakdown" || (c.startsWith("used") && c != "updatedat" && c != "updatedby") -> FEED_USED
            c.startsWith("feedrecb") || c.startsWith("feedtypeb") -> FEED_RECEIVED
            c == "birdslifted" || c == "weightlifted" || c == "liftedat" || c == "liftlaps" -> LIFTING
            c == "penstartft" || c == "broodinglength" || c == "pensidestartft" || c == "pensideendft" -> BARRICADES
            c == "feedings" -> FEEDING
            c == "notes" -> NOTES
            c == "dieselcansused" -> DIESEL
            c.startsWith("outtemp") || c.startsWith("outrh") -> WEATHER
            c in READINGS -> READING
            else -> null
        }

        private val READINGS = setOf("watertempc", "waterph", "feedmoisturepct", "measuredco2", "measurednh3", "measuredo2",
            "measuredpressure", "measuredairspeed", "padwetmin", "paddrymin", "luxperft2", "actualfans", "actualfantime")
    }
}

/** The event log: what a save changed, as events with their two times (when it happened, when it was entered). */
object FlockEvents {

    /**
     * The events in one save of a day row: the changed columns [cols] (normalised) grouped by kind, each with its new and
     * old values. When it happened: the weighing's time for a weighing, the lifting's for a lifting, the feeding's for a
     * feeding, else [at] (the time picked for the entries) or [now].
     */
    fun fromSave(before: DailyDataEntity, after: DailyDataEntity, cols: Set<String>, by: String, now: Long, at: Long? = null,
                 feedingAt: Long? = null): List<FlockEventEntity> {
        if (cols.isEmpty()) return emptyList()
        val a = SheetSchema.dayValues(after); val b = SheetSchema.dayValues(before)
        return cols.groupBy { EventKind.ofColumn(it) }.mapNotNull { (kind, cs) ->
            kind ?: return@mapNotNull null
            val happened = when (kind) {
                EventKind.WEIGHING -> after.weighedAt.takeIf { it > 0 } ?: at ?: now
                EventKind.LIFTING -> after.liftedAt.takeIf { it > 0 } ?: at ?: now
                EventKind.FEEDING -> feedingAt ?: at ?: now
                else -> at ?: now
            }
            FlockEventEntity(
                id = java.util.UUID.randomUUID().toString(), spreadsheetId = after.spreadsheetId, flockId = after.flockId,
                day = after.dayNumber, kind = kind.key, happenedAt = happened, enteredAt = now, enteredBy = by,
                touches = kind.touches.joinToString(","),
                newValues = json(cs.sorted().associateWith { SheetSchema.cellText(a[it]) }),
                oldValues = json(cs.sorted().associateWith { SheetSchema.cellText(b[it]) }))
        }.sortedBy { it.kind }
    }

    /** One event not tied to a day row's columns (chicks placed, farm settings changed). */
    fun single(kind: EventKind, sid: String, flockId: String, day: Int, by: String, now: Long, values: Map<String, String>,
               before: Map<String, String> = emptyMap(), happenedAt: Long = now) = FlockEventEntity(
        id = java.util.UUID.randomUUID().toString(), spreadsheetId = sid, flockId = flockId, day = day, kind = kind.key,
        happenedAt = happenedAt, enteredAt = now, enteredBy = by, touches = kind.touches.joinToString(","),
        newValues = json(values), oldValues = json(before))

    fun json(m: Map<String, String>): String = JSONObject().apply { m.forEach { (k, v) -> put(k, v) } }.toString()
    fun map(s: String): Map<String, String> = try {
        if (s.isBlank()) emptyMap() else JSONObject(s).let { o -> o.keys().asSequence().associateWith { o.optString(it, "") } }
    } catch (e: Exception) { emptyMap() }

    /** A short line of what an event set: "Deaths 4 (was 3)", "W1 6000 · N1 10". */
    fun summary(e: FlockEventEntity, max: Int = 4): String {
        val now = map(e.newValues); val was = map(e.oldValues)
        return now.entries.filter { it.value.isNotBlank() || was[it.key]?.isNotBlank() == true }.take(max).joinToString(" · ") { (k, v) ->
            val label = SheetSchema.columnLabel(k)
            val old = was[k]
            label + " " + v.ifBlank { "cleared" } + if (!old.isNullOrBlank() && old != v) " (was $old)" else ""
        } + if (now.size > max) " …" else ""
    }
}
