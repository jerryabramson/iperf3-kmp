/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.ksp)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)

    // Needed transitively for KSP codegen at the app level, alongside
    // :shared's own kspAndroid registration.
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
}

// Release (Play upload) signing, opt-in and per developer: set these in your
// own ~/.gradle/gradle.properties, never in the repo's gradle.properties.
//   androidApp.releaseStoreFile=/absolute/path/to/upload-keystore.jks
//   androidApp.releaseKeyAlias=upload
//   androidApp.releaseStorePassword=...   (or env ANDROID_RELEASE_STORE_PASSWORD)
//   androidApp.releaseKeyPassword=...     (or env ANDROID_RELEASE_KEY_PASSWORD)
// Without androidApp.releaseStoreFile, release builds are unsigned (Play
// rejects those). Setup: docs/platform-builds/android.md.
fun releaseSigningValue(property: String, env: String? = null): String? =
    providers.gradleProperty(property).orNull
        ?: env?.let { providers.environmentVariable(it).orNull }
val releaseStoreFile = releaseSigningValue("androidApp.releaseStoreFile")

android {
    namespace = "com.jabramson.iperf3_network_tester_kmp"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.jabramson.iperf3_network_tester_kmp"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // Read from the repo-root VERSION file (see root build.gradle.kts),
        // not hardcoded here -- keeps this in sync with desktop/iOS/the CLI.
        versionCode = rootProject.extra["appBuildNumber"] as Int
        versionName = rootProject.extra["appVersion"] as String
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                fun required(property: String, env: String? = null) =
                    releaseSigningValue(property, env) ?: throw GradleException(
                        "androidApp.releaseStoreFile is set but $property is missing " +
                            "(set it in ~/.gradle/gradle.properties${env?.let { " or env $it" } ?: ""})."
                    )
                storeFile = file(releaseStoreFile)
                keyAlias = required("androidApp.releaseKeyAlias")
                storePassword = required("androidApp.releaseStorePassword", "ANDROID_RELEASE_STORE_PASSWORD")
                keyPassword = required("androidApp.releaseKeyPassword", "ANDROID_RELEASE_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}