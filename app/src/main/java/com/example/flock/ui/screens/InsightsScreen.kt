package com.example.flock.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flock.data.DailyDataEntity
import com.example.flock.data.FarmEntity
import com.example.flock.data.FlockEntity
import com.example.ui.theme.BrandEmerald
import com.example.ui.theme.DomainFeed
import com.example.ui.theme.DomainInsight
import com.example.ui.theme.DomainInsightWash
import com.example.ui.theme.StatusCrit
import com.example.ui.theme.StatusGood
import com.example.ui.theme.StatusWarn

@Composable
fun InsightsScreen(
    entry: DailyDataEntity?,
    flock: FlockEntity?,
    farm: FarmEntity,
    modifier: Modifier = Modifier
) {
    val day = (entry?.dayNumber ?: 1).coerceAtLeast(1)
    val avgWeightG = entry?.avgWeight ?: entry?.idealWeight ?: 0.0
    val avgWeightKg = avgWeightG / 1000.0
    val livability = entry?.livability ?: 100.0
    val fcr = entry?.fcr ?: 1.35
    val cFcr = entry?.cFcr ?: 1.35
    val cv = entry?.cv ?: 8.5
    val density = entry?.densityKgM2 ?: 0.0

    // European Production Efficiency Factor (EPEF)
    // EPEF = (Livability % * Average Weight kg) / (Age in days * FCR) * 100
    val epef = if (day > 0 && fcr > 0) {
        (livability * avgWeightKg) / (day * fcr) * 100.0
    } else 0.0

    // Uniformity estimate from CV
    // In normal distribution: % within ±10% ≈ erf(10 / (cv * sqrt(2)))
    val majorityPct = (100.0 - (cv * 2.2)).coerceIn(55.0, 95.0)
    val underPct = ((100.0 - majorityPct) / 2.0).coerceAtLeast(2.0)
    val overPct = 100.0 - majorityPct - underPct

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("insights_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Production Performance KPIs Banner
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
                        text = "Flock Performance Index",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Surface(color = DomainInsightWash, shape = RoundedCornerShape(8.dp)) {
                        Text(
                            text = String.format("EPEF %.0f", epef),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = DomainInsight
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KpiTile(
                        modifier = Modifier.weight(1f),
                        label = "Livability",
                        value = String.format("%.1f%%", livability),
                        isGood = livability >= 96.0
                    )
                    KpiTile(
                        modifier = Modifier.weight(1f),
                        label = "cFCR (2kg)",
                        value = String.format("%.3f", cFcr),
                        isGood = cFcr <= 1.45
                    )
                    KpiTile(
                        modifier = Modifier.weight(1f),
                        label = "Density",
                        value = String.format("%.1f", density),
                        isGood = density <= farm.densityCapDefault,
                        sub = "kg/m²"
                    )
                }
            }
        }

        // 2. Uniformity & Weight-Distribution Bands (CV%)
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
                        text = "Flock Uniformity & CV%",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    val cvRating = if (cv < 10.0) "Uniform (<10%)" else if (cv < 12.0) "Moderate" else "Uneven (>12%)"
                    val cvRatingColor = if (cv < 10.0) StatusGood else if (cv < 12.0) StatusWarn else StatusCrit
                    Surface(
                        color = cvRatingColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = cvRating,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = cvRatingColor
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Text(
                    text = String.format("Current sample CV: %.1f%% across 5 house locations.", cv),
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Distribution Stacked Bar
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                ) {
                    val w = size.width
                    val h = size.height

                    val underW = (w * (underPct / 100.0)).toFloat()
                    val majW = (w * (majorityPct / 100.0)).toFloat()
                    val overW = (w * (overPct / 100.0)).toFloat()

                    // Underweight (StatusCrit/Warn)
                    drawRoundRect(
                        color = StatusWarn,
                        topLeft = Offset(0f, 0f),
                        size = Size(underW, h),
                        cornerRadius = CornerRadius(4.dp.toPx())
                    )

                    // Majority (BrandEmerald)
                    drawRect(
                        color = BrandEmerald,
                        topLeft = Offset(underW, 0f),
                        size = Size(majW, h)
                    )

                    // Overweight (DomainFeed)
                    drawRoundRect(
                        color = DomainFeed,
                        topLeft = Offset(underW + majW, 0f),
                        size = Size(overW, h),
                        cornerRadius = CornerRadius(4.dp.toPx())
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Legend
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).background(StatusWarn, RoundedCornerShape(2.dp)))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = String.format("Small: %.1f%%", underPct), style = MaterialTheme.typography.labelSmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).background(BrandEmerald, RoundedCornerShape(2.dp)))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = String.format("Majority (±10%%): %.1f%%", majorityPct), style = MaterialTheme.typography.labelSmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).background(DomainFeed, RoundedCornerShape(2.dp)))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = String.format("Heavy: %.1f%%", overPct), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // 3. Actionable Management Insights
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Operational Action Checklist",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(10.dp))

                if (cv >= 10.0) {
                    ActionBullet(
                        isWarning = true,
                        title = "Flock Uniformity Alert (CV ${String.format("%.1f", cv)}%)",
                        description = "Check feeder hopper distribution and line heights. Ensure smaller birds have unobstructed access during morning runs."
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (density > farm.densityCapDefault * 0.9) {
                    ActionBullet(
                        isWarning = density > farm.densityCapDefault,
                        title = "Stocking Density Advisory",
                        description = if (density > farm.densityCapDefault) "Density exceeds cap. Schedule thin-out / partial harvest." else "Approaching house capacity limit."
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                ActionBullet(
                    isWarning = false,
                    title = "Ventilation & Crop Protection",
                    description = "Ensure feed lines are raised 2 hours before peak heat to prevent metabolic stress."
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun KpiTile(
    label: String,
    value: String,
    isGood: Boolean,
    modifier: Modifier = Modifier,
    sub: String? = null
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        color = if (isGood) StatusGood else StatusWarn
                    )
                )
                if (sub != null) {
                    Text(
                        text = " $sub",
                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }
        }
    }
}

@Composable
fun ActionBullet(
    isWarning: Boolean,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = if (isWarning) Icons.Default.Warning else Icons.Default.CheckCircle,
            contentDescription = null,
            tint = if (isWarning) StatusWarn else StatusGood,
            modifier = Modifier.size(18.dp).padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
