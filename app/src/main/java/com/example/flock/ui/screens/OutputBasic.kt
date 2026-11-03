package com.example.flock.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flock.domain.BirdEnvironment
import com.example.flock.ui.Fmt
import com.example.ui.theme.ValuePredicted
import com.example.ui.theme.ValuePresent

private val P = ValueKind.PRESENT
private val PR = ValueKind.PREDICTED
private val I = ValueKind.IDEAL
private val MN = ValueKind.MIN
private val MX = ValueKind.MAX

/** How an hour of the house reads for the birds, in the colour of its kind. */
fun stateKind(s: BirdEnvironment.State): ValueKind = when (s) {
    BirdEnvironment.State.COMFORT -> ValueKind.PREDICTED
    BirdEnvironment.State.WARM, BirdEnvironment.State.COOL -> ValueKind.PREDICTED
    else -> ValueKind.MAX
}

/** What the weather does to the day's plan, in four multipliers (shown when the switch is on and anything moves). */
@Composable
fun WeatherEffects(d: OutputData) {
    val day = d.env ?: return
    if (!d.envOn) return
    fun pct(f: Double) = Fmt.signed((f - 1) * 100, 1) + " %"
    fun kind(f: Double, worseUp: Boolean) = PR
    OutputCard(title = "What the weather does today", info = "weather") {
        ValueRow(listOf(vt(pct(d.envFeedF), kind(d.envFeedF, false), "Feed"), vt(pct(d.envWaterF), PR, "Water"),
            vt(pct(d.envGainF), kind(d.envGainF, false), "Growth"), vt("× " + Fmt.n(d.envMortF, 2), kind(d.envMortF, true), "Deaths risk")), "effect")
        val ok = if (d.outsideSource == BirdEnvironment.Source.WEATHER) ValueKind.NEUTRAL else PR
        ValueRow(listOf(v(day.outMinC, 1, ok, "Low °C"), v(day.outMeanC, 1, ok, "Mean °C"), v(day.outMaxC, 1, ok, "High °C"), v(day.outMeanRh, 0, ok, "RH %")), "outside")
        ValueRow(listOf(vi(day.hoursWarm, PR, "Warm h"), vi(day.hoursHot, PR, "Hot h"), vi(day.hoursCold, PR, "Cold h")), "hours")
    }
}
