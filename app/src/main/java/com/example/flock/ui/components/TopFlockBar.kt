package com.example.flock.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    flock: FlockEntity?,
    selectedDay: Int,
    maxDay: Int,
    lockStatus: LockStatus,
    weather: WeatherResult?,
    reminderCount: Int,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    onFlockClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onBellClick: () -> Unit
) {
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
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1: Flock selector + Weather + Bell + Settings
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Flock Selector Chip
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
                                text = flock?.name ?: "No Flock",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${flock?.breed ?: "—"} · ${flock?.birdsPlaced ?: 0} placed",
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
                            contentDescription = "Switch flock",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Weather chip
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
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
                        val tempStr = weather?.let { String.format("%.1f°C", it.tempC) } ?: "—"
                        Text(
                            text = tempStr,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                }

                // Bell Icon for Reminders
                BadgedBox(
                    badge = {
                        if (reminderCount > 0) {
                            Badge(
                                containerColor = StatusCrit,
                                contentColor = Color.White
                            ) {
                                Text("$reminderCount")
                            }
                        }
                    }
                ) {
                    IconButton(
                        onClick = onBellClick,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("reminders_bell_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Reminders and Alerts",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Settings Icon
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

            // Row 2: Day stepper + Lock status countdown chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Day Stepper
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.testTag("day_stepper_row")
                ) {
                    IconButton(
                        onClick = onPrevDay,
                        enabled = selectedDay > 0,
                        modifier = Modifier.size(32.dp).testTag("prev_day_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Previous Day"
                        )
                    }

                    Surface(
                        color = BrandEmerald,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(
                                text = "$selectedDay",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    lineHeight = 22.sp
                                )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "DAY",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 10.sp
                                ),
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onNextDay,
                        enabled = selectedDay < maxDay,
                        modifier = Modifier.size(32.dp).testTag("next_day_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Next Day"
                        )
                    }
                }

                // Lock countdown or warning badge
                if (lockStatus.isToday) {
                    val (bgColor, textColor, textMsg) = if (lockStatus.isHardLocked) {
                        Triple(StatusCritWash, StatusCrit, "Hard inputs locked (past 11:00 AM)")
                    } else if (lockStatus.isCutoffApproaching) {
                        Triple(StatusWarnWash, StatusWarn, "Cutoff near! Closes at 11:00 AM")
                    } else {
                        Triple(StatusWarnWash, StatusWarn, "Timed inputs close at 11:00 AM")
                    }

                    Surface(
                        color = bgColor,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("cutoff_badge")
                    ) {
                        Text(
                            text = textMsg,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = textColor,
                                fontSize = 11.sp
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                } else if (lockStatus.isPastDay) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Past day · Read-only",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
