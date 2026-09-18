package com.example.flock.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.ui.theme.BrandEmerald
import com.example.ui.theme.DomainFeed
import com.example.ui.theme.StatusCrit
import com.example.ui.theme.StatusGood
import com.example.ui.theme.StatusGoodWash
import com.example.ui.theme.StatusProjected
import com.example.ui.theme.StatusWarn
import com.example.ui.theme.StatusWarnWash

@Composable
fun GlanceTiles(
    entry: DailyDataEntity?,
    modifier: Modifier = Modifier
) {
    val d = entry

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("glance_tiles_container"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Tile 1: Sample Avg / Projected Weight
            GlanceTile(
                modifier = Modifier.weight(1f),
                label = "Sample avg",
                accentColor = BrandEmerald,
                value = String.format("%.0f", (d?.avgWeight ?: d?.idealWeight) ?: 0.0),
                unit = "g",
                isWarning = d?.projected == true,
                warningColor = StatusProjected,
                subtext = if (d?.projected == true) "projected · no sample" else String.format("wt-age %.1f d", d?.weightAge ?: 0.0)
            )

            // Tile 2: CV %
            val cv = d?.cv
            GlanceTile(
                modifier = Modifier.weight(1f),
                label = "CV %",
                accentColor = StatusGood,
                value = if (cv != null && cv > 0) String.format("%.1f", cv) else "—",
                unit = "%",
                badgeText = if (cv == null || cv == 0.0) null else if (cv < 10.0) "Uniform" else "Uneven",
                badgeIsGood = (cv ?: 0.0) < 10.0,
                subtext = if (cv == null) "no sample" else if (cv < 10.0) "target <10%" else "grade/split-feed"
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Tile 3: Mortality Yesterday
            GlanceTile(
                modifier = Modifier.weight(1f),
                label = "Mortality y'day",
                accentColor = StatusCrit,
                value = "${d?.mortality ?: 0}",
                unit = "birds",
                subtext = String.format("cum %d (%.2f%%)", d?.cumMort ?: 0, d?.cumMortPct ?: 0.0)
            )

            // Tile 4: Live Birds
            GlanceTile(
                modifier = Modifier.weight(1f),
                label = "Live birds",
                accentColor = DomainFeed,
                value = String.format("%,d", d?.liveBirds ?: 0),
                unit = "",
                subtext = String.format("livability %.1f%%", d?.livability ?: 100.0)
            )
        }
    }
}

@Composable
fun GlanceTile(
    label: String,
    accentColor: Color,
    value: String,
    unit: String,
    subtext: String,
    modifier: Modifier = Modifier,
    isWarning: Boolean = false,
    warningColor: Color = StatusCrit,
    badgeText: String? = null,
    badgeIsGood: Boolean = true
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp
    ) {
        Box {
            // Left border accent
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .matchParentSize()
                        .background(if (isWarning) warningColor else accentColor)
                )
            }

            Column(
                modifier = Modifier.padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    if (badgeText != null) {
                        Surface(
                            color = if (badgeIsGood) StatusGoodWash else StatusWarnWash,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    color = if (badgeIsGood) StatusGood else StatusWarn
                                ),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isWarning) warningColor else MaterialTheme.colorScheme.onSurface
                        )
                    )
                    if (unit.isNotBlank()) {
                        Text(
                            text = " $unit",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }

                Text(
                    text = subtext,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 10.5.sp,
                        color = if (isWarning) warningColor else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    maxLines = 1
                )
            }
        }
    }
}
