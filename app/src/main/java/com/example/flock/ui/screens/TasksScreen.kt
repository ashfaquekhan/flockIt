package com.example.flock.ui.screens

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import com.example.flock.data.TaskEntity
import com.example.ui.theme.BrandEmerald
import com.example.ui.theme.DomainFeed
import com.example.ui.theme.DomainTask
import com.example.ui.theme.DomainVent

/** Minimal segment colour: Morning = amber, Evening = blue, Night = indigo. */
fun blockColor(block: String): Color = when {
    block.startsWith("Morning") -> DomainFeed
    block.startsWith("Evening") -> DomainVent
    else -> DomainTask
}

val FIXED_BLOCKS = listOf(
    "Morning 05:00–08:30",
    "Morning 08:30–12:00",
    "Evening 12:00–16:00",
    "Evening 16:00–20:00",
    "Night 20:00–00:00",
    "Night 00:00–05:00"
)

fun detectBlockFromTime(time: String): String {
    val hour = time.split(":").getOrNull(0)?.toIntOrNull() ?: 7
    val min = time.split(":").getOrNull(1)?.toIntOrNull() ?: 0
    val totalMin = hour * 60 + min

    return when {
        totalMin in (5 * 60)..(8 * 60 + 30) -> "Morning 05:00–08:30"
        totalMin in (8 * 60 + 31)..(12 * 60) -> "Morning 08:30–12:00"
        totalMin in (12 * 60 + 1)..(16 * 60) -> "Evening 12:00–16:00"
        totalMin in (16 * 60 + 1)..(20 * 60) -> "Evening 16:00–20:00"
        totalMin in (20 * 60 + 1)..(23 * 60 + 59) -> "Night 20:00–00:00"
        else -> "Night 00:00–05:00"
    }
}

@Composable
fun TasksScreen(
    tasks: List<TaskEntity>,
    dayNumber: Int,
    onAddTask: (block: String, label: String, time: String, everyDay: Boolean) -> Unit,
    onDeleteTask: (taskId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var taskLabel by remember { mutableStateOf("") }
    var taskTime by remember { mutableStateOf("07:00") }
    var everyDay by remember { mutableStateOf(true) }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header & Add Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Day $dayNumber Task Planner",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "6 Fixed Daily Operational Blocks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = { showAddDialog = !showAddDialog },
                colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("add_task_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Task", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Task")
            }
        }

        // Add Task Card
        if (showAddDialog) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "New Operational Task",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )

                    OutlinedTextField(
                        value = taskLabel,
                        onValueChange = { taskLabel = it },
                        label = { Text("Task description") },
                        placeholder = { Text("e.g. Weigh 5 locations, Flush drinker line...") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("task_label_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = taskTime,
                            onValueChange = { taskTime = it },
                            label = { Text("Time (HH:mm)") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("task_time_input")
                        )

                        val autoBlock = detectBlockFromTime(taskTime)
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1.5f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("Auto Block:", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    text = autoBlock,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Repeat Every Day",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = if (everyDay) "Appears on all days of flock" else "Only on Day $dayNumber",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = everyDay,
                            onCheckedChange = { everyDay = it },
                            modifier = Modifier.testTag("task_everyday_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = {
                                if (taskLabel.isNotBlank()) {
                                    val block = detectBlockFromTime(taskTime)
                                    onAddTask(block, taskLabel, taskTime, everyDay)
                                    taskLabel = ""
                                    showAddDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                            enabled = taskLabel.isNotBlank()
                        ) {
                            Text("Save Task")
                        }
                    }
                }
            }
        }

        // 6 FIXED BLOCKS
        for (block in FIXED_BLOCKS) {
            val blockTasks = tasks.filter {
                it.block == block || (it.block.isBlank() && detectBlockFromTime(it.time) == block)
            }.sortedBy { it.time }

            BlockCard(
                blockTitle = block,
                tasks = blockTasks,
                accent = blockColor(block),
                onDeleteTask = onDeleteTask
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun BlockCard(
    blockTitle: String,
    tasks: List<TaskEntity>,
    accent: Color,
    onDeleteTask: (String) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(width = 4.dp, height = 18.dp)
                            .background(accent, RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = blockTitle,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = accent)
                    )
                }
                Text(
                    text = "${tasks.size} ${if (tasks.size == 1) "task" else "tasks"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Divider(modifier = Modifier.padding(vertical = 2.dp))

            if (tasks.isEmpty()) {
                Text(
                    text = "No tasks in this block",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                for (task in tasks) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = task.time,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = BrandEmerald
                                )
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = task.label,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                )
                                if (task.everyDay) {
                                    Text(
                                        text = "Every day template",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = { onDeleteTask(task.taskId) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete task",
                                tint = Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
