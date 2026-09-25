package com.example.flock.ui.screens

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
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flock.data.DailyDataEntity
import com.example.flock.data.FarmEntity
import com.example.flock.engine.PhysiologicalEngine
import com.example.ui.theme.BrandEmerald
import com.example.ui.theme.BrandWashLight
import com.example.ui.theme.StatusCrit
import com.example.ui.theme.StatusGood
import com.example.ui.theme.StatusProjected
import com.example.ui.theme.StatusWarn
import kotlin.math.abs

data class DualAnchorRow(
    val parameter: String,
    val unit: String,
    val calendarVal: String,
    val weightAgeVal: String,
    val deltaStr: String,
    val deltaIsAhead: Boolean?
)

@Composable
fun TargetsScreen(
    entry: DailyDataEntity?,
    farm: FarmEntity,
    breed: String = "Ross308",
    modifier: Modifier = Modifier
) {
    val calDay = entry?.dayNumber ?: 0
    val weightAge = entry?.weightAge ?: calDay.toDouble()
    val isProjected = entry?.projected == true
    val deltaAge = weightAge - calDay.toDouble()

    // 1. Calendar targets (Day calDay)
    val calBw = PhysiologicalEngine.bwFromDay(calDay.toDouble(), breed)
    val calTemp = PhysiologicalEngine.interpolate(PhysiologicalEngine.CURVE_TEMP_BY_BW, calBw)
    val calFeed = PhysiologicalEngine.dailyFeedFromDay(calDay.toDouble(), breed)
    val calVent = PhysiologicalEngine.designMinVentCfmPerBird(calBw / 1000.0)
    val calFcr = PhysiologicalEngine.stdFcrFromDay(calDay.toDouble(), breed)

    // 2. Weight-Age targets (Ground Data)
    val wtBw = entry?.avgWeight ?: PhysiologicalEngine.bwFromDay(weightAge, breed)
    val wtTemp = entry?.setTemp ?: PhysiologicalEngine.interpolate(PhysiologicalEngine.CURVE_TEMP_BY_BW, wtBw)
    val wtFeed = entry?.feedPerBird ?: PhysiologicalEngine.dailyFeedFromDay(weightAge, breed)
    val wtVent = entry?.cfmPerBird ?: PhysiologicalEngine.designMinVentCfmPerBird(wtBw / 1000.0)
    val wtFcr = PhysiologicalEngine.stdFcrFromDay(weightAge, breed)

    val rows = listOf(
        DualAnchorRow(
            parameter = "Body Weight",
            unit = "g",
            calendarVal = String.format("%.0f", calBw),
            weightAgeVal = String.format("%.0f", wtBw),
            deltaStr = String.format("%+.0f g", wtBw - calBw),
            deltaIsAhead = wtBw >= calBw
        ),
        DualAnchorRow(
            parameter = "Target Temp",
            unit = "°C",
            calendarVal = String.format("%.1f", calTemp),
            weightAgeVal = String.format("%.1f", wtTemp),
            deltaStr = String.format("%+.1f°C", wtTemp - calTemp),
            deltaIsAhead = null
        ),
        DualAnchorRow(
            parameter = "Min-Vent Rate",
            unit = "cfm/bird",
            calendarVal = String.format("%.3f", calVent),
            weightAgeVal = String.format("%.3f", wtVent),
            deltaStr = String.format("%+.3f", wtVent - calVent),
            deltaIsAhead = wtVent >= calVent
        ),
        DualAnchorRow(
            parameter = "Daily Feed Intake",
            unit = "g/bird",
            calendarVal = String.format("%.1f", calFeed),
            weightAgeVal = String.format("%.1f", wtFeed),
            deltaStr = String.format("%+.1f g", wtFeed - calFeed),
            deltaIsAhead = wtFeed >= calFeed
        ),
        DualAnchorRow(
            parameter = "Water Intake",
            unit = "mL/bird",
            calendarVal = String.format("%.0f", calFeed * 1.8),
            weightAgeVal = String.format("%.0f", entry?.waterPerBird ?: (wtFeed * 1.8)),
            deltaStr = String.format("%+.0f mL", (entry?.waterPerBird ?: (wtFeed * 1.8)) - (calFeed * 1.8)),
            deltaIsAhead = null
        ),
        DualAnchorRow(
            parameter = "Standard FCR",
            unit = "",
            calendarVal = String.format("%.3f", calFcr),
            weightAgeVal = String.format("%.3f", wtFcr),
            deltaStr = String.format("%+.3f", wtFcr - calFcr),
            deltaIsAhead = null
        ),
        DualAnchorRow(
            parameter = "Occupied Area",
            unit = "ft²",
            calendarVal = String.format("%,.0f", entry?.occupiedFt2 ?: (farm.usableLengthFt * farm.usableWidthFt)),
            weightAgeVal = String.format("%,.0f", entry?.occupiedFt2 ?: (farm.usableLengthFt * farm.usableWidthFt)),
            deltaStr = if (entry?.barricadeFt != null) "${entry.barricadeFt} ft" else "Full",
            deltaIsAhead = null
        )
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("targets_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Core Premise Anchor Header Card
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
                        text = "Physiological vs Calendar Anchor",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Surface(
                        color = if (isProjected) Color(0xFFFDECEB) else BrandWashLight,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isProjected) "Projected" else "Ground-Truth",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isProjected) StatusProjected else BrandEmerald
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Calendar Day Tile
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "CALENDAR AGE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Day $calDay",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold
                                )
                            )
                            Text(
                                text = "Calendar elapsed",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }

                    // Weight-Age Tile
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = if (isProjected) Color(0xFFFDECEB) else BrandWashLight,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "WEIGHT-AGE (REAL)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isProjected) StatusProjected else BrandEmerald
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = String.format("Day %.1f", weightAge),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isProjected) StatusProjected else BrandEmerald
                                )
                            )
                            Text(
                                text = if (deltaAge >= 0) String.format("+%.1f d ahead", deltaAge) else String.format("%.1f d behind", deltaAge),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (deltaAge >= 0) StatusGood else StatusWarn
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "All ventilation, temperature setpoints, and feed allowances must obey Weight-Age to match the flock's metabolic demand.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }

        // Side-by-Side Comparison Table
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Dual-Anchor Targets Table",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Table Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "PARAMETER",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(1.3f)
                    )
                    Text(
                        text = "CALENDAR",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "WEIGHT-AGE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = BrandEmerald
                        ),
                        modifier = Modifier.weight(1.1f)
                    )
                    Text(
                        text = "VARIANCE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(0.9f)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                rows.forEachIndexed { index, row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1.3f)) {
                            Text(
                                text = row.parameter,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = row.unit,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }

                        Text(
                            text = row.calendarVal,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        Text(
                            text = row.weightAgeVal,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = BrandEmerald
                            ),
                            modifier = Modifier.weight(1.1f)
                        )

                        val vColor = when (row.deltaIsAhead) {
                            true -> StatusGood
                            false -> StatusWarn
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }

                        Text(
                            text = row.deltaStr,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = vColor
                            ),
                            modifier = Modifier.weight(0.9f)
                        )
                    }

                    if (index < rows.size - 1) {
                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
