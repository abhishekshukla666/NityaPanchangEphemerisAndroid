# NityaPanchangEphemerisAndroid

An Android library wrapping the [Swiss Ephemeris](https://www.astro.com/swisseph/) C library
for computing Hindu Panchang (tithi, nakshatra, yoga, karana, muhurats, choghadiya, hora,
lagna) and festivals.

Android counterpart of [NityaPanchangEphemeris](https://github.com/abhishekshukla666/NityaPanchangEphemeris)
(the iOS Swift Package) — ported out of the [NityaPanchagamAndroid](https://github.com/abhishekshukla666/NityaPanchagamAndroid)
app so it can be reused across projects via a single Gradle dependency.

## Installation

Add JitPack to your root `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

Then add the dependency:

```kotlin
dependencies {
    implementation("com.github.abhishekshukla666:NityaPanchangEphemerisAndroid:v1.0.0")
}
```

## Usage

```kotlin
import com.nityapanchangam.ephemeris.PanchangRepository
import com.nityapanchangam.ephemeris.SwissEphWrapper

val repository = PanchangRepository(context, SwissEphWrapper(context))

val panchang = repository.fetchPanchang(
    date = Date(),
    latitude = 23.1765,   // Ujjain
    longitude = 75.7885
)

println("${panchang.tithi.name} ${panchang.nakshatra.name} ${panchang.lunarMonth}")
```

`PanchangRepository` is the only type you need to instantiate (via a `SwissEphWrapper`).
Everything it returns (`PanchangDay`, `HinduFestival`, etc.) is a plain data class, safe to
pass straight into Compose UI or across module boundaries.

### Available calls

```kotlin
class PanchangRepository(context: Context, wrapper: SwissEphWrapper) {
    suspend fun fetchPanchang(date: Date, latitude: Double, longitude: Double): PanchangDay
    suspend fun fetchMonthTithis(year: Int, month: Int, latitude: Double, longitude: Double): Map<Int, Int>
    suspend fun fetchFestivals(startDate: Date, endDate: Date, latitude: Double, longitude: Double): List<HinduFestival>
}
```

### Localization

Unlike the iOS package (which is English-only and leaves translation to the host app), this
library returns already-localized strings — it resolves names via `context.getString(...)`
against its own bundled `values`/`values-hi` resources, following whatever locale the `Context`
passed in is configured with. Pass a locale-wrapped `Context`
(`context.createConfigurationContext(...)` / `AppCompatDelegate` per-app locale) if you need
Hindi output.

## What's inside

| Layer | Contents |
|---|---|
| C sources (`src/main/cpp`) | Astrodienst's Swiss Ephemeris core (`sweph.c`, `swecl.c`, `swemplan.c`, `swemmoon.c`, `swehouse.c`, `swejpl.c`, `swedate.c`, `swephlib.c`) — unmodified, plus a JNI bridge (`native-lib.cpp`). The Sun/Moon compressed ephemeris data files (`sepl_18.se1`, `semo_18.se1`) are bundled as library assets and extracted to app-private storage on first use. |
| Kotlin (`src/main/java`) | `SwissEphWrapper` (JNI declarations), `PanchangRepository`, `PanchaangHelper` (name/lookup tables), and the Panchang/festival value types. |

## License

Swiss Ephemeris is dual-licensed by Astrodienst AG under the **GNU Affero General Public
License v3** or a paid **Swiss Ephemeris Professional License** (see
https://www.astro.com/swisseph/). This library bundles the AGPL-licensed source, so the whole
repository — including the Kotlin wrapper code here — is distributed under **AGPL-3.0** (see
[LICENSE](LICENSE)).

If you need to use this in a closed-source / non-AGPL app, you must purchase a Swiss Ephemeris
Professional License from Astrodienst and are responsible for complying with its terms.
