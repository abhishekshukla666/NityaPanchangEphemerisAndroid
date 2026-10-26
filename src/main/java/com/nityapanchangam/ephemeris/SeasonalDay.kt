package com.nityapanchangam.ephemeris

import com.nityapanchangam.ephemeris.models.DailyPanchangSummary
import com.nityapanchangam.ephemeris.models.EkadashiTradition
import com.nityapanchangam.ephemeris.models.HinduFestival
import com.nityapanchangam.ephemeris.models.Paksha
import java.util.Calendar
import java.util.Date

// The season and the sacred calendar for one day, as a food guide reads them: the Ritu, the
// lunar month, whether it is Chaturmas, whether it is a vrat, and whether the Ritu is about to
// turn or has just turned (Ritu Sandhi). The same answers as the iOS library's SeasonalDay.swift.
//
// Built on the repository's own summaries and festivals rather than on new ephemeris calls, so it
// can never disagree with the dates the calendar screens already show: the Ekadashi here is the
// Ekadashi the festival list names, under the same tradition. The rules themselves are a plain
// function of those two lists ([SeasonalCalendar.resolve]), so they are tested on the JVM without
// the native library.

/**
 * The six seasons, each two consecutive lunar months — the pairing Charaka gives (Sutrasthana 6):
 * Chaitra and Vaishakha are Vasanta, and so on to Magha and Phalguna, which are Shishira.
 */
enum class Ritu {
    VASANTA, GRISHMA, VARSHA, SHARAD, HEMANTA, SHISHIRA;

    /**
     * The Ritu six months on. South of the equator the year's weather is reversed, so the season
     * a reader actually lives in is this one.
     */
    val southernHemisphere: Ritu get() = entries[(ordinal + 3) % 6]

    companion object {
        /**
         * The Ritu of a 1-12 lunar month number, Chaitra first. An Adhik month carries the number
         * of the month it precedes, and so that month's Ritu.
         */
        fun forLunarMonth(lunarMonth: Int): Ritu = entries[(((lunarMonth - 1) % 12 + 12) % 12) / 2]
    }
}

/**
 * Which month boundary a reader counts by: Purnimanta (the month ends at the full moon, as in the
 * north) or Amanta (at the new moon, as in the south, Gujarat and Maharashtra).
 */
enum class LunarMonthSystem { PURNIMANTA, AMANTA }

/** The vrats a food guide plans around. */
enum class VratDay {
    /** The day the Ekadashi is kept, under the chosen tradition. */
    EKADASHI,

    /** Chaitra or Sharad Navratri, from Pratipada to the Navami that closes it. */
    NAVRATRI,
}

data class SeasonalDay(
    /** By the month in the chosen system, and six months on south of the equator. */
    val ritu: Ritu,
    /** 1-12 in the chosen system. Not shifted for the hemisphere: a month is a count of moons. */
    val lunarMonth: Int,
    val isAdhikMaas: Boolean,
    /**
     * From Devshayani Ekadashi (Ashadha Shukla 11) to Devutthana (Prabodhini) Ekadashi (Kartika
     * Shukla 11), both days included, as the festival list dates them.
     */
    val isChaturmas: Boolean,
    val vrat: VratDay?,
    /**
     * The last seven days of a Ritu and the first seven of the next — the fortnight Ashtanga
     * Hridaya (Sutrasthana 3) asks the diet to change over.
     */
    val isRituSandhi: Boolean,
)

/**
 * The season and sacred calendar for the day containing [date].
 *
 * The Ritu follows the month in [system], so it always agrees with the month a reader is shown:
 * an Amanta reader's Ritu turns at the new moon, a Purnimanta reader's at the full moon.
 * ([LunarMonthSystem.PURNIMANTA] gives exactly the Ritu the Panchang app has always shown.)
 * Months and tithis are read at sunrise where the reader is; the vrat and Chaturmas dates come
 * from the festival list, which dates them nationally, as a printed calendar does.
 *
 * Null when the repository can't answer, such as a day it produced no summary for.
 */
suspend fun PanchangRepository.fetchSeasonalDay(
    date: Date,
    latitude: Double,
    longitude: Double,
    system: LunarMonthSystem,
    tradition: EkadashiTradition = EkadashiTradition.DEFAULT,
): SeasonalDay? {
    val day = SeasonalCalendar.startOfDay(date)
    // Ritu Sandhi needs the Ritu a week either side; Navratri runs up to ten days, so twelve
    // either side holds both its first day and its Navami whenever the day is inside one.
    val summaries = fetchDailySummaries(SeasonalCalendar.offset(day, -7), SeasonalCalendar.offset(day, 7),
                                        latitude, longitude)
    val festivals = fetchFestivals(SeasonalCalendar.offset(day, -12), SeasonalCalendar.offset(day, 12),
                                   latitude, longitude, tradition)
    return SeasonalCalendar.resolve(day, summaries, festivals, latitude, system)
}

/** The rules, as a plain function of the repository's two lists. */
internal object SeasonalCalendar {

    private val ekadashiNames: Set<String> =
        (PanchaangHelper.shuklaEkadashiNames + PanchaangHelper.krishnaEkadashiNames).toSet() +
            PanchaangHelper.ekadashiName(1, Paksha.SHUKLA, isAdhik = true)
    private val devshayani = PanchaangHelper.ekadashiName(4, Paksha.SHUKLA)
    private val devutthana = PanchaangHelper.ekadashiName(8, Paksha.SHUKLA)

    /** Each Navratri's first day and the Navami that closes it, by their names in the festival rules. */
    private val navratris = listOf("Navratri (Chaitra)" to "Ram Navami", "Navratri" to "Maha Navami")

    fun resolve(
        day: Date,
        summaries: List<DailyPanchangSummary>,
        festivals: List<HinduFestival>,
        latitude: Double,
        system: LunarMonthSystem,
    ): SeasonalDay? {
        fun summary(days: Int) = summaries.firstOrNull { sameDay(it.date, offset(day, days)) }
        fun month(s: DailyPanchangSummary): Pair<Int, Boolean>? = when (system) {
            LunarMonthSystem.PURNIMANTA -> s.lunarMonth to s.isAdhikMaas
            LunarMonthSystem.AMANTA -> if (s.amantaMonth == 0) null else s.amantaMonth to s.isAmantaAdhikMaas
        }
        fun ritu(s: DailyPanchangSummary): Ritu? = month(s)?.let { (number, _) ->
            Ritu.forLunarMonth(number).let { if (latitude < 0) it.southernHemisphere else it }
        }

        val today = summary(0) ?: return null
        val (monthNumber, adhik) = month(today) ?: return null
        val thisRitu = ritu(today) ?: return null
        // A Ritu lasts about fifty-nine days, so a week away differs exactly when a turn falls
        // within that week.
        val weekAgo = summary(-7)?.let(::ritu) ?: return null
        val weekOn = summary(7)?.let(::ritu) ?: return null

        fun dates(name: String) = festivals.filter { it.name == name }.map { startOfDay(it.date) }

        return SeasonalDay(
            ritu = thisRitu,
            lunarMonth = monthNumber,
            isAdhikMaas = adhik,
            isChaturmas = isChaturmas(day, today, ::dates),
            vrat = vrat(day, festivals, ::dates),
            isRituSandhi = weekAgo != thisRitu || weekOn != thisRitu,
        )
    }

    /**
     * Whether [day] falls in Chaturmas.
     *
     * Read from the Purnimanta month and tithi at sunrise, which settle every day but the few
     * around each end. There a vriddhi or kshaya Ekadashi can move the observed day off the
     * sunrise reading, so the answer comes from the festival list's own Devshayani and Devutthana
     * dates instead. Purnimanta tithis run 1-15 Krishna, 16-30 Shukla; Ekadashi Shukla is 26.
     */
    private fun isChaturmas(day: Date, s: DailyPanchangSummary, dates: (String) -> List<Date>): Boolean {
        val tithi = s.tithiNumber
        val adhik = s.isAdhikMaas // the Purnimanta reading, pairing with lunarMonth
        return when (s.lunarMonth) {
            4 -> when {
                adhik -> false // Adhik Ashadha comes before Devshayani
                tithi in 24..27 -> dates(devshayani).let { d -> d.any { !it.after(day) } || (d.isEmpty() && tithi >= 26) }
                else -> tithi >= 28 // the last Shukla days of Ashadha
            }
            5, 6, 7 -> true // Adhik or not, the heart of the four months
            8 -> when {
                adhik -> true // Adhik Kartika comes before the real one
                tithi in 25..28 -> dates(devutthana).let { d -> d.any { !it.before(day) } || (d.isEmpty() && tithi <= 26) }
                else -> tithi <= 24 // Krishna paksha and early Shukla of Kartika
            }
            else -> false
        }
    }

    private fun vrat(day: Date, festivals: List<HinduFestival>, dates: (String) -> List<Date>): VratDay? {
        if (festivals.any { it.name in ekadashiNames && sameDay(it.date, day) }) return VratDay.EKADASHI
        for ((first, navami) in navratris) {
            val start = dates(first).lastOrNull { !it.after(day) } ?: continue
            if (dates(navami).any { !it.before(day) && !it.before(start) }) return VratDay.NAVRATRI
        }
        return null
    }

    // Days in the device's time zone, as the repository counts them.
    fun startOfDay(date: Date): Date = Calendar.getInstance().apply {
        time = date
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.time

    fun offset(day: Date, days: Int): Date =
        Calendar.getInstance().apply { time = day; add(Calendar.DAY_OF_YEAR, days) }.time

    private fun sameDay(a: Date, b: Date): Boolean = startOfDay(a) == startOfDay(b)
}
