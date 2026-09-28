package com.example.flock.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.AssistChip
import androidx.compose.ui.text.input.ImeAction
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
import androidx.compose.ui.graphics.Color
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
import com.example.flock.ui.EntryDrafts
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
    cutoffLockEnabled: Boolean,
    draftKey: String,
    previousCounts: List<Int?>,
    recentNotes: List<String> = emptyList(),
    onSave: (DailyInputs) -> Unit,
    onToggleLockTimer: () -> Unit,
    onRevertDay: () -> Unit,
    modifier: Modifier = Modifier
) {
    var w1 by remember { mutableStateOf("") }
    var n1 by remember { mutableStateOf("") }
    var w2 by remember { mutableStateOf("") }
    var n2 by remember { mutableStateOf("") }
    var w3 by remember { mutableStateOf("") }
    var n3 by remember { mutableStateOf("") }
    var w4 by remember { mutableStateOf("") }
    var n4 by remember { mutableStateOf("") }
    var w5 by remember { mutableStateOf("") }
    var n5 by remember { mutableStateOf("") }

    var mortality by remember { mutableStateOf("") }

    // Feed used today — one or more (type, bags) rows.
    val feedUse = remember { mutableStateListOf<FeedUseRow>() }

    var birdsLifted by remember { mutableStateOf("") }
    var weightLifted by remember { mutableStateOf("") }
    var lameSeparated by remember { mutableStateOf("") }

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

    fun currentValues(): Map<String, String> = mapOf(
        "w1" to w1, "n1" to n1, "w2" to w2, "n2" to n2, "w3" to w3, "n3" to n3,
        "w4" to w4, "n4" to n4, "w5" to w5, "n5" to n5,
        "mort" to mortality,
        "feed" to feedUse.joinToString(";") { "${it.type}=${it.bags}" },
        "lift" to birdsLifted, "liftKg" to weightLifted, "lame" to lameSeparated,
        "rec1" to feedRecB1, "rt1" to feedTypeB1, "rec2" to feedRecB2, "rt2" to feedTypeB2,
        "rec3" to feedRecB3, "rt3" to feedTypeB3, "notes" to noteItems.joinToString("\n"), "noteNew" to newNote,
        "diesel" to dieselCansUsed
    )

    // Load the saved day, overlay any unsaved draft typed earlier, then keep the draft updated.
    LaunchedEffect(entry?.updatedAt, entry != null, draftKey) {
        w1 = entry?.w1?.fmt() ?: ""; n1 = entry?.n1?.toString() ?: ""
        w2 = entry?.w2?.fmt() ?: ""; n2 = entry?.n2?.toString() ?: ""
        w3 = entry?.w3?.fmt() ?: ""; n3 = entry?.n3?.toString() ?: ""
        w4 = entry?.w4?.fmt() ?: ""; n4 = entry?.n4?.toString() ?: ""
        w5 = entry?.w5?.fmt() ?: ""; n5 = entry?.n5?.toString() ?: ""

        mortality = if ("M" in saved) (entry?.mortality ?: 0).toString()
            else entry?.mortality?.let { if (it > 0) it.toString() else "" } ?: ""

        feedUse.clear()
        val breakdown = parseFeedBreakdown(entry?.feedUsedBreakdown ?: "")
        when {
            breakdown.isNotEmpty() -> breakdown.forEach { (c, b) -> feedUse.add(FeedUseRow(c, b.fmt())) }
            (entry?.feedBagsUsed ?: 0.0) > 0 || "F" in saved ->
                feedUse.add(FeedUseRow(entry?.feedUsedType ?: (feedTypes.firstOrNull()?.code ?: "B1"), (entry?.feedBagsUsed ?: 0.0).fmt()))
            else -> feedUse.add(FeedUseRow(feedTypes.firstOrNull()?.code ?: "B1", ""))
        }

        birdsLifted = entry?.birdsLifted?.let { if (it > 0) it.toString() else "" } ?: ""
        weightLifted = entry?.weightLifted?.let { if (it > 0) it.toString() else "" } ?: ""
        lameSeparated = entry?.lameSeparated?.let { if (it > 0) it.toString() else "" } ?: ""

        feedRecB1 = entry?.feedRecB1?.let { if (it > 0) it.toString() else "" } ?: ""
        feedTypeB1 = entry?.feedTypeB1?.ifEmpty { "B1" } ?: (feedTypes.getOrNull(0)?.code ?: "B1")
        feedRecB2 = entry?.feedRecB2?.let { if (it > 0) it.toString() else "" } ?: ""
        feedTypeB2 = entry?.feedTypeB2?.ifEmpty { "B2" } ?: (feedTypes.getOrNull(1)?.code ?: "B2")
        feedRecB3 = entry?.feedRecB3?.let { if (it > 0) it.toString() else "" } ?: ""
        feedTypeB3 = entry?.feedTypeB3?.ifEmpty { "B3" } ?: (feedTypes.getOrNull(2)?.code ?: "B3")

        setNotes(entry?.notes ?: ""); newNote = ""; noteMsg = null
        dieselCansUsed = entry?.dieselCansUsed?.let { if (it > 0) it.toString() else "" } ?: ""

        // Unsaved draft typed earlier (only for fields that are still open).
        val d = EntryDrafts.load(context, draftKey)
        if ("W" !in saved) {
            d["w1"]?.let { w1 = it }; d["n1"]?.let { n1 = it }; d["w2"]?.let { w2 = it }; d["n2"]?.let { n2 = it }
            d["w3"]?.let { w3 = it }; d["n3"]?.let { n3 = it }; d["w4"]?.let { w4 = it }; d["n4"]?.let { n4 = it }
            d["w5"]?.let { w5 = it }; d["n5"]?.let { n5 = it }
            // Bird counts per location rarely change: carry the last weighing's counts forward.
            val counts = listOf(n1, n2, n3, n4, n5).toMutableList()
            for (i in 0 until 5) if (counts[i].isBlank()) previousCounts.getOrNull(i)?.let { counts[i] = it.toString() }
            n1 = counts[0]; n2 = counts[1]; n3 = counts[2]; n4 = counts[3]; n5 = counts[4]
        }
        if ("M" !in saved) d["mort"]?.let { mortality = it }
        if ("F" !in saved) d["feed"]?.takeIf { it.isNotBlank() }?.let { f ->
            val rows = f.split(";").mapNotNull { part -> part.split("=").takeIf { it.size == 2 }?.let { FeedUseRow(it[0], it[1]) } }
            if (rows.isNotEmpty()) { feedUse.clear(); feedUse.addAll(rows) }
        }
        d["lift"]?.let { birdsLifted = it }; d["liftKg"]?.let { weightLifted = it }; d["lame"]?.let { lameSeparated = it }
        d["rec1"]?.let { feedRecB1 = it }; d["rt1"]?.let { feedTypeB1 = it }
        d["rec2"]?.let { feedRecB2 = it }; d["rt2"]?.let { feedTypeB2 = it }
        d["rec3"]?.let { feedRecB3 = it }; d["rt3"]?.let { feedTypeB3 = it }
        d["notes"]?.let { setNotes(it) }; d["noteNew"]?.let { newNote = it }; d["diesel"]?.let { dieselCansUsed = it }

        snapshotFlow { currentValues() }.drop(1).collectLatest { values ->
            delay(300)
            EntryDrafts.save(context, draftKey, values)
        }
    }

    val isHardLocked = lockStatus.isHardLocked
    // Important fields lock one by one, only once each has been entered and saved. Fields not yet
    // entered stay open (even past the cut-off) so a late figure can still be added.
    val openDay = !lockStatus.isFuture
    val weightsEnabled = openDay && "W" !in saved
    val mortEnabled = openDay && "M" !in saved
    val feedEnabled = openDay && "F" !in saved
    val scrollState = rememberScrollState()

    val liveSamples = listOf(
        PhysiologicalEngine.LocationSample(w1.toDoubleOrNull() ?: 0.0, n1.toIntOrNull() ?: 0),
        PhysiologicalEngine.LocationSample(w2.toDoubleOrNull() ?: 0.0, n2.toIntOrNull() ?: 0),
        PhysiologicalEngine.LocationSample(w3.toDoubleOrNull() ?: 0.0, n3.toIntOrNull() ?: 0),
        PhysiologicalEngine.LocationSample(w4.toDoubleOrNull() ?: 0.0, n4.toIntOrNull() ?: 0),
        PhysiologicalEngine.LocationSample(w5.toDoubleOrNull() ?: 0.0, n5.toIntOrNull() ?: 0)
    )
    val liveSampleRes = PhysiologicalEngine.computeWeightSamples(liveSamples)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Banners
        if (saved.isNotEmpty()) {
            val names = mapOf("W" to "weights", "M" to "mortality", "F" to "feed used")
            val open = listOf("W", "M", "F").filter { it !in saved && !(it == "F" && dayNumber == 0) }
            InfoBanner(
                icon = Icons.Default.Lock, tint = StatusGood, wash = StatusGoodWash,
                text = "Saved & locked: " + listOf("W", "M", "F").filter { it in saved }.mapNotNull { names[it] }.joinToString(", ") +
                    (if (open.isNotEmpty()) ". Still open: " + open.mapNotNull { names[it] }.joinToString(", ") + " — enter it and hold Update."
                     else ". Use Revert day to change them.")
            )
        }
        if (isHardLocked) {
            InfoBanner(
                icon = Icons.Default.Lock, tint = StatusCrit, wash = StatusCritWash,
                text = lockStatus.lockReason.ifEmpty { "Inputs locked. Edit directly in the Google Sheet." }
            )
        } else if (lockStatus.isCutoffApproaching) {
            InfoBanner(
                icon = Icons.Default.Warning, tint = StatusWarn, wash = StatusWarnWash,
                text = "Cutoff approaching! Today's samples and mortality lock at ${lockStatus.cutoffTime}."
            )
        }

        // SECTION 1: 5 Location Weight Samples
        Section(title = "1. Weight samples (5 locations)", subtitle = "Zig-zag across 5 house spots before the ${lockStatus.cutoffTime} cutoff") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Location", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.2f))
                Text("Total Weight (g)", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(2f))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Chicks Count", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.5f))
            }
            WeightSampleRow("Loc 1", w1, n1, weightsEnabled, { w1 = it }, { n1 = it }, "w1_input", "n1_input")
            WeightSampleRow("Loc 2", w2, n2, weightsEnabled, { w2 = it }, { n2 = it }, "w2_input", "n2_input")
            WeightSampleRow("Loc 3", w3, n3, weightsEnabled, { w3 = it }, { n3 = it }, "w3_input", "n3_input")
            WeightSampleRow("Loc 4", w4, n4, weightsEnabled, { w4 = it }, { n4 = it }, "w4_input", "n4_input")
            WeightSampleRow("Loc 5", w5, n5, weightsEnabled, { w5 = it }, { n5 = it }, "w5_input", "n5_input")

            Spacer(modifier = Modifier.height(8.dp))
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Flock Avg (ΣW / ΣN)", style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = if (liveSampleRes.hasSample) String.format("%.1f g", liveSampleRes.flockAvgG) else "No sample",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Uniformity CV%", style = MaterialTheme.typography.labelSmall)
                        val cvStr = if (liveSampleRes.hasSample && liveSampleRes.totalWeighed >= 2) String.format("%.1f%%", liveSampleRes.cvPercent) else "—"
                        Text(
                            text = cvStr,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                                color = if (liveSampleRes.cvPercent >= 12.0) StatusCrit else if (liveSampleRes.cvPercent >= 10.0) StatusWarn else BrandEmerald
                            )
                        )
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
                            value = row.bags,
                            onValueChange = { row.bags = it },
                            label = { Text("Bags · $yesterdayDate") },
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
                        onClick = { feedUse.add(FeedUseRow(feedTypes.firstOrNull()?.code ?: "B1", "")) },
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
        Section(title = "4. Harvest lifting & culls") {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = birdsLifted, onValueChange = { birdsLifted = it },
                    label = { Text("Birds lifted") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !isHardLocked, modifier = Modifier.weight(1f).testTag("birds_lifted_input")
                )
                OutlinedTextField(
                    value = weightLifted, onValueChange = { weightLifted = it },
                    label = { Text("Lift weight (kg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    enabled = !isHardLocked, modifier = Modifier.weight(1f).testTag("weight_lifted_input")
                )
            }
            OutlinedTextField(
                value = lameSeparated, onValueChange = { lameSeparated = it },
                label = { Text("Lame / culls separated") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !isHardLocked, modifier = Modifier.fillMaxWidth().testTag("lame_separated_input")
            )
        }

        // SECTION 5: Notes & diesel (only miscellaneous section kept)
        Section(title = "5. Notes & diesel") {
            OutlinedTextField(
                value = dieselCansUsed, onValueChange = { dieselCansUsed = it },
                label = { Text("Diesel cans used") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                enabled = !isHardLocked, modifier = Modifier.fillMaxWidth()
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

        // SAVE — press & hold ~1s so it can't be tapped by accident (it locks after saving)
        HoldButton(
            label = if (saved.isNotEmpty()) "Hold to update · Day $dayNumber" else "Hold to save · Day $dayNumber",
            enabled = openDay,
            onComplete = {
                val rows = feedUse.filter { (it.bags.toDoubleOrNull() ?: 0.0) > 0.0 }
                val breakdown = rows.joinToString(";") { "${it.type}=${it.bags.toDoubleOrNull() ?: 0.0}" }
                val feedSum = rows.sumOf { it.bags.toDoubleOrNull() ?: 0.0 }
                val firstType = rows.firstOrNull()?.type ?: (feedTypes.firstOrNull()?.code ?: "B1")
                onSave(
                    DailyInputs(
                        w1 = w1.toDoubleOrNull(), n1 = n1.toIntOrNull(),
                        w2 = w2.toDoubleOrNull(), n2 = n2.toIntOrNull(),
                        w3 = w3.toDoubleOrNull(), n3 = n3.toIntOrNull(),
                        w4 = w4.toDoubleOrNull(), n4 = n4.toIntOrNull(),
                        w5 = w5.toDoubleOrNull(), n5 = n5.toIntOrNull(),
                        mortality = mortality.trim().toIntOrNull(),
                        feedBagsUsed = feedSum,
                        feedUsedType = firstType,
                        feedUsedBreakdown = breakdown,
                        birdsLifted = birdsLifted.toIntOrNull() ?: 0,
                        weightLifted = weightLifted.toDoubleOrNull() ?: 0.0,
                        lameSeparated = lameSeparated.toIntOrNull() ?: 0,
                        feedRecB1 = feedRecB1.toDoubleOrNull() ?: 0.0, feedTypeB1 = feedTypeB1,
                        feedRecB2 = feedRecB2.toDoubleOrNull() ?: 0.0, feedTypeB2 = feedTypeB2,
                        feedRecB3 = feedRecB3.toDoubleOrNull() ?: 0.0, feedTypeB3 = feedTypeB3,
                        broodingLength = null, actualFans = null, actualFanTime = null,
                        outTemp = null, outRH = null,
                        notes = (noteItems + listOfNotNull(cleanNote(newNote).takeIf { v -> v.isNotEmpty() && noteItems.none { noteKey(it) == noteKey(v) } }))
                            .joinToString("\n") { "- $it" },
                        dieselCansUsed = dieselCansUsed.toDoubleOrNull() ?: 0.0,
                        entered = buildSet {
                            if (mortEnabled && mortality.trim().toIntOrNull() != null) add("M")
                            if (feedEnabled && feedUse.any { it.bags.trim().toDoubleOrNull() != null }) add("F")
                        }
                    )
                )
            }
        )

        // Cut-off timer toggle + safe revert (both hold-to-confirm)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HoldButton(
                label = if (cutoffLockEnabled) "Disable cut-off lock" else "Enable cut-off lock",
                onComplete = onToggleLockTimer,
                container = Color(0xFF3E6E86),
                icon = if (cutoffLockEnabled) Icons.Default.LockOpen else Icons.Default.Lock,
                height = 46,
                modifier = Modifier.weight(1f)
            )
            HoldButton(
                label = "Revert day",
                onComplete = onRevertDay,
                container = StatusCrit,
                icon = Icons.Default.Restore,
                height = 46,
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            text = "Disable the cut-off lock to edit after the cut-off. Revert clears this day's entries so you can re-enter.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(96.dp).navigationBarsPadding())
    }
}

/** Note text without leading bullet marks or extra spaces. */
fun cleanNote(raw: String): String = raw.trim().trimStart('-', '•', '*', '·', '–').trim().replace(Regex("\\s+"), " ")

/** Case- and punctuation-insensitive key, so "Litter turned." and "litter turned" count as the same point. */
fun noteKey(raw: String): String = cleanNote(raw).lowercase().trimEnd('.', '!', ',')

/** Splits saved notes (one point per line, any bullet style) into unique points. */
fun parseNoteItems(raw: String): List<String> =
    raw.lines().map { cleanNote(it) }.filter { it.isNotEmpty() }.distinctBy { noteKey(it) }

private fun Double.fmt(): String = if (this % 1.0 == 0.0) this.toInt().toString() else this.toString()

/**
 * A full-width button that must be pressed and held for [holdSeconds] to fire, so it can't be
 * triggered by an accidental tap. A fill sweeps left→right while held; releasing early cancels.
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
    height: Int = 52
) {
    val holdMs = holdSeconds * 1000f
    var progress by remember { mutableStateOf(0f) }
    val base = if (enabled) container else container.copy(alpha = 0.35f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(base)
            .then(
                if (!enabled) Modifier
                else Modifier.pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            progress = 0f
                            var fired = false
                            coroutineScope {
                                val anim = launch {
                                    val t0 = System.nanoTime()
                                    while (true) {
                                        val elapsed = (System.nanoTime() - t0) / 1_000_000f
                                        progress = (elapsed / holdMs).coerceIn(0f, 1f)
                                        if (progress >= 1f) { fired = true; onComplete(); break }
                                        delay(16)
                                    }
                                }
                                tryAwaitRelease()
                                anim.cancel()
                            }
                            if (!fired) progress = 0f
                        }
                    )
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (progress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(Color.White.copy(alpha = 0.22f))
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (progress in 0.001f..0.999f) "Keep holding…" else label,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
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
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
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
            Text(text, color = tint, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
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
        Text(loc, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1.2f))
        OutlinedTextField(
            value = weight, onValueChange = onWeightChange,
            placeholder = { Text("Weight (g)") }, singleLine = true, enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(2f).testTag(weightTag)
        )
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedTextField(
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
    val displayLabel = options.firstOrNull { it.code == selectedCode }?.let { "${it.code} (${it.name})" } ?: selectedCode.ifEmpty { "Feed Type" }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = displayLabel, onValueChange = {}, readOnly = true, enabled = enabled,
            label = { Text("Feed Type") },
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
