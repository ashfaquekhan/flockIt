package com.example.flock.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flock.data.FlockEntity
import com.example.flock.network.WeatherResult
import com.example.flock.ui.LockStatus
import com.example.ui.theme.BrandEmerald
import com.example.ui.theme.StatusCrit
import com.example.ui.theme.StatusCritWash
import com.example.ui.theme.StatusGood
import com.example.ui.theme.StatusWarn
import com.example.ui.theme.StatusWarnWash

@Composable
fun TopFlockBar(
    farmName: String,
    flock: FlockEntity?,
    selectedDay: Int,
    currentFlockDay: Int,
    dayDate: String,
    lockStatus: LockStatus,
    weather: WeatherResult?,
    syncStatus: String,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    onSelectDay: (Int) -> Unit,
    onFarmClick: () -> Unit,
    onFlockClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onWeatherClick: () -> Unit = {}
) {
    val harvestAge = flock?.harvestAge ?: 42
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("top_flock_bar"),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1: Farm / Flock Selector + Weather + Sync + Settings
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Flock & Farm Chip
                Surface(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onFlockClick() }
                        .testTag("flock_selector_chip"),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (flock?.status == "active") StatusGood else Color.Gray)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = flock?.name ?: "No Batch Selected",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$farmName · ${flock?.breed ?: "—"}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Switch batch or farm",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Weather Chip (tap to refresh)
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onWeatherClick() }
                        .testTag("weather_chip"),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.WbSunny,
                            contentDescription = "Weather",
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFFE5A93C)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        val tempStr = weather?.let { String.format("%.1f°C", it.tempC) } ?: "28°C"
                        Text(
                            text = tempStr,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Sync status chip
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onFarmClick() }
                        .testTag("sync_status_chip"),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val icon = when (syncStatus) {
                            "syncing" -> Icons.Default.Refresh
                            "offline" -> Icons.Default.CloudOff
                            else -> Icons.Default.CloudDone
                        }
                        val tint = when (syncStatus) {
                            "syncing" -> StatusWarn
                            "offline" -> Color.Gray
                            else -> StatusGood
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = syncStatus,
                            modifier = Modifier.size(16.dp),
                            tint = tint
                        )
                    }
                }

                // Farm Settings Icon
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("farm_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Farm Settings",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Row 2: Day Stepper (cannot exceed current real flock day) + Date + Cutoff / Lock Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Day Stepper
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.testTag("day_stepper")
                ) {
                    IconButton(
                        onClick = onPrevDay,
                        enabled = selectedDay > 0,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("prev_day_button")
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Day")
                    }

                    Surface(
                        color = if (selectedDay == currentFlockDay) BrandEmerald else MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = "Day $selectedDay",
                            color = if (selectedDay == currentFlockDay) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    IconButton(
                        onClick = onNextDay,
                        // Can view future days (ideal projections) up to harvest age
                        enabled = selectedDay < harvestAge,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("next_day_button")
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next Day")
                    }
                }

                // Date Label
                Text(
                    text = dayDate,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                )

                // Cutoff / Lock Chip
                val chipColor = when {
                    lockStatus.isHardLocked -> StatusCritWash
                    lockStatus.isCutoffApproaching -> StatusWarnWash
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                val textColor = when {
                    lockStatus.isHardLocked -> StatusCrit
                    lockStatus.isCutoffApproaching -> StatusWarn
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                val labelText = when {
                    lockStatus.isPastDay -> "Locked (Past)"
                    lockStatus.isFuture -> "Locked (Future)"
                    lockStatus.isHardLocked -> "Locked (11:00 cutoff)"
                    else -> "Inputs lock at ${lockStatus.cutoffTime}"
                }

                Surface(
                    color = chipColor,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = labelText,
                        color = textColor,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Row 3: clean day scrubber — glide across the whole cycle (future days show projections)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "0",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = selectedDay.toFloat().coerceIn(0f, harvestAge.toFloat()),
                    onValueChange = { onSelectDay(it.roundToInt().coerceIn(0, harvestAge)) },
                    valueRange = 0f..harvestAge.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = BrandEmerald,
                        activeTrackColor = BrandEmerald,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("day_slider")
                )
                Text(
                    "$harvestAge",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
