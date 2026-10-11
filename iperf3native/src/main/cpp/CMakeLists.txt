cmake_minimum_required(VERSION 3.22.1)
project(iperf3native C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

# Single source of truth for the JNI bridge lives at repo root in native/,
# shared with the desktop JVM build (see shared/build.gradle.kts'
# compileIperf3Jvm task) — same convention as :nativeformat.
set(BRIDGE_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../../../../native)

# Vendored esnet/iperf 3.21 submodule — see IPERF3_NATIVE_INTEGRATION.md.
set(IPERF_SRC_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/iperf/src)

# iperf3's own ./configure generates iperf_config.h/version.h; there's no
# NDK-toolchain autotools run here, so both are supplied instead — a
# hand-written config for Android (native/iperf_config_android.h, see its
# header comment for why) and a version.h substituted from iperf3's own
# template. Both are generated into the CMake build dir, never into the
# submodule's own src/ tree (which would leave the submodule's working tree
# dirty).
set(GENERATED_DIR ${CMAKE_CURRENT_BINARY_DIR}/generated)
file(MAKE_DIRECTORY ${GENERATED_DIR})

configure_file(
    ${BRIDGE_ROOT}/iperf_config_android.h
    ${GENERATED_DIR}/iperf_config.h
    COPYONLY
)

set(PACKAGE_VERSION "3.21")
configure_file(
    ${IPERF_SRC_DIR}/version.h.in
    ${GENERATED_DIR}/version.h
    @ONLY
)

# Client-only build: no auth (OpenSSL), no SCTP, no server mode — matches
# this app's use of libiperf (client tests only) and IPERF3_NATIVE_INTEGRATION.md
# §6.6's decision to drop OpenSSL entirely.
add_library(iperf3native SHARED
    ${BRIDGE_ROOT}/iperf3_jni.c
    ${BRIDGE_ROOT}/iperf3_bridge.c
    ${IPERF_SRC_DIR}/cjson.c
    ${IPERF_SRC_DIR}/iperf_api.c
    ${IPERF_SRC_DIR}/iperf_client_api.c
    ${IPERF_SRC_DIR}/iperf_error.c
    ${IPERF_SRC_DIR}/iperf_locale.c
    ${IPERF_SRC_DIR}/iperf_pthread.c
    ${IPERF_SRC_DIR}/iperf_tcp.c
    ${IPERF_SRC_DIR}/iperf_time.c
    ${IPERF_SRC_DIR}/iperf_udp.c
    ${IPERF_SRC_DIR}/iperf_util.c
    ${IPERF_SRC_DIR}/dscp.c
    ${IPERF_SRC_DIR}/net.c
    ${IPERF_SRC_DIR}/tcp_info.c
    ${IPERF_SRC_DIR}/timer.c
    ${IPERF_SRC_DIR}/units.c
)

target_include_directories(iperf3native PRIVATE
    ${GENERATED_DIR}
    ${IPERF_SRC_DIR}
    ${BRIDGE_ROOT}
)

target_compile_definitions(iperf3native PRIVATE HAVE_CONFIG_H __ANDROID__)

find_package(Threads REQUIRED)
target_link_libraries(iperf3native
    Threads::Threads
    log
    android
)
