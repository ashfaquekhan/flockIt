package com.example.flock

import com.example.flock.data.FlockCalc
import com.example.flock.data.filledSamples
import com.example.flock.data.formatFeedings
import com.example.flock.data.parseFeedings
import com.example.flock.domain.Litter
import com.example.flock.domain.SpacePlan
import com.example.flock.domain.Suggest
import com.example.flock.ui.screens.toCount
import com.example.flock.ui.screens.toNum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * v47 against the farm's own flock to day 44: the lifting on days 34, 40, 42 and 43 counted in FCR and cFCR, the birds
 * left after each lifting, the floor (density, barricade or pans) and what the last days say.
 */
class LiftingSpaceTest {
    private val rows = KgfFlock.rows(lastDay = 45, upTo = 44)
    private val computed = FlockCalc.compute(KgfFlock.flock, KgfFlock.farm, KgfFlock.config, rows, KgfFlock.kgOf)
    private val mass = FlockCalc.mass(KgfFlock.flock, KgfFlock.farm, KgfFlock.config, rows, KgfFlock.kgOf).associateBy { it.day }

    @Test fun liftedBirdsCountInTheFcr() {
        // the sheet showed FCR 1.54 on day 39, then 1.78, 2.29 and 3.08 as birds were lifted — the lifted meat was left out
        val before = mass[39]!!.fcr!!
        listOf(34, 40, 42, 43).forEach { d ->
            val m = mass[d]!!; val prev = mass[d - 1]!!
            println("day $d: lifted ${m.liftedToday} at ${"%.3f".format(m.liftAvgKg)} kg · flock ${"%.0f".format(m.avgBeforeKg * 1000)} → left ${"%.0f".format(m.avgAfterKg * 1000)} g · " +
                "FCR ${"%.3f".format(m.fcr)} (with dead ${"%.3f".format(m.fcrAll)}) · cFCR company ${"%.3f".format(m.cFcrCompany)} breed ${"%.3f".format(m.cFcrBreed)}")
            assertTrue("FCR day $d ${m.fcr} vs ${prev.fcr}", abs(m.fcr!! - prev.fcr!!) < 0.08)      // no jump on a lifting day
        }
        val m43 = mass[43]!!
        assertEquals(1008 + 1901 + 2378 + 2589, m43.liftedBirds)
        assertEquals(2409.0 + 5357.7 + 7043.4 + 7835.7, m43.liftedKg, 0.01)
        assertTrue("FCR day 43 ${m43.fcr}", m43.fcr!! in before..(before + 0.15))
        assertEquals(m43.liveKg + m43.liftedKg, m43.producedKg, 1e-6)
        // the stored FCR is the same one
        assertEquals(m43.fcr!!, computed.first { it.dayNumber == 43 }.fcr!!, 1e-9)
        // the company's cFCR, worked out on the average of every bird produced
        assertEquals(m43.fcr!! + (2 - m43.cumBwKg) * 0.25, m43.cFcrCompany!!, 1e-9)
        assertTrue("cumulative body weight ${m43.cumBwKg}", m43.cumBwKg in 2.6..3.2)
        assertTrue(m43.fcrAll!! < m43.fcr!!)                          // counting the dead lowers feed per kilo grown
    }

    @Test fun theBirdsLeftAfterALiftingGetTheirOwnAverage() {
        val m = mass[34]!!
        // weighed 2.30 kg in the morning; 1,008 birds lifted in the evening at 2.39 kg
        assertEquals(2409.0 / 1008, m.liftAvgKg!!, 1e-9)
        assertTrue("after ${m.avgAfterKg}", m.avgAfterKg in 2.28..2.40)
        // the next day is carried on from the birds that stayed, not from the morning's weighing
        val next = computed.first { it.dayNumber == 35 }
        assertTrue(next.projected)
        val grownFromAfter = FlockCalc.grow(m.avgAfterKg * 1000, 1.0 - (20.0 - 8.0) / 24.0, KgfFlock.flock.breed)
        assertEquals(grownFromAfter, com.example.flock.engine.PhysiologicalEngine.bwFromDay(next.weightAge, KgfFlock.flock.breed), 30.0)
        // density is on the birds left
        val d43 = computed.first { it.dayNumber == 43 }
        assertTrue("density day 43 ${d43.densityKgM2}", d43.densityKgM2!! in 15.0..25.0)
    }

    @Test fun densityIsOnTheFloorTheBirdsHave() {
        // day 2: the chicks are penned on part of the house, so the kg per m² is over that part, not the whole house
        val d2 = computed.first { it.dayNumber == 2 }
        val wholeHouse = d2.liveBirds * (d2.avgWeight!! / 1000) / (299.0 * 39 * 0.092903)
        assertTrue(d2.occupiedFt2 < 299.0 * 39 * 0.6)
        assertTrue("${d2.densityKgM2} vs $wholeHouse", d2.densityKgM2!! > wholeHouse * 1.6)
        // a barricade entered is where the birds are, and it holds until another is entered
        val penned = rows.map { if (it.dayNumber == 40) it.copy(broodingLength = 200.0) else it }
        val c = FlockCalc.compute(KgfFlock.flock, KgfFlock.farm, KgfFlock.config, penned, KgfFlock.kgOf)
        assertEquals(200.0 * 39, c.first { it.dayNumber == 41 }.occupiedFt2, 1e-6)
        val open = penned.map { if (it.dayNumber == 42) it.copy(broodingLength = 0.0) else it }
        assertEquals(299.0 * 39, FlockCalc.compute(KgfFlock.flock, KgfFlock.farm, KgfFlock.config, open, KgfFlock.kgOf).first { it.dayNumber == 43 }.occupiedFt2, 1e-6)
    }

    private fun spaceIn(live: Int, kg: Double, hot: Double, cool: Double, day: Int = 44, area: Double = 299.0 * 39, lifted: Boolean = true) = SpacePlan.In(
        live = live, avgKg = kg, day = day, houseLenFt = 299.0, widthFt = 39.0, areaFt2 = area, penFt = null,
        feederLines = 4, pansPerLine = 114, panSpacingFt = 2.5, lineStartFt = 5.0, feederGapFt = 39.0 / 4, drinkerGapFt = 39.0 / 5,
        capKgM2 = 38.97, hotC = hot, coolC = cool, lifted = lifted)

    @Test fun afterLiftingPansOrBarricadeFollowTheWeather() {
        // day 44: 7,222 birds of about 3.1 kg in the whole house — 16 birds a pan
        val warm = SpacePlan.plan(spaceIn(7222, 3.1, hot = 31.0, cool = 21.0))
        println("warm: ${"%.1f".format(warm.kgM2)} kg/m², ${"%.2f".format(warm.ft2PerBird)} ft² a bird (needs ${"%.2f".format(warm.needFt2)}), ${warm.room} · ${warm.why}")
        warm.options.forEach { println("  ${it.title}: ${"%.1f".format(it.kgM2)} kg/m², ${it.openPans} pans ${"%.1f".format(it.birdsPerPan)} a pan, walk ${"%.2f".format(it.walkM)} m") }
        assertEquals(20.7, warm.kgM2, 0.3)
        assertEquals(SpacePlan.Room.ROOMY, warm.room)
        assertEquals("pans", warm.options[warm.best].key)                       // keep the floor, shut pans
        assertTrue(warm.options[warm.best].birdsPerPan in 30.0..60.0)
        assertTrue(warm.options.all { it.walkM <= SpacePlan.MAX_WALK_M })
        // v54: a cool day too — the app never suggests a barricade of its own (the farm sets them)
        val cool = SpacePlan.plan(spaceIn(7222, 3.1, hot = 24.0, cool = 15.0))
        assertEquals("pans", cool.options[cool.best].key)
        assertTrue(cool.options.none { it.key == "pen" })
        // before lifting, a full house on a hot day is tight
        val full = SpacePlan.plan(spaceIn(14200, 2.7, hot = 34.0, cool = 26.0, day = 39, lifted = false))
        assertEquals(SpacePlan.Room.TIGHT, full.room)
        assertTrue(full.why.startsWith("Too tight"))
        // brooding: never "roomy"
        assertEquals(SpacePlan.Room.RIGHT, SpacePlan.plan(spaceIn(15600, 0.1, 30.0, 24.0, day = 3, area = 299.0 * 39 * 0.4, lifted = false)).room)
    }

    @Test fun theLastDaysAreReadTogether() {
        fun days(feedF: Double, gainF: Double, cv: List<Double?> = listOf(null, null)): List<Suggest.Day> = (20..30).map { d ->
            val com = 100.0 + d * 5; val bw = 1000.0 + (d - 20) * 90.0
            val weighed = d == 24 || d == 27 || d == 30
            Suggest.Day(d, com * feedF, com, if (weighed) 1000.0 + (d - 20) * 90.0 * gainF else null, bw,
                if (d == 27) cv[0] else if (d == 30) cv[1] else null)
        }
        val low = Suggest.advise(days(0.88, 0.85), 30)
        assertEquals(-12.0, low.w3.feedPct!!, 0.5)
        assertTrue(low.w3.gainPct!! < -8)
        assertTrue(low.lines.any { it.level == 2 && it.text.startsWith("Eating and growing below") })
        val thrifty = Suggest.advise(days(0.94, 1.0), 30)
        assertTrue(thrifty.lines.any { it.text.startsWith("Eating less but growing") })
        val wasteful = Suggest.advise(days(1.12, 0.95), 30)
        assertTrue(wasteful.lines.any { it.level == 2 && it.text.startsWith("Eating more") })
        val uneven = Suggest.advise(days(1.0, 1.0, listOf(10.0, 12.6)), 30)
        assertTrue(uneven.lines.any { it.level == 2 && it.text.startsWith("Very uneven") })
        // the farm's own flock, day 33: eating under the company but growing well above it
        val real = Suggest.advise(rows.filter { it.dayNumber <= 33 }.map { r ->
            val next = rows.firstOrNull { it.dayNumber == r.dayNumber + 1 }
            val liveThen = computed.first { it.dayNumber == r.dayNumber }.liveBirds
            Suggest.Day(r.dayNumber, next?.let { FlockCalc.usedKg(it, KgfFlock.kgOf, 60.0) * 1000 / liveThen }?.takeIf { it > 0 && r.dayNumber < 33 },
                com.example.flock.engine.CompanyStandard.feedPerDay(r.dayNumber + 1), computed.first { it.dayNumber == r.dayNumber }.avgWeight,
                com.example.flock.engine.CompanyStandard.bw(r.dayNumber), null)
        }, 33)
        real.lines.forEach { println("day 33: ${it.text}") }
        assertTrue(real.lines.isNotEmpty())
    }

    /**
     * The farm's question: 7,222 birds left of 15,664 in the 299 × 39 ft house, 4 feeder lines of 114 pans, 3 bags fill a
     * line, about 26 bags a day. Warm: keep the house, shut pans to about 45 birds each (and each feeding still reaches the
     * last open pan); cool: the barricade, every pan past it shut. The plan is three steps.
     */
    @Test fun aHalfEmptyHouseGetsAFloorPansAndFeedingsPlan() {
        fun plan(hot: Double, cool: Double) = SpacePlan.plan(spaceIn(7222, 3.1, hot, cool).copy(dayBags = 26.0, bagsFullLine = 3.0, birdsPerPanMax = 71.0, feedingsWanted = 2))
        val warm = plan(31.0, 24.0)
        val w = warm.options[warm.best]
        warm.steps.forEach { println("warm: $it") }
        assertEquals("pans", w.key)
        assertTrue(w.birdsPerPan in 35.0..60.0)
        assertTrue(w.feedReaches)
        assertEquals(2, w.feedings)
        assertTrue("one fill ${w.fillBags} under a feeding ${w.bagsPerFeeding}", w.fillBags <= w.bagsPerFeeding)
        assertEquals(3, warm.steps.size)
        assertTrue(warm.steps[1].startsWith("1 on · 2 off"))
        // the farm pens the birds itself (a barricade at 185 ft): the pans and feedings follow that floor
        val cool = SpacePlan.plan(spaceIn(7222, 3.1, 24.0, 15.0, area = 185.0 * 39).copy(penFt = 185.0, dayBags = 26.0, bagsFullLine = 3.0,
            birdsPerPanMax = 71.0, feedingsWanted = 2))
        val c = cool.options[cool.best]
        cool.steps.forEach { println("set: $it") }
        assertEquals("set", c.key)
        assertTrue(c.shutPastPen > 0)
        assertTrue(cool.steps[1].contains("outside") && cool.steps[0].startsWith("Barricade at 185"))
        // too few bags to fill the open pans even once: the plan says so
        val short = SpacePlan.plan(spaceIn(7222, 3.1, 31.0, 24.0).copy(dayBags = 3.0, bagsFullLine = 3.0, birdsPerPanMax = 71.0))
        assertTrue(short.options.first().let { !it.feedReaches })
    }

    /**
     * v54: the floor is what the farm sets. Nothing set: the brooding plan (part of the house growing to all of it); once a
     * barricade is set — or "none" — that is the floor, the brooding plan no longer applies. The plan never adds a barricade.
     */
    @Test fun theFloorIsWhatTheFarmSets() {
        val plain = rows.map { it.copy(broodingLength = null, penStartFt = null) }
        val house = KgfFlock.farm.usableLengthFt * KgfFlock.farm.usableWidthFt
        val c = FlockCalc.compute(KgfFlock.flock, KgfFlock.farm, KgfFlock.config, plain, KgfFlock.kgOf)
        assertTrue(c.first { it.dayNumber == 2 }.occupiedFt2 < house * 0.6)
        // "none" set on day 0: the whole house from the start
        val open = plain.map { if (it.dayNumber == 0) it.copy(broodingLength = 0.0, penStartFt = 0.0) else it }
        assertEquals(house, FlockCalc.compute(KgfFlock.flock, KgfFlock.farm, KgfFlock.config, open, KgfFlock.kgOf).first { it.dayNumber == 2 }.occupiedFt2, 1e-6)
        // barricades at 40 and 140 ft from day 1: 100 ft of floor, held on
        val pen = plain.map { if (it.dayNumber == 1) it.copy(broodingLength = 140.0, penStartFt = 40.0) else it }
        assertEquals(100.0 * KgfFlock.farm.usableWidthFt, FlockCalc.compute(KgfFlock.flock, KgfFlock.farm, KgfFlock.config, pen, KgfFlock.kgOf)
            .first { it.dayNumber == 3 }.occupiedFt2, 1e-6)
        // and the plan for a floor set from the Output page (not saved yet) follows it
        val r = KgfFlock.computed(44)
        val d = com.example.flock.ui.screens.OutputData(KgfFlock.flock, KgfFlock.farm, r.last(), r, KgfFlock.feedTypes, null, emptyList(), false, false)
        val p = com.example.flock.ui.screens.withBarricades(d, 40.0, 250.0)
        assertEquals(40.0, p.penStartFt, 1e-9); assertEquals(250.0, p.barricadeFtNow, 1e-6)
        assertEquals("set", p.space.options[p.space.best].key)
        assertTrue("pans in the floor ${p.pansInArea}", p.pansInArea < d.pansInArea)
        assertTrue(p.feedOptions.isNotEmpty())
    }

    /** v53: the weather of every finished day of the flock (24 hours known), lowest, average and highest. */
    @Test fun eachFinishedDayKeepsItsWeather() {
        val hours = (0 until 72).map { i ->
            val date = java.time.LocalDate.parse("2026-11-01").plusDays((i / 24).toLong()).toString()
            com.example.flock.network.HourPoint(String.format("%sT%02d:00", date, i % 24), 22.0 + (i % 24) * 0.5, 90.0 - (i % 24))
        } + com.example.flock.network.HourPoint("2026-10-31T23:00", 20.0, 95.0)
        val w = com.example.flock.ui.dayWeatherOf(hours, "2026-10-31", "2026-11-03")
        assertEquals(setOf("2026-11-01", "2026-11-02"), w.keys)       // 31 Oct has one hour, 3 Nov is today
        val d = w["2026-11-01"]!!
        assertEquals(22.0, d.tMin, 1e-9); assertEquals(33.5, d.tMax, 1e-9); assertEquals(27.8, d.tAvg, 1e-9)
        assertEquals(67.0, d.rhMin, 1e-9); assertEquals(90.0, d.rhMax, 1e-9)
    }

    /**
     * v56: yesterday's gain is yesterday's own: its weighing against the one before — entered (green) when yesterday was
     * weighed, whatever has been entered today; and with no harvest age the lights stay at 18 h after the first week.
     */
    /**
     * v59: the sums from first principles, on the real flock, every day: birds are conserved (placed − reception − deaths −
     * culls − lifted), so is meat (in the house + lifted = produced), and FCR, cFCR, mortality, livability and density
     * are what their definitions say.
     */
    @Test fun theSumsHoldFromFirstPrinciples() {
        val r = KgfFlock.computed(44)
        val f = KgfFlock.flock; val farm = KgfFlock.farm
        val kgOf = KgfFlock.feedTypes.associate { it.code to it.bagKg }
        val bag = if (farm.feedBagKg > 0) farm.feedBagKg else 50.0
        val mass = FlockCalc.mass(f, farm, com.example.flock.data.ConfigEntity(spreadsheetId = farm.spreadsheetId), r, kgOf).associateBy { it.day }
        val entry = f.birdsPlaced - f.receptionMort
        var dead = 0; var culled = 0; var lifted = 0; var liftedKg = 0.0; var feedKg = 0.0
        r.forEach { d ->
            dead += d.mortality; culled += d.lameSeparated; lifted += d.birdsLifted; liftedKg += d.weightLifted
            feedKg += FlockCalc.usedKg(d, kgOf, bag)
            val tag = "day ${d.dayNumber}"
            // birds
            assertEquals(tag, entry - dead - culled - lifted, d.liveBirds)
            assertEquals(tag, dead * 100.0 / entry, d.cumMortPct!!, 1e-9)
            assertEquals(tag, (d.liveBirds + lifted) * 100.0 / entry, d.livability!!, 1e-9)
            // meat
            val m = mass[d.dayNumber]!!
            assertEquals(tag, lifted, m.liftedBirds); assertEquals(tag, liftedKg, m.liftedKg, 1e-6)
            assertEquals(tag, d.liveBirds * m.avgAfterKg, m.liveKg, 1e-6)
            assertEquals(tag, m.liveKg + m.liftedKg, m.producedKg, 1e-6)
            if (d.liveBirds + lifted > 0) assertEquals(tag, m.producedKg / (d.liveBirds + lifted), m.cumBwKg, 1e-9)
            // feed against meat
            if (feedKg > 0 && m.producedKg > 0) {
                assertEquals(tag, feedKg / m.producedKg, m.fcr!!, 1e-9)
                assertEquals(tag, m.fcr!! + (2.0 - m.cumBwKg) * 0.25, m.cFcrCompany!!, 1e-9)
            }
            // the floor: kg per m² is the birds' kilos over the floor they have; ft² per bird the floor over the birds
            if (d.liveBirds > 0 && d.occupiedFt2 > 0) {
                assertEquals(tag, d.liveBirds * m.avgAfterKg / (d.occupiedFt2 * 0.092903), d.densityKgM2!!, 0.02)
                assertEquals(tag, d.occupiedFt2 / d.liveBirds, d.ftPerBird, 1e-6)
            }
            // water follows feed (1.6–2.6 L a kg through cool and hot days)
            if (d.totalFeedKg > 0) assertTrue(tag + " water " + d.totalWaterL / d.totalFeedKg, d.totalWaterL / d.totalFeedKg in 1.5..2.7)
            // a weighing is the birds weighed over their number
            d.avgWeight?.let { w ->
                val g = d.filledSamples(); val singles = com.example.flock.engine.PhysiologicalEngine.parseWeights(d.indivWeights)
                val n = g.sumOf { it.second } + singles.size
                if (n > 0) assertEquals(tag, (g.sumOf { it.first } + singles.sum()) / n, w, 1e-6)
            }
        }
        // the day's deaths are a share of the birds the day began with, lifting day or not
        val liftDay = r.first { it.birdsLifted > 0 }
        val o = com.example.flock.ui.screens.OutputData(f, farm, liftDay, r, KgfFlock.feedTypes, null, emptyList(), false, false)
        assertEquals(r.first { it.dayNumber == liftDay.dayNumber - 1 }.liveBirds, o.startLive)
        assertEquals(liftDay.mortality * 100.0 / o.startLive, o.mortTodayPct!!, 1e-9)
        // the ideal floor holds the birds at the comfortable density: its length × the width × that density = their kilos
        val kgOnIdeal = o.idealFloorFt * farm.usableWidthFt * 0.092903 * o.space.targetKgM2
        if (o.idealFloorFt < farm.usableLengthFt - 0.5 && o.idealFloorFt > 10.5) assertEquals(o.live * o.bwNow / 1000.0, kgOnIdeal, o.live * o.bwNow / 1000.0 * 0.01)
        assertTrue("ideal floor ${o.idealFloorFt} ft", o.idealFloorFt > 30)
        // at the end of a lifting day the FCR by the clock is feed over everything made: the birds left and the kilos lifted
        val z = java.time.ZoneId.of(farm.timeZone)
        val v = o.nowView(java.time.LocalDate.parse(liftDay.date).atTime(23, 59).atZone(z))
        assertEquals(liftDay.liveBirds.toDouble(), v.live, 1e-9)
        assertTrue("fcr by the clock ${v.fcr} against the day's ${mass[liftDay.dayNumber]!!.fcr}", Math.abs(v.fcr!! - mass[liftDay.dayNumber]!!.fcr!!) / mass[liftDay.dayNumber]!!.fcr!! < 0.12)
    }

    @Test fun yesterdaysGainIsYesterdays() {
        val r = KgfFlock.computed(44)
        val weighed = r.filter { it.avgWeight != null && it.dayNumber in 5..43 }.map { it.dayNumber }
        val dY = weighed.first { (it + 1) !in weighed }           // a day weighed, the next one not
        val prev = r.filter { it.avgWeight != null && it.dayNumber < dY }.maxByOrNull { it.dayNumber }!!
        val dd = com.example.flock.ui.screens.OutputData(KgfFlock.flock, KgfFlock.farm, r.first { it.dayNumber == dY + 1 }, r, KgfFlock.feedTypes, null, emptyList(), false, false)
        assertEquals(com.example.flock.ui.screens.ValueKind.PRESENT, dd.gainYesterdayKind)
        assertEquals((r.first { it.dayNumber == dY }.avgWeight!! - prev.avgWeight!!) / (dY - prev.dayNumber), dd.gainYesterday!!, 1e-9)
        // the day after a day nobody weighed: projected
        val notWeighed = (6..43).first { it !in weighed }
        val de = com.example.flock.ui.screens.OutputData(KgfFlock.flock, KgfFlock.farm, r.first { it.dayNumber == notWeighed + 1 }, r, KgfFlock.feedTypes, null, emptyList(), false, false)
        assertEquals(com.example.flock.ui.screens.ValueKind.PREDICTED, de.gainYesterdayKind)
        assertEquals(18.0, com.example.flock.engine.PhysiologicalEngine.lightHours(30, 0), 1e-9)
        assertEquals(23.0, com.example.flock.engine.PhysiologicalEngine.lightHours(40, 42), 1e-9)
    }

    @Test fun theLitterGetsDeeperWetterAndGivesOffAmmoniaWithAge() {
        fun at(day: Int, feedKg: Double, rh: Double = 70.0, speed: Double = 0.3) =
            Litter.state(Litter.In(day = day, kgM2 = 30.0, feedKgTD = feedKg, floorM2 = 1083.0, airC = 26.0, rhPct = rh, airSpeedMs = speed))
        val young = at(5, 2000.0); val old = at(44, 60000.0)
        println("day 5: ${young}\nday 44: $old")
        assertTrue(old.depthCm > young.depthCm && old.depthCm in 8.0..16.0)
        assertTrue(young.moisturePct in 12.0..22.0 && old.moisturePct in 24.0..34.0)
        assertTrue(old.underDrinkersPct > old.moisturePct)
        assertEquals(0.0, young.nh3Ppm, 0.0)
        assertTrue(old.nh3Ppm > 0)
        // more air over the litter clears ammonia; a humid day wets it
        assertTrue(at(44, 60000.0, speed = 2.0).nh3Ppm < old.nh3Ppm)
        assertTrue(at(44, 60000.0, rh = 90.0).moisturePct > old.moisturePct)
    }

    /** The walk to feed and water: the limits by age, the furthest bird's walk, trays, and the radar's scoring of it. */
    @Test fun theWalkToFeedAndWater() {
        assertEquals(1.0, com.example.flock.domain.Travel.limits(2).safeM, 0.0)
        assertEquals(3.0, com.example.flock.domain.Travel.limits(44).maxM, 0.0)
        // feeder lines 10 ft apart, pans 2.5 ft apart, 1 on · 2 off: the open pans are 3 pans apart
        val walk = com.example.flock.domain.Travel.furthest(10 * 0.3048, 3 * 2.5 * 0.3048)
        assertEquals(1.905, walk, 0.01)
        assertEquals(com.example.flock.domain.Travel.Grade.SAFE, com.example.flock.domain.Travel.grade(walk, com.example.flock.domain.Travel.limits(44)))
        assertEquals(com.example.flock.domain.Travel.Grade.MOST, com.example.flock.domain.Travel.grade(walk, com.example.flock.domain.Travel.limits(5)))
        assertEquals(com.example.flock.domain.Travel.Grade.TOO_FAR, com.example.flock.domain.Travel.grade(walk, com.example.flock.domain.Travel.limits(2)))
        // 150 trays on 465 m²: one every 3.1 m², the furthest chick 1.25 m away
        assertEquals(1.245, com.example.flock.domain.Travel.spread(465.0, 150)!!, 0.01)
        assertEquals(100.0, com.example.flock.domain.Travel.score(1.9, com.example.flock.domain.Travel.limits(44)), 0.0)
        assertEquals(50.0, com.example.flock.domain.Travel.score(3.0, com.example.flock.domain.Travel.limits(44)), 0.01)
        assertEquals(0.0, com.example.flock.domain.Travel.score(4.5, com.example.flock.domain.Travel.limits(44)), 0.01)
        // the radar's band: 100 inside, 50 at the limit, 0 as far again
        assertEquals(100.0, com.example.flock.domain.Radar.band(22.0, 20.0, 25.0, 15.0, 30.0)!!, 0.0)
        assertEquals(50.0, com.example.flock.domain.Radar.band(30.0, 20.0, 25.0, 15.0, 30.0)!!, 0.01)
        assertEquals(0.0, com.example.flock.domain.Radar.band(35.0, 20.0, 25.0, 15.0, 30.0)!!, 0.01)
        // the farm's flock on day 44: every bird within the most of a pan and a nipple
        val r = KgfFlock.computed(44)
        val d = com.example.flock.ui.screens.OutputData(KgfFlock.flock, KgfFlock.farm, r.last(), r, KgfFlock.feedTypes, null, emptyList(), false, false)
        val feed = d.feedWalkM(d.feedPattern); val water = d.waterWalkM
        println("day 44 walk: feed ${"%.2f".format(feed)} m (${d.feedPattern.label}), water ${"%.2f".format(water)} m, limits ${d.walkLimits}")
        assertTrue(feed <= d.walkLimits.maxM && water <= d.walkLimits.maxM)
    }

    /** Both barricades: the floor between them, its pans, and a feeding that fills the open pans only (the tube stays full). */
    @Test fun theFloorBetweenTwoBarricades() {
        // 7,222 birds of 3.2 kg left in the house, trimmed 50 ft at the front and 50 ft at the back of 299 ft
        val i = spaceIn(7222, 3.2, 31.0, 24.0, area = (299.0 - 100) * 39).copy(penStartFt = 50.0, penFt = 249.0,
            dayBags = 22.0, bagsFullLine = 3.0, bagsShutLine = 1.0, birdsPerPanMax = 70.0, feedingsWanted = 2)
        val out = SpacePlan.plan(i)
        val set = out.options.first { it.key == "set" }
        println("as set: ${set.title}; ${out.why}; ${out.steps}")
        assertEquals("set", out.options[out.best].key)
        assertTrue("pans inside the barricades only", set.pansInPen in 75..82)
        assertEquals(199.0 * 39, set.areaFt2, 1.0)
        // filling the lines takes the tube (1 bag a line) as well as the open pans (114 pans share the other 2 bags)
        assertEquals(4 * 1.0 + 4 * set.openPerLine / (114 / 2.0), set.fillBags, 0.01)
        assertTrue(out.steps[0].startsWith("Barricades at 50 and 249 ft"))
    }

    /** The house map: tunnel air warms towards the fans, the birds add up to the flock, the litter is wetter under the drinkers. */
    @Test fun theHouseMap() {
        val m = com.example.flock.models.HouseMap.field(com.example.flock.models.HouseMap.In(
            lengthFt = 299.0, widthFt = 39.0, penStartFt = 0.0, penEndFt = 299.0,
            drinkerYFt = listOf(2.2, 10.8, 19.5, 28.2, 36.8), feederYFt = listOf(6.5, 15.2, 23.8, 32.5),
            live = 7222, avgKg = 3.15, houseC = 30.0, houseRh = 65.0, airCfm = 100_000.0, tunnel = true, comfortC = 21.0,
            litterMoistPct = 28.0, densityIdeal = 30.0, densityMost = 39.0, rhIdeal = 70.0, rhMost = 80.0))
        println("rise ${"%.2f".format(m.riseC)} °C; air ${"%.1f".format(m.airC(5.0))} → ${"%.1f".format(m.airC(295.0))}; rh ${"%.1f".format(m.rh(5.0))} → ${"%.1f".format(m.rh(295.0))}")
        assertTrue(m.riseC in 0.5..4.0)
        // warmer towards the fans; the humidity can go either way (warming dries the air, the birds' breath wets it)
        assertTrue(m.airC(295.0) > m.airC(5.0))
        assertTrue(m.rh(5.0) in 40.0..90.0 && m.rh(295.0) in 40.0..90.0)
        // the birds on the floor add up to the flock
        var birds = 0.0
        for (a in 0 until 60) for (b in 0 until 40) birds += m.birdsM2((a + 0.5) * 299 / 60, (b + 0.5) * 39 / 40) * (299.0 / 60 * 39 / 40 * 0.0929)
        assertEquals(7222.0, birds, 7222 * 0.05)
        // wetter under a drinker line than between the lines; outside the birds' floor nobody lives
        assertTrue(m.litterMoist(150.0, 10.8) > m.litterMoist(150.0, 6.5))
        val pen = com.example.flock.models.HouseMap.field(com.example.flock.models.HouseMap.In(299.0, 39.0, 50.0, 249.0, listOf(10.8), listOf(6.5),
            7222, 3.15, 24.0, 60.0, 10_000.0, false, 21.0, 25.0, 30.0, 39.0, 70.0, 80.0))
        assertEquals(0.0, pen.kgM2(20.0, 10.0), 0.0)
        assertTrue(pen.kgM2(150.0, 10.0) > 0)
    }

    /** Numbers as typed on the entry page: comma decimals, spaces, "12.0" for a count. */
    @Test fun numbersAsTyped() {
        assertEquals(3.5, "3,5".toNum()!!, 0.0); assertEquals(3.5, " 3.5 ".toNum()!!, 0.0); assertEquals(1234.5, "1,234.5".toNum()!!, 0.0)
        assertEquals(12, "12.0".toCount()); assertEquals(1234, "1,234".toCount()); assertEquals(null, "12.5".toCount())
    }

    @Test fun feedingsRoundTrip() {
        val l = parseFeedings("8:30=13;14:00=10.5;bad;20:00=0")
        assertEquals(listOf("08:30" to 13.0, "14:00" to 10.5), l)
        assertEquals("08:30=13;14:00=10.5", formatFeedings(l))
        assertEquals(listOf("06:00" to 4.0, "08:30" to 13.0, "14:00" to 10.5), parseFeedings(formatFeedings(l + ("06:00" to 4.0))))
    }
}
