package com.example.flock.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flock.engine.CompanyStandard
import com.example.flock.engine.PhysiologicalEngine
import com.example.flock.ui.Fmt
import com.example.ui.theme.GlassLine
import com.example.ui.theme.StatusCrit
import com.example.ui.theme.StatusWarn
import com.example.ui.theme.ValueIdeal
import com.example.ui.theme.ValuePresent
import kotlin.math.max
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.ceil

private val P = ValueKind.PRESENT
private val PR = ValueKind.PREDICTED
private val I = ValueKind.IDEAL
private val C = ValueKind.COMMERCIAL
private val MN = ValueKind.MIN
private val MX = ValueKind.MAX

private val TempDial = Color(0xFFFFB38A)
private val RhDial = Color(0xFFA9CCF0)

// =================================== page pieces ===================================

@Composable
fun SectionLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Normal, letterSpacing = 1.6.sp),
        color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(top = 10.dp))
}

@Composable
fun AlertList(alerts: List<TopicAlert>) {
    if (alerts.isEmpty()) return
    OutputCard(title = "Needs attention") {
        alerts.sortedByDescending { it.level }.take(6).forEach { a ->
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 6.dp).size(7.dp).border(4.dp, alertColor(a.level), RoundedCornerShape(4.dp)))
                Spacer(Modifier.width(10.dp))
                Text(a.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// =================================== TABS ===================================

internal fun normCdf(z: Double): Double {
    // Abramowitz & Stegun 7.1.26
    val t = 1 / (1 + 0.3275911 * kotlin.math.abs(z) / Math.sqrt(2.0))
    val y = 1 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * Math.exp(-z * z / 2)
    return if (z >= 0) (1 + y) / 2 else (1 - y) / 2
}

/** Bars of the share of birds in each 5 % step around the average; the even band (±10 %) stands out. */
@Composable
internal fun WeightHistogram(bins: List<Double>, mean: Double, measured: Boolean) {
    val top = bins.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val evenCol = if (measured) ValuePresent else ValueIdeal
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(130.dp)) {
        val px = density
        val padB = 30f * px; val padT = 16f * px
        val bw = size.width / bins.size
        val paint = android.graphics.Paint().apply { isAntiAlias = true; textSize = 10.5f * px; typeface = com.example.ui.theme.AppFonts.mono; textAlign = android.graphics.Paint.Align.CENTER }
        bins.forEachIndexed { i, f ->
            val h = ((f / top) * (size.height - padB - padT)).toFloat()
            val isEven = i in 3..6
            drawRect((if (isEven) evenCol else Color.White.copy(alpha = 0.4f)).copy(alpha = if (measured) 1f else 0.7f),
                androidx.compose.ui.geometry.Offset(i * bw + 2f * px, size.height - padB - h), androidx.compose.ui.geometry.Size(bw - 4f * px, h))
            if (f >= 0.005) { paint.color = Color.White.copy(alpha = 0.75f).toArgb(); drawContext.canvas.nativeCanvas.drawText(Fmt.n(f * 100, 0), i * bw + bw / 2, size.height - padB - h - 3f * px, paint) }
        }
        // the average and ±10 % marks, with weights under them
        paint.color = Color.White.copy(alpha = 0.6f).toArgb()
        listOf(-0.2, -0.1, 0.0, 0.1, 0.2).forEach { dev ->
            val x = ((dev + 0.25) / 0.5 * size.width).toFloat()
            drawLine(Color.White.copy(alpha = if (dev == 0.0) 0.8f else 0.3f), androidx.compose.ui.geometry.Offset(x, padT), androidx.compose.ui.geometry.Offset(x, size.height - padB), 1f * px)
            drawContext.canvas.nativeCanvas.drawText((if (dev > 0) "+" else "") + Fmt.n(dev * 100, 0) + "%", x, size.height - padB + 12f * px, paint)
            drawContext.canvas.nativeCanvas.drawText(Fmt.n(mean * (1 + dev), 0), x, size.height - padB + 25f * px, paint)
        }
    }
}


/** The farm's time zone (the phone's if the setting can't be read). */
fun farmZone(f: com.example.flock.data.FarmEntity): java.time.ZoneId = try { java.time.ZoneId.of(f.timeZone) } catch (e: Exception) { java.time.ZoneId.systemDefault() }

@Composable
fun StockBlock(d: OutputData) {
    val f = d.farm
    OutputCard(title = "Feed store", info = "stock") {
        if (f.godownBags > 0) {
            FillBar(d.stockBagsTotal / f.godownBags, Color.White.copy(alpha = 0.7f))
            Param("Godown", "bags", v(d.stockBagsTotal, 2, P, "In store"), v(f.godownBags, 1, MX, "Holds"), v(d.godownFree, 2, P, "Free"))
        }
        val maxBags = d.stockCodes.maxOfOrNull { max(d.recByCode[it] ?: 0.0, d.stockBags(it)) } ?: 0.0
        val unit = listOf(1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0).firstOrNull { maxBags / it <= 20 } ?: 200.0
        d.stockCodes.forEach { code -> FeedTypeBlock(d, code, unit) }
        Text("All types, bags", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Normal))
        StockRow(d.recBagsTD, d.usedBagsTD, d.stockBagsTotal)
        val canL = f.dieselCanL
        Param("Diesel", "L", v(d.e.dieselCansUsed * canL, 1, P, "Today"), v(d.dieselTD * canL, 1, P, "Till date"))
    }
}

@Composable
private fun FeedTypeBlock(d: OutputData, code: String, unit: Double) {
    val ft = d.feedTypes.firstOrNull { it.code == code }
    val rec = d.recByCode[code] ?: 0.0
    val used = d.usedByCode[code] ?: 0.0
    val stock = d.stockBags(code)
    Column(Modifier.fillMaxWidth().border(1.dp, GlassLine.copy(alpha = 0.35f), RoundedCornerShape(10.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$code · ${ft?.name ?: "Feed"}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Normal), modifier = Modifier.weight(1f))
            Text(Fmt.n(stock, 2), style = MaterialTheme.typography.titleMedium.copy(fontFamily = com.example.ui.theme.NumberFont, fontWeight = FontWeight.Normal),
                color = if (stock < 0) StatusCrit else kindColor(P))
        }
        if (rec > 0 || used > 0) SackStrip(max(0.0, stock) / unit, kotlin.math.min(used, rec) / unit, Color.White.copy(alpha = 0.8f))
        StockRow(rec, used, stock)
    }
}

/** Received (into the store), used (out of it) and what is in store, each with its symbol. */
@Composable
private fun StockRow(rec: Double, used: Double, stock: Double) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StockChip(Icons.Filled.Download, "Received", v(rec, 2, P), Modifier.weight(1f))
        StockChip(Icons.Filled.Upload, "Used", v(used, 2, P), Modifier.weight(1f))
        StockChip(Icons.Filled.Inventory2, "In store", v(stock, 2, if (stock < 0) MX else P), Modifier.weight(1f))
    }
}

@Composable
private fun StockChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, x: V, modifier: Modifier = Modifier) {
    val col = kindColor(x.kind)
    Column(modifier.border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(icon, contentDescription = label, tint = col.copy(alpha = 0.9f), modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(3.dp))
            Text(label, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal, fontSize = 10.5.sp, letterSpacing = (-0.1).sp), color = col.copy(alpha = 0.9f))
        }
        Text(x.text, maxLines = 1, softWrap = false,
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = com.example.ui.theme.NumberFont, fontWeight = FontWeight.Normal, fontSize = 15.sp, letterSpacing = (-0.3).sp), color = col)
    }
}

// =================================== VENTILATION ===================================

@Composable
fun VentSection(d: OutputData, hour: Int = d.hour) {
    // the dials follow the weather at the farm; a value set by hand holds for 30 seconds, then they go back to it
    val wx = d.outsideHours.getOrNull(hour.coerceIn(0, 23))
    val autoT = ((wx?.second ?: d.hottestC) * 2).let { Math.round(it) / 2.0 }
    val autoRh = ((wx?.third ?: 60.0) / 5).let { Math.round(it) * 5.0 }.coerceIn(10.0, 100.0)
    var manual by remember(d.day) { androidx.compose.runtime.mutableStateOf<Pair<Double, Double>?>(null) }
    var left by remember { mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(manual) {
        if (manual != null) { for (sec in MANUAL_HOLD_S downTo 1) { left = sec; kotlinx.coroutines.delay(1000) }; manual = null }
        left = 0
    }
    val tC = manual?.first ?: autoT
    val rh = manual?.second ?: autoRh
    FanFinderCard(d, tC, rh, if (manual != null) left else null, { manual = it to rh }, { manual = tC to it }) {
        CoolingGridCard(d) { t, h -> manual = t to h }
    }
}

/** how long a temperature or humidity set by hand holds before the dials go back to the weather */
const val MANUAL_HOLD_S = 30

/** The fans for an outside temperature and humidity: the weather at the farm, or what the dials are set to for a moment. */
@Composable
private fun FanFinderCard(d: OutputData, tC: Double, rh: Double, heldS: Int?, setT: (Double) -> Unit, setRh: (Double) -> Unit, more: @Composable () -> Unit = {}) {
    val f = d.farm
    val e = d.e
    val live = d.liveSafe.toDouble()
    OutputCard(title = "Fans for the air outside", info = "fanfinder") {
        Text(if (heldS != null) "Set by hand · back to the weather in $heldS s"
             else if (d.outsideSource == com.example.flock.domain.BirdEnvironment.Source.WEATHER) "The weather at the farm now · turn a dial to try another"
             else "The season's typical day (no forecast) · turn a dial to try another",
            style = MaterialTheme.typography.labelMedium, color = if (heldS != null) kindColor(PR) else Color.White.copy(alpha = 0.65f), modifier = Modifier.testTag("dial_note"))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Knob("Outside", "°C", tC, 5.0, 48.0, 0.5, TempDial, setT)
            Knob("Outside", "% RH", rh, 10.0, 100.0, 5.0, RhDial, setRh)
        }
        val s = remember(tC, rh, d) { d.sim(tC, rh, d.hour) }
        val lv = d.levelOf(s)
        val cfm = s.fans * d.fanCfm
        HouseAirflow(lv, f.fanCount, f.hasEC, s.padEff > 0, s.heaterKw > 0.05, d.airFpm(s.fans))
        Param("Fans", "", v(s.fans, 2, PR, "Average"), vi(lv.fansOn, PR, "On"), vi(d.plan.maxFans, MX, "Allowed"), vi(s.level, PR, "Level"), strong = true)
        Param("House", "", v(s.houseC, 1, PR, "°C"), v(s.houseRh, 1, PR, "% RH"), v(s.feltC, 1, PR, "Birds feel °C"))
        Param("Air", "cfm", listOf("per bird" to listOf(v(cfm / live, 3, PR)), "house" to listOf(v(cfm, 1, PR))))
        RangeParam("Air speed", "ft/min", null, null, PhysiologicalEngine.maxAirSpeedFpm(d.day), null, projected = d.airFpm(s.fans))
        Param("Heat and pads", "", v(s.heaterKw, 1, PR, "Heat kW"), v(s.padEff * 100, 1, PR, "Pads %"))
        more()
    }
}

@Composable
private fun CoolingGridCard(d: OutputData, onPick: (Double, Double) -> Unit) {
    val states = remember(d) { d.gridTemps.map { t -> d.gridRh.map { h -> d.sim(t, h) } } }
    Part("Fans by outside air", "grid") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Spacer(Modifier.width(56.dp))
            d.gridRh.forEach { h -> Text("${Fmt.n(h, 1)}%", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge, color = RhDial) }
        }
        d.gridTemps.forEachIndexed { i, t ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${Fmt.n(t, 1)}°", Modifier.width(56.dp), style = MaterialTheme.typography.labelLarge, color = TempDial)
                d.gridRh.forEachIndexed { j, h ->
                    val s = states[i][j]
                    val over = s.feltC - d.plan.comfort
                    val col = when { over > 4 -> StatusCrit; over > 2 -> StatusWarn; over < -3 -> ValueIdeal; else -> ValuePresent }
                    Column(Modifier.weight(1f).border(1.dp, GlassLine, RoundedCornerShape(10.dp)).clickable { onPick(t, h) }.padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(Fmt.n(s.fans, 1), style = MaterialTheme.typography.titleMedium.copy(fontFamily = com.example.ui.theme.NumberFont, fontWeight = FontWeight.Normal), color = col)
                        Text("${Fmt.n(s.feltC, 1)}°", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                    }
                }
            }
        }
        KeyLine(ValuePresent to "comfy", StatusWarn to "warm", StatusCrit to "hot", ValueIdeal to "cool")
    }
}

// =================================== MORTALITY ===================================


// =================================== ENVIRONMENT ===================================

