package com.nityapanchangam.ephemeris

import android.content.Context
import com.nityapanchangam.ephemeris.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.math.abs
import kotlin.math.max

class PanchangRepository(private val context: Context, private val wrapper: SwissEphWrapper) {

    suspend fun fetchPanchang(date: Date, latitude: Double, longitude: Double): PanchangDay = withContext(Dispatchers.Default) {
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

    suspend fun fetchMonthTithis(year: Int, month: Int, latitude: Double, longitude: Double): Map<Int, Int> = withContext(Dispatchers.Default) {
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

    suspend fun fetchFestivals(startDate: Date, endDate: Date, latitude: Double, longitude: Double): List<HinduFestival> = withContext(Dispatchers.Default) {
        val calendar = Calendar.getInstance()
        val festivals = mutableListOf<HinduFestival>()
        val seen = mutableSetOf<String>()
        var current = getStartOfDay(startDate)

        val end = getStartOfDay(endDate)

        while (current.time <= end.time) {
            val calYear = Calendar.getInstance().apply { time = current }.get(Calendar.YEAR)
            val jdSunrise = dateToJD(current) + (6.0 / 24.0) // Approx sunrise for base metrics

            // In a real implementation, we'd calculate actual sunrise, but this is a port of the logic
            val tithiSunrise = wrapper.calculateTithiNumberForJulianDay(jdSunrise)
            val monthSunrise = wrapper.calculatePurnimantaMonthForJulianDay(jdSunrise)
            val isAdhikSunrise = wrapper.calculateIsPurnimantaAdhikMaasForJulianDay(jdSunrise)

            // Evaluate Rules
            for (rule in allFestivalRules) {
                if (rule.observationTime == ObservationTime.MIDNIGHT) {
                    val diff = abs(tithiSunrise - rule.tithiNumber)
                    if (diff > 2 && diff < 28) continue

                    val jdMidnight = dateToJD(current) + (23.9 / 24.0)
                    val tithiMid = wrapper.calculateTithiNumberForJulianDay(jdMidnight)
                    val monthMid = wrapper.calculatePurnimantaMonthForJulianDay(jdMidnight)
                    val isAdhikMid = wrapper.calculateIsPurnimantaAdhikMaasForJulianDay(jdMidnight)

                    if (!isAdhikMid && monthMid == rule.lunarMonth && tithiMid == rule.tithiNumber) {
                        val key = "${rule.name}-$calYear"
                        if (seen.add(key)) {
                            festivals.add(HinduFestival(rule.name, current, rule.emoji, rule.hasIcon))
                        }
                    }
                } else {
                    if (!isAdhikSunrise && monthSunrise == rule.lunarMonth && tithiSunrise == rule.tithiNumber) {
                        val key = "${rule.name}-$calYear"
                        if (seen.add(key)) {
                            festivals.add(HinduFestival(rule.name, current, rule.emoji, rule.hasIcon))
                        }
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
        festivals.sortedBy { it.date }
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
        val chaldean = listOf(
            Triple("Saturn", "♄", MuhuratType.INAUSPICIOUS),
            Triple("Jupiter", "♃", MuhuratType.AUSPICIOUS),
            Triple("Mars", "♂", MuhuratType.INAUSPICIOUS),
            Triple("Sun", "☉", MuhuratType.NEUTRAL),
            Triple("Venus", "♀", MuhuratType.AUSPICIOUS),
            Triple("Mercury", "☿", MuhuratType.AUSPICIOUS),
            Triple("Moon", "☽", MuhuratType.AUSPICIOUS)
        )
        val startIndices = listOf(3, 6, 2, 5, 1, 4, 0) // Sunday to Saturday
        val startIdx = startIndices[weekday - 1]
        val dayLen = (sunsetJD - sunriseJD) / 12.0
        val nightLen = max(nextSunriseJD - sunsetJD, 1.0 / 1440.0) / 12.0
        val result = mutableListOf<HoraInfo>()
        for (i in 0 until 12) {
            val p = chaldean[(startIdx + i) % 7]
            result.add(HoraInfo(i, p.first, p.second, jdToDate(sunriseJD + i * dayLen), jdToDate(sunriseJD + (i + 1) * dayLen), true, p.third))
        }
        for (i in 0 until 12) {
            val p = chaldean[(startIdx + 12 + i) % 7]
            result.add(HoraInfo(12 + i, p.first, p.second, jdToDate(sunsetJD + i * nightLen), jdToDate(sunsetJD + (i + 1) * nightLen), false, p.third))
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
}
