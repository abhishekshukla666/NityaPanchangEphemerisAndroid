package com.nityapanchangam.ephemeris.models

import java.util.Date

data class HinduFestival(
    val name: String,
    val date: Date,
    val emoji: String,
    val hasIcon: Boolean = false
)

enum class ObservationTime {
    SUNRISE, MIDNIGHT, SUNSET
}

data class FestivalRule(
    val name: String,
    val lunarMonth: Int,
    val tithiNumber: Int,
    val emoji: String,
    val observationTime: ObservationTime = ObservationTime.SUNRISE,
    val hasIcon: Boolean = false
)

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
val allFestivalRules: List<FestivalRule> = listOf(

    // Chaitra (1)
    FestivalRule("Ugadi", 1, 16, "🪷"),
    FestivalRule("Gudi Padwa", 1, 16, "🌾"),
    FestivalRule("Navratri (Chaitra)", 1, 16, "🎊"),
    FestivalRule("Ram Navami", 1, 24, "🏹"),
    FestivalRule("Hanuman Jayanti", 1, 30, "gada", hasIcon = true),

    // Vaishakha (2)
    FestivalRule("Akshaya Tritiya", 2, 18, "gold_pot", hasIcon = true),
    FestivalRule("Parshuram Jayanti", 2, 18, "axe", hasIcon = true),
    FestivalRule("Buddha Purnima", 2, 30, "buddha", hasIcon = true),

    // Jyeshtha (3)
    FestivalRule("Vat Savitri Vrat", 3, 15, "🌳"),
    FestivalRule("Ganga Dussehra", 3, 25, "🌊"),
    FestivalRule("Vat Savitri Purnima", 3, 30, "🌳"),

    // Ashadha (4)
    FestivalRule("Jagannath Rath Yatra", 4, 17, "chariot", hasIcon = true),
    FestivalRule("Guru Purnima", 4, 30, "sacred", hasIcon = true),

    // Shravana (5)
    FestivalRule("Sawan Shivratri", 5, 14, "lordshiv", ObservationTime.MIDNIGHT, hasIcon = true),
    FestivalRule("Hariyali Teej", 5, 18, "🌿"),
    FestivalRule("Nag Panchami", 5, 20, "🐍"),
    FestivalRule("Raksha Bandhan", 5, 30, "🪢"),

    // Bhadrapada (6)
    FestivalRule("Krishna Janmashtami", 6, 8, "krishna", hasIcon = true),
    FestivalRule("Hartalika Teej", 6, 18, "🌺"),
    FestivalRule("Ganesh Chaturthi", 6, 19, "ganesh", hasIcon = true),
    FestivalRule("Rishi Panchami", 6, 20, "🌸"),
    FestivalRule("Radha Ashtami", 6, 23, "🪈"),
    FestivalRule("Anant Chaturdashi", 6, 29, "conch_shell", hasIcon = true),

    // Ashwina (7)
    FestivalRule("Mahalaya Amavasya", 7, 15, "🌚"),
    FestivalRule("Navratri", 7, 16, "navratri", hasIcon = true),
    FestivalRule("Durga Ashtami", 7, 23, "lion", hasIcon = true),
    FestivalRule("Maha Navami", 7, 24, "🪔"),
    FestivalRule("Dussehra", 7, 25, "🏹"),
    FestivalRule("Sharad Purnima", 7, 30, "🌝"),

    // Kartika (8)
    FestivalRule("Karwa Chauth", 8, 4, "🌝"),
    FestivalRule("Ahoi Ashtami", 8, 8, "⭐"),
    FestivalRule("Dhanteras", 8, 13, "dhanteras", hasIcon = true),
    FestivalRule("Narak Chaturdashi", 8, 14, "🪔"),
    FestivalRule("Diwali", 8, 15, "diwali", hasIcon = true),
    FestivalRule("Govardhan Puja", 8, 16, "🐄"),
    FestivalRule("Bhai Dooj", 8, 17, "bhaidooj", hasIcon = true),
    FestivalRule("Chhath Puja", 8, 21, "☀️"),
    FestivalRule("Dev Uthani Ekadashi", 8, 26, "🛕"),
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
    FestivalRule("Magha Purnima", 11, 30, "🌝"),

    // Phalguna (12)
    FestivalRule("Maha Shivratri", 12, 14, "lordshiv", ObservationTime.MIDNIGHT, hasIcon = true),
    FestivalRule("Holika Dahan", 12, 29, "🔥"),
    FestivalRule("Holi", 12, 30, "holi", hasIcon = true),

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
    StaticFestivalRule("Lohri", 1, 13, "🔥"),
    StaticFestivalRule("Makar Sankranti", 1, 14, "🌾"),
    StaticFestivalRule("Republic Day", 1, 26, "🇮🇳"),
    StaticFestivalRule("Ambedkar Jayanti", 4, 14, "📜"),
    StaticFestivalRule("Independence Day", 8, 15, "🇮🇳"),
    StaticFestivalRule("Gandhi Jayanti", 10, 2, "🕊️"),
    StaticFestivalRule("Children's Day", 11, 14, "🧒"),
    StaticFestivalRule("Christmas", 12, 25, "🎄")
)
