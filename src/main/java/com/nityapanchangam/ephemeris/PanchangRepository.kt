package com.nityapanchangam.ephemeris

import android.content.Context
import com.nityapanchangam.ephemeris.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import com.nityapanchangam.ephemeris.PanchaangHelper.localized
import com.nityapanchangam.ephemeris.PanchaangHelper.localizedFormat

/**
 * Panchang computations backed by the Swiss Ephemeris C library.
 *
 * Thread-safety: Swiss Ephemeris keeps ALL of its state — open ephemeris file handles,
 * segment caches, the sidereal/ayanamsha mode — in one process-global struct (`swed`) that
 * is not reentrant. Because the heavy work here runs on [Dispatchers.Default], which is a
 * *multi-threaded* pool, two concurrent calls (say several calendar months loading at once
 * while the user flings the month pager) would otherwise race on that shared state: one call
 * can free a segment buffer another is mid-read of, yielding wrong values, empty results, or
 * a native crash inside `get_new_segment`.
 *
 * Every entry point therefore funnels through [ephemerisMutex], which is deliberately
 * `companion`-scoped rather than per-instance: giving each repository its own lock would not
 * help, since the state being guarded is global to the process, and apps routinely build a
 * repository per screen/worker. This mirrors the iOS package, which serializes on a single
 * shared queue for exactly the same reason.
 *
 * None of the public methods call one another, so the non-reentrant mutex cannot self-deadlock.
 */
class PanchangRepository(private val context: Context, private val wrapper: SwissEphWrapper) {

    private companion object {
        val ephemerisMutex = Mutex()

        // Pradosh Kaal (Diwali, Holika Dahan) and Aparahna (Dussehra) windows are computed
        // from real sunrise/sunset, but for a fixed reference point — Ujjain, the traditional
        // reference meridian for Indian panchangs — rather than the user's live location.
        // These dates are meant to be one nationally-agreed day, the way a printed calendar
        // publishes them, not something that shifts with the viewer's GPS the way a personal
        // Muhurat rightly does.
        const val REFERENCE_LATITUDE = 23.1765
        const val REFERENCE_LONGITUDE = 75.7885
    }

    /**
     * Runs [block] on the computation dispatcher with exclusive access to the ephemeris.
     * The lock is taken before dispatching so callers waiting their turn simply suspend
     * rather than occupying a pool thread.
     */
    private suspend fun <T> ephemerisCall(block: suspend () -> T): T =
        ephemerisMutex.withLock { withContext(Dispatchers.Default) { block() } }

    suspend fun fetchPanchang(date: Date, latitude: Double, longitude: Double): PanchangDay = ephemerisCall {
        val dayStart = getStartOfDay(date)
        val jdDayStart = dateToJD(dayStart)

        // Sunrise must be computed first
        val sunData = wrapper.calculateSunriseSunset(jdDayStart, latitude, longitude)
        val sunriseJD = sunData["sunriseJD"] ?: 0.0
        val sunsetJD = sunData["sunsetJD"] ?: 0.0
        val moonriseJD = sunData["moonriseJD"] ?: 0.0
        val moonsetJD = sunData["moonsetJD"] ?: 0.0

        val moonrise = if (moonriseJD > 2400000) jdToDate(moonriseJD) else null
        val moonset = if (moonsetJD > 2400000) jdToDate(moonsetJD) else null

        val refJD = if (sunriseJD > 2400000) sunriseJD else jdDayStart
        val raw = wrapper.calculateTithiForJulianDay(refJD)

        val tithiEndJD = raw["tithiEndJD"] ?: 0.0
        val tithiNum = (raw["tithiNumber"] ?: 1.0).toInt()
        val nakshatraNum = (raw["nakshatraNumber"] ?: 1.0).toInt()
        val yogaNum = (raw["yogaNumber"] ?: 1.0).toInt()
        val karanaNum = (raw["karanaNumber"] ?: 1.0).toInt()

        val tithiEnd = jdToDate(tithiEndJD)
        val nakshatraEnd = jdToDate(wrapper.calculateNakshatraEndTimeForJulianDay(refJD))
        val yogaEnd = jdToDate(wrapper.calculateYogaEndTimeForJulianDay(refJD))

        val rashiNum = wrapper.calculateMoonRashiForJulianDay(refJD)
        val moonRashi = "${PanchaangHelper.getRashiSymbol(rashiNum)} ${PanchaangHelper.getMoonRashiName(context, rashiNum)}"

        val isAdhik = wrapper.calculateIsPurnimantaAdhikMaasForJulianDay(refJD)
        val monthNum = wrapper.calculatePurnimantaMonthForJulianDay(refJD)
        val monthName = PanchaangHelper.getLunarMonthName(context, monthNum, isAdhik)

        // Amanta -- a display-only parallel to the Purnimanta name above. Everything that
        // matches on a month (festivals, Ekadashi, Samvat, Ritu) keeps using monthNum, so
        // which of the two the user reads cannot move a date.
        val amantaIsAdhik = wrapper.calculateIsAdhikMaasForJulianDay(refJD)
        val amantaMonthName = PanchaangHelper.getLunarMonthName(
            context, wrapper.calculateAmantaMonthForJulianDay(refJD), amantaIsAdhik
        )

        val weekday = Calendar.getInstance().apply { time = dayStart }.get(Calendar.DAY_OF_WEEK)
        val mData = wrapper.calculateMuhurats(sunriseJD, sunsetJD, weekday)

        fun jdTime(key: String): Date = jdToDate(mData[key] ?: 0.0)

        val muhurats = listOf(
            Muhurat("1", context.localized("muhurat_brahma", "Brahma Muhurat"), jdTime("brahmaStart"), jdTime("brahmaEnd"), MuhuratType.AUSPICIOUS),
            Muhurat("2", context.localized("muhurat_amrit", "Amrit Kaal"), jdTime("amritStart"), jdTime("amritEnd"), MuhuratType.AUSPICIOUS),
            Muhurat("3", context.localized("muhurat_abhijit", "Abhijit Muhurat"), jdTime("abhijitStart"), jdTime("abhijitEnd"), MuhuratType.AUSPICIOUS),
            Muhurat("4", context.localized("muhurat_vijaya", "Vijaya Muhurat"), jdTime("vijayaStart"), jdTime("vijayaEnd"), MuhuratType.AUSPICIOUS),
            Muhurat("5", context.localized("muhurat_godhuli", "Godhuli Muhurat"), jdTime("godhuliStart"), jdTime("godhuliEnd"), MuhuratType.NEUTRAL),
            Muhurat("6", context.localized("muhurat_rahu", "Rahu Kaal"), jdTime("rahuStart"), jdTime("rahuEnd"), MuhuratType.INAUSPICIOUS),
            Muhurat("7", context.localized("muhurat_yamaganda", "Yamaganda"), jdTime("yamaStart"), jdTime("yamaEnd"), MuhuratType.INAUSPICIOUS),
            Muhurat("8", context.localized("muhurat_gulik", "Gulik Kaal"), jdTime("gulikStart"), jdTime("gulikEnd"), MuhuratType.NEUTRAL)
        ).sortedBy { it.startTime }

        val rawPlanets = wrapper.calculatePlanetPositionsForJulianDay(refJD)
        val planetPositions = PanchaangHelper.buildPlanetPositions(context, rawPlanets)

        val sunRashiNum = planetPositions.find { it.id == 0 }?.rashiNumber ?: 1
        val isUttarayana = sunRashiNum <= 3 || sunRashiNum >= 10
        val vedaAyana = if (isUttarayana) context.localized("ayana_uttarayana", "Uttarayana") else context.localized("ayana_dakshinayana", "Dakshinayana")

        val raviYoga = listOf(
            listOf(3, 12, 21), // Sun
            listOf(4, 13, 22), // Mon
            listOf(5, 14, 23), // Tue
            listOf(9, 18, 27), // Wed
            listOf(7, 16, 25), // Thu
            listOf(2, 11, 20), // Fri
            listOf(8, 17, 26)  // Sat
        )[weekday - 1].contains(nakshatraNum)

        val chaughariyaNames = listOf(
            context.localized("chaugh_udveg", "Udveg"),
            context.localized("chaugh_char", "Char"),
            context.localized("chaugh_labh", "Labh"),
            context.localized("chaugh_amrit", "Amrit"),
            context.localized("chaugh_kaal", "Kaal"),
            context.localized("chaugh_shubh", "Shubh"),
            context.localized("chaugh_rog", "Rog")
        )
        val chaughariyaTypes = listOf(MuhuratType.INAUSPICIOUS, MuhuratType.NEUTRAL, MuhuratType.AUSPICIOUS, MuhuratType.AUSPICIOUS, MuhuratType.INAUSPICIOUS, MuhuratType.AUSPICIOUS, MuhuratType.INAUSPICIOUS)

        val dayIdx = listOf(0, 3, 6, 2, 5, 1, 4)[weekday - 1]
        val daySegLen = (sunsetJD - sunriseJD) / 8.0
        val chaughariya = (0 until 8).map { i ->
            val nameIdx = (dayIdx + i) % 7
            Muhurat("$i", chaughariyaNames[nameIdx], jdToDate(sunriseJD + i * daySegLen), jdToDate(sunriseJD + (i + 1) * daySegLen), chaughariyaTypes[nameIdx])
        }

        val nextDayStart = Calendar.getInstance().apply {
            time = dayStart
            add(Calendar.DAY_OF_YEAR, 1)
        }.time
        val nextSunData = wrapper.calculateSunriseSunset(dateToJD(nextDayStart), latitude, longitude)
        val nextSunriseJD = nextSunData["sunriseJD"] ?: (sunriseJD + 1.0)

        // Pradosh Vrat. Reuses nextSunriseJD above to close tonight's window; the two
        // neighbouring days are only fetched when tonight actually holds some Trayodashi,
        // which is a handful of days a month rather than every call to this hot path.
        val ownPradoshOverlap = trayodashiMinutesInPradosh(sunsetJD, nextSunriseJD)
        val isPradoshVratDay = ownPradoshOverlap > 0 && isPradoshDay(
            own = ownPradoshOverlap,
            previous = pradoshOverlapOn(jdDayStart - 1.0, latitude, longitude),
            next = pradoshOverlapOn(jdDayStart + 1.0, latitude, longitude)
        )

        val nightIdx = listOf(5, 1, 4, 0, 3, 6, 2)[weekday - 1]
        val nightSegLen = max(nextSunriseJD - sunsetJD, 1.0 / 1440.0) / 8.0
        val nightChaughariya = (0 until 8).map { i ->
            val nameIdx = (nightIdx + i) % 7
            Muhurat("$i", chaughariyaNames[nameIdx], jdToDate(sunsetJD + i * nightSegLen), jdToDate(sunsetJD + (i + 1) * nightSegLen), chaughariyaTypes[nameIdx])
        }

        val horas = computeHoras(sunriseJD, sunsetJD, nextSunriseJD, weekday)
        val lagnas = computeLagnas(sunriseJD, sunsetJD, nextSunriseJD, latitude, longitude)

        PanchangDay(
            date = date,
            lunarMonth = monthName,
            amantaMonth = amantaMonthName,
            isPradoshVrat = isPradoshVratDay,
            lunarMonthNumber = monthNum,
            isAdhikMaas = isAdhik,
            sunrise = jdToDate(sunriseJD),
            sunset = jdToDate(sunsetJD),
            moonrise = moonrise,
            moonset = moonset,
            tithi = Tithi(PanchaangHelper.getTithiName(context, tithiNum), tithiEnd, if (tithiNum <= 15) Paksha.KRISHNA else Paksha.SHUKLA),
            tithiNumber = tithiNum,
            nakshatra = Nakshatra(PanchaangHelper.getNakshatraName(context, nakshatraNum), nakshatraEnd),
            nakshatraNumber = nakshatraNum,
            yoga = MinorLimb(PanchaangHelper.getYogaName(context, yogaNum), yogaEnd),
            karana = MinorLimb(PanchaangHelper.getKaranaName(context, karanaNum), null),
            vara = PanchaangHelper.getVaraName(context, dayStart),
            moonRashi = moonRashi,
            muhurats = muhurats,
            chaughariya = chaughariya,
            nightChaughariya = nightChaughariya,
            planetPositions = planetPositions,
            vedaAyana = vedaAyana,
            isUttarayana = isUttarayana,
            raviYoga = raviYoga,
            horas = horas,
            lagnas = lagnas,
            bhadraKaal = computeBhadraKaal(sunriseJD, nextSunriseJD)
        )
    }

    /**
     * Scans the calendar day (sunrise to next sunrise) for a Vishti (Bhadra) karana window —
     * the classically inauspicious half-tithi period most people meet through the "don't tie a
     * Rakhi during Bhadra" rule.
     *
     * A karana already under way at sunrise is walked backward to its true start rather than
     * clipped to sunrise: a warning needs an accurate start time to be useful, not just "some
     * time before now".
     */
    private fun computeBhadraKaal(sunriseJD: Double, nextSunriseJD: Double): Muhurat? {
        val step = 15.0 / 1440.0
        var searchJD = sunriseJD

        while (searchJD < nextSunriseJD) {
            val karanaNum = wrapper.calculateKaranaForJulianDay(searchJD)
            val endJD = wrapper.calculateKaranaEndTimeForJulianDay(searchJD)
            // Vishti sits at index 6 of the 7-karana movable cycle (numbers 2-57). The four
            // fixed karanas (1 and 58-60) can never match, which the range check enforces.
            val isVishti = karanaNum in 2..57 && (karanaNum - 2) % 7 == 6

            if (isVishti) {
                var startJD = searchJD
                while (startJD - step >= sunriseJD - 0.833 &&
                    wrapper.calculateKaranaForJulianDay(startJD - step) == karanaNum
                ) {
                    startJD -= step
                }
                return Muhurat(
                    id = "bhadra",
                    name = context.localized("bhadra_kaal", "Bhadra Kaal"),
                    startTime = jdToDate(startJD),
                    endTime = jdToDate(minOf(endJD, nextSunriseJD)),
                    type = MuhuratType.INAUSPICIOUS
                )
            }
            searchJD = endJD
        }
        return null
    }

    suspend fun fetchMonthTithis(
        year: Int,
        month: Int,
        latitude: Double,
        longitude: Double
    ): Map<Int, MonthDayTithis> = ephemerisCall {
        val calendar = Calendar.getInstance()
        calendar.set(year, month - 1, 1, 0, 0, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

        // Sunrise and sunset for every day of the month plus the first of the next, gathered
        // once. Pradosh Kaal needs the FOLLOWING day's sunrise to close the night, and
        // fetching that separately per day would double the ephemeris calls for this scan.
        val sunrises = DoubleArray(daysInMonth + 2)
        val sunsets = DoubleArray(daysInMonth + 2)
        for (day in 1..daysInMonth + 1) {
            val c = (calendar.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, day - 1) }
            val jd = dateToJD(c.time)
            val sun = wrapper.calculateSunriseSunset(jd, latitude, longitude)
            // Sunrise legitimately does not exist on some polar days; fall back to the day
            // itself so the scan still yields a row rather than dropping the date.
            sunrises[day] = (sun["sunriseJD"] ?: 0.0).let { if (it > 2400000) it else jd }
            sunsets[day] = (sun["sunsetJD"] ?: 0.0).let { if (it > 2400000) it else jd + 0.5 }
        }

        // Overlap for every day plus the two the month's edges compare against, so the
        // 1st and the last can be judged against neighbours outside the month.
        val overlap = IntArray(daysInMonth + 3)
        for (day in 0..daysInMonth + 1) {
            val c = (calendar.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, day - 1) }
            overlap[day + 1] = pradoshOverlapOn(dateToJD(c.time), latitude, longitude)
        }

        val results = mutableMapOf<Int, MonthDayTithis>()
        for (day in 1..daysInMonth) {
            results[day] = MonthDayTithis(
                sunriseTithi = wrapper.calculateTithiNumberForJulianDay(sunrises[day]),
                isPradoshVrat = isPradoshDay(overlap[day + 1], overlap[day], overlap[day + 2])
            )
        }
        results
    }

    /**
     * Every day in the range reduced to the limbs a date-scan needs, evaluated at each day's
     * own sunrise — the same reference instant fetchPanchang and the festival scan use, so a
     * tithi reported here is the tithi those agree the day carries.
     *
     * One ephemerisCall for the whole range rather than one per day: the lock is uncontended
     * for the duration either way, and taking it 400 times would add 400 dispatches for no gain.
     */
    suspend fun fetchDailySummaries(
        startDate: Date,
        endDate: Date,
        latitude: Double,
        longitude: Double
    ): List<DailyPanchangSummary> = ephemerisCall {
        val results = mutableListOf<DailyPanchangSummary>()
        val cursor = Calendar.getInstance().apply { time = getStartOfDay(startDate) }
        val end = getStartOfDay(endDate).time

        while (cursor.time.time <= end) {
            val dayStart = cursor.time
            val jdDayStart = dateToJD(dayStart)
            val sunriseJD = wrapper.calculateSunriseSunset(jdDayStart, latitude, longitude)["sunriseJD"] ?: 0.0
            // Falls back to 6am local when sunrise cannot be resolved — above the Arctic circle
            // it legitimately does not exist on some days, and the scan must still produce a row.
            val refJD = if (sunriseJD > 2400000) sunriseJD else jdDayStart + (6.0 / 24.0)

            // Pradosh Vrat, decided at the caller's own location rather than the fixed
            // Ujjain reference the festival rules use: a vrat is kept where the observer is.
            val ownOverlap = pradoshOverlapOn(jdDayStart, latitude, longitude)
            val isPradoshVratDay = ownOverlap > 0 && isPradoshDay(
                own = ownOverlap,
                previous = pradoshOverlapOn(jdDayStart - 1.0, latitude, longitude),
                next = pradoshOverlapOn(jdDayStart + 1.0, latitude, longitude)
            )

            results.add(
                DailyPanchangSummary(
                    date = dayStart,
                    tithiNumber = wrapper.calculateTithiNumberForJulianDay(refJD),
                    nakshatraNumber = wrapper.calculateNakshatraForJulianDay(refJD),
                    lunarMonth = wrapper.calculatePurnimantaMonthForJulianDay(refJD),
                    isAdhikMaas = wrapper.calculateIsPurnimantaAdhikMaasForJulianDay(refJD),
                    isPradoshVrat = isPradoshVratDay
                )
            )
            cursor.add(Calendar.DAY_OF_YEAR, 1)
        }
        results
    }

    suspend fun fetchFestivals(startDate: Date, endDate: Date, latitude: Double, longitude: Double): List<HinduFestival> = ephemerisCall {
        val calendar = Calendar.getInstance()
        val festivals = mutableListOf<HinduFestival>()
        val seen = mutableSetOf<String>()
        var current = getStartOfDay(startDate)

        val end = getStartOfDay(endDate)

        while (current.time <= end.time) {
            val calYear = Calendar.getInstance().apply { time = current }.get(Calendar.YEAR)
            val dayStartJD = dateToJD(current)

            // 1. Always compute the sunrise metrics — every rule needs them, either as its own
            //    anchor or as the proximity check below.
            val jdSunrise = dayStartJD + (6.0 / 24.0)
            val tithiSunrise = wrapper.calculateTithiNumberForJulianDay(jdSunrise)
            val monthSunrise = wrapper.calculatePurnimantaMonthForJulianDay(jdSunrise)
            val isAdhikSunrise = wrapper.calculateIsPurnimantaAdhikMaasForJulianDay(jdSunrise)

            // 2. One lazy cache per non-sunrise instant, filled the first time a rule that day
            //    actually needs it. Most days no rule does, so most days pay nothing.
            var midnight: DayAnchor? = null
            var pradosh: DayAnchor? = null
            var aparahna: DayAnchor? = null

            for (rule in allFestivalRules) {
                // Proximity short-circuit shared by every non-sunrise instant: if the sunrise
                // tithi is nowhere near the rule's target, the real instant — all within about
                // a day of sunrise — cannot be either. The >= 28 arm handles the wrap from
                // Amavasya back to Pratipada.
                fun nearSunrise(): Boolean {
                    val diff = abs(tithiSunrise - rule.tithiNumber)
                    return diff <= 2 || diff >= 28
                }

                val anchor: DayAnchor = when (rule.observationTime) {
                    ObservationTime.SUNRISE ->
                        DayAnchor(tithiSunrise, monthSunrise, isAdhikSunrise)

                    ObservationTime.MIDNIGHT -> {
                        if (!nearSunrise()) continue
                        if (midnight == null) {
                            midnight = anchorAt(dayStartJD + (23.0 * 60 + 59) / 1440.0)
                        }
                        midnight
                    }

                    ObservationTime.PRADOSH_KAAL -> {
                        if (!nearSunrise()) continue
                        if (pradosh == null) {
                            val sun = wrapper.calculateSunriseSunset(dayStartJD, REFERENCE_LATITUDE, REFERENCE_LONGITUDE)
                            val sunsetJD = sun["sunsetJD"] ?: jdSunrise
                            val nextSun = wrapper.calculateSunriseSunset(dayStartJD + 1.0, REFERENCE_LATITUDE, REFERENCE_LONGITUDE)
                            val nextSunriseJD = nextSun["sunriseJD"] ?: (sunsetJD + 0.5)
                            // First fifth of the night (sunset -> next sunrise), sampled at its midpoint.
                            val nightLen = max(nextSunriseJD - sunsetJD, 1.0 / 1440.0)
                            pradosh = anchorAt(sunsetJD + nightLen / 10.0)
                        }
                        pradosh
                    }

                    ObservationTime.APARAHNA -> {
                        if (!nearSunrise()) continue
                        if (aparahna == null) {
                            val sun = wrapper.calculateSunriseSunset(dayStartJD, REFERENCE_LATITUDE, REFERENCE_LONGITUDE)
                            val sunriseRefJD = sun["sunriseJD"] ?: jdSunrise
                            val sunsetRefJD = sun["sunsetJD"] ?: (sunriseRefJD + 0.5)
                            // Aparahna is the FOURTH of five equal divisions of daylight
                            // (Pratahkal, Sangava, Madhyahna, Aparahna, Sayahna), sampled at
                            // its midpoint -- 3.5/5, not 2.5/5, which was Madhyahna's.
                            val dayLen = max(sunsetRefJD - sunriseRefJD, 1.0 / 1440.0)
                            aparahna = anchorAt(sunriseRefJD + dayLen * 3.5 / 5.0)
                        }
                        aparahna
                    }
                } ?: continue

                if (anchor.isAdhik || anchor.month !in 1..12) continue

                if (rule.lunarMonth == anchor.month && rule.tithiNumber == anchor.tithi) {
                    val key = "${rule.name}-$calYear"
                    if (seen.add(key)) {
                        festivals.add(HinduFestival(rule.name, current, rule.emoji, rule.hasIcon))
                    }
                }
            }

            // Static rules
            val cal = Calendar.getInstance().apply { time = current }
            val gMonth = cal.get(Calendar.MONTH) + 1
            val gDay = cal.get(Calendar.DAY_OF_MONTH)
            for (rule in allStaticFestivalRules) {
                if (rule.month == gMonth && rule.day == gDay) {
                    val key = "${rule.name}-$calYear"
                    if (seen.add(key)) {
                        festivals.add(HinduFestival(rule.name, current, rule.emoji, rule.hasIcon))
                    }
                }
            }

            calendar.time = current
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            current = calendar.time
        }

        festivals.addAll(kshayaFallbackFestivals(startDate, endDate, seen))
        festivals.addAll(holiFestivals(startDate, endDate, seen))
        festivals.sortedBy { it.date }
    }

    /** Tithi, Purnimanta month and Adhik flag as they stand at one instant. */

    /**
     * Holika Dahan and Holi, which the festival table cannot express.
     *
     * Holika Dahan is lit in Pradosh Kaal on the day Phalguna Purnima prevails there, but
     * Bhadra must be avoided. If Bhadra ends before midnight the bonfire is lit later that
     * same night; if it runs past midnight the observance defers to the next day. Holi is
     * then simply the day after, whichever day that turned out to be — which is why it is not
     * a fixed tithi either: in 2024 and 2025 it fell on the Purnima sunrise day, in 2023 and
     * 2026 on Chaitra Krishna Pratipada.
     *
     * Verified against the ephemeris for 2023-2026, where the Bhadra end time discriminates
     * the deferred years from the rest exactly:
     *
     *   2023  Bhadra ends 05:17 next morning  -> deferred  -> 7 Mar
     *   2024  Bhadra ends 23:14 same night    -> same day  -> 24 Mar
     *   2025  Bhadra ends 23:28 same night    -> same day  -> 13 Mar
     *   2026  Bhadra ends 05:29 next morning  -> deferred  -> 3 Mar
     *
     * Dated at the Ujjain reference like the other Pradosh and Aparahna rules, since these
     * are nationally agreed dates rather than personal observances.
     */
    private fun holiFestivals(startDate: Date, endDate: Date, seen: MutableSet<String>): List<HinduFestival> {
        val out = mutableListOf<HinduFestival>()
        // A day either side: Holika Dahan can defer forward out of the window, and Holi is a
        // further day on, so the Purnima that produces them may sit just before the start.
        val cursor = Calendar.getInstance().apply {
            time = getStartOfDay(startDate); add(Calendar.DAY_OF_YEAR, -2)
        }
        val scanEnd = Calendar.getInstance().apply {
            time = getStartOfDay(endDate); add(Calendar.DAY_OF_YEAR, 2)
        }.time

        while (cursor.time.time <= scanEnd.time) {
            val dayStart = getStartOfDay(cursor.time)
            val jdDayStart = dateToJD(dayStart)
            val sun = wrapper.calculateSunriseSunset(jdDayStart, REFERENCE_LATITUDE, REFERENCE_LONGITUDE)
            val sunsetJD = sun["sunsetJD"]
            val nextSunriseJD = wrapper
                .calculateSunriseSunset(jdDayStart + 1.0, REFERENCE_LATITUDE, REFERENCE_LONGITUDE)["sunriseJD"]

            if (sunsetJD != null && nextSunriseJD != null) {
                val nightLen = max(nextSunriseJD - sunsetJD, 1.0 / 1440.0)
                val pradoshJD = sunsetJD + nightLen / 10.0
                val anchor = anchorAt(pradoshJD)

                // Phalguna Purnima at Pradosh: the one day a year this can fire.
                if (anchor.tithi == 30 && anchor.month == 12 && !anchor.isAdhik) {
                    val bhadraEnds = bhadraEndAfter(sunsetJD, sunsetJD + nightLen / 5.0)
                    val nextMidnight = Calendar.getInstance().apply {
                        time = dayStart; add(Calendar.DAY_OF_YEAR, 1)
                    }.time
                    val defer = bhadraEnds != null && !bhadraEnds.before(nextMidnight)

                    val dahan = if (defer) nextMidnight else dayStart
                    val holi = Calendar.getInstance().apply {
                        time = dahan; add(Calendar.DAY_OF_YEAR, 1)
                    }.time
                    val year = Calendar.getInstance().apply { time = dahan }.get(Calendar.YEAR)

                    if (seen.add("Holika Dahan-$year")) {
                        out.add(HinduFestival("Holika Dahan", dahan, "🔥", false))
                    }
                    if (seen.add("Holi-$year")) {
                        out.add(HinduFestival("Holi", holi, "holi", true))
                    }
                }
            }
            cursor.add(Calendar.DAY_OF_YEAR, 1)
        }
        return out.filter { it.date >= getStartOfDay(startDate) && it.date <= getStartOfDay(endDate) }
    }

    /**
     * End of the Bhadra window overlapping [windowStart]..[windowEnd], or null when none does.
     *
     * Only the end matters here: whether it lands before or after midnight is what decides
     * the deferral, and a Bhadra that never touches Pradosh cannot block the bonfire at all.
     */
    private fun bhadraEndAfter(windowStart: Double, windowEnd: Double): Date? {
        val step = 15.0 / 1440.0
        var t = windowStart
        var found = false
        while (t <= windowEnd) {
            if (isVishti(t)) { found = true; break }
            t += step
        }
        if (!found) return null
        // Walk to the end of this karana. Bounded: a karana runs well under a day.
        var end = t
        while (isVishti(end) && end < t + 1.0) end += step
        return jdToDate(end)
    }

    private fun isVishti(jd: Double): Boolean {
        val k = wrapper.calculateKaranaForJulianDay(jd)
        return k in 2..57 && (k - 2) % 7 == 6
    }


    /**
     * Minutes of Trayodashi falling inside this day's Pradosh Kaal window.
     *
     * Pradosh Vrat is kept on the day Trayodashi *prevails during* Pradosh Kaal, which is an
     * overlap, not a reading at an instant. Sampling one point inside the window — the
     * midpoint, as this used to — silently misses a Trayodashi that covers only part of it.
     * On 1 Mar 2026 Trayodashi ran 28 Feb 20:44 to 1 Mar 19:10 while the midpoint sample sat
     * at 19:44, so it was missed on both days and the vrat disappeared from that fortnight.
     */
    private fun trayodashiMinutesInPradosh(sunsetJD: Double, nextSunriseJD: Double): Int {
        val nightLen = max(nextSunriseJD - sunsetJD, 1.0 / 1440.0)
        val windowEnd = sunsetJD + nightLen / 5.0
        val step = 1.0 / 1440.0
        var minutes = 0
        var t = sunsetJD
        while (t < windowEnd) {
            val tithi = wrapper.calculateTithiNumberForJulianDay(t)
            if (tithi == 13 || tithi == 28) minutes++
            t += step
        }
        return minutes
    }

    /** [trayodashiMinutesInPradosh] for the day starting at [jdDayStart]. */
    private fun pradoshOverlapOn(jdDayStart: Double, latitude: Double, longitude: Double): Int {
        val sunsetJD = wrapper.calculateSunriseSunset(jdDayStart, latitude, longitude)["sunsetJD"]
            ?: return 0
        val nextSunriseJD = wrapper
            .calculateSunriseSunset(jdDayStart + 1.0, latitude, longitude)["sunriseJD"]
            ?: return 0
        return trayodashiMinutesInPradosh(sunsetJD, nextSunriseJD)
    }

    /**
     * Whether this is the day to keep Pradosh Vrat.
     *
     * A Trayodashi usually touches two consecutive Pradosh windows; the vrat belongs to the
     * one holding more of it, ties going to the later day. Checked across 2020-2043 this
     * selects exactly one day per Trayodashi — 593 for 593 — so unlike a point sample it can
     * neither lose a fortnight nor claim two days for one vrat.
     */
    private fun isPradoshDay(own: Int, previous: Int, next: Int): Boolean =
        own > 0 && own >= previous && own > next

    private data class DayAnchor(val tithi: Int, val month: Int, val isAdhik: Boolean)

    private fun anchorAt(jd: Double): DayAnchor = DayAnchor(
        wrapper.calculateTithiNumberForJulianDay(jd),
        wrapper.calculatePurnimantaMonthForJulianDay(jd),
        wrapper.calculateIsPurnimantaAdhikMaasForJulianDay(jd)
    )

    /**
     * A tithi is "kshaya" (lost) when it starts after one sunrise and ends before the next — it
     * never touches ANY sunrise, so the loop above, which only asks "what tithi is it AT
     * sunrise", never finds it and a festival pinned to that tithi silently never fires.
     *
     * The exact start/end times are not needed: a kshaya tithi is by definition contained in
     * exactly one sunrise-to-next-sunrise window — the window whose sunrise tithi is followed,
     * at the very next sunrise, by a number more than one higher. That gap identifies both
     * which tithi was skipped and which day held it, with no tie-break required.
     *
     * Scoped to SUNRISE rules only, as on iOS. Midnight, Pradosh and Aparahna rules have their
     * own anchor instants and would need their own gap tracking to fix correctly.
     */
    private fun kshayaFallbackFestivals(startDate: Date, endDate: Date, seen: MutableSet<String>): List<HinduFestival> {
        val windowStart = getStartOfDay(startDate)
        val windowEnd = getStartOfDay(endDate)

        // One extra day each side, so a kshaya tithi sitting at the window's edge is still
        // caught: the pair that reveals it may straddle the boundary even though the day it
        // belongs to is inside.
        val cursor = Calendar.getInstance().apply {
            time = windowStart
            add(Calendar.DAY_OF_YEAR, -1)
        }
        val scanEnd = Calendar.getInstance().apply {
            time = windowEnd
            add(Calendar.DAY_OF_YEAR, 1)
        }.time

        val fallback = mutableListOf<HinduFestival>()
        var previous: Triple<Date, DayAnchor, Int>? = null   // date, anchor, calendar year

        while (cursor.time.time <= scanEnd.time) {
            val dayStart = getStartOfDay(cursor.time)
            val anchor = anchorAt(dateToJD(dayStart) + (6.0 / 24.0))
            val year = Calendar.getInstance().apply { time = dayStart }.get(Calendar.YEAR)

            val prev = previous
            if (prev != null && !prev.second.isAdhik && !anchor.isAdhik &&
                prev.first.time >= windowStart.time && prev.first.time <= windowEnd.time
            ) {
                // Tithis skipped between the two sunrises. A real kshaya skips one, very rarely
                // two; anything larger (29, from a same-tithi repeat on a vriddhi day) is not a
                // kshaya and must not be treated as one.
                val gap = (((anchor.tithi - prev.second.tithi - 1) % 30) + 30) % 30
                if (gap in 1..2) {
                    for (offset in 1..gap) {
                        val skipped = ((prev.second.tithi - 1 + offset) % 30) + 1
                        // Tithi 1 always opens the new lunar month, so a skipped Pratipada
                        // belongs to the day AFTER the gap, not before.
                        val skippedMonth = if (skipped == 1) anchor.month else prev.second.month
                        if (skippedMonth !in 1..12) continue

                        for (rule in allFestivalRules) {
                            if (rule.observationTime != ObservationTime.SUNRISE) continue
                            if (rule.tithiNumber != skipped || rule.lunarMonth != skippedMonth) continue
                            val key = "${rule.name}-${prev.third}"
                            if (seen.add(key)) {
                                fallback.add(HinduFestival(rule.name, prev.first, rule.emoji, rule.hasIcon))
                            }
                        }
                    }
                }
            }

            previous = Triple(dayStart, anchor, year)
            cursor.add(Calendar.DAY_OF_YEAR, 1)
        }
        return fallback
    }

    suspend fun fetchBirthChart(date: Date, latitude: Double, longitude: Double): BirthChart = ephemerisCall {
        val jd = dateToJD(date)

        val nakshatraNum = wrapper.calculateNakshatraForJulianDay(jd)
        val rashiNum = wrapper.calculateMoonRashiForJulianDay(jd)
        val lagnaLongitude = wrapper.calculateAscendantAtJD(jd, latitude, longitude)
        val lagnaRashi = (lagnaLongitude / 30.0).toInt() + 1

        val rawPlanets = wrapper.calculatePlanetPositionsForJulianDay(jd)
        val planetPositions = PanchaangHelper.buildPlanetPositions(context, rawPlanets)
        val moonLongitude = planetPositions.find { it.id == 1 }?.longitude ?: 0.0
        val marsRashi = planetPositions.find { it.id == 2 }?.rashiNumber ?: 1

        // Pada: each nakshatra spans 13°20' (13.3333°), split into 4 padas of 3°20' each.
        val nakshatraSpan = 360.0 / 27.0
        val offsetInNakshatra = moonLongitude % nakshatraSpan
        val pada = (offsetInNakshatra / (nakshatraSpan / 4.0)).toInt() + 1

        BirthChart(
            nakshatra = nakshatraNum,
            pada = pada.coerceIn(1, 4),
            rashi = rashiNum,
            moonLongitude = moonLongitude,
            marsRashi = marsRashi,
            lagnaRashi = lagnaRashi.coerceIn(1, 12),
            lagnaLongitude = lagnaLongitude,
            planetPositions = planetPositions
        )
    }

    private fun getStartOfDay(date: Date): Date {
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.time
    }

    private fun computeHoras(sunriseJD: Double, sunsetJD: Double, nextSunriseJD: Double, weekday: Int): List<HoraInfo> {
        // Chaldean order, held as planet *indices* rather than English literals so the name
        // can be resolved through the same localized `planet_N` lookup the Graha Sthiti grid
        // already uses. Hardcoding the names here was why the Hora widget, live tile, Wear
        // card and notifications all stayed English while the rest of the app switched to
        // Hindi. The moon's symbol stays ☽ (the crescent this screen has always drawn),
        // deliberately not PanchaangHelper's ☾, so the Hora rows don't change appearance.
        val chaldean = listOf(
            Triple(6, "♄", MuhuratType.INAUSPICIOUS), // Saturn
            Triple(4, "♃", MuhuratType.AUSPICIOUS),   // Jupiter
            Triple(2, "♂", MuhuratType.INAUSPICIOUS), // Mars
            Triple(0, "☉", MuhuratType.NEUTRAL),      // Sun
            Triple(5, "♀", MuhuratType.AUSPICIOUS),   // Venus
            Triple(3, "☿", MuhuratType.AUSPICIOUS),   // Mercury
            Triple(1, "☽", MuhuratType.AUSPICIOUS)    // Moon
        )
        val names = chaldean.map { PanchaangHelper.getPlanetName(context, it.first) }
        val startIndices = listOf(3, 6, 2, 5, 1, 4, 0) // Sunday to Saturday
        val startIdx = startIndices[weekday - 1]
        val dayLen = (sunsetJD - sunriseJD) / 12.0
        val nightLen = max(nextSunriseJD - sunsetJD, 1.0 / 1440.0) / 12.0
        val result = mutableListOf<HoraInfo>()
        for (i in 0 until 12) {
            val slot = (startIdx + i) % 7
            val p = chaldean[slot]
            result.add(HoraInfo(i, names[slot], p.second, jdToDate(sunriseJD + i * dayLen), jdToDate(sunriseJD + (i + 1) * dayLen), true, p.third))
        }
        for (i in 0 until 12) {
            val slot = (startIdx + 12 + i) % 7
            val p = chaldean[slot]
            result.add(HoraInfo(12 + i, names[slot], p.second, jdToDate(sunsetJD + i * nightLen), jdToDate(sunsetJD + (i + 1) * nightLen), false, p.third))
        }
        return result
    }

    private fun computeLagnas(sunriseJD: Double, sunsetJD: Double, nextSunriseJD: Double, latitude: Double, longitude: Double): List<LagnaPeriod> {
        val step = 15.0 / 1440.0
        val lagnas = mutableListOf<LagnaPeriod>()
        val firstAsc = wrapper.calculateAscendantAtJD(sunriseJD, latitude, longitude)
        var prevRashi = (firstAsc / 30.0).toInt() + 1
        var periodStart = sunriseJD
        var currentJD = sunriseJD + step

        while (currentJD <= nextSunriseJD) {
            val asc = wrapper.calculateAscendantAtJD(currentJD, latitude, longitude)
            val rashi = (asc / 30.0).toInt() + 1
            if (rashi != prevRashi) {
                val boundary = findLagnaTransition(currentJD - step, currentJD, prevRashi, latitude, longitude)
                lagnas.add(LagnaPeriod(lagnas.size, prevRashi, PanchaangHelper.getMoonRashiName(context, prevRashi), PanchaangHelper.getRashiSymbol(prevRashi), periodStart < sunsetJD, jdToDate(periodStart), jdToDate(boundary)))
                periodStart = boundary
                prevRashi = rashi
            }
            currentJD += step
        }
        lagnas.add(LagnaPeriod(lagnas.size, prevRashi, PanchaangHelper.getMoonRashiName(context, prevRashi), PanchaangHelper.getRashiSymbol(prevRashi), periodStart < sunsetJD, jdToDate(periodStart), jdToDate(nextSunriseJD)))
        return lagnas
    }

    private fun findLagnaTransition(startJD: Double, endJD: Double, prevRashi: Int, latitude: Double, longitude: Double): Double {
        var lo = startJD
        var hi = endJD
        repeat(12) {
            val mid = (lo + hi) / 2.0
            val asc = wrapper.calculateAscendantAtJD(mid, latitude, longitude)
            val rashi = (asc / 30.0).toInt() + 1
            if (rashi != prevRashi) hi = mid else lo = mid
        }
        return (lo + hi) / 2.0
    }

    private fun jdToDate(jd: Double): Date {
        return Date(((jd - 2440587.5) * 86400000).toLong())
    }

    private fun dateToJD(date: Date): Double {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.time = date
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val second = calendar.get(Calendar.SECOND)
        val hourDecimal = hour + minute / 60.0 + second / 3600.0
        return wrapper.getJulianDayUTC(year, month, day, hourDecimal)
    }

    suspend fun fetchGrahans(startDate: Date, endDate: Date, latitude: Double, longitude: Double): List<Grahan> = ephemerisCall {
        if (endDate <= startDate) return@ephemerisCall emptyList<Grahan>()
        val startJD = dateToJD(startDate)
        val endJD = dateToJD(endDate)

        val found = mutableListOf<Grahan>()
        val maxIterations = 200

        // Solar
        var cursor = startJD
        for (i in 0 until maxIterations) {
            val raw = wrapper.nextSolarEclipseVisible(cursor, latitude, longitude, endJD - cursor) ?: break
            val peakJD = raw["maxJD"] ?: 0.0
            if (peakJD <= cursor) break
            
            found.add(solarGrahan(raw, peakJD))
            cursor = peakJD + 1.0
            if (cursor >= endJD) break
        }

        // Lunar
        cursor = startJD
        for (i in 0 until maxIterations) {
            val raw = wrapper.nextLunarEclipseVisible(cursor, latitude, longitude, endJD - cursor) ?: break
            val peakJD = raw["maxJD"] ?: 0.0
            if (peakJD <= cursor) break
            
            found.add(lunarGrahan(raw, peakJD))
            cursor = peakJD + 1.0
            if (cursor >= endJD) break
        }

        found.sortedBy { it.peak }
    }

    private fun solarGrahan(raw: Map<String, Double>, peakJD: Double): Grahan {
        val isTotal = (raw["isTotal"] ?: 0.0) != 0.0
        val isAnnular = (raw["isAnnular"] ?: 0.0) != 0.0
        val extent = when {
            isTotal -> GrahanExtent.TOTAL
            isAnnular -> GrahanExtent.ANNULAR
            else -> GrahanExtent.PARTIAL
        }

        val first = raw["firstContactJD"] ?: 0.0
        val fourth = raw["fourthContactJD"] ?: 0.0
        val second = raw["secondContactJD"] ?: 0.0
        val third = raw["thirdContactJD"] ?: 0.0

        return Grahan(
            kind = GrahanKind.SOLAR,
            extent = extent,
            peak = jdToDate(peakJD),
            begins = jdToDate(if (first > 0) first else peakJD),
            ends = jdToDate(if (fourth > 0) fourth else peakJD),
            totalityBegins = if (second > 0) jdToDate(second) else null,
            totalityEnds = if (third > 0) jdToDate(third) else null,
            magnitude = raw["magnitude"] ?: 0.0
        )
    }

    private fun lunarGrahan(raw: Map<String, Double>, peakJD: Double): Grahan {
        val isTotal = (raw["isTotal"] ?: 0.0) != 0.0
        val isPartial = (raw["isPartial"] ?: 0.0) != 0.0
        val extent = when {
            isTotal -> GrahanExtent.TOTAL
            isPartial -> GrahanExtent.PARTIAL
            else -> GrahanExtent.PENUMBRAL
        }

        val tStart = raw["totalBeginJD"] ?: 0.0
        val tEnd = raw["totalEndJD"] ?: 0.0
        val pStart = raw["penumbralBeginJD"] ?: 0.0
        val pEnd = raw["penumbralEndJD"] ?: 0.0
        val parStart = raw["partialBeginJD"] ?: 0.0
        val parEnd = raw["partialEndJD"] ?: 0.0

        val begins = if (pStart > 0) pStart else (if (parStart > 0) parStart else peakJD)
        val ends = if (pEnd > 0) pEnd else (if (parEnd > 0) parEnd else peakJD)

        val umbral = raw["umbralMagnitude"] ?: 0.0
        val penumbral = raw["penumbralMagnitude"] ?: 0.0

        return Grahan(
            kind = GrahanKind.LUNAR,
            extent = extent,
            peak = jdToDate(peakJD),
            begins = jdToDate(begins),
            ends = jdToDate(ends),
            totalityBegins = if (tStart > 0) jdToDate(tStart) else null,
            totalityEnds = if (tEnd > 0) jdToDate(tEnd) else null,
            magnitude = if (umbral > 0) umbral else penumbral
        )
    }
}
