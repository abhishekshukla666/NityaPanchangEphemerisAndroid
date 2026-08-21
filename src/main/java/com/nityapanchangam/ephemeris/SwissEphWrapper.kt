package com.nityapanchangam.ephemeris

import android.content.Context

class SwissEphWrapper(context: Context) {

    init {
        ensureEphemerisPathSet(context.applicationContext)
    }

    companion object {
        init {
            System.loadLibrary("nityapanchangephemeris")
        }

        // swe_set_ephe_path() closes every open ephemeris file and re-probes the data
        // directory — safe to call once per process, not safe to call concurrently from
        // multiple SwissEphWrapper instances, so this runs exactly once, guarded.
        @Volatile
        private var ephemerisPathSet = false

        @Synchronized
        private fun ensureEphemerisPathSet(context: Context) {
            if (ephemerisPathSet) return
            val path = EphemerisAssets.ensureExtracted(context)
            setEphemerisPath(path)
            ephemerisPathSet = true
        }

        @JvmStatic
        private external fun setEphemerisPath(path: String)
    }

    external fun calculateTithiNumberForJulianDay(jd: Double): Int

    external fun calculateTithiForJulianDay(jd: Double): Map<String, Double>

    external fun calculateSunriseSunset(jd: Double, latitude: Double, longitude: Double): Map<String, Double>

    external fun calculateLunarMonthForJulianDay(jd: Double): Int

    external fun calculatePurnimantaMonthForJulianDay(jd: Double): Int

    external fun calculateIsAdhikMaasForJulianDay(jd: Double): Boolean

    external fun calculateIsPurnimantaAdhikMaasForJulianDay(jd: Double): Boolean

    external fun calculateYogaForJulianDay(jd: Double): Int

    external fun calculateNakshatraForJulianDay(jd: Double): Int

    external fun calculateNakshatraEndTimeForJulianDay(startJD: Double): Double

    external fun calculateMuhurats(sunriseJD: Double, sunsetJD: Double, weekday: Int): Map<String, Double>

    external fun calculateYogaEndTimeForJulianDay(startJD: Double): Double

    external fun calculateMoonRashiForJulianDay(jd: Double): Int

    // Returning a simple double array for planet positions to keep JNI simple
    // [planetIndex, longitude, rashiNumber, degrees, ...] repeats for 9 planets
    external fun calculatePlanetPositionsForJulianDay(jd: Double): DoubleArray

    external fun calculateAscendantAtJD(jd: Double, latitude: Double, longitude: Double): Double

    external fun nextSolarEclipseVisible(jd: Double, latitude: Double, longitude: Double, maxDaysAhead: Double): Map<String, Double>?

    external fun nextLunarEclipseVisible(jd: Double, latitude: Double, longitude: Double, maxDaysAhead: Double): Map<String, Double>?

    external fun getJulianDayUTC(year: Int, month: Int, day: Int, hourDecimal: Double): Double
}
