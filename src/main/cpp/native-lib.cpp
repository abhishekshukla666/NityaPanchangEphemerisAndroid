#include <jni.h>
#include <string>
#include <map>
#include <vector>
#include <cmath>
#include "swephexp.h"

extern "C" {

// Helper to create a HashMap<String, Double>
jobject create_double_map(JNIEnv *env, const std::map<std::string, double>& data) {
    jclass mapClass = env->FindClass("java/util/HashMap");
    jmethodID mapConstructor = env->GetMethodID(mapClass, "<init>", "()V");
    jobject mapObj = env->NewObject(mapClass, mapConstructor);
    jmethodID putMethod = env->GetMethodID(mapClass, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");

    jclass doubleClass = env->FindClass("java/lang/Double");
    jmethodID doubleConstructor = env->GetMethodID(doubleClass, "<init>", "(D)V");

    for (auto const& item : data) {
        jstring jKey = env->NewStringUTF(item.first.c_str());
        jobject jVal = env->NewObject(doubleClass, doubleConstructor, item.second);
        env->CallObjectMethod(mapObj, putMethod, jKey, jVal);
    }
    return mapObj;
}

JNIEXPORT jdouble JNICALL Java_com_nityapanchangam_ephemeris_SwissEphWrapper_getJulianDayUTC(JNIEnv *env, jobject thiz, jint year, jint month, jint day, jdouble hourDecimal) {
    return swe_julday(year, month, day, hourDecimal, SE_GREG_CAL);
}

JNIEXPORT jint JNICALL Java_com_nityapanchangam_ephemeris_SwissEphWrapper_calculateTithiNumberForJulianDay(JNIEnv *env, jobject thiz, jdouble jd) {
    swe_set_sid_mode(SE_SIDM_LAHIRI, 0, 0);
    double sunPosition[6], moonPosition[6];
    char errorMessage[256];
    swe_calc_ut(jd, SE_SUN, SEFLG_SWIEPH | SEFLG_SIDEREAL, sunPosition, errorMessage);
    swe_calc_ut(jd, SE_MOON, SEFLG_SWIEPH | SEFLG_SIDEREAL, moonPosition, errorMessage);
    double e = moonPosition[0] - sunPosition[0];
    if (e < 0) e += 360.0;
    return ((int)((e + 180.0) / 12.0) % 30) + 1;
}

JNIEXPORT jobject JNICALL Java_com_nityapanchangam_ephemeris_SwissEphWrapper_calculateTithiForJulianDay(JNIEnv *env, jobject thiz, jdouble jd) {
    swe_set_sid_mode(SE_SIDM_LAHIRI, 0, 0);
    double sunPosition[6], moonPosition[6];
    char errorMessage[256];
    swe_calc_ut(jd, SE_SUN, SEFLG_SWIEPH | SEFLG_SIDEREAL, sunPosition, errorMessage);
    swe_calc_ut(jd, SE_MOON, SEFLG_SWIEPH | SEFLG_SIDEREAL, moonPosition, errorMessage);

    double sunLong = sunPosition[0];
    double moonLong = moonPosition[0];
    double angleDifference = moonLong - sunLong;
    if (angleDifference < 0) { angleDifference += 360.0; }

    int tithiNumber = ((int)((angleDifference + 180.0) / 12.0) % 30) + 1;
    double nakshatraLength = 360.0 / 27.0;
    int nakshatraNumber = (int)(moonLong / nakshatraLength) + 1;

    double combinedLong = sunLong + moonLong;
    if (combinedLong >= 360.0) { combinedLong -= 360.0; }
    int yogaNumber = (int)(combinedLong / nakshatraLength) + 1;
    int karanaNumber = (int)(angleDifference / 6.0) + 1;

    // Find tithi end
    double searchJD = jd;
    int currentTithi = tithiNumber;
    double stepInterval = 15.0 / 1440.0;
    while (currentTithi == tithiNumber) {
        searchJD += stepInterval;
        // Call internal helper or re-calculate
        double sPos[6], mPos[6];
        swe_calc_ut(searchJD, SE_SUN, SEFLG_SWIEPH | SEFLG_SIDEREAL, sPos, errorMessage);
        swe_calc_ut(searchJD, SE_MOON, SEFLG_SWIEPH | SEFLG_SIDEREAL, mPos, errorMessage);
        double e = mPos[0] - sPos[0];
        if (e < 0) e += 360.0;
        currentTithi = ((int)((e + 180.0) / 12.0) % 30) + 1;
        if (searchJD > jd + 2.0) { break; }
    }

    std::map<std::string, double> result;
    result["julianDay"] = jd;
    result["tithiEndJD"] = searchJD;
    result["tithiNumber"] = (double)tithiNumber;
    result["nakshatraNumber"] = (double)nakshatraNumber;
    result["yogaNumber"] = (double)yogaNumber;
    result["karanaNumber"] = (double)karanaNumber;

    return create_double_map(env, result);
}

JNIEXPORT jobject JNICALL Java_com_nityapanchangam_ephemeris_SwissEphWrapper_calculateSunriseSunset(JNIEnv *env, jobject thiz, jdouble jd, jdouble latitude, jdouble longitude) {
    swe_set_topo(longitude, latitude, 0);

    double riseTret[3] = {0, 0, 0};
    double setTret[3] = {0, 0, 0};
    double moonRiseTret[3] = {0, 0, 0};
    double moonSetTret[3] = {0, 0, 0};

    char errorMessage[256];
    char starname[] = "";
    double geopos[3] = {longitude, latitude, 0.0};

    swe_rise_trans(jd, SE_SUN, starname, SEFLG_SWIEPH, SE_CALC_RISE, geopos, 0.0, 0.0, riseTret, errorMessage);
    swe_rise_trans(jd, SE_SUN, starname, SEFLG_SWIEPH, SE_CALC_SET, geopos, 0.0, 0.0, setTret, errorMessage);
    swe_rise_trans(jd, SE_MOON, starname, SEFLG_SWIEPH, SE_CALC_RISE, geopos, 0.0, 0.0, moonRiseTret, errorMessage);
    swe_rise_trans(jd, SE_MOON, starname, SEFLG_SWIEPH, SE_CALC_SET, geopos, 0.0, 0.0, moonSetTret, errorMessage);

    std::map<std::string, double> result;
    result["sunriseJD"] = riseTret[0];
    result["sunsetJD"] = setTret[0];
    result["moonriseJD"] = moonRiseTret[0];
    result["moonsetJD"] = moonSetTret[0];

    return create_double_map(env, result);
}

// Logic for Amanta month details copied from SwissEphWrapper.m helpers
static double refineAmavasyaJD(double approxJD) {
    char errorMessage[256];
    double sunPos[6], moonPos[6];
    double amavJD = approxJD;
    for (int i = 0; i < 10; i++) {
        swe_calc_ut(amavJD, SE_SUN, SEFLG_SWIEPH | SEFLG_SIDEREAL, sunPos, errorMessage);
        swe_calc_ut(amavJD, SE_MOON, SEFLG_SWIEPH | SEFLG_SIDEREAL, moonPos, errorMessage);
        double e = moonPos[0] - sunPos[0];
        if (e < 0) e += 360.0;
        if (e > 180.0) e -= 360.0;
        double correction = e * (29.53059 / 360.0);
        amavJD -= correction;
        if (std::abs(correction) < 0.00001) break;
    }
    return amavJD;
}

static void getAmantaMonthDetails(double jd, int *monthIndex, bool *isAdhik) {
    swe_set_sid_mode(SE_SIDM_LAHIRI, 0, 0);
    char errorMessage[256];
    double sunPos[6], moonPos[6];
    swe_calc_ut(jd, SE_SUN, SEFLG_SWIEPH | SEFLG_SIDEREAL, sunPos, errorMessage);
    swe_calc_ut(jd, SE_MOON, SEFLG_SWIEPH | SEFLG_SIDEREAL, moonPos, errorMessage);

    double elongation = moonPos[0] - sunPos[0];
    if (elongation < 0) elongation += 360.0;

    double degreesToNextAmav = 360.0 - elongation;
    if (degreesToNextAmav < 0.5) degreesToNextAmav = 360.0;

    double daysAhead = degreesToNextAmav * 29.53059 / 360.0;
    double nextAmavJD = refineAmavasyaJD(jd + daysAhead);
    double prevAmavJD = refineAmavasyaJD(nextAmavJD - 29.53059);

    double sunAtNext[6], sunAtPrev[6];
    swe_calc_ut(nextAmavJD, SE_SUN, SEFLG_SWIEPH | SEFLG_SIDEREAL, sunAtNext, errorMessage);
    swe_calc_ut(prevAmavJD, SE_SUN, SEFLG_SWIEPH | SEFLG_SIDEREAL, sunAtPrev, errorMessage);

    int rashiNext = (int)(sunAtNext[0] / 30.0);
    int rashiPrev = (int)(sunAtPrev[0] / 30.0);

    if (rashiNext == rashiPrev) {
        *isAdhik = true;
        *monthIndex = ((rashiNext + 2 - 1) % 12) + 1;
    } else {
        *isAdhik = false;
        *monthIndex = ((rashiNext + 1 - 1) % 12) + 1;
    }
}

JNIEXPORT jint JNICALL Java_com_nityapanchangam_ephemeris_SwissEphWrapper_calculateLunarMonthForJulianDay(JNIEnv *env, jobject thiz, jdouble jd) {
    swe_set_sid_mode(SE_SIDM_LAHIRI, 0, 0);
    char errorMessage[256];
    double sunPos[6];
    swe_calc_ut(jd, SE_SUN, SEFLG_SWIEPH | SEFLG_SIDEREAL, sunPos, errorMessage);
    int rashi = (int)(sunPos[0] / 30.0);
    return ((rashi + 1) % 12) + 1;
}

JNIEXPORT jint JNICALL Java_com_nityapanchangam_ephemeris_SwissEphWrapper_calculatePurnimantaMonthForJulianDay(JNIEnv *env, jobject thiz, jdouble jd) {
    swe_set_sid_mode(SE_SIDM_LAHIRI, 0, 0);
    char errorMessage[256];
    double sunPos[6], moonPos[6];
    swe_calc_ut(jd, SE_SUN, SEFLG_SWIEPH | SEFLG_SIDEREAL, sunPos, errorMessage);
    swe_calc_ut(jd, SE_MOON, SEFLG_SWIEPH | SEFLG_SIDEREAL, moonPos, errorMessage);

    double elongation = moonPos[0] - sunPos[0];
    if (elongation < 0) elongation += 360.0;

    int currentMonth;
    bool currentIsAdhik;
    getAmantaMonthDetails(jd, &currentMonth, &currentIsAdhik);

    if (elongation >= 180.0) {
        double degreesToNextAmav = 360.0 - elongation;
        double daysToAmav = degreesToNextAmav * 29.53059 / 360.0;
        double nextMonthJD = jd + daysToAmav + 5.0;
        int nextMonth;
        bool nextIsAdhik;
        getAmantaMonthDetails(nextMonthJD, &nextMonth, &nextIsAdhik);
        return nextMonth;
    } else {
        return currentMonth;
    }
}

JNIEXPORT jboolean JNICALL Java_com_nityapanchangam_ephemeris_SwissEphWrapper_calculateIsAdhikMaasForJulianDay(JNIEnv *env, jobject thiz, jdouble jd) {
    int currentMonth;
    bool currentIsAdhik;
    getAmantaMonthDetails(jd, &currentMonth, &currentIsAdhik);
    return currentIsAdhik;
}

JNIEXPORT jboolean JNICALL Java_com_nityapanchangam_ephemeris_SwissEphWrapper_calculateIsPurnimantaAdhikMaasForJulianDay(JNIEnv *env, jobject thiz, jdouble jd) {
    int currentMonth;
    bool currentIsAdhik;
    getAmantaMonthDetails(jd, &currentMonth, &currentIsAdhik);
    return currentIsAdhik;
}

JNIEXPORT jint JNICALL Java_com_nityapanchangam_ephemeris_SwissEphWrapper_calculateYogaForJulianDay(JNIEnv *env, jobject thiz, jdouble jd) {
    swe_set_sid_mode(SE_SIDM_LAHIRI, 0, 0);
    double sunPosition[6], moonPosition[6];
    char errorMessage[256];
    swe_calc_ut(jd, SE_SUN, SEFLG_SWIEPH | SEFLG_SIDEREAL, sunPosition, errorMessage);
    swe_calc_ut(jd, SE_MOON, SEFLG_SWIEPH | SEFLG_SIDEREAL, moonPosition, errorMessage);
    double totalLongitude = sunPosition[0] + moonPosition[0];
    if (totalLongitude >= 360.0) { totalLongitude -= 360.0; }
    return (int)(totalLongitude / 13.333333) + 1;
}

JNIEXPORT jint JNICALL Java_com_nityapanchangam_ephemeris_SwissEphWrapper_calculateNakshatraForJulianDay(JNIEnv *env, jobject thiz, jdouble jd) {
    swe_set_sid_mode(SE_SIDM_LAHIRI, 0, 0);
    double moonPosition[6];
    char errorMessage[256];
    swe_calc_ut(jd, SE_MOON, SEFLG_SWIEPH | SEFLG_SIDEREAL, moonPosition, errorMessage);
    return (int)(moonPosition[0] / 13.333333) + 1;
}

JNIEXPORT jdouble JNICALL Java_com_nityapanchangam_ephemeris_SwissEphWrapper_calculateNakshatraEndTimeForJulianDay(JNIEnv *env, jobject thiz, jdouble startJD) {
    // Re-use logic from calculateNakshatraForJulianDay
    auto getNakshatra = [](double jd) {
        swe_set_sid_mode(SE_SIDM_LAHIRI, 0, 0);
        double moonPosition[6];
        char errorMessage[256];
        swe_calc_ut(jd, SE_MOON, SEFLG_SWIEPH | SEFLG_SIDEREAL, moonPosition, errorMessage);
        return (int)(moonPosition[0] / 13.333333) + 1;
    };

    int startingNakshatra = getNakshatra(startJD);
    double step = 15.0 / (24.0 * 60.0);
    double searchJD = startJD;
    int searchNakshatra = startingNakshatra;
    while (searchNakshatra == startingNakshatra) {
        searchJD += step;
        searchNakshatra = getNakshatra(searchJD);
        if (searchJD > startJD + 1.5) { break; }
    }
    return searchJD;
}

JNIEXPORT jobject JNICALL Java_com_nityapanchangam_ephemeris_SwissEphWrapper_calculateMuhurats(JNIEnv *env, jobject thiz, jdouble sunriseJD, jdouble sunsetJD, jint weekday) {
    double dayDuration = sunsetJD - sunriseJD;
    double muhuratLength15 = dayDuration / 15.0;
    double muhuratLength8 = dayDuration / 8.0;

    std::map<std::string, double> result;

    // Abhijit (8th of 15)
    result["abhijitStart"] = sunriseJD + 7.0 * muhuratLength15;
    result["abhijitEnd"] = result["abhijitStart"] + muhuratLength15;

    // Vijaya (10th of 15)
    result["vijayaStart"] = sunriseJD + 9.0 * muhuratLength15;
    result["vijayaEnd"] = result["vijayaStart"] + muhuratLength15;

    int rahuSegments[7] = {8, 2, 7, 5, 6, 4, 3};
    int yamaSegments[7] = {5, 4, 3, 2, 7, 6, 1};
    int gulikSegments[7] = {6, 5, 4, 3, 2, 1, 7};

    result["rahuStart"] = sunriseJD + (rahuSegments[weekday - 1] - 1) * muhuratLength8;
    result["rahuEnd"] = result["rahuStart"] + muhuratLength8;
    result["yamaStart"] = sunriseJD + (yamaSegments[weekday - 1] - 1) * muhuratLength8;
    result["yamaEnd"] = result["yamaStart"] + muhuratLength8;
    result["gulikStart"] = sunriseJD + (gulikSegments[weekday - 1] - 1) * muhuratLength8;
    result["gulikEnd"] = result["gulikStart"] + muhuratLength8;

    result["brahmaStart"] = sunriseJD - (96.0 / 1440.0);
    result["brahmaEnd"] = sunriseJD - (48.0 / 1440.0);

    int amritSegments[7] = {3, 0, 5, 1, 6, 2, 7};
    result["amritStart"] = sunriseJD + amritSegments[weekday - 1] * muhuratLength8;
    result["amritEnd"] = result["amritStart"] + muhuratLength8;

    result["godhuliStart"] = sunsetJD - (24.0 / 1440.0);
    result["godhuliEnd"] = sunsetJD + (24.0 / 1440.0);

    return create_double_map(env, result);
}

JNIEXPORT jdouble JNICALL Java_com_nityapanchangam_ephemeris_SwissEphWrapper_calculateYogaEndTimeForJulianDay(JNIEnv *env, jobject thiz, jdouble startJD) {
    auto getYoga = [](double jd) {
        swe_set_sid_mode(SE_SIDM_LAHIRI, 0, 0);
        double sunPosition[6], moonPosition[6];
        char errorMessage[256];
        swe_calc_ut(jd, SE_SUN, SEFLG_SWIEPH | SEFLG_SIDEREAL, sunPosition, errorMessage);
        swe_calc_ut(jd, SE_MOON, SEFLG_SWIEPH | SEFLG_SIDEREAL, moonPosition, errorMessage);
        double totalLongitude = sunPosition[0] + moonPosition[0];
        if (totalLongitude >= 360.0) { totalLongitude -= 360.0; }
        return (int)(totalLongitude / 13.333333) + 1;
    };

    int startingYoga = getYoga(startJD);
    double step = 15.0 / (24.0 * 60.0);
    double searchJD = startJD;
    int searchYoga = startingYoga;
    while (searchYoga == startingYoga) {
        searchJD += step;
        searchYoga = getYoga(searchJD);
        if (searchJD > startJD + 1.5) { break; }
    }
    return searchJD;
}

JNIEXPORT jint JNICALL Java_com_nityapanchangam_ephemeris_SwissEphWrapper_calculateMoonRashiForJulianDay(JNIEnv *env, jobject thiz, jdouble jd) {
    swe_set_sid_mode(SE_SIDM_LAHIRI, 0, 0);
    double moonPosition[6];
    char errorMessage[256];
    swe_calc_ut(jd, SE_MOON, SEFLG_SWIEPH | SEFLG_SIDEREAL, moonPosition, errorMessage);
    return (int)(moonPosition[0] / 30.0) + 1;
}

JNIEXPORT jdoubleArray JNICALL Java_com_nityapanchangam_ephemeris_SwissEphWrapper_calculatePlanetPositionsForJulianDay(JNIEnv *env, jobject thiz, jdouble jd) {
    swe_set_sid_mode(SE_SIDM_LAHIRI, 0, 0);
    long flags = SEFLG_SWIEPH | SEFLG_SIDEREAL;
    char errorMessage[256];

    std::vector<double> resultData;
    int seIds[7] = { SE_SUN, SE_MOON, SE_MARS, SE_MERCURY, SE_JUPITER, SE_VENUS, SE_SATURN };

    for (int i = 0; i < 7; i++) {
        double pos[6];
        swe_calc_ut(jd, seIds[i], flags, pos, errorMessage);
        double lon = pos[0];
        while (lon < 0) lon += 360.0;
        while (lon >= 360) lon -= 360.0;
        int rashi = (int)(lon / 30.0) + 1;
        double deg = std::fmod(lon, 30.0);
        resultData.push_back((double)i);
        resultData.push_back(lon);
        resultData.push_back((double)rashi);
        resultData.push_back(deg);
    }

    double rahuPos[6];
    swe_calc_ut(jd, SE_MEAN_NODE, flags, rahuPos, errorMessage);
    double rahuLon = rahuPos[0];
    while (rahuLon < 0) rahuLon += 360.0;
    while (rahuLon >= 360) rahuLon -= 360.0;
    int rahuRashi = (int)(rahuLon / 30.0) + 1;
    resultData.push_back(7.0);
    resultData.push_back(rahuLon);
    resultData.push_back((double)rahuRashi);
    resultData.push_back(std::fmod(rahuLon, 30.0));

    double ketuLon = std::fmod(rahuLon + 180.0, 360.0);
    int ketuRashi = (int)(ketuLon / 30.0) + 1;
    resultData.push_back(8.0);
    resultData.push_back(ketuLon);
    resultData.push_back((double)ketuRashi);
    resultData.push_back(std::fmod(ketuLon, 30.0));

    jdoubleArray result = env->NewDoubleArray(resultData.size());
    env->SetDoubleArrayRegion(result, 0, resultData.size(), resultData.data());
    return result;
}

JNIEXPORT jdouble JNICALL Java_com_nityapanchangam_ephemeris_SwissEphWrapper_calculateAscendantAtJD(JNIEnv *env, jobject thiz, jdouble jd, jdouble latitude, jdouble longitude) {
    swe_set_sid_mode(SE_SIDM_LAHIRI, 0, 0);
    double cusps[13] = {0};
    double ascmc[10] = {0};
    swe_houses_ex(jd, SEFLG_SIDEREAL, latitude, longitude, 'W', cusps, ascmc);
    double asc = ascmc[0];
    while (asc < 0.0) asc += 360.0;
    while (asc >= 360.0) asc -= 360.0;
    return asc;
}

JNIEXPORT void JNICALL Java_com_nityapanchangam_ephemeris_SwissEphWrapper_setEphemerisPath(JNIEnv *env, jobject thiz, jstring path) {
    const char *pathChars = env->GetStringUTFChars(path, nullptr);
    swe_set_ephe_path(const_cast<char *>(pathChars));
    env->ReleaseStringUTFChars(path, pathChars);
}

}
