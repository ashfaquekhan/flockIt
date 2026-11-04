package com.example.flock.models

import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The model suite's files on the phone:
 *  - the weights: `assets/models/params.json` (shipped with the app), overridden by `files/models/params.json` when a
 *    newer one is put there ({"version": n, "models": {"house_map": {"drinker_wet_pts": 7.0, …}, …}});
 *  - the log: every model run kept for a flock as one JSON line in `files/models/log-<flock>.jsonl` (the newest run for
 *    a day and model replaces the older one), to export and fit the models on real flocks.
 */
object ModelStore {
    private const val DIR = "models"
    @Volatile private var cached: ModelParams? = null

    /** The weights: the newer of the shipped file and the phone's own (by "version"); defaults when neither reads. */
    fun params(ctx: Context): ModelParams = cached ?: run {
        val shipped = runCatching { ctx.assets.open("$DIR/params.json").bufferedReader().use { it.readText() } }.getOrNull()?.let(::parse)
        val local = runCatching { File(ctx.filesDir, "$DIR/params.json").takeIf { it.exists() }?.readText() }.getOrNull()?.let(::parse)
        (listOfNotNull(shipped, local).maxByOrNull { it.version } ?: ModelParams.DEFAULTS).also { cached = it }
    }

    fun parse(json: String): ModelParams? = runCatching {
        val o = JSONObject(json)
        val values = HashMap<String, Double>()
        val models = o.optJSONObject("models") ?: JSONObject()
        models.keys().forEach { m ->
            val mo = models.optJSONObject(m) ?: return@forEach
            mo.keys().forEach { k ->
                val v = mo.opt(k)
                when (v) { is Number -> values["$m.$k"] = v.toDouble(); is Boolean -> values["$m.$k"] = if (v) 1.0 else 0.0 }
            }
        }
        ModelParams(values, o.optInt("version", 0))
    }.getOrNull()

    private fun logFile(ctx: Context, flockId: String) = File(File(ctx.filesDir, DIR).apply { mkdirs() }, "log-" + flockId.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".jsonl")

    private fun toJson(v: Any?): Any? = when (v) {
        null -> JSONObject.NULL
        is Map<*, *> -> JSONObject().also { o -> v.forEach { (k, x) -> o.put(k.toString(), toJson(x)) } }
        is List<*> -> JSONArray().also { a -> v.forEach { a.put(toJson(it)) } }
        is Double -> if (v.isNaN() || v.isInfinite()) JSONObject.NULL else v
        else -> v
    }

    /** Keeps [r] in the flock's log (replacing the same model's earlier run for that day). Never throws. */
    @Synchronized fun record(ctx: Context, r: ModelRecord) {
        runCatching {
            val f = logFile(ctx, r.flockId)
            val line = JSONObject().apply {
                put("model", r.model); put("version", r.version); put("paramsVersion", r.paramsVersion)
                put("flockId", r.flockId); put("day", r.day); put("atMs", r.atMs)
                put("inputs", toJson(r.inputs)); put("outputs", toJson(r.outputs))
            }.toString()
            val kept = if (f.exists()) f.readLines().filter { l ->
                runCatching { JSONObject(l).let { !(it.optString("model") == r.model && it.optInt("day") == r.day) } }.getOrDefault(false)
            } else emptyList()
            f.writeText((kept + line).joinToString("\n") + "\n")
        }
    }

    /** Hands the flock's log to another app (mail, Drive …) as a file; false when there is nothing to send. */
    fun export(ctx: Context, flockId: String): Boolean {
        val f = logFile(ctx, flockId)
        if (!f.exists() || f.length() == 0L) return false
        return runCatching {
            val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "FlockIt model log · ${f.name}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(send, "Export the model log").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }
}
