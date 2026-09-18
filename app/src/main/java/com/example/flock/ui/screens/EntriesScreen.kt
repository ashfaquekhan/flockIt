package com.example.flock.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flock.data.DailyDataEntity
import com.example.flock.data.FeedTypeEntity
import com.example.flock.engine.PhysiologicalEngine
import com.example.flock.ui.LockStatus
import com.example.ui.theme.BrandEmerald
import com.example.ui.theme.StatusCrit
import com.example.ui.theme.StatusCritWash
import com.example.ui.theme.StatusWarn
import com.example.ui.theme.StatusWarnWash

@Composable
fun EntriesScreen(
    entry: DailyDataEntity?,
    dayNumber: Int,
    dayDate: String,
    yesterdayDate: String,
    feedTypes: List<FeedTypeEntity>,
    lockStatus: LockStatus,
    onSave: (
        w1: Double?, n1: Int?,
        w2: Double?, n2: Int?,
        w3: Double?, n3: Int?,
        w4: Double?, n4: Int?,
        w5: Double?, n5: Int?,
        mortality: Int,
        feedBagsUsed: Double,
        feedUsedType: String,
        birdsLifted: Int,
        weightLifted: Double,
        lameSeparated: Int,
        feedRecB1: Double, feedTypeB1: String,
        feedRecB2: Double, feedTypeB2: String,
        feedRecB3: Double, feedTypeB3: String,
        broodingLength: Double?,
        actualFans: Int?,
        actualFanTime: Int?,
        outTemp: Double?,
        outRH: Double?,
        notes: String
    ) -> Unit,
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
    var feedBagsUsed by remember { mutableStateOf("") }
    var feedUsedType by remember { mutableStateOf("") }

    var birdsLifted by remember { mutableStateOf("") }
    var weightLifted by remember { mutableStateOf("") }
    var lameSeparated by remember { mutableStateOf("") }

    var feedRecB1 by remember { mutableStateOf("") }
    var feedTypeB1 by remember { mutableStateOf("") }
    var feedRecB2 by remember { mutableStateOf("") }
    var feedTypeB2 by remember { mutableStateOf("") }
    var feedRecB3 by remember { mutableStateOf("") }
    var feedTypeB3 by remember { mutableStateOf("") }

    var broodingLength by remember { mutableStateOf("") }
    var actualFans by remember { mutableStateOf("") }
    var actualFanTime by remember { mutableStateOf("") }
    var outTemp by remember { mutableStateOf("") }
    var outRH by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    LaunchedEffect(entry) {
        w1 = entry?.w1?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: ""
        n1 = entry?.n1?.toString() ?: ""
        w2 = entry?.w2?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: ""
        n2 = entry?.n2?.toString() ?: ""
        w3 = entry?.w3?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: ""
        n3 = entry?.n3?.toString() ?: ""
        w4 = entry?.w4?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: ""
        n4 = entry?.n4?.toString() ?: ""
        w5 = entry?.w5?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: ""
        n5 = entry?.n5?.toString() ?: ""

        mortality = entry?.mortality?.let { if (it > 0) it.toString() else "" } ?: ""
        feedBagsUsed = entry?.feedBagsUsed?.let { if (it > 0) it.toString() else "" } ?: ""
        feedUsedType = entry?.feedUsedType ?: feedTypes.firstOrNull()?.code ?: "B1"

        birdsLifted = entry?.birdsLifted?.let { if (it > 0) it.toString() else "" } ?: ""
        weightLifted = entry?.weightLifted?.let { if (it > 0) it.toString() else "" } ?: ""
        lameSeparated = entry?.lameSeparated?.let { if (it > 0) it.toString() else "" } ?: ""

        feedRecB1 = entry?.feedRecB1?.let { if (it > 0) it.toString() else "" } ?: ""
        feedTypeB1 = entry?.feedTypeB1?.ifEmpty { "B1" } ?: (feedTypes.getOrNull(0)?.code ?: "B1")
        feedRecB2 = entry?.feedRecB2?.let { if (it > 0) it.toString() else "" } ?: ""
        feedTypeB2 = entry?.feedTypeB2?.ifEmpty { "B2" } ?: (feedTypes.getOrNull(1)?.code ?: "B2")
        feedRecB3 = entry?.feedRecB3?.let { if (it > 0) it.toString() else "" } ?: ""
        feedTypeB3 = entry?.feedTypeB3?.ifEmpty { "B3" } ?: (feedTypes.getOrNull(2)?.code ?: "B3")

        broodingLength = entry?.broodingLength?.toString() ?: ""
        actualFans = entry?.actualFans?.toString() ?: ""
        actualFanTime = entry?.actualFanTime?.toString() ?: ""
        outTemp = entry?.outTemp?.toString() ?: ""
        outRH = entry?.outRH?.toString() ?: ""
        notes = entry?.notes ?: ""
    }

    val isHardLocked = lockStatus.isHardLocked
    val scrollState = rememberScrollState()

    // Live calculation of 5-location sample
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Lock Banner if locked
        if (isHardLocked) {
            Surface(
                color = StatusCritWash,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = StatusCrit,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = lockStatus.lockReason.ifEmpty { "Inputs locked. Direct edit in Google Sheet required." },
                        color = StatusCrit,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        } else if (lockStatus.isCutoffApproaching) {
            Surface(
                color = StatusWarnWash,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = StatusWarn,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Cutoff approaching! Today's samples and mortality lock at ${lockStatus.cutoffTime}.",
                        color = StatusWarn,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }

        // SECTION 1: 5 Location Weight Samples
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "1. 5 Location Weight Samples",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Zig-zag across 5 house locations before 11:00 AM cutoff",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Table Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Location", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.2f))
                    Text("Total Weight (g)", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(2f))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Chicks Count", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.5f))
                }

                // 5 Rows
                WeightSampleRow(
                    loc = "Loc 1",
                    weight = w1,
                    count = n1,
                    enabled = !isHardLocked,
                    onWeightChange = { w1 = it },
                    onCountChange = { n1 = it },
                    weightTag = "w1_input",
                    countTag = "n1_input"
                )
                WeightSampleRow(
                    loc = "Loc 2",
                    weight = w2,
                    count = n2,
                    enabled = !isHardLocked,
                    onWeightChange = { w2 = it },
                    onCountChange = { n2 = it },
                    weightTag = "w2_input",
                    countTag = "n2_input"
                )
                WeightSampleRow(
                    loc = "Loc 3",
                    weight = w3,
                    count = n3,
                    enabled = !isHardLocked,
                    onWeightChange = { w3 = it },
                    onCountChange = { n3 = it },
                    weightTag = "w3_input",
                    countTag = "n3_input"
                )
                WeightSampleRow(
                    loc = "Loc 4",
                    weight = w4,
                    count = n4,
                    enabled = !isHardLocked,
                    onWeightChange = { w4 = it },
                    onCountChange = { n4 = it },
                    weightTag = "w4_input",
                    countTag = "n4_input"
                )
                WeightSampleRow(
                    loc = "Loc 5",
                    weight = w5,
                    count = n5,
                    enabled = !isHardLocked,
                    onWeightChange = { w5 = it },
                    onCountChange = { n5 = it },
                    weightTag = "w5_input",
                    countTag = "n5_input"
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Live Summary Strip
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Flock Avg (ΣW / ΣN)", style = MaterialTheme.typography.labelSmall)
                            Text(
                                text = if (liveSampleRes.hasSample) String.format("%.1f g", liveSampleRes.flockAvgG) else "No sample",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Uniformity CV%", style = MaterialTheme.typography.labelSmall)
                            val cvStr = if (liveSampleRes.hasSample && liveSampleRes.totalWeighed >= 2) String.format("%.1f%%", liveSampleRes.cvPercent) else "—"
                            Text(
                                text = cvStr,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (liveSampleRes.cvPercent >= 12.0) StatusCrit else if (liveSampleRes.cvPercent >= 10.0) StatusWarn else BrandEmerald
                                )
                            )
                        }
                    }
                }
            }
        }

        // SECTION 2: Mortality & Feed Consumption
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "2. Mortality & Feed Consumed",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                // Mortality
                OutlinedTextField(
                    value = mortality,
                    onValueChange = { mortality = it },
                    label = { Text("Mortality (chicks dead today)") },
                    placeholder = { Text("Mortality for Day $dayNumber · $dayDate") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !isHardLocked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mortality_input")
                )

                // Feed bags used + Feed type used
                val isDay0 = dayNumber == 0
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = if (isDay0) "0" else feedBagsUsed,
                            onValueChange = { if (!isDay0) feedBagsUsed = it },
                            label = { Text("Feed bags used") },
                            placeholder = { Text("Bags used up to Day ${if (dayNumber > 0) dayNumber - 1 else 0} · $yesterdayDate") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            enabled = !isHardLocked && !isDay0,
                            modifier = Modifier
                                .weight(1.8f)
                                .testTag("feed_bags_used_input")
                        )

                        FeedTypeDropdown(
                            selectedCode = feedUsedType,
                            options = feedTypes,
                            enabled = !isHardLocked && !isDay0,
                            onSelect = { feedUsedType = it },
                            modifier = Modifier.weight(1.2f)
                        )
                    }

                    if (isDay0) {
                        Text(
                            text = "No feed consumed before placement day (Day 0)",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                        )
                    }
                }
            }
        }

        // SECTION 3: Feed Deliveries (Up to 3 slots)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "3. Feed Received Today",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Log delivery receipts for stock inventory (up to 3 batches)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Slot 1
                DeliveryRow(
                    slotLabel = "Delivery 1",
                    bags = feedRecB1,
                    feedTypeCode = feedTypeB1,
                    feedTypes = feedTypes,
                    enabled = !isHardLocked,
                    onBagsChange = { feedRecB1 = it },
                    onTypeChange = { feedTypeB1 = it }
                )

                // Slot 2
                DeliveryRow(
                    slotLabel = "Delivery 2",
                    bags = feedRecB2,
                    feedTypeCode = feedTypeB2,
                    feedTypes = feedTypes,
                    enabled = !isHardLocked,
                    onBagsChange = { feedRecB2 = it },
                    onTypeChange = { feedTypeB2 = it }
                )

                // Slot 3 (Bug 1 Fix)
                DeliveryRow(
                    slotLabel = "Delivery 3",
                    bags = feedRecB3,
                    feedTypeCode = feedTypeB3,
                    feedTypes = feedTypes,
                    enabled = !isHardLocked,
                    onBagsChange = { feedRecB3 = it },
                    onTypeChange = { feedTypeB3 = it }
                )
            }
        }

        // SECTION 4: Lifting & Culls
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "4. Harvest Lifting & Culls",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = birdsLifted,
                        onValueChange = { birdsLifted = it },
                        label = { Text("Birds lifted") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = !isHardLocked,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("birds_lifted_input")
                    )
                    OutlinedTextField(
                        value = weightLifted,
                        onValueChange = { weightLifted = it },
                        label = { Text("Lift weight (kg)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        enabled = !isHardLocked,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("weight_lifted_input")
                    )
                }

                OutlinedTextField(
                    value = lameSeparated,
                    onValueChange = { lameSeparated = it },
                    label = { Text("Lame / culls separated") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !isHardLocked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lame_separated_input")
                )
            }
        }

        // SECTION 5: Shed Management & Climate (Soft Fields - editable until midnight)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "5. House Checks & Climate (Optional)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                OutlinedTextField(
                    value = broodingLength,
                    onValueChange = { broodingLength = it },
                    label = { Text("Brooding barricade length (ft)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("brooding_length_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = actualFans,
                        onValueChange = { actualFans = it },
                        label = { Text("Fans running") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = actualFanTime,
                        onValueChange = { actualFanTime = it },
                        label = { Text("Fan ON sec") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = outTemp,
                        onValueChange = { outTemp = it },
                        label = { Text("Measured Temp (°C)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = outRH,
                        onValueChange = { outRH = it },
                        label = { Text("Measured RH (%)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("House Notes") },
                    placeholder = { Text("Litter condition, drinker pressure, bird activity...") },
                    minLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("notes_input")
                )
            }
        }

        // SAVE BUTTON
        Button(
            onClick = {
                onSave(
                    w1.toDoubleOrNull(), n1.toIntOrNull(),
                    w2.toDoubleOrNull(), n2.toIntOrNull(),
                    w3.toDoubleOrNull(), n3.toIntOrNull(),
                    w4.toDoubleOrNull(), n4.toIntOrNull(),
                    w5.toDoubleOrNull(), n5.toIntOrNull(),
                    mortality.toIntOrNull() ?: 0,
                    feedBagsUsed.toDoubleOrNull() ?: 0.0,
                    feedUsedType,
                    birdsLifted.toIntOrNull() ?: 0,
                    weightLifted.toDoubleOrNull() ?: 0.0,
                    lameSeparated.toIntOrNull() ?: 0,
                    feedRecB1.toDoubleOrNull() ?: 0.0, feedTypeB1,
                    feedRecB2.toDoubleOrNull() ?: 0.0, feedTypeB2,
                    feedRecB3.toDoubleOrNull() ?: 0.0, feedTypeB3,
                    broodingLength.toDoubleOrNull(),
                    actualFans.toIntOrNull(),
                    actualFanTime.toIntOrNull(),
                    outTemp.toDoubleOrNull(),
                    outRH.toDoubleOrNull(),
                    notes
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("save_day_entry_button")
        ) {
            Icon(Icons.Default.Check, contentDescription = "Save")
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Save Day $dayNumber",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = loc,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.weight(1.2f)
        )

        OutlinedTextField(
            value = weight,
            onValueChange = onWeightChange,
            placeholder = { Text("Weight (g)") },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier
                .weight(2f)
                .testTag(weightTag)
        )

        Spacer(modifier = Modifier.width(8.dp))

        OutlinedTextField(
            value = count,
            onValueChange = onCountChange,
            placeholder = { Text("Count") },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .weight(1.5f)
                .testTag(countTag)
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
            value = bags,
            onValueChange = onBagsChange,
            label = { Text("$slotLabel (bags)") },
            placeholder = { Text("Bags received") },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1.8f)
        )

        FeedTypeDropdown(
            selectedCode = feedTypeCode,
            options = feedTypes,
            enabled = enabled,
            onSelect = onTypeChange,
            modifier = Modifier.weight(1.2f)
        )
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
            value = displayLabel,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text("Feed Type") },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Select feed type",
                    modifier = Modifier.clickable(enabled = enabled) { expanded = true }
                )
            },
            modifier = Modifier.fillMaxWidth()
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            for (opt in options) {
                DropdownMenuItem(
                    text = { Text("${opt.code} — ${opt.name} (${opt.bagKg}kg)") },
                    onClick = {
                        onSelect(opt.code)
                        expanded = false
                    }
                )
            }
        }
    }
}
