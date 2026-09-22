package com.nityapanchangam.ephemeris

import android.content.Context
import com.nityapanchangam.ephemeris.models.Paksha
import com.nityapanchangam.ephemeris.models.PlanetPosition
import java.util.Calendar
import java.util.Date

object PanchaangHelper {

    /**
     * Resolves a string the consuming app owns, by name.
     *
     * These names used to live in this library's own resources, which meant a translator had
     * two files to find in two repositories, and adding a language meant cutting a library
     * release before the app could use it. They belong with the other ~200 Panchang names in
     * the app instead, so this resolves them the same way [getFestivalName] and the tithi,
     * nakshatra and yoga lookups already do.
     *
     * The English fallback matters: a name lookup cannot be checked at compile time, and the
     * resource shrinker strips what it cannot see referenced. If the app forgets its keep
     * rule the app shows "Rahu Kaal" rather than a blank row.
     */
    internal fun Context.localized(key: String, fallback: String): String {
        val resId = resources.getIdentifier(key, "string", packageName)
        return if (resId != 0) getString(resId) else fallback
    }

    /** [localized] for the two names that take a format argument. */
    internal fun Context.localizedFormat(key: String, fallback: String, arg: Any): String {
        val resId = resources.getIdentifier(key, "string", packageName)
        return if (resId != 0) getString(resId, arg) else String.format(fallback, arg)
    }

    fun getLunarMonthName(context: Context, number: Int, isAdhik: Boolean = false): String {
        val resId = context.resources.getIdentifier("month_$number", "string", context.packageName)
        val name = if (resId != 0) context.getString(resId) else "Month $number"
        return if (isAdhik) context.localizedFormat("adhik_prefix", "Adhik %s", name) else name
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

    /** Sun, Moon, Mars, Mercury, Jupiter, Venus, Saturn, Rahu, Ketu, then Uranus, Neptune and
     *  Pluto — index order shared by the `planet_N` string resources, [PLANET_SYMBOLS], and
     *  the raw ephemeris output.
     *
     *  Ids 0–8 are the Navagraha and are what every classical calculation in this library
     *  uses: a dasha, a lordship, a hora, a combustion orb. Ids 9–11 are the three no
     *  classical text knows. They are computed and named here so a caller can *show* them, and
     *  they are carried in their own array on [com.nityapanchangam.ephemeris.models.PanchangDay]
     *  so nothing that reasons about the nine can pick them up by accident. */
    private val PLANET_SYMBOLS =
        listOf("☉", "☾", "♂", "☿", "♃", "♀", "♄", "☊", "☋", "♅", "♆", "♇")

    private val PLANET_FALLBACK_NAMES = listOf(
        "Sun", "Moon", "Mars", "Mercury", "Jupiter", "Venus", "Saturn", "Rahu", "Ketu",
        "Uranus", "Neptune", "Pluto"
    )

    /** The Navagraha — the ids a classical rule may look at. */
    val NAVAGRAHA_IDS = 0..8

    /** Uranus, Neptune and Pluto. */
    val OUTER_PLANET_IDS = 9..11

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
        // Five doubles per planet, matching what native-lib.cpp pushes: index, longitude,
        // rashi, degree, and Vakri as 1.0 or 0.0. The stride and the native layout have to
        // move together.
        for (i in 0 until raw.size step 5) {
            val idx = raw[i].toInt()
            val lon = raw[i + 1]
            val rashi = raw[i + 2].toInt()
            val deg = raw[i + 3]
            val isRetrograde = raw[i + 4] != 0.0
            if (idx < PLANET_SYMBOLS.size) {
                result.add(
                    PlanetPosition(idx, getPlanetName(context, idx), PLANET_SYMBOLS[idx], lon, rashi, deg, isRetrograde)
                )
            }
        }
        return result.sortedBy { it.id }
    }

    /**
     * The twenty-four Ekadashis, by Purnimanta month.
     *
     * Whole names rather than a stem plus " Ekadashi". Two of them need a month in front --
     * there is a Putrada Ekadashi in Shravana and another in Pausha, and a bare "Putrada
     * Ekadashi" cannot tell a reader which one is in front of them -- so the composition never
     * held anyway. Dropping it also means one resource id per Ekadashi rather than a stem id
     * and a suffix, and the id is the same one the festival rules ask for.
     *
     * These are also the only source of the twenty-four Ekadashi festival rules;
     * ekadashiFestivalRules builds them from here. The two used to be written out separately
     * and had drifted apart on four of the twenty-four, so the calendar and the Quick Lookup
     * tile named the same day differently.
     *
     * Index is `lunarMonth - 1`, Chaitra first. Kept identical to iOS's PanchaangHelper.
     */
    val shuklaEkadashiNames = listOf(
        "Kamada Ekadashi", "Mohini Ekadashi", "Nirjala Ekadashi", "Devshayani Ekadashi",
        "Shravana Putrada Ekadashi", "Parivartini Ekadashi", "Papankusha Ekadashi",
        "Devutthana Ekadashi", "Mokshada Ekadashi", "Pausha Putrada Ekadashi",
        "Jaya Ekadashi", "Amalaki Ekadashi"
    )

    val krishnaEkadashiNames = listOf(
        "Papamochani Ekadashi", "Varuthini Ekadashi", "Apara Ekadashi", "Yogini Ekadashi",
        "Kamika Ekadashi", "Aja Ekadashi", "Indira Ekadashi", "Rama Ekadashi",
        "Utpanna Ekadashi", "Saphala Ekadashi", "Shattila Ekadashi", "Vijaya Ekadashi"
    )

    /** The English name, which is also the resource id's source. */
    fun ekadashiName(lunarMonth: Int, paksha: Paksha, isAdhik: Boolean = false): String {
        // An Adhik month repeats a month number, so the table above would name the Ekadashi of
        // the ordinary month of the same number. Both of an Adhik month's Ekadashis are Padmini.
        if (isAdhik) return "Padmini Ekadashi"
        val idx = (lunarMonth - 1) % 12
        return if (paksha == Paksha.SHUKLA) shuklaEkadashiNames[idx] else krishnaEkadashiNames[idx]
    }

    fun getEkadashiName(context: Context, lunarMonth: Int, paksha: Paksha, isAdhik: Boolean = false): String =
        getFestivalName(context, ekadashiName(lunarMonth, paksha, isAdhik))

    /**
     * Whether a karana number is Vishti, the one Bhadra is named for.
     *
     * Vishti sits at index 6 of the seven movable karanas, which run 2-57. The four fixed ones —
     * 1, and 58 through 60 — can never be it, which the range check enforces.
     */
    fun isVishti(karanaNumber: Int): Boolean =
        karanaNumber in 2..57 && (karanaNumber - 2) % 7 == 6

    /**
     * Whether any karana between two readings is Vishti — that is, whether Bhadra touches the
     * span they bound.
     *
     * Exact rather than sampled, and it costs two ephemeris readings instead of a scan. The
     * Moon-Sun elongation a karana is cut from only ever increases, so the karanas covering a
     * span are precisely those from the one at its start to the one at its end.
     *
     * A day cannot be decided by its sunrise karana alone: a karana runs ten to thirteen hours
     * against a twenty-four hour day, so about half of all Bhadras begin after one sunrise and
     * end before the next, covering neither.
     */
    fun isVishti(first: Int, last: Int): Boolean {
        var karana = first
        repeat(60) {
            if (isVishti(karana)) return true
            if (karana == last) return false
            karana = karana % 60 + 1
        }
        return false
    }

    fun isGandaMoola(nakshatraNumber: Int): Boolean {
        return listOf(1, 9, 10, 18, 19, 27).contains(nakshatraNumber)
    }

    /**
     * Panchak — the Moon in Kumbha or Meena; avoid south travel, construction, cremation.
     *
     * Read from the Moon's SIGN, not its nakshatra, and that distinction is the whole of this
     * function. "The last five nakshatras" is how Panchak is usually described and it is half a
     * nakshatra wrong: the period is the Moon's passage through Kumbha and Meena, 300 to 360
     * degrees, which begins at Dhanishtha's THIRD pada. Dhanishtha spans 293°20'-306°40', so
     * its first half lies in Makara and is not Panchak at all.
     *
     * Testing the nakshatra number instead opened the period 6°40' early — about twelve and a
     * half hours of Moon travel, which crosses a sunrise often enough that seven days of 2026
     * were flagged Panchak a day before it began. It can only ever over-report, never miss: the
     * other four nakshatras lie wholly inside the two signs.
     */
    fun isPanchak(moonRashiNumber: Int): Boolean {
        return moonRashiNumber == 11 || moonRashiNumber == 12
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
            Calendar.SUNDAY -> Triple("पश्चिम", context.localized("direction_west", "West"), "←")
            Calendar.MONDAY -> Triple("पूर्व", context.localized("direction_east", "East"), "→")
            Calendar.TUESDAY -> Triple("उत्तर", context.localized("direction_north", "North"), "↑")
            Calendar.WEDNESDAY -> Triple("उत्तर", context.localized("direction_north", "North"), "↑")
            Calendar.THURSDAY -> Triple("दक्षिण", context.localized("direction_south", "South"), "↓")
            Calendar.FRIDAY -> Triple("पश्चिम", context.localized("direction_west", "West"), "←")
            Calendar.SATURDAY -> Triple("पूर्व", context.localized("direction_east", "East"), "→")
            else -> Triple("", "", "")
        }
    }
}
