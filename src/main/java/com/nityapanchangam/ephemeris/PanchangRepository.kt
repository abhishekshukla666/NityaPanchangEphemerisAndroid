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

        val weekday = Calendar.getInstance().apply { time = dayStart }.get(Calendar.DAY_OF_WEEK)
        val mData = wrapper.calculateMuhurats(sunriseJD, sunsetJD, weekday)

        fun jdTime(key: String): Date = jdToDate(mData[key] ?: 0.0)

        val muhurats = listOf(
            Muhurat("1", context.getString(R.string.muhurat_brahma), jdTime("brahmaStart"), jdTime("brahmaEnd"), MuhuratType.AUSPICIOUS),
            Muhurat("2", context.getString(R.string.muhurat_amrit), jdTime("amritStart"), jdTime("amritEnd"), MuhuratType.AUSPICIOUS),
            Muhurat("3", context.getString(R.string.muhurat_abhijit), jdTime("abhijitStart"), jdTime("abhijitEnd"), MuhuratType.AUSPICIOUS),
            Muhurat("4", context.getString(R.string.muhurat_vijaya), jdTime("vijayaStart"), jdTime("vijayaEnd"), MuhuratType.AUSPICIOUS),
            Muhurat("5", context.getString(R.string.muhurat_godhuli), jdTime("godhuliStart"), jdTime("godhuliEnd"), MuhuratType.NEUTRAL),
            Muhurat("6", context.getString(R.string.muhurat_rahu), jdTime("rahuStart"), jdTime("rahuEnd"), MuhuratType.INAUSPICIOUS),
            Muhurat("7", context.getString(R.string.muhurat_yamaganda), jdTime("yamaStart"), jdTime("yamaEnd"), MuhuratType.INAUSPICIOUS),
            Muhurat("8", context.getString(R.string.muhurat_gulik), jdTime("gulikStart"), jdTime("gulikEnd"), MuhuratType.NEUTRAL)
        ).sortedBy { it.startTime }

        val rawPlanets = wrapper.calculatePlanetPositionsForJulianDay(refJD)
        val planetPositions = PanchaangHelper.buildPlanetPositions(context, rawPlanets)

        val sunRashiNum = planetPositions.find { it.id == 0 }?.rashiNumber ?: 1
        val isUttarayana = sunRashiNum <= 3 || sunRashiNum >= 10
        val vedaAyana = if (isUttarayana) context.getString(R.string.ayana_uttarayana) else context.getString(R.string.ayana_dakshinayana)

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
            context.getString(R.string.chaugh_udveg),
            context.getString(R.string.chaugh_char),
            context.getString(R.string.chaugh_labh),
            context.getString(R.string.chaugh_amrit),
            context.getString(R.string.chaugh_kaal),
            context.getString(R.string.chaugh_shubh),
            context.getString(R.string.chaugh_rog)
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
            lagnas = lagnas
        )
    }

    suspend fun fetchMonthTithis(year: Int, month: Int, latitude: Double, longitude: Double): Map<Int, Int> = ephemerisCall {
        val calendar = Calendar.getInstance()
        calendar.set(year, month - 1, 1, 0, 0, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val results = mutableMapOf<Int, Int>()

        for (day in 1..daysInMonth) {
            calendar.set(Calendar.DAY_OF_MONTH, day)
            val date = calendar.time
            val sunData = wrapper.calculateSunriseSunset(dateToJD(date), latitude, longitude)
            val sunriseJD = sunData["sunriseJD"] ?: 0.0
            val refJD = if (sunriseJD > 2400000) sunriseJD else dateToJD(date)
            results[day] = wrapper.calculateTithiNumberForJulianDay(refJD)
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
                            // Third of five equal divisions of daylight, sampled at its midpoint.
                            val dayLen = max(sunsetRefJD - sunriseRefJD, 1.0 / 1440.0)
                            aparahna = anchorAt(sunriseRefJD + dayLen * 2.5 / 5.0)
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
        festivals.sortedBy { it.date }
    }

    /** Tithi, Purnimanta month and Adhik flag as they stand at one instant. */
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
