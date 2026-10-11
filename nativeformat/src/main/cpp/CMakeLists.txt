cmake_minimum_required(VERSION 3.22.1)
project(nativeformat C)

# Single source of truth for printf-style formatting lives at repo root in
# native/, shared with the desktop JNI build and the iOS cinterop build.
set(NATIVE_FORMAT_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../../../../native)

add_library(nativeformat SHARED
    ${NATIVE_FORMAT_ROOT}/native_format_jni.c
)

target_include_directories(nativeformat PRIVATE ${NATIVE_FORMAT_ROOT})

target_link_libraries(nativeformat log)
