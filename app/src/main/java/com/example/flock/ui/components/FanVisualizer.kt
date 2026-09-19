package com.example.flock.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Toys
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flock.data.DailyDataEntity
import com.example.flock.data.FarmEntity
import com.example.ui.theme.DomainVent
import com.example.ui.theme.DomainVentWash

@Composable
fun FanVisualizer(
    entry: DailyDataEntity?,
    farm: FarmEntity,
    modifier: Modifier = Modifier
) {
    val totalFans = farm.fanCount
    val fansToRun = entry?.fansToRun ?: 1
    val onSec = entry?.fanOnSec ?: 300
    val offSec = entry?.fanOffSec ?: 0
    val ventModeName = when (entry?.ventMode) {
        2 -> "Tunnel cool"
        1 -> "Transitional"
        else -> "Min-vent (cycling)"
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
                    text = "Ventilation & Fans",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Surface(
                    color = DomainVentWash,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = ventModeName,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = DomainVent
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Fans Grid (Row of fans)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (fanIdx in 1..totalFans) {
                    val isRunning = fanIdx <= fansToRun
                    FanItem(
                        fanNumber = fanIdx,
                        isRunning = isRunning,
                        rotation = if (isRunning) rotationAngle else 0f,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(8.dp))

            // Specs metadata
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MetaRow(
                    label = "Fans running",
                    value = "$fansToRun of $totalFans (staged from Fan 1)"
                )
                MetaRow(
                    label = "Cycle timer",
                    value = if (offSec > 0) "$onSec s ON · $offSec s OFF (5-min timer)" else "Continuous (100% duty)"
                )
                MetaRow(
                    label = "Air delivery",
                    value = String.format("%.3f cfm/bird (req: %.3f cfm)", entry?.cfmPerBird ?: 0.0, entry?.cfmPerBird ?: 0.0)
                )
                MetaRow(
                    label = "House air-speed",
                    value = "${entry?.airspeed?.toInt() ?: 0} ft/min target"
                )
            }
        }
    }
}

@Composable
private fun FanItem(
    fanNumber: Int,
    isRunning: Boolean,
    rotation: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(52.dp)
            .border(
                width = 1.dp,
                color = if (isRunning) DomainVent else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(8.dp)
            ),
        shape = RoundedCornerShape(8.dp),
        color = if (isRunning) DomainVentWash else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(2.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Toys,
                contentDescription = "Fan $fanNumber",
                modifier = Modifier
                    .size(24.dp)
                    .rotate(rotation),
                tint = if (isRunning) DomainVent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                text = "$fanNumber",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (isRunning) DomainVent else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.align(Alignment.BottomEnd)
            )
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
