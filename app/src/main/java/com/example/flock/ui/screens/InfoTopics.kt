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
        "glance" to InfoTopic("Birds",
            "The farm window shows the birds at the time on the rail down the right edge (touch it to move the time; Now, bottom right, goes back to the clock). Under it the flock's numbers in the farm's order — live birds, weight, FCR, cFCR, mortality, CV with how the weights spread, stocking density — then the radar. Each number: green = entered, amber = projected, violet com = the company, blue ideal = the ideal (Ross); the bar under it has the company's tick and ±5 % band, the ideal's tick and the flock's dot. Arrows only show up or down against the company; green good, red bad, by what the number is. FCR = feed till date ÷ the kilos the flock has made (the birds in the house × their weight + the kilos lifted). cFCR = FCR + (2 − the average weight of every bird produced, kg) × 0.25 — the company's formula, written out under it.",
            "Radar: each score is the flock's figure as a share of the company's for the day, 100 = the company (the dashed ring), further out is better; the number in the middle is the six averaged, green at or above the company, red below.",
            "Every entry and its time, the feed plan and the flock's appetite, the lights, the weather switch.",
            "The time on the rail, every entry.",
            "A point inside the dashed ring is behind the company: start there. Evenness is estimated until 10 or more birds are weighed one by one."),
        "walk" to InfoTopic("Walk to feed and water",
            "How far the furthest bird walks to an open pan and to a nipple, against the ideal walk and the most for the age: chicks 1.0 m ideal and 1.5 m at most (days 0–3), 1.5 m and 2.5 m (days 4–10), then 2.0 m and 3.0 m.",
            "The furthest bird stands halfway between two lines and halfway between two open points on them: walk = √((line gap ÷ 2)² + (point gap ÷ 2)²). Shutting pans (1 on · 2 off) makes the gap along the line 3 pans. Trays and chick drinkers count while they are down: one every (floor ÷ count) m², the furthest bird at the middle of a square of them (side ÷ √2). The drawing is to scale; round the nearest open point: green = the furthest bird's walk now, blue = the ideal, red = the most. ✕ is the furthest bird.",
            "Feeder and drinker lines, pans a line and their spacing, nipples (25 cm apart), the barricades, the pan series picked in the feed card, trays and chick drinkers.",
            "Feedings picked, pans shut, the barricades, birds' age.",
            "Aviagen: chicks no more than 1 m from water in the first 24 hours; the management guides: no bird more than 3 m from water. When the green circle is outside the red one, open more pans or move the barricades."),
        "today" to InfoTopic("Today: entered and projected",
            "Two columns that never mix. Entered: what was typed in for this day, exactly as entered — it does not move. Projected: what the app works out. For today it is the figure right now and moves with the clock; for an earlier day it is what the app had projected for that day before its entry (so the two can be compared); for a later day it is the forecast.",
            "Weight now = the last weighing grown along the breed curve for the hours since it was saved. Eaten today = the bags the flock is likely to eat today × the share of the lit hours gone. Water today = the day's water the same way. Deaths today = the flock's own recent daily rate × the share of the day gone, until today's deaths are entered. Live birds = yesterday's − deaths. FCR = (feed entered + feed eaten since) ÷ (live birds × weight now).",
            "The time the weights were saved (recorded from this version on; earlier weighings count from 08:00 of their day), the feed plan and the flock's appetite, the light hours, the weather switch.",
            "The clock, every entry, the weather switch (it slows growth and eating in heat).",
            "Projections depend on when you enter: a weighing saved in the evening starts the weight from the evening. Weigh at about the same time every day and the projected line stays smooth."),
        "weather" to InfoTopic("Weather at the farm",
            "On: the temperature and humidity at the farm's location (from the weather service, hour by hour) are turned into the air the house would have, and that air acts on the birds — in the farm window, in the feed and water for the day, in growth and in the risk of losing birds. Off: the house is taken at its ideal, as before. There are no house sensors yet; when there are, they take the weather's place without anything else changing.",
            "Outside → house: the fan plan is run for the outside air (minimum ventilation, more fans, pads, heaters) and gives the house temperature, humidity and air speed. House → what the birds feel: temperature, corrected for humidity and the cooling of moving air, minus the comfort temperature for their size = the heat load. Heat load → birds: feed about −1.5 % per °C above comfort, steepening to −3.5 % per °C in strong heat (Baziz 1996; Teyssier 2022); water about +6 % per °C (NRC 1994); growth falls more than feed because upkeep is paid first; body temperature rises about 1.0 / 2.5 / 4.0 °C at the alert / danger / emergency steps (Tao and Xin 2003); breathing climbs to panting above 60 a minute.",
            "The farm's location (farm settings), the fans, pads and heaters, the flock's weight and number, the day's light hours.",
            "The weather, the hour, the flock's weight (bigger birds stand less heat), more or fewer fans.",
            "It is a model, not a measurement: the house may run warmer or cooler than worked out (curtains, sun on the roof, fans not running). The numbers are from published trials, not fitted to this house. Use it to see the direction and the hours to watch; trust the birds and a thermometer over it."),
        "needs" to InfoTopic("Hungry, thirsty, panting",
            "Three likelihoods, not readings — there are no sensors in the house. Hungry: the share of birds likely to have an empty crop. Thirsty: the share likely to want water. Panting: the share likely to be panting. \"In 3 h\" is the same, three hours on, if nothing more is poured.",
            "Hungry: the feedings logged under the farm window (bags and time) are eaten down at the pace the flock eats — the day's likely intake (company ration × the flock's own appetite) spread over the day the way birds eat: most in the hours after the lights come on and before they go off, least around midday, less in hot hours. The pace cannot be known exactly, so it is also run slower and faster (±18 %) and the cases are weighed. Once the pans are empty the first birds are hungry after about an hour, all of them after four. Thirsty: about 5 % at any lit moment, more with heat (half the birds at 7 °C of heat load) and after the dark. Panting: half the birds at 6 °C of heat load, few under 3 °C, nearly all above 9 °C. Heat load = what the birds feel − their comfort temperature, from the weather at the farm.",
            "Feedings logged under the farm window (while none is logged today, the plan's feeding times and amounts are assumed), the feed plan and the flock's appetite, the light hours, the weather switch.",
            "Each feeding logged, the clock, the weather.",
            "This replaces the feeder's \"empty since\" time, which claimed a minute it could not know. Example, day 20: 13 bags poured at 08:30 — an even spread over the lit hours said empty by 14:26; with the midday lull, the flock's appetite and the warm afternoon it comes to about 16:10 (between 15:00 and 17:50). Walk the house: if the pans run empty earlier or later than this suggests, say so and the pace can be tuned. Thirst assumes the tank has not run dry."),
        "window" to InfoTopic("Farm window",
            "A piece of the house, 10 ft long, with its real feeder and drinker lines and the birds at the flock's real density. The birds behave as birds of this size do — and, with the weather switch on, as they would in the air the house has now.",
            "Time: a broiler spends most of its day lying. About 12 % of the time on the move under 0.5 kg, falling to 5 % by 0.9 kg and 4 % above 2 kg; walking about 0.13 m/s as a chick and 0.10 m/s from 0.8 kg (Tickle and others 2018); about 15 % at the feeder and 7 % at the drinker (Ross 308 time budgets), busiest after the lights come on and before they go off. Heat: less walking and eating, more drinking, lying apart, wings held out, panting. Cold: chicks crowd together. Sizes: each bird = the average weight × (1 + CV × its own offset), so a larger CV shows as a more uneven crowd. Birds on the floor = live birds ÷ the floor in use (it only changes with deaths and the barricade); weight on the floor = that × the average weight (it grows every day).",
            "Weight, live birds, the day's light hours, the farm's lines and pans; the weather at the farm when the switch is on; a feeding logged under the window.",
            "The day, a weighing, the weather switch, the time of day, a feeding.",
            "Body temperature and breaths \"now\" are worked out for the air the house is estimated to have — not measured. Check a few birds at the vent with a thermometer on a hot afternoon and count breaths: panting starts above 60 a minute."),
        "clock" to InfoTopic("Day clock",
            "When it is dark, when to load the feeders, refill the tank and walk the house. The outer ring is the day in sections (sleep, light, hot hours), every hour numbered; inside it each job has its own lane: walks, feed, water. The bar on the ring is the time now. Under the dial: how often and how much, what is next, and the whole day in order.",
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
            "Colours: green once today's deaths are entered, yellow before (yesterday's count carried on). Commercial for today = the company's row for day + 1 (the company's table starts at day 1, the flock at day 0). Entry birds = placed − reception deaths. Live = entry − deaths − culls − lifted. Deaths today % = today's deaths ÷ (live + today's deaths) × 100. Mortality till date % = all deaths ÷ entry × 100. Livability = live ÷ entry × 100. Commercial = the company's daily and cumulative standard; ideal = the breed's target curve.",
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
            "How even the flock is: CV (the spread of bird weights as a % of the average) and the share of birds within ±10 % of it.",
            "True CV = standard deviation ÷ average × 100 of birds weighed one by one (10 or more). When birds are weighed in groups, the CV is estimated: the average of ten birds varies √10 less than single birds, so CV ≈ spread between the group averages × √(birds per group), pooled over the last 3 weighings. The farm window draws the birds with this CV.",
            "Birds weighed one by one (Entry), or three or more group weighings a day.",
            "Every weighing.",
            "The estimate is rough and reads high when one end of the house is lighter than the other (that is a location effect, not unevenness everywhere). For a figure to act on, weigh 30 or more birds one by one."),
        "comfort" to InfoTopic("Comfort",
            "Body temperature, feet, density, floor space and light.",
            "Body temperature is the bird's core temperature, measured with a thermometer at the vent: 39.4–40.5 °C in the first two days, about 41–42 °C once grown. With the weather switch on, \"projected\" is the body temperature worked out for the house's air now (it rises once the birds feel more than about 4 °C above comfort). Density = live birds × weight ÷ floor. Light hours follow the lighting programme.",
            "Weight, live birds, floor in use, the weather switch.",
            "A weighing, deaths, the barricade, the weather.",
            "Take a few birds' vent temperature in the first days: under 39.4 °C they are cold, over 40.5 °C too warm."),
        "air" to InfoTopic("Air and ventilation",
            "The whole house in four views: Air (heat on the floor, the air moving through from the pads to the fans), Fans (those running lit, on their timer), Pads (wet or dry, side by side or C-type as set in Farm settings) and Litter (moisture on the floor). No sensors yet: every value is projected from the weather (switch beside the title) and the flock; each tile keeps a place for a sensor's reading (sensor —), and the posts on the house show where sensors would stand. Turn the dials under Fans to see the fans for another outside temperature.",
            "Temperature ideal follows the age (and weight) curve; min / max = ideal ∓ the band set for the farm. Humidity 50–70 %. Air speed max by age (no draught on chicks). Static pressure 20–25 Pa with minimum ventilation. CO₂ under 3,000 ppm, NH₃ under 10 ppm.",
            "Age, weight, farm thresholds (Settings), measured readings when entered in the sheet.",
            "Age and the farm's settings.",
            "Smell ammonia at bird height: if you can, it is over 10 ppm — raise minimum ventilation."),
        "litter" to InfoTopic("Litter",
            "The deep litter drawn from the side, as it is worked out to be now (there are no sensors) against what it should be: its depth, how wet it is (and under the drinker lines), its warmth, the ammonia coming off it and how much of the floor has caked.",
            "Depth = the bedding (7.5 cm unless known) + the droppings: a quarter of the feed eaten comes out as dry matter, packing at about 400 kg/m³ on the floor the birds have; old litter settles 15 %. Moisture = 12 % fresh, rising towards 30 % over the flock, more on a humid day and a crowded floor; under the drinkers about 7 points more. Warmth = the air at the floor + the litter working (more when old, wet and crowded). Ammonia comes off litter wetter than 22 %, more when warm and old, less with air moving over it. Caked = where it is wetter than 30 %.",
            "The feed entered, the floor in use and its density, the age, the house air at the hour (from the weather, else the ideal) and the air speed.",
            "Age, feed, crowding, humid weather, ventilation, leaking drinkers.",
            "Ideal (Ross): 5–10 cm of bedding, moisture 20–25 % (30 % at most), ammonia under 10 ppm (25 the limit). Squeeze test: litter that stays in a ball is too wet; if you can smell ammonia at bird height it is over 10 ppm — more minimum air, fix the drinkers, top up or turn the litter."),
        "minvent" to InfoTopic("Minimum ventilation",
            "The least air the house needs for oxygen and to remove moisture, CO₂ and ammonia, even when cold.",
            "cfm per bird = Ross minimum by weight × 1.3 air-quality margin × the farm's calibration. House cfm = cfm per bird × live birds. The fan timer runs one fan for on / off seconds to give that air (on time at least 50 s).",
            "Weight, live birds, fan rated cfm and derate (Settings), timer settings.",
            "Weight and bird numbers every day.",
            "Never switch minimum ventilation off for warmth — use the heater."),
        "fanfinder" to InfoTopic("Fans for the air outside",
            "The fans the house needs for an outside temperature and humidity, and the house they give.",
            "The dials stand at the weather at the farm now (hour by hour from the weather service; the season's typical day when there is no forecast). Turn one to try another value: it holds for 30 seconds, then the dials go back to the weather. For the air on the dials the controller's levels are run until the house settles: fans (average over the timer), house temperature and humidity, and what the birds feel.",
            "The farm's fans, pads and heaters, the flock's weight and number, the farm's location.",
            "The weather, the dials, the flock's weight.",
            "Compare with the controller on a hot afternoon: if the house runs hotter than shown, check fan belts, shutters and the pads."),
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
        "plan" to InfoTopic("Feed and floor",
            "The bags to pour today and how, for the floor between the barricades. Drag the two barricades along the house (5 ft steps): the pans, the bags and the feedings follow at once; Set keeps them from this day on (until a later day changes them). Pick 2, 3 or 4 feedings (best marked). The drawing is the whole house straight from the front and above: amber pans open, white shut, crossed ones outside the barricades; a dashed barricade is not set yet (the brooding plan, or one being moved). Pinch to zoom, slide sideways, double tap fits it again. Gain today is the curve's: the company's day scaled by how the flock stands against it.",
            "Bags: the company's ration for birds of the flock's weight, corrected by the flock's own appetite once it is known, in whole bags. Pans: on the floor between the barricades every pan is on, unless there are fewer than 25 birds a pan — then a series is shut (1 on · 1 off …) to about 45 birds a pan, never past a 2 m walk. Feedings: as many as the age wants while each pour still fills to the last open pan, else fewer. Until a barricade is set the chicks are taken to have part of the house growing to all of it by day 11; once one is set, the floor is exactly what is set.",
            "The flock's weight (a weighing, or the last one carried on), live birds, the feed entered each day (from day 6: the first days are trays, paper and the first fill), the farm's lines, pans and bags per line.",
            "A weighing, deaths, every feed entry; the weather switch (heat lowers the ration).",
            "\"Entered\" shows the plan against the entries: any 3 days together should be within about ±10 %; a single day can be off by a quarter. Pour more only if the pans are cleared before the next feeding, less only if feed is left in them."),
        "correction" to InfoTopic("Feed correction",
            "Whether to load a little more or less than the standard to bring weight or FCR back on track.",
            "Weight more than 3 % under commercial: up to +5 % (half the gap). FCR more than 3 % over commercial with weight on target: up to −3 %. None in the first week.",
            "Weight and FCR against the commercial standard.",
            "Each sample day.",
            "Birds eat to appetite: more feed only helps if they clear the pans before the next feeding. Before cutting feed, check for wastage (pan height, spillage). Underweight birds usually need heat, water or health checked first."),
        "eaten" to InfoTopic("Eaten",
            "Feed actually used, per bird and in bags by type.",
            "Yesterday per bird = bags used × that type's bag kg ÷ live birds. Till date per bird = all feed used ÷ live birds. Bags till date = the sum of each type's bags used. Commercial for yesterday = the company's row for today's day number (the company's day 1 is the placement day's feed); till date = the company's cumulative feed for today's number.",
            "Feed used rows (one per type) in Entry.",
            "Each day's feed entry.",
            "Per bird should track the commercial line; well above it with poor FCR means wastage."),
        "water" to InfoTopic("Water",
            "How much the birds drink today and how to keep the water right: the day's litres and the refills of the tank (× sets how often it is refilled), per bird, per hour while the lights are on, the walk to a nipple (drawn to scale), birds per nipple, nipple height, pressure and flow for the age, the water's temperature and pH (a place for sensors), and how likely the birds are thirsty now.",
            "Water = feed × 1.8 (water : feed), more on hot days when the weather switch is on. Refills = the day's water ÷ the tank, rounded up, × the multiplier. Birds per nipple = live birds ÷ the nipples on all drinker lines. Nipple flow, height and pressure follow the age (Ross).",
            "Feed, temperature, tank size and drinker lines (Settings), multiplier. The drawing shows the line's regulator with its sight tube (the water stands as high as the line pressure, in inches of water) and a dial reading the same, the line at its height over the litter and a bird of today's size drinking.",
            "Age, heat, the multiplier.",
            "Water 18–21 °C, pH 6.0–6.8; a sudden drop in drinking is often the first sign of trouble."),
        "trends" to InfoTopic("Trends",
            "Only what rests on counts and weighings: the birds placed and started, those removed (deaths and culls), the average daily gain, the first week's deaths and the last seven days' deaths, each against the company and the ideal.",
            "Average daily gain = weight ÷ age. First week deaths = deaths in days 1–7 ÷ birds started. Last 7 days = deaths in the last 7 days ÷ birds started.",
            "Weight samples, deaths and culls from Entry.",
            "Every entry. A weight that is projected (amber) makes the gain projected too.",
            "Deaths in the first week well above the company: look at the chicks' start (brooding temperature, water, feed on paper)."),
        "forecast" to InfoTopic("Target and forecast",
            "The weight the flock is grown to, when it will get there and what it will weigh at the planned harvest day — from the flock's own weighings.",
            "Each weighing is the flock's share of the commercial weight for that day (weighed ÷ standard). The share is tracked with a Kalman filter: it drifts about 0.6 % a day and one weighing is taken to be within about 3 %. Forecast weight on day d = standard weight on d × share. Low and High are the range it falls in 8 times out of 10; the range widens the further ahead it looks. Target day = the first day the forecast reaches the target; Earliest and Latest use the High and Low lines. Chance of target = the probability the weight at the harvest day is at or above the target.",
            "The flock's weight samples; the target weight and harvest day set with the button on this card.",
            "Every new weighing; the target and harvest day.",
            "Weigh at least twice a week: with few weighings the range is wide. The flock can run past the harvest day — the day bar grows by a day at a time up to day 70."),
        "accuracy" to InfoTopic("Projection check",
            "How close the app's projections were: for each value, what was projected for a day from the days before it, what was then entered, and the miss.",
            "Weight: the weighing before, carried along the breed curve. Feed: the feed plan × the flock's appetite − the feed in the lines. FCR: (feed till the day before + projected feed) ÷ (projected birds × projected weight). Mortality: the flock's own recent daily rate. The same numbers are plotted as the Projected line in every chart and written to the sheet's Projections tab.",
            "Weights, feed and deaths entered on consecutive days.",
            "Each entry adds a check.",
            "Weight within about 4 % is as good as weighing 50 birds allows. A single day's feed entry is often 20 % off the projection because feed stays in the lines; over 3 days it should be within 10 %."),
        "suggest" to InfoTopic("Feeding suggestion",
            "Whether to load more or fewer bags than the plan, from what this flock has actually eaten.",
            "Each day the flock's appetite = feed eaten ÷ feed the plan expected. It is tracked with a Kalman filter (a level that drifts a little each day, read through noisy daily bag counts): after each entry the estimate moves towards the new reading by the gain K = P ÷ (P + r), where P is how unsure it still is and r the day-to-day scatter of this flock's own readings. Likely = today's plan × appetite. Low and High = the range the day's intake falls in 8 times out of 10. Chance = the probability that amount of whole bags is enough for the day. Safe range = from the bags that are enough at least 8 times out of 10 up to the 95 % point. Avg miss = how far off these forecasts have been so far.",
            "Feed used entered each morning (at least 3 days), the plan for those days.",
            "Every feed entry; the plan (weight, live birds).",
            "A suggestion is not an order: give more only if the pans are being cleared before the next feeding, and less only if feed is left in them. Above the safe range feed sits in the pans and goes stale."),
        "map" to InfoTopic("Map",
            "Everything the app knows about the flock on one map: the flock in the middle; round it the groups (birds, feed, lifting, air, water, the house, events); outside them every number, every part of the house and every kind of event. The lines are the links: what each number is worked out from (green raises it, red lowers it, white shapes it; thicker is stronger; dashed acts later, like heat on growth or the house temperature on the fans), how the parts of the house hang together (dashed white, with their own figures: 4 lines, 112 pans a line, 2.5 ft apart …) and which events move what (violet). Tap a point for its value, what it comes from, what it moves and the events that set it; two fingers or a double tap zoom.",
            "Under the map, the event log: every save is kept as events — a weighing, deaths, feed used or received, a lifting, a feeding, the barricades moved, the weather kept, farm settings — each with when it happened and when it was entered (a lifting entered next morning keeps its evening), who entered it and the values before and after. They go to the sheet's Events tab, so every phone has them.",
            "Every entry and setting; the links are the app's own sums and the published relations they come from.",
            "Saving anything adds events; the links change only with a new version of the app.",
            "The strengths of the links are set by hand (1 a little, 3 strongly) from how the sums work and the published trials; learning them from the farm's own flocks comes later. The numbers still come from the day rows; the log is kept beside them."),
        "lifting" to InfoTopic("Lifting",
            "Where the flock's meat is — in the house, lifted, dead and culled — each with its kilos and its share of all of it; the birds lifted and their average; livability, EPEF and what was produced. FCR and cFCR are in Birds (one of each). There is no target weight or harvest day: the app follows the flock, its curves and its own record.",
            "In the house = live birds × their weight. Lifted = the kilos weighed on the trucks. Dead and culled = each day's deaths and culls × the weight on that day. Produced = in the house + lifted; per bird = produced ÷ the birds that made it.",
            "Birds lifted and the truck's kg (Entry → Lifting, with its own Save and its own time: the morning weighing keeps its time; the birds left and their average count from the lifting's time on); the weighings, deaths, culls and feed.",
            "Every lifting, weighing and feed entry.",
            "Why the company's rule: a heavier bird needs more feed for each kilo (it spends more on keeping its body going), so a flock lifted at 3 kg shows a higher FCR than the same flock at 2 kg. Bringing every flock to 2 kg — 0.25 per kg — lets them be compared. Both ways are shown: the company's (the one it pays on) and the breed curve's (the Ross table's own slope, about 0.2–0.3 per kg). Enter the lifting time when the trucks leave: it decides whether the morning's weighing is grown on before the lifted kg are taken out."),
        "reception" to InfoTopic("Start and removals",
            "Chicks placed, the ones dead on arrival or in the first hours (reception deaths), and the birds the flock really started with. Reception deaths are not counted in the flock's mortality — they are the hatchery's and the transport's — but they are shown, and mortality is also given with them in.",
            "Started = placed − reception deaths. Mortality % = deaths till date ÷ started × 100. With reception % = (deaths + reception deaths) ÷ placed × 100. Reception % = reception deaths ÷ placed × 100.",
            "Birds placed and reception deaths (the flock's settings), the deaths entered each day.",
            "Each day's deaths; a change to the flock's settings.",
            "Count the reception deaths on the first day, before the first night."),
        "space" to InfoTopic("Floor, pans and feedings",
            "How tightly the birds stand on the floor they have, against what they need today; how far they walk to feed and water; and, once the birds are grown or lifted, the plan in three steps — the floor (whole house or barricade), the pans (which to shut), the feedings (how many and how big) — with the ways to set the floor side by side.",
            "Density = live birds × their average ÷ the floor up to the barricade, kg/m². Needs: the density birds stay comfortable at — 30 kg/m² on a hot day (32 °C or more outside), 34 on a warm one (27 °C or more), else the farm's cap (39). Has ÷ needs: under 0.95 tight, over 1.6 roomy. Walk: the furthest a bird can be from an open pan and a drinker line (Ross: no more than 2 m). The ways: the whole house; the barricade where the birds have 34 kg/m² at most (5 ft steps, never under 40 % of the house); the whole house with every other — or two of three — pans shut, the series nearest 45 birds a pan that keeps the walk under 2 m.",
            "Live birds and their average after lifting, the barricade (Entry, section 4, or the brooding plan), the feeder and drinker lines and pans (farm settings), the day's outside temperatures.",
            "Lifting, deaths, growth, the barricade, the weather.",
            "First principles, for a house half empty after lifting (say 7,000 birds left of 15,000) or few bags: 1) Floor — on a warm day keep the whole house, space sheds heat; on a cool day with room to spare pen the birds with the barricade (a smaller floor keeps warm, the birds stay near feed, water and heat). 2) Pans — a pan serves 45–80 grown birds; with under 25 the feed in it goes stale and it never empties, so shut pans in a series (1 on · 2 off …), never so many that a bird walks more than 2 m; shut every pan past a barricade too, or the auger fills them for no bird. 3) Feedings — a pour fills the open pans from the hopper outward, so each feeding must be at least one fill of the open pans (bags to fill = open pans ÷ the pans one bag fills); keep the feedings the age needs while each still reaches the last pan, else fewer. Fewer open pans need fewer bags to fill, so the same bags go round more often and fresher."),
        "lastdays" to InfoTopic("Last 3 and 7 days",
            "What the last 3 and 7 days say, read together: the feed eaten against the company's feed, the daily gain against the company's gain, and how even the flock is (CV).",
            "Eaten vs company = the feed eaten each day (bags entered the next morning × bag kg ÷ birds) ÷ the company's feed for those days − 1. Gain = (last weighing − the one before it, at least two days apart) ÷ days between, against the company's weights on the same days. CV: the first and last weighing in the window (birds weighed one by one, else estimated from the locations).",
            "Feed used, weighings, the company's table.",
            "Each entry.",
            "Eating less but growing to the standard is good conversion — keep the ration. Eating more but growing less points to waste, heat or sickness. A CV climbing by 1.5 points or more in a week means the small birds are losing out at the pans."),
        "book" to InfoTopic("Flock book",
            "The flock day by day, like a small sheet: live birds, deaths, mortality, lifting, bags used, feed per bird, weight against the company's, CV, FCR, cFCR and density. Entered numbers are green; projected ones (a day without its entry) yellow; the company's violet. Lifting days are marked L. Touch a number to light it with its day and column. A live count in red does not add up: more birds lifted than the count held (−), or birds in the count never lifted (+).",
            "As on the rest of the page: the FCR and cFCR count the birds lifted; bags and feed per bird are those used on the day (entered the next morning).",
            "Every entry of the flock.",
            "Each entry.",
            "Tap a day to open it. Full screen shows more of it at once."),
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
        Text("i", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal), color = Color.White.copy(alpha = 0.85f))
    }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(topic.title) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("What it is" to topic.what, "How it is worked out" to topic.how, "What it needs" to topic.inputs,
                    "What changes it" to topic.changes, "Keeping it right" to topic.check).forEach { (h, b) ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(h, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Normal), color = Color.White.copy(alpha = 0.7f))
                        Text(b, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } }
    )
}
