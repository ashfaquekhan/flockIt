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
    lastFeedType: String? = null,
    /**
     * The lifting, saved on its own (v57): birds, kg and when the trucks were loaded (epoch ms, null = now); [done] runs
     * once it is saved. The day's other entries keep their own time (the morning weighing stays the morning's).
     */
    onSaveLift: (birds: Int, kg: Double, at: Long?, done: () -> Unit) -> Unit = { _, _, _, done -> done() }
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
        // Unsaved draft typed earlier (on a saved day only what stays open there: notes, feed received and the lifting).
        val keepOpen = setOf("rec1", "rt1", "rec2", "rt2", "rec3", "rt3", "notes", "noteNew", "lift", "liftKg")
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
    // the lifting has its own save (v57) and stays open on a locked day, like the notes
    val dayLocked = openDay && !unlocked && (entry?.updatedAt ?: 0L) > 0 && saved.isNotEmpty()
    val weightsEnabled = openDay && !dayLocked
    val mortEnabled = openDay && !dayLocked
    val feedEnabled = openDay && !dayLocked
    val othersEnabled = !isHardLocked && !dayLocked
    val zone = remember(farm?.timeZone) { try { java.time.ZoneId.of(farm?.timeZone ?: "Asia/Kolkata") } catch (e: Exception) { java.time.ZoneId.systemDefault() } }
    val dayIso = entry?.date.orEmpty()
    // the time the day's data was first entered (its weighing, else — a day saved before times were kept — its save on
    // that same date): a save again keeps it unless another is picked at Save. The lifting keeps its own time (v57).
    val savedAtMs = (entry?.weighedAt ?: 0L).takeIf { it > 0 }
        ?: entry?.updatedAt?.takeIf { it > 0 && saved.isNotEmpty() }?.takeIf { ms ->
            java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toString() == dayIso }
    val savedHHmm = savedAtMs?.let { ms ->
        java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalTime().let { String.format("%02d:%02d", it.hour, it.minute) }
    }
    var nowTick by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); nowTick = System.currentTimeMillis() } }
    val nowHHmm = java.time.Instant.ofEpochMilli(nowTick).atZone(zone).toLocalTime().let { String.format("%02d:%02d", it.hour, it.minute) }
    val shownHHmm = pickedAt ?: savedHHmm ?: if (isToday) nowHHmm else "08:00"
    /** the moment the entries are about, epoch ms (null: now) — the saved time when none is picked */
    /** [hhmm] on this day, epoch ms (no later than now); the saved moment when nothing was picked; null = now. */
    fun momentOf(picked: String?, savedMs: Long?, hhmm: String): Long? {
        if (picked == null && savedMs != null) return savedMs
        if (picked == null && isToday) return null
        val date = try { java.time.LocalDate.parse(dayIso) } catch (e: Exception) { return null }
        val h = hhmm.substringBefore(":").toCount() ?: return null
        val m = hhmm.substringAfter(":").toCount() ?: return null
        val t = date.atTime(h, m).atZone(zone).toInstant().toEpochMilli()
        return t.coerceAtMost(System.currentTimeMillis())
    }
    fun entryAt(): Long? = momentOf(pickedAt, savedAtMs, shownHHmm)
    // the lifting's own time: as saved, else now (today) or 20:00 (a past day: the trucks are loaded in the evening)
    val savedLiftMs = entry?.liftedAt?.takeIf { it > 0 && (entry.birdsLifted) > 0 }
    val savedLiftHHmm = savedLiftMs?.let { ms ->
        java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalTime().let { String.format("%02d:%02d", it.hour, it.minute) }
    }
    var liftPicked by remember(draftKey) { mutableStateOf<String?>(null) }
    val liftHHmm = liftPicked ?: savedLiftHHmm ?: if (isToday) nowHHmm else "20:00"
    var askLiftTime by remember(draftKey) { mutableStateOf(false) }
    var pickLiftTime by remember(draftKey) { mutableStateOf(false) }
    var remind by remember(draftKey) { mutableStateOf<LiftReminder?>(null) }
    val liftSavedN = entry?.birdsLifted ?: 0
    fun doSaveLift() {
        val n = birdsLifted.toCount()?.coerceAtLeast(0) ?: 0
        val kg = if (n > 0) weightLifted.toNum()?.coerceAtLeast(0.0) ?: 0.0 else 0.0
        // the reminder's figures, from the day as it stood before this lifting
        val r = entry?.let { e ->
            val before = (e.liveBirds + e.birdsLifted).coerceAtLeast(0)
            val left = (before - n).coerceAtLeast(0)
            val perBird = if (e.liveBirds > 0) e.totalFeedKg / e.liveBirds else 0.0
            val bagKg = (farm?.feedBagKg ?: 50.0).takeIf { it > 0 } ?: 50.0
            val area = e.occupiedFt2.takeIf { it > 0 }
            LiftReminder(n, liftHHmm, left,
                kotlin.math.ceil(perBird * before / bagKg).toInt(), kotlin.math.ceil(perBird * left / bagKg).toInt(),
                area?.let { a -> a / before.coerceAtLeast(1) }, area?.let { a -> a / left.coerceAtLeast(1) })
        }
        onSaveLift(n, kg, momentOf(liftPicked, savedLiftMs, liftHHmm)) { if (n > 0) remind = r }
        liftPicked = null
    }
    var askTime by remember(draftKey) { mutableStateOf(false) }
    var pickTime by remember(draftKey) { mutableStateOf(false) }
    fun doSave() {
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
                // the lifting is saved with its own button and time (v57): this save leaves it as it is
                birdsLifted = entry?.birdsLifted ?: 0,
                weightLifted = entry?.weightLifted ?: 0.0,
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
            val what = listOf("W", "M", "F").filter { it in saved }.mapNotNull { names[it] }.joinToString(", ")
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
                text = "Unlocked · locks again once saved")
        }
        if (isHardLocked) {
            InfoBanner(
                icon = Icons.Default.Lock, tint = StatusWarn, wash = StatusWarnWash,
                text = lockStatus.lockReason.ifEmpty { "This day has not come yet." }
            )
        }

        // (the time of the entries is picked at Save, v52: no separate box)
        Section(title = "Weight samples", trailing = { SexesMark() }) {
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
                Text("${locs.size} locations", style = MaterialTheme.typography.labelLarge.copy(fontFamily = com.example.ui.theme.NumberFont, fontWeight = FontWeight.Normal),
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
            // (v57: no house drawing here — the ♂♀ mark by the title is all)

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
                    val mono = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Normal, fontFamily = com.example.ui.theme.NumberFont)
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

        Section(title = "Mortality and feed used") {
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

        Section(title = "Feed received") {
            DeliveryRow("Delivery 1", feedRecB1, feedTypeB1, feedTypes, !isHardLocked, { feedRecB1 = it }, { feedTypeB1 = it })
            DeliveryRow("Delivery 2", feedRecB2, feedTypeB2, feedTypes, !isHardLocked, { feedRecB2 = it }, { feedTypeB2 = it })
            DeliveryRow("Delivery 3", feedRecB3, feedTypeB3, feedTypes, !isHardLocked, { feedRecB3 = it }, { feedTypeB3 = it })
        }

        // the lifting: its own Save and its own time (v57) — the trucks come in the evening, the weighing stays the morning's;
        // open on a locked day, like the notes
        Section(title = "Lifting") {
            val liftOpen = !isHardLocked
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    colors = entryFieldColors(),
                    value = birdsLifted, onValueChange = { birdsLifted = it },
                    label = { Text("Birds lifted") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = liftOpen, modifier = Modifier.weight(1f).testTag("birds_lifted_input")
                )
                OutlinedTextField(
                    colors = entryFieldColors(),
                    value = weightLifted, onValueChange = { weightLifted = it },
                    label = { Text("Lift weight (kg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    enabled = liftOpen, modifier = Modifier.weight(1f).testTag("weight_lifted_input")
                )
            }
            val liftN = birdsLifted.toCount() ?: 0
            val liftKg = weightLifted.replace(",", ".").toNum() ?: 0.0
            if (liftN > 0 && liftKg > 0) Text("Lifted average ${String.format("%.3f", liftKg / liftN)} kg a bird",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = com.example.ui.theme.NumberFont), color = com.example.ui.theme.ValuePresent)
            if (liftSavedN > 0 && savedLiftHHmm != null) Text("Saved · ${String.format("%,d", liftSavedN)} birds at $savedLiftHHmm",
                style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.testTag("lift_saved"))
            HoldButton(
                label = if (liftSavedN > 0) "Hold to update the lifting" else "Hold to save the lifting",
                onComplete = { askLiftTime = true },
                enabled = openDay && liftOpen && (liftN > 0 || liftSavedN > 0),
                icon = Icons.Default.Check, height = 48, tag = "lift_hold"
            )
        }

        Section(title = "Culls and barricades") {
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

        Section(title = "Notes and diesel") {
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

        // SAVE — press & hold ~1 s so it can't be tapped by accident; then the time of the entries is picked (as saved, or
        // now) and saved; a locked day saves its notes and feed received at once, its time unchanged
        HoldButton(
            label = if (dayLocked) "Hold to save notes and feed received"
                else if (saved.isNotEmpty()) "Hold to update day $dayNumber" else "Hold to save day $dayNumber",
            enabled = openDay,
            onComplete = { if (dayLocked) doSave() else askTime = true }
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
        text = { Text("Change the saved entries, then hold Update.") },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { unlocked = true; askUnlock = false }, modifier = Modifier.testTag("unlock_confirm")) { Text("Unlock") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { askUnlock = false }) { Text("Keep locked") } }
    )
    // after the hold: when the entries happened — as first saved (an update), else now (a past day 08:00); it can be changed
    if (askTime) androidx.compose.material3.AlertDialog(
        onDismissRequest = { askTime = false; pickedAt = null },
        title = { Text(if (saved.isNotEmpty()) "Update day $dayNumber" else "Save day $dayNumber") },
        text = {
            // the time as plain text and a button to change it (no text box: nothing to type here)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (pickedAt == null && savedHHmm != null) "Time, as first saved" else if (pickedAt == null && isToday) "Time, now" else "Time",
                        style = MaterialTheme.typography.labelLarge, color = Color.White)
                    Text(shownHHmm, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = com.example.ui.theme.NumberFont),
                        color = Color.White, modifier = Modifier.testTag("entry_time"))
                }
                OutlinedButton(onClick = { pickTime = true }, shape = RoundedCornerShape(10.dp), modifier = Modifier.testTag("entry_time_change")) {
                    Text("Change", maxLines = 1, softWrap = false)
                }
            }
            if (pickTime) com.example.flock.ui.components.TimePickDialog("Time of the entries", shownHHmm,
                { picked -> pickedAt = if (isToday && picked > nowHHmm) nowHHmm else picked; pickTime = false }, { pickTime = false })
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { askTime = false; doSave() }, modifier = Modifier.testTag("save_confirm")) {
            Text(if (saved.isNotEmpty()) "Update" else "Save") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { askTime = false; pickedAt = null }) { Text("Cancel") } }
    )
    // the lifting's own time: as saved, else now (a past day 20:00); the morning's entries keep theirs
    if (askLiftTime) androidx.compose.material3.AlertDialog(
        onDismissRequest = { askLiftTime = false; liftPicked = null },
        title = { Text(if (liftSavedN > 0) "Update the lifting" else "Save the lifting") },
        text = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (liftPicked == null && savedLiftHHmm != null) "Lifted at, as saved" else if (liftPicked == null && isToday) "Lifted at, now" else "Lifted at",
                        style = MaterialTheme.typography.labelLarge, color = Color.White)
                    Text(liftHHmm, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = com.example.ui.theme.NumberFont),
                        color = Color.White, modifier = Modifier.testTag("lift_time"))
                }
                OutlinedButton(onClick = { pickLiftTime = true }, shape = RoundedCornerShape(10.dp), modifier = Modifier.testTag("lift_time_change")) {
                    Text("Change", maxLines = 1, softWrap = false)
                }
            }
            if (pickLiftTime) com.example.flock.ui.components.TimePickDialog("Time of the lifting", liftHHmm,
                { picked -> liftPicked = if (isToday && picked > nowHHmm) nowHHmm else picked; pickLiftTime = false }, { pickLiftTime = false })
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { askLiftTime = false; doSaveLift() }, modifier = Modifier.testTag("lift_save_confirm")) {
            Text(if (liftSavedN > 0) "Update" else "Save") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { askLiftTime = false; liftPicked = null }) { Text("Cancel") } }
    )
    // once the lifting is saved: what to do in the house now
    remind?.let { r -> LiftReminderDialog(r) { remind = null } }
}

/** What the lifting changed, for the reminder after it: birds lifted and left, the day's bags and the floor a bird has, before and after. */
private data class LiftReminder(val lifted: Int, val hhmm: String, val left: Int, val bagsWas: Int, val bagsNow: Int, val ft2Was: Double?, val ft2Now: Double?)

/** After a lifting: spread the flock, lower the feed, move the barricades — each with its figure. */
@Composable
private fun LiftReminderDialog(r: LiftReminder, onDone: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDone,
        title = { Text("After the lifting") },
        text = {
            Column(Modifier.fillMaxWidth().testTag("lift_reminder"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("${String.format("%,d", r.lifted)} birds lifted at ${r.hhmm}", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.7f))
                ReminderStep(GIcon.BIRDS, "Spread the flock", "${String.format("%,d", r.left)} birds left", com.example.ui.theme.ValuePresent)
                if (r.bagsNow > 0) ReminderStep(GIcon.BAGS, "Lower the feed", "${r.bagsNow} bags a day · was ${r.bagsWas}", com.example.ui.theme.ValuePredicted)
                ReminderStep(GIcon.BARRICADE, "Move the barricades",
                    r.ft2Now?.let { now -> Fmt.n(now, 2) + " ft² per bird · was " + Fmt.n(r.ft2Was ?: now, 2) } ?: "set them below or in Feed",
                    com.example.ui.theme.ValuePresent)
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDone, modifier = Modifier.testTag("lift_reminder_ok")) { Text("OK") } }
    )
}

@Composable
private fun ReminderStep(icon: GIcon, title: String, figure: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GameIcon(icon, 26.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Color.White)
            Text(figure, style = MaterialTheme.typography.labelMedium.copy(fontFamily = com.example.ui.theme.NumberFont), color = color)
        }
    }
}

/** A small mark for the weighing: the female and male signs, linked (a mixed, as-hatched flock). Thin white lines. */
@Composable
private fun SexesMark(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier.size(width = 34.dp, height = 28.dp).testTag("sexes_mark")) {
        val px = density
        val w = 1.4f * px
        val col = Color.White.copy(alpha = 0.85f)
        val round = androidx.compose.ui.graphics.StrokeCap.Round
        val r = 5.8f * px
        // ♀: a ring, a stem down and its cross
        val f = androidx.compose.ui.geometry.Offset(11f * px, 10f * px)
        drawCircle(col, r, f, style = androidx.compose.ui.graphics.drawscope.Stroke(w))
        drawLine(col, androidx.compose.ui.geometry.Offset(f.x, f.y + r), androidx.compose.ui.geometry.Offset(f.x, f.y + r + 8f * px), w, round)
        drawLine(col, androidx.compose.ui.geometry.Offset(f.x - 3.5f * px, f.y + r + 4f * px), androidx.compose.ui.geometry.Offset(f.x + 3.5f * px, f.y + r + 4f * px), w, round)
        // ♂: a ring through the first, its arrow up and to the right
        val m = androidx.compose.ui.geometry.Offset(19.5f * px, 14f * px)
        drawCircle(col, r, m, style = androidx.compose.ui.graphics.drawscope.Stroke(w))
        val k = 0.7071f
        val a0 = androidx.compose.ui.geometry.Offset(m.x + r * k, m.y - r * k)
        val a1 = androidx.compose.ui.geometry.Offset(a0.x + 6.5f * px, a0.y - 6.5f * px)
        drawLine(col, a0, a1, w, round)
        drawLine(col, a1, androidx.compose.ui.geometry.Offset(a1.x - 4.5f * px, a1.y), w, round)
        drawLine(col, a1, androidx.compose.ui.geometry.Offset(a1.x, a1.y + 4.5f * px), w, round)
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
    height: Int = 56,
    tag: String = "hold_button"
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
            .testTag(tag),
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
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
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
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    // no outline or panel (v54): the section's title and its fields straight on the black
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium), modifier = Modifier.weight(1f))
                trailing?.invoke()
            }
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
            Text(text, color = tint, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Normal))
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
        Text(loc, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal), modifier = Modifier.weight(1.2f))
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
