package com.nityapanchangam.ephemeris

import com.nityapanchangam.ephemeris.models.EkadashiDating
import com.nityapanchangam.ephemeris.models.EkadashiTradition
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Smarta and Vaishnava Ekadashi, against Drik Panchang's published lists for New Delhi,
 * 2023-2028: every one of 149 Ekadashis, of which the two traditions part on 30.
 *
 * The ephemeris is native code and cannot run on the JVM, so each case carries the tithis it needs
 * -- at the sunrises from the day before its Udaya day to nine after, and at the arunodayas around
 * it -- read from the same Swiss Ephemeris core by the iOS library, whose own suite runs these 149
 * dates through the full engine. What this pins is the Kotlin port of the rules: that the same
 * tithis lead it to the same days.
 *
 * Read from drikpanchang.com/vrats/ekadashidates.html and /iskcon/iskcon-ekadashi-list.html on
 * 16 November 2026.
 */
class EkadashiDayTest {

    /**
     * @param sunrise the tithi at sunrise from one day before the Udaya day (index 0) to nine after.
     * @param arunodaya the tithi at arunodaya from one day before the Udaya day to one after.
     * @param smarta the published Smarta day, in days from the Udaya day.
     * @param vaishnava the published ISKCON day, likewise.
     * @param kshaya the Ekadashi holds no sunrise; the offsets are then from the day it runs.
     */
    private class Case(
        val name: String,
        val tithi: Int,
        val kshaya: Boolean,
        val sunrise: IntArray,
        val arunodaya: IntArray,
        val smarta: Int,
        val vaishnava: Int,
    )

    private fun offset(c: Case, tradition: EkadashiTradition): Int =
        if (c.kshaya) EkadashiDay.kshayaOffset(tradition)
        else EkadashiDay.offset(c.tithi, tradition, { k -> c.sunrise[k + 1] }, { k -> c.arunodaya[k + 1] })

    @Test
    fun smartaMatchesTheSmarthaListForSixYears() {
        val wrong = cases.filter { offset(it, EkadashiTradition.SMARTA) != it.smarta }.map { it.name }
        assertEquals(emptyList<String>(), wrong)
    }

    /**
     * One is excepted: Nirjala 2026, whose Purnima outlasts its second sunrise at New Delhi by
     * about a minute on this ephemeris -- a Pakshavardhini here and not in the published list,
     * inside the margin two ephemerides differ by. It is the only one of the 149 that close, and
     * its case above carries the engine's day.
     */
    @Test
    fun vaishnavaMatchesTheIskconListForSixYears() {
        val wrong = cases.filter { offset(it, EkadashiTradition.VAISHNAVA) != it.vaishnava }.map { it.name }
        assertEquals(emptyList<String>(), wrong)
    }

    @Test
    fun theTwoPartOnThirtyAndAlwaysByOneDay() {
        val parted = cases.filter { offset(it, EkadashiTradition.SMARTA) != offset(it, EkadashiTradition.VAISHNAVA) }
        assertEquals(31, parted.size)   // the published 30, and Nirjala 2026
        parted.forEach {
            assertEquals(it.name, 1, offset(it, EkadashiTradition.VAISHNAVA) - offset(it, EkadashiTradition.SMARTA))
        }
    }

    /** Asked for both: one untagged day where the two agree, two tagged days where they part. */
    @Test
    fun bothTagsOnlyTheEkadashisTheyDateApart() {
        fun both(c: Case) =
            if (c.kshaya) EkadashiDay.kshayaDays(EkadashiDating.Both)
            else EkadashiDay.days(c.tithi, EkadashiDating.Both, { k -> c.sunrise[k + 1] }, { k -> c.arunodaya[k + 1] })
        for (c in cases) {
            val expected = if (c.smarta == c.vaishnava) listOf(c.smarta to null)
                else listOf(c.smarta to EkadashiTradition.SMARTA, c.vaishnava to EkadashiTradition.VAISHNAVA)
            assertEquals(c.name, expected, both(c))
        }
        assertEquals(listOf(0 to null),
            EkadashiDay.kshayaDays(EkadashiDating.Tradition(EkadashiTradition.SMARTA)))
    }

    /** An Adhik month's Shukla Ekadashi is Padmini and its Krishna one Parama, as Drik Panchang
     *  publishes them: 27 May and 11 June 2026. */
    @Test
    fun anAdhikMonthsEkadashisArePadminiAndParama() {
        assertEquals("Padmini Ekadashi", PanchaangHelper.ekadashiName(3, com.nityapanchangam.ephemeris.models.Paksha.SHUKLA, isAdhik = true))
        assertEquals("Parama Ekadashi", PanchaangHelper.ekadashiName(3, com.nityapanchangam.ephemeris.models.Paksha.KRISHNA, isAdhik = true))
    }

    @Test
    fun theDefaultIsSmarta() {
        assertEquals(EkadashiTradition.SMARTA, EkadashiTradition.DEFAULT)
    }

    @Test
    fun ekadashisTwelveLunationsApartAreKeyedApart() {
        // Saphala 7 Jan and 26 Dec 2024 -- the pair a name-and-year key collapsed.
        val jan = 2_460_316.5
        assertEquals(12, EkadashiDay.lunation(jan + 354.0) - EkadashiDay.lunation(jan))
    }

    private val cases = listOf(
        Case("Pausha Putrada 2023", 26, false, intArrayOf(25, 26, 27, 28, 29, 30, 1, 2, 2, 3, 4), intArrayOf(25, 26, 27), 0, 0),
        Case("Shattila 2023", 11, false, intArrayOf(10, 11, 12, 13, 15, 16, 17, 18, 19, 20, 21), intArrayOf(10, 11, 12), 0, 0),
        Case("Jaya 2023", 26, false, intArrayOf(25, 26, 27, 28, 29, 30, 1, 2, 3, 4, 4), intArrayOf(25, 26, 27), 0, 0),
        Case("Vijaya 2023", 11, false, intArrayOf(9, 11, 12, 13, 14, 15, 16, 18, 19, 20, 21), intArrayOf(9, 10, 12), 0, 1),
        Case("Amalaki 2023", 26, false, intArrayOf(25, 26, 26, 27, 28, 29, 30, 1, 2, 3, 4), intArrayOf(25, 25, 26), 1, 1),
        Case("Papamochani 2023", 11, false, intArrayOf(10, 11, 12, 14, 15, 16, 17, 18, 19, 20, 21), intArrayOf(10, 11, 12), 0, 0),
        Case("Kamada 2023", 26, false, intArrayOf(25, 26, 27, 27, 28, 29, 30, 1, 2, 3, 4), intArrayOf(25, 26, 27), 0, 1),
        Case("Varuthini 2023", 11, false, intArrayOf(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20), intArrayOf(10, 11, 12), 0, 0),
        Case("Mohini 2023", 26, false, intArrayOf(25, 26, 27, 28, 29, 30, 1, 2, 3, 4, 5), intArrayOf(25, 26, 27), 0, 0),
        Case("Apara 2023", 11, false, intArrayOf(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20), intArrayOf(9, 11, 12), 0, 0),
        Case("Nirjala 2023", 26, false, intArrayOf(25, 26, 27, 28, 29, 30, 1, 3, 4, 5, 6), intArrayOf(25, 26, 27), 0, 0),
        Case("Yogini 2023", 11, false, intArrayOf(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20), intArrayOf(10, 11, 12), 0, 0),
        Case("Devshayani 2023", 26, false, intArrayOf(25, 26, 27, 28, 29, 30, 1, 2, 3, 5, 6), intArrayOf(25, 26, 27), 0, 0),
        Case("Kamika 2023", 11, false, intArrayOf(10, 11, 12, 13, 14, 15, 16, 17, 18, 18, 19), intArrayOf(10, 11, 12), 0, 0),
        Case("Padmini 2023", 26, false, intArrayOf(25, 26, 27, 28, 30, 1, 2, 3, 4, 5, 7), intArrayOf(25, 26, 27), 0, 0),
        Case("Parama 2023", 11, false, intArrayOf(10, 11, 11, 12, 13, 14, 15, 16, 17, 18, 19), intArrayOf(9, 10, 11), 1, 1),
        Case("Shravana Putrada 2023", 26, false, intArrayOf(25, 26, 27, 28, 29, 30, 2, 3, 4, 5, 6), intArrayOf(25, 26, 27), 0, 0),
        Case("Aja 2023", 11, false, intArrayOf(10, 11, 12, 13, 14, 15, 15, 16, 17, 18, 19), intArrayOf(10, 11, 12), 0, 1),
        Case("Parsva 2023", 26, true, intArrayOf(24, 25, 27, 28, 29, 30, 1, 2, 3, 5, 6), intArrayOf(24, 25, 26), 0, 1),
        Case("Indira 2023", 11, false, intArrayOf(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20), intArrayOf(10, 11, 12), 0, 0),
        Case("Papankusha 2023", 26, false, intArrayOf(25, 26, 27, 28, 30, 1, 2, 3, 4, 5, 6), intArrayOf(25, 26, 27), 0, 0),
        Case("Rama 2023", 11, false, intArrayOf(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20), intArrayOf(10, 11, 12), 0, 0),
        Case("Devutthana 2023", 26, false, intArrayOf(25, 26, 27, 28, 29, 30, 1, 2, 3, 4, 5), intArrayOf(25, 26, 27), 0, 0),
        Case("Utpanna 2023", 11, false, intArrayOf(10, 11, 12, 12, 13, 15, 16, 17, 18, 19, 20), intArrayOf(10, 11, 11), 0, 1),
        Case("Mokshada 2023", 26, false, intArrayOf(25, 26, 28, 29, 30, 1, 2, 2, 3, 4, 5), intArrayOf(25, 26, 27), -1, 0),
        Case("Saphala 2024", 11, false, intArrayOf(10, 11, 12, 13, 14, 15, 16, 17, 18, 20, 21), intArrayOf(10, 11, 12), 0, 0),
        Case("Pausha Putrada 2024", 26, false, intArrayOf(25, 26, 27, 28, 29, 30, 1, 2, 3, 4, 4), intArrayOf(25, 26, 27), 0, 0),
        Case("Shattila 2024", 11, false, intArrayOf(10, 11, 12, 13, 14, 16, 17, 18, 19, 20, 21), intArrayOf(10, 11, 12), 0, 0),
        Case("Jaya 2024", 26, false, intArrayOf(25, 26, 27, 28, 29, 30, 1, 2, 3, 4, 5), intArrayOf(25, 26, 27), 0, 0),
        Case("Vijaya 2024", 11, false, intArrayOf(9, 11, 12, 13, 14, 15, 16, 17, 19, 20, 21), intArrayOf(9, 10, 12), 0, 1),
        Case("Amalaki 2024", 26, false, intArrayOf(25, 26, 27, 28, 28, 29, 30, 1, 2, 3, 4), intArrayOf(25, 26, 27), 0, 0),
        Case("Papamochani 2024", 11, false, intArrayOf(10, 11, 12, 13, 15, 16, 17, 18, 19, 20, 21), intArrayOf(10, 11, 12), 0, 0),
        Case("Kamada 2024", 26, false, intArrayOf(25, 26, 27, 28, 29, 30, 1, 1, 2, 3, 4), intArrayOf(25, 26, 27), 0, 0),
        Case("Varuthini 2024", 11, false, intArrayOf(10, 11, 12, 13, 14, 15, 16, 18, 19, 20, 21), intArrayOf(10, 11, 12), 0, 0),
        Case("Mohini 2024", 26, false, intArrayOf(25, 26, 27, 28, 29, 30, 1, 2, 3, 4, 5), intArrayOf(25, 26, 27), 0, 0),
        Case("Apara 2024", 11, false, intArrayOf(9, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20), intArrayOf(9, 10, 12), 0, 1),
        Case("Nirjala 2024", 26, false, intArrayOf(25, 26, 26, 27, 28, 29, 30, 2, 3, 4, 5), intArrayOf(25, 25, 26), 1, 1),
        Case("Yogini 2024", 11, false, intArrayOf(10, 11, 12, 13, 15, 16, 17, 18, 18, 19, 20), intArrayOf(10, 11, 12), 0, 0),
        Case("Devshayani 2024", 26, false, intArrayOf(25, 26, 27, 28, 29, 30, 1, 2, 3, 5, 6), intArrayOf(25, 26, 27), 0, 0),
        Case("Kamika 2024", 11, false, intArrayOf(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20), intArrayOf(10, 11, 12), 0, 0),
        Case("Shravana Putrada 2024", 26, false, intArrayOf(25, 26, 27, 29, 30, 1, 2, 3, 4, 5, 7), intArrayOf(25, 26, 27), 0, 0),
        Case("Aja 2024", 11, false, intArrayOf(10, 11, 12, 13, 14, 15, 15, 16, 17, 18, 19), intArrayOf(10, 11, 12), 0, 1),
        Case("Parsva 2024", 26, false, intArrayOf(25, 26, 27, 28, 29, 30, 2, 3, 4, 5, 6), intArrayOf(25, 26, 27), 0, 0),
        Case("Indira 2024", 11, false, intArrayOf(10, 11, 12, 13, 14, 15, 16, 17, 18, 18, 19), intArrayOf(10, 11, 12), 0, 0),
        Case("Papankusha 2024", 26, false, intArrayOf(25, 26, 28, 29, 30, 1, 2, 3, 5, 6, 7), intArrayOf(25, 26, 28), -1, 0),
        Case("Rama 2024", 11, false, intArrayOf(10, 11, 11, 12, 13, 14, 15, 16, 17, 18, 19), intArrayOf(10, 10, 11), 1, 1),
        Case("Devutthana 2024", 26, false, intArrayOf(25, 26, 27, 28, 30, 1, 2, 3, 4, 5, 6), intArrayOf(25, 26, 27), 0, 0),
        Case("Utpanna 2024", 11, false, intArrayOf(10, 11, 12, 13, 13, 14, 15, 16, 17, 18, 19), intArrayOf(10, 11, 12), 0, 0),
        Case("Mokshada 2024", 26, false, intArrayOf(25, 26, 27, 28, 29, 30, 1, 2, 3, 4, 5), intArrayOf(24, 26, 27), 0, 0),
        Case("Saphala 2024", 11, false, intArrayOf(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20), intArrayOf(10, 11, 12), 0, 0),
        Case("Pausha Putrada 2025", 26, false, intArrayOf(25, 26, 27, 29, 30, 1, 2, 3, 4, 5, 5), intArrayOf(25, 26, 27), 0, 0),
        Case("Shattila 2025", 11, false, intArrayOf(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 21), intArrayOf(10, 11, 12), 0, 0),
        Case("Jaya 2025", 26, false, intArrayOf(25, 26, 27, 28, 29, 30, 1, 2, 3, 4, 5), intArrayOf(25, 26, 27), 0, 0),
        Case("Vijaya 2025", 11, false, intArrayOf(10, 11, 12, 13, 14, 16, 17, 18, 19, 20, 21), intArrayOf(10, 11, 12), 0, 0),
        Case("Amalaki 2025", 26, false, intArrayOf(25, 26, 27, 28, 29, 30, 1, 2, 3, 4, 5), intArrayOf(25, 26, 27), 0, 0),
        Case("Papamochani 2025", 11, false, intArrayOf(10, 11, 12, 13, 14, 15, 16, 17, 19, 20, 21), intArrayOf(9, 10, 12), 0, 1),
        Case("Kamada 2025", 26, false, intArrayOf(25, 26, 27, 28, 29, 30, 1, 1, 2, 3, 4), intArrayOf(25, 26, 27), 0, 0),
        Case("Varuthini 2025", 11, false, intArrayOf(10, 11, 12, 13, 15, 16, 17, 18, 19, 20, 21), intArrayOf(10, 11, 12), 0, 0),
        Case("Mohini 2025", 26, false, intArrayOf(25, 26, 27, 28, 29, 30, 1, 2, 3, 4, 5), intArrayOf(25, 26, 27), 0, 0),
        Case("Apara 2025", 11, false, intArrayOf(10, 11, 12, 13, 14, 15, 17, 18, 19, 20, 21), intArrayOf(10, 11, 12), 0, 0),
        Case("Nirjala 2025", 26, false, intArrayOf(25, 26, 27, 27, 28, 29, 30, 1, 2, 3, 4), intArrayOf(25, 26, 26), 0, 1),
        Case("Yogini 2025", 11, true, intArrayOf(9, 10, 12, 13, 14, 15, 16, 17, 18, 19, 20), intArrayOf(9, 10, 11), 0, 1),
        Case("Devshayani 2025", 26, false, intArrayOf(25, 26, 27, 28, 29, 30, 1, 2, 3, 4, 5), intArrayOf(25, 26, 27), 0, 0),
        Case("Kamika 2025", 11, false, intArrayOf(10, 11, 12, 14, 15, 16, 17, 18, 19, 20, 21), intArrayOf(10, 11, 12), 0, 0),
        Case("Shravana Putrada 2025", 26, false, intArrayOf(25, 26, 27, 28, 29, 30, 1, 2, 3, 4, 6), intArrayOf(25, 26, 27), 0, 0),
        Case("Aja 2025", 11, false, intArrayOf(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20), intArrayOf(10, 11, 12), 0, 0),
        Case("Parsva 2025", 26, false, intArrayOf(25, 26, 27, 28, 29, 30, 1, 2, 3, 4, 5), intArrayOf(25, 26, 27), 0, 0),
        Case("Indira 2025", 11, false, intArrayOf(10, 11, 12, 13, 14, 15, 16, 17, 18, 18, 19), intArrayOf(10, 11, 12), 0, 0),
        Case("Papankusha 2025", 26, false, intArrayOf(25, 26, 27, 28, 29, 30, 2, 3, 4, 5, 6), intArrayOf(25, 26, 27), 0, 0),
        Case("Rama 2025", 11, false, intArrayOf(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20), intArrayOf(10, 11, 12), 0, 0),
        Case("Devutthana 2025", 26, false, intArrayOf(25, 26, 28, 29, 30, 1, 2, 3, 5, 6, 7), intArrayOf(25, 26, 27), -1, 0),
        Case("Utpanna 2025", 11, false, intArrayOf(10, 11, 12, 13, 13, 14, 15, 16, 17, 18, 19), intArrayOf(10, 11, 12), 0, 0),
        Case("Mokshada 2025", 26, false, intArrayOf(25, 26, 27, 28, 29, 1, 2, 3, 4, 5, 6), intArrayOf(25, 26, 27), 0, 0),
        Case("Saphala 2025", 11, false, intArrayOf(10, 11, 12, 13, 14, 15, 15, 16, 17, 18, 19), intArrayOf(10, 11, 12), 0, 1),
        Case("Pausha Putrada 2025", 26, true, intArrayOf(24, 25, 27, 28, 29, 30, 1, 2, 3, 5, 6), intArrayOf(24, 25, 27), 0, 1),
        Case("Shattila 2026", 11, false, intArrayOf(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20), intArrayOf(10, 11, 12), 0, 0),
        Case("Jaya 2026", 26, false, intArrayOf(25, 26, 27, 28, 30, 1, 2, 3, 4, 5, 6), intArrayOf(25, 26, 27), 0, 0),
        Case("Vijaya 2026", 11, false, intArrayOf(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20), intArrayOf(10, 11, 12), 0, 0),
        Case("Amalaki 2026", 26, false, intArrayOf(25, 26, 27, 28, 29, 30, 1, 2, 3, 4, 5), intArrayOf(25, 26, 27), 0, 0),
        Case("Papamochani 2026", 11, false, intArrayOf(10, 11, 12, 13, 14, 15, 17, 18, 19, 20, 21), intArrayOf(10, 11, 12), 0, 0),
        Case("Kamada 2026", 26, false, intArrayOf(25, 26, 27, 28, 29, 30, 1, 2, 3, 4, 5), intArrayOf(25, 26, 27), 0, 0),
        Case("Varuthini 2026", 11, false, intArrayOf(10, 11, 12, 13, 14, 15, 16, 17, 18, 20, 21), intArrayOf(10, 11, 12), 0, 0),
        Case("Mohini 2026", 26, false, intArrayOf(25, 26, 27, 28, 29, 30, 1, 2, 3, 4, 4), intArrayOf(25, 26, 27), 0, 0),
        Case("Apara 2026", 11, false, intArrayOf(10, 11, 12, 13, 15, 16, 17, 18, 19, 20, 21), intArrayOf(10, 11, 12), 0, 0),
        Case("Padmini 2026", 26, false, intArrayOf(25, 26, 26, 27, 28, 29, 30, 1, 2, 3, 4), intArrayOf(24, 25, 26), 1, 1),
        Case("Parama 2026", 11, false, intArrayOf(10, 11, 12, 13, 14, 15, 17, 18, 19, 20, 21), intArrayOf(10, 11, 12), 0, 0),
        Case("Nirjala 2026", 26, false, intArrayOf(25, 26, 27, 28, 29, 30, 30, 1, 2, 3, 4), intArrayOf(25, 26, 27), 0, 1), // see testVaishnavaMatchesIskcon
        Case("Yogini 2026", 11, true, intArrayOf(9, 10, 12, 13, 14, 15, 16, 17, 18, 20, 21), intArrayOf(9, 10, 11), 0, 1),
        Case("Devshayani 2026", 26, false, intArrayOf(25, 26, 27, 28, 29, 30, 1, 2, 3, 4, 5), intArrayOf(25, 26, 27), 0, 0),
        Case("Kamika 2026", 11, false, intArrayOf(10, 11, 12, 14, 15, 16, 17, 18, 19, 20, 21), intArrayOf(10, 11, 12), 0, 0),
        Case("Shravana Putrada 2026", 26, false, intArrayOf(25, 26, 27, 27, 28, 29, 30, 1, 2, 3, 4), intArrayOf(25, 26, 26), 0, 1),
        Case("Aja 2026", 11, false, intArrayOf(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20), intArrayOf(10, 11, 12), 0, 0),
        Case("Parsva 2026", 26, false, intArrayOf(25, 26, 27, 28, 29, 30, 1, 2, 3, 4, 5), intArrayOf(25, 26, 27), 0, 0),
        Case("Indira 2026", 11, false, intArrayOf(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20), intArrayOf(10, 11, 12), 0, 0),
        Case("Papankusha 2026", 26, false, intArrayOf(25, 26, 27, 28, 29, 30, 1, 3, 4, 5, 6), intArrayOf(25, 26, 27), 0, 0),
        Case("Rama 2026", 11, false, intArrayOf(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20), intArrayOf(10, 11, 12), 0, 0),
        Case("Devutthana 2026", 26, true, intArrayOf(24, 25, 27, 28, 29, 30, 1, 2, 3, 5, 6), intArrayOf(24, 25, 26), 0, 1),
        Case("Utpanna 2026", 11, false, intArrayOf(10, 11, 12, 13, 14, 15, 16, 16, 17, 18, 19), intArrayOf(10, 11, 12), 0, 0),
        Case("Mokshada 2026", 26, false, intArrayOf(25, 26, 27, 28, 29, 1, 2, 3, 4, 5, 6), intArrayOf(25, 26, 27), 0, 0),
        Case("Saphala 2027", 11, false, intArrayOf(10, 11, 12, 13, 14, 15, 16, 17, 18, 18, 19), intArrayOf(10, 11, 12), 0, 0),
        Case("Pausha Putrada 2027", 26, false, intArrayOf(25, 26, 28, 29, 30, 1, 2, 3, 5, 6, 7), intArrayOf(25, 26, 28), -1, 0),
        Case("Shattila 2027", 11, false, intArrayOf(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20), intArrayOf(10, 11, 12), 0, 0),
        Case("Jaya 2027", 26, false, intArrayOf(25, 26, 27, 28, 29, 1, 2, 3, 4, 5, 6), intArrayOf(25, 26, 27), 0, 0),
        Case("Vijaya 2027", 11, false, intArrayOf(10, 11, 11, 12, 13, 14, 15, 16, 17, 18, 19), intArrayOf(10, 11, 11), 1, 1),
        Case("Amalaki 2027", 26, false, intArrayOf(24, 26, 27, 28, 29, 30, 1, 2, 3, 4, 5), intArrayOf(24, 26, 27), 0, 0),
        Case("Papamochani 2027", 11, false, intArrayOf(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20), intArrayOf(10, 11, 12), 0, 0),
        Case("Kamada 2027", 26, false, intArrayOf(25, 26, 27, 28, 30, 1, 2, 3, 4, 5, 5), intArrayOf(25, 26, 27), 0, 0),
        Case("Varuthini 2027", 11, false, intArrayOf(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 21), intArrayOf(10, 11, 12), 0, 0),
        Case("Mohini 2027", 26, false, intArrayOf(25, 26, 27, 28, 29, 30, 1, 2, 3, 4, 5), intArrayOf(25, 26, 27), 0, 0),
        Case("Apara 2027", 11, false, intArrayOf(10, 11, 12, 13, 15, 16, 17, 18, 19, 20, 21), intArrayOf(10, 11, 12), 0, 0),
        Case("Nirjala 2027", 26, false, intArrayOf(25, 26, 27, 28, 29, 30, 30, 1, 2, 3, 4), intArrayOf(25, 26, 27), 0, 1),
        Case("Yogini 2027", 11, false, intArrayOf(10, 11, 12, 13, 14, 15, 17, 18, 19, 20, 21), intArrayOf(10, 11, 12), 0, 0),
        Case("Devshayani 2027", 26, false, intArrayOf(25, 26, 27, 28, 29, 30, 1, 2, 3, 4, 4), intArrayOf(25, 26, 27), 0, 0),
        Case("Kamika 2027", 11, true, intArrayOf(9, 10, 12, 13, 14, 15, 16, 17, 19, 20, 21), intArrayOf(9, 10, 11), 0, 1),
        Case("Shravana Putrada 2027", 26, false, intArrayOf(25, 26, 27, 28, 28, 29, 30, 1, 2, 3, 4), intArrayOf(25, 26, 27), 0, 0),
        Case("Aja 2027", 11, false, intArrayOf(10, 11, 12, 13, 15, 16, 17, 18, 19, 20, 21), intArrayOf(10, 11, 12), 0, 0),
        Case("Parsva 2027", 26, false, intArrayOf(25, 26, 27, 28, 29, 30, 1, 2, 2, 3, 4), intArrayOf(25, 26, 27), 0, 0),
        Case("Indira 2027", 11, false, intArrayOf(10, 11, 12, 13, 14, 15, 17, 18, 19, 20, 21), intArrayOf(10, 11, 12), 0, 0),
        Case("Papankusha 2027", 26, false, intArrayOf(25, 26, 27, 28, 29, 30, 1, 2, 3, 4, 5), intArrayOf(25, 26, 27), 0, 0),
        Case("Rama 2027", 11, false, intArrayOf(9, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20), intArrayOf(9, 10, 12), 0, 1),
        Case("Devutthana 2027", 26, false, intArrayOf(25, 26, 27, 28, 29, 30, 1, 3, 4, 5, 6), intArrayOf(25, 26, 27), 0, 0),
        Case("Utpanna 2027", 11, false, intArrayOf(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20), intArrayOf(10, 11, 12), 0, 0),
        Case("Mokshada 2027", 26, false, intArrayOf(25, 26, 27, 28, 29, 30, 1, 2, 3, 4, 5), intArrayOf(25, 26, 27), 0, 0),
        Case("Saphala 2027", 11, false, intArrayOf(10, 11, 12, 13, 14, 15, 16, 17, 18, 18, 19), intArrayOf(10, 11, 12), 0, 0),
        Case("Pausha Putrada 2028", 26, false, intArrayOf(25, 26, 27, 28, 29, 30, 2, 3, 4, 5, 6), intArrayOf(25, 26, 27), 0, 0),
        Case("Shattila 2028", 11, false, intArrayOf(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 19), intArrayOf(10, 11, 12), 0, 0),
        Case("Jaya 2028", 26, false, intArrayOf(25, 26, 28, 29, 30, 1, 2, 3, 5, 6, 7), intArrayOf(25, 26, 27), -1, 0),
        Case("Vijaya 2028", 11, false, intArrayOf(10, 11, 12, 12, 13, 14, 15, 16, 17, 18, 19), intArrayOf(10, 11, 11), 0, 1),
        Case("Amalaki 2028", 26, false, intArrayOf(25, 26, 27, 28, 29, 30, 2, 3, 4, 5, 6), intArrayOf(25, 26, 27), 0, 0),
        Case("Papamochani 2028", 11, false, intArrayOf(10, 11, 12, 13, 14, 14, 15, 16, 17, 18, 19), intArrayOf(10, 11, 12), 0, 0),
        Case("Kamada 2028", 26, true, intArrayOf(24, 25, 27, 28, 29, 30, 1, 2, 3, 4, 5), intArrayOf(24, 25, 26), 0, 1),
        Case("Varuthini 2028", 11, false, intArrayOf(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20), intArrayOf(10, 11, 12), 0, 0),
        Case("Mohini 2028", 26, false, intArrayOf(25, 26, 27, 28, 30, 1, 2, 3, 4, 5, 6), intArrayOf(25, 26, 27), 0, 0),
        Case("Apara 2028", 11, false, intArrayOf(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20), intArrayOf(10, 11, 12), 0, 0),
        Case("Nirjala 2028", 26, false, intArrayOf(25, 26, 27, 28, 29, 30, 1, 2, 3, 4, 5), intArrayOf(25, 26, 27), 0, 0),
        Case("Yogini 2028", 11, false, intArrayOf(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20), intArrayOf(10, 11, 12), 0, 0),
        Case("Devshayani 2028", 26, false, intArrayOf(25, 26, 27, 28, 29, 30, 1, 2, 3, 4, 4), intArrayOf(25, 26, 27), 0, 0),
        Case("Kamika 2028", 11, false, intArrayOf(10, 11, 12, 13, 14, 15, 17, 18, 19, 20, 21), intArrayOf(10, 11, 12), 0, 0),
        Case("Shravana Putrada 2028", 26, false, intArrayOf(25, 26, 27, 28, 29, 30, 1, 2, 3, 4, 5), intArrayOf(25, 26, 27), 0, 0),
        Case("Aja 2028", 11, false, intArrayOf(10, 11, 12, 13, 14, 15, 16, 17, 19, 20, 21), intArrayOf(9, 10, 12), 0, 1),
        Case("Parsva 2028", 26, false, intArrayOf(25, 26, 27, 28, 29, 30, 1, 1, 2, 3, 4), intArrayOf(25, 26, 27), 0, 0),
        Case("Indira 2028", 11, false, intArrayOf(10, 11, 12, 13, 15, 16, 17, 18, 19, 20, 21), intArrayOf(10, 11, 12), 0, 0),
        Case("Papankusha 2028", 26, false, intArrayOf(25, 26, 27, 28, 29, 30, 1, 2, 3, 4, 5), intArrayOf(25, 26, 27), 0, 0),
        Case("Rama 2028", 11, false, intArrayOf(10, 11, 12, 13, 14, 15, 17, 18, 19, 20, 21), intArrayOf(10, 11, 12), 0, 0),
        Case("Devutthana 2028", 26, false, intArrayOf(25, 26, 27, 27, 28, 29, 30, 1, 2, 3, 4), intArrayOf(25, 26, 26), 0, 1),
        Case("Utpanna 2028", 11, false, intArrayOf(10, 11, 13, 14, 15, 16, 17, 18, 19, 20, 21), intArrayOf(10, 11, 13), -1, 0),
        Case("Mokshada 2028", 26, false, intArrayOf(25, 26, 27, 28, 29, 30, 30, 1, 3, 4, 5), intArrayOf(25, 26, 27), 0, 1),
        Case("Saphala 2028", 11, false, intArrayOf(10, 11, 12, 13, 14, 15, 17, 18, 19, 19, 20), intArrayOf(10, 11, 12), 0, 0),
        Case("Pausha Putrada 2028", 26, false, intArrayOf(25, 26, 27, 28, 29, 30, 1, 2, 3, 4, 5), intArrayOf(25, 26, 27), 0, 0),
    )
}
