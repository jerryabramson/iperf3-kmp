/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
}

// Single source of truth for the app's version: the plain-text VERSION file
// at repo root. Every place a version number appears (Android versionName,
// desktop packageVersion, iOS Config.xcconfig via syncVersions below, the
// CLI's --version output) reads from this one value instead of carrying its
// own independent literal -- see PLATFORM_BUILD_NOTES.md for the bump
// process and how this ties to GitHub release tags.
val appVersion: String = rootProject.file("VERSION").readText().trim()
extra["appVersion"] = appVersion

/**
 * Derives a monotonically increasing integer build number from [appVersion]
 * -- Android's versionCode and iOS's CURRENT_PROJECT_VERSION both need a
 * plain integer that increases every release, not the dotted semver string
 * itself. Assumes each of major/minor/patch stays in 0..99; a component
 * past that would need this formula revisited, but is far beyond anything
 * this project is near.
 */
fun buildNumberFromSemver(semver: String): Int {
    val parts = semver.split(".").map { it.toIntOrNull() ?: 0 }
    val major = parts.getOrElse(0) { 0 }
    val minor = parts.getOrElse(1) { 0 }
    val patch = parts.getOrElse(2) { 0 }
    return major * 10000 + minor * 100 + patch
}
extra["appBuildNumber"] = buildNumberFromSemver(appVersion)

// iOS isn't a Gradle module (see CLAUDE.md: "Open in Xcode, never build via
// Gradle") -- Config.xcconfig has no automatic way to pick up VERSION, so
// this task exists to be run manually before an Xcode build/archive/
// TestFlight upload, keeping its two version lines in sync with VERSION on
// demand instead of silently drifting. Doesn't touch the iOS build itself
// (no Gradle involvement added to how Xcode builds iosApp) -- it only
// rewrites plain text in a config file, the same two lines a human would
// otherwise hand-edit.
tasks.register("syncVersions") {
    group = "build setup"
    description = "Writes the current VERSION file's value into iosApp/Configuration/Config.xcconfig's MARKETING_VERSION/CURRENT_PROJECT_VERSION -- run this before building/archiving in Xcode."

    val version = appVersion
    val buildNumber = rootProject.extra["appBuildNumber"] as Int
    val xcconfigFile = rootProject.file("iosApp/Configuration/Config.xcconfig")

    doLast {
        val text = xcconfigFile.readText()
        val updated = text
            .replace(Regex("""(?m)^MARKETING_VERSION\s*=.*$"""), "MARKETING_VERSION=$version")
            .replace(Regex("""(?m)^CURRENT_PROJECT_VERSION\s*=.*$"""), "CURRENT_PROJECT_VERSION=$buildNumber")
        xcconfigFile.writeText(updated)
        println("Synced iosApp/Configuration/Config.xcconfig to MARKETING_VERSION=$version, CURRENT_PROJECT_VERSION=$buildNumber")
    }
}

// AGP's CMake external-native-build up-to-date check doesn't reliably
// notice changes to native/*.c — both :iperf3native and :nativeformat
// reference it via a relative path outside their own module directory
// (../../../../native/...), which falls outside what AGP tracks for
// incremental rebuilds. A plain assembleDebug/installDebug can silently
// keep using a stale .so after editing those shared files. This task
// forces a clean CMake reconfigure by deleting both modules' external
// native build directories — meant to be wired in as an IDE "Before
// launch" Gradle-task step (Android Studio: Run/Debug Configurations ->
// + -> Run Gradle Task -> cleanNativeCxxCaches), run only when native/
// files were actually touched, not on every build (a full CMake
// reconfigure on every run would be needlessly slow).
val nativeCxxCacheDirs: List<File> = listOf(
    project(":iperf3native").layout.projectDirectory.dir(".cxx").asFile,
    project(":iperf3native").layout.projectDirectory.dir("build").asFile,
    project(":nativeformat").layout.projectDirectory.dir(".cxx").asFile,
    project(":nativeformat").layout.projectDirectory.dir("build").asFile,
)

tasks.register("cleanNativeCxxCaches") {
    group = "build setup"
    description = "Deletes :iperf3native's and :nativeformat's CMake build dirs, forcing a clean native rebuild next time (needed after editing native/*.c — see task comment)."

    // Local shadow copy isolates the variable from the parent script scope
    val nativeCxxCacheDirs = nativeCxxCacheDirs

    doLast {
        for (file in nativeCxxCacheDirs) {
            if (file.exists()) {
                file.deleteRecursively()
                println("Deleted $file")
            }
        }
    }
}
