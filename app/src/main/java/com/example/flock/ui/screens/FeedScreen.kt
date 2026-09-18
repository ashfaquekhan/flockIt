package com.example.flock.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flock.data.DailyDataEntity
import com.example.flock.data.FarmEntity
import com.example.flock.engine.PhysiologicalEngine
import com.example.ui.theme.DomainFeed
import com.example.ui.theme.DomainFeedWash
import com.example.ui.theme.StatusCrit
import com.example.ui.theme.StatusGood
import com.example.ui.theme.StatusWarn
import kotlin.math.roundToInt

@Composable
fun FeedScreen(
    entry: DailyDataEntity?,
    farm: FarmEntity,
    modifier: Modifier = Modifier
) {
    val bagsToIssue = entry?.feedBags ?: 0
    val totalFeedKg = entry?.totalFeedKg ?: 0.0
    val feedPerBirdG = entry?.feedPerBird ?: 0.0
    val fcr = entry?.fcr
    val cFcr = entry?.cFcr
    val bagWeightKg = farm.feedBagKg

    // Heat de-rate factor
    val temp = entry?.outTemp ?: 20.0
    val deRatePct = if (temp > 20.0) (temp - 20.0) * 1.2 else 0.0

    // Day/Night distribution
    val dayBags = (bagsToIssue * (farm.feedDistDay / 100.0)).roundToInt().coerceAtLeast(0)
    val midBags = (bagsToIssue * (farm.feedDistMid / 100.0)).roundToInt().coerceAtLeast(0)
    val nightBags = (bagsToIssue - dayBags - midBags).coerceAtLeast(0)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("feed_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Daily Issue Banner
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
                        text = "Feed to Issue Today",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Surface(color = DomainFeedWash, shape = RoundedCornerShape(8.dp)) {
                        Text(
                            text = "${bagWeightKg.toInt()} kg / bag",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = DomainFeed
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
                        text = "$bagsToIssue",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = DomainFeed
                        )
                    )
                    Text(
                        text = " bags",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = String.format("(%,.0f kg total · %.1f g/bird)", totalFeedKg, feedPerBirdG),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                if (deRatePct > 0.0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.TrendingDown,
                            contentDescription = null,
                            tint = StatusWarn,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = String.format("Heat de-rated: -%.1f%% due to %.1f°C incoming temp", deRatePct, temp),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = StatusWarn,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }
        }

        // 2. 5 Time Blocks Feed Distribution
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Diurnal Distribution (5 Blocks)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Protects birds against heat prostration by keeping crops empty at peak heat.",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )

                Spacer(modifier = Modifier.height(12.dp))

                FeedBlockRow(
                    time = "06:00",
                    title = "Morning fill",
                    pct = farm.feedDistDay,
                    bags = dayBags,
                    action = "Fill pans before temperature climbs",
                    isRest = false
                )
                Divider(modifier = Modifier.padding(vertical = 8.dp))

                FeedBlockRow(
                    time = "10:00",
                    title = "Pre-peak pull",
                    pct = 0,
                    bags = 0,
                    action = "Raise feeder lines or stop hoppers",
                    isRest = true
                )
                Divider(modifier = Modifier.padding(vertical = 8.dp))

                FeedBlockRow(
                    time = "12:00",
                    title = "Peak heat",
                    pct = 0,
                    bags = 0,
                    action = "Empty crops · full tunnel ventilation",
                    isRest = true
                )
                Divider(modifier = Modifier.padding(vertical = 8.dp))

                FeedBlockRow(
                    time = "17:00",
                    title = "Evening restart",
                    pct = farm.feedDistMid,
                    bags = midBags,
                    action = "Lower lines as ambient falls",
                    isRest = false
                )
                Divider(modifier = Modifier.padding(vertical = 8.dp))

                FeedBlockRow(
                    time = "21:00",
                    title = "Night feed",
                    pct = farm.feedDistNight,
                    bags = nightBags,
                    action = "Primary growth window in cool night air",
                    isRest = false
                )
            }
        }

        // 3. Conversion Cards (FCR, cFCR, Breed Standard)
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Feed Conversion Performance",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FcrCard(
                        modifier = Modifier.weight(1f),
                        label = "FCR Actual",
                        value = if (fcr != null && fcr > 0) String.format("%.3f", fcr) else "—",
                        sub = "feed / weight gain"
                    )
                    FcrCard(
                        modifier = Modifier.weight(1f),
                        label = "cFCR (2.0kg)",
                        value = if (cFcr != null && cFcr > 0) String.format("%.3f", cFcr) else "—",
                        sub = "std weight corrected"
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val stdFcr = entry?.weightAge?.let { d -> PhysiologicalEngine.stdFcrFromDay(d, "Ross308") }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Breed benchmark at weight-age:",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                    Text(
                        text = String.format("%.3f", stdFcr ?: 1.35),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun FeedBlockRow(
    time: String,
    title: String,
    pct: Int,
    bags: Int,
    action: String,
    isRest: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.width(60.dp)) {
            Text(
                text = time,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            )
            if (pct > 0) {
                Text(
                    text = "$pct%",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = DomainFeed,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = if (isRest) StatusCrit else MaterialTheme.colorScheme.onSurface
                )
            )
            Text(
                text = action,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }

        if (!isRest) {
            Surface(
                color = DomainFeedWash,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "$bags bags",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = DomainFeed
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Lines Up",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = StatusCrit
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun FcrCard(
    label: String,
    value: String,
    sub: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    color = DomainFeed
                )
            )
            Text(
                text = sub,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            )
        }
    }
}
