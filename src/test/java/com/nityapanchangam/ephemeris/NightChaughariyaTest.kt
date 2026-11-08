package com.nityapanchangam.ephemeris

import org.junit.Assert.assertEquals
import org.junit.Test

/** The night's Choghadiya order, against the order every panchang prints. Matches iOS. */
class NightChaughariyaTest {

    private val names = listOf("Udveg", "Char", "Labh", "Amrit", "Kaal", "Shubh", "Rog")

    private val standard = listOf(
        listOf("Shubh", "Amrit", "Char", "Rog", "Kaal", "Labh", "Udveg", "Shubh"),
        listOf("Char", "Rog", "Kaal", "Labh", "Udveg", "Shubh", "Amrit", "Char"),
        listOf("Kaal", "Labh", "Udveg", "Shubh", "Amrit", "Char", "Rog", "Kaal"),
        listOf("Udveg", "Shubh", "Amrit", "Char", "Rog", "Kaal", "Labh", "Udveg"),
        listOf("Amrit", "Char", "Rog", "Kaal", "Labh", "Udveg", "Shubh", "Amrit"),
        listOf("Rog", "Kaal", "Labh", "Udveg", "Shubh", "Amrit", "Char", "Rog"),
        listOf("Labh", "Udveg", "Shubh", "Amrit", "Char", "Rog", "Kaal", "Labh"),
    )

    @Test fun everyNightOfTheWeekRunsInTheStandardOrder() {
        for (weekday in 1..7) {
            assertEquals("weekday $weekday", standard[weekday - 1], nightChaughariyaOrder(weekday).map { names[it] })
        }
    }
}
