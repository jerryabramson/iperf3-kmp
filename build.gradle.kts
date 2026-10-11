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
