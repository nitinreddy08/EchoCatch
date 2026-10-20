// SPDX-License-Identifier: GPL-3.0-only

plugins {
    // AGP 9 has Kotlin built in; the kotlin-android plugin must not be applied.
    alias(libs.plugins.android.application)
}

// Throwaway experiment app for milestone M0 (docs/plan/04-spikes.md). Never shipped.
android {
    namespace = "io.github.nitinreddy08.echocatch.lab"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.nitinreddy08.echocatch.lab"
        minSdk = 26 // ADR-0009
        targetSdk = 36
        versionCode = 1
        versionName = "0.0.1-lab1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
