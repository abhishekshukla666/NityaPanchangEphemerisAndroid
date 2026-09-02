package com.nityapanchangam.ephemeris.models

import java.util.Date

enum class Paksha {
    SHUKLA, KRISHNA
}

data class UserLocation(
    val latitude: Double,
    val longitude: Double,
    val name: String = "Current Location"
)

data class Tithi(
    val name: String,
    val endTime: Date,
    val paksha: Paksha
)

data class Nakshatra(
    val name: String,
    val endTime: Date
)

data class MinorLimb(
    val name: String,
    val endTime: Date? = null
)

enum class MuhuratType {
    AUSPICIOUS, INAUSPICIOUS, NEUTRAL
}

data class Muhurat(
    val id: String,
    val name: String,
    val startTime: Date,
    val endTime: Date,
    val type: MuhuratType
)

data class PlanetPosition(
    val id: Int,
    val name: String,
    val symbol: String,
    val longitude: Double,
    val rashiNumber: Int,
    val degrees: Double
)

/** Computed chart used for marriage matching (Guna Milan) and Kundli charts. */
data class BirthChart(
    val nakshatra: Int,        // 1-27
    val pada: Int,              // 1-4
    val rashi: Int,              // 1-12 (Moon sign)
    val moonLongitude: Double,  // 0-360 sidereal
    val marsRashi: Int,         // 1-12
    val lagnaRashi: Int,        // 1-12 (Ascendant sign)
    val lagnaLongitude: Double,           // 0-360 sidereal - precise Ascendant, used for Navamsa
    val planetPositions: List<PlanetPosition>  // all 9 grahas, used to draw Lagna/Navamsa kundli charts
)

data class HoraInfo(
    val id: Int,
    val planet: String,
    val symbol: String,
    val startTime: Date,
    val endTime: Date,
    val isDay: Boolean,
    val type: MuhuratType
) {
    val isActive: Boolean get() {
        val now = Date()
        return now.after(startTime) && now.before(endTime)
    }
}

data class LagnaPeriod(
    val id: Int,
    val rashiNumber: Int,
    val rashiName: String,
    val rashiSymbol: String,
    val isDay: Boolean,
    val startTime: Date,
    val endTime: Date
) {
    val isActive: Boolean get() {
        val now = Date()
        return now.after(startTime) && now.before(endTime)
    }
}

data class PanchangDay(
    val date: Date,
    val lunarMonth: String,
    val lunarMonthNumber: Int,
    val isAdhikMaas: Boolean,
    val sunrise: Date,
    val sunset: Date,
    val moonrise: Date?,
    val moonset: Date?,
    val tithi: Tithi,
    val tithiNumber: Int,
    val nakshatra: Nakshatra,
    val nakshatraNumber: Int,
    val yoga: MinorLimb,
    val karana: MinorLimb,
    val vara: String,
    val moonRashi: String,
    val muhurats: List<Muhurat>,
    val chaughariya: List<Muhurat>,
    val nightChaughariya: List<Muhurat>,
    val planetPositions: List<PlanetPosition>,
    val vedaAyana: String,
    val isUttarayana: Boolean,
    val raviYoga: Boolean,
    val horas: List<HoraInfo>,
    val lagnas: List<LagnaPeriod>,
    /** The day's Vishti (Bhadra) window, or null on the majority of days that have none
     *  between sunrise and the next sunrise. */
    val bhadraKaal: Muhurat? = null,
    /**
     * The Amanta name for this same day, a display-only parallel to [lunarMonth].
     *
     * Every internal rule -- festivals, Ekadashi, Samvat, Ritu -- keeps matching against the
     * Purnimanta [lunarMonthNumber] whatever this says, so switching the displayed calendar
     * cannot move a festival. Defaults to "" so existing callers need no change.
     */
    val amantaMonth: String = "",
    /**
     * Whether Pradosh Vrat is kept on this day.
     *
     * Decided by how much Trayodashi falls inside each day's Pradosh Kaal window rather than
     * by a tithi read at one instant, because the vrat is dated by the tithi *prevailing
     * during* dusk. A Trayodashi usually touches two consecutive windows and belongs to
     * whichever holds more of it.
     */
    val isPradoshVrat: Boolean = false
)

/**
 * One day reduced to just the limbs a date-scan needs.
 *
 * Deliberately far cheaper than a full [PanchangDay]: scanning a year for "when is the next
 * Ekadashi" means ~400 days, and a full computation each would mean muhurats, planet positions
 * and 96 ascendant calls per day for data the scan never reads. Everything here comes from four
 * native calls at sunrise.
 */
/**
 * The two tithi readings a calendar cell needs for one day.
 *
 * Most markers are Udaya Tithi observances and read [sunriseTithi]; Pradosh Vrat is dated by
 * dusk and reads [pradoshTithi]. Carrying both avoids a second scan of the month, and avoids
 * the calendar and the Quick Lookup section disagreeing about the same day.
 */
data class MonthDayTithis(
    val sunriseTithi: Int,
    /**
     * A tithi that began after this day's sunrise and ended before the next, so it reaches no
     * sunrise anywhere and [sunriseTithi] cannot show it — 0 when there is none.
     *
     * The calendar's Purnima and Amavasya badges need this or they silently skip a fortnight:
     * Purnima is lost this way on 23 Dec 2026, where the 23rd reads 29 and the 24th already
     * reads 1. The full or new moon still happens on the day that held it, which is this day.
     */
    val lostTithi: Int = 0,
    /**
     * Whether Pradosh Vrat is kept on this day.
     *
     * Decided by how much Trayodashi falls inside each day's Pradosh Kaal window rather than
     * by a tithi read at one instant, because the vrat is dated by the tithi *prevailing
     * during* dusk. A Trayodashi usually touches two consecutive windows and belongs to
     * whichever holds more of it.
     */
    val isPradoshVrat: Boolean
)

data class DailyPanchangSummary(
    val date: Date,
    val tithiNumber: Int,        // 1-30, at sunrise (Udaya Tithi)
    val nakshatraNumber: Int,    // 1-27
    val lunarMonth: Int,         // 1-12, Purnimanta
    val isAdhikMaas: Boolean,
    /**
     * Whether Pradosh Vrat is kept on this day.
     *
     * Decided by how much Trayodashi falls inside each day's Pradosh Kaal window rather than
     * by a tithi read at one instant, because the vrat is dated by the tithi *prevailing
     * during* dusk. A Trayodashi usually touches two consecutive windows and belongs to
     * whichever holds more of it.
     */
    val isPradoshVrat: Boolean = false
)
