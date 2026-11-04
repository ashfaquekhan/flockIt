package com.example.flock.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.flock.ui.Clash

/**
 * Another phone changed the same entries as this one before this one's reached the sheet (v55): each value both ways,
 * and the farm keeps one. Nothing is overwritten until then; "Later" closes it until the next clash.
 */
@Composable
fun ClashDialog(clashes: List<Clash>, onKeep: (Clash, Boolean) -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("The other phone changed the same entry") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()).testTag("clash_dialog"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                clashes.forEach { c ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Day ${c.day} · ${c.label}", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium), color = Color.White)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Choice("This phone", c.mine, Modifier.weight(1f).testTag("clash_mine_${c.day}_${c.column}")) { onKeep(c, true) }
                            Choice("The sheet", c.theirs, Modifier.weight(1f).testTag("clash_theirs_${c.day}_${c.column}")) { onKeep(c, false) }
                        }
                    }
                }
                Text("Tap the one to keep.", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.6f))
            }
        },
        confirmButton = { TextButton(onClick = onLater, modifier = Modifier.testTag("clash_later")) { Text("Later") } }
    )
}

/** One side of a clash: whose it is and the value, tapped to keep it. */
@Composable
private fun Choice(who: String, value: String, modifier: Modifier, onTap: () -> Unit) {
    Column(modifier.border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(10.dp)).clickable { onTap() }.padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text(who, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
        Text(value.ifBlank { "—" }, style = MaterialTheme.typography.titleMedium.copy(fontFamily = com.example.ui.theme.NumberFont), color = Color.White,
            maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}
