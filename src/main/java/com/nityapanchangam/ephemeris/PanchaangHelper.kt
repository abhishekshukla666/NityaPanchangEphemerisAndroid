package com.nityapanchangam.ephemeris

import android.content.Context
import com.nityapanchangam.ephemeris.models.Paksha
import com.nityapanchangam.ephemeris.models.PlanetPosition
import java.util.Calendar
import java.util.Date

object PanchaangHelper {

    fun getLunarMonthName(context: Context, number: Int, isAdhik: Boolean = false): String {
        val resId = context.resources.getIdentifier("month_$number", "string", context.packageName)
        val name = if (resId != 0) context.getString(resId) else "Month $number"
        return if (isAdhik) context.getString(R.string.adhik_prefix, name) else name
    }

    fun getTithiName(context: Context, number: Int): String {
        val id = if (number == 30) 30 else (number - 1) % 15 + 1
        val resId = context.resources.getIdentifier("tithi_$id", "string", context.packageName)
        return if (resId != 0) context.getString(resId) else "Tithi $number"
    }

    fun getNakshatraName(context: Context, number: Int): String {
        val resId = context.resources.getIdentifier("nakshatra_$number", "string", context.packageName)
        return if (resId != 0) context.getString(resId) else "Nakshatra $number"
    }

    fun getYogaName(context: Context, number: Int): String {
        val resId = context.resources.getIdentifier("yoga_$number", "string", context.packageName)
        return if (resId != 0) context.getString(resId) else "Yoga $number"
    }

    fun getKaranaName(context: Context, number: Int): String {
        val resId = when {
            number == 1 -> context.resources.getIdentifier("karana_fixed_4", "string", context.packageName)
            number >= 58 -> {
                val fixedIdx = number - 57 // 1 to 4
                context.resources.getIdentifier("karana_fixed_$fixedIdx", "string", context.packageName)
            }
            else -> {
                val movingIdx = (number - 2) % 7 + 1
                context.resources.getIdentifier("karana_$movingIdx", "string", context.packageName)
            }
        }
        return if (resId != 0) context.getString(resId) else "Karana $number"
    }

    fun getVaraName(context: Context, date: Date): String {
        val calendar = Calendar.getInstance()
        calendar.time = date
        val weekday = calendar.get(Calendar.DAY_OF_WEEK)
        val resId = context.resources.getIdentifier("vara_$weekday", "string", context.packageName)
        return if (resId != 0) context.getString(resId) else "Day $weekday"
    }

    fun getMoonRashiName(context: Context, number: Int): String {
        val resId = context.resources.getIdentifier("rashi_$number", "string", context.packageName)
        return if (resId != 0) context.getString(resId) else "Rashi $number"
    }

    fun getRashiSymbol(number: Int): String {
        val symbols = listOf("♈", "♉", "♊", "♋", "♌", "♍", "♎", "♏", "♐", "♑", "♒", "♓")
        return symbols.getOrElse(number - 1) { symbols.last() }
    }

    /** Sun, Moon, Mars, Mercury, Jupiter, Venus, Saturn, Rahu, Ketu — index order shared by
     *  the `planet_N` string resources, [PLANET_SYMBOLS], and the raw ephemeris output. */
    private val PLANET_SYMBOLS = listOf("☉", "☾", "♂", "☿", "♃", "♀", "♄", "☊", "☋")

    private val PLANET_FALLBACK_NAMES =
        listOf("Sun", "Moon", "Mars", "Mercury", "Jupiter", "Venus", "Saturn", "Rahu", "Ketu")

    /**
     * Localized name for a planet index, resolved from the consuming app's `planet_N` string
     * resources. Looked up by name rather than by `R.string` constant because this library
     * deliberately doesn't own these strings — the app does, so a host that ships more (or
     * fewer) languages than we do gets its own translations without any change here. Falls
     * back to English if the resource is absent.
     */
    fun getPlanetName(context: Context, index: Int): String {
        val resId = context.resources.getIdentifier("planet_$index", "string", context.packageName)
        if (resId != 0) return context.getString(resId)
        return PLANET_FALLBACK_NAMES.getOrElse(index) { "Planet $index" }
    }

    fun getPlanetSymbol(index: Int): String = PLANET_SYMBOLS.getOrElse(index) { "" }

    fun buildPlanetPositions(context: Context, raw: DoubleArray): List<PlanetPosition> {
        val result = mutableListOf<PlanetPosition>()
        for (i in 0 until raw.size step 4) {
            val idx = raw[i].toInt()
            val lon = raw[i + 1]
            val rashi = raw[i + 2].toInt()
            val deg = raw[i + 3]
            if (idx < PLANET_SYMBOLS.size) {
                result.add(
                    PlanetPosition(idx, getPlanetName(context, idx), PLANET_SYMBOLS[idx], lon, rashi, deg)
                )
            }
        }
        return result.sortedBy { it.id }
    }

    fun getEkadashiName(context: Context, lunarMonth: Int, paksha: Paksha, isAdhik: Boolean = false): String {
        val shukla = listOf(
            "Kamada", "Mohini", "Nirjala", "Devshayani", "Putrada", "Parsva",
            "Pasankusha", "Devutthana", "Mokshada", "Putrada", "Jaya", "Amalaki"
        )
        val krishna = listOf(
            "Papamochani", "Varuthini", "Apara", "Yogini", "Kamika", "Aja",
            "Indira", "Rama", "Utpanna", "Saphala", "Shattila", "Vijaya"
        )
        val baseName = if (isAdhik) "Padmini" else {
            val idx = (lunarMonth - 1) % 12
            if (paksha == Paksha.SHUKLA) shukla[idx] else krishna[idx]
        }
        val localizedName = getFestivalName(context, baseName)
        return context.getString(R.string.ekadashi_suffix, localizedName)
    }

    fun isGandaMoola(nakshatraNumber: Int): Boolean {
        return listOf(1, 9, 10, 18, 19, 27).contains(nakshatraNumber)
    }

    fun isPanchak(nakshatraNumber: Int): Boolean {
        return nakshatraNumber >= 23
    }

    fun getFestivalName(context: Context, name: String): String {
        val key = "fest_" + name.lowercase()
            .replace(" ", "_")
            .replace("(", "")
            .replace(")", "")
            .replace("'", "")
            .replace("-", "_")
        val resId = context.resources.getIdentifier(key, "string", context.packageName)
        return if (resId != 0) context.getString(resId) else name
    }

    fun dishashool(context: Context, date: Date): Triple<String, String, String> {
        val calendar = Calendar.getInstance().apply { time = date }
        val weekday = calendar.get(Calendar.DAY_OF_WEEK)
        return when (weekday) {
            Calendar.SUNDAY -> Triple("पश्चिम", context.getString(R.string.direction_west), "←")
            Calendar.MONDAY -> Triple("पूर्व", context.getString(R.string.direction_east), "→")
            Calendar.TUESDAY -> Triple("उत्तर", context.getString(R.string.direction_north), "↑")
            Calendar.WEDNESDAY -> Triple("उत्तर", context.getString(R.string.direction_north), "↑")
            Calendar.THURSDAY -> Triple("दक्षिण", context.getString(R.string.direction_south), "↓")
            Calendar.FRIDAY -> Triple("पश्चिम", context.getString(R.string.direction_west), "←")
            Calendar.SATURDAY -> Triple("पूर्व", context.getString(R.string.direction_east), "→")
            else -> Triple("", "", "")
        }
    }
}
