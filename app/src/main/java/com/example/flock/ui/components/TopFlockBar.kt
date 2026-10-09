package com.example.flock.ui.components

import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
    lastDay: Int? = null,
    /** the short view (the day in brief) or the full one; null hides the switch */
    basicView: Boolean? = null,
    onBasicView: (Boolean) -> Unit = {}
) {
    val harvestAge = lastDay ?: flock?.harvestAge ?: 42
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("top_flock_bar"),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
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
                        // Outside temperature and humidity side by side (humidity changes how hot birds feel).
                        val tempStr = weather?.let { String.format("%.1f°C · %.0f%%", it.tempC, it.rhPercent) } ?: "—"
                        Text(
                            text = tempStr,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = com.example.ui.theme.NumberFont
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
            }

            // the view switch, above the day slider: the day in brief, or everything
            if (basicView != null) ViewSwitch(basicView, onBasicView)

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
                    Surface(
                        color = com.example.ui.theme.SoftFill,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        // the dot carries the colour of the period the day is in (the same colours as the slider)
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            val period = dayPeriods(harvestAge, flock?.harvestAge ?: harvestAge).firstOrNull { selectedDay in it.from..it.to }
                            if (period != null) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(period.color))
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                text = "Day $selectedDay",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                    if (selectedDay != currentFlockDay) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = com.example.ui.theme.SoftFill,
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
                // the slider and the flock's periods in one: the track is coloured by period
                DaySlider(selectedDay, harvestAge, flock?.harvestAge ?: harvestAge, currentFlockDay, onSelectDay, Modifier.weight(1f))
                IconButton(onClick = onNextDay, enabled = selectedDay < harvestAge, modifier = Modifier.size(36.dp).testTag("next_day")) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next day")
                }
            }
        }
    }
}

/** Two halves, one chosen: Basic (the day in brief) or Advanced (every number). */
@Composable
private fun ViewSwitch(basic: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().then(com.example.ui.theme.softBox(RoundedCornerShape(12.dp))).padding(3.dp).testTag("view_switch"),
        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        listOf(true to "Basic", false to "Advanced").forEach { (isBasic, label) ->
            val on = basic == isBasic
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                    .background(if (on) com.example.ui.theme.SoftFillStrong else Color.Transparent)
                    .clickable { onChange(isBasic) }
                    .padding(vertical = 5.dp)
                    .testTag(if (isBasic) "view_basic" else "view_advanced"),
                contentAlignment = Alignment.Center
            ) {
                Text(label, maxLines = 1, softWrap = false,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Medium),
                    color = Color.White.copy(alpha = if (on) 1f else 0.6f))
            }
        }
    }
}

/** One stretch of the flock's life on the day slider. */
data class DayPeriod(val name: String, val from: Int, val to: Int, val color: Color)

/** Brooding (the floor opens day by day, heat on) to day 10, growing to day 27, finishing to the planned harvest, and any days run past it. */
fun dayPeriods(lastDay: Int, plannedHarvest: Int): List<DayPeriod> = buildList {
    val end = lastDay.coerceAtLeast(1)
    val harvest = plannedHarvest.coerceIn(1, end)
    add(DayPeriod("Brooding", 0, minOf(10, harvest), Color(0xFFFFA057)))
    if (harvest > 10) add(DayPeriod("Growing", 11, minOf(27, harvest), Color(0xFF3DD68C)))
    if (harvest > 27) add(DayPeriod("Finishing", 28, harvest, Color(0xFF70B8FF)))
    if (end > harvest) add(DayPeriod("Past harvest", harvest + 1, end, Color(0xFFFF9592)))
}

private val TRACK_PAD = 6.dp

/**
 * The day slider and the flock's periods in one piece: the track itself is coloured by period (brooding orange,
 * growing green, finishing blue, days past the planned harvest red) — bright up to the chosen day, dim after
 * it — with a white bar at the chosen day and a small white dot at today. Drag or tap to choose a day.
 */
@Composable
private fun DaySlider(day: Int, lastDay: Int, plannedHarvest: Int, today: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val span = lastDay.coerceAtLeast(1)
    val periods = remember(span, plannedHarvest) { dayPeriods(span, plannedHarvest) }
    val cut = MaterialTheme.colorScheme.background
    val select by androidx.compose.runtime.rememberUpdatedState(onSelect)
    androidx.compose.foundation.Canvas(
        modifier.height(36.dp).testTag("day_slider")
            .semantics {
                contentDescription = "Day $day of $span" + (periods.firstOrNull { day in it.from..it.to }?.let { ", " + it.name } ?: "")
                progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(day.toFloat(), 0f..span.toFloat(), (span - 1).coerceAtLeast(0))
                setProgress { v -> select(v.roundToInt().coerceIn(0, span)); true }
            }
            .pointerInput(span) {
                detectTapGestures { o ->
                    val pad = TRACK_PAD.toPx()
                    select(((o.x - pad) / (size.width - 2 * pad) * span).roundToInt().coerceIn(0, span))
                }
            }
            .pointerInput(span) {
                fun dayAt(x: Float): Int { val pad = TRACK_PAD.toPx(); return ((x - pad) / (size.width - 2 * pad) * span).roundToInt().coerceIn(0, span) }
                detectHorizontalDragGestures(onDragStart = { o -> select(dayAt(o.x)) }) { change, _ -> change.consume(); select(dayAt(change.position.x)) }
            }
    ) {
        val pad = TRACK_PAD.toPx(); val w = size.width - 2 * pad
        val cy = size.height / 2; val th = 8.dp.toPx(); val gap = 1.dp.toPx()
        fun x(d: Float) = pad + (d / span).coerceIn(0f, 1f) * w
        val sel = x(day.toFloat())
        fun segments(alpha: Float) = periods.forEach { p ->
            val a = if (p.from <= 0) x(0f) else x(p.from - 0.5f) + gap
            val b = if (p.to >= span) x(span.toFloat()) else x(p.to + 0.5f) - gap
            if (b > a) drawRoundRect(p.color.copy(alpha = alpha), androidx.compose.ui.geometry.Offset(a, cy - th / 2), androidx.compose.ui.geometry.Size(b - a, th),
                androidx.compose.ui.geometry.CornerRadius(th / 2, th / 2))
        }
        segments(0.26f)                                   // the days ahead: dim
        clipRect(right = sel) { segments(1f) }            // up to the chosen day: bright
        // today: a small dot on the track
        if (today in 0..span && today != day) drawCircle(Color.White, 2.5.dp.toPx(), androidx.compose.ui.geometry.Offset(x(today.toFloat()), cy))
        // the chosen day: a white bar, the track cut away beside it
        val tw = 4.dp.toPx()
        drawRect(cut, androidx.compose.ui.geometry.Offset(sel - tw / 2 - 3.dp.toPx(), cy - th), androidx.compose.ui.geometry.Size(tw + 6.dp.toPx(), th * 2))
        drawRoundRect(Color.White, androidx.compose.ui.geometry.Offset(sel - tw / 2, cy - 13.dp.toPx()), androidx.compose.ui.geometry.Size(tw, 26.dp.toPx()),
            androidx.compose.ui.geometry.CornerRadius(tw / 2, tw / 2))
    }
}
