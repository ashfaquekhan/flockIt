package com.example.flock.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flock.data.TaskEntity
import com.example.flock.ui.components.TimePickerField
import com.example.ui.theme.BrandEmerald
import com.example.ui.theme.DomainFeed
import com.example.ui.theme.DomainTask
import com.example.ui.theme.DomainVent
import com.example.ui.theme.StatusWarn
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun blockColor(block: String): Color = when {
    block.startsWith("Morning") -> DomainFeed
    block.startsWith("Evening") -> DomainVent
    else -> DomainTask
}

val FIXED_BLOCKS = listOf("Morning 05:00–12:00", "Evening 12:00–20:00", "Night 20:00–05:00")

fun detectBlockFromTime(time: String): String {
    val hour = time.split(":").getOrNull(0)?.toIntOrNull() ?: 7
    val min = time.split(":").getOrNull(1)?.toIntOrNull() ?: 0
    val totalMin = hour * 60 + min
    return when (totalMin) {
        in (5 * 60)..(12 * 60 - 1) -> FIXED_BLOCKS[0]
        in (12 * 60)..(20 * 60 - 1) -> FIXED_BLOCKS[1]
        else -> FIXED_BLOCKS[2]
    }
}

private fun recurrenceLabel(t: TaskEntity): String = when (t.recurrence) {
    "once" -> "once"
    "everyN" -> "every ${t.everyN}d"
    "daily" -> "daily"
    else -> if (t.everyDay) "daily" else "once"
}

@Composable
fun TasksScreen(
    allTasks: List<TaskEntity>,
    dayNumber: Int,
    harvestAge: Int,
    onSaveTask: (taskId: String?, block: String, label: String, time: String, startDay: Int, endDay: Int, recurrence: String, everyN: Int, alertEnabled: Boolean, kind: String) -> Unit,
    onDeleteTask: (taskId: String) -> Unit,
    onToggleComplete: (taskId: String, day: Int, done: Boolean) -> Unit,
    onToggleAlert: (taskId: String, enabled: Boolean) -> Unit,
    onCopyToRange: (taskId: String, fromDay: Int, toDay: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by remember { mutableStateOf<TaskEntity?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var copying by remember { mutableStateOf<TaskEntity?>(null) }

    val dayTasks = remember(allTasks, dayNumber) {
        allTasks.filter { it.appliesOn(dayNumber, harvestAge) }.sortedBy { it.time }
    }
    val doneCount = dayTasks.count { it.isCompletedOn(dayNumber) }

    val scroll = rememberScrollState()
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(scroll).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Day $dayNumber planner", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black))
                Text("$doneCount of ${dayTasks.size} done", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(
                onClick = { editing = null; showAdd = true },
                colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp)); Text("Add task")
            }
        }

        for (block in FIXED_BLOCKS) {
            val tasks = dayTasks.filter { detectBlockFromTime(it.time) == block }
            BlockCard(block, tasks, dayNumber, blockColor(block), onToggleComplete, onToggleAlert,
                onEdit = { editing = it; showAdd = true }, onDelete = onDeleteTask, onCopy = { copying = it })
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showAdd) {
        AddEditTaskDialog(
            existing = editing,
            defaultDay = dayNumber,
            harvestAge = harvestAge,
            onDismiss = { showAdd = false },
            onSave = { label, time, startDay, endDay, recurrence, everyN, alert, kind ->
                onSaveTask(editing?.taskId, detectBlockFromTime(time), label, time, startDay, endDay, recurrence, everyN, alert, kind)
                showAdd = false
            }
        )
    }
    copying?.let { t ->
        CopyToRangeDialog(defaultFrom = dayNumber, defaultTo = harvestAge, harvestAge = harvestAge,
            onDismiss = { copying = null },
            onConfirm = { from, to -> onCopyToRange(t.taskId, from, to); copying = null })
    }
}

@Composable
private fun BlockCard(
    blockTitle: String,
    tasks: List<TaskEntity>,
    day: Int,
    accent: Color,
    onToggleComplete: (String, Int, Boolean) -> Unit,
    onToggleAlert: (String, Boolean) -> Unit,
    onEdit: (TaskEntity) -> Unit,
    onDelete: (String) -> Unit,
    onCopy: (TaskEntity) -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp), tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(width = 4.dp, height = 16.dp).background(accent, RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.Schedule, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(blockTitle, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = accent))
                Spacer(Modifier.weight(1f))
                Text("${tasks.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Divider()
            if (tasks.isEmpty()) {
                Text("No tasks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
            } else {
                for (t in tasks) TaskRow(t, day, onToggleComplete, onToggleAlert, onEdit, onDelete, onCopy)
            }
        }
    }
}

@Composable
private fun TaskRow(
    t: TaskEntity,
    day: Int,
    onToggleComplete: (String, Int, Boolean) -> Unit,
    onToggleAlert: (String, Boolean) -> Unit,
    onEdit: (TaskEntity) -> Unit,
    onDelete: (String) -> Unit,
    onCopy: (TaskEntity) -> Unit
) {
    val done = t.isCompletedOn(day)
    var menu by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        HoldCompleteButton(done) { onToggleComplete(t.taskId, day, !done) }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                t.label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.time, style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = BrandEmerald))
                Spacer(Modifier.width(8.dp))
                Text(recurrenceLabel(t), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (t.kind == "waterfill") {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.WaterDrop, contentDescription = "Water fill", tint = DomainVent, modifier = Modifier.size(12.dp))
                }
            }
        }
        IconButton(onClick = { onToggleAlert(t.taskId, !t.alertEnabled) }, modifier = Modifier.size(34.dp)) {
            Icon(
                if (t.alertEnabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                contentDescription = "Toggle alert",
                tint = if (t.alertEnabled) StatusWarn else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; onEdit(t) })
                DropdownMenuItem(text = { Text("Copy to days…") }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }, onClick = { menu = false; onCopy(t) })
                DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menu = false; onDelete(t.taskId) })
            }
        }
    }
}

/** Circular control: hold ~0.9s to toggle completion (avoids accidental taps). */
@Composable
private fun HoldCompleteButton(done: Boolean, onToggle: () -> Unit) {
    var progress by remember { mutableStateOf(0f) }
    val ring = if (done) BrandEmerald else MaterialTheme.colorScheme.outline
    Box(
        modifier = Modifier.size(30.dp).clip(CircleShape)
            .background(if (done) BrandEmerald else Color.Transparent)
            .pointerInput(done) {
                detectTapGestures(onPress = {
                    progress = 0f
                    var fired = false
                    coroutineScope {
                        val job = launch {
                            val t0 = System.nanoTime()
                            while (true) {
                                progress = ((System.nanoTime() - t0) / 1_000_000f / 900f).coerceIn(0f, 1f)
                                if (progress >= 1f) { fired = true; onToggle(); break }
                                delay(16)
                            }
                        }
                        tryAwaitRelease(); job.cancel()
                    }
                    if (!fired) progress = 0f
                })
            },
        contentAlignment = Alignment.Center
    ) {
        if (!done && progress > 0f) {
            Box(modifier = Modifier.size((30 * progress).dp).clip(CircleShape).background(BrandEmerald.copy(alpha = 0.35f)))
        }
        Box(modifier = Modifier.size(30.dp).clip(CircleShape).background(Color.Transparent)) {}
        Icon(
            Icons.Default.Check,
            contentDescription = if (done) "Done" else "Hold to complete",
            tint = if (done) Color.White else ring,
            modifier = Modifier.size(18.dp).align(Alignment.Center)
        )
    }
}

@Composable
private fun AddEditTaskDialog(
    existing: TaskEntity?,
    defaultDay: Int,
    harvestAge: Int,
    onDismiss: () -> Unit,
    onSave: (label: String, time: String, startDay: Int, endDay: Int, recurrence: String, everyN: Int, alert: Boolean, kind: String) -> Unit
) {
    var label by remember { mutableStateOf(existing?.label ?: "") }
    var time by remember { mutableStateOf(existing?.time ?: "07:00") }
    var recurrence by remember { mutableStateOf(existing?.recurrence?.ifBlank { if (existing.everyDay) "daily" else "once" } ?: "daily") }
    var everyN by remember { mutableStateOf((existing?.everyN ?: 2).toString()) }
    var startDay by remember { mutableStateOf((if ((existing?.startDay ?: -1) >= 0) existing!!.startDay else defaultDay).toString()) }
    var endDay by remember { mutableStateOf((if ((existing?.endDay ?: -1) >= 0) existing!!.endDay else harvestAge).toString()) }
    var alert by remember { mutableStateOf(existing?.alertEnabled ?: false) }
    var waterFill by remember { mutableStateOf(existing?.kind == "waterfill") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New task" else "Edit task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(label, { label = it }, label = { Text("Task") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TimePickerField(label = "Time", valueHHmm = time, onPick = { time = it }, modifier = Modifier.weight(1f))
                }
                Text("Repeat", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = recurrence == "daily", onClick = { recurrence = "daily" }, label = { Text("Daily") })
                    FilterChip(selected = recurrence == "once", onClick = { recurrence = "once" }, label = { Text("Once") })
                    FilterChip(selected = recurrence == "everyN", onClick = { recurrence = "everyN" }, label = { Text("Every N") })
                }
                if (recurrence == "everyN") {
                    OutlinedTextField(everyN, { everyN = it.filter { c -> c.isDigit() } }, label = { Text("Every N days") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(startDay, { startDay = it.filter { c -> c.isDigit() } }, label = { Text("From day") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(endDay, { endDay = it.filter { c -> c.isDigit() } }, label = { Text("To day") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Alert (notification at time)", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = alert, onCheckedChange = { alert = it })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Water-fill task (increases with age)", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = waterFill, onCheckedChange = { waterFill = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val s = startDay.toIntOrNull() ?: defaultDay
                val e = (endDay.toIntOrNull() ?: harvestAge).coerceAtLeast(s)
                val n = everyN.toIntOrNull()?.coerceAtLeast(1) ?: 2
                if (label.isNotBlank()) onSave(label.trim(), time.trim(), s, e, recurrence, n, alert, if (waterFill) "waterfill" else "task")
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CopyToRangeDialog(defaultFrom: Int, defaultTo: Int, harvestAge: Int, onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    var from by remember { mutableStateOf(defaultFrom.toString()) }
    var to by remember { mutableStateOf(defaultTo.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Copy task to days") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(from, { from = it.filter { c -> c.isDigit() } }, label = { Text("From day") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                OutlinedTextField(to, { to = it.filter { c -> c.isDigit() } }, label = { Text("To day") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val f = from.toIntOrNull() ?: defaultFrom
                val t = (to.toIntOrNull() ?: harvestAge).coerceAtLeast(f)
                onConfirm(f, t)
            }) { Text("Copy") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
