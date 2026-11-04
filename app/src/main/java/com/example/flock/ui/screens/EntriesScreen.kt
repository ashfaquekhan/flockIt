package com.example.flock.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.AssistChip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import com.example.flock.ui.Fmt
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import com.example.flock.data.savedGroups
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.example.flock.data.sampleList
import androidx.compose.material.icons.filled.Remove
import com.example.flock.ui.EntryDrafts
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flock.data.DailyDataEntity
import com.example.flock.data.FeedTypeEntity
import com.example.flock.data.parseFeedBreakdown
import com.example.flock.engine.PhysiologicalEngine
import com.example.flock.ui.DailyInputs
import com.example.flock.ui.LockStatus
import com.example.ui.theme.BrandEmerald
import com.example.ui.theme.StatusCrit
import com.example.ui.theme.StatusCritWash
import com.example.ui.theme.StatusGood
import com.example.ui.theme.StatusGoodWash
import com.example.ui.theme.StatusWarn
import com.example.ui.theme.StatusWarnWash

/** One sample location: total weight of the birds caught there (g) and how many. */
private class LocRow(w: String = "", n: String = "") {
    var w by mutableStateOf(w)
    var n by mutableStateOf(n)
    val blank get() = w.isBlank() && n.isBlank()
}

/** One editable "feed used" line (type + bags). */
private class FeedUseRow(type: String, bags: String) {
    var type by mutableStateOf(type)
    var bags by mutableStateOf(bags)
}

@Composable
fun EntriesScreen(
    entry: DailyDataEntity?,
    dayNumber: Int,
    dayDate: String,
    yesterdayDate: String,
    feedTypes: List<FeedTypeEntity>,
    lockStatus: LockStatus,
    draftKey: String,
    previousCounts: List<Int?>,
    recentNotes: List<String> = emptyList(),
    onSave: (DailyInputs) -> Unit,
    onClearDay: () -> Unit = {},
    modifier: Modifier = Modifier,
    /** the house, for the sampling map (null: no map) */
    farm: com.example.flock.data.FarmEntity? = null,
    /** the day shown is today (the time of the entries starts at "now"; on a past day at that day's morning) */
    isToday: Boolean = true,
    /** the feed variety entered last on an earlier day: a new day's feed row starts with it */
    lastFeedType: String? = null
) {
    val startType = lastFeedType?.takeIf { code -> feedTypes.isEmpty() || feedTypes.any { it.code == code } } ?: (feedTypes.firstOrNull()?.code ?: "B1")
    // sample locations: as many as the last weighing had (5 to start with); + and − change the number
    val locs = remember { mutableStateListOf<LocRow>() }
    // birds weighed one by one (grams) — gives the true CV and uniformity
    var singles by remember { mutableStateOf("") }

    var mortality by remember { mutableStateOf("") }

    // Feed used today — one or more (type, bags) rows.
    val feedUse = remember { mutableStateListOf<FeedUseRow>() }

    var birdsLifted by remember { mutableStateOf("") }
    var weightLifted by remember { mutableStateOf("") }
    var lameSeparated by remember { mutableStateOf("") }
    // the barricades today: the front one, ft from the front wall; the back one, ft from the back wall (empty: none)
    var penFt by remember { mutableStateOf("") }
    var penFrontFt by remember { mutableStateOf("") }
    val houseLen = farm?.usableLengthFt?.takeIf { it > 0 }
    // when what is entered happened: null = the time already saved, else now (today) / 08:00 (a past day)
    var pickedAt by remember(draftKey) { mutableStateOf<String?>(null) }
    // a saved day is locked (so it is not saved again by mistake) until unlocked here; it locks again once saved
    var unlocked by androidx.compose.runtime.saveable.rememberSaveable(draftKey) { mutableStateOf(false) }
    var askUnlock by remember(draftKey) { mutableStateOf(false) }

    var feedRecB1 by remember { mutableStateOf("") }
    var feedTypeB1 by remember { mutableStateOf("") }
    var feedRecB2 by remember { mutableStateOf("") }
    var feedTypeB2 by remember { mutableStateOf("") }
    var feedRecB3 by remember { mutableStateOf("") }
    var feedTypeB3 by remember { mutableStateOf("") }

    // Miscellaneous notes as separate bullet points (no duplicates within a day).
    val noteItems = remember { mutableStateListOf<String>() }
    var newNote by remember { mutableStateOf("") }
    var noteMsg by remember { mutableStateOf<String?>(null) }
    fun setNotes(raw: String) { noteItems.clear(); noteItems.addAll(parseNoteItems(raw)) }
    fun addNote(text: String) {
        val v = cleanNote(text)
        if (v.isEmpty()) return
        if (noteItems.any { noteKey(it) == noteKey(v) }) { noteMsg = "Already in today's notes"; return }
        noteItems.add(v); newNote = ""; noteMsg = null
    }
    var dieselCansUsed by remember { mutableStateOf("") }

    val context = LocalContext.current
    val saved = entry?.savedGroups() ?: emptySet()

    fun currentValues(): Map<String, String> = locs.flatMapIndexed { i, l -> listOf("w${i + 1}" to l.w, "n${i + 1}" to l.n) }.toMap() + mapOf(
        "locs" to locs.size.toString(), "indiv" to singles,
        "mort" to mortality,
        "feed" to feedUse.joinToString(";") { "${it.type}=${it.bags}" },
        "lift" to birdsLifted, "liftKg" to weightLifted, "lame" to lameSeparated, "penBack" to penFt, "penFront" to penFrontFt,
        "rec1" to feedRecB1, "rt1" to feedTypeB1, "rec2" to feedRecB2, "rt2" to feedTypeB2,
        "rec3" to feedRecB3, "rt3" to feedTypeB3, "notes" to noteItems.joinToString("\n"), "noteNew" to newNote,
        "diesel" to dieselCansUsed
    )

    // Load the saved day, overlay any unsaved draft typed earlier, then keep the draft updated.
    LaunchedEffect(entry?.updatedAt, entry != null, draftKey) {
        // how many locations: what this day was saved with, else as many as it holds, else as the last weighing (else 5)
        val savedSamples = entry?.sampleList().orEmpty()
        val draft0 = EntryDrafts.load(context, draftKey)
        val shown = listOf(
            entry?.locCount ?: 0,
            savedSamples.indexOfLast { it.first != null || it.second != null } + 1,
            if ((entry?.locCount ?: 0) > 0 || "W" in saved) 0 else (draft0["locs"]?.toCount() ?: previousCounts.size.takeIf { it > 0 } ?: 5)
        ).max().coerceIn(1, com.example.flock.data.MAX_LOCATIONS)
        locs.clear()
        repeat(shown) { i -> locs.add(LocRow(savedSamples.getOrNull(i)?.first?.fmt() ?: "", savedSamples.getOrNull(i)?.second?.toString() ?: "")) }
        singles = entry?.indivWeights ?: ""

        mortality = if ("M" in saved) (entry?.mortality ?: 0).toString()
            else entry?.mortality?.let { if (it > 0) it.toString() else "" } ?: ""

        feedUse.clear()
        val breakdown = parseFeedBreakdown(entry?.feedUsedBreakdown ?: "")
        when {
            breakdown.isNotEmpty() -> breakdown.forEach { (c, b) -> feedUse.add(FeedUseRow(c, b.fmt())) }
            (entry?.feedBagsUsed ?: 0.0) > 0 || "F" in saved ->
                feedUse.add(FeedUseRow(entry?.feedUsedType ?: (feedTypes.firstOrNull()?.code ?: "B1"), (entry?.feedBagsUsed ?: 0.0).fmt()))
            else -> feedUse.add(FeedUseRow(startType, ""))
        }

        birdsLifted = entry?.birdsLifted?.let { if (it > 0) it.toString() else "" } ?: ""
        weightLifted = entry?.weightLifted?.let { if (it > 0) it.toString() else "" } ?: ""
        lameSeparated = entry?.lameSeparated?.let { if (it > 0) it.toString() else "" } ?: ""
        // the back barricade is kept as ft from the front wall (BroodingLength); typed and shown as ft from the back wall
        penFt = entry?.broodingLength?.takeIf { it > 0 && (houseLen == null || it < houseLen - 0.5) }?.let { if (houseLen != null) houseLen - it else it }?.fmt() ?: ""
        penFrontFt = entry?.penStartFt?.takeIf { it > 0 }?.fmt() ?: ""

        feedRecB1 = entry?.feedRecB1?.let { if (it > 0) it.toString() else "" } ?: ""
        feedTypeB1 = entry?.feedTypeB1?.ifEmpty { "B1" } ?: (feedTypes.getOrNull(0)?.code ?: "B1")
        feedRecB2 = entry?.feedRecB2?.let { if (it > 0) it.toString() else "" } ?: ""
        feedTypeB2 = entry?.feedTypeB2?.ifEmpty { "B2" } ?: (feedTypes.getOrNull(1)?.code ?: "B2")
        feedRecB3 = entry?.feedRecB3?.let { if (it > 0) it.toString() else "" } ?: ""
        feedTypeB3 = entry?.feedTypeB3?.ifEmpty { "B3" } ?: (feedTypes.getOrNull(2)?.code ?: "B3")

        setNotes(entry?.notes ?: ""); newNote = ""; noteMsg = null
        dieselCansUsed = entry?.dieselCansUsed?.let { if (it > 0) it.toString() else "" } ?: ""

        // what is saved, before any draft: a draft keeps only what was typed over it, so a newer value loaded from
        // the sheet (another phone's) is never put back to an old one by a stale draft
        val loaded = currentValues()
        // Unsaved draft typed earlier (on a saved day only what stays open there: notes and feed received).
        val keepOpen = setOf("rec1", "rt1", "rec2", "rt2", "rec3", "rt3", "notes", "noteNew")
        val d = EntryDrafts.load(context, draftKey).let { all ->
            if (!unlocked && (entry?.updatedAt ?: 0L) > 0 && entry?.savedGroups().orEmpty().isNotEmpty()) all.filterKeys { it in keepOpen } else all
        }
        locs.forEachIndexed { i, l -> d["w${i + 1}"]?.let { l.w = it }; d["n${i + 1}"]?.let { l.n = it } }
        d["indiv"]?.let { singles = it }
        // Bird counts per location rarely change: carry the last weighing's counts forward.
        if ("W" !in saved) locs.forEachIndexed { i, l -> if (l.n.isBlank()) previousCounts.getOrNull(i)?.let { l.n = it.toString() } }
        d["mort"]?.let { mortality = it }
        d["feed"]?.takeIf { it.isNotBlank() }?.let { f ->
            val rows = f.split(";").mapNotNull { part -> part.split("=").takeIf { it.size == 2 }?.let { FeedUseRow(it[0], it[1]) } }
            if (rows.isNotEmpty()) { feedUse.clear(); feedUse.addAll(rows) }
        }
        d["lift"]?.let { birdsLifted = it }; d["liftKg"]?.let { weightLifted = it }; d["lame"]?.let { lameSeparated = it }; d["penBack"]?.let { penFt = it }; d["penFront"]?.let { penFrontFt = it }
        d["rec1"]?.let { feedRecB1 = it }; d["rt1"]?.let { feedTypeB1 = it }
        d["rec2"]?.let { feedRecB2 = it }; d["rt2"]?.let { feedTypeB2 = it }
        d["rec3"]?.let { feedRecB3 = it }; d["rt3"]?.let { feedTypeB3 = it }
        d["notes"]?.let { setNotes(it) }; d["noteNew"]?.let { newNote = it }; d["diesel"]?.let { dieselCansUsed = it }

        snapshotFlow { currentValues() }.drop(1).collectLatest { values ->
            delay(300)
            EntryDrafts.save(context, draftKey, values.filter { (k, v) -> loaded[k] != v })
        }
    }

    // a day to come cannot be entered; a saved day is locked until unlocked (notes and feed received stay open on it)
    val isHardLocked = lockStatus.isFuture
    val openDay = !lockStatus.isFuture
    val dayLocked = openDay && !unlocked && (entry?.updatedAt ?: 0L) > 0 && (saved.isNotEmpty() || (entry?.birdsLifted ?: 0) > 0)
    val weightsEnabled = openDay && !dayLocked
    val mortEnabled = openDay && !dayLocked
    val feedEnabled = openDay && !dayLocked
    val othersEnabled = !isHardLocked && !dayLocked
    val zone = remember(farm?.timeZone) { try { java.time.ZoneId.of(farm?.timeZone ?: "Asia/Kolkata") } catch (e: Exception) { java.time.ZoneId.systemDefault() } }
    val dayIso = entry?.date.orEmpty()
    // the time already saved for this day (its weighing, else its lifting): a save again keeps it unless another is picked
    val savedAtMs = listOf(entry?.weighedAt ?: 0L, entry?.liftedAt ?: 0L).firstOrNull { it > 0 }
    val savedHHmm = savedAtMs?.let { ms ->
        java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalTime().let { String.format("%02d:%02d", it.hour, it.minute) }
    }
    var nowTick by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); nowTick = System.currentTimeMillis() } }
    val nowHHmm = java.time.Instant.ofEpochMilli(nowTick).atZone(zone).toLocalTime().let { String.format("%02d:%02d", it.hour, it.minute) }
    val shownHHmm = pickedAt ?: savedHHmm ?: if (isToday) nowHHmm else "08:00"
    /** the moment the entries are about, epoch ms (null: now) — the saved time when none is picked */
    fun entryAt(): Long? {
        if (pickedAt == null && savedAtMs != null) return savedAtMs
        if (pickedAt == null && isToday) return null
        val date = try { java.time.LocalDate.parse(dayIso) } catch (e: Exception) { return null }
        val h = shownHHmm.substringBefore(":").toCount() ?: return null
        val m = shownHHmm.substringAfter(":").toCount() ?: return null
        val t = date.atTime(h, m).atZone(zone).toInstant().toEpochMilli()
        return t.coerceAtMost(System.currentTimeMillis())
    }
    val scrollState = rememberScrollState()

    val liveSamples = locs.map { PhysiologicalEngine.LocationSample(it.w.toNum() ?: 0.0, it.n.toCount() ?: 0) }
    val liveSampleRes = PhysiologicalEngine.computeWeightSamples(liveSamples, PhysiologicalEngine.parseWeights(singles))

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // a saved day: what was saved and when; locked until unlocked (notes and feed received stay open)
        if (dayLocked || (saved.isNotEmpty() && (entry?.updatedAt ?: 0L) > 0)) {
            val names = mapOf("W" to "weights", "M" to "mortality", "F" to "feed used")
            val at = java.time.Instant.ofEpochMilli(entry!!.updatedAt).atZone(zone)
            val what = (listOf("W", "M", "F").filter { it in saved }.mapNotNull { names[it] } +
                listOfNotNull("lifting".takeIf { entry.birdsLifted > 0 })).joinToString(", ")
            val by = entry.updatedBy.substringBefore("@").takeIf { it.isNotBlank() }?.let { " by $it" } ?: ""
            val change = at.toLocalDate().let { if (it.toString() == dayIso) "" else "${it.dayOfMonth} ${it.month.name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() }} " } +
                String.format("%02d:%02d", at.hour, at.minute)
            if (dayLocked) {
                InfoBanner(icon = Icons.Default.Lock, tint = StatusGood, wash = StatusGoodWash,
                    text = "Saved and locked · $what" + (savedHHmm?.let { " at $it" } ?: "") + " · changed $change$by")
                OutlinedButton(onClick = { askUnlock = true }, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("unlock_day")) {
                    Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Unlock day $dayNumber")
                }
            } else InfoBanner(icon = Icons.Default.LockOpen, tint = StatusWarn, wash = StatusWarnWash,
                text = "Unlocked" + (savedHHmm?.let { " · time stays $it" } ?: "") + " · locks again once saved")
        }
        if (isHardLocked) {
            InfoBanner(
                icon = Icons.Default.Lock, tint = StatusWarn, wash = StatusWarnWash,
                text = lockStatus.lockReason.ifEmpty { "This day has not come yet." }
            )
        }

        // when what is entered happened: now, or an earlier time picked (the weighing at 08:15, the lifting at 21:00)
        if (openDay) Section(title = "Time of these entries") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.example.flock.ui.components.TimePickerField(
                    label = if (pickedAt == null && savedHHmm != null) "At (as saved)" else if (pickedAt == null && isToday) "At (now)" else "At", valueHHmm = shownHHmm,
                    onPick = { picked -> if (!dayLocked) pickedAt = if (isToday && picked > nowHHmm) nowHHmm else picked },
                    modifier = Modifier.weight(1f).testTag("entry_time"))
                if (!dayLocked && pickedAt != null) OutlinedButton(onClick = { pickedAt = null }, shape = RoundedCornerShape(10.dp)) {
                    Text(if (savedHHmm != null) "As saved" else if (isToday) "Use now" else "Reset", maxLines = 1, softWrap = false)
                }
            }
        }

        // SECTION 1: 5 Location Weight Samples
        Section(title = "1. Weight samples (${locs.size} locations)", subtitle = "Zig-zag across ${locs.size} spots of the house") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Location", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.2f))
                Text("Total Weight (g)", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(2f))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Chicks Count", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.5f))
            }
            locs.forEachIndexed { i, l ->
                WeightSampleRow("Loc ${i + 1}", l.w, l.n, weightsEnabled, { l.w = it }, { l.n = it }, "w${i + 1}_input", "n${i + 1}_input")
            }
            // more or fewer locations; the number is kept for the next weighing
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${locs.size} locations", style = MaterialTheme.typography.labelLarge.copy(fontFamily = com.example.ui.theme.NumberFont, fontWeight = FontWeight.Medium),
                    modifier = Modifier.weight(1f), maxLines = 1, softWrap = false)
                OutlinedButton(onClick = { if (locs.size > 1) locs.removeAt(locs.size - 1) },
                    enabled = weightsEnabled && locs.size > 1 && locs.last().w.isBlank(),
                    shape = RoundedCornerShape(10.dp), modifier = Modifier.testTag("loc_remove")) {
                    Icon(Icons.Default.Remove, contentDescription = "One location fewer", modifier = Modifier.size(18.dp))
                }
                OutlinedButton(onClick = { locs.add(LocRow("", locs.lastOrNull()?.n ?: "")) },
                    enabled = weightsEnabled && locs.size < com.example.flock.data.MAX_LOCATIONS,
                    shape = RoundedCornerShape(10.dp), modifier = Modifier.testTag("loc_add")) {
                    Icon(Icons.Default.Add, contentDescription = "One more location", modifier = Modifier.size(18.dp))
                }
            }
            if (farm != null) SampleMap(locs.size, farm, entry?.occupiedFt2 ?: 0.0)

            OutlinedTextField(
                colors = entryFieldColors(),
                value = singles, onValueChange = { singles = it },
                label = { Text("Birds weighed one by one (g)") },
                placeholder = { Text("352, 361, 340, 355 …") },
                supportingText = { Text("${PhysiologicalEngine.parseWeights(singles).size} birds · ${PhysiologicalEngine.MIN_BIRDS_FOR_CV}+ give the true CV") },
                enabled = weightsEnabled, minLines = 2,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().testTag("singles_input")
            )
            Spacer(modifier = Modifier.height(4.dp))
            Surface(border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.GlassLine), color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                ) {
                    val mono = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium, fontFamily = com.example.ui.theme.NumberFont)
                    Column {
                        Text("Average", style = MaterialTheme.typography.labelSmall)
                        Text(if (liveSampleRes.hasSample) String.format("%.1f g", liveSampleRes.flockAvgG) else "—", style = mono)
                    }
                    val cv = liveSampleRes.birdCv
                    if (cv != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("CV (birds)", style = MaterialTheme.typography.labelSmall)
                            Text(String.format("%.2f%%", cv), style = mono.copy(color = if (cv >= 12.0) StatusCrit else if (cv >= 10.0) StatusWarn else com.example.ui.theme.ValuePresent))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Uniformity ±10%", style = MaterialTheme.typography.labelSmall)
                            Text(String.format("%.1f%%", liveSampleRes.uniformityPct ?: 0.0), style = mono.copy(color = com.example.ui.theme.ValuePresent))
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Spread between locations", style = MaterialTheme.typography.labelSmall)
                            Text(liveSampleRes.locSpreadPct?.let { String.format("%.2f%%", it) } ?: "—", style = mono)
                        }
                    }
                }
            }
            if (!liveSampleRes.hasSample) {
                Text(
                    text = "No weights entered — the dashboard is running on ideal / projected targets for Day $dayNumber.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        // SECTION 2: Mortality & Feed used (multi-type)
        Section(title = "2. Mortality & feed used") {
            OutlinedTextField(
                colors = entryFieldColors(),
                value = mortality,
                onValueChange = { mortality = it },
                label = { Text("Mortality (chicks dead today)") },
                placeholder = { Text("Leave empty until counted") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = mortEnabled,
                modifier = Modifier.fillMaxWidth().testTag("mortality_input")
            )

            val isDay0 = dayNumber == 0
            Text(
                text = if (isDay0) "No feed consumed before placement day (Day 0)"
                       else "Feed used yesterday · $yesterdayDate — one row per feed type",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!isDay0) {
                feedUse.forEachIndexed { idx, row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            colors = entryFieldColors(),
                            value = row.bags,
                            onValueChange = { row.bags = it },
                            label = { Text("Bags", maxLines = 1, softWrap = false) },
                            placeholder = { Text("used on $yesterdayDate") },
                            singleLine = true,
                            enabled = feedEnabled,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1.4f)
                        )
                        FeedTypeDropdown(
                            selectedCode = row.type,
                            options = feedTypes,
                            enabled = feedEnabled,
                            onSelect = { row.type = it },
                            modifier = Modifier.weight(1.4f)
                        )
                        IconButton(
                            onClick = { if (feedUse.size > 1) feedUse.removeAt(idx) },
                            enabled = feedEnabled && feedUse.size > 1,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Remove feed row", modifier = Modifier.size(18.dp))
                        }
                    }
                }
                if (feedEnabled) {
                    OutlinedButton(
                        onClick = { feedUse.add(FeedUseRow(feedUse.lastOrNull()?.type ?: startType, "")) },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add feed type")
                    }
                }
            }
        }

        // SECTION 3: Feed received (stock)
        Section(title = "3. Feed received today", subtitle = "Log delivery receipts for stock (up to 3 batches)") {
            DeliveryRow("Delivery 1", feedRecB1, feedTypeB1, feedTypes, !isHardLocked, { feedRecB1 = it }, { feedTypeB1 = it })
            DeliveryRow("Delivery 2", feedRecB2, feedTypeB2, feedTypes, !isHardLocked, { feedRecB2 = it }, { feedTypeB2 = it })
            DeliveryRow("Delivery 3", feedRecB3, feedTypeB3, feedTypes, !isHardLocked, { feedRecB3 = it }, { feedTypeB3 = it })
        }

        // SECTION 4: Lifting & culls
        Section(title = "4. Lifting, culls & barricade", subtitle = "Birds lifted and the truck's kg: FCR counts them, and the birds left get their own average") {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    colors = entryFieldColors(),
                    value = birdsLifted, onValueChange = { birdsLifted = it },
                    label = { Text("Birds lifted") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = othersEnabled, modifier = Modifier.weight(1f).testTag("birds_lifted_input")
                )
                OutlinedTextField(
                    colors = entryFieldColors(),
                    value = weightLifted, onValueChange = { weightLifted = it },
                    label = { Text("Lift weight (kg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    enabled = othersEnabled, modifier = Modifier.weight(1f).testTag("weight_lifted_input")
                )
            }
            val liftN = birdsLifted.toCount() ?: 0
            val liftKg = weightLifted.replace(",", ".").toNum() ?: 0.0
            if (liftN > 0 && liftKg > 0) Text("Lifted average ${String.format("%.3f", liftKg / liftN)} kg a bird",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = com.example.ui.theme.NumberFont), color = com.example.ui.theme.ValuePresent)
            OutlinedTextField(
                colors = entryFieldColors(),
                value = lameSeparated, onValueChange = { lameSeparated = it },
                label = { Text("Lame / culls separated") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = othersEnabled, modifier = Modifier.fillMaxWidth().testTag("lame_separated_input")
            )
            // the birds' floor between two barricades: each holds until changed; empty = no barricade at that end
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    colors = entryFieldColors(),
                    value = penFrontFt, onValueChange = { penFrontFt = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text("Front barricade, ft") },
                    supportingText = { Text("from the front wall") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    enabled = othersEnabled, modifier = Modifier.weight(1f).testTag("pen_front_input")
                )
                OutlinedTextField(
                    colors = entryFieldColors(),
                    value = penFt, onValueChange = { penFt = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text("Back barricade, ft") },
                    supportingText = { Text("from the back wall") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    enabled = othersEnabled, modifier = Modifier.weight(1f).testTag("pen_input")
                )
            }
            val front = penFrontFt.toNum() ?: 0.0; val back = penFt.toNum() ?: 0.0
            if (houseLen != null && (front > 0 || back > 0)) Text(
                "Birds' floor ${Fmt.n(front, 1)} – ${Fmt.n(houseLen - back, 1)} ft · ${Fmt.n(houseLen - front - back, 1)} ft long",
                style = MaterialTheme.typography.labelMedium, color = Color.White)
        }

        // SECTION 5: Notes & diesel (only miscellaneous section kept)
        Section(title = "5. Notes & diesel") {
            OutlinedTextField(
                colors = entryFieldColors(),
                value = dieselCansUsed, onValueChange = { dieselCansUsed = it },
                label = { Text("Diesel cans used") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                enabled = othersEnabled, modifier = Modifier.fillMaxWidth()
            )
            Text("Notes — one point each", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            noteItems.forEachIndexed { idx, item ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("•", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 8.dp))
                    Text(item, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { noteItems.removeAt(idx) }, enabled = !isHardLocked, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Remove note", modifier = Modifier.size(16.dp))
                    }
                }
            }
            if (!isHardLocked) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        colors = entryFieldColors(),
                        value = newNote, onValueChange = { newNote = it; noteMsg = null },
                        placeholder = { Text("Add a point, e.g. litter turned") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { addNote(newNote) }),
                        isError = noteMsg != null,
                        supportingText = noteMsg?.let { m -> { Text(m) } },
                        modifier = Modifier.weight(1f).testTag("notes_input")
                    )
                    IconButton(onClick = { addNote(newNote) }, enabled = newNote.isNotBlank()) {
                        Icon(Icons.Default.Add, contentDescription = "Add note")
                    }
                }
                val reuse = recentNotes.filter { r -> noteItems.none { noteKey(it) == noteKey(r) } }.take(8)
                if (reuse.isNotEmpty()) {
                    Text("Tap to reuse from earlier days", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        reuse.forEach { r -> AssistChip(onClick = { addNote(r) }, label = { Text(r, maxLines = 1) }) }
                    }
                }
            }
        }

        // SAVE — press & hold ~1 s so it can't be tapped by accident; nothing locks after saving
        HoldButton(
            label = if (dayLocked) "Hold to save notes and feed received"
                else (if (saved.isNotEmpty()) "Hold to update day $dayNumber" else "Hold to save day $dayNumber") + " · at $shownHHmm",
            enabled = openDay,
            onComplete = {
                val rows = feedUse.filter { (it.bags.toNum() ?: 0.0) > 0.0 }
                val breakdown = rows.joinToString(";") { "${it.type}=${it.bags.toNum() ?: 0.0}" }
                val feedSum = rows.sumOf { it.bags.toNum() ?: 0.0 }
                val firstType = rows.firstOrNull()?.type ?: (feedTypes.firstOrNull()?.code ?: "B1")
                onSave(
                    DailyInputs(
                        w1 = locs.getOrNull(0)?.w?.toNum(), n1 = locs.getOrNull(0)?.n?.toCount(),
                        w2 = locs.getOrNull(1)?.w?.toNum(), n2 = locs.getOrNull(1)?.n?.toCount(),
                        w3 = locs.getOrNull(2)?.w?.toNum(), n3 = locs.getOrNull(2)?.n?.toCount(),
                        w4 = locs.getOrNull(3)?.w?.toNum(), n4 = locs.getOrNull(3)?.n?.toCount(),
                        w5 = locs.getOrNull(4)?.w?.toNum(), n5 = locs.getOrNull(4)?.n?.toCount(),
                        moreSamples = com.example.flock.data.formatMoreSamples(locs.drop(5).map { it.w.toNum() to it.n.toCount() }),
                        locCount = locs.size,
                        indivWeights = PhysiologicalEngine.parseWeights(singles).joinToString(", ") { it.fmt() },
                        mortality = mortality.trim().toCount(),
                        feedBagsUsed = feedSum,
                        feedUsedType = firstType,
                        feedUsedBreakdown = breakdown,
                        birdsLifted = birdsLifted.toCount() ?: 0,
                        weightLifted = weightLifted.toNum() ?: 0.0,
                        lameSeparated = lameSeparated.toCount() ?: 0,
                        feedRecB1 = feedRecB1.toNum() ?: 0.0, feedTypeB1 = feedTypeB1,
                        feedRecB2 = feedRecB2.toNum() ?: 0.0, feedTypeB2 = feedTypeB2,
                        feedRecB3 = feedRecB3.toNum() ?: 0.0, feedTypeB3 = feedTypeB3,
                        // the back barricade as ft from the front wall (0: the whole house); the front one as typed (0: none)
                        broodingLength = penFt.toNum()?.takeIf { it > 0 }?.let { b -> if (houseLen != null) (houseLen - b).coerceAtLeast(10.0) else b }
                            ?: entry?.broodingLength?.let { 0.0 }, actualFans = null, actualFanTime = null,
                        outTemp = null, outRH = null,
                        // the notes as saved when nothing in them was changed (an untouched note is not re-sent over another phone's)
                        notes = (noteItems + listOfNotNull(cleanNote(newNote).takeIf { v -> v.isNotEmpty() && noteItems.none { noteKey(it) == noteKey(v) } })).let { items ->
                            if (entry != null && items == parseNoteItems(entry.notes)) entry.notes else items.joinToString("\n") { "- $it" }
                        },
                        dieselCansUsed = dieselCansUsed.toNum() ?: 0.0,
                        entered = buildSet {
                            if (mortEnabled && mortality.trim().toCount() != null) add("M")
                            if (feedEnabled && feedUse.any { it.bags.trim().toNum() != null }) add("F")
                        },
                        at = entryAt(),
                        penStartFt = penFrontFt.toNum()?.coerceAtLeast(0.0) ?: entry?.penStartFt?.let { 0.0 }
                    )
                )
                pickedAt = null
                unlocked = false
            }
        )

        HoldButton(
            label = "Hold to clear every entry of day $dayNumber",
            onComplete = onClearDay,
            container = StatusCrit,
            icon = Icons.Default.Close,
            enabled = !isHardLocked && saved.isNotEmpty() && !dayLocked
        )

        Spacer(modifier = Modifier.height(96.dp).navigationBarsPadding())
    }
    if (askUnlock) androidx.compose.material3.AlertDialog(
        onDismissRequest = { askUnlock = false },
        title = { Text("Unlock day $dayNumber?") },
        text = { Text("Change the saved entries, then hold Update." + (savedHHmm?.let { " The time stays $it." } ?: "")) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { unlocked = true; askUnlock = false }, modifier = Modifier.testTag("unlock_confirm")) { Text("Unlock") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { askUnlock = false }) { Text("Keep locked") } }
    )
}

/**
 * Where to catch the birds for weighing: the birds' floor (front wall to the barricade) from above, with the
 * feeder (gold) and drinker (blue) lines, and one numbered spot per location — spread evenly along the house
 * and switching sides, so the sample covers the front, the middle and the back, left and right. The dashed
 * line is the walk.
 */
@Composable
fun SampleMap(locations: Int, farm: com.example.flock.data.FarmEntity, occupiedFt2: Double, modifier: Modifier = Modifier) {
    val widthFt = farm.usableWidthFt.coerceAtLeast(1.0)
    val lenFt = (if (occupiedFt2 > 0) occupiedFt2 / widthFt else farm.usableLengthFt).coerceIn(10.0, farm.usableLengthFt.coerceAtLeast(10.0))
    val n = locations.coerceIn(1, com.example.flock.data.MAX_LOCATIONS)
    // along: evenly spaced; across: left third / right third in turn (a single spot goes in the middle)
    val spots = (0 until n).map { i -> ((i + 0.5) / n) to (if (n == 1) 0.5 else if (i % 2 == 0) 0.28 else 0.72) }
    val gold = com.example.ui.theme.ValuePredicted; val blue = com.example.ui.theme.ValueMin
    Column(modifier.fillMaxWidth().testTag("sample_map"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Where to catch the birds", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium))
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(118.dp)) {
            val px = density
            val padL = 34f * px; val padR = 8f * px; val padT = 6f * px; val padB = 20f * px
            val w = size.width - padL - padR; val h = size.height - padT - padB
            fun x(f: Double) = padL + (f * w).toFloat()
            fun y(f: Double) = padT + (f * h).toFloat()
            drawRect(Color.White.copy(alpha = 0.06f), androidx.compose.ui.geometry.Offset(padL, padT), androidx.compose.ui.geometry.Size(w, h))
            drawRect(Color.White.copy(alpha = 0.7f), androidx.compose.ui.geometry.Offset(padL, padT), androidx.compose.ui.geometry.Size(w, h),
                style = androidx.compose.ui.graphics.drawscope.Stroke(1.4f * px))
            // feeder and drinker lines, spread across the width
            val fl = farm.feederLines.coerceAtLeast(0); val dl = farm.drinkerLines.coerceAtLeast(0)
            for (i in 0 until fl) { val yy = y((i + 0.5) / fl); drawLine(gold.copy(alpha = 0.55f), androidx.compose.ui.geometry.Offset(padL, yy), androidx.compose.ui.geometry.Offset(padL + w, yy), 1.3f * px) }
            for (i in 0 until dl) { val yy = y((i + 0.5) / dl); drawLine(blue.copy(alpha = 0.45f), androidx.compose.ui.geometry.Offset(padL, yy), androidx.compose.ui.geometry.Offset(padL + w, yy), 1f * px,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f * px, 4f * px))) }
            // the walk: in at the front, spot to spot
            val pts = listOf(androidx.compose.ui.geometry.Offset(padL, y(0.5))) + spots.map { androidx.compose.ui.geometry.Offset(x(it.first), y(it.second)) }
            for (i in 1 until pts.size) drawLine(Color.White.copy(alpha = 0.55f), pts[i - 1], pts[i], 1.4f * px,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f * px, 5f * px)))
            val paint = android.graphics.Paint().apply { isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER; typeface = com.example.ui.theme.AppFonts.mono; isFakeBoldText = false }
            spots.forEachIndexed { i, s ->
                val c = androidx.compose.ui.geometry.Offset(x(s.first), y(s.second))
                drawCircle(Color.Black, 10.5f * px, c); drawCircle(Color.White, 10.5f * px, c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.6f * px))
                paint.textSize = 11.5f * px; paint.color = android.graphics.Color.WHITE
                drawContext.canvas.nativeCanvas.drawText("${i + 1}", c.x, c.y + paint.textSize * 0.36f, paint)
            }
            // front wall, and feet along the bottom
            paint.isFakeBoldText = false; paint.textSize = 10f * px; paint.color = Color.White.copy(alpha = 0.65f).toArgb()
            drawContext.canvas.nativeCanvas.drawText("front", padL / 2, padT + h / 2 + paint.textSize * 0.36f, paint)
            listOf(0.0, 0.5, 1.0).forEach { f ->
                paint.textAlign = if (f == 0.0) android.graphics.Paint.Align.LEFT else if (f == 1.0) android.graphics.Paint.Align.RIGHT else android.graphics.Paint.Align.CENTER
                drawContext.canvas.nativeCanvas.drawText(String.format("%.1f ft", lenFt * f), x(f), size.height - 5f * px, paint)
            }
        }
        // each spot: how far from the front wall, and which side — two to a line
        spots.withIndex().chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth()) {
                pair.forEach { (i, s) ->
                    Text("${i + 1}  ${String.format("%.1f", s.first * lenFt)} ft  " + (if (n == 1) "middle" else if (s.second < 0.5) "left" else "right"),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = com.example.ui.theme.NumberFont, letterSpacing = 0.sp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Text("Catch the birds at each number, between a feeder and a drinker line and away from the walls; about the same number at every spot.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Field colours: saved (locked) values stay clearly readable instead of fading to grey on black. */
@Composable
fun entryFieldColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
    disabledBorderColor = com.example.ui.theme.GlassLine,
    disabledLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
    disabledSupportingTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
)

/** Note text without leading bullet marks or extra spaces. */
fun cleanNote(raw: String): String = raw.trim().trimStart('-', '•', '*', '·', '–').trim().replace(Regex("\\s+"), " ")

/** Case- and punctuation-insensitive key, so "Litter turned." and "litter turned" count as the same point. */
fun noteKey(raw: String): String = cleanNote(raw).lowercase().trimEnd('.', '!', ',')

/** Splits saved notes (one point per line, any bullet style) into unique points. */
fun parseNoteItems(raw: String): List<String> =
    raw.lines().map { cleanNote(it) }.filter { it.isNotEmpty() }.distinctBy { noteKey(it) }

private fun Double.fmt(): String = if (this % 1.0 == 0.0) this.toInt().toString() else this.toString()

/** A number as typed: "3.5", "3,5" (a keyboard's comma decimal), " 12 " and "1,234.5" all read. */
internal fun String.toNum(): Double? {
    val t = trim().replace(" ", "")
    if (t.isEmpty()) return null
    return (if ('.' in t) t.replace(",", "") else t.replace(',', '.')).toDoubleOrNull()
}

/** A count as typed: "12", "12.0" or "1,234"; a fraction is not a count. */
internal fun String.toCount(): Int? {
    val x = trim().replace(" ", "").replace(",", "").toDoubleOrNull() ?: return null
    return if (kotlin.math.abs(x - Math.rint(x)) < 1e-9) Math.rint(x).toInt() else null
}

/**
 * A full-width button that must be pressed and held for [holdSeconds] to fire, so it can't be triggered by an
 * accidental tap. While held a fill sweeps left to right with a bright edge, a ring round the icon closes and the text
 * counts up; let go early and it runs back. It waits while another function runs ([LocalBusy]).
 */
@Composable
fun HoldButton(
    label: String,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    holdSeconds: Float = 1.0f,
    container: Color = BrandEmerald,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Check,
    height: Int = 56
) {
    // the edge and icon carry the button's colour when it is a risky one (clear); the save button stays white on black
    val accent = if (container == BrandEmerald) Color.White else container
    val busy = com.example.flock.ui.components.LocalBusy.current
    val active = enabled && busy == null
    val holdMs = holdSeconds * 1000f
    // the latest action, not the one from the first time the button was drawn (the gesture below lives on)
    val fire by androidx.compose.runtime.rememberUpdatedState(onComplete)
    var progress by remember { mutableStateOf(0f) }
    var pressed by remember { mutableStateOf(false) }
    var doneFlash by remember { mutableStateOf(false) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val scale by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.97f else 1f, label = "holdScale")
    // let go early: the fill runs back instead of vanishing
    val shownProgress by androidx.compose.animation.core.animateFloatAsState(progress, if (pressed) androidx.compose.animation.core.snap()
        else androidx.compose.animation.core.tween(220), label = "holdFill")
    androidx.compose.runtime.LaunchedEffect(doneFlash) { if (doneFlash) { delay(900); doneFlash = false } }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = height.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (active || doneFlash) 1f else 0.45f }
            .clip(RoundedCornerShape(12.dp))
            // drawn behind the text (a child box can't fill the height inside a scrolling page)
            .drawBehind {
                val f = if (doneFlash) 1f else shownProgress
                if (f > 0f) {
                    drawRect(accent.copy(alpha = if (doneFlash) 0.30f else 0.24f), size = androidx.compose.ui.geometry.Size(size.width * f, size.height))
                    if (!doneFlash) drawRect(Color.White, topLeft = androidx.compose.ui.geometry.Offset(size.width * f - 3.dp.toPx(), 0f),
                        size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height))
                }
            }
            .border(if (pressed || doneFlash) 2.dp else 1.5.dp, if (!active) com.example.ui.theme.GlassLine.copy(alpha = 0.3f) else if (pressed || doneFlash) Color.White else accent.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
            .then(
                if (!active) Modifier
                else Modifier.pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            progress = 0f
                            pressed = true
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            coroutineScope {
                                // timed by the screen's frames: the fill moves every frame the finger stays down
                                val anim = launch {
                                    val t0 = androidx.compose.runtime.withFrameNanos { it }
                                    while (true) {
                                        val elapsed = (androidx.compose.runtime.withFrameNanos { it } - t0) / 1_000_000f
                                        progress = (elapsed / holdMs).coerceIn(0f, 1f)
                                        if (progress >= 1f) {
                                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                            doneFlash = true
                                            fire(); break
                                        }
                                    }
                                }
                                tryAwaitRelease()
                                anim.cancel()
                            }
                            pressed = false
                            progress = 0f
                        }
                    )
                }
            )
            .testTag("hold_button"),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            // the icon in a ring that closes as the hold goes on
            Box(Modifier.size(30.dp).drawBehind {
                val stroke = 2.5.dp.toPx()
                drawCircle(Color.White.copy(alpha = 0.18f), radius = size.minDimension / 2 - stroke / 2, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                val f = if (doneFlash) 1f else shownProgress
                if (f > 0f) drawArc(accent, -90f, 360f * f, false, topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }, contentAlignment = Alignment.Center) {
                Icon(if (doneFlash) Icons.Default.Check else icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = when {
                    doneFlash -> "Done"
                    busy != null -> "Wait: ${busy.replaceFirstChar { it.lowercase() }}…"
                    progress in 0.001f..0.999f -> "Keep holding… ${(progress * 100).toInt()} %"
                    else -> label
                },
                color = Color.White,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 3
            )
        }
    }
}

@Composable
private fun Section(
    title: String,
    subtitle: String? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Surface(border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.GlassLine), 
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium))
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

@Composable
private fun InfoBanner(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    wash: androidx.compose.ui.graphics.Color,
    text: String
) {
    Surface(color = wash, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text, color = tint, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium))
        }
    }
}

@Composable
fun WeightSampleRow(
    loc: String,
    weight: String,
    count: String,
    enabled: Boolean,
    onWeightChange: (String) -> Unit,
    onCountChange: (String) -> Unit,
    weightTag: String,
    countTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(loc, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium), modifier = Modifier.weight(1.2f))
        OutlinedTextField(
            colors = entryFieldColors(),
            value = weight, onValueChange = onWeightChange,
            placeholder = { Text("Weight (g)") }, singleLine = true, enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(2f).testTag(weightTag)
        )
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedTextField(
            colors = entryFieldColors(),
            value = count, onValueChange = onCountChange,
            placeholder = { Text("Count") }, singleLine = true, enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1.5f).testTag(countTag)
        )
    }
}

@Composable
fun DeliveryRow(
    slotLabel: String,
    bags: String,
    feedTypeCode: String,
    feedTypes: List<FeedTypeEntity>,
    enabled: Boolean,
    onBagsChange: (String) -> Unit,
    onTypeChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            colors = entryFieldColors(),
            value = bags, onValueChange = onBagsChange,
            label = { Text("$slotLabel (bags)") }, singleLine = true, enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1.8f)
        )
        FeedTypeDropdown(feedTypeCode, feedTypes, enabled, onTypeChange, Modifier.weight(1.2f))
    }
}

@Composable
fun FeedTypeDropdown(
    selectedCode: String,
    options: List<FeedTypeEntity>,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    // the box shows the code only (the names are in the list): "B1 (Pre-starter)" would break mid-word in a narrow box
    val displayLabel = selectedCode.ifEmpty { "Type" }

    Box(modifier = modifier) {
        OutlinedTextField(
            colors = entryFieldColors(),
            value = displayLabel, onValueChange = {}, readOnly = true, enabled = enabled, singleLine = true,
            label = { Text("Type", maxLines = 1, softWrap = false) },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown, contentDescription = "Select feed type",
                    modifier = Modifier.clickable(enabled = enabled) { expanded = true }
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (opt in options) {
                DropdownMenuItem(
                    text = { Text("${opt.code} — ${opt.name} (${opt.bagKg}kg)") },
                    onClick = { onSelect(opt.code); expanded = false }
                )
            }
        }
    }
}
