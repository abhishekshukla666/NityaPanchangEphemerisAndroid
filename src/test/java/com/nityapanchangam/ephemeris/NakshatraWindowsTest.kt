package com.nityapanchangam.ephemeris

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Amrit Kaal and Varjyam against Drik Panchang's published times for New Delhi, from Drik's own
 * nakshatra times. The ephemeris is native code and cannot run on the JVM, so this pins the
 * Kotlin port of the rule: that the same nakshatra lands the same window. The iOS engine's
 * DrikMuhuratTests run the full engine over the same month.
 *
 * Read from drikpanchang.com's day panchang (geoname 1261481) on 25 November 2026.
 */
class NakshatraWindowsTest {

    private val fmt = SimpleDateFormat("yyyy dd MMM HH:mm", Locale.ENGLISH).apply {
        timeZone = TimeZone.getTimeZone("Asia/Kolkata")
    }

    private fun t(s: String): Double = fmt.parse("2026 $s")!!.time / 60_000.0   // minutes

    private val names = listOf(
        "Ashwini", "Bharani", "Krittika", "Rohini", "Mrigashira", "Ardra", "Punarvasu", "Pushya", "Ashlesha",
        "Magha", "Purva Phalguni", "Uttara Phalguni", "Hasta", "Chitra", "Swati", "Vishakha", "Anuradha", "Jyeshtha",
        "Mula", "Purva Ashadha", "Uttara Ashadha", "Shravana", "Dhanishta", "Shatabhisha", "Purva Bhadrapada",
        "Uttara Bhadrapada", "Revati"
    )

    /** Each nakshatra's end, in order, as Drik prints them. */
    private val ends = listOf(
        "Magha" to "01 Dec 09:00", "Purva Phalguni" to "02 Dec 08:55", "Uttara Phalguni" to "03 Dec 09:23",
        "Hasta" to "04 Dec 10:22", "Chitra" to "05 Dec 11:48", "Swati" to "06 Dec 13:38", "Vishakha" to "07 Dec 15:48",
        "Anuradha" to "08 Dec 18:16", "Jyeshtha" to "09 Dec 21:00", "Mula" to "10 Dec 23:58",
        "Purva Ashadha" to "12 Dec 03:04", "Uttara Ashadha" to "13 Dec 06:12", "Shravana" to "14 Dec 09:12",
        "Dhanishta" to "15 Dec 11:52", "Shatabhisha" to "16 Dec 14:02", "Purva Bhadrapada" to "17 Dec 15:30",
        "Uttara Bhadrapada" to "18 Dec 16:10", "Revati" to "19 Dec 15:58", "Ashwini" to "20 Dec 14:55",
        "Bharani" to "21 Dec 13:08", "Krittika" to "22 Dec 10:45", "Rohini" to "23 Dec 07:56",
        "Mrigashira" to "24 Dec 04:53", "Ardra" to "25 Dec 01:47", "Punarvasu" to "25 Dec 22:50",
        "Pushya" to "26 Dec 20:12", "Ashlesha" to "27 Dec 18:04", "Magha" to "28 Dec 16:32",
        "Purva Phalguni" to "29 Dec 15:43"
    )

    private fun nakshatra(name: String): Triple<Int, Double, Double> {
        val i = (1 until ends.size).first { ends[it].first == name }
        return Triple(names.indexOf(name) + 1, t(ends[i - 1].second), t(ends[i].second))
    }

    /** Drik's Amrit Kalam start for every nakshatra in the month. */
    private val amrit = listOf(
        "Purva Phalguni" to "02 Dec 02:32", "Uttara Phalguni" to "03 Dec 02:03", "Hasta" to "04 Dec 04:08",
        "Chitra" to "05 Dec 05:01", "Swati" to "06 Dec 04:10", "Vishakha" to "07 Dec 06:12",
        "Anuradha" to "08 Dec 06:48", "Jyeshtha" to "09 Dec 11:12", "Mula" to "10 Dec 16:46",
        "Purva Ashadha" to "11 Dec 21:39", "Uttara Ashadha" to "12 Dec 22:58", "Shravana" to "13 Dec 21:30",
        "Dhanishta" to "15 Dec 00:19", "Shatabhisha" to "16 Dec 06:11", "Purva Bhadrapada" to "17 Dec 07:01",
        "Uttara Bhadrapada" to "18 Dec 11:14", "Revati" to "19 Dec 13:35", "Ashwini" to "20 Dec 08:02",
        "Bharani" to "21 Dec 08:42", "Krittika" to "22 Dec 08:35", "Rohini" to "23 Dec 05:07",
        "Mrigashira" to "23 Dec 21:12", "Ardra" to "24 Dec 17:04", "Punarvasu" to "25 Dec 20:43",
        "Pushya" to "26 Dec 14:30", "Ashlesha" to "27 Dec 16:37", "Magha" to "28 Dec 14:18"
    )

    /** Drik's Varjyam starts; Mula's two among them. */
    private val varjyam = listOf(
        "Purva Phalguni" to "01 Dec 16:58", "Uttara Phalguni" to "02 Dec 16:15", "Hasta" to "03 Dec 18:08",
        "Chitra" to "04 Dec 18:51", "Swati" to "05 Dec 17:50", "Vishakha" to "06 Dec 19:44",
        "Anuradha" to "07 Dec 20:12", "Jyeshtha" to "09 Dec 00:30", "Mula" to "10 Dec 05:59",
        "Mula" to "10 Dec 22:10", "Purva Ashadha" to "11 Dec 10:48", "Uttara Ashadha" to "12 Dec 12:06",
        "Shravana" to "13 Dec 10:42", "Dhanishta" to "14 Dec 13:39", "Shatabhisha" to "15 Dec 19:43",
        "Purva Bhadrapada" to "16 Dec 20:50", "Uttara Bhadrapada" to "18 Dec 01:22", "Revati" to "19 Dec 04:04",
        "Ashwini" to "20 Dec 11:06", "Krittika" to "21 Dec 23:57", "Rohini" to "23 Dec 00:52",
        "Mrigashira" to "23 Dec 12:49", "Ardra" to "24 Dec 12:12", "Punarvasu" to "25 Dec 12:18",
        "Ashlesha" to "27 Dec 07:52"
    )

    @Test
    fun amritKaalMatchesDrikForEveryNakshatra() {
        for ((name, published) in amrit) {
            val (n, start, end) = nakshatra(name)
            val window = NakshatraWindows.amrit(n, start, end).single()
            assertEquals("$name Amrit Kaal", t(published), window.first, 1.5)
        }
        assertEquals(27, amrit.map { it.first }.toSet().size)
    }

    @Test
    fun varjyamMatchesDrik() {
        for ((name, published) in varjyam) {
            val (n, start, end) = nakshatra(name)
            val windows = NakshatraWindows.varjyam(n, start, end)
            val nearest = windows.minByOrNull { kotlin.math.abs(it.first - t(published)) }!!
            assertEquals("$name Varjyam", t(published), nearest.first, 1.5)
        }
    }

    /** Four ghatis of the nakshatra: 85 minutes on 25 Nov's Rohini, Drik's 5:46-7:11 PM. */
    @Test
    fun aWindowIsFourGhatisOfItsNakshatra() {
        val start = t("24 Nov 23:21")
        val end = t("25 Nov 20:36")
        val (s, e) = NakshatraWindows.amrit(4, start, end).single()
        assertEquals(t("25 Nov 17:46"), s, 1.5)
        assertEquals(t("25 Nov 19:11"), e, 1.5)
    }

    @Test
    fun mulaHoldsTwoVarjyams() {
        assertEquals(listOf(20.0, 56.0), NakshatraWindows.varjyamGhatis[18])
        assertEquals(27, NakshatraWindows.amritGhati.size)
        assertEquals(27, NakshatraWindows.varjyamGhatis.size)
    }
}
