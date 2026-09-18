package com.example.flock.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flock.data.DailyDataEntity
import com.example.flock.data.FarmEntity
import com.example.ui.theme.BrandEmerald
import com.example.ui.theme.BrandWashLight
import com.example.ui.theme.DomainFeed
import com.example.ui.theme.StatusCrit
import com.example.ui.theme.StatusGood
import com.example.ui.theme.StatusWarn

@Composable
fun HouseFloorPlan(
    entry: DailyDataEntity?,
    farm: FarmEntity,
    modifier: Modifier = Modifier
) {
    val usableLength = farm.usableLengthFt
    val barricadeFt = entry?.barricadeFt ?: 0
    val fullHouse = entry?.occupiedFt2?.let { it >= (usableLength * farm.usableWidthFt * 0.98) } ?: false
    val occupiedFt2 = entry?.occupiedFt2 ?: 0.0
    val density = entry?.densityKgM2 ?: 0.0
    val ftPerBird = entry?.ftPerBird ?: 0.0
    val minFtPerBird = entry?.minFtPerBird ?: 0.0

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("house_floor_plan_card"),
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
                    text = "House Floor-Plan & Space",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Surface(
                    color = BrandWashLight,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (fullHouse) "Full House (${usableLength.toInt()} ft)" else "Barricade at $barricadeFt ft",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = BrandEmerald
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Visual House Blueprint
            val fractionOccupied = if (fullHouse || usableLength <= 0) 1.0f else (barricadeFt / usableLength).toFloat().coerceIn(0.1f, 1.0f)
            val broodFraction = (0.28f).coerceAtMost(fractionOccupied)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val w = size.width
                    val h = size.height

                    // Grid lines across house
                    val gridCols = 10
                    for (i in 1..gridCols) {
                        val x = w * (i.toFloat() / gridCols)
                        drawLine(
                            color = Color(0x18000000),
                            start = Offset(x, 0f),
                            end = Offset(x, h),
                            strokeWidth = 1f
                        )
                    }

                    // 1. Occupied Zone (Emerald wash)
                    val occupiedWidth = w * fractionOccupied
                    drawRect(
                        color = Color(0x331B7A63),
                        topLeft = Offset(0f, 0f),
                        size = Size(occupiedWidth, h)
                    )

                    // 2. Brood Area (Day 0 zone, subtle Amber)
                    val broodWidth = w * broodFraction
                    drawRect(
                        color = Color(0x25B26A1E),
                        topLeft = Offset(0f, 0f),
                        size = Size(broodWidth, h)
                    )

                    // Brood divider line (dashed)
                    drawLine(
                        color = DomainFeed,
                        start = Offset(broodWidth, 0f),
                        end = Offset(broodWidth, h),
                        strokeWidth = 2.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                    )

                    // Barricade boundary line
                    if (!fullHouse) {
                        drawLine(
                            color = BrandEmerald,
                            start = Offset(occupiedWidth, 0f),
                            end = Offset(occupiedWidth, h),
                            strokeWidth = 4.5f
                        )
                    }
                }

                // Labels overlaid on top of canvas
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Brood (D0)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = DomainFeed
                        )
                    )
                    Text(
                        text = if (fullHouse) "Full House" else "$barricadeFt ft Barricade",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandEmerald
                        )
                    )
                }
            }

            // Ticks along bottom
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "0 ft",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Text(
                    text = "${(usableLength / 2).toInt()} ft",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Text(
                    text = "${usableLength.toInt()} ft (gross)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(8.dp))

            // Density & Space metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "OCCUPIED AREA",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Text(
                        text = String.format("%,.0f ft²", occupiedFt2),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                val spaceOk = ftPerBird >= minFtPerBird
                Column {
                    Text(
                        text = "SPACE / BIRD",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Text(
                        text = String.format("%.2f ft² (min %.2f)", ftPerBird, minFtPerBird),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (spaceOk) StatusGood else StatusWarn
                        )
                    )
                }

                val densityOk = density <= farm.densityCapDefault
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "DENSITY",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Text(
                        text = String.format("%.1f / %.0f kg/m²", density, farm.densityCapDefault),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (densityOk) StatusGood else StatusCrit
                        )
                    )
                }
            }
        }
    }
}
