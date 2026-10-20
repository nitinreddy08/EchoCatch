// SPDX-License-Identifier: GPL-3.0-only

// Intentionally empty. Do NOT declare the Kotlin or Android plugins here with "apply false": that loads the Kotlin
// plugin in a parent classloader that cannot see the Android plugin, and AGP 9 fails with
// NoClassDefFoundError: com/android/build/gradle/api/BaseVariant. Each module applies the plugins it needs.
// Shared settings for the pure Kotlin/JVM modules are repeated in their build files (there are only two).
