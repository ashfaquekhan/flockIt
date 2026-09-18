package com.example.flock.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flock.data.DailyDataEntity
import com.example.flock.ui.ReminderItem
import com.example.ui.theme.BrandEmerald
import com.example.ui.theme.DomainMed
import com.example.ui.theme.DomainMedWash
import com.example.ui.theme.DomainTask
import com.example.ui.theme.DomainTaskWash
import com.example.ui.theme.DomainWater
import com.example.ui.theme.DomainWaterWash
import com.example.ui.theme.StatusCrit
import com.example.ui.theme.StatusCritWash
import com.example.ui.theme.StatusGood
import com.example.ui.theme.StatusGoodWash
import com.example.ui.theme.StatusWarn
import com.example.ui.theme.StatusWarnWash

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersBottomSheet(
    reminders: List<ReminderItem>,
    entry: DailyDataEntity?,
    onDismissRequest: () -> Unit,
    onToggleReminder: (String, Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("reminders_bottom_sheet")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = BrandEmerald,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Live Reminders & Status",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
                IconButton(onClick = onDismissRequest) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // House Status Summary
            val alertLvl = entry?.alertLevel ?: "ok"
            val alertTxt = entry?.alertText ?: "All key markers OK"

            val (statusBg, statusFg, statusIcon) = when (alertLvl) {
                "crit" -> Triple(StatusCritWash, StatusCrit, Icons.Default.Warning)
                "warn" -> Triple(StatusWarnWash, StatusWarn, Icons.Default.Warning)
                else -> Triple(StatusGoodWash, StatusGood, Icons.Default.CheckCircle)
            }

            Surface(
                color = statusBg,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = statusIcon, contentDescription = null, tint = statusFg, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (alertLvl == "ok") "House Status: Normal" else "House Status: Attention Required",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = statusFg)
                        )
                        Text(
                            text = alertTxt,
                            style = MaterialTheme.typography.bodySmall.copy(color = statusFg, fontSize = 11.5.sp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "TODAY'S ACTION ITEMS",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Spacer(modifier = Modifier.height(6.dp))

            if (reminders.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No pending alarms or routines today.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(reminders, key = { it.id }) { item ->
                        ReminderRow(
                            item = item,
                            onToggle = { onToggleReminder(item.id, !item.isDone) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun ReminderRow(
    item: ReminderItem,
    onToggle: () -> Unit
) {
    val (kindBg, kindFg, kindIcon) = when (item.kind) {
        "water" -> Triple(DomainWaterWash, DomainWater, Icons.Default.Opacity)
        "med" -> Triple(DomainMedWash, DomainMed, Icons.Default.MedicalServices)
        else -> Triple(DomainTaskWash, DomainTask, Icons.Default.Check)
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (item.isDone) 0.5f else 1f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Badge
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(kindBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = kindIcon,
                    contentDescription = null,
                    tint = kindFg,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            textDecoration = if (item.isDone) TextDecoration.LineThrough else TextDecoration.None
                        )
                    )
                    if (item.isDue && !item.isDone) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(color = StatusCrit, shape = RoundedCornerShape(4.dp)) {
                            Text(
                                text = "DUE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    text = "${item.time} · ${item.detail}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.5.sp
                    )
                )
            }

            // Done check button
            Surface(
                shape = CircleShape,
                color = if (item.isDone) StatusGood else MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable { onToggle() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (item.isDone) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Done",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
