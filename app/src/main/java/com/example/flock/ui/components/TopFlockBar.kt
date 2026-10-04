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
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    onWeatherClick: () -> Unit = {},
    /** the last day the day bar reaches: the planned harvest age, or further once the flock has run past it */
    lastDay: Int? = null
) {
    val harvestAge = lastDay ?: flock?.harvestAge ?: 42
    Surface(border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.GlassLine), 
        modifier = Modifier
            .fillMaxWidth()
            .testTag("top_flock_bar"),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
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
                Surface(border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.GlassLine), 
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
                Surface(border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.GlassLine), 
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
                        // Outside temperature and humidity side by side (humidity changes how hot birds feel).
                        val tempStr = weather?.let { String.format("%.1f°C · %.0f%%", it.tempC, it.rhPercent) } ?: "—"
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
                Surface(border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.GlassLine), 
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
            }

            // Row 2: Day Stepper (cannot exceed current real flock day) + Date + Cutoff / Lock Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Day chip + "Today" jump (no arrows — use the slider to scrub)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.testTag("day_stepper")
                ) {
                    Surface(border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.GlassLine), 
                        color = androidx.compose.ui.graphics.Color.Black,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Day $selectedDay",
                            color = androidx.compose.ui.graphics.Color(0xFFF2F2F0),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    if (selectedDay != currentFlockDay) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.GlassLine), 
                            color = androidx.compose.ui.graphics.Color.Black,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onSelectDay(currentFlockDay) }.testTag("today_button")
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Today, contentDescription = "Go to today", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Today", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
                            }
                        }
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
                IconButton(onClick = onPrevDay, enabled = selectedDay > 0, modifier = Modifier.size(36.dp).testTag("prev_day")) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous day")
                }
                // Local drag state so the thumb tracks the finger smoothly (no round-trip jitter).
                var sliderPos by remember { mutableFloatStateOf(selectedDay.toFloat()) }
                LaunchedEffect(selectedDay) { sliderPos = selectedDay.toFloat() }
                Slider(
                    value = sliderPos.coerceIn(0f, harvestAge.toFloat()),
                    onValueChange = {
                        sliderPos = it
                        onSelectDay(it.roundToInt().coerceIn(0, harvestAge))
                    },
                    valueRange = 0f..harvestAge.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = androidx.compose.ui.graphics.Color(0xFFF2F2F0),
                        activeTrackColor = androidx.compose.ui.graphics.Color(0xFFF2F2F0),
                        inactiveTrackColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.18f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("day_slider")
                )
                IconButton(onClick = onNextDay, enabled = selectedDay < harvestAge, modifier = Modifier.size(36.dp).testTag("next_day")) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next day")
                }
            }
        }
    }
}
