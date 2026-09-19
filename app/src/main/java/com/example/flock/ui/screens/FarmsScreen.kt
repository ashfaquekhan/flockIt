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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.flock.data.FarmRegistryEntity
import com.example.ui.theme.BrandEmerald

@Composable
fun FarmsScreen(
    farms: List<FarmRegistryEntity>,
    userEmail: String,
    isDemoMode: Boolean,
    onOpenFarm: (String) -> Unit,
    onCreateFarm: (String) -> Unit,
    onOpenSharedFarm: (String) -> Unit,
    onShareFarm: (FarmRegistryEntity) -> Unit,
    onDeleteFarm: (FarmRegistryEntity) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreate by remember { mutableStateOf(false) }
    var showOpenShared by remember { mutableStateOf(false) }
    var deletingFarm by remember { mutableStateOf<FarmRegistryEntity?>(null) }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Your Farms",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black)
                    )
                    Text(
                        text = (if (isDemoMode) "Offline · " else "") + userEmail.ifBlank { "local device" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onSignOut) {
                    Icon(Icons.Default.Logout, contentDescription = "Sign out", tint = BrandEmerald)
                }
            }

            Spacer(Modifier.height(12.dp))

            if (farms.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Agriculture,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "No farms yet",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            "Create a farm to start managing flocks.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(farms, key = { it.spreadsheetId }) { farm ->
                        FarmCard(
                            farm = farm,
                            onOpen = { onOpenFarm(farm.spreadsheetId) },
                            onShare = { onShareFarm(farm) },
                            onDelete = { deletingFarm = farm }
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
                Text("  New farm", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showOpenShared = true },
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Open a shared farm", color = BrandEmerald, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showCreate) {
        TextEntryDialog(
            title = "New farm",
            label = "Farm name",
            confirmText = "Create",
            onConfirm = { name -> if (name.isNotBlank()) onCreateFarm(name.trim()); showCreate = false },
            onDismiss = { showCreate = false }
        )
    }
    if (showOpenShared) {
        TextEntryDialog(
            title = "Open shared farm",
            label = "Google Sheet ID (from the share link)",
            confirmText = "Open",
            onConfirm = { id -> if (id.isNotBlank()) onOpenSharedFarm(id.trim()); showOpenShared = false },
            onDismiss = { showOpenShared = false }
        )
    }
    deletingFarm?.let { f ->
        AlertDialog(
            onDismissRequest = { deletingFarm = null },
            title = { Text("Delete farm?") },
            text = { Text("Remove \"${f.farmName}\" and all its flocks & data from this device? The Google Sheet (if any) is left intact.") },
            confirmButton = { TextButton(onClick = { onDeleteFarm(f); deletingFarm = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deletingFarm = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun FarmCard(
    farm: FarmRegistryEntity,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().clickable { onOpen() }
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    farm.farmName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "${farm.role} · ${farm.syncStatus}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (farm.isOwner) {
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.PersonAdd, contentDescription = "Share farm", tint = BrandEmerald)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete farm", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun TextEntryDialog(
    title: String,
    label: String,
    confirmText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(value) }) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
