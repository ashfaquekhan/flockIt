package com.example.flock.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
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
import com.example.flock.ui.LockStatus
import com.example.flock.ui.components.GlanceTiles
import com.example.ui.theme.BrandEmerald
import com.example.ui.theme.StatusCrit
import com.example.ui.theme.StatusCritWash
import com.example.ui.theme.StatusWarn
import com.example.ui.theme.StatusWarnWash

@Composable
fun EntriesScreen(
    entry: DailyDataEntity?,
    lockStatus: LockStatus,
    onSave: (
        w1: String, n1: String,
        w2: String, n2: String,
        w3: String, n3: String,
        w4: String, n4: String,
        w5: String, n5: String,
        mortality: String,
        feedBagsUsed: String,
        birdsLifted: String,
        weightLifted: String,
        lameSeparated: String,
        feedRecB1: String, feedTypeB1: String,
        feedRecB2: String, feedTypeB2: String,
        actualFans: String,
        actualFanTime: String,
        outTemp: String,
        outRH: String,
        broodingLength: String,
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
    var birdsLifted by remember { mutableStateOf("") }
    var weightLifted by remember { mutableStateOf("") }
    var lameSeparated by remember { mutableStateOf("") }

    var feedRecB1 by remember { mutableStateOf("") }
    var feedTypeB1 by remember { mutableStateOf("") }
    var feedRecB2 by remember { mutableStateOf("") }
    var feedTypeB2 by remember { mutableStateOf("") }

    var actualFans by remember { mutableStateOf("") }
    var actualFanTime by remember { mutableStateOf("") }
    var outTemp by remember { mutableStateOf("") }
    var outRH by remember { mutableStateOf("") }
    var broodingLength by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    var savedMessage by remember { mutableStateOf(false) }

    LaunchedEffect(entry) {
        if (entry != null) {
            w1 = entry.w1?.toString() ?: ""
            n1 = entry.n1?.toString() ?: ""
            w2 = entry.w2?.toString() ?: ""
            n2 = entry.n2?.toString() ?: ""
            w3 = entry.w3?.toString() ?: ""
            n3 = entry.n3?.toString() ?: ""
            w4 = entry.w4?.toString() ?: ""
            n4 = entry.n4?.toString() ?: ""
            w5 = entry.w5?.toString() ?: ""
            n5 = entry.n5?.toString() ?: ""

            mortality = if (entry.mortality > 0) entry.mortality.toString() else ""
            feedBagsUsed = if (entry.feedBagsUsed > 0) entry.feedBagsUsed.toString() else ""
            birdsLifted = if (entry.birdsLifted > 0) entry.birdsLifted.toString() else ""
            weightLifted = if (entry.weightLifted > 0) entry.weightLifted.toString() else ""
            lameSeparated = if (entry.lameSeparated > 0) entry.lameSeparated.toString() else ""

            feedRecB1 = if (entry.feedRecB1 > 0) entry.feedRecB1.toString() else ""
            feedTypeB1 = entry.feedTypeB1
            feedRecB2 = if (entry.feedRecB2 > 0) entry.feedRecB2.toString() else ""
            feedTypeB2 = entry.feedTypeB2

            actualFans = entry.actualFans?.toString() ?: ""
            actualFanTime = entry.actualFanTime?.toString() ?: ""
            outTemp = entry.outTemp?.toString() ?: ""
            outRH = entry.outRH?.toString() ?: ""
            broodingLength = entry.broodingLength?.toString() ?: ""
            notes = entry.notes
        }
    }

    val isHardDisabled = lockStatus.isHardLocked
    val isSoftDisabled = lockStatus.isPastDay || lockStatus.isFuture

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("entries_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Cutoff banner
        if (lockStatus.isToday && !lockStatus.isHardLocked) {
            Surface(
                color = StatusWarnWash,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = StatusWarn, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Hard-lock cutoff at ${lockStatus.cutoffTime}: Samples, mortality, and feed bags lock promptly.",
                        style = MaterialTheme.typography.bodySmall.copy(color = StatusWarn, fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        } else if (lockStatus.isHardLocked && lockStatus.isToday) {
            Surface(
                color = StatusCritWash,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = StatusCrit, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Hard-lock active: Morning samples and mortality are locked for today.",
                        style = MaterialTheme.typography.bodySmall.copy(color = StatusCrit, fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }

        // Glance metrics
        GlanceTiles(entry = entry)

        // 1. 5 Location Weight Samples Card
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "5 Location Weight Samples",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    LockBadge(isHard = true, isLocked = isHardDisabled)
                }
                Text(
                    text = "Total weight (g) + count per location. Flock avg = Σweight / Σchicks.",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )

                Spacer(modifier = Modifier.height(10.dp))

                val locLabels = listOf("Loc 1 (Brood front)", "Loc 2 (Mid-left)", "Loc 3 (Centre)", "Loc 4 (Mid-right)", "Loc 5 (Tunnel end)")
                val weights = listOf(w1 to { v: String -> w1 = v }, w2 to { v: String -> w2 = v }, w3 to { v: String -> w3 = v }, w4 to { v: String -> w4 = v }, w5 to { v: String -> w5 = v })
                val counts = listOf(n1 to { v: String -> n1 = v }, n2 to { v: String -> n2 = v }, n3 to { v: String -> n3 = v }, n4 to { v: String -> n4 = v }, n5 to { v: String -> n5 = v })

                for (i in 0 until 5) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = locLabels[i],
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.width(110.dp)
                        )
                        OutlinedTextField(
                            value = weights[i].first,
                            onValueChange = weights[i].second,
                            placeholder = { Text("wt (g)", fontSize = 12.sp) },
                            enabled = !isHardDisabled,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1.2f)
                        )
                        OutlinedTextField(
                            value = counts[i].first,
                            onValueChange = counts[i].second,
                            placeholder = { Text("# chicks", fontSize = 12.sp) },
                            enabled = !isHardDisabled,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 2. Book-Keeping Card (Mortality, Feed bags used)
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Daily Mortality & Feed Consumption",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    LockBadge(isHard = true, isLocked = isHardDisabled)
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = mortality,
                        onValueChange = { mortality = it },
                        label = { Text("Mortality count") },
                        enabled = !isHardDisabled,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = feedBagsUsed,
                        onValueChange = { feedBagsUsed = it },
                        label = { Text("Feed bags used") },
                        enabled = !isHardDisabled,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 3. Soft-Lock Fields: Lifting, Culls, Feed Received
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Lifting, Culls & Deliveries",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    LockBadge(isHard = false, isLocked = isSoftDisabled)
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = birdsLifted,
                        onValueChange = { birdsLifted = it },
                        label = { Text("Birds lifted") },
                        enabled = !isSoftDisabled,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = weightLifted,
                        onValueChange = { weightLifted = it },
                        label = { Text("Wt lifted (kg)") },
                        enabled = !isSoftDisabled,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = lameSeparated,
                        onValueChange = { lameSeparated = it },
                        label = { Text("Lame/culled birds") },
                        enabled = !isSoftDisabled,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = broodingLength,
                        onValueChange = { broodingLength = it },
                        label = { Text("Barricade length (ft)") },
                        enabled = !isSoftDisabled,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "FEED DELIVERIES RECEIVED TODAY",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = feedTypeB1,
                        onValueChange = { feedTypeB1 = it },
                        label = { Text("Feed type 1") },
                        placeholder = { Text("Starter/Grower") },
                        enabled = !isSoftDisabled,
                        modifier = Modifier.weight(1.2f)
                    )
                    OutlinedTextField(
                        value = feedRecB1,
                        onValueChange = { feedRecB1 = it },
                        label = { Text("Bags rec'd") },
                        enabled = !isSoftDisabled,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 4. Operational Measurements Card
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Measured Operations (Optional)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    LockBadge(isHard = false, isLocked = isSoftDisabled)
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = outTemp,
                        onValueChange = { outTemp = it },
                        label = { Text("House Temp (°C)") },
                        enabled = !isSoftDisabled,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = outRH,
                        onValueChange = { outRH = it },
                        label = { Text("House RH (%)") },
                        enabled = !isSoftDisabled,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = actualFans,
                        onValueChange = { actualFans = it },
                        label = { Text("Actual fans running") },
                        enabled = !isSoftDisabled,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = actualFanTime,
                        onValueChange = { actualFanTime = it },
                        label = { Text("Fan ON sec") },
                        enabled = !isSoftDisabled,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Manager observations & notes") },
                    enabled = !isSoftDisabled,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Action Buttons
        if (!lockStatus.isPastDay) {
            Button(
                onClick = {
                    onSave(
                        w1, n1, w2, n2, w3, n3, w4, n4, w5, n5,
                        mortality, feedBagsUsed, birdsLifted, weightLifted, lameSeparated,
                        feedRecB1, feedTypeB1, feedRecB2, feedTypeB2,
                        actualFans, actualFanTime, outTemp, outRH, broodingLength, notes
                    )
                    savedMessage = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_day_button"),
                colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald)
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Save Day Entry",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        if (savedMessage) {
            Text(
                text = "✓ Day entry saved & recalculations updated.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = BrandEmerald,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
fun LockBadge(isHard: Boolean, isLocked: Boolean) {
    val (bgColor, fgColor, text) = if (isHard) {
        if (isLocked) Triple(StatusCritWash, StatusCrit, "HARD LOCKED") else Triple(StatusCritWash, StatusCrit, "HARD LOCK 11:00 AM")
    } else {
        if (isLocked) Triple(Color(0xFFECE8DE), Color.Gray, "LOCKED") else Triple(Color(0xFFECE8DE), Color(0xFF4A5149), "TILL MIDNIGHT")
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = fgColor,
                modifier = Modifier.size(11.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = fgColor
                )
            )
        }
    }
}
