// SPDX-License-Identifier: GPL-3.0-only

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google\\.android.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google\\.android.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "EchoCatch"

// Pure Kotlin/JVM modules build anywhere. Android modules need the Android SDK, so they are included only when an SDK
// is found (ANDROID_HOME, ANDROID_SDK_ROOT or local.properties). Override with -Pechocatch.android=true|false.
val sdkInLocalProperties = file("local.properties").takeIf { it.exists() }
    ?.readLines()?.any { it.trim().startsWith("sdk.dir=") } ?: false
val sdkInEnvironment = providers.environmentVariable("ANDROID_HOME").isPresent ||
    providers.environmentVariable("ANDROID_SDK_ROOT").isPresent
val includeAndroid = providers.gradleProperty("echocatch.android").orNull?.toBoolean()
    ?: (sdkInLocalProperties || sdkInEnvironment)

include(":core:model", ":core:policy")

if (includeAndroid) {
    include(":app")
} else {
    logger.lifecycle("EchoCatch: Android modules skipped (no Android SDK found, or -Pechocatch.android=false).")
}
