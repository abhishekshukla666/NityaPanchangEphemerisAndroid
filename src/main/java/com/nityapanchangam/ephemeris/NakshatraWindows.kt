package com.nityapanchangam.ephemeris

/**
 * Amrit Kaal and Varjyam: the two windows a nakshatra holds at fixed points in itself. The same
 * as the iOS engine's NakshatraWindows.swift.
 *
 * Each nakshatra is sixty ghatis long, however many hours the Moon takes to cross it. Amrit Kaal
 * begins at a set ghati of each -- Rohini's at 52, Mrigashira's at 38 -- and Varjyam, the window
 * to keep clear of, at another; each lasts four ghatis. So both stretch or shrink with their
 * nakshatra: about 85 minutes on a fast Moon, nearer 100 on a slow one. Mula alone holds two
 * Varjyams.
 *
 * Checked against Drik Panchang's daily times for New Delhi, 25 November to 29 December 2026:
 * all 27 nakshatras' Amrit Kaal and 24 of their Varjyams land within a minute of the published
 * start. The other three Varjyam points are Drik's own table of tyajya ghatis.
 *
 * The app used to show the Amrit chaughadiya, an eighth of the daytime, as "Amrit Kaal" -- on
 * Wednesdays the same eighth as Yamaganda, and on Thursdays as Rahu Kaal.
 */
internal object NakshatraWindows {
    /** The ghati of its nakshatra at which Amrit Kaal begins, Ashwini (1) to Revati (27). */
    val amritGhati = doubleArrayOf(
        42.0, 48.0, 54.0, 52.0, 38.0, 35.0, 54.0, 44.0, 56.0,   // Ashwini … Ashlesha
        54.0, 44.0, 42.0, 45.0, 44.0, 38.0, 38.0, 34.0, 38.0,   // Magha … Jyeshtha
        44.0, 48.0, 44.0, 34.0, 34.0, 42.0, 40.0, 48.0, 54.0    // Mula … Revati
    )

    /** The ghatis at which Varjyam begins, likewise. Mula has two. */
    val varjyamGhatis: List<List<Double>> = listOf(
        listOf(50.0), listOf(24.0), listOf(30.0), listOf(40.0), listOf(14.0), listOf(21.0), listOf(30.0), listOf(20.0), listOf(32.0),
        listOf(30.0), listOf(20.0), listOf(18.0), listOf(21.0), listOf(20.0), listOf(14.0), listOf(14.0), listOf(10.0), listOf(14.0),
        listOf(20.0, 56.0), listOf(24.0), listOf(20.0), listOf(10.0), listOf(10.0), listOf(18.0), listOf(16.0), listOf(24.0), listOf(30.0)
    )

    /** Both last four ghatis of their nakshatra. */
    const val LENGTH_GHATIS = 4.0

    /** The windows at [ghatis] in a nakshatra running [start] to [end], in the same units. */
    fun windows(ghatis: List<Double>, start: Double, end: Double): List<Pair<Double, Double>> {
        val ghati = (end - start) / 60.0
        return ghatis.map { (start + it * ghati) to (start + (it + LENGTH_GHATIS) * ghati) }
    }

    /** [number] is 1-27. */
    fun amrit(number: Int, start: Double, end: Double): List<Pair<Double, Double>> =
        if (number !in 1..27) emptyList() else windows(listOf(amritGhati[number - 1]), start, end)

    fun varjyam(number: Int, start: Double, end: Double): List<Pair<Double, Double>> =
        if (number !in 1..27) emptyList() else windows(varjyamGhatis[number - 1], start, end)
}
