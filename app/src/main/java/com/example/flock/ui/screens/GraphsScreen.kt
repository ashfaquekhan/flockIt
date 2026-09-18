package com.example.flock.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flock.data.DailyDataEntity
import com.example.flock.engine.PhysiologicalEngine
import com.example.ui.theme.BrandEmerald
import com.example.ui.theme.DomainFeed
import com.example.ui.theme.DomainWater
import com.example.ui.theme.StatusCrit
import com.example.ui.theme.StatusGood
import com.example.ui.theme.StatusProjected
import com.example.ui.theme.StatusWarn
import kotlin.math.max

@Composable
fun GraphsScreen(
    dailyRows: List<DailyDataEntity>,
    selectedDay: Int,
    onSelectDay: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val sortedRows = remember(dailyRows) { dailyRows.sortedBy { it.dayNumber } }
    val maxDay = (sortedRows.maxOfOrNull { it.dayNumber } ?: 42).coerceAtLeast(1)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("graphs_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Chart 1: Body Weight — Measured vs Projected vs Breed Standard
        WeightGrowthChartCard(
            rows = sortedRows,
            maxDay = maxDay,
            selectedDay = selectedDay,
            onSelectDay = onSelectDay
        )

        // Chart 2: Feed & Water Consumption Daily
        FeedWaterChartCard(
            rows = sortedRows,
            maxDay = maxDay,
            selectedDay = selectedDay,
            onSelectDay = onSelectDay
        )

        // Chart 3: Livability & Mortality Ceiling
        MortalityChartCard(
            rows = sortedRows,
            maxDay = maxDay,
            selectedDay = selectedDay,
            onSelectDay = onSelectDay
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun WeightGrowthChartCard(
    rows: List<DailyDataEntity>,
    maxDay: Int,
    selectedDay: Int,
    onSelectDay: (Int) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Body Weight Trajectory",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Tap any day to inspect",
                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LegendItem(color = Color.Gray, label = "Breed Std")
                LegendItem(color = BrandEmerald, label = "Measured")
                LegendItem(color = StatusProjected, label = "Projected (dashed)")
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Canvas Chart
            val maxWeight = 3600f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(rows) {
                            detectTapGestures { offset ->
                                val tappedDay = ((offset.x / size.width) * maxDay).toInt().coerceIn(0, maxDay)
                                onSelectDay(tappedDay)
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height

                    // Grid lines
                    for (yStep in 0..4) {
                        val y = h - (h * (yStep / 4f))
                        drawLine(
                            color = Color(0x18000000),
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1f
                        )
                    }

                    // 1. Breed Standard curve
                    val stdPath = Path()
                    for (d in 0..maxDay) {
                        val x = (d.toFloat() / maxDay) * w
                        val bw = PhysiologicalEngine.bwFromDay(d.toDouble(), "Ross308").toFloat()
                        val y = h - (bw / maxWeight) * h
                        if (d == 0) stdPath.moveTo(x, y) else stdPath.lineTo(x, y)
                    }
                    drawPath(
                        path = stdPath,
                        color = Color.Gray.copy(alpha = 0.5f),
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // 2. Actual & Projected points & paths
                    val measuredPath = Path()
                    val projPath = Path()
                    var hasMeasuredStart = false
                    var hasProjStart = false

                    rows.forEach { r ->
                        val d = r.dayNumber
                        val x = (d.toFloat() / maxDay) * w
                        val bw = (r.avgWeight ?: r.idealWeight ?: 0.0).toFloat()
                        val y = h - (bw / maxWeight) * h

                        if (!r.projected && r.sampleEntered) {
                            if (!hasMeasuredStart) {
                                measuredPath.moveTo(x, y)
                                hasMeasuredStart = true
                            } else {
                                measuredPath.lineTo(x, y)
                            }
                            drawCircle(color = BrandEmerald, radius = 4.dp.toPx(), center = Offset(x, y))
                        } else if (r.projected) {
                            if (!hasProjStart) {
                                projPath.moveTo(x, y)
                                hasProjStart = true
                            } else {
                                projPath.lineTo(x, y)
                            }
                            drawCircle(color = StatusProjected, radius = 2.5.dp.toPx(), center = Offset(x, y))
                        }
                    }

                    if (hasMeasuredStart) {
                        drawPath(
                            path = measuredPath,
                            color = BrandEmerald,
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }

                    if (hasProjStart) {
                        drawPath(
                            path = projPath,
                            color = StatusProjected,
                            style = Stroke(
                                width = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                            )
                        )
                    }

                    // Highlight selected day
                    val selX = (selectedDay.toFloat() / maxDay) * w
                    drawLine(
                        color = BrandEmerald.copy(alpha = 0.6f),
                        start = Offset(selX, 0f),
                        end = Offset(selX, h),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }

            // X-axis day labels
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "D0", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, color = Color.Gray))
                Text(text = "D14", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, color = Color.Gray))
                Text(text = "D28", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, color = Color.Gray))
                Text(text = "D$maxDay", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, color = Color.Gray))
            }
        }
    }
}

@Composable
fun FeedWaterChartCard(
    rows: List<DailyDataEntity>,
    maxDay: Int,
    selectedDay: Int,
    onSelectDay: (Int) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Daily Feed & Water per Bird",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LegendItem(color = DomainFeed, label = "Feed (g/bird)")
                LegendItem(color = DomainWater, label = "Water (mL/bird / 2)")
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bar Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(rows) {
                            detectTapGestures { offset ->
                                val tappedDay = ((offset.x / size.width) * maxDay).toInt().coerceIn(0, maxDay)
                                onSelectDay(tappedDay)
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val maxVal = 220f
                    val barWidth = (w / (maxDay + 1)) * 0.4f

                    rows.forEach { r ->
                        val d = r.dayNumber
                        val x = (d.toFloat() / (maxDay + 1)) * w
                        val feed = (r.feedPerBird ?: 0.0).toFloat()
                        val feedH = (feed / maxVal) * h

                        val water = ((r.waterPerBird ?: 0.0) / 2.0).toFloat()
                        val waterH = (water / maxVal) * h

                        // Feed bar
                        drawRect(
                            color = DomainFeed,
                            topLeft = Offset(x, h - feedH),
                            size = Size(barWidth, feedH)
                        )

                        // Water bar
                        drawRect(
                            color = DomainWater,
                            topLeft = Offset(x + barWidth, h - waterH),
                            size = Size(barWidth, waterH)
                        )
                    }

                    // Highlight selected day
                    val selX = (selectedDay.toFloat() / (maxDay + 1)) * w
                    drawLine(
                        color = Color.Black.copy(alpha = 0.3f),
                        start = Offset(selX, 0f),
                        end = Offset(selX, h),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }
        }
    }
}

@Composable
fun MortalityChartCard(
    rows: List<DailyDataEntity>,
    maxDay: Int,
    selectedDay: Int,
    onSelectDay: (Int) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Cumulative Mortality vs Ceiling",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LegendItem(color = StatusCrit, label = "Flock Cum %")
                LegendItem(color = StatusWarn, label = "Max Ceiling (dashed)")
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(rows) {
                            detectTapGestures { offset ->
                                val tappedDay = ((offset.x / size.width) * maxDay).toInt().coerceIn(0, maxDay)
                                onSelectDay(tappedDay)
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val maxMortPct = 5.0f

                    // Ceiling curve
                    val ceilPath = Path()
                    for (d in 0..maxDay) {
                        val x = (d.toFloat() / maxDay) * w
                        val ceil = PhysiologicalEngine.interpolate(PhysiologicalEngine.CURVE_MAXMORT_BY_AGE, d.toDouble()).toFloat()
                        val y = h - (ceil / maxMortPct) * h
                        if (d == 0) ceilPath.moveTo(x, y) else ceilPath.lineTo(x, y)
                    }
                    drawPath(
                        path = ceilPath,
                        color = StatusWarn,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                        )
                    )

                    // Actual cum mort path
                    val mortPath = Path()
                    var hasStart = false
                    rows.forEach { r ->
                        val d = r.dayNumber
                        val x = (d.toFloat() / maxDay) * w
                        val mort = (r.cumMortPct ?: 0.0).toFloat()
                        val y = h - (mort / maxMortPct) * h
                        if (!hasStart) {
                            mortPath.moveTo(x, y)
                            hasStart = true
                        } else {
                            mortPath.lineTo(x, y)
                        }
                        drawCircle(color = StatusCrit, radius = 2.5.dp.toPx(), center = Offset(x, y))
                    }

                    if (hasStart) {
                        drawPath(
                            path = mortPath,
                            color = StatusCrit,
                            style = Stroke(width = 2.5.dp.toPx())
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = color, shape = RoundedCornerShape(2.dp), modifier = Modifier.size(10.dp, 4.dp)) {}
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
        )
    }
}
