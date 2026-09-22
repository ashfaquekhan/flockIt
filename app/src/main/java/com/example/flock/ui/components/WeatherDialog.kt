package com.example.flock.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.flock.network.ForecastDay
import com.example.flock.network.ForecastResult
import com.example.ui.theme.ValueIdeal
import com.example.ui.theme.ValuePredicted
import com.example.ui.theme.ValuePresent
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun WeatherForecastDialog(forecast: ForecastResult?, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.85f).padding(vertical = 24.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("14-day weather", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                        Text(
                            text = forecast?.let {
                                "${it.locationName.ifBlank { "Farm location" }} · ${String.format("%.3f", it.lat)}, ${String.format("%.3f", it.lon)}"
                            } ?: "Loading…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
                }

                Spacer(Modifier.height(10.dp))

                when {
                    forecast == null -> Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    forecast.days.isEmpty() -> Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "Weather unavailable — check the farm's location in settings and your internet connection.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        // Header row
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text("Day", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.6f))
                            Text("Temp °C", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.3f))
                            Text("Rain", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                            Text("Wind", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                            Text("Conf.", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                        }
                        Divider()
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(forecast.days) { d -> ForecastRow(d) }
                        }
                        Text(
                            "Confidence is a modelled estimate (it falls for days further out) — not a published accuracy figure.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ForecastRow(d: ForecastDay) {
    val label = try {
        LocalDate.parse(d.date, DateTimeFormatter.ISO_LOCAL_DATE).format(DateTimeFormatter.ofPattern("EEE dd MMM"))
    } catch (e: Exception) { d.date }
    val confColor = when {
        d.confidencePct >= 80 -> ValuePresent
        d.confidencePct >= 60 -> ValueIdeal
        else -> ValuePredicted
    }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1.6f))
        Text(
            text = "${d.tMinC?.let { String.format("%.0f", it) } ?: "–"}–${d.tMaxC?.let { String.format("%.0f", it) } ?: "–"}",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.weight(1.3f)
        )
        Text("${d.precipProbPct ?: 0}%", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.weight(1f))
        Text(d.windKmh?.let { "${it.toInt()}" } ?: "–", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.weight(1f))
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).padding(end = 0.dp)) {}
            Text("${d.confidencePct}%", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = confColor, fontSize = 12.sp))
        }
    }
}
