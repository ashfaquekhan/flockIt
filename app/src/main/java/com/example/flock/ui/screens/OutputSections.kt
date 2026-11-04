package com.example.flock.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.flock.domain.SpacePlan
import com.example.flock.ui.Fmt
import com.example.ui.theme.StatusCrit
import com.example.ui.theme.StatusWarn
import kotlin.math.max

private val P = ValueKind.PRESENT
private val PR = ValueKind.PREDICTED
private val I = ValueKind.IDEAL
private val C = ValueKind.COMMERCIAL
private val MX = ValueKind.MAX
private val NE = ValueKind.NEUTRAL

/** The last 3 and 7 days: eaten and gained against the company, how even, and what to do. */
@Composable
fun SuggestCard(d: OutputData) {
    val s = d.suggest
    Part("Last 3 and 7 days", "lastdays") {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1.3f))
            Text("3 days", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium), maxLines = 1)
            Text("7 days", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium), maxLines = 1)
        }
        @Composable fun box(x: V, m: Modifier) = Text(x.text, m.border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 5.dp),
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = com.example.ui.theme.NumberFont, fontWeight = FontWeight.Medium),
            color = kindColor(if (x.text == "—") NE else x.kind), maxLines = 1, softWrap = false,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        @Composable fun row(label: String, a: V, b: V) = Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1.3f), style = MaterialTheme.typography.bodySmall, maxLines = 2)
            box(a, Modifier.weight(1f).padding(end = 4.dp)); box(b, Modifier.weight(1f))
        }
        fun pct(x: Double?) = if (x == null) vt("—", NE) else vt(Fmt.signed(x, 1) + " %", P)
        row("Eaten vs company", pct(s.w3.feedPct), pct(s.w7.feedPct))
        row("Gain, g a day", v(s.w3.gainG, 1, P), v(s.w7.gainG, 1, P))
        row("Company gain", v(s.w3.comGainG, 1, C), v(s.w7.comGainG, 1, C))
        row("CV, first → last", vt(cvText(s.w3), P), vt(cvText(s.w7), P))
        s.lines.forEach { l ->
            Text(l.text, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (l.level > 0) FontWeight.Medium else FontWeight.Normal),
                color = when (l.level) { 2 -> StatusCrit; 1 -> StatusWarn; else -> Color.White.copy(alpha = 0.9f) })
        }
    }
}

private fun cvText(w: com.example.flock.domain.Suggest.Window): String =
    if (w.cvFrom == null) "—" else if (w.cvTo == null) Fmt.n(w.cvFrom, 1) else Fmt.n(w.cvFrom, 1) + "→" + Fmt.n(w.cvTo, 1)
