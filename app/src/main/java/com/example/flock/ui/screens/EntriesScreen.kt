package com.example.flock.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
    onSave: (DailyInputs) -> Unit,
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

    var notes by remember { mutableStateOf("") }
    var dieselCansUsed by remember { mutableStateOf("") }

    LaunchedEffect(entry) {
        w1 = entry?.w1?.fmt() ?: ""; n1 = entry?.n1?.toString() ?: ""
        w2 = entry?.w2?.fmt() ?: ""; n2 = entry?.n2?.toString() ?: ""
        w3 = entry?.w3?.fmt() ?: ""; n3 = entry?.n3?.toString() ?: ""
        w4 = entry?.w4?.fmt() ?: ""; n4 = entry?.n4?.toString() ?: ""
        w5 = entry?.w5?.fmt() ?: ""; n5 = entry?.n5?.toString() ?: ""

        mortality = entry?.mortality?.let { if (it > 0) it.toString() else "" } ?: ""

        feedUse.clear()
        val breakdown = parseFeedBreakdown(entry?.feedUsedBreakdown ?: "")
        when {
            breakdown.isNotEmpty() -> breakdown.forEach { (c, b) -> feedUse.add(FeedUseRow(c, b.fmt())) }
            (entry?.feedBagsUsed ?: 0.0) > 0 ->
                feedUse.add(FeedUseRow(entry?.feedUsedType ?: (feedTypes.firstOrNull()?.code ?: "B1"), entry!!.feedBagsUsed.fmt()))
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

        notes = entry?.notes ?: ""
        dieselCansUsed = entry?.dieselCansUsed?.let { if (it > 0) it.toString() else "" } ?: ""
    }

    val isHardLocked = lockStatus.isHardLocked
    val committed = entry?.committed == true
    // Important fields (weight+location, mortality, feed used) are read-only once saved.
    val importantEnabled = !isHardLocked && !committed
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
        if (committed && !isHardLocked) {
            InfoBanner(
                icon = Icons.Default.Lock, tint = StatusGood, wash = StatusGoodWash,
                text = "Day saved. Weight, mortality & feed are locked. Edit directly in the Google Sheet to change them."
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
            WeightSampleRow("Loc 1", w1, n1, importantEnabled, { w1 = it }, { n1 = it }, "w1_input", "n1_input")
            WeightSampleRow("Loc 2", w2, n2, importantEnabled, { w2 = it }, { n2 = it }, "w2_input", "n2_input")
            WeightSampleRow("Loc 3", w3, n3, importantEnabled, { w3 = it }, { n3 = it }, "w3_input", "n3_input")
            WeightSampleRow("Loc 4", w4, n4, importantEnabled, { w4 = it }, { n4 = it }, "w4_input", "n4_input")
            WeightSampleRow("Loc 5", w5, n5, importantEnabled, { w5 = it }, { n5 = it }, "w5_input", "n5_input")

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
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = importantEnabled,
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
                            enabled = importantEnabled,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1.4f)
                        )
                        FeedTypeDropdown(
                            selectedCode = row.type,
                            options = feedTypes,
                            enabled = importantEnabled,
                            onSelect = { row.type = it },
                            modifier = Modifier.weight(1.4f)
                        )
                        IconButton(
                            onClick = { if (feedUse.size > 1) feedUse.removeAt(idx) },
                            enabled = importantEnabled && feedUse.size > 1,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Remove feed row", modifier = Modifier.size(18.dp))
                        }
                    }
                }
                if (importantEnabled) {
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
            OutlinedTextField(
                value = notes, onValueChange = { notes = it },
                label = { Text("Miscellaneous notes") },
                placeholder = { Text("One point per line:\n- litter turned\n- bird activity normal") },
                minLines = 3,
                enabled = !isHardLocked,
                modifier = Modifier.fillMaxWidth().testTag("notes_input")
            )
        }

        // SAVE — press & hold for 2.5s so it can't be tapped by accident (it locks after saving)
        HoldToSaveButton(
            label = if (committed) "Hold to update · Day $dayNumber" else "Hold to save · Day $dayNumber",
            enabled = !isHardLocked,
            onSave = {
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
                        mortality = mortality.toIntOrNull() ?: 0,
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
                        notes = notes,
                        dieselCansUsed = dieselCansUsed.toDoubleOrNull() ?: 0.0
                    )
                )
            }
        )

        Spacer(modifier = Modifier.height(96.dp).navigationBarsPadding())
    }
}

private fun Double.fmt(): String = if (this % 1.0 == 0.0) this.toInt().toString() else this.toString()

/**
 * A full-width button that must be pressed and held for ~2.5s to fire, so it can't be
 * triggered by an accidental tap (the day locks once saved). A fill sweeps left→right
 * while held; releasing early cancels.
 */
@Composable
fun HoldToSaveButton(
    label: String,
    enabled: Boolean,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val holdMs = 2500f
    var progress by remember { mutableStateOf(0f) }
    val base = if (enabled) BrandEmerald else BrandEmerald.copy(alpha = 0.35f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
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
                                        if (progress >= 1f) { fired = true; onSave(); break }
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
        // progress sweep
        if (progress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(Color.White.copy(alpha = 0.22f))
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Check, contentDescription = "Save", tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (progress in 0.001f..0.999f) "Keep holding…" else label,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
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
