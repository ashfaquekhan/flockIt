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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Egg
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.flock.data.FlockEntity
import com.example.ui.theme.BrandEmerald
import com.example.ui.theme.StatusWarn
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun FlocksScreen(
    farmName: String,
    timeZone: String,
    flocks: List<FlockEntity>,
    onBack: () -> Unit,
    onOpenFlock: (String) -> Unit,
    onCreateFlock: (name: String, breed: String, startDate: String, startTime: String, placed: Int, transitMort: Int, harvestAge: Int) -> Unit,
    onDeleteFlock: (String) -> Unit,
    onToggleLock: (flockId: String, locked: Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreate by remember { mutableStateOf(false) }
    var deletingFlock by remember { mutableStateOf<FlockEntity?>(null) }
    val ordered = remember(flocks) { flocks.sortedBy { it.createdAt } }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back to farms", tint = BrandEmerald)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        farmName,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                        maxLines = 1
                    )
                    Text(
                        "Batches / flocks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Farm settings", tint = BrandEmerald)
                }
            }

            Spacer(Modifier.height(10.dp))

            if (ordered.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Egg, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("No flocks yet", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Text(
                            "Add a batch to start day-by-day tracking.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(ordered.size) { idx ->
                        val flock = ordered[idx]
                        FlockCard(
                            seq = idx + 1,
                            flock = flock,
                            timeZone = timeZone,
                            onOpen = { onOpenFlock(flock.flockId) },
                            onDelete = { deletingFlock = flock },
                            onToggleLock = { onToggleLock(flock.flockId, !flock.locked) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { showCreate = true },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                Text("  New flock / batch", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showCreate) {
        CreateFlockDialog(
            defaultName = "Batch #${ordered.size + 1}",
            onDismiss = { showCreate = false },
            onCreate = { name, breed, startDate, startTime, placed, transitMort, harvestAge ->
                onCreateFlock(name, breed, startDate, startTime, placed, transitMort, harvestAge)
                showCreate = false
            }
        )
    }
    deletingFlock?.let { f ->
        AlertDialog(
            onDismissRequest = { deletingFlock = null },
            title = { Text("Delete flock?") },
            text = { Text("Permanently remove \"${f.name}\" and all its daily data and tasks? This cannot be undone.") },
            confirmButton = { TextButton(onClick = { onDeleteFlock(f.flockId); deletingFlock = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deletingFlock = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun FlockCard(
    seq: Int,
    flock: FlockEntity,
    timeZone: String,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onToggleLock: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val curDay = remember(flock.startDate, timeZone) {
        try {
            val zone = ZoneId.of(timeZone)
            val start = LocalDate.parse(flock.startDate, DateTimeFormatter.ISO_LOCAL_DATE)
            ChronoUnit.DAYS.between(start, LocalDate.now(zone)).toInt().coerceIn(0, flock.harvestAge)
        } catch (e: Exception) { 0 }
    }
    val startPretty = remember(flock.startDate) {
        try {
            LocalDate.parse(flock.startDate, DateTimeFormatter.ISO_LOCAL_DATE)
                .format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
        } catch (e: Exception) { flock.startDate }
    }
    val isClosed = flock.status == "closed"

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().clickable { onOpen() }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "#$seq · ${flock.name}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f)
                )
                if (flock.locked) {
                    Icon(Icons.Default.Lock, contentDescription = "Locked", tint = StatusWarn, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                }
                FilterChip(
                    selected = !isClosed,
                    onClick = onOpen,
                    label = { Text(if (isClosed) "closed" else "Day $curDay/${flock.harvestAge}") }
                )
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (flock.locked) "Unlock (allow edits)" else "Lock (read-only)") },
                            leadingIcon = { Icon(if (flock.locked) Icons.Default.LockOpen else Icons.Default.Lock, contentDescription = null) },
                            onClick = { menuOpen = false; onToggleLock() }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete flock") },
                            leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
                            onClick = { menuOpen = false; onDelete() }
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${flock.breed} · ${flock.birdsPlaced} placed · started $startPretty",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "ID: ${flock.flockId}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CreateFlockDialog(
    defaultName: String,
    onDismiss: () -> Unit,
    onCreate: (name: String, breed: String, startDate: String, startTime: String, placed: Int, transitMort: Int, harvestAge: Int) -> Unit
) {
    var name by remember { mutableStateOf(defaultName) }
    var breed by remember { mutableStateOf("Ross308") }
    var startDate by remember { mutableStateOf(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)) }
    var startTime by remember { mutableStateOf("08:00") }
    var placed by remember { mutableStateOf("") }
    var transitMort by remember { mutableStateOf("0") }
    var harvest by remember { mutableStateOf("42") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New flock / batch") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Batch name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = breed == "Ross308", onClick = { breed = "Ross308" }, label = { Text("Ross 308") })
                    FilterChip(selected = breed == "Cobb500", onClick = { breed = "Cobb500" }, label = { Text("Cobb 500") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(startDate, { startDate = it }, label = { Text("Placement date") }, singleLine = true, modifier = Modifier.weight(1.4f))
                    OutlinedTextField(startTime, { startTime = it }, label = { Text("Time (HH:mm)") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(placed, { placed = it.filter { c -> c.isDigit() } }, label = { Text("Birds placed") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(transitMort, { transitMort = it.filter { c -> c.isDigit() } }, label = { Text("Transit mortality") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(harvest, { harvest = it.filter { c -> c.isDigit() } }, label = { Text("Harvest age (days)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val p = placed.toIntOrNull() ?: 0
                val tm = transitMort.toIntOrNull() ?: 0
                val h = harvest.toIntOrNull() ?: 42
                if (name.isNotBlank() && p > 0) onCreate(name.trim(), breed, startDate.trim(), startTime.trim(), p, tm, h)
            }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
