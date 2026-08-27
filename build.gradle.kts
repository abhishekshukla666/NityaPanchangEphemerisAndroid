plugins {
    alias(libs.plugins.android.library)
    id("maven-publish")
}

android {
    namespace = "com.nityapanchangam.ephemeris"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake {
                cppFlags("")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // Ship the .so files with their symbol tables intact. AGP strips native libraries when
    // it packages an AAR, so a consuming app has nothing left to extract and Play reports
    // native crashes as bare addresses instead of function names. The consumer still strips
    // for its own packaging (that is what ndk.debugSymbolLevel does, after extracting the
    // symbols into the bundle), so this costs AAR size, not installed app size.
    packaging {
        jniLibs {
            keepDebugSymbols += "**/*.so"
        }
    }

    publishing {
        singleVariant("release")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

// JitPack builds this repo by running `./gradlew publishToMavenLocal` and picking up
// whatever lands under groupId "com.github.abhishekshukla666" — see
// https://docs.jitpack.io/android/
afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.github.abhishekshukla666"
                artifactId = "NityaPanchangEphemerisAndroid"
            }
        }
    }
}
