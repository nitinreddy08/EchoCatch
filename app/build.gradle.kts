// SPDX-License-Identifier: GPL-3.0-only

plugins {
    // AGP 9 has Kotlin built in; the kotlin-android plugin must not be applied.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.nitinreddy08.echocatch"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.nitinreddy08.echocatch"
        minSdk = 26 // ADR-0009
        targetSdk = 36 // Google Play requires 36 or higher from 2026-08-31 (ADR-0011)
        versionCode = 2
        versionName = "0.1.0-m1"
    }

    buildFeatures {
        compose = true
        buildConfig = true
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

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:policy"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.core)
    implementation(libs.androidx.datastore.preferences)
}
