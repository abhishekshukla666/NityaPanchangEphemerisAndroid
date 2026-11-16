package com.nityapanchangam.ephemeris

import com.nityapanchangam.ephemeris.models.EkadashiDating
import com.nityapanchangam.ephemeris.models.EkadashiTradition
import kotlin.math.floor

/**
 * Which day an Ekadashi is kept, under each tradition. A pure function of the tithis at the
 * reference sunrises and arunodayas around it, so the rules can be tested on the JVM against
 * published dates -- the ephemeris itself is native code and cannot run there.
 *
 * Fitted to, and tested against, Drik Panchang's Smartha and ISKCON lists for 2023-2028: 149
 * Ekadashis, of which the two traditions part on 30, every one of them one of the cases below.
 * Identical to iOS's EphemerisPanchaangRepository.ekadashiOffset.
 *
 * A Smarta keeps:
 *  - **the Udaya day** -- the first whose sunrise the Ekadashi holds -- ordinarily;
 *  - **the second day of a vriddhi** Ekadashi, one holding two sunrises, as published for Amalaki
 *    2023, Nirjala 2024, Rama 2024 and Vijaya 2027, under both traditions;
 *  - **the day before a trisprisha** -- when Ekadashi, Dwadashi and Trayodashi all touch the Udaya
 *    day, the Smartha list takes the day the Ekadashi began and lists the trisprisha day as the
 *    second, "Gauna", one. Six times in six years, and it never once kept a trisprisha day.
 *
 * A Vaishnava starts from the Smarta day and moves to the next -- the Dwadashi -- on any of:
 *  - **Dashami at arunodaya**: Dashami still running ninety-six minutes before sunrise. This covers
 *    the trisprisha and kshaya days too, whose Smarta day has Dashami at sunrise itself;
 *  - **Vyanjuli**: Dwadashi holding two sunrises;
 *  - **Pakshavardhini**: the Purnima or Amavasya ending the paksha holding two sunrises.
 *
 * The Gaudiya calendar's other Mahadvadashis, set by the nakshatra on the Dwadashi, moved no
 * Ekadashi in those six years and are not modelled.
 */
internal object EkadashiDay {

    /**
     * Days from the Udaya day to the vrat.
     *
     * @param tithi the Ekadashi: 11 (Krishna) or 26 (Shukla).
     * @param sunriseTithi the tithi at the reference sunrise `k` days after the Udaya day.
     * @param arunodayaTithi the tithi at arunodaya `k` days after the Udaya day.
     */
    fun offset(
        tithi: Int,
        tradition: EkadashiTradition,
        sunriseTithi: (Int) -> Int,
        arunodayaTithi: (Int) -> Int,
    ): Int {
        val smarta = when (sunriseTithi(1)) {
            tithi -> 1          // vriddhi
            tithi + 2 -> -1     // trisprisha: Trayodashi by the next sunrise
            else -> 0
        }
        if (tradition != EkadashiTradition.VAISHNAVA) return smarta
        return smarta + if (vaishnavaMovesOn(smarta, tithi, sunriseTithi, arunodayaTithi)) 1 else 0
    }

    /**
     * The day or days an Ekadashi goes on, as offsets from its Udaya day: one, untagged, for a
     * single tradition or where both keep the same day; two, each tagged, where they part.
     */
    fun days(
        tithi: Int,
        ekadashis: EkadashiDating,
        sunriseTithi: (Int) -> Int,
        arunodayaTithi: (Int) -> Int,
    ): List<Pair<Int, EkadashiTradition?>> = tagged(ekadashis) { offset(tithi, it, sunriseTithi, arunodayaTithi) }

    /** [days], for a kshaya Ekadashi -- see [kshayaOffset]. */
    fun kshayaDays(ekadashis: EkadashiDating): List<Pair<Int, EkadashiTradition?>> =
        tagged(ekadashis) { kshayaOffset(it) }

    private fun tagged(
        ekadashis: EkadashiDating,
        offset: (EkadashiTradition) -> Int,
    ): List<Pair<Int, EkadashiTradition?>> = when (ekadashis) {
        is EkadashiDating.Tradition -> listOf(offset(ekadashis.tradition) to null)
        EkadashiDating.Both -> {
            val smarta = offset(EkadashiTradition.SMARTA)
            val vaishnava = offset(EkadashiTradition.VAISHNAVA)
            if (smarta == vaishnava) listOf(smarta to null)
            else listOf(smarta to EkadashiTradition.SMARTA, vaishnava to EkadashiTradition.VAISHNAVA)
        }
    }

    /** A kshaya Ekadashi -- one holding no sunrise -- is the Smarta's on the day it runs, with
     *  Dashami at that day's sunrise; so a Vaishnava's is always the next. */
    fun kshayaOffset(tradition: EkadashiTradition): Int =
        if (tradition == EkadashiTradition.VAISHNAVA) 1 else 0

    private fun vaishnavaMovesOn(
        day: Int,
        tithi: Int,
        sunriseTithi: (Int) -> Int,
        arunodayaTithi: (Int) -> Int,
    ): Boolean {
        if (arunodayaTithi(day) != tithi) return true
        val dwadashi = tithi + 1
        if (sunriseTithi(day + 1) == dwadashi && sunriseTithi(day + 2) == dwadashi) return true
        val pakshaEnd = tithi + 4
        for (k in 1..6) {
            if (sunriseTithi(day + k) == pakshaEnd) return sunriseTithi(day + k + 1) == pakshaEnd
        }
        return false
    }

    /**
     * The lunation an instant falls in, counted from the new moon of 6 January 2000. Mean, not
     * true, but an Ekadashi sits four days or more from either new moon, so the two cannot
     * disagree about one. Ekadashis are keyed by it rather than the calendar year: Saphala and
     * Pausha Putrada can each fall twice in one year, and a name-and-year key dropped the second.
     */
    fun lunation(jd: Double): Int = floor((jd - 2_451_550.098) / 29.530_588_853).toInt()
}
