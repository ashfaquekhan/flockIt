package com.example.flock.ui.screens

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flock.data.DailyDataEntity
import com.example.flock.data.FarmEntity
import com.example.ui.theme.DomainWater
import com.example.ui.theme.DomainWaterWash
import com.example.ui.theme.StatusGood
import com.example.ui.theme.StatusWarn
import kotlin.math.max

@Composable
fun WaterScreen(
    entry: DailyDataEntity?,
    farm: FarmEntity,
    modifier: Modifier = Modifier
) {
    val totalWaterL = entry?.totalWaterL ?: 0.0
    val waterPerBirdMl = entry?.waterPerBird ?: 0.0
    val tankCapacity = farm.drinkTankL
    val refills = entry?.tankRefills ?: 1
    val temp = entry?.outTemp ?: 20.0
    val heatFactor = if (temp > 20.0) 1.0 + (temp - 20.0) * 0.06 else 1.0
    val wfRatio = 1.8 * heatFactor

    // Drinker height and line pressure guideline by weight
    val avgWeight = entry?.avgWeight ?: entry?.idealWeight ?: 0.0
    val (drinkerHeightDesc, columnPressureCm) = when {
        avgWeight < 300 -> "Eye level, birds peck upwards easily" to "5–10 cm H₂O"
        avgWeight < 1200 -> "Back height, birds reach at 45° angle" to "15–20 cm H₂O"
        else -> "Shoulder height, birds stretch neck slightly (60°)" to "25–35 cm H₂O"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("water_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Water Intake Banner
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Water Requirement Today",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Surface(color = DomainWaterWash, shape = RoundedCornerShape(8.dp)) {
                        Text(
                            text = String.format("W:F ratio %.2f", wfRatio),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = DomainWater
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = String.format("%,.0f", totalWaterL),
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = DomainWater
                        )
                    )
                    Text(
                        text = " Litres",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = String.format("(%.0f mL / bird)", waterPerBirdMl),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                if (heatFactor > 1.0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Thermostat, contentDescription = null, tint = StatusWarn, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = String.format("Heat uplift: +%.1f%% demand at %.1f°C", (heatFactor - 1.0) * 100.0, temp),
                            style = MaterialTheme.typography.bodySmall.copy(color = StatusWarn, fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }
        }

        // 2. Tank Refill Schedule
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tank Refill Schedule",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Surface(color = DomainWaterWash, shape = RoundedCornerShape(8.dp)) {
                        Text(
                            text = "$refills refills · ${tankCapacity.toInt()}L tank",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = DomainWater
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Text(
                    text = "Refill times spaced across the active working day (~${farm.drinkFillMin.toInt()} min fill time).",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )

                Spacer(modifier = Modifier.height(12.dp))

                val startH = 7
                val endH = 23
                for (k in 1..refills) {
                    val h = (startH + (endH - startH) * (k - 1) / max(1, refills))
                    val timeStr = String.format("%02d:00", h)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Schedule, contentDescription = null, tint = DomainWater, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = timeStr,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Refill #$k (~${tankCapacity.toInt()} L)",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp)) {
                            Text(
                                text = "${farm.drinkFillMin.toInt()} min pump",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (k < refills) {
                        Divider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }

        // 3. Line Pressure & Height Targets
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Line Targets & Sanitation",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(10.dp))

                TargetRow(label = "Drinker nipple height", value = drinkerHeightDesc)
                Divider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)

                TargetRow(label = "Line static pressure", value = columnPressureCm)
                Divider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)

                TargetRow(label = "Ideal water temp", value = "18°C – 21°C (<25°C max)")
                Divider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)

                TargetRow(label = "Sanitation & pH", value = "pH 6.0–6.8 · Free Cl 3–5 ppm · ORP >650 mV")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun TargetRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
            modifier = Modifier.width(130.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}
