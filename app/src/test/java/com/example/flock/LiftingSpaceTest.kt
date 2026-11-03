package com.example.flock

import com.example.flock.data.FlockCalc
import com.example.flock.data.formatFeedings
import com.example.flock.data.parseFeedings
import com.example.flock.domain.HouseField.Layer
import com.example.flock.domain.SpacePlan
import com.example.flock.domain.Suggest
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
        assertEquals("pans", warm.options[warm.best].key)                       // warm: keep the floor, shut pans
        assertTrue(warm.options[warm.best].birdsPerPan in 30.0..60.0)
        assertTrue(warm.options.all { it.walkM <= SpacePlan.MAX_WALK_M })
        // a cool day: pen the birds — the barricade where they have the floor they need
        val cool = SpacePlan.plan(spaceIn(7222, 3.1, hot = 24.0, cool = 15.0))
        val pen = cool.options[cool.best]
        assertEquals("pen", pen.key)
        assertTrue("pen at ${pen.penFt}", pen.penFt in 120.0..250.0 && pen.penFt % 5.0 == 0.0)
        assertTrue(pen.kgM2 <= 38.97 + 0.5)
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

    private fun field(fanCfm: Double, speed: Double, day: Int = 40) = com.example.flock.domain.HouseField.In(
        lengthFt = 299.0, widthFt = 39.0, heightFt = 7.5, barricadeFt = 299.0,
        feederYs = listOf(1, 3, 5, 7).map { (it + 0.5) * 39.0 / 9 }, drinkerYs = listOf(0, 2, 4, 6, 8).map { (it + 0.5) * 39.0 / 9 },
        live = 12000, avgKg = 2.8, day = day, airC = 27.0, rhPct = 70.0, idealC = 21.0, idealRh = 60.0,
        fanCfm = fanCfm, airSpeedMs = speed, heatLoadC = 2.0, outRh = 80.0, body = Triple(40.6, 41.2, 42.0), heatersOn = false)

    @Test fun theHouseMapLayersPointTheRightWay() {
        val H = com.example.flock.domain.HouseField
        val tunnel = field(8 * 20000.0, 2.0); val still = field(20000.0, 0.15)
        // air warms from the pads to the fans, more with little air moved
        assertTrue(H.airC(tunnel, 290.0, 0.12) > H.airC(tunnel, 10.0, 0.12))
        assertTrue(H.lengthRiseC(still) > H.lengthRiseC(tunnel))
        // still air is warmer under the roof; fast air mixes it
        val stillUp = H.airC(still, 150.0, 0.95) - H.airC(still, 150.0, 0.05)
        val tunnelUp = H.airC(tunnel, 150.0, 0.95) - H.airC(tunnel, 150.0, 0.05)
        assertTrue("still $stillUp tunnel $tunnelUp", stillUp > 2.0 && tunnelUp < 1.0)
        // warmer air reads lower humidity
        assertTrue(H.rh(still, 150.0, 0.95) < H.rh(still, 150.0, 0.05))
        // the density averages to the floor's
        val avg = H.densityAvg(tunnel)
        val mean = (0 until 99).flatMap { xi -> (0 until 39).map { yi -> H.density(tunnel, xi * 3.0 + 1.5, yi + 0.5) } }.average()
        assertEquals(avg, mean, avg * 0.03)
        // wetter litter under a drinker line than between the lines; more ammonia where it is wetter
        val dy = tunnel.drinkerYs[2]; val fy = tunnel.feederYs[1]
        assertTrue(H.moisture(tunnel, 150.0, dy) > H.moisture(tunnel, 150.0, fy))
        assertTrue(H.ammonia(still, 150.0, dy) > H.ammonia(still, 150.0, fy))
        // the ideal is the same everywhere; the position on the scale is 0 in the good band
        assertEquals(H.ideal(Layer.MOISTURE, tunnel, 10.0, 3.0), H.ideal(Layer.MOISTURE, tunnel, 280.0, 30.0), 0.0)
        assertEquals(0.0, H.position(H.scale(Layer.AMMONIA, tunnel), 5.0), 0.0)
        assertTrue(H.position(H.scale(Layer.AMMONIA, tunnel), 25.0) > 0.5)
    }

    @Test fun feedingsRoundTrip() {
        val l = parseFeedings("8:30=13;14:00=10.5;bad;20:00=0")
        assertEquals(listOf("08:30" to 13.0, "14:00" to 10.5), l)
        assertEquals("08:30=13;14:00=10.5", formatFeedings(l))
        assertEquals(listOf("06:00" to 4.0, "08:30" to 13.0, "14:00" to 10.5), parseFeedings(formatFeedings(l + ("06:00" to 4.0))))
    }
}
