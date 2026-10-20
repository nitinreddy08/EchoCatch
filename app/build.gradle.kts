// SPDX-License-Identifier: GPL-3.0-only

plugins {
    // AGP 9 has Kotlin built in; the kotlin-android plugin must not be applied.
    alias(libs.plugins.android.application)
}

android {
    namespace = "io.github.nitinreddy08.echocatch"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.nitinreddy08.echocatch"
        minSdk = 26 // ADR-0009
        targetSdk = 36 // Google Play requires 36 or higher from 2026-08-31 (ADR-0011)
        versionCode = 1
        versionName = "0.0.1-m0"
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
