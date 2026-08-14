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
    val lagnas: List<LagnaPeriod>
)
