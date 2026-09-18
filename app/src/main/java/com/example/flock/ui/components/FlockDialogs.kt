package com.example.flock.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flock.data.FarmEntity
import com.example.flock.data.FlockEntity
import com.example.ui.theme.BrandEmerald
import com.example.ui.theme.StatusCrit
import com.example.ui.theme.StatusGood
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FlockManagementDialog(
    flocks: List<FlockEntity>,
    activeFlockId: String?,
    onSelectFlock: (String) -> Unit,
    onCreateFlock: (name: String, breed: String, startDate: String, placed: Int, targetWeight: Double, harvestAge: Int, season: String) -> Unit,
    onDeleteFlock: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showCreateForm by remember { mutableStateOf(false) }

    val todayStr = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
    var name by remember { mutableStateOf("House 3") }
    var breed by remember { mutableStateOf("Ross308") }
    var startDate by remember { mutableStateOf(todayStr) }
    var birdsPlaced by remember { mutableStateOf("15000") }
    var targetWeight by remember { mutableStateOf("3200") }
    var harvestAge by remember { mutableStateOf("42") }
    var season by remember { mutableStateOf("Monsoon") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (showCreateForm) "Start New Batch" else "Flock Registry",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!showCreateForm) {
                    Text(
                        text = "ACTIVE & REGISTERED FLOCKS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )

                    flocks.forEach { f ->
                        val isSelected = f.flockId == activeFlockId
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectFlock(f.flockId) }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = f.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "${f.breed} · ${f.birdsPlaced} placed · start ${f.startDate}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                                if (flocks.size > 1) {
                                    IconButton(onClick = { onDeleteFlock(f.flockId) }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete flock",
                                            tint = StatusCrit
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { showCreateForm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add New Batch")
                    }
                } else {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("House / Batch Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = breed,
                        onValueChange = { breed = it },
                        label = { Text("Breed (Ross308 or Cobb500)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = startDate,
                        onValueChange = { startDate = it },
                        label = { Text("Placement Date (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = birdsPlaced,
                        onValueChange = { birdsPlaced = it },
                        label = { Text("Birds Placed") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = targetWeight,
                        onValueChange = { targetWeight = it },
                        label = { Text("Target Weight (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = harvestAge,
                        onValueChange = { harvestAge = it },
                        label = { Text("Harvest Age (days)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = season,
                        onValueChange = { season = it },
                        label = { Text("Season (Summer, Monsoon, Winter)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            if (showCreateForm) {
                Button(
                    onClick = {
                        val placedInt = birdsPlaced.toIntOrNull() ?: 15000
                        val targetDbl = targetWeight.toDoubleOrNull() ?: 3200.0
                        val harvestInt = harvestAge.toIntOrNull() ?: 42
                        onCreateFlock(name, breed, startDate, placedInt, targetDbl, harvestInt, season)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald)
                ) {
                    Text("Create Batch")
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        },
        dismissButton = {
            if (showCreateForm) {
                TextButton(onClick = { showCreateForm = false }) {
                    Text("Back")
                }
            }
        }
    )
}

@Composable
fun FarmSettingsDialog(
    farm: FarmEntity,
    onSaveFarm: (FarmEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var houseName by remember { mutableStateOf(farm.houseName) }
    var lengthFt by remember { mutableStateOf(farm.usableLengthFt.toString()) }
    var widthFt by remember { mutableStateOf(farm.usableWidthFt.toString()) }
    var fanCount by remember { mutableStateOf(farm.fanCount.toString()) }
    var fanRatedCfm by remember { mutableStateOf(farm.fanRatedCfm.toString()) }
    var heaterCount by remember { mutableStateOf(farm.heaterCount.toString()) }
    var drinkTankL by remember { mutableStateOf(farm.drinkTankL.toString()) }
    var feedBagKg by remember { mutableStateOf(farm.feedBagKg.toString()) }
    var season by remember { mutableStateOf(farm.season) }
    var weatherLat by remember { mutableStateOf(farm.weatherLat.toString()) }
    var weatherLon by remember { mutableStateOf(farm.weatherLon.toString()) }
    var weatherName by remember { mutableStateOf(farm.weatherName) }
    var densityCap by remember { mutableStateOf(farm.densityCapDefault.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Farm Hardware & Profile",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Drives all volumetric, ventilation, feed & water calculations.",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )

                OutlinedTextField(
                    value = houseName,
                    onValueChange = { houseName = it },
                    label = { Text("House Label") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = lengthFt,
                        onValueChange = { lengthFt = it },
                        label = { Text("Usable Length (ft)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = widthFt,
                        onValueChange = { widthFt = it },
                        label = { Text("Usable Width (ft)") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = fanCount,
                        onValueChange = { fanCount = it },
                        label = { Text("Fans Installed") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = fanRatedCfm,
                        onValueChange = { fanRatedCfm = it },
                        label = { Text("Fan CFM (ea)") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = drinkTankL,
                        onValueChange = { drinkTankL = it },
                        label = { Text("Tank Volume (L)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = feedBagKg,
                        onValueChange = { feedBagKg = it },
                        label = { Text("Feed Bag (kg)") },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = densityCap,
                    onValueChange = { densityCap = it },
                    label = { Text("Base Density Cap (kg/m²)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = season,
                    onValueChange = { season = it },
                    label = { Text("Season (Monsoon, Summer, Winter)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "WEATHER LOCATION (OPEN-METEO)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                OutlinedTextField(
                    value = weatherName,
                    onValueChange = { weatherName = it },
                    label = { Text("Location Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = weatherLat,
                        onValueChange = { weatherLat = it },
                        label = { Text("Latitude") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = weatherLon,
                        onValueChange = { weatherLon = it },
                        label = { Text("Longitude") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveFarm(
                        farm.copy(
                            houseName = houseName,
                            usableLengthFt = lengthFt.toDoubleOrNull() ?: farm.usableLengthFt,
                            usableWidthFt = widthFt.toDoubleOrNull() ?: farm.usableWidthFt,
                            fanCount = fanCount.toIntOrNull() ?: farm.fanCount,
                            fanRatedCfm = fanRatedCfm.toDoubleOrNull() ?: farm.fanRatedCfm,
                            drinkTankL = drinkTankL.toDoubleOrNull() ?: farm.drinkTankL,
                            feedBagKg = feedBagKg.toDoubleOrNull() ?: farm.feedBagKg,
                            densityCapDefault = densityCap.toDoubleOrNull() ?: farm.densityCapDefault,
                            season = season,
                            weatherName = weatherName,
                            weatherLat = weatherLat.toDoubleOrNull() ?: farm.weatherLat,
                            weatherLon = weatherLon.toDoubleOrNull() ?: farm.weatherLon
                        )
                    )
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald)
            ) {
                Text("Save Profile")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
