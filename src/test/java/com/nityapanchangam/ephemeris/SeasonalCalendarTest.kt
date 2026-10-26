package com.nityapanchangam.ephemeris

import com.nityapanchangam.ephemeris.models.DailyPanchangSummary
import com.nityapanchangam.ephemeris.models.HinduFestival
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

/**
 * The seasonal rules, fed summaries and festivals built here rather than computed, so the edges a
 * real year may not happen to contain — a vriddhi or kshaya Ekadashi at either end of Chaturmas,
 * an Adhik Ashadha — are each pinned down. The iOS library runs the same rules against the real
 * ephemeris for 2026.
 */
class SeasonalCalendarTest {

    private val day: Date = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 1, 12, 0, 0) }.time
    private val north = 23.1765

    /** One summary per day, from a week before [day] to a week after. */
    private fun week(
        purnimanta: (Int) -> Int,
        tithi: (Int) -> Int = { 20 },
        adhik: (Int) -> Boolean = { false },
        amanta: (Int) -> Int = { offset -> purnimanta(offset) },
    ): List<DailyPanchangSummary> = (-7..7).map { offset ->
        DailyPanchangSummary(
            date = SeasonalCalendar.offset(SeasonalCalendar.startOfDay(day), offset),
            tithiNumber = tithi(offset), nakshatraNumber = 1, moonRashiNumber = 1,
            lunarMonth = purnimanta(offset), isAdhikMaas = adhik(offset), amantaMonth = amanta(offset),
        )
    }

    private fun on(offset: Int, name: String) =
        HinduFestival(name, SeasonalCalendar.offset(SeasonalCalendar.startOfDay(day), offset), "")

    private fun resolve(
        summaries: List<DailyPanchangSummary>,
        festivals: List<HinduFestival> = emptyList(),
        system: LunarMonthSystem = LunarMonthSystem.PURNIMANTA,
        latitude: Double = north,
    ) = SeasonalCalendar.resolve(SeasonalCalendar.startOfDay(day), summaries, festivals, latitude, system)

    // ------------------------------------------------------------------ Ritu

    @Test fun rituPairsTheMonthsAsCharakaDoes() {
        val expected = listOf(Ritu.VASANTA, Ritu.VASANTA, Ritu.GRISHMA, Ritu.GRISHMA, Ritu.VARSHA, Ritu.VARSHA,
                              Ritu.SHARAD, Ritu.SHARAD, Ritu.HEMANTA, Ritu.HEMANTA, Ritu.SHISHIRA, Ritu.SHISHIRA)
        assertEquals(expected, (1..12).map(Ritu::forLunarMonth))
        assertEquals(Ritu.VASANTA, Ritu.forLunarMonth(13))
    }

    @Test fun southOfTheEquatorTheRituIsSixMonthsOnButTheMonthIsNot() {
        assertEquals(listOf(Ritu.SHARAD, Ritu.HEMANTA, Ritu.SHISHIRA, Ritu.VASANTA, Ritu.GRISHMA, Ritu.VARSHA),
                     Ritu.entries.map { it.southernHemisphere })
        val south = resolve(week({ 6 }), latitude = -33.87)!!
        assertEquals(Ritu.SHISHIRA, south.ritu)
        assertEquals(6, south.lunarMonth)
    }

    @Test fun thePurnimantaRituIsThePanchangAppsAndTheAmantaRituFollowsItsOwnMonth() {
        // A Krishna day across the Sharad-Hemanta turn: Purnimanta is in Margashirsha, Amanta in Kartika.
        val summaries = week({ 9 }, tithi = { 5 }, amanta = { 8 })
        assertEquals(Ritu.HEMANTA, resolve(summaries, system = LunarMonthSystem.PURNIMANTA)!!.ritu)
        val amanta = resolve(summaries, system = LunarMonthSystem.AMANTA)!!
        assertEquals(Ritu.SHARAD, amanta.ritu)
        assertEquals(8, amanta.lunarMonth)
    }

    // ------------------------------------------------------------------ Ritu Sandhi

    /** The Ritu turns to Hemanta on the day [turn] days from [day]. */
    private fun turningAt(turn: Int) = week({ offset -> if (offset >= turn) 9 else 8 })

    @Test fun rituSandhiIsTheWeekEitherSideOfTheTurn() {
        // Seven days before the turn through six days after it.
        for (turn in -6..7) assertTrue("turn at $turn", resolve(turningAt(turn))!!.isRituSandhi)
        assertFalse("eight days before", resolve(turningAt(8))!!.isRituSandhi)
        assertFalse("seven days after", resolve(turningAt(-7))!!.isRituSandhi)
    }

    // ------------------------------------------------------------------ Chaturmas

    private fun chaturmas(month: Int, tithi: Int, adhik: Boolean = false, vararg festivals: HinduFestival) =
        resolve(week({ month }, tithi = { offset -> tithi + offset }, adhik = { adhik }), festivals.toList())!!.isChaturmas

    @Test fun chaturmasBeginsOnTheObservedDevshayani() {
        assertFalse("the day before", chaturmas(4, 25, false, on(1, "Devshayani Ekadashi")))
        assertTrue("the day itself", chaturmas(4, 26, false, on(0, "Devshayani Ekadashi")))
        // Vriddhi: Ekadashi holds two sunrises and is kept on the second, so the first isn't yet Chaturmas.
        assertFalse("a vriddhi Ekadashi's first sunrise", chaturmas(4, 26, false, on(1, "Devshayani Ekadashi")))
        // Kshaya: no sunrise holds it, and it's kept on the day that held the greater part.
        assertTrue("a kshaya Ekadashi kept on Dashami's sunrise", chaturmas(4, 25, false, on(0, "Devshayani Ekadashi")))
        assertTrue("Ashadha Purnima", chaturmas(4, 30))
        assertFalse("early Ashadha", chaturmas(4, 10))
        assertFalse("Adhik Ashadha comes before Devshayani", chaturmas(4, 28, adhik = true))
    }

    @Test fun chaturmasRunsThroughTheMonsoonMonthsAdhikOrNot() {
        for (month in 5..7) {
            assertTrue("month $month", chaturmas(month, 3))
            assertTrue("Adhik month $month", chaturmas(month, 3, adhik = true))
        }
    }

    @Test fun chaturmasEndsOnTheObservedDevutthana() {
        assertTrue("Diwali fortnight", chaturmas(8, 14))
        assertTrue("the day itself", chaturmas(8, 26, false, on(0, "Devutthana Ekadashi")))
        assertTrue("a vriddhi Ekadashi's first sunrise", chaturmas(8, 26, false, on(1, "Devutthana Ekadashi")))
        assertFalse("the day after", chaturmas(8, 27, false, on(-1, "Devutthana Ekadashi")))
        assertFalse("Kartika Purnima", chaturmas(8, 30))
        assertTrue("Adhik Kartika comes before the real one", chaturmas(8, 29, adhik = true))
        assertFalse("Margashirsha", chaturmas(9, 3))
        assertFalse("spring", chaturmas(1, 20))
    }

    // ------------------------------------------------------------------ Vrat

    @Test fun theObservedEkadashiIsAVrat() {
        assertEquals(VratDay.EKADASHI, resolve(week({ 8 }), listOf(on(0, "Rama Ekadashi")))!!.vrat)
        assertEquals(VratDay.EKADASHI, resolve(week({ 5 }), listOf(on(0, "Padmini Ekadashi")))!!.vrat)
        assertNull(resolve(week({ 8 }), listOf(on(-1, "Rama Ekadashi")))!!.vrat)
        assertNull("not every festival is a vrat", resolve(week({ 8 }), listOf(on(0, "Dhanteras")))!!.vrat)
    }

    @Test fun navratriRunsFromItsFirstDayToItsNavami() {
        val sharad = listOf(on(-3, "Navratri"), on(5, "Maha Navami"))
        assertEquals(VratDay.NAVRATRI, resolve(week({ 7 }), sharad)!!.vrat)
        assertEquals("first day", VratDay.NAVRATRI, resolve(week({ 7 }), listOf(on(0, "Navratri"), on(8, "Maha Navami")))!!.vrat)
        assertEquals("Navami", VratDay.NAVRATRI, resolve(week({ 7 }), listOf(on(-8, "Navratri"), on(0, "Maha Navami")))!!.vrat)
        assertNull("Dussehra", resolve(week({ 7 }), listOf(on(-9, "Navratri"), on(-1, "Maha Navami")))!!.vrat)
        assertNull("the day before", resolve(week({ 7 }), listOf(on(1, "Navratri"), on(9, "Maha Navami")))!!.vrat)
        assertEquals("Chaitra", VratDay.NAVRATRI,
                     resolve(week({ 1 }), listOf(on(-2, "Navratri (Chaitra)"), on(6, "Ram Navami")))!!.vrat)
    }

    // ------------------------------------------------------------------ No answer

    @Test fun missingDaysGiveNoAnswer() {
        assertNull("no summary for the day", resolve(week({ 6 }).filter { it.date != SeasonalCalendar.startOfDay(day) }))
        assertNull("no summary a week on", resolve(week({ 6 }).dropLast(1)))
        assertNull("Amanta asked of a summary without it", resolve(week({ 6 }, amanta = { 0 }), system = LunarMonthSystem.AMANTA))
    }
}
