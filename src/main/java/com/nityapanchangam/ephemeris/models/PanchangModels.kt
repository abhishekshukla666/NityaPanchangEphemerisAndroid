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
    val degrees: Double,
    /**
     * Vakri — apparent backward motion against the zodiac.
     *
     * Defaulted so existing callers keep compiling; the ephemeris fills it from the body's
     * computed daily motion. The Sun and Moon are never retrograde; Rahu and Ketu always are.
     */
    val isRetrograde: Boolean = false
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

/**
 * One stretch of the day during which a limb holds a single value.
 *
 * [PanchangDay.nakshatra], [PanchangDay.yoga] and [PanchangDay.karana] are the *Udaya*
 * readings — taken at sunrise, which is what names the day and what every festival and vrat in
 * this library is dated by. They are correct and they must not change. But they are also a
 * snapshot, and a limb moves on during the day: a karana lasts about eleven hours, so by
 * mid-morning the sunrise reading is describing something that has already finished.
 *
 * These lists carry the whole day so a caller can show both — the reading that names the day,
 * and the one running at this moment. Deliberately periods rather than a "current" field: a
 * PanchangDay is fetched once and held, while now keeps moving, so anything baked in as
 * current would go stale in the hand. Hora, Lagna and Chaughariya are already shaped this way.
 */
/**
 * When a graha next changes sign — Rashi Parivartan.
 *
 * Its own type, and its own fetch, rather than a field on [PlanetPosition]: searching for all
 * nine costs about as much again as the whole panchang day it would ride along with, and every
 * screen in the app pays for a panchang while only one card ever asks for this.
 */
data class RashiChange(
    /** The graha, by the same ids [PlanetPosition] uses. */
    val planetId: Int,
    val date: Date,
    /**
     * The sign it moves into, 1-12 — and not always the next one up. A retrograde graha leaves
     * through the boundary behind it, which is how Rahu and Ketu always travel and how Venus
     * or Mercury sometimes do.
     */
    val toRashi: Int
)

data class LimbPeriod(
    val id: Int,
    /** The limb's name, already localized the same way the Udaya reading's is. */
    val name: String,
    val startTime: Date,
    val endTime: Date
) {
    operator fun contains(date: Date): Boolean = date >= startTime && date < endTime

    /** Convenience for views, matching [LagnaPeriod]. Prefer [contains] where the instant
     *  matters — a test, or a screen that pins a date. */
    val isActive: Boolean get() = Date() in this
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
    val isPradoshVrat: Boolean = false,
    /**
     * Every nakshatra, yoga, karana and Moon-sign touching this panchang day, sunrise to next
     * sunrise — see [LimbPeriod]. Two or three entries each for the first three; a karana is
     * about half a tithi, so three of them usually reach into one day.
     *
     * [rashis] is usually a single entry whose end falls a day or two out: a rashi is 30
     * degrees and the Moon covers about 13.2 a day, so it holds one sign for roughly two and a
     * quarter days. Unlike the others it often does not change during the day at all, and the
     * useful fact is when the Moon next moves on.
     *
     * Bounded by sunrise the way the hora and lagna lists are, so nothing is active between
     * midnight and sunrise: before sunrise the panchang day has not begun.
     *
     * Defaulted to empty so existing callers, previews and tests are unaffected.
     */
    val nakshatras: List<LimbPeriod> = emptyList(),
    val yogas: List<LimbPeriod> = emptyList(),
    val karanas: List<LimbPeriod> = emptyList(),
    val rashis: List<LimbPeriod> = emptyList(),
    /**
     * Uranus, Neptune and Pluto, in their own list rather than among the nine.
     *
     * No classical rule has a place for them — Vimshottari divides its hundred and twenty
     * years among nine lords, a hora belongs to one of the seven, these three rule no sign in
     * Parashari, and no text gives them an orb of combustion. Keeping them apart means code
     * that reasons about the Navagraha cannot pick them up by accident, and a screen that
     * wants to show them has to say so.
     *
     * A birth chart does not get them at all: a kundli is read by rules that cannot take them.
     */
    val outerPlanets: List<PlanetPosition> = emptyList()
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
    val isPradoshVrat: Boolean,
    /**
     * Whether Sankashti Chaturthi is kept on this day.
     *
     * Like [isPradoshVrat], and for the same kind of reason, this cannot be read off a sunrise
     * tithi: the vrat is dated by the tithi running at moonrise, since the fast is broken on
     * sighting the moon, and that is regularly a different day from the one Chaturthi reaches
     * at sunrise.
     */
    val isSankashtiChaturthi: Boolean = false
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
    val isPradoshVrat: Boolean = false,
    /**
     * Whether Sankashti Chaturthi is kept on this day.
     *
     * Like [isPradoshVrat], and for the same kind of reason, this cannot be read off a sunrise
     * tithi: the vrat is dated by the tithi running at moonrise, since the fast is broken on
     * sighting the moon, and that is regularly a different day from the one Chaturthi reaches
     * at sunrise.
     */
    val isSankashtiChaturthi: Boolean = false
)
