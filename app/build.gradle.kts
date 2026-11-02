import java.util.Properties
import java.io.File
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
    alias(libs.plugins.crashlytics)
}

val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(FileInputStream(f))
}
val tmdbApiKey: String = localProperties.getProperty("TMDB_API_KEY")
    ?: System.getenv("TMDB_API_KEY")
    ?: ""

// The same TMDB v4 read token the desktop client uses. Sent as a bearer token
// so both apps authenticate identically; the v3 key stays on the query string
// as a fallback for when the token is absent from a build environment.
val tmdbV4Token: String = localProperties.getProperty("TMDB_V4_TOKEN")
    ?: System.getenv("TMDB_V4_TOKEN")
    ?: ""

// Resolved once so the signing config and the release build type agree on
// whether a usable keystore exists. A relative path resolves against the
// project root — which is where `nashat-tv-release.jks` actually lives — and
// the existence check means a stale path falls back to debug signing instead
// of failing the build.
val releaseKeystore: File? = (localProperties.getProperty("RELEASE_STORE_FILE")
    ?: System.getenv("RELEASE_STORE_FILE"))
    ?.let { path ->
        val f = File(path)
        if (f.isAbsolute) f else rootProject.file(path)
    }
    ?.takeIf { it.exists() }

android {
    namespace = "com.nashat.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nashat.tv"
        // Support Android 6.0+ as requested
        minSdk = 23
        targetSdk = 35
        versionCode = 10
        versionName = "1.7.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        buildConfigField("String", "TMDB_API_KEY", "\"" + tmdbApiKey + "\"")
        buildConfigField("String", "TMDB_V4_TOKEN", "\"" + tmdbV4Token + "\"")
    }

    signingConfigs {
        create("release") {
            if (releaseKeystore != null) {
                storeFile = releaseKeystore
                storePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD")
                    ?: System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS")
                    ?: System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD")
                    ?: System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Use release signing when the keystore is actually present,
            // otherwise fall back to debug so a local build still succeeds.
            signingConfig = if (releaseKeystore != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            isMinifyEnabled = false
            isDebuggable = true
            // Sign debug with the release key when it is available. Some vendor
            // ROMs (HyperOS, and MIUI before it) refuse a USB install of an APK
            // whose signature does not match what is already on the device —
            // an update installs, a fresh package does not. Sharing the key
            // keeps a debug build installable over the shipped one.
            if (releaseKeystore != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    packaging {
        // ExoPlayer may pull in duplicate licenses; exclude common meta files
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    implementation(libs.core)

    // UI + Lists
    implementation(libs.recyclerview)
    implementation(libs.swiperefreshlayout)

    // Single-Activity navigation. Replaces four Activities that each carried
    // their own hand-built copy of the bottom bar and lost all scroll state
    // on every tab switch.
    implementation(libs.fragment)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)

    // Platform splash screen, backported to this app's minSdk of 23.
    implementation(libs.splashscreen)

    // Media
    implementation(libs.exoplayer)
    implementation(libs.exoplayer.ui)
    implementation(libs.exoplayer.cast)

    // Chromecast / Google Cast
    implementation(libs.mediarouter)
    implementation(libs.cast.framework)

    // JSON
    implementation(libs.gson)

    // Images
    implementation(libs.glide)
    annotationProcessor(libs.glide.compiler)

    // Preferences (for favorites / settings)
    implementation(libs.preference)
    implementation(libs.work.runtime)

    // Network — OkHttp with disk cache for fast/cached TMDB calls
    implementation(libs.okhttp)

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // ============================
   
     // Firebase BoM (لتثبيت الإصدارات بشكل متناسق)
    implementation(platform("com.google.firebase:firebase-bom:34.1.0"))

    // Realtime Database
    implementation("com.google.firebase:firebase-database")

    // App Check — Play Integrity (production) + Debug provider (for local builds)
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    debugImplementation("com.google.firebase:firebase-appcheck-debug")

    // Crash reporting and product analytics. Both are gated behind a user
    // toggle in Settings (see AppTelemetry) and collection is disabled by
    // default in debug builds so local runs never pollute the dashboards.
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-analytics")
}
