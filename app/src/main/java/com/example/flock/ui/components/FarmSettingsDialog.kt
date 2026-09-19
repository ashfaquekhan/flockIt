package com.example.flock.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.flock.data.ConfigEntity
import com.example.flock.data.FarmEntity
import com.example.flock.data.FeedTypeEntity
import com.example.ui.theme.BrandEmerald

@Composable
fun FarmSettingsDialog(
    farm: FarmEntity,
    config: ConfigEntity,
    feedTypes: List<FeedTypeEntity>,
    onDismiss: () -> Unit,
    onSaveFarmSettings: (FarmEntity, ConfigEntity) -> Unit,
    onSaveFeedType: (FeedTypeEntity) -> Unit,
    onDeleteFeedType: (String) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }

    var houseName by remember { mutableStateOf(farm.houseName) }
    var timeZone by remember { mutableStateOf(farm.timeZone) }
    var cutoffTime by remember { mutableStateOf(farm.cutoffTime) }
    var lengthFt by remember { mutableStateOf(farm.lengthFt.toString()) }
    var widthFt by remember { mutableStateOf(farm.widthFt.toString()) }
    var heightFt by remember { mutableStateOf(farm.heightFt.toString()) }
    var fanCount by remember { mutableStateOf(farm.fanCount.toString()) }
    var fanCfm by remember { mutableStateOf(farm.fanRatedCfm.toString()) }
    var drinkTankL by remember { mutableStateOf(farm.drinkTankL.toString()) }
    var feedBagKg by remember { mutableStateOf(farm.feedBagKg.toString()) }
    var densityCap by remember { mutableStateOf(farm.densityCapDefault.toString()) }

    // Placement area, cooling pads, drinker/feeder hardware, heaters & fuel
    var usableLengthFt by remember { mutableStateOf(farm.usableLengthFt.toString()) }
    var usableWidthFt by remember { mutableStateOf(farm.usableWidthFt.toString()) }
    var padAreaFt2 by remember { mutableStateOf(farm.padAreaFt2.toString()) }
    var padCount by remember { mutableStateOf(farm.padCount.toString()) }
    var heaterCount by remember { mutableStateOf(farm.heaterCount.toString()) }
    var heaterKw by remember { mutableStateOf(farm.heaterKw.toString()) }
    var drinkerLines by remember { mutableStateOf(farm.drinkerLines.toString()) }
    var nippleLineHoldL by remember { mutableStateOf(farm.nippleLineHoldL.toString()) }
    var feederLines by remember { mutableStateOf(farm.feederLines.toString()) }
    var feederLineBags by remember { mutableStateOf(farm.feederLineBags.toString()) }
    var feederMoveMin by remember { mutableStateOf(farm.feederMoveMin.toString()) }
    var pansPerFeederLine by remember { mutableStateOf(farm.pansPerFeederLine.toString()) }
    var dieselCanL by remember { mutableStateOf(farm.dieselCanL.toString()) }

    var tempBand by remember { mutableStateOf(config.tempBand.toString()) }
    var rhMin by remember { mutableStateOf(config.rhMin.toString()) }
    var rhMax by remember { mutableStateOf(config.rhMax.toString()) }
    var cvWarn by remember { mutableStateOf(config.cvWarn.toString()) }
    var cvCrit by remember { mutableStateOf(config.cvCrit.toString()) }

    // FeedType Add state
    var newFeedCode by remember { mutableStateOf("") }
    var newFeedName by remember { mutableStateOf("") }
    var newFeedBagKg by remember { mutableStateOf("60.0") }
    var newFeedPhase by remember { mutableStateOf("grower") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 24.dp)
                .testTag("farm_settings_dialog"),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Farm & Shed Configuration",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = farm.farmName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("House & Climate") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Feed Types (_FeedTypes)") }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (selectedTab == 0) {
                        // House & Specs
                        Text("Shed Geometry & Hardware", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = houseName,
                                onValueChange = { houseName = it },
                                label = { Text("House Name") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = cutoffTime,
                                onValueChange = { cutoffTime = it },
                                label = { Text("Daily Cutoff Time") },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        OutlinedTextField(
                            value = timeZone,
                            onValueChange = { timeZone = it },
                            label = { Text("Time Zone (IANA)") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = lengthFt,
                                onValueChange = { lengthFt = it },
                                label = { Text("Length (ft)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = widthFt,
                                onValueChange = { widthFt = it },
                                label = { Text("Width (ft)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = heightFt,
                                onValueChange = { heightFt = it },
                                label = { Text("Height (ft)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = fanCount,
                                onValueChange = { fanCount = it },
                                label = { Text("Fan Count") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = fanCfm,
                                onValueChange = { fanCfm = it },
                                label = { Text("Fan Rated CFM") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = drinkTankL,
                                onValueChange = { drinkTankL = it },
                                label = { Text("Drink Tank (L)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = feedBagKg,
                                onValueChange = { feedBagKg = it },
                                label = { Text("Feed Bag (kg)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = densityCap,
                                onValueChange = { densityCap = it },
                                label = { Text("Density Cap (kg/m²)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Divider(modifier = Modifier.padding(vertical = 4.dp))

                        Text("Placement Area & Cooling Pads", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(usableLengthFt, { usableLengthFt = it }, label = { Text("Placement length (ft)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                            OutlinedTextField(usableWidthFt, { usableWidthFt = it }, label = { Text("Placement width (ft)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(padAreaFt2, { padAreaFt2 = it }, label = { Text("Pad area (ft²)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                            OutlinedTextField(padCount, { padCount = it }, label = { Text("Number of pads") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                        }

                        Divider(modifier = Modifier.padding(vertical = 4.dp))

                        Text("Heaters & Fuel", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(heaterCount, { heaterCount = it }, label = { Text("Heaters") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                            OutlinedTextField(heaterKw, { heaterKw = it }, label = { Text("Heater kW each") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                            OutlinedTextField(dieselCanL, { dieselCanL = it }, label = { Text("Diesel can (L)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                        }

                        Divider(modifier = Modifier.padding(vertical = 4.dp))

                        Text("Drinkers & Feeders", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(drinkerLines, { drinkerLines = it }, label = { Text("Drinker lines") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                            OutlinedTextField(nippleLineHoldL, { nippleLineHoldL = it }, label = { Text("Nipple line fill (L)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(feederLines, { feederLines = it }, label = { Text("Feeder lines") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                            OutlinedTextField(feederLineBags, { feederLineBags = it }, label = { Text("Bags/feeder line") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(feederMoveMin, { feederMoveMin = it }, label = { Text("Feeder move (min)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                            OutlinedTextField(pansPerFeederLine, { pansPerFeederLine = it }, label = { Text("Pans/feeder line") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                        }

                        Divider(modifier = Modifier.padding(vertical = 4.dp))

                        Text("Target Comfort Bands", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = tempBand,
                                onValueChange = { tempBand = it },
                                label = { Text("Temp Band (±°C)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = rhMin,
                                onValueChange = { rhMin = it },
                                label = { Text("RH Min %") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = rhMax,
                                onValueChange = { rhMax = it },
                                label = { Text("RH Max %") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = cvWarn,
                                onValueChange = { cvWarn = it },
                                label = { Text("CV Warn %") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = cvCrit,
                                onValueChange = { cvCrit = it },
                                label = { Text("CV Crit %") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        // User-Defined Feed Types (_FeedTypes)
                        Text(
                            text = "Custom Feed Types (_FeedTypes)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Define feed types available on your farm for delivery logging and stock tracking.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        for (ft in feedTypes) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "${ft.code} — ${ft.name}",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = "${ft.bagKg} kg/bag · Phase: ${ft.phase}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    IconButton(
                                        onClick = { onDeleteFeedType(ft.code) },
                                        enabled = feedTypes.size > 1
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete feed type", tint = Color.Gray)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Add Feed Type Form
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Add Custom Feed Type", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = newFeedCode,
                                        onValueChange = { newFeedCode = it.uppercase() },
                                        label = { Text("Code (e.g. B4)") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = newFeedName,
                                        onValueChange = { newFeedName = it },
                                        label = { Text("Name (e.g. Withdrawal)") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1.5f)
                                    )
                                }

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = newFeedBagKg,
                                        onValueChange = { newFeedBagKg = it },
                                        label = { Text("Bag Weight (kg)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = newFeedPhase,
                                        onValueChange = { newFeedPhase = it },
                                        label = { Text("Phase") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Button(
                                    onClick = {
                                        if (newFeedCode.isNotBlank() && newFeedName.isNotBlank()) {
                                            val newFt = FeedTypeEntity(
                                                spreadsheetId = farm.spreadsheetId,
                                                code = newFeedCode,
                                                name = newFeedName,
                                                bagKg = newFeedBagKg.toDoubleOrNull() ?: 50.0,
                                                phase = newFeedPhase,
                                                sortOrder = feedTypes.size + 1
                                            )
                                            onSaveFeedType(newFt)
                                            newFeedCode = ""
                                            newFeedName = ""
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                                    enabled = newFeedCode.isNotBlank() && newFeedName.isNotBlank()
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add Feed Type")
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = {
                            val updatedFarm = farm.copy(
                                houseName = houseName,
                                timeZone = timeZone,
                                cutoffTime = cutoffTime,
                                lengthFt = lengthFt.toDoubleOrNull() ?: farm.lengthFt,
                                widthFt = widthFt.toDoubleOrNull() ?: farm.widthFt,
                                heightFt = heightFt.toDoubleOrNull() ?: farm.heightFt,
                                fanCount = fanCount.toIntOrNull() ?: farm.fanCount,
                                fanRatedCfm = fanCfm.toDoubleOrNull() ?: farm.fanRatedCfm,
                                drinkTankL = drinkTankL.toDoubleOrNull() ?: farm.drinkTankL,
                                feedBagKg = feedBagKg.toDoubleOrNull() ?: farm.feedBagKg,
                                densityCapDefault = densityCap.toDoubleOrNull() ?: farm.densityCapDefault,
                                usableLengthFt = usableLengthFt.toDoubleOrNull() ?: farm.usableLengthFt,
                                usableWidthFt = usableWidthFt.toDoubleOrNull() ?: farm.usableWidthFt,
                                padAreaFt2 = padAreaFt2.toDoubleOrNull() ?: farm.padAreaFt2,
                                padCount = padCount.toIntOrNull() ?: farm.padCount,
                                heaterCount = heaterCount.toIntOrNull() ?: farm.heaterCount,
                                heaterKw = heaterKw.toDoubleOrNull() ?: farm.heaterKw,
                                drinkerLines = drinkerLines.toIntOrNull() ?: farm.drinkerLines,
                                nippleLineHoldL = nippleLineHoldL.toDoubleOrNull() ?: farm.nippleLineHoldL,
                                feederLines = feederLines.toIntOrNull() ?: farm.feederLines,
                                feederLineBags = feederLineBags.toIntOrNull() ?: farm.feederLineBags,
                                feederMoveMin = feederMoveMin.toDoubleOrNull() ?: farm.feederMoveMin,
                                pansPerFeederLine = pansPerFeederLine.toIntOrNull() ?: farm.pansPerFeederLine,
                                dieselCanL = dieselCanL.toDoubleOrNull() ?: farm.dieselCanL
                            )
                            val updatedConfig = config.copy(
                                tempBand = tempBand.toDoubleOrNull() ?: config.tempBand,
                                rhMin = rhMin.toDoubleOrNull() ?: config.rhMin,
                                rhMax = rhMax.toDoubleOrNull() ?: config.rhMax,
                                cvWarn = cvWarn.toDoubleOrNull() ?: config.cvWarn,
                                cvCrit = cvCrit.toDoubleOrNull() ?: config.cvCrit
                            )
                            onSaveFarmSettings(updatedFarm, updatedConfig)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald)
                    ) {
                        Text("Save Settings")
                    }
                }
            }
        }
    }
}
