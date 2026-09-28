package com.example.flock.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flock.data.DailyDataEntity
import com.example.flock.data.FarmEntity
import com.example.flock.data.FeedStockSummary
import com.example.flock.data.FeedTypeEntity
import com.example.flock.data.FlockEntity
import com.example.flock.network.HourPoint
import com.example.flock.network.WeatherResult
import com.example.flock.ui.Fmt
import com.example.ui.theme.BrandEmerald
import com.example.ui.theme.StatusCrit
import com.example.ui.theme.StatusWarn
import com.example.ui.theme.ValueCommercial
import com.example.ui.theme.ValueCommercialWash
import com.example.ui.theme.ValueIdeal
import com.example.ui.theme.ValueIdealWash
import com.example.ui.theme.ValuePredicted
import com.example.ui.theme.ValuePredictedWash
import com.example.ui.theme.ValuePresent
import com.example.ui.theme.ValuePresentWash
import kotlin.math.abs

/**
 * How a displayed number was produced — every value carries one of these tags and its colour:
 * PRESENT = logged / measured, PREDICTED ("Projected") = estimated because nothing was logged,
 * IDEAL = Ross 308 breed objective, COMMERCIAL = the company's all-branches standard.
 */
enum class ValueKind { PRESENT, PREDICTED, IDEAL, COMMERCIAL, NEUTRAL }

fun kindColor(k: ValueKind): Color = when (k) {
    ValueKind.PRESENT -> ValuePresent
    ValueKind.PREDICTED -> ValuePredicted
    ValueKind.IDEAL -> ValueIdeal
    ValueKind.COMMERCIAL -> ValueCommercial
    ValueKind.NEUTRAL -> Color(0xFF9AA0A6)
}

fun kindWash(k: ValueKind): Color = when (k) {
    ValueKind.PRESENT -> ValuePresentWash
    ValueKind.PREDICTED -> ValuePredictedWash
    ValueKind.IDEAL -> ValueIdealWash
    ValueKind.COMMERCIAL -> ValueCommercialWash
    ValueKind.NEUTRAL -> Color(0xFF26292E)
}

fun kindTag(k: ValueKind): String = when (k) {
    ValueKind.PRESENT -> "Present"
    ValueKind.PREDICTED -> "Projected"
    ValueKind.IDEAL -> "Ideal"
    ValueKind.COMMERCIAL -> "Commercial"
    ValueKind.NEUTRAL -> ""
}

/** Density is stored kg/m² but displayed in kg/ft² (1 m² = 10.7639 ft²). */
fun kgPerFt2(kgPerM2: Double): Double = kgPerM2 * 0.092903

@Composable
fun OutputScreen(
    flock: FlockEntity?,
    farm: FarmEntity,
    entry: DailyDataEntity?,
    dailyRows: List<DailyDataEntity>,
    feedStockSummary: FeedStockSummary,
    feedTypes: List<FeedTypeEntity> = emptyList(),
    weather: WeatherResult? = null,
    hourly: List<HourPoint> = emptyList(),
    isToday: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (entry == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No data for this day. Enter data in the ENTRY tab.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    val d = remember(flock, farm, entry, dailyRows, feedTypes, weather, hourly, isToday) {
        OutputData(flock, farm, entry, dailyRows, feedTypes, weather, hourly, isToday)
    }
    var topic by rememberSaveable { mutableStateOf(Topic.VENT.name) }
    val sel = Topic.valueOf(topic)

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TagLegend()
        TopicTiles(d, sel) { topic = it.name }
        TopicView(d, sel)
        Spacer(modifier = Modifier.height(32.dp))
    }
}

// =================================== tags ===================================

/** The four value tags and their colours. */
@Composable
fun TagLegend() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf(ValueKind.PRESENT, ValueKind.PREDICTED, ValueKind.IDEAL, ValueKind.COMMERCIAL).forEach { TagChip(it) }
    }
}

@Composable
fun TagChip(k: ValueKind, modifier: Modifier = Modifier, text: String = kindTag(k), compact: Boolean = false) {
    Surface(color = kindWash(k), shape = RoundedCornerShape(6.dp), modifier = modifier) {
        Row(Modifier.padding(horizontal = if (compact) 4.dp else 7.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center) {
            Box(Modifier.size(if (compact) 6.dp else 8.dp).background(kindColor(k), CircleShape))
            Spacer(Modifier.width(if (compact) 3.dp else 5.dp))
            Text(text, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                style = (if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium)
                    .copy(fontWeight = FontWeight.Bold, color = kindColor(k)))
        }
    }
}

// =================================== topic tiles ===================================

fun alertColor(level: Int): Color = when (level) { 2 -> StatusCrit; 1 -> StatusWarn; else -> BrandEmerald }

private fun headline(d: OutputData, t: Topic): Pair<String, ValueKind> = when (t) {
    Topic.VENT -> d.now?.let { "${Fmt.n(it.fans, 2)} fans now" to ValueKind.PREDICTED }
        ?: ("min ${Fmt.n(d.minLevel.avgFans, 2)} fans" to ValueKind.IDEAL)
    Topic.ENV -> (d.weather?.let { "out ${Fmt.n(it.tempC, 1)} °C" to ValueKind.PRESENT }
        ?: ("set ${Fmt.n(d.plan.comfort, 1)} °C" to ValueKind.IDEAL))
    Topic.BIRDS -> "${Fmt.n(d.bw, 1)} g" to d.vk
    Topic.FEED -> "${Fmt.n(d.giveBags, 2)} bags" to d.vk
    Topic.STOCK -> "${Fmt.n(d.stockBagsTotal, 2)} bags" to ValueKind.PRESENT
}

@Composable
fun TopicTiles(d: OutputData, selected: Topic, onSelect: (Topic) -> Unit) {
    val all = Topic.entries
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        all.chunked(3).forEach { rowTopics ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowTopics.forEach { t -> TopicTile(d, t, t == selected, Modifier.weight(1f)) { onSelect(t) } }
                repeat(3 - rowTopics.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun TopicTile(d: OutputData, t: Topic, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val lvl = d.worst(t)
    val n = d.alerts[t].orEmpty().size
    val (value, kind) = headline(d, t)
    val shape = RoundedCornerShape(12.dp)
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface,
        shape = shape, tonalElevation = 1.dp,
        modifier = modifier
            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), shape)
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.emoji, fontSize = 22.sp)
                Spacer(Modifier.weight(1f))
                Surface(color = alertColor(lvl), shape = CircleShape) {
                    Text(if (n > 0) "$n" else "✓", modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black, color = Color.White))
                }
            }
            Text(t.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp),
                color = kindColor(kind), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// =================================== card + shared bits ===================================

@Composable
fun OutputCard(
    title: String,
    content: @Composable () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(width = 4.dp, height = 18.dp).background(accent, RoundedCornerShape(2.dp)))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface))
            }
            content()
        }
    }
}

data class GistItem(val label: String, val value: String, val sub: String, val kind: ValueKind = ValueKind.NEUTRAL)

@Composable
fun GistTile(item: GistItem, modifier: Modifier = Modifier) {
    val valColor = if (item.value == "—") kindColor(ValueKind.NEUTRAL) else kindColor(item.kind)
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp), modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
            Text(item.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(item.value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 16.sp),
                color = valColor, maxLines = 2)
            Text(item.sub, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.kind != ValueKind.NEUTRAL && item.value != "—") {
                Spacer(Modifier.height(4.dp))
                TagChip(item.kind)
            }
        }
    }
}

/**
 * Population size distribution from today's 5-spot weight sample — how big the birds are and how
 * uniform the flock is.
 */
@Composable
fun PopulationDistributionCard(entry: DailyDataEntity) {
    data class Loc(val label: String, val avg: Double, val count: Int)
    val locs = buildList {
        val pairs = listOf(
            "Spot 1" to (entry.w1 to entry.n1), "Spot 2" to (entry.w2 to entry.n2), "Spot 3" to (entry.w3 to entry.n3),
            "Spot 4" to (entry.w4 to entry.n4), "Spot 5" to (entry.w5 to entry.n5)
        )
        for ((label, wn) in pairs) {
            val w = wn.first ?: 0.0
            val n = wn.second ?: 0
            if (w > 0 && n > 0) add(Loc(label, w / n, n))
        }
    }

    OutputCard(title = "⚖️ Bird size & uniformity") {
        if (locs.isEmpty()) {
            Text(
                "No weights entered today — weights shown are projected. Weigh 5 spots on the Entry tab to see the size spread.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@OutputCard
        }
        val totalN = locs.sumOf { it.count }
        val mean = entry.avgWeight ?: (locs.sumOf { it.avg * it.count } / totalN)
        val cv = entry.cv
        val minScale = mean * 0.8
        val maxScale = mean * 1.2

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GistTile(GistItem("Mean weight", "${Fmt.n(mean, 1)} g", "$totalN birds weighed", ValueKind.PRESENT), Modifier.weight(1f))
            GistTile(GistItem("CV", Fmt.n(cv, 2) + "%",
                if (cv == null) "need ≥ 2 spots" else if (cv < 10) "uniform" else if (cv < 12) "uneven" else "very uneven", ValueKind.PRESENT), Modifier.weight(1f))
            val spread = locs.maxOf { it.avg } - locs.minOf { it.avg }
            GistTile(GistItem("Spread", "${Fmt.n(spread, 1)} g", "lightest → heaviest", ValueKind.PRESENT), Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(160.dp).padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            for (loc in locs) {
                val frac = ((loc.avg - minScale) / (maxScale - minScale)).coerceIn(0.08, 1.0)
                val dev = if (mean > 0) (loc.avg - mean) / mean else 0.0
                val barColor = when {
                    abs(dev) <= 0.05 -> BrandEmerald
                    abs(dev) <= 0.10 -> StatusWarn
                    else -> StatusCrit
                }
                Column(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Text(Fmt.n(loc.avg, 1), style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold))
                    Box(modifier = Modifier.fillMaxWidth().height((frac * 110).dp).background(barColor, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)))
                    Text(loc.label, style = MaterialTheme.typography.labelMedium)
                    Text("${loc.count} birds", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val light = locs.count { it.avg < mean * 0.95 }
        val onTarget = locs.count { it.avg >= mean * 0.95 && it.avg <= mean * 1.05 }
        val heavy = locs.count { it.avg > mean * 1.05 }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BandChip("Light < 95%", light, StatusWarn, Modifier.weight(1f))
            BandChip("On target", onTarget, BrandEmerald, Modifier.weight(1f))
            BandChip("Heavy > 105%", heavy, StatusCrit, Modifier.weight(1f))
        }
    }
}

@Composable
private fun BandChip(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp), modifier = modifier) {
        Column(modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$count spots", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black, color = color, fontFamily = FontFamily.Monospace))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}
