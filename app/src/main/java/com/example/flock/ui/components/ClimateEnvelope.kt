package com.example.flock.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
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
import com.example.ui.theme.StatusCrit
import com.example.ui.theme.StatusGood
import com.example.ui.theme.StatusWarn

data class EnvelopeMetric(
    val label: String,
    val subtitle: String,
    val minBound: Double,
    val maxBound: Double,
    val idealLo: Double,
    val idealHi: Double,
    val currentValue: Double?,
    val unit: String,
    val decimals: Int = 0
)

@Composable
fun ClimateEnvelope(
    entry: DailyDataEntity?,
    modifier: Modifier = Modifier
) {
    val setTemp = entry?.setTemp ?: 20.0
    val band = 1.5
    val outTemp = entry?.outTemp
    val outRH = entry?.outRH
    val windChill = entry?.windChill

    val metrics = listOf(
        EnvelopeMetric(
            label = "Air temperature",
            subtitle = String.format("set %.1f°C ± %.1f", setTemp, band),
            minBound = setTemp - band - 4.0,
            maxBound = setTemp + band + 6.0,
            idealLo = setTemp - band,
            idealHi = setTemp + band,
            currentValue = outTemp,
            unit = "°C",
            decimals = 1
        ),
        EnvelopeMetric(
            label = "Felt / wind-chill",
            subtitle = "−0.0114 × airspeed",
            minBound = setTemp - band - 6.0,
            maxBound = setTemp + band + 6.0,
            idealLo = setTemp - band,
            idealHi = setTemp + band,
            currentValue = windChill,
            unit = "°C",
            decimals = 1
        ),
        EnvelopeMetric(
            label = "Humidity (RH)",
            subtitle = "ideal 50–70%",
            minBound = 40.0,
            maxBound = 95.0,
            idealLo = 50.0,
            idealHi = 70.0,
            currentValue = outRH,
            unit = "%",
            decimals = 0
        ),
        EnvelopeMetric(
            label = "CO₂ level",
            subtitle = "max 3,500 ppm",
            minBound = 400.0,
            maxBound = 4000.0,
            idealLo = 800.0,
            idealHi = 3000.0,
            currentValue = null,
            unit = " ppm",
            decimals = 0
        ),
        EnvelopeMetric(
            label = "NH₃ (ammonia)",
            subtitle = "max 20 ppm",
            minBound = 0.0,
            maxBound = 25.0,
            idealLo = 0.0,
            idealHi = 10.0,
            currentValue = null,
            unit = " ppm",
            decimals = 0
        ),
        EnvelopeMetric(
            label = "Static pressure",
            subtitle = "target 25 ± 10 Pa",
            minBound = 0.0,
            maxBound = 45.0,
            idealLo = 15.0,
            idealHi = 35.0,
            currentValue = null,
            unit = " Pa",
            decimals = 0
        )
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("climate_envelope_card"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Climate Envelope",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = if (outTemp != null) "Logged readings" else "Pending readings",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            metrics.forEachIndexed { index, metric ->
                EnvelopeRow(metric = metric)
                if (index < metrics.size - 1) {
                    Divider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EnvelopeRow(metric: EnvelopeMetric) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Label column
        Column(modifier = Modifier.width(110.dp)) {
            Text(
                text = metric.label,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 1
            )
            Text(
                text = metric.subtitle,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                maxLines = 1
            )
        }

        // Visual Range Canvas Bar
        Column(modifier = Modifier.weight(1f)) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
            ) {
                val w = size.width
                val h = size.height
                val totalRange = (metric.maxBound - metric.minBound).coerceAtLeast(1.0)

                // Background track
                drawRoundRect(
                    color = Color(0xFFE2DED3),
                    topLeft = Offset(0f, 0f),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )

                // Ideal Band (Green)
                val bandStartFrac = ((metric.idealLo - metric.minBound) / totalRange).toFloat().coerceIn(0f, 1f)
                val bandEndFrac = ((metric.idealHi - metric.minBound) / totalRange).toFloat().coerceIn(0f, 1f)
                val bandX = w * bandStartFrac
                val bandWidth = (w * bandEndFrac) - bandX

                drawRoundRect(
                    color = StatusGood,
                    topLeft = Offset(bandX, 0f),
                    size = Size(bandWidth, h),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )

                // Current Value Indicator Pin
                if (metric.currentValue != null) {
                    val curFrac = ((metric.currentValue - metric.minBound) / totalRange).toFloat().coerceIn(0f, 1f)
                    val pinX = w * curFrac
                    val pinColor = when {
                        metric.currentValue > metric.idealHi || metric.currentValue < metric.idealLo -> StatusWarn
                        else -> StatusGood
                    }

                    drawCircle(
                        color = Color.White,
                        radius = 6.dp.toPx(),
                        center = Offset(pinX, h / 2f)
                    )
                    drawCircle(
                        color = pinColor,
                        radius = 4.dp.toPx(),
                        center = Offset(pinX, h / 2f)
                    )
                }
            }

            // Ideal boundaries text
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = String.format("%.0f", metric.minBound),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, color = Color.Gray)
                )
                Text(
                    text = "ideal ${String.format("%.0f", metric.idealLo)}–${String.format("%.0f", metric.idealHi)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, color = StatusGood)
                )
                Text(
                    text = String.format("%.0f", metric.maxBound),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, color = Color.Gray)
                )
            }
        }

        // Current Value Text column
        val hasVal = metric.currentValue != null
        val curStr = if (hasVal) {
            String.format("%.${metric.decimals}f%s", metric.currentValue, metric.unit)
        } else "—"

        val valColor = if (!hasVal) Color.Gray else if (metric.currentValue!! > metric.idealHi || metric.currentValue < metric.idealLo) {
            StatusWarn
        } else StatusGood

        Text(
            text = curStr,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = valColor
            ),
            modifier = Modifier.width(55.dp)
        )
    }
}
