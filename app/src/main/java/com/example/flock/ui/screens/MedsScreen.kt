package com.example.flock.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flock.data.DailyDataEntity
import com.example.flock.data.RoutineEntity
import com.example.ui.theme.BrandEmerald
import com.example.ui.theme.DomainMed
import com.example.ui.theme.DomainMedWash
import com.example.ui.theme.StatusGood

@Composable
fun MedsScreen(
    entry: DailyDataEntity?,
    routines: List<RoutineEntity>,
    dismissedIds: Set<String>,
    onToggleAlarm: (routineId: String, currentAlarm: Boolean) -> Unit,
    onToggleDone: (routineId: String, isDone: Boolean) -> Unit,
    onAddMed: (title: String, time: String, applyAllDays: Boolean) -> Unit,
    onDeleteMed: (routineId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var newTitle by remember { mutableStateOf("") }
    var newTime by remember { mutableStateOf("07:00") }
    var applyAll by remember { mutableStateOf(false) }

    val medRoutines = routines.filter { it.type == "med" }
    val quickMeds = listOf(
        "Vitamin + Electrolytes",
        "Probiotics in Drink",
        "Liver Tonic & B-Complex",
        "Acidifier (pH 6.2)",
        "Gumboro / IBD Vaccine",
        "Water Sanitizer Shock"
    )

    val totalWaterL = entry?.totalWaterL ?: 0.0

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("meds_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Water Dosing Helper Banner
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
                        text = "Water Dosing Assistant",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Surface(color = DomainMedWash, shape = RoundedCornerShape(8.dp)) {
                        Text(
                            text = String.format("%,.0f L Water Today", totalWaterL),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = DomainMed
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Text(
                    text = "Calculate dosages across tank refills so stock solution is completely consumed within 4–6 hours.",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }
        }

        // Quick Add Med Chips
        Text(
            text = "QUICK ADD MEDICINE SCHEDULE",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(quickMeds) { chip ->
                Surface(
                    color = DomainMedWash,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable {
                        onAddMed(chip, "07:00", false)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = DomainMed, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = chip,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = DomainMed)
                        )
                    }
                }
            }
        }

        // Custom Add Med
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    placeholder = { Text("Medicine name & dosage...", fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1.5f)
                )
                OutlinedTextField(
                    value = newTime,
                    onValueChange = { newTime = it },
                    placeholder = { Text("07:00", fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.width(85.dp)
                )
                Button(
                    onClick = {
                        if (newTitle.isNotBlank()) {
                            onAddMed(newTitle, newTime.ifBlank { "07:00" }, applyAll)
                            newTitle = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DomainMed),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Add")
                }
            }
        }

        // Medicines List
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Scheduled Medicines & Vaccines",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = DomainMed)
                )
                Spacer(modifier = Modifier.height(10.dp))

                if (medRoutines.isEmpty()) {
                    Text(
                        text = "No medications or vaccines scheduled for this day.",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                } else {
                    medRoutines.forEachIndexed { idx, m ->
                        val isDone = dismissedIds.contains(m.routineId)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (isDone) StatusGood else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .clickable { onToggleDone(m.routineId, !isDone) }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (isDone) {
                                        Icon(imageVector = Icons.Default.Check, contentDescription = "Done", tint = Color.White, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = m.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None
                                    )
                                )
                                if (m.detail.isNotBlank()) {
                                    Text(
                                        text = m.detail,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            }

                            Text(
                                text = m.time,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )

                            Spacer(modifier = Modifier.width(4.dp))

                            IconButton(
                                onClick = { onToggleAlarm(m.routineId, m.alarmOn) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (m.alarmOn) Icons.Default.Alarm else Icons.Default.AlarmOff,
                                    contentDescription = "Toggle alarm",
                                    tint = if (m.alarmOn) BrandEmerald else Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = { onDeleteMed(m.routineId) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete med",
                                    tint = Color.Gray.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        if (idx < medRoutines.size - 1) {
                            Divider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
