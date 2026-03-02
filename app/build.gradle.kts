plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.nashat.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nashat.tv"
        // Support Android 6.0+ as requested
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Keep defaults; enable as needed
        }
    }

    buildFeatures {
        viewBinding = true
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

    // UI + Lists
    implementation(libs.recyclerview)
    implementation(libs.swiperefreshlayout)

    // Media
    implementation(libs.exoplayer)
    implementation(libs.exoplayer.ui)

    // JSON
    implementation(libs.gson)

    // Images
    implementation(libs.glide)
    annotationProcessor(libs.glide.compiler)

    // Preferences (for favorites / settings)
    implementation(libs.preference)

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // ============================
   
     // Firebase BoM (لتثبيت الإصدارات بشكل متناسق)
    implementation(platform("com.google.firebase:firebase-bom:34.1.0"))

    // Realtime Database
    implementation("com.google.firebase:firebase-database")

    // (اختياري) Firestore إذا تحب
    // implementation("com.google.firebase:firebase-firestore")
}