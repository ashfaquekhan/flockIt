package com.example.flock.graph

import com.example.flock.data.FarmEntity
import com.example.flock.data.FlockEntity

/**
 * The flock as properties, things, events and the links between them (v58).
 *
 * Three layers, as in event-sourced, property-graph designs:
 *  - **events** ([EventKind]): what happened, when it happened and when it was entered — kept as an append-only log
 *    (the phone's `flock_events` table and the sheet's Events tab);
 *  - **properties and things** ([Prop]): every number the app works with and every part of the house, each in a group,
 *    each with where it comes from ([Source]);
 *  - **links** ([Edge]): what each property is worked out from (with the direction and how strongly it pushes), how the
 *    things of the house hang together (relations that carry their own properties), and which properties each event
 *    changes.
 *
 * The day rows stay the record the numbers are worked out from; the log is kept beside them, so the history of every
 * change (with its two times) is there to build on.
 */
object FlockGraph {

    enum class Group(val label: String) {
        BIRDS("Birds"), FEED("Feed"), LIFTING("Lifting"), AIR("Air"), WATER("Water"), HOUSE("House"), EVENTS("Events")
    }

    /** Where a property's value comes from. */
    enum class Source(val label: String) {
        ENTERED("entered"), DERIVED("worked out"), MODEL("model"), SETTING("farm setting"), THING("part of the house"),
        WEATHER("weather"), CLOCK("clock"), SENSOR("sensor"), EVENT("event")
    }

    data class Prop(val key: String, val label: String, val group: Group, val source: Source, val unit: String = "")

    enum class EdgeKind { DEPENDS, RELATION, CHANGES }

    /**
     * A link [from] → [to]. DEPENDS: [to] is worked out from [from], [sign] +1 raises it, −1 lowers it, 0 shapes it;
     * [strength] 1 a little, 2 some, 3 strongly. RELATION: how two things hang together ([label], with its properties
     * filled in from the farm by [relationProps]). CHANGES: an event (key "ev:…") sets or moves a property.
     */
    data class Edge(val from: String, val to: String, val kind: EdgeKind, val sign: Int = 0, val strength: Int = 2, val label: String = "",
                    /** a feedback that acts later (the next hour, the next days): it closes a loop without making one in the sum */
                    val lag: Boolean = false)

    private fun p(key: String, label: String, group: Group, source: Source, unit: String = "") = Prop(key, label, group, source, unit)

    val props: List<Prop> = listOf(
        // ---- birds
        p("placed", "Birds placed", Group.BIRDS, Source.ENTERED),
        p("deaths", "Deaths", Group.BIRDS, Source.ENTERED),
        p("culls", "Culls", Group.BIRDS, Source.ENTERED),
        p("live", "Live birds", Group.BIRDS, Source.DERIVED),
        p("mortality", "Mortality", Group.BIRDS, Source.DERIVED, "%"),
        p("livability", "Livability", Group.BIRDS, Source.DERIVED, "%"),
        p("samples", "Weight samples", Group.BIRDS, Source.ENTERED, "g"),
        p("singles", "Birds weighed one by one", Group.BIRDS, Source.ENTERED, "g"),
        p("weight", "Weight", Group.BIRDS, Source.DERIVED, "g"),
        p("weightNow", "Weight now", Group.BIRDS, Source.DERIVED, "g"),
        p("cv", "CV", Group.BIRDS, Source.DERIVED, "%"),
        p("gainY", "Yesterday's gain", Group.BIRDS, Source.DERIVED, "g"),
        p("density", "Stocking density", Group.BIRDS, Source.DERIVED, "kg/m²"),
        p("ft2Bird", "Floor per bird", Group.BIRDS, Source.DERIVED, "ft²"),
        p("fcr", "FCR", Group.BIRDS, Source.DERIVED),
        p("cfcr", "cFCR", Group.BIRDS, Source.DERIVED),
        p("cumBw", "Every bird produced, average", Group.BIRDS, Source.DERIVED, "kg"),
        p("epef", "EPEF", Group.BIRDS, Source.DERIVED),
        p("heatLoad", "Heat on the birds", Group.BIRDS, Source.MODEL, "°C"),
        p("panting", "Panting", Group.BIRDS, Source.MODEL, "%"),
        p("birdHeat", "Heat the birds give", Group.BIRDS, Source.DERIVED, "W"),
        // ---- feed
        p("feedUsed", "Feed used", Group.FEED, Source.ENTERED, "bags"),
        p("feedTD", "Feed till date", Group.FEED, Source.DERIVED, "kg"),
        p("received", "Feed received", Group.FEED, Source.ENTERED, "bags"),
        p("store", "Feed in store", Group.FEED, Source.DERIVED, "bags"),
        p("appetite", "The flock's appetite", Group.FEED, Source.MODEL, "kg"),
        p("bags", "Bags to use", Group.FEED, Source.DERIVED, "bags"),
        p("perBird", "Feed per bird", Group.FEED, Source.DERIVED, "g"),
        p("eatenY", "Eaten yesterday", Group.FEED, Source.DERIVED, "g"),
        p("gainToday", "Gain today, curve", Group.FEED, Source.DERIVED, "g"),
        p("pansOpen", "Pans open", Group.FEED, Source.DERIVED),
        p("birdsPerPan", "Birds per pan", Group.FEED, Source.DERIVED),
        p("feedings", "Feedings", Group.FEED, Source.DERIVED),
        p("pansFilled", "Pans filled", Group.FEED, Source.ENTERED, "bags"),
        p("hungry", "Hungry now", Group.FEED, Source.MODEL, "%"),
        p("walkFeed", "Walk to feed", Group.FEED, Source.DERIVED, "m"),
        p("correction", "Feed correction", Group.FEED, Source.MODEL, "%"),
        // ---- lifting
        p("lifted", "Birds lifted", Group.LIFTING, Source.ENTERED),
        p("liftedKg", "Kg lifted", Group.LIFTING, Source.ENTERED, "kg"),
        p("liftAvg", "Lifted, per bird", Group.LIFTING, Source.DERIVED, "kg"),
        p("liftTime", "Lifting time", Group.LIFTING, Source.ENTERED),
        p("kgInHouse", "Kg in the house", Group.LIFTING, Source.DERIVED, "kg"),
        p("deadKg", "Kg dead and culled", Group.LIFTING, Source.DERIVED, "kg"),
        p("produced", "Produced", Group.LIFTING, Source.DERIVED, "kg"),
        // ---- air
        p("outTemp", "Outside temperature", Group.AIR, Source.WEATHER, "°C"),
        p("outRh", "Outside humidity", Group.AIR, Source.WEATHER, "%"),
        p("houseTemp", "House temperature", Group.AIR, Source.MODEL, "°C"),
        p("houseRh", "House humidity", Group.AIR, Source.MODEL, "%"),
        p("airSpeed", "Air speed", Group.AIR, Source.MODEL, "m/s"),
        p("minVent", "Minimum ventilation", Group.AIR, Source.DERIVED, "cfm"),
        p("fansOn", "Fans running", Group.AIR, Source.MODEL),
        p("padsWet", "Pads wet", Group.AIR, Source.MODEL),
        p("litterMoist", "Litter moisture", Group.AIR, Source.MODEL, "%"),
        p("ammonia", "Ammonia", Group.AIR, Source.MODEL, "ppm"),
        p("lightHours", "Light hours", Group.AIR, Source.DERIVED, "h"),
        p("sensors", "House sensors", Group.AIR, Source.SENSOR),
        // ---- water
        p("water", "Water today", Group.WATER, Source.DERIVED, "L"),
        p("waterPerBird", "Water per bird", Group.WATER, Source.DERIVED, "mL"),
        p("refills", "Tank refills", Group.WATER, Source.DERIVED),
        p("birdsPerNipple", "Birds per nipple", Group.WATER, Source.DERIVED),
        p("walkWater", "Walk to water", Group.WATER, Source.DERIVED, "m"),
        p("nippleHeight", "Nipple height", Group.WATER, Source.DERIVED, "in"),
        p("thirsty", "Thirsty now", Group.WATER, Source.MODEL, "%"),
        // ---- the house: its parts (things) and settings, the clock
        p("age", "Age", Group.HOUSE, Source.CLOCK, "days"),
        p("clock", "Time of day", Group.HOUSE, Source.CLOCK),
        p("house", "House", Group.HOUSE, Source.THING, "ft"),
        p("floor", "Birds' floor", Group.HOUSE, Source.DERIVED, "ft²"),
        p("barricades", "Barricades", Group.HOUSE, Source.THING, "ft"),
        p("feederLines", "Feeder lines", Group.HOUSE, Source.THING),
        p("pans", "Pans", Group.HOUSE, Source.THING),
        p("drinkerLines", "Drinker lines", Group.HOUSE, Source.THING),
        p("nipples", "Nipples", Group.HOUSE, Source.THING),
        p("fans", "Fans", Group.HOUSE, Source.THING),
        p("pads", "Cooling pads", Group.HOUSE, Source.THING),
        p("tank", "Water tank", Group.HOUSE, Source.THING, "L"),
        p("trucks", "Trucks", Group.HOUSE, Source.THING),
        p("godown", "Feed store", Group.HOUSE, Source.THING),
    ) + EventKind.values().map { Prop(it.nodeKey, it.label, Group.EVENTS, Source.EVENT) }

    private fun d(from: String, to: String, sign: Int, strength: Int, lag: Boolean = false) = Edge(from, to, EdgeKind.DEPENDS, sign, strength, lag = lag)
    private fun r(from: String, to: String, label: String) = Edge(from, to, EdgeKind.RELATION, label = label)

    /** What each property is worked out from: + raises it, − lowers it, 0 shapes it; 1 a little, 3 strongly. */
    val depends: List<Edge> = listOf(
        d("placed", "live", +1, 3), d("deaths", "live", -1, 2), d("culls", "live", -1, 1), d("lifted", "live", -1, 3),
        d("deaths", "mortality", +1, 3), d("placed", "mortality", -1, 1),
        d("deaths", "livability", -1, 3), d("culls", "livability", -1, 1),
        d("samples", "weight", +1, 3), d("age", "weight", +1, 2), d("heatLoad", "weight", -1, 1, lag = true),
        d("weight", "weightNow", +1, 3), d("clock", "weightNow", +1, 1), d("lifted", "weightNow", -1, 1), d("liftAvg", "weightNow", -1, 2), d("liftTime", "weightNow", 0, 1),
        d("singles", "cv", +1, 3), d("samples", "cv", +1, 1),
        d("weight", "gainY", +1, 3),
        d("live", "density", +1, 3), d("weightNow", "density", +1, 3), d("floor", "density", -1, 3),
        d("floor", "ft2Bird", +1, 3), d("live", "ft2Bird", -1, 3),
        d("feedTD", "fcr", +1, 3), d("kgInHouse", "fcr", -1, 3), d("liftedKg", "fcr", -1, 2),
        d("fcr", "cfcr", +1, 3), d("cumBw", "cfcr", -1, 2),
        d("kgInHouse", "cumBw", +1, 2), d("liftedKg", "cumBw", +1, 2), d("live", "cumBw", -1, 2), d("lifted", "cumBw", -1, 1),
        d("livability", "epef", +1, 2), d("weightNow", "epef", +1, 3), d("fcr", "epef", -1, 3), d("age", "epef", -1, 2),
        d("houseTemp", "heatLoad", +1, 3), d("houseRh", "heatLoad", +1, 2), d("airSpeed", "heatLoad", -1, 3), d("weightNow", "heatLoad", +1, 1), d("density", "heatLoad", +1, 1),
        d("heatLoad", "panting", +1, 3),
        d("live", "birdHeat", +1, 3), d("weightNow", "birdHeat", +1, 2),
        // feed
        d("feedUsed", "feedTD", +1, 3),
        d("received", "store", +1, 3), d("feedUsed", "store", -1, 3),
        d("feedUsed", "appetite", +1, 3), d("live", "appetite", -1, 2),
        d("live", "bags", +1, 3), d("weightNow", "bags", +1, 2), d("appetite", "bags", +1, 2), d("heatLoad", "bags", -1, 1), d("correction", "bags", +1, 1),
        d("bags", "perBird", +1, 3), d("live", "perBird", -1, 3),
        d("feedUsed", "eatenY", +1, 3), d("live", "eatenY", -1, 2),
        d("weight", "gainToday", +1, 2), d("age", "gainToday", 0, 2), d("heatLoad", "gainToday", -1, 1),
        d("live", "pansOpen", +1, 2), d("floor", "pansOpen", +1, 2), d("pans", "pansOpen", +1, 2), d("age", "pansOpen", +1, 1),
        d("live", "birdsPerPan", +1, 3), d("pansOpen", "birdsPerPan", -1, 3),
        d("bags", "feedings", +1, 2), d("pansOpen", "feedings", -1, 1), d("heatLoad", "feedings", 0, 1),
        d("pansFilled", "hungry", -1, 3), d("feedings", "hungry", -1, 2), d("clock", "hungry", 0, 2), d("lightHours", "hungry", 0, 1), d("heatLoad", "hungry", -1, 1),
        d("pansOpen", "walkFeed", -1, 3), d("floor", "walkFeed", +1, 1), d("feederLines", "walkFeed", -1, 2),
        d("weight", "correction", -1, 2), d("fcr", "correction", -1, 1),
        // lifting
        d("liftedKg", "liftAvg", +1, 3), d("lifted", "liftAvg", -1, 3),
        d("live", "kgInHouse", +1, 3), d("weightNow", "kgInHouse", +1, 3),
        d("deaths", "deadKg", +1, 3), d("culls", "deadKg", +1, 1), d("weight", "deadKg", +1, 2),
        d("kgInHouse", "produced", +1, 3), d("liftedKg", "produced", +1, 3),
        // air
        d("outTemp", "houseTemp", +1, 3), d("fansOn", "houseTemp", -1, 2), d("padsWet", "houseTemp", -1, 2), d("birdHeat", "houseTemp", +1, 2), d("sensors", "houseTemp", 0, 3),
        d("outRh", "houseRh", +1, 3), d("padsWet", "houseRh", +1, 2), d("fansOn", "houseRh", -1, 1), d("sensors", "houseRh", 0, 3),
        d("fansOn", "airSpeed", +1, 3), d("fans", "airSpeed", +1, 1),
        d("live", "minVent", +1, 3), d("weightNow", "minVent", +1, 2),
        d("minVent", "fansOn", +1, 3), d("houseTemp", "fansOn", +1, 2, lag = true), d("fans", "fansOn", 0, 1),
        d("outTemp", "padsWet", +1, 2), d("pads", "padsWet", 0, 1),
        d("feedTD", "litterMoist", +1, 2), d("density", "litterMoist", +1, 2), d("houseRh", "litterMoist", +1, 2), d("airSpeed", "litterMoist", -1, 2),
        d("litterMoist", "ammonia", +1, 3), d("houseTemp", "ammonia", +1, 1), d("minVent", "ammonia", -1, 2),
        d("age", "lightHours", 0, 3),
        // water
        d("bags", "water", +1, 3), d("outTemp", "water", +1, 2),
        d("water", "waterPerBird", +1, 3), d("live", "waterPerBird", -1, 3),
        d("water", "refills", +1, 3), d("tank", "refills", -1, 3),
        d("live", "birdsPerNipple", +1, 3), d("nipples", "birdsPerNipple", -1, 3),
        d("nipples", "walkWater", -1, 2), d("drinkerLines", "walkWater", -1, 2), d("floor", "walkWater", +1, 1),
        d("age", "nippleHeight", +1, 3),
        d("heatLoad", "thirsty", +1, 2), d("clock", "thirsty", 0, 2), d("hungry", "thirsty", 0, 1),
        // the house
        d("house", "floor", +1, 3), d("barricades", "floor", -1, 3),
    )

    /** How the things of the house hang together; [relationProps] fills in each relation's own properties. */
    val relations: List<Edge> = listOf(
        r("house", "feederLines", "has"), r("feederLines", "pans", "carry"), r("house", "drinkerLines", "has"),
        r("drinkerLines", "nipples", "carry"), r("tank", "drinkerLines", "feeds"), r("house", "fans", "has"),
        r("house", "pads", "has"), r("barricades", "floor", "bound"), r("godown", "feederLines", "fills"),
        r("trucks", "lifted", "took"), r("placed", "house", "in"),
    )

    /** Which properties each event changes. */
    val changes: List<Edge> = EventKind.values().flatMap { k -> k.changes.map { Edge(k.nodeKey, it, EdgeKind.CHANGES) } }

    val edges: List<Edge> get() = depends + relations + changes

    private val byKey: Map<String, Prop> by lazy { props.associateBy { it.key } }
    fun prop(key: String): Prop? = byKey[key]

    /** What [key] is worked out from (DEPENDS), strongest first. */
    fun inputsOf(key: String): List<Edge> = depends.filter { it.to == key }.sortedByDescending { it.strength }
    /** What [key] moves (DEPENDS), strongest first. */
    fun outputsOf(key: String): List<Edge> = depends.filter { it.from == key }.sortedByDescending { it.strength }
    /** Events that change [key]. */
    fun eventsOf(key: String): List<EventKind> = EventKind.values().filter { key in it.changes }

    /** Everything [key] is worked out from, near and far (each once). */
    fun upstream(key: String): Set<String> {
        val seen = LinkedHashSet<String>(); val todo = ArrayDeque(listOf(key))
        while (todo.isNotEmpty()) { val k = todo.removeFirst(); inputsOf(k).forEach { if (seen.add(it.from)) todo.add(it.from) } }
        return seen
    }

    /**
     * Why [key] is not an entered value: the inputs that are projected or modelled (per [kindOf], true = entered or set),
     * strongest first. Empty when every input is known.
     */
    fun why(key: String, isKnown: (String) -> Boolean?): List<String> =
        inputsOf(key).filter { isKnown(it.from) == false }.map { it.from }

    /** Problems with the registry (an unknown key, a loop in what is worked out from what); empty when sound. */
    fun check(): List<String> {
        val out = mutableListOf<String>()
        val dup = props.groupBy { it.key }.filter { it.value.size > 1 }.keys
        if (dup.isNotEmpty()) out += "keys used twice: $dup"
        edges.forEach { e -> listOf(e.from, e.to).filter { byKey[it] == null }.forEach { out += "unknown key '$it' in ${e.from} → ${e.to}" } }
        // a loop in DEPENDS would make a number depend on itself
        val state = HashMap<String, Int>()
        fun visit(k: String, path: List<String>) {
            when (state[k]) { 1 -> { out += "loop: " + (path + k).joinToString(" → "); return }; 2 -> return }
            state[k] = 1
            outputsOf(k).filter { !it.lag }.forEach { visit(it.to, path + k) }
            state[k] = 2
        }
        props.forEach { visit(it.key, emptyList()) }
        return out
    }

    /**
     * The things' relations with their own properties, from the farm's settings and the flock: e.g.
     * feeder lines —carry→ pans: "112 a line · 2.5 ft apart".
     */
    fun relationProps(farm: FarmEntity, flock: FlockEntity?, liftings: Int, barricadeText: String): Map<Pair<String, String>, String> {
        fun n(x: Double) = if (x % 1.0 == 0.0) x.toInt().toString() else String.format("%.1f", x)
        return mapOf(
            ("house" to "feederLines") to "${farm.feederLines} lines",
            ("feederLines" to "pans") to "${farm.pansPerFeederLine} a line · ${n(farm.panSpacingFt)} ft apart",
            ("house" to "drinkerLines") to "${farm.drinkerLines} lines",
            ("drinkerLines" to "nipples") to "${farm.nipplesPerLine} a line",
            ("tank" to "drinkerLines") to "${n(farm.drinkTankL)} L",
            ("house" to "fans") to "${farm.fanCount} × ${n(farm.fanRatedCfm)} cfm",
            ("house" to "pads") to farm.padLayout,
            ("barricades" to "floor") to barricadeText,
            ("godown" to "feederLines") to "${n(farm.godownBags.toDouble())} bags room",
            ("trucks" to "lifted") to "$liftings liftings",
            ("placed" to "house") to (flock?.let { "${it.birdsPlaced} on ${it.startDate}" } ?: "—"),
        )
    }
}
