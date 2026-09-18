package com.example.flock.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.flock.data.DailyDataEntity
import com.example.flock.data.FarmEntity
import com.example.flock.ui.components.ClimateEnvelope
import com.example.flock.ui.components.FanVisualizer
import com.example.flock.ui.components.HouseFloorPlan

@Composable
fun VentClimateScreen(
    entry: DailyDataEntity?,
    farm: FarmEntity,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("vent_climate_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. House Blueprint Floor Plan with Space & Density
        HouseFloorPlan(entry = entry, farm = farm)

        // 2. Animated Fans & Staging
        FanVisualizer(entry = entry, farm = farm)

        // 3. Climate Envelope (Temp, Wind-Chill, RH, CO2, NH3, Pressure)
        ClimateEnvelope(entry = entry)

        Spacer(modifier = Modifier.height(24.dp))
    }
}
