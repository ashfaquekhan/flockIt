package com.example.flock.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** One explanation: what the number is, how it is worked out, what it needs, what moves it, how to keep it right. */
data class InfoTopic(
    val title: String,
    val what: String,
    val how: String,
    val inputs: String,
    val changes: String,
    val check: String
)

/** Every ⓘ on the Output page reads from here (one place to keep the explanations exact). */
object InfoTopics {
    val all: Map<String, InfoTopic> = mapOf(
        "window" to InfoTopic("Farm window",
            "A slice of the real house: 10 ft along it and the full, half or a quarter of the width across, with the real feeder and drinker lines.",
            "Birds in view = birds per ft² × floor in view that is inside the barricade. Birds per ft² = live birds ÷ floor in use (barricade length × usable width). Pans open or shut follow today's pan pattern; feed runs down each line from the hopper after a feeding.",
            "Farm settings (house size, lines, pans, spacing, gap), today's live birds and weight, the barricade (occupied floor), the feeding log.",
            "Moving the window on the map, the day, the barricade, feedings logged.",
            "Temperatures, gases and breathing shown in the box are the targets for this age (no house sensors yet), not readings. Min vent / bird = the day's minimum-ventilation cfm per bird; house = × live birds. Breaths: resting rate at the ideal temperature (30–50 a minute in week 1, 20–40 after); over 60 a minute the birds are panting — too hot or short of air."),
        "clock" to InfoTopic("Day clock",
            "When it is dark, when to load the feeders, refill the tank and walk the house. The outer ring is the day in sections (sleep, light, hot hours); inside it each job has its own lane: walks, feed, water. The bar on the ring is the time now.",
            "Dark = one unbroken block ending at 05:00, as long as 24 − light hours. First feeding at lights on; the last ends 2 h before the dark (birds fill the crop for the night). Extra feedings go to the coolest light hours, never in the hottest 4 hours or the 2 h before them. Tank refills: before the birds wake, before the heat, then evenly. Walks: after lights on, an hour after each feeding, at the peak heat, before the dark.",
            "Light hours (Entry), times a day (feeding plan), refills (water multiplier), outside temperature through the day (forecast if there is one, else the season's typical day).",
            "Light hours, the feeding count, the refill multiplier, the season or forecast.",
            "Birds should never go into the dark hungry or thirsty: check the pans and the tank at the last walk before lights off."),
        "kpis" to InfoTopic("Today",
            "The numbers that decide the batch result, against the company standard (commercial) and the breed objective (ideal).",
            "Each line: the flock's value, ▲ above or ▼ below the standard. Green = better than it, orange = a little worse (up to 9 %), red = well worse; = means within ±3 %.",
            "Entry: weights, deaths, feed used; Farm settings; the flock's start.",
            "Every saved entry recalculates the whole batch.",
            "Act on red first; orange is a watch."),
        "flock" to InfoTopic("Flock",
            "How many birds are alive and how many were lost, against the standards.",
            "Entry birds = placed − reception deaths. Live = entry − deaths − culls − lifted. Deaths today % = today's deaths ÷ (live + today's deaths) × 100. Mortality till date % = all deaths ÷ entry × 100. Livability = live ÷ entry × 100. Commercial = the company's daily and cumulative standard; ideal = the breed's target curve.",
            "Placed and reception deaths (flock), deaths / culls / lifted each day (Entry).",
            "Every death, cull or lift entered.",
            "Daily deaths above 0.15 % (week 1) / 0.10 % later need a look at brooding temperature, water and disease."),
        "growth" to InfoTopic("Growth",
            "Weight and feed efficiency against the standards.",
            "Weight = Σ sample weights ÷ Σ birds (buckets and birds weighed one by one). Days without a sample are projected along the breed curve from the last sample. Gain = today − yesterday. FCR = all feed used (kg) ÷ (live birds × weight kg). cFCR = (2 − weight kg) × 0.25 + FCR. EPEF = livability % × weight kg ÷ (age × FCR) × 100.",
            "Weight samples, feed used by type (each type's bag weight), deaths.",
            "Sample days, feed entries, deaths.",
            "Weigh at least twice a week at the same time of day; a projected weight (yellow) is a guess until the next sample."),
        "uniformity" to InfoTopic("Uniformity",
            "How evenly the birds are growing: a flock of equal birds eats, drinks and sells better.",
            "CV = standard deviation ÷ average × 100, from birds weighed one by one (10 or more; 50–100 is better). Uniformity = birds within ±10 % of their average. Spread between locations = standard deviation of the 5 bucket averages ÷ their average: weighing a bucket together averages away the bird-to-bird spread (by about √birds per bucket), so it is much smaller than the CV and only shows if one part of the house lags. The bars: the share of birds in each 5 % step of weight around the average (−25 % to +25 %); the middle four (±10 %) are the even birds, left of them the light ones, right the heavy ones. With 10 or more birds weighed one by one the bars are those birds; without, they are the spread a flock at that CV (8 % if none) would have, drawn paler and marked as expected.",
            "Birds weighed one by one (Entry, Section 1); the 5 location buckets.",
            "Brooding temperature, feeder and drinker space, disease, the grading of chicks at placement.",
            "CV under 8 % good, 8–10 % fair, over 10 % uneven: check feeder and drinker space and heat spread; uniformity should stay above 80 %."),
        "comfort" to InfoTopic("Comfort",
            "Is the bird comfortable: body heat, space and light.",
            "Vent (cloacal) temperature target 39.4–40.5 °C in the first days, 40.6–41.7 °C later. Feet temperature (thermal) shows if the floor is too cold. Density = live weight ÷ floor in use against the farm's cap. Floor = ft² per bird against the minimum for this age. Light hours and lux by age.",
            "Weight, live birds, occupied floor (barricade), light hours.",
            "Bird size, barricade moves, light programme.",
            "No sensors yet: these are targets. Check vent temperature on 10 chicks in the first week."),
        "air" to InfoTopic("House air",
            "The air the birds live in.",
            "Temperature ideal follows the age (and weight) curve; min / max = ideal ∓ the band set for the farm. Humidity 50–70 %. Air speed max by age (no draught on chicks). Static pressure 20–25 Pa with minimum ventilation. CO₂ under 3,000 ppm, NH₃ under 10 ppm.",
            "Age, weight, farm thresholds (Settings), measured readings when entered in the sheet.",
            "Age and the farm's settings.",
            "Smell ammonia at bird height: if you can, it is over 10 ppm — raise minimum ventilation."),
        "litter" to InfoTopic("Litter",
            "Floor conditions under the birds.",
            "Temperature target by age; moisture ideal 25 % (20–30 %).",
            "Age.",
            "Ventilation, drinker leaks, density.",
            "Squeeze test: litter that sticks in a ball is too wet."),
        "minvent" to InfoTopic("Minimum ventilation",
            "The least air the house needs for oxygen and to remove moisture, CO₂ and ammonia, even when cold.",
            "cfm per bird = Ross minimum by weight × 1.3 air-quality margin × the farm's calibration. House cfm = cfm per bird × live birds. The fan timer runs one fan for on / off seconds to give that air (on time at least 50 s).",
            "Weight, live birds, fan rated cfm and derate (Settings), timer settings.",
            "Weight and bird numbers every day.",
            "Never switch minimum ventilation off for warmth — use the heater."),
        "fanfinder" to InfoTopic("Fan finder",
            "How many fans the controller should be running for an outside temperature and humidity.",
            "A house heat and moisture balance for the dialled outside air: bird heat, sun, walls, pads and fans, until the birds feel the comfort temperature.",
            "The dials, weight, live birds, fans, pads, house size.",
            "Turn the dials.",
            "Compare with what the controller is really running at that weather."),
        "grid" to InfoTopic("Fans by outside air",
            "The fan finder for 9 outside conditions at once.",
            "Same balance as the fan finder, for the day's hottest temperature and 4 / 8 °C cooler, each at dry, medium and humid air.",
            "As for the fan finder.",
            "Season or forecast.",
            "Tap a cell to open it in the fan finder."),
        "controller" to InfoTopic("Controller",
            "Settings for the IB controller for this day.",
            "SET = comfort − the cooling the minimum level gives; heat on below SET; limits from the age's air-speed cap.",
            "Age, weight, fans.",
            "Every day.",
            "Update the controller's day curve when these move."),
        "plan" to InfoTopic("Feeding plan",
            "How many bags to load, how many times, and which pans to open so the feed reaches the end of every line.",
            "Feed needed = company feed per bird for this weight × live birds ÷ bag kg. Full bags = rounded up (never more). Each feeding = full bags ÷ times a day; per line = ÷ lines. Pans one bag fills = feed pans ÷ bags to fill a line. Pattern = the most open on/off series whose open pans that pour fills to the last one. Max birds per pan = 80 × ∛(2000 ÷ weight g), at most 160 (45 above 3.5 kg): the breed's 45–80 for grown birds, more for small ones. Travel to pan = √((line gap ÷ 2)² + (gap between open pans ÷ 2)²): the furthest any bird stands from an open pan; kept within 2 m.",
            "Farm settings (lines, pans, bags per line, spacing, gap), weight, live birds, the barricade.",
            "Weight and birds each day, the feeding count you pick.",
            "If birds leave feed in the pans at the next feeding, the plan is too much; if pans are empty for hours, too little."),
        "correction" to InfoTopic("Feed correction",
            "Whether to load a little more or less than the standard to bring weight or FCR back on track.",
            "Weight more than 3 % under commercial: up to +5 % (half the gap). FCR more than 3 % over commercial with weight on target: up to −3 %. None in the first week.",
            "Weight and FCR against the commercial standard.",
            "Each sample day.",
            "Birds eat to appetite: more feed only helps if they clear the pans before the next feeding. Before cutting feed, check for wastage (pan height, spillage). Underweight birds usually need heat, water or health checked first."),
        "eaten" to InfoTopic("Eaten",
            "Feed actually used, per bird and in bags by type.",
            "Yesterday per bird = bags used × that type's bag kg ÷ live birds. Till date per bird = all feed used ÷ live birds. Bags till date = the sum of each type's bags used.",
            "Feed used rows (one per type) in Entry.",
            "Each day's feed entry.",
            "Per bird should track the commercial line; well above it with poor FCR means wastage."),
        "water" to InfoTopic("Water",
            "How much the birds drink and how to keep the tank fresh.",
            "Water = feed × 1.8 (water : feed) × 6 % more per °C above 20 °C. Tank fills needed = water ÷ tank litres, rounded up; refills = that × your multiplier (fresher, cooler water). Nipple flow, height and line pressure follow age (line pressure is in inches of water column on the regulator's sight tube, from the age curve).",
            "Feed, temperature, tank size and drinker lines (Settings), multiplier.",
            "Age, heat, the multiplier.",
            "Water 18–21 °C, pH 6.0–6.8; a sudden drop in drinking is often the first sign of trouble."),
        "stock" to InfoTopic("Feed store",
            "Feed in the godown by type: what came in, what was used, what is left.",
            "Received = all deliveries entered for that type. Used = all bags entered as used for that type. In store = received − used. Godown free = what it holds − in store.",
            "Feed received (Entry, Section 3) and feed used by type (Entry).",
            "Deliveries and daily use.",
            "Count the bags in the godown now and then; a negative figure means a delivery was not entered.")
    )
}

/** A small ⓘ that opens the explanation for [key]. */
@Composable
fun InfoButton(key: String, modifier: Modifier = Modifier) {
    val topic = InfoTopics.all[key] ?: return
    var open by remember { mutableStateOf(false) }
    Box(modifier.size(24.dp).border(1.dp, Color.White.copy(alpha = 0.55f), CircleShape).clickable { open = true }.testTag("info_$key"),
        contentAlignment = Alignment.Center) {
        Text("i", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color.White.copy(alpha = 0.85f))
    }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(topic.title) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("What it is" to topic.what, "How it is worked out" to topic.how, "What it needs" to topic.inputs,
                    "What changes it" to topic.changes, "Keeping it right" to topic.check).forEach { (h, b) ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(h, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Color.White.copy(alpha = 0.7f))
                        Text(b, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } }
    )
}
