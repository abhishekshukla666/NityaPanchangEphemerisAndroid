package com.nityapanchangam.ephemeris.models

import java.util.Calendar
import java.util.Date


/**
 * Which regional calendar keeps a festival, as a bitmask.
 *
 * A set rather than a single value: most days are kept everywhere, and the ones that are not
 * are often kept in two regions but not a third. Bestu Varas is Gujarat's new year on the same
 * day Karnataka keeps Balipadyami, and both fall on the Kartika Shukla Pratipada the north
 * calls Govardhan Puja — one date, three names, three audiences.
 *
 * Existing rules are all [ALL], deliberately. Tagging the Hindi-belt days (Chhath, Karwa
 * Chauth, Ahoi Ashtami) as north-only would silently remove festivals that readers of the
 * shipped app already see, which is a decision for the app's owner rather than a side effect
 * of adding three languages. Narrowing them later is a one-word change per rule.
 *
 * Mirrors iOS's FestivalRegion OptionSet, bit for bit, so a date computed on one platform
 * carries the same regions on the other.
 */
object FestivalRegion {
    const val NORTH = 1 shl 0
    const val GUJARAT = 1 shl 1
    const val KARNATAKA = 1 shl 2
    const val TELUGU = 1 shl 3
    const val MAHARASHTRA = 1 shl 4

    /**
     * Kept everywhere the app is read. Adding a region widens every existing ALL rule to the
     * new audience, which is the intent: a pan-Indian festival does not stop being pan-Indian
     * because one more calendar can now be selected.
     */
    const val ALL = NORTH or GUJARAT or KARNATAKA or TELUGU or MAHARASHTRA
    /** The two southern calendars, which share most of what the north does not. */
    const val SOUTH = KARNATAKA or TELUGU
    /** Maharashtra keeps Amanta months like the south, but its festival calendar sits closer
     *  to the Deccan than to either — so it is its own bit, not a member of SOUTH. */
    const val DECCAN = KARNATAKA or MAHARASHTRA
}

data class HinduFestival(
    val name: String,
    val date: Date,
    val emoji: String,
    val hasIcon: Boolean = false,
    /**
     * Where this day is kept. Defaulted so every existing call site — and any caller that
     * does not care — keeps compiling and keeps meaning "kept everywhere".
     */
    val regions: Int = FestivalRegion.ALL
)

/**
 * The instant of the day at which a festival's tithi must prevail.
 *
 * SUNSET used to be listed here but no rule ever used it, and the computation had no branch
 * for it — a rule tagged SUNSET would silently have been evaluated at sunrise. Replaced with
 * the two instants that are actually needed, matching iOS's ObservationTime exactly.
 */
enum class ObservationTime {
    /** Udaya Tithi — the default for most festivals. */
    SUNRISE,

    /** Nishita Kaal — Shivratri, Janmashtami. */
    MIDNIGHT,

    /** Pradosh-vyapini — Diwali Laxmi Puja, Holika Dahan. The tithi must prevail in the
     *  first fifth of the night after sunset. */
    PRADOSH_KAAL,

    /** Aparahna-vyapini — Dussehra. The tithi must prevail in the fourth of five equal
     *  divisions of daylight. */
    APARAHNA,

    /** Madhyahna-vyapini — Akshaya Tritiya. The THIRD of the five divisions, so midday
     *  rather than afternoon: the two are one division apart and pick different days
     *  whenever the tithi turns over between them. */
    MADHYAHNA
}

data class FestivalRule(
    val name: String,
    val lunarMonth: Int,
    val tithiNumber: Int,
    val emoji: String,
    val observationTime: ObservationTime = ObservationTime.SUNRISE,
    val hasIcon: Boolean = false,
    val regions: Int = FestivalRegion.ALL,
    /**
     * Upper bound when the rule matches a RANGE of tithis rather than one.
     *
     * Varalakshmi Vratam is the Friday before Shravana Purnima, which is not a tithi at all --
     * it is whichever tithi that Friday happens to land on. Pairing a range with [weekday]
     * expresses it exactly: the Friday whose tithi falls in the week before the full moon, and
     * there is only ever one.
     */
    val tithiUpperBound: Int? = null,
    /**
     * Calendar weekday (Calendar.SUNDAY == 1) the day must fall on, when the observance is
     * defined by the weekday rather than by the tithi alone.
     */
    val weekday: Int? = null
) {
    /** Whether [tithi] satisfies this rule, single value or range. */
    fun matches(tithi: Int): Boolean =
        tithiUpperBound?.let { tithi in tithiNumber..it } ?: (tithi == tithiNumber)

    /**
     * A vriddhi Ekadashi — one whose tithi is current at two consecutive sunrises — is kept
     * on the **second** day, not the first.
     *
     * The first is Dashami-viddha: Dashami was still running into it, and the vrat may not be
     * kept on such a day. Published dates agree across Amalaki 2023 (3 Mar), Nirjala 2024
     * (18 Jun), Rama 2024 (28 Oct) and Vijaya 2027 (4 Mar) — in each the tithi holds both
     * sunrises and the observance is the later one. Ordinary festivals take the *first*
     * sunrise their tithi touches, which is why this cannot be the default.
     *
     * Only vriddhi. A *kshaya* Ekadashi, touching no sunrise at all, stays on the day that
     * held the greater part of it — the ordinary fallback — as Parivartini 2023 (25 Sep) and
     * Yogini 2025 (21 Jun) both show.
     *
     * Derived from the tithi rather than set per rule, so all twenty-four Ekadashis are
     * covered without per-rule bookkeeping.
     */
    val resolvesForward: Boolean get() = tithiNumber == 11 || tithiNumber == 26
}

data class StaticFestivalRule(
    val name: String,
    val month: Int, // 1-12
    val day: Int,
    val emoji: String,
    val hasIcon: Boolean = false
)

// Lunar month numbers: Chaitra=1, Vaishakha=2, Jyeshtha=3, Ashadha=4,
//   Shravana=5, Bhadrapada=6, Ashwina=7, Kartika=8, Margashirsha=9,
//   Pausha=10, Magha=11, Phalguna=12
//
// Purnimanta convention — month starts at Krishna Paksha (after previous Purnima)
// and closes at the next Purnima. Tithi numbering within each month:
//   Krishna 1-14 + Amavasya  = tithis  1-15  (dark fortnight, opens the month)
//   Shukla  1-14 + Purnima   = tithis 16-30  (bright fortnight, closes the month)
/**
 * Days kept in one regional calendar but not across all of them.
 *
 * Tithi numbers follow the same Purnimanta convention as the table above — Krishna 1-15,
 * Shukla 16-30 — because every rule here matches on the Purnimanta month the ephemeris
 * carries, whatever convention the app happens to display.
 *
 * Several fall on a day the pan-Indian table already names: Bestu Varas is the Kartika Shukla
 * Pratipada the north calls Govardhan Puja, Gowri Habba the Bhadrapada Shukla Tritiya the
 * north calls Hartalika Teej. They are separate rules rather than aliases because a Gujarati
 * reader looking for their new year will not find it under "Govardhan Puja", and the two are
 * genuinely different observances that happen to share a date.
 *
 * Kept identical to iOS's regionalFestivalRules; the Swift file is the source both were
 * written from.
 */
val regionalFestivalRules: List<FestivalRule> = listOf(
    FestivalRule("Bestu Varas", 8, 16, "🪔", regions = FestivalRegion.GUJARAT),
    FestivalRule("Labh Pancham", 8, 20, "📿", regions = FestivalRegion.GUJARAT),
    FestivalRule("Vagh Baras", 8, 12, "🐄", regions = FestivalRegion.GUJARAT),
    FestivalRule("Jaya Parvati Vrat", 4, 28, "🌺", regions = FestivalRegion.GUJARAT),
    FestivalRule("Randhan Chhath", 5, 6, "🍲", regions = FestivalRegion.GUJARAT),
    FestivalRule("Shitala Satam", 5, 7, "🙏", regions = FestivalRegion.GUJARAT),
    // Kutchi new year, the Ashadha Shukla Dwitiya that is also Rath Yatra.
    FestivalRule("Ashadhi Beej", 4, 17, "🌾", regions = FestivalRegion.GUJARAT),
    FestivalRule("Bhadarvi Poonam", 6, 30, "🌝", regions = FestivalRegion.GUJARAT),
    FestivalRule("Gowri Habba", 6, 18, "🌺", regions = FestivalRegion.KARNATAKA),
    FestivalRule("Ayudha Puja", 7, 24, "🛠️", regions = FestivalRegion.SOUTH),
    FestivalRule("Basava Jayanti", 2, 18, "🙏", regions = FestivalRegion.KARNATAKA),
    FestivalRule("Balipadyami", 8, 16, "🪔", regions = FestivalRegion.KARNATAKA),
    FestivalRule("Bathukamma", 7, 15, "💐", regions = FestivalRegion.TELUGU),
    FestivalRule("Atla Tadde", 7, 3, "🥞", regions = FestivalRegion.TELUGU),
    FestivalRule("Nagula Chavithi", 8, 19, "🐍", regions = FestivalRegion.TELUGU),
    FestivalRule("Boddemma", 6, 23, "💐", regions = FestivalRegion.TELUGU),
    // The same Shravana Amavasya Maharashtra keeps as Bail Pola.
    FestivalRule("Polala Amavasya", 6, 15, "🐄", regions = FestivalRegion.TELUGU),
    // Maharashtra. Narali Purnima is the Shravana Purnima the north keeps as Raksha Bandhan;
    // Bail Pola the Shravana Amavasya, which is a Bhadrapada Krishna tithi in the Purnimanta
    // months these rules count in; Rang Panchami the fifth day after Holi, so a Chaitra
    // Krishna tithi rather than a Phalguna one.
    FestivalRule("Narali Purnima", 5, 30, "🥥", regions = FestivalRegion.MAHARASHTRA),
    FestivalRule("Bail Pola", 6, 15, "🐂", regions = FestivalRegion.MAHARASHTRA),
    FestivalRule("Rang Panchami", 1, 5, "🎨", regions = FestivalRegion.MAHARASHTRA),
    FestivalRule("Datta Jayanti", 9, 30, "🕉️", regions = FestivalRegion.MAHARASHTRA),
    // The winter Ganesh Jayanti, distinct from the Bhadrapada Ganesh Chaturthi.
    FestivalRule("Ganesh Jayanti", 11, 19, "🐘", regions = FestivalRegion.MAHARASHTRA),
    // One Margashirsha Shukla Shashthi under two names: Khandoba in Maharashtra,
    // Subrahmanya in Karnataka. Each region gets its own.
    FestivalRule("Champa Shashthi", 9, 21, "🙏", regions = FestivalRegion.MAHARASHTRA),
    FestivalRule("Subrahmanya Shashti", 9, 21, "🐍", regions = FestivalRegion.KARNATAKA),
    // Friday before Shravana Purnima: dated by weekday, not by tithi. Calendar.FRIDAY == 6,
    // the same number iOS uses for Calendar.component(.weekday:).
    FestivalRule("Varalakshmi Vratam", 5, 23, "🪷", regions = FestivalRegion.SOUTH,
        tithiUpperBound = 29, weekday = Calendar.FRIDAY),
    FestivalRule("Vaikuntha Ekadashi", 9, 26, "🛕", regions = FestivalRegion.SOUTH),
)

/**
 * Every tithi-derived rule the engine evaluates: the pan-Indian table plus the regional one.
 * Kept as a join rather than by pasting the regional days into the main table, so "which of
 * these is regional" stays answerable by reading one list.
 */
val allFestivalRules: List<FestivalRule> get() = panIndianFestivalRules + regionalFestivalRules

val panIndianFestivalRules: List<FestivalRule> = listOf(

    // Chaitra (1)
    FestivalRule("Sheetala Ashtami", 1, 8, "🙏"),
    FestivalRule("Ugadi", 1, 16, "🪷"),
    FestivalRule("Gudi Padwa", 1, 16, "🌾"),
    FestivalRule("Navratri (Chaitra)", 1, 16, "🎊"),
    FestivalRule("Ram Navami", 1, 24, "ram", hasIcon = true),
    FestivalRule("Hanuman Jayanti", 1, 30, "hanuman", hasIcon = true),

    // Vaishakha (2)
    // Madhyahna, not sunrise. Tritiya at midday is what dates these: in 2026 it reaches
    // sunrise only on 20 Apr but holds midday on the 19th, and 2023 splits the same way.
    FestivalRule("Akshaya Tritiya", 2, 18, "gold_pot", ObservationTime.MADHYAHNA, hasIcon = true),
    FestivalRule("Parshuram Jayanti", 2, 18, "axe", ObservationTime.MADHYAHNA, hasIcon = true),
    FestivalRule("Buddha Purnima", 2, 30, "buddha", hasIcon = true),

    // Jyeshtha (3)
    FestivalRule("Vat Savitri Vrat", 3, 15, "🌳"),
    FestivalRule("Shankaracharya Jayanti", 2, 20, "🕉️"),
    FestivalRule("Surdas Jayanti", 2, 20, "🎵"),
    FestivalRule("Ganga Dussehra", 3, 25, "🌊"),
    FestivalRule("Vat Savitri Purnima", 3, 30, "🌳"),

    // Ashadha (4)
    FestivalRule("Jagannath Rath Yatra", 4, 17, "chariot", hasIcon = true),
    FestivalRule("Guru Purnima", 4, 30, "guru", hasIcon = true),

    // Shravana (5)
    FestivalRule("Sawan Shivratri", 5, 14, "lordshiv", ObservationTime.MIDNIGHT, hasIcon = true),
    FestivalRule("Hariyali Teej", 5, 18, "🌿"),
    FestivalRule("Nag Panchami", 5, 20, "🐍"),
    FestivalRule("Raksha Bandhan", 5, 30, "🪢"),

    // Bhadrapada (6)
    FestivalRule("Kajari Teej", 6, 3, "🌿"),
    FestivalRule("Bahula Chaturthi", 6, 4, "🐄"),
    FestivalRule("Hal Chhath", 6, 6, "🐂"),
    FestivalRule("Krishna Janmashtami", 6, 8, "krishna", hasIcon = true),
    FestivalRule("Hartalika Teej", 6, 18, "🌺"),
    // Madhyahna, not sunrise: Ganesha was born in the Hindu midday, so the day is the one
    // whose Madhyahna holds Chaturthi — the same rule Akshaya Tritiya above is dated by. The
    // two readings pick the same day in most years and differ in 2026, 2032 and 2033 across
    // 2020-2035; 2026 is the live one, where Chaturthi covers the 14th's Madhyahna and has
    // ended before the 15th's, while the sunrise reading pointed at the 15th.
    FestivalRule("Ganesh Chaturthi", 6, 19, "ganesh", ObservationTime.MADHYAHNA, hasIcon = true),
    FestivalRule("Rishi Panchami", 6, 20, "🌸"),
    FestivalRule("Radha Ashtami", 6, 23, "🪈"),
    FestivalRule("Anant Chaturdashi", 6, 29, "conch_shell", hasIcon = true),

    // Ashwina (7)
    FestivalRule("Jivitputrika Vrat", 7, 8, "🙏"),
    // Pitra Amavasya, which is what it is asked for by — the Amavasya that closes Pitru
    // Paksha and carries the last tarpan. Mahalaya Amavasya and Sarva Pitru Amavasya name the
    // same day; the rule's name is the string-resource key, so this is the one that reaches a
    // reader.
    FestivalRule("Pitra Amavasya", 7, 15, "🌚"),
    FestivalRule("Navratri", 7, 16, "navratri", hasIcon = true),
    FestivalRule("Durga Ashtami", 7, 23, "lion", hasIcon = true),
    // Udaya Tithi, not Pradosh Kaal. iOS moved this to Pradosh to stop Maha Navami and
    // Dussehra landing on the same day in 2026, but that collision is real -- Navami
    // prevails at sunrise on 20 Oct 2026 and Dashami prevails at Aparahna the same day --
    // and the change broke every year where they do not collide: 2025's Navami went to
    // 30 Sep, the day of Durga Ashtami, instead of 1 Oct. Pradosh did not even separate
    // them in 2026; it moved Navami onto 19 Oct, which is Durga Ashtami that year.
    FestivalRule("Maha Navami", 7, 24, "🪔", ObservationTime.APARAHNA),
    FestivalRule("Dussehra", 7, 25, "🏹", ObservationTime.APARAHNA),
    // Nishita, not sunrise: the whole observance is the Kojagara moon-viewing at night, so
    // the day is the one whose night holds Purnima. The two readings differ in eight of the
    // ten years 2023-2032 — a Purnima that begins in the afternoon and ends the next morning
    // is dated by the night it covers, not by the morning it happens to reach. 2024 was kept
    // on 16 Oct against the sunrise reading's 17th, 2025 on 6 Oct against the 7th.
    FestivalRule("Sharad Purnima", 7, 30, "🌝", ObservationTime.MIDNIGHT),

    // Kartika (8)
    // Pradosh, not sunrise: the whole observance is the evening moon sighting, so the day
    // is the one whose dusk holds Chaturthi. In 2027 Chaturthi runs 18 Oct 17:53 to 19 Oct
    // 16:43 — it fills the 18th's window and is long gone before the 19th's, while the
    // sunrise reading pointed at the 19th.
    FestivalRule("Karwa Chauth", 8, 4, "karwa-chauth", ObservationTime.PRADOSH_KAAL, hasIcon = true),
    // Pradosh: the fast breaks on sighting the stars, so the evening decides the day, the
    // same shape as Karwa Chauth four days earlier. Sunrise gave 14 Oct 2025 against the
    // observed 13th.
    FestivalRule("Ahoi Ashtami", 8, 8, "⭐", ObservationTime.PRADOSH_KAAL),
    // Pradosh: the Dhanteras puja is at dusk, like Diwali two days later. Sunrise put it a
    // day late in 2023, 2024, 2025 and 2026 alike.
    FestivalRule("Dhanteras", 8, 13, "dhanteras", ObservationTime.PRADOSH_KAAL, hasIcon = true),
    // Diwali is listed before Narak Chaturdashi although its tithi is a day later. The two
    // regularly share a Gregorian date, the scan keeps rule order within a day, and every
    // surface that shows one festival shows the first — which should be Diwali.
    FestivalRule("Diwali", 8, 15, "diwali", ObservationTime.PRADOSH_KAAL, hasIcon = true),
    FestivalRule("Narak Chaturdashi", 8, 14, "🪔"),
    FestivalRule("Govardhan Puja", 8, 16, "goverdhan", hasIcon = true),
    FestivalRule("Bhai Dooj", 8, 17, "bhaidooj", hasIcon = true),
    FestivalRule("Chhath Puja", 8, 21, "☀️"),
    FestivalRule("Gopashtami", 8, 23, "🐄"),
    // Kartika Shukla Ekadashi is listed once, in the 24 Ekadashis below, as
    // "Devutthana Ekadashi" -- the spelling PanchaangHelper's Ekadashi table
    // also uses. A second rule here under "Dev Uthani Ekadashi" put the same
    // day in the list twice, which Hindi showed as two names and Kannada,
    // Telugu and Gujarati showed as the same name twice.
    FestivalRule("Tulsi Vivah", 8, 27, "🌿"),
    FestivalRule("Guru Nanak Jayanti", 8, 30, "gurunanak", hasIcon = true),
    FestivalRule("Kartik Purnima", 8, 30, "🌝"),

    // Margashirsha (9)
    FestivalRule("Vivah Panchami", 9, 20, "💐"),

    // Pausha (10)
    FestivalRule("Pausha Purnima", 10, 30, "🌝"),

    // Magha (11)
    FestivalRule("Mauni Amavasya", 11, 15, "🤫"),
    FestivalRule("Basant Panchami", 11, 20, "spring", hasIcon = true),
    // The same day under its other name — Vasant Panchami is when Saraswati is worshipped.
    FestivalRule("Saraswati Puja", 11, 20, "📖"),
    FestivalRule("Ratha Saptami", 11, 22, "☀️"),
    FestivalRule("Magha Purnima", 11, 30, "🌝"),

    // Phalguna (12)
    FestivalRule("Maha Shivratri", 12, 14, "lordshiv", ObservationTime.MIDNIGHT, hasIcon = true),
    // Holika Dahan and Holi are NOT in this table. Neither can be expressed as "a tithi
    // prevails at an instant": Holika Dahan is the Purnima Pradosh unless Bhadra runs past
    // midnight, in which case it defers a day, and Holi is simply the day after whichever
    // day that lands on. See PanchangRepository.holiFestivals.

    // The 24 Ekadashis

    // 1. Chaitra
    FestivalRule("Papmochani Ekadashi", 1, 11, "🛕"),
    FestivalRule("Kamada Ekadashi", 1, 26, "🛕"),

    // 2. Vaishakha
    FestivalRule("Varuthini Ekadashi", 2, 11, "🛕"),
    FestivalRule("Mohini Ekadashi", 2, 26, "🛕"),

    // 3. Jyeshtha
    FestivalRule("Apara Ekadashi", 3, 11, "🛕"),
    FestivalRule("Nirjala Ekadashi", 3, 26, "🛕"),

    // 4. Ashadha
    FestivalRule("Yogini Ekadashi", 4, 11, "🛕"),
    FestivalRule("Devshayani Ekadashi", 4, 26, "🛕"),

    // 5. Shravana
    FestivalRule("Kamika Ekadashi", 5, 11, "🛕"),
    FestivalRule("Shravana Putrada Ekadashi", 5, 26, "🛕"),

    // 6. Bhadrapada
    FestivalRule("Aja Ekadashi", 6, 11, "🛕"),
    FestivalRule("Parivartini Ekadashi", 6, 26, "🛕"),

    // 7. Ashwina
    FestivalRule("Indira Ekadashi", 7, 11, "🛕"),
    FestivalRule("Papankusha Ekadashi", 7, 26, "🛕"),

    // 8. Kartika
    FestivalRule("Rama Ekadashi", 8, 11, "🛕"),
    FestivalRule("Devutthana Ekadashi", 8, 26, "🛕"),

    // 9. Margashirsha
    FestivalRule("Utpanna Ekadashi", 9, 11, "🛕"),
    FestivalRule("Mokshada Ekadashi", 9, 26, "🛕"),

    // 10. Pausha
    FestivalRule("Saphala Ekadashi", 10, 11, "🛕"),
    FestivalRule("Pausha Putrada Ekadashi", 10, 26, "🛕"),

    // 11. Magha
    FestivalRule("Shattila Ekadashi", 11, 11, "🛕"),
    FestivalRule("Jaya Ekadashi", 11, 26, "🛕"),

    // 12. Phalguna
    FestivalRule("Vijaya Ekadashi", 12, 11, "🛕"),
    FestivalRule("Amalaki Ekadashi", 12, 26, "🛕")
)

// National holidays and universally observed fixed-date festivals for India.
val allStaticFestivalRules: List<StaticFestivalRule> = listOf(
    StaticFestivalRule("New Year's Day", 1, 1, "🎆"),
    // Neither Lohri nor Makar Sankranti is here: both are solar, not fixed Gregorian dates.
    // Lohri is the eve of Maghi, so it follows the Sankranti wherever precession puts it. See
    // PanchangRepository.makarSankranti.
    StaticFestivalRule("Republic Day", 1, 26, "🇮🇳"),
    // Observed on the Gregorian date by the Maharashtra government, which is how it is
    // printed on calendars; the tithi reckoning (Phalguna Krishna Tritiya) is a separate
    // observance and not what most people look for.
    StaticFestivalRule("Shivaji Jayanti", 2, 19, "🚩"),
    StaticFestivalRule("Ambedkar Jayanti", 4, 14, "📜"),
    StaticFestivalRule("Independence Day", 8, 15, "🇮🇳"),
    StaticFestivalRule("Gandhi Jayanti", 10, 2, "🕊️"),
    // Engineer's Day — Sir M. Visvesvaraya's birth anniversary, a fixed Gregorian date.
    StaticFestivalRule("Vishveshvaraya Jayanti", 9, 15, "⚙️"),
    StaticFestivalRule("Children's Day", 11, 14, "🧒"),
    StaticFestivalRule("Christmas", 12, 25, "🎄")
)
