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
import com.example.flock.data.RoutineEntity
import com.example.ui.theme.BrandEmerald
import com.example.ui.theme.DomainTask
import com.example.ui.theme.DomainTaskWash
import com.example.ui.theme.StatusGood

@Composable
fun TasksScreen(
    routines: List<RoutineEntity>,
    dismissedIds: Set<String>,
    onToggleAlarm: (routineId: String, currentAlarm: Boolean) -> Unit,
    onToggleDone: (routineId: String, isDone: Boolean) -> Unit,
    onAddTask: (title: String, time: String, applyAllDays: Boolean) -> Unit,
    onDeleteTask: (routineId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var newTitle by remember { mutableStateOf("") }
    var newTime by remember { mutableStateOf("07:00") }
    var applyAll by remember { mutableStateOf(true) }

    val taskRoutines = routines.filter { it.type == "task" }
    val quickChips = listOf("Walk house", "Flush drinkers", "Raise feeders", "Check mortality", "Litter check", "Sample weigh")

    // Grouping by time blocks
    val morningTasks = taskRoutines.filter { parseHour(it.time) < 12 }
    val afternoonTasks = taskRoutines.filter { parseHour(it.time) in 12..17 }
    val nightTasks = taskRoutines.filter { parseHour(it.time) >= 18 }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("tasks_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Quick Add Chips
        Text(
            text = "QUICK ADD ROUTINE TASK",
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
            items(quickChips) { chip ->
                Surface(
                    color = DomainTaskWash,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable {
                        onAddTask(chip, "08:00", true)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = DomainTask, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = chip,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = DomainTask)
                        )
                    }
                }
            }
        }

        // Custom Add Row
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
                    placeholder = { Text("Task description...", fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1.5f)
                )
                OutlinedTextField(
                    value = newTime,
                    onValueChange = { newTime = it },
                    placeholder = { Text("HH:MM", fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.width(85.dp)
                )
                Button(
                    onClick = {
                        if (newTitle.isNotBlank()) {
                            onAddTask(newTitle, newTime.ifBlank { "08:00" }, applyAll)
                            newTitle = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DomainTask),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Add")
                }
            }
        }

        // Morning Tasks Block
        TaskGroupCard(
            title = "Morning Block (06:00 – 11:59)",
            tasks = morningTasks,
            dismissedIds = dismissedIds,
            onToggleAlarm = onToggleAlarm,
            onToggleDone = onToggleDone,
            onDeleteTask = onDeleteTask
        )

        // Afternoon Tasks Block
        TaskGroupCard(
            title = "Midday & Peak Heat (12:00 – 17:59)",
            tasks = afternoonTasks,
            dismissedIds = dismissedIds,
            onToggleAlarm = onToggleAlarm,
            onToggleDone = onToggleDone,
            onDeleteTask = onDeleteTask
        )

        // Evening & Night Block
        TaskGroupCard(
            title = "Evening & Night (18:00 – 23:59)",
            tasks = nightTasks,
            dismissedIds = dismissedIds,
            onToggleAlarm = onToggleAlarm,
            onToggleDone = onToggleDone,
            onDeleteTask = onDeleteTask
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun TaskGroupCard(
    title: String,
    tasks: List<RoutineEntity>,
    dismissedIds: Set<String>,
    onToggleAlarm: (routineId: String, currentAlarm: Boolean) -> Unit,
    onToggleDone: (routineId: String, isDone: Boolean) -> Unit,
    onDeleteTask: (routineId: String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = DomainTask)
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (tasks.isEmpty()) {
                Text(
                    text = "No tasks scheduled in this block.",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
            } else {
                tasks.forEachIndexed { idx, t ->
                    val isDone = dismissedIds.contains(t.routineId)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Checkbox
                        Surface(
                            shape = CircleShape,
                            color = if (isDone) StatusGood else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .clickable { onToggleDone(t.routineId, !isDone) }
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
                                text = t.title,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None
                                )
                            )
                            if (t.detail.isNotBlank()) {
                                Text(
                                    text = t.detail,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }

                        // Time
                        Text(
                            text = t.time,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        // Alarm toggle
                        IconButton(
                            onClick = { onToggleAlarm(t.routineId, t.alarmOn) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (t.alarmOn) Icons.Default.Alarm else Icons.Default.AlarmOff,
                                contentDescription = "Toggle alarm",
                                tint = if (t.alarmOn) BrandEmerald else Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Delete
                        IconButton(
                            onClick = { onDeleteTask(t.routineId) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete task",
                                tint = Color.Gray.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    if (idx < tasks.size - 1) {
                        Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

private fun parseHour(time: String): Int {
    return time.split(":").firstOrNull()?.toIntOrNull() ?: 8
}
