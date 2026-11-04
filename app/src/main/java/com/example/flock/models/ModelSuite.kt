package com.example.flock.models

/**
 * The suite of the app's prediction and projection models, kept apart from the screens so each can be replaced,
 * re-weighted or re-fitted without touching the app. Pure Kotlin, no Android (the file and export side is
 * [ModelStore]).
 *
 * How it is put together:
 *  - Every model reads the same [Sources]: what was entered by hand, the weather at the farm, and sensor readings
 *    (none yet — [SensorReading] is the place for them; a model uses a reading where one exists and its own estimate
 *    elsewhere, and says which in [Provenance]).
 *  - Every model's weights live in [ModelParams] under its id ("house_map.drinker_wet_pts" …), with the defaults in
 *    the code and overrides from `assets/models/params.json` or a newer file the app is given (see [ModelStore]), so
 *    a better fit can ship without a new app.
 *  - Every run can be kept as a [ModelRecord] (model id and version, the inputs it read, what it gave) — the long-term
 *    data, with as few hand entries as possible, to compare predictions against what happened and fit the weights.
 *
 * The registry ([MODELS]) lists what is in the suite and where each lives.
 */
object ModelSuite {
    data class Info(val id: String, val version: Int, val name: String, val lives: String, val reads: String)

    val MODELS = listOf(
        Info("house_map", HouseMap.VERSION, "The house floor, cell by cell", "models/HouseMap.kt",
            "the barricades, lines, birds and weight, the fans and air, the weather, the litter model, sensor readings"),
        Info("travel", 1, "Walk to feed and water", "domain/Travel.kt", "line and pan / nipple spacing, the pans open, trays, age"),
        Info("litter", 1, "Litter depth, moisture, ammonia", "domain/Litter.kt", "age, kg/m², feed eaten, house air, air speed"),
        Info("space_plan", 1, "Floor, pans and feedings", "domain/SpacePlan.kt", "birds, weight, barricades, pans, bags, weather"),
        Info("intake", 1, "The flock's appetite", "domain/IntakeForecast.kt", "feed planned and entered"),
        Info("growth", 1, "Weight to the target", "domain/GrowthForecast.kt", "weighings, the company curve"),
        Info("projection", 1, "The day projected from the days before", "domain/Projection.kt + data/FlockCalc.kt", "every entry"),
        Info("needs", 1, "Hungry, thirsty, panting", "domain/FlockNeeds.kt", "feedings, lights, heat load"),
        Info("environment", 1, "The weather → house → birds", "domain/BirdEnvironment.kt + engine/IbController.kt", "weather, fans, birds"),
        Info("radar", 1, "Birds against the company", "domain/Radar.kt", "the day's KPIs")
    )
}

/** Where a value comes from: entered by hand, a sensor, the weather, or the model's own estimate. */
enum class Provenance { ENTERED, SENSOR, WEATHER, MODEL }

/** One sensor reading somewhere in the house (none yet): what, where along and across the house (ft), when, the value. */
data class SensorReading(val kind: Kind, val xFt: Double, val yFt: Double, val atMs: Long, val value: Double) {
    enum class Kind { AIR_C, RH_PCT, NH3_PPM, CO2_PPM, LITTER_MOIST_PCT, AIR_SPEED_FPM, STATIC_PA }
}

/** The weather at the farm for an hour (outside air), and the hand entries a model may read. */
data class Sources(
    val outsideC: Double?, val outsideRh: Double?,
    val sensors: List<SensorReading> = emptyList()
) {
    fun readings(kind: SensorReading.Kind) = sensors.filter { it.kind == kind }
}

/**
 * The models' weights: "model.key" → number. Defaults are in each model; a value here overrides it. Built from the
 * bundled JSON and any newer file (see [ModelStore]); empty in tests (the defaults).
 */
class ModelParams(private val values: Map<String, Double> = emptyMap(), val version: Int = 0) {
    fun get(model: String, key: String, default: Double): Double = values["$model.$key"] ?: default
    fun keys(): Set<String> = values.keys
    companion object { val DEFAULTS = ModelParams() }
}

/** One run of a model, kept for the long-term data: which model and version, the day and time, what it read, what it gave. */
data class ModelRecord(
    val model: String, val version: Int, val paramsVersion: Int,
    val flockId: String, val day: Int, val atMs: Long,
    val inputs: Map<String, Any?>, val outputs: Map<String, Any?>
)
