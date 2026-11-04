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
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.draw.clip
import com.example.ui.theme.GlassFill
import com.example.ui.theme.GlassFillTop
import com.example.ui.theme.GlassLine
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
import com.example.flock.data.savedGroups
import kotlinx.coroutines.launch
import com.example.ui.theme.BrandEmerald
import com.example.ui.theme.StatusCrit
import com.example.ui.theme.StatusWarn
import com.example.ui.theme.ValueCommercial
import com.example.ui.theme.ValueCommercialWash
import com.example.ui.theme.ValueIdeal
import com.example.ui.theme.ValueIdealWash
import com.example.ui.theme.ValueMax
import com.example.ui.theme.ValueMaxWash
import com.example.ui.theme.ValueMin
import com.example.ui.theme.ValueMinWash
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
enum class ValueKind { PRESENT, PREDICTED, IDEAL, COMMERCIAL, MIN, MAX, NEUTRAL }

fun kindColor(k: ValueKind): Color = when (k) {
    ValueKind.PRESENT -> ValuePresent
    ValueKind.PREDICTED -> ValuePredicted
    ValueKind.IDEAL -> ValueIdeal
    ValueKind.COMMERCIAL -> ValueCommercial
    ValueKind.MIN -> ValueMin
    ValueKind.MAX -> ValueMax
    ValueKind.NEUTRAL -> Color(0xFF9AA0A6)
}

fun kindWash(k: ValueKind): Color = when (k) {
    ValueKind.PRESENT -> ValuePresentWash
    ValueKind.PREDICTED -> ValuePredictedWash
    ValueKind.IDEAL -> ValueIdealWash
    ValueKind.COMMERCIAL -> ValueCommercialWash
    ValueKind.MIN -> ValueMinWash
    ValueKind.MAX -> ValueMaxWash
    ValueKind.NEUTRAL -> Color(0xFF26292E)
}

fun kindTag(k: ValueKind): String = when (k) {
    ValueKind.PRESENT -> "Present"
    ValueKind.PREDICTED -> "Projected"
    ValueKind.IDEAL -> "Ideal"
    ValueKind.COMMERCIAL -> "Commercial"
    ValueKind.MIN -> "Min"
    ValueKind.MAX -> "Max"
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
    onCloseBatch: (() -> Unit)? = null,
    onFarmChange: ((FarmEntity) -> Unit)? = null,
    modifier: Modifier = Modifier,
    /** sets the flock's target weight (g) and planned harvest day */
    onFlockPlan: ((Double, Int) -> Unit)? = null,
    /** a feeding poured: bags, at (epoch ms) — kept on the sheet with the day */
    onLogFeeding: (Double, Long) -> Unit = { _, _ -> },
    onUndoFeeding: () -> Unit = {},
    /** the weather at the farm acts on the house and the birds */
    weatherOn: Boolean = false,
    onWeatherOn: (Boolean) -> Unit = {},
    /** opens another day (a day tapped in the flock book) */
    onSelectDay: ((Int) -> Unit)? = null
) {
    // one scroll position for every day; the section being read stays put when the day changes
    val scroll = rememberScrollState()
    val anchors = remember { ScrollAnchors(scroll) }
    val day = entry?.dayNumber ?: Int.MIN_VALUE
    anchors.hasContent = entry != null
    androidx.compose.runtime.SideEffect { anchors.dayShown(day) }
    androidx.compose.runtime.LaunchedEffect(Unit) { androidx.compose.runtime.snapshotFlow { scroll.value }.collect { anchors.capture() } }
    androidx.compose.runtime.LaunchedEffect(day) {
        repeat(2) { androidx.compose.runtime.withFrameNanos { } }
        anchors.restoreTarget()?.let { scroll.scrollTo(it) }
        anchors.settled(day)
    }
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
    val d = remember(flock, farm, entry, dailyRows, feedTypes, weather, hourly, isToday, weatherOn) {
        OutputData(flock, farm, entry, dailyRows, feedTypes, weather, hourly, isToday, weatherOn)
    }
    // feedings logged (on the sheet with their day; older ones kept on this phone) drive the coop's feeder; re-worked every 30 s
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val flockKey = flock?.flockId ?: "none"
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    androidx.compose.runtime.LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(30_000); now = System.currentTimeMillis() } }
    val zone = remember(farm.timeZone) { try { java.time.ZoneId.of(farm.timeZone) } catch (e: Exception) { java.time.ZoneId.systemDefault() } }
    val localEvents = remember(flockKey) { FeedLog.load(ctx, flockKey) }
    // feedings: those on the sheet, and on a day with none the model's own (the farm window feeds the birds at them)
    val events = remember(dailyRows, localEvents, zone, d) {
        val logged = feedEvents(dailyRows, localEvents, zone)
        val date = try { java.time.LocalDate.parse(entry.date) } catch (e: Exception) { null }
        val onDay = logged.any { date != null && java.time.Instant.ofEpochMilli(it.t).atZone(zone).toLocalDate() == date }
        if (date == null || onDay) logged else logged + d.daySchedule(d.feedings).feeds.map { h ->
            FeedEvent(date.atStartOfDay(zone).toInstant().toEpochMilli() + (h * 3600_000).toLong(), d.bagsPerFeeding) }.sortedBy { it.t }
    }
    val light = LightProgram(entry.lightHours)
    // the time the page shows: today the clock (until the time slider is moved), another day midday
    val nowZ = remember(now, zone) { java.time.Instant.ofEpochMilli(now).atZone(zone) }
    var pinned by remember(d.day) { mutableStateOf<Int?>(null) }
    val live = isToday && pinned == null
    val minutes = pinned ?: if (isToday) nowZ.hour * 60 + nowZ.minute else 12 * 60
    val dayDate = remember(entry.date) { try { java.time.LocalDate.parse(entry.date) } catch (e: Exception) { null } }
    val viewZ = if (live || dayDate == null) nowZ else dayDate.atTime(minutes / 60, minutes % 60).atZone(zone)
    val viewMs = viewZ.toInstant().toEpochMilli()
    val view = remember(d, viewZ) { d.nowView(viewZ) }
    val hour = viewZ.hour
    // the house at that hour, worked out from the weather (null with the switch off: the ideal house)
    val air = d.envAt(hour)
    // hungry, thirsty, panting at that time: likelihoods from the feedings (those logged; else the plan's), the lights and the weather
    val viewHour = hour + viewZ.minute / 60.0
    val logged = remember(events, viewMs, d) { loggedFeedings(events, viewMs, zone, d.kgPerBag(d.phase)) }
    val fedKnown = logged.isNotEmpty()
    val fed = if (fedKnown) logged else d.plannedFeedings
    val needsNow = remember(d, viewMs, events) { d.needsAt(viewHour, fed) }
    val feeder = remember(events, viewMs, d) {
        feederState(events, zone, needsNow, (d.bagsFillOpen * d.kgPerBag(d.phase)).takeIf { it > 0 } ?: d.giveKg)
    }
    // when the day's entries were made: the weighing, else the lifting, else the last save on that day
    val entryHHmm = remember(entry) {
        listOf(entry.weighedAt, entry.liftedAt).firstOrNull { it > 0 }?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalTime() }
            ?.let { String.format("%02d:%02d", it.hour, it.minute) }
            ?: entry.updatedAt.takeIf { it > 0 && entry.savedGroups().isNotEmpty() }?.let { java.time.Instant.ofEpochMilli(it).atZone(zone) }
                ?.takeIf { it.toLocalDate() == dayDate }?.let { String.format("%02d:%02d", it.hour, it.minute) }
    }
    // the house floor place by place at the time shown (the model suite's house map)
    val houseMap = remember(d, hour) { houseMapOf(d, hour, com.example.flock.models.ModelStore.params(ctx)) }
    // kept for the long-term data: the house map's run and the day's inputs and projections (one line each a day)
    androidx.compose.runtime.LaunchedEffect(flockKey, d.day, isToday) {
        if (isToday && flock != null) {
            val t = System.currentTimeMillis()
            val runs = listOf(houseMap.record(flockKey, d.day, t), dayRecord(d, flockKey, t))
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runs.forEach { com.example.flock.models.ModelStore.record(ctx, it) } }
        }
    }
    val coop = CoopInput(
        age = d.day, meanG = view.weightG.takeIf { d.vk != ValueKind.PRESENT } ?: d.bw, cvPct = d.cvEst?.coerceIn(3.0, 20.0), live = d.live, entry = d.entryBirds, stage = d.stage, light = light, feeder = feeder,
        zoneId = zone, airC = air?.air?.tempC ?: entry.tempIdeal, rhPct = air?.air?.rhPct ?: entry.rhIdeal, feelsC = air?.r?.feltC ?: d.plan.comfort,
        chillC = com.example.flock.engine.IbController.levelChill(d.minLevel, d.day, d.plan.comfort, d.fanCfm, d.plan.crossFt2),
        pressurePa = 22.5, litterC = d.litterTemp.second, litterMoist = 25.0, bodyC = d.bodyTemp.second,
        waterC = 18.0 to 21.0, waterPh = 6.0 to 6.8, travelM = ALLOWED_TRAVEL_M,
        layout = FarmLayout(farm.usableLengthFt, farm.usableWidthFt, d.lineOrder, d.lineGapFt, d.lineStartFt, d.panSpacingFt, d.pansPerLine,
            d.sensorPans, d.feedPattern.on, d.feedPattern.off, d.pansInArea, NIPPLE_SPACING_FT, d.drinkerLenFt, d.drinkerHtIn * 0.0254,
            d.barricadeFtNow, d.live.toDouble() / d.areaInUseFt2, d.penStartFt),
        minVentCfmBird = d.minVentCfmBird, minVentCfm = d.minVentCfmBird * d.live, idealC = entry.tempIdeal,
        nh3Max = entry.nh3Max, co2Max = entry.co2Max, ventC = d.bodyTemp, feetC = d.footTemp.second,
        breaths = d.breathsIdeal, pantAbove = PANT_ABOVE_PER_MIN, weightMeasured = d.vk == ValueKind.PRESENT,
        heatLoadC = air?.r?.heatLoadC ?: 0.0, airModelled = air != null, bodyNowC = air?.r?.bodyTempC, breathsNow = air?.r?.breathsPerMin,
        stateWord = air?.r?.state?.label, airFrom = air?.air?.source?.label,
        densityKgFt2 = d.live * d.bwNow / 1000.0 / d.areaInUseFt2, cvEstimated = !d.cvMeasured, compact = false,
        hungry = needsNow.hungry, thirsty = needsNow.thirsty, panting = needsNow.panting,
        atMs = if (live) null else viewMs, ft2PerBird = d.space.ft2PerBird, roam = d.roam,
        map = houseMap, facts = coopFacts(d, view, air)
    )
    var confirmClose by remember { mutableStateOf(false) }

    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun minutesOf(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(zone).let { it.hour * 60 + it.minute }
    val entryMin = listOf(entry.weighedAt, entry.liftedAt).firstOrNull { it > 0 }?.let { minutesOf(it) }

    // the page's sections, in the order a day is worked: what to do first (feed, water, air), then how the flock is doing
    // the order of urgency: air can kill in minutes, water in hours, feed over the day; then the birds, their floor, the records
    val tabs = listOf("coop" to "Farm", "glance" to "Glance", "air" to "Air", "water" to "Water", "feed" to "Feed", "birds" to "Birds",
        "floor" to "Floor", "walk" to "Walk", "litter" to "Litter", "fcr" to "FCR", "graphs" to "Graphs", "book" to "Book")
    val dens = androidx.compose.ui.platform.LocalDensity.current
    var tabsPx by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val tabsDp = with(dens) { tabsPx.toDp() }
    val reading by remember { androidx.compose.runtime.derivedStateOf { anchors.currentOf(tabs.map { it.first }, scroll.value + tabsPx + with(dens) { 24.dp.toPx() }) } }
    // how many times to feed today: chosen in the feed card; the pans it opens count in the glance and the walk too
    var feedPick by rememberSaveable(d.day, d.recommendedOption) { androidx.compose.runtime.mutableIntStateOf(d.recommendedOption) }
    val pat = d.patternFor(feedPick)

    androidx.compose.runtime.CompositionLocalProvider(LocalScrollAnchors provides anchors) {
    Box(modifier.fillMaxSize().onGloballyPositioned { anchors.viewportTop = it.positionInRoot().y }) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(start = 14.dp, end = 14.dp, top = tabsDp + 10.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // the house and the birds in it, at the time on the rail
        Anchored("coop") { GlassBox(Modifier.fillMaxWidth()) {
            Coop3D(coop.copy(feedTimes = d.modelFeedTimes(d.feedOptions[feedPick.coerceIn(0, d.feedOptions.size - 1)].feedings)))
        } }
        // the day at a glance: the four radars and the twelve numbers that matter
        Anchored("glance") {
            GlanceCard(d, view, entryHHmm, live, { pinned = null }, hour, pat)
            AlertList(d.allAlerts)
        }
        Anchored("air") { VentTab(d, hour, needsNow, weatherOn, onWeatherOn) }
        Anchored("water") { WaterCard(d, onFarmChange) }
        // the feedings are the model's (shown in the farm window); nothing to log by hand
        Anchored("feed") { FeedTodayCard(d, feedPick, { feedPick = it }) }
        Anchored("birds") { BirdsTab(d, onFlockPlan) }
        Anchored("floor") { SpaceCard(d) }
        Anchored("walk") { WalkCard(d, pat) }
        Anchored("litter") { LitterCard(d, hour) }
        Anchored("fcr") { LiftingCard(d) }
        Anchored("graphs") { GraphsCard(d) }
        Anchored("book") { FlockBookCard(d, onSelectDay) }
        if (onCloseBatch != null && flock?.status != "closed") {
            OutlinedButton(onClick = { confirmClose = true }, modifier = Modifier.fillMaxWidth(),
                border = androidx.compose.foundation.BorderStroke(1.dp, GlassLine)) {
                Text("Close batch and save", color = MaterialTheme.colorScheme.onSurface)
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
    // the sections, always at the top: the one being read is lit, a tap goes to another
    SectionTabs(tabs, reading, { key ->
        anchors.topOf(key)?.let { y -> scope.launch { scroll.animateScrollTo((y - tabsPx - with(dens) { 8.dp.toPx() }.toInt()).coerceAtLeast(0)) } }
    }, Modifier.align(Alignment.TopStart).onGloballyPositioned { tabsPx = it.size.height })
    // the time of the day: a rail down the right edge, opened by touching it
    TimeRail(minutes, if (isToday) nowZ.hour * 60 + nowZ.minute else null, entryMin, light, { pinned = it }, { pinned = null },
        Modifier.align(Alignment.CenterEnd).padding(top = tabsDp, bottom = 64.dp))
    // back to the present in one tap, wherever the page is scrolled to
    if (isToday && pinned != null) NowButton({ pinned = null }, Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 12.dp))
    }
    }
    if (confirmClose) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text("Close ${flock?.name ?: "batch"}?") },
            text = { Text("Saves every day of this batch to the Google Sheet, then marks it closed and read-only.") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { confirmClose = false; onCloseBatch?.invoke() }) { Text("Close batch") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmClose = false }) { Text("Cancel") } }
        )
    }
}

/**
 * The day clock — feed, water, walks, lights and the hot hours round the day — for the Tasks page, under the tasks.
 */
@Composable
fun DayClockSection(
    flock: FlockEntity?, farm: FarmEntity, entry: DailyDataEntity?, dailyRows: List<DailyDataEntity>, feedTypes: List<FeedTypeEntity>,
    weather: WeatherResult?, hourly: List<HourPoint>, isToday: Boolean, weatherOn: Boolean
) {
    if (entry == null) return
    val d = remember(flock, farm, entry, dailyRows, feedTypes, weather, hourly, isToday, weatherOn) {
        OutputData(flock, farm, entry, dailyRows, feedTypes, weather, hourly, isToday, weatherOn)
    }
    val opt = d.feedOptions.getOrNull(d.recommendedOption)
    OutputCard(title = "Day clock", info = "clock") {
        FarmDayClock(d.daySchedule(opt?.feedings ?: d.feedings), farmZone(d.farm),
            amounts = DayAmounts(opt?.bagsPerFeeding, opt?.bagsPerLine, d.waterL / d.waterRefills))
    }
}

/** The feed store on its own page: what came in, what was used and what is left, by feed type. */
@Composable
fun StockScreen(
    flock: FlockEntity?, farm: FarmEntity, entry: DailyDataEntity?, dailyRows: List<DailyDataEntity>,
    feedTypes: List<FeedTypeEntity> = emptyList(), modifier: Modifier = Modifier
) {
    if (entry == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No data for this day. Enter data in the ENTRY tab.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val d = remember(flock, farm, entry, dailyRows, feedTypes) { OutputData(flock, farm, entry, dailyRows, feedTypes, null, emptyList(), false) }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AlertList(d.stockAlerts)
        StockBlock(d)
        Spacer(modifier = Modifier.height(32.dp))
    }
}

/**
 * Log a feeding for the birds in the window: the bags poured into the lines and the time they were given
 * (now, unless another time is picked — a feeding given earlier can be logged afterwards).
 */
@Composable
private fun FeedEntryRow(d: OutputData, feeder: FeederState, zone: java.time.ZoneId, onFeed: (Double, Long) -> Unit, onUndo: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<String?>(null) }          // null = now
    val nowHHmm = java.time.ZonedDateTime.now(zone).let { String.format("%02d:%02d", it.hour, it.minute) }
    fun submit() {
        val bags = text.replace(",", ".").toDoubleOrNull()?.takeIf { it > 0 } ?: return
        val at = picked?.let { t ->
            val h = t.substringBefore(":").toIntOrNull() ?: return@let null
            val m = t.substringAfter(":").toIntOrNull() ?: return@let null
            val z = java.time.ZonedDateTime.now(zone).withHour(h).withMinute(m).withSecond(0).withNano(0)
            // a time later than now means that time yesterday
            (if (z.toInstant().toEpochMilli() > System.currentTimeMillis() + 60_000) z.minusDays(1) else z).toInstant().toEpochMilli()
        } ?: System.currentTimeMillis()
        onFeed(bags, at); text = ""; picked = null
    }
    Column(Modifier.fillMaxWidth().border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(10.dp)).padding(10.dp).testTag("feed_log"),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Log a feeding", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium))
        run {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = text, onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text("Bags fed") }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.weight(1f).testTag("feed_bags")
                )
                com.example.flock.ui.components.TimePickerField(label = if (picked == null) "At (now)" else "At", valueHHmm = picked ?: nowHHmm,
                    onPick = { picked = it }, modifier = Modifier.weight(1.15f).testTag("feed_time"))
                OutlinedButton(onClick = { submit() }, border = androidx.compose.foundation.BorderStroke(1.dp, GlassLine),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) { Text("Feed", color = MaterialTheme.colorScheme.onSurface) }
            }
            Text(
                "Given today ${Fmt.n(feeder.givenTodayBags, 2)} of ${Fmt.n(d.planBags, 2)} bags" +
                    (feeder.lastFedAt?.let { " · last at " + java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalTime().withNano(0).toString().take(5) } ?: ""),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (feeder.lastFedAt != null) androidx.compose.material3.TextButton(onClick = onUndo, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text("Undo last feeding", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// =================================== tags ===================================

/** One line: the six value colours. */
@Composable
fun TagLegend() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(listOf(ValueKind.PRESENT, ValueKind.PREDICTED, ValueKind.IDEAL), listOf(ValueKind.COMMERCIAL, ValueKind.MIN, ValueKind.MAX)).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { k ->
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).background(kindColor(k), CircleShape))
                        Spacer(Modifier.width(5.dp))
                        Text(kindTag(k), style = MaterialTheme.typography.labelMedium, color = kindColor(k), maxLines = 1)
                    }
                }
            }
        }
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
                    .copy(fontWeight = FontWeight.Medium, color = kindColor(k)))
        }
    }
}

fun alertColor(level: Int): Color = when (level) { 2 -> StatusCrit; 1 -> StatusWarn; else -> ValuePresent }

// =================================== card + shared bits ===================================

/** Transparent glass panel with a thin white outline on matte black. */
@Composable
fun GlassBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .clip(shape)
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(GlassFillTop, GlassFill)))
            .border(1.dp, GlassLine, shape)
    ) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface) { content() }
    }
}

@Composable
fun OutputCard(
    title: String,
    info: String? = null,
    content: @Composable () -> Unit
) {
    val anchors = LocalScrollAnchors.current
    GlassBox(Modifier.fillMaxWidth().onGloballyPositioned { anchors?.report("card:$title", it.positionInRoot().y) }) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.weight(1f))
                if (info != null) InfoButton(info)
            }
            content()
        }
    }
}

/** One part of a card that holds several: a thin line, its name (and ⓘ), then its numbers. */
@Composable
fun Part(title: String, info: String? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.16f)))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                color = Color.White, modifier = Modifier.weight(1f))
            if (info != null) InfoButton(info)
        }
        content()
    }
}

data class GistItem(val label: String, val value: String, val sub: String, val kind: ValueKind = ValueKind.NEUTRAL)

@Composable
fun GistTile(item: GistItem, modifier: Modifier = Modifier) {
    val valColor = if (item.value == "—") kindColor(ValueKind.NEUTRAL) else kindColor(item.kind)
    Surface(color = Color.Black, shape = RoundedCornerShape(10.dp), border = androidx.compose.foundation.BorderStroke(1.dp, GlassLine), modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
            Text(item.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(item.value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium, fontFamily = com.example.ui.theme.NumberFont, fontSize = 16.sp),
                color = valColor, maxLines = 2)
            Text(item.sub, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

    OutputCard(title = "Bird size & uniformity") {
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
                    abs(dev) <= 0.05 -> ValuePresent
                    abs(dev) <= 0.10 -> StatusWarn
                    else -> StatusCrit
                }
                Column(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Text(Fmt.n(loc.avg, 1), style = MaterialTheme.typography.labelMedium.copy(fontFamily = com.example.ui.theme.NumberFont, fontWeight = FontWeight.Medium))
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
            BandChip("On target", onTarget, ValuePresent, Modifier.weight(1f))
            BandChip("Heavy > 105%", heavy, StatusCrit, Modifier.weight(1f))
        }
    }
}

@Composable
private fun BandChip(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Surface(color = Color.Black, shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, GlassLine), modifier = modifier) {
        Column(modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$count spots", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium, color = color, fontFamily = com.example.ui.theme.NumberFont))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

// =================================== scroll anchors ===================================

/**
 * Remembers which card is at the top of the screen (and how far into it) while scrolling, and puts the
 * same card back there after the day changes — cards above it may be taller or shorter on another day.
 */
class ScrollAnchors(private val scroll: androidx.compose.foundation.ScrollState) {
    var viewportTop = 0f
    var hasContent = true
    private val tops = HashMap<String, Float>()      // content y of each card
    private val seen = HashMap<String, Int>()         // layout generation each card was last placed in
    private var gen = 0
    private var shown = Int.MIN_VALUE + 1
    private var settledDay = Int.MIN_VALUE + 2
    private var stack: List<Pair<String, Float>> = emptyList()   // cards at/above the top, nearest first, with the offset into each
    fun report(key: String, rootY: Float) { tops[key] = rootY - viewportTop + scroll.value; seen[key] = gen }
    /** where the block [key] starts on the page (scroll position), when it has been laid out */
    fun topOf(key: String): Int? = tops[key]?.toInt()?.coerceAtLeast(0)
    /** the last of [keys] that starts above [y] (a scroll position): the section being read */
    fun currentOf(keys: List<String>, y: Float): String? = keys.lastOrNull { k -> tops[k]?.let { it <= y } == true } ?: keys.firstOrNull()
    fun dayShown(day: Int) { if (day != shown) { shown = day; gen++ } }
    fun settled(day: Int) { settledDay = day }
    fun capture() {
        if (shown != settledDay || !hasContent) return
        val v = scroll.value.toFloat()
        stack = tops.entries.filter { seen[it.key] == gen && it.value <= v + 2f }.sortedByDescending { it.value }.map { it.key to (v - it.value) }
    }
    fun restoreTarget(): Int? {
        if (!hasContent) return null
        val (k, off) = stack.firstOrNull { seen[it.first] == gen } ?: return null
        return ((tops[k] ?: return null) + off).toInt().coerceAtLeast(0)
    }
}

val LocalScrollAnchors = androidx.compose.runtime.staticCompositionLocalOf<ScrollAnchors?> { null }

/** A block of the Output page that the scroll position can hold on to. */
@Composable
fun Anchored(key: String, content: @Composable () -> Unit) {
    val a = LocalScrollAnchors.current
    // a column (not a box): some blocks emit several cards, which must stack, not overlap
    Column(Modifier.fillMaxWidth().onGloballyPositioned { a?.report(key, it.positionInRoot().y) }, verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
}


/** The house map's inputs for the hour shown: the barricades and lines, the birds, the house air and fans, the litter, the weather. */
fun houseMapOf(d: OutputData, hour: Int, params: com.example.flock.models.ModelParams = com.example.flock.models.ModelParams.DEFAULTS): com.example.flock.models.HouseMap.Field {
    val h = d.envAt(hour)
    val sky = d.env?.at(hour)
    val fans = h?.fans ?: d.minLevel.avgFans
    val lines = d.lineOrder.withIndex()
    return com.example.flock.models.HouseMap.field(com.example.flock.models.HouseMap.In(
        lengthFt = d.farm.usableLengthFt, widthFt = d.farm.usableWidthFt, penStartFt = d.penStartFt, penEndFt = d.barricadeFtNow,
        drinkerYFt = lines.filter { it.value == 'D' }.map { (it.index + 0.5) * d.lineGapFt },
        feederYFt = lines.filter { it.value == 'F' }.map { (it.index + 0.5) * d.lineGapFt },
        live = d.live, avgKg = d.bwNow / 1000.0,
        houseC = h?.air?.tempC ?: d.e.tempIdeal, houseRh = h?.air?.rhPct ?: d.e.rhIdeal,
        airCfm = fans * d.fanCfm, tunnel = fans > d.minLevel.avgFans * 1.5 + 0.5, comfortC = d.plan.comfort,
        litterMoistPct = litterOf(d, hour).moisturePct,
        densityIdeal = d.space.targetKgM2, densityMost = d.farm.densityCapDefault, rhIdeal = d.e.rhMax, rhMost = d.e.rhMax + 10,
        sources = com.example.flock.models.Sources(sky?.outC, sky?.outRh), params = params))
}

/** The small numbers under the farm window: the flock now, and what the house should be today. */
fun coopFacts(d: OutputData, view: OutputData.NowView, air: com.example.flock.domain.BirdEnvironment.Hour?): List<Triple<String, String, ValueKind>> {
    val m = d.mass
    val e = d.e
    val ach = if (d.houseVolFt3 > 0) d.minVentCfmBird * d.liveSafe * 60 / d.houseVolFt3 else null
    return listOf(
        Triple("avg wt", Fmt.n(if (d.vk == ValueKind.PRESENT) d.bwNow else view.weightG, 1) + " g", d.vk),
        Triple("CV", Fmt.n(d.cvEst, 1) + " %", if (d.cvMeasured) ValueKind.PRESENT else ValueKind.PREDICTED),
        Triple("area", Fmt.n(d.space.ft2PerBird, 2) + " ft²/bird", ValueKind.PREDICTED),
        Triple("density", Fmt.n(d.space.kgM2, 1) + " kg/m²", ValueKind.PREDICTED),
        Triple("mortality", Fmt.n(d.mortTDPct, 2) + " %", d.mk),
        Triple("FCR", Fmt.n(m?.fcr ?: view.fcr, 3), d.fcrKind),
        Triple("cFCR", Fmt.n(m?.cFcrCompany, 3), d.fcrKind),
        Triple("feels", air?.r?.feltC?.let { Fmt.n(it, 1) + " °C" } ?: "—", ValueKind.PREDICTED),
        Triple("ideal temp", Fmt.n(e.tempIdeal, 1) + " °C", ValueKind.IDEAL),
        Triple("ideal RH", Fmt.n(e.rhIdeal, 1) + " %", ValueKind.IDEAL),
        Triple("ideal air", Fmt.n(d.minVentCfmBird, 3) + " cfm/bird", ValueKind.IDEAL),
        Triple("air changes", ach?.let { Fmt.n(it, 1) + " /h" } ?: "—", ValueKind.IDEAL),
        Triple("ideal pressure", "20.0–25.0 Pa", ValueKind.IDEAL),
        Triple("light", Fmt.n(e.lightHours, 1) + " h", ValueKind.IDEAL)
    )
}

/** The day as the models saw it: what was entered, the weather, and what was projected — one line a day in the model log. */
fun dayRecord(d: OutputData, flockId: String, atMs: Long): com.example.flock.models.ModelRecord {
    val e = d.e
    val m = d.mass
    return com.example.flock.models.ModelRecord("day", 1, 0, flockId, d.day, atMs,
        inputs = mapOf("date" to e.date, "liveBirds" to d.live, "mortality" to e.mortality, "avgWeightG" to e.avgWeight, "cv" to e.cv,
            "feedBagsUsed" to e.feedBagsUsed, "birdsLifted" to e.birdsLifted, "weightLiftedKg" to e.weightLifted,
            "penStartFt" to d.penStartFt, "penEndFt" to d.barricadeFtNow, "lightHours" to e.lightHours,
            "outsideHours" to d.outsideHours.map { listOf(it.first, it.second, it.third) }, "weatherSource" to d.outsideSource.name,
            "sensors" to emptyList<Any>()),
        outputs = mapOf("bwG" to d.bw, "bwNowG" to d.bwNow, "fcr" to m?.fcr, "cfcr" to m?.cFcrCompany, "epef" to d.epef,
            "dayBags" to d.dayBags, "feedings" to d.feedings, "feedTimes" to d.modelFeedTimes(d.feedings), "waterL" to d.waterL,
            "kgM2" to d.space.kgM2, "walkFeedM" to d.feedWalkM(d.feedPattern), "walkWaterM" to d.waterWalkM,
            "expectedDeaths" to d.expectedDeaths, "litterMoistPct" to litterOf(d, 12).moisturePct))
}
