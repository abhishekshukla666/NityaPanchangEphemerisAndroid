package com.nityapanchangam.ephemeris.models

import java.util.Date
import java.util.Calendar

/** Solar or Lunar eclipse. */
enum class GrahanKind {
    SOLAR, LUNAR
}

/** Severity/depth of the eclipse at a location. */
enum class GrahanExtent {
    TOTAL,
    ANNULAR,      // solar only
    PARTIAL,
    PENUMBRAL     // lunar only
}

/**
 * An eclipse (grahan) as observed from one place on Earth.
 *
 * Only locally visible eclipses are ever produced. Swiss Ephemeris can also
 * enumerate every eclipse globally, but for panchang purposes that would be
 * misleading: Sutak is observed only where the eclipse is actually visible, so
 * an eclipse over the Pacific is not an event in the user's almanac.
 */
data class Grahan(
    val kind: GrahanKind,
    val extent: GrahanExtent,

    /** Greatest eclipse as seen from the requested location. */
    val peak: Date,
    /** First and last local contact — the span over which anything is visible. */
    val begins: Date,
    val ends: Date,
    /** Totality (or annularity) window, when the eclipse reaches that phase here. */
    val totalityBegins: Date? = null,
    val totalityEnds: Date? = null,

    /** Fraction of the disc covered at greatest eclipse, as seen from this place. */
    val magnitude: Double
) {
    val id: String get() = "${kind.name}-${peak.time / 1000}"

    /** Untranslated name; the app maps this through its own dictionary. */
    val name: String get() = when {
        kind == GrahanKind.SOLAR && extent == GrahanExtent.TOTAL -> "Total Solar Eclipse"
        kind == GrahanKind.SOLAR && extent == GrahanExtent.ANNULAR -> "Annular Solar Eclipse"
        kind == GrahanKind.SOLAR -> "Partial Solar Eclipse"
        kind == GrahanKind.LUNAR && extent == GrahanExtent.TOTAL -> "Total Lunar Eclipse"
        kind == GrahanKind.LUNAR && extent == GrahanExtent.PENUMBRAL -> "Penumbral Lunar Eclipse"
        else -> "Partial Lunar Eclipse"
    }

    /** Whether any part of this eclipse falls on the given local day. */
    fun occursOn(date: Date): Boolean {
        val cal = Calendar.getInstance().apply { time = date }
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val dayStart = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val dayEnd = cal.timeInMillis
        return begins.time < dayEnd && ends.time >= dayStart
    }
}
