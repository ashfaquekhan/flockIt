package com.example.flock.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.flock.data.FarmRegistryEntity
import com.example.flock.data.FlockEntity
import com.example.ui.theme.BrandEmerald
import com.example.ui.theme.StatusCrit

@Composable
fun InitialsAvatar(name: String, size: Dp) {
    val letter = name.trim().firstOrNull()?.uppercase() ?: "F"
    Box(
        modifier = Modifier
            .size(size)
            .background(BrandEmerald, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(letter, color = Color.White, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
    }
}

@Composable
fun AccountSheet(
    displayName: String,
    email: String,
    isDemo: Boolean,
    appVersion: String,
    onOpenRecycleBin: () -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { InitialsAvatar(displayName.ifBlank { email.ifBlank { "F" } }, 56.dp) },
        title = { Text(displayName.ifBlank { "Local Farmer" }, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (isDemo) "Offline (local only)" else email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Divider()
                TextButton(onClick = { onDismiss(); onOpenRecycleBin() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Recycle bin", modifier = Modifier.weight(1f))
                }
                Text(
                    "FlockIt v$appVersion",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            OutlinedButton(onClick = { onDismiss(); onSignOut() }) {
                Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isDemo) "Exit offline mode" else "Sign out")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun RecycleBinDialog(
    deletedFarms: List<FarmRegistryEntity>,
    deletedFlocks: List<FlockEntity>,
    onRestoreFarm: (String) -> Unit,
    onPurgeFarm: (String) -> Unit,
    onRestoreFlock: (spreadsheetId: String, flockId: String) -> Unit,
    onPurgeFlock: (spreadsheetId: String, flockId: String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.85f).padding(vertical = 24.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recycle Bin", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
                }
                Spacer(Modifier.height(4.dp))

                if (deletedFarms.isEmpty() && deletedFlocks.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("Recycle bin is empty", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (deletedFarms.isNotEmpty()) {
                            item { BinHeader("Farms") }
                            items(deletedFarms, key = { "farm_" + it.spreadsheetId }) { f ->
                                BinRow(
                                    title = f.farmName,
                                    subtitle = "Farm · ${f.role}",
                                    onRestore = { onRestoreFarm(f.spreadsheetId) },
                                    onPurge = { onPurgeFarm(f.spreadsheetId) }
                                )
                            }
                        }
                        if (deletedFlocks.isNotEmpty()) {
                            item { BinHeader("Flocks") }
                            items(deletedFlocks, key = { "flock_" + it.spreadsheetId + it.flockId }) { fl ->
                                BinRow(
                                    title = fl.name,
                                    subtitle = "Flock · ${fl.breed} · ${fl.birdsPlaced} placed",
                                    onRestore = { onRestoreFlock(fl.spreadsheetId, fl.flockId) },
                                    onPurge = { onPurgeFlock(fl.spreadsheetId, fl.flockId) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BinHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(top = 6.dp)
    )
}

@Composable
private fun BinRow(title: String, subtitle: String, onRestore: () -> Unit, onPurge: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            IconButton(onClick = onRestore) {
                Icon(Icons.Default.Restore, contentDescription = "Restore", tint = BrandEmerald)
            }
            IconButton(onClick = onPurge) {
                Icon(Icons.Default.DeleteForever, contentDescription = "Delete forever", tint = StatusCrit)
            }
        }
    }
}
