package com.example.flock.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flock.data.DailyDataEntity
import com.example.flock.data.FarmEntity
import com.example.flock.engine.PhysiologicalEngine
import com.example.ui.theme.DomainVent
import com.example.ui.theme.DomainVentWash
import kotlin.math.min

/**
 * Fan controller card: which fan does what, and the level table to enter on the controller.
 * Level 1 = min-vent timer fans; every level above adds one continuous fan, 0.3 °C warmer,
 * in the symmetric switch-on order (middle odd fan first).
 */
@Composable
fun FanVisualizer(
    entry: DailyDataEntity?,
    farm: FarmEntity,
    modifier: Modifier = Modifier
) {
    val totalFans = farm.fanCount
    val day = entry?.dayNumber ?: 0
    val l1Fans = entry?.fansToRun ?: 1
    val onSec = entry?.fanOnSec ?: 300
    val offSec = entry?.fanOffSec ?: 0
    val effFanCfm = farm.fanRatedCfm * (1.0 - farm.fanDerate)
    val ladder = PhysiologicalEngine.controllerLadder(
        day, entry?.setTemp ?: 20.0, l1Fans, onSec, offSec,
        totalFans, effFanCfm, farm.usableWidthFt * farm.heightFt
    )
    val sequence = PhysiologicalEngine.fanSequence(totalFans)
    // Level at which each physical fan first switches on (null = locked out at this age).
    val joinLevel = HashMap<Int, Int?>()
    sequence.forEachIndexed { i, fan ->
        joinLevel[fan] = when {
            i < ladder.levels.first().fans -> 1
            i < ladder.maxFans -> i - ladder.levels.first().fans + 3
            else -> null
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "fanSpin")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("fan_visualizer_card"),
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
                    text = "Fan Controller Levels",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Surface(color = DomainVentWash, shape = RoundedCornerShape(8.dp)) {
                    Text(
                        text = "Day $day",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = DomainVent),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Physical fan bank; label under each fan = level it joins.
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (fanIdx in 1..totalFans) {
                    val lvl = joinLevel[fanIdx]
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        FanItem(
                            fanNumber = fanIdx,
                            isRunning = lvl == 1,
                            locked = lvl == null,
                            rotation = if (lvl == 1) rotationAngle else 0f
                        )
                        Text(
                            text = lvl?.let { "L$it" } ?: "—",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (lvl == 1) DomainVent else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            MetaRow(label = "Switch-on order", value = sequence.take(ladder.maxFans).joinToString("→"))
            Spacer(modifier = Modifier.height(8.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(6.dp))

            // Level table — what to enter on the controller today.
            LevelRow("Lv", "Start °C", "Fans", "Run / Stop", header = true)
            ladder.levels.forEach { lv ->
                LevelRow(
                    lv = "${lv.level}",
                    start = String.format("%.1f", lv.startC),
                    fans = "${lv.fans}",
                    runStop = if (lv.offSec > 0) "${lv.onSec} / ${lv.offSec} s" else "continuous",
                    highlight = lv.level == 1
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Level 1 is the min-vent timer (air quality). Every level above adds one continuous fan to remove heat. " +
                    "Fans marked — stay off at this age (max ${ladder.maxFans}, ≈${ladder.maxAirSpeedFpm.toInt()} ft/min).",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LevelRow(
    lv: String,
    start: String,
    fans: String,
    runStop: String,
    header: Boolean = false,
    highlight: Boolean = false
) {
    val style = if (header) {
        MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            color = if (highlight) DomainVent else MaterialTheme.colorScheme.onSurface
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (highlight) DomainVentWash else Color.Transparent, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Text(lv, style = style, modifier = Modifier.weight(0.6f))
        Text(start, style = style, modifier = Modifier.weight(1.2f))
        Text(fans, style = style, modifier = Modifier.weight(0.8f))
        Text(runStop, style = style, modifier = Modifier.weight(1.6f), textAlign = TextAlign.End)
    }
}

@Composable
private fun FanItem(
    fanNumber: Int,
    isRunning: Boolean,
    locked: Boolean,
    rotation: Float,
    modifier: Modifier = Modifier
) {
    val idle = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .border(
                width = 1.dp,
                color = if (isRunning) DomainVent else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(8.dp)
            ),
        shape = RoundedCornerShape(8.dp),
        color = if (isRunning) DomainVentWash else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(2.dp)) {
            FanBlades(
                rotation = rotation,
                color = when {
                    isRunning -> DomainVent
                    locked -> idle.copy(alpha = 0.25f)
                    else -> idle.copy(alpha = 0.6f)
                },
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "$fanNumber",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (isRunning) DomainVent else idle
                ),
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}

@Composable
private fun FanBlades(rotation: Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = min(cx, cy)
        drawCircle(color = color, radius = r * 0.16f, center = Offset(cx, cy))
        for (i in 0..2) {
            rotate(degrees = rotation + i * 120f, pivot = Offset(cx, cy)) {
                val p = Path().apply {
                    moveTo(cx, cy)
                    quadraticBezierTo(cx + r * 0.55f, cy - r * 0.35f, cx + r * 0.12f, cy - r * 0.95f)
                    quadraticBezierTo(cx - r * 0.20f, cy - r * 0.50f, cx, cy)
                    close()
                }
                drawPath(p, color)
            }
        }
    }
}

@Composable
fun MetaRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
        )
    }
}
