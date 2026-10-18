// Copyright (c) 2026, Jerold Abramson
//
// This source code is licensed under the BSD 3-Clause License found in
// the LICENSE file in the root directory of this source tree.

/*
 * native_format_jni.c — JNI bridge to native_format.h's format_string().
 *
 * Shared verbatim between Android (compiled by :nativeformat's CMake/NDK
 * build) and desktop JVM (compiled by :shared's desktop native-build Gradle
 * task) — both are ordinary JNI, so the same wrapper works unmodified on
 * either JVM implementation. Both target the same Kotlin bridge:
 * com.jabramson.iperf3_network_tester_kmp.utils.NativeFormatterBridge
 *
 * Native methods are bound explicitly via RegisterNatives() in JNI_OnLoad
 * instead of relying on javah-style symbol name mangling, which is fragile
 * for packages containing underscores (this project's package does).
 */
#include <jni.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>

#include "native_format.h"

#define OUT_BUFFER_SIZE 65536

static jstring native_format_call(JNIEnv* env, jclass clazz, jstring jfmt, jobjectArray jargs) {
    (void)clazz;

    if (jfmt == NULL) {
        jclass npe = (*env)->FindClass(env, "java/lang/NullPointerException");
        (*env)->ThrowNew(env, npe, "format string must not be null");
        return NULL;
    }

    const char* fmt = (*env)->GetStringUTFChars(env, jfmt, NULL);
    if (fmt == NULL) {
        return NULL; /* OutOfMemoryError already pending */
    }

    jsize argc = (jargs == NULL) ? 0 : (*env)->GetArrayLength(env, jargs);

    FormatArg* args = NULL;
    const char** utf_refs = NULL;
    jobject* str_objs = NULL;
    if (argc > 0) {
        args = (FormatArg*)calloc((size_t)argc, sizeof(FormatArg));
        utf_refs = (const char**)calloc((size_t)argc, sizeof(char*));
        str_objs = (jobject*)calloc((size_t)argc, sizeof(jobject));
    }

    jclass stringClass = (*env)->FindClass(env, "java/lang/String");
    jclass numberClass = (*env)->FindClass(env, "java/lang/Number");
    jclass doubleClass = (*env)->FindClass(env, "java/lang/Double");
    jclass floatClass = (*env)->FindClass(env, "java/lang/Float");
    jmethodID longValueMethod = (*env)->GetMethodID(env, numberClass, "longValue", "()J");
    jmethodID doubleValueMethod = (*env)->GetMethodID(env, numberClass, "doubleValue", "()D");

    jboolean ok = JNI_TRUE;
    for (jsize idx = 0; idx < argc && ok; idx++) {
        jobject obj = (*env)->GetObjectArrayElement(env, jargs, idx);
        if (obj == NULL) {
            args[idx].type = FORMAT_ARG_STRING;
            args[idx].s = NULL;
        } else if ((*env)->IsInstanceOf(env, obj, stringClass)) {
            const char* utf = (*env)->GetStringUTFChars(env, (jstring)obj, NULL);
            utf_refs[idx] = utf;
            str_objs[idx] = obj;
            args[idx].type = FORMAT_ARG_STRING;
            args[idx].s = utf;
        } else if ((*env)->IsInstanceOf(env, obj, doubleClass) || (*env)->IsInstanceOf(env, obj, floatClass)) {
            args[idx].type = FORMAT_ARG_DOUBLE;
            args[idx].d = (*env)->CallDoubleMethod(env, obj, doubleValueMethod);
            (*env)->DeleteLocalRef(env, obj);
        } else if ((*env)->IsInstanceOf(env, obj, numberClass)) {
            args[idx].type = FORMAT_ARG_INT64;
            args[idx].i = (*env)->CallLongMethod(env, obj, longValueMethod);
            (*env)->DeleteLocalRef(env, obj);
        } else {
            ok = JNI_FALSE;
            (*env)->DeleteLocalRef(env, obj);
        }
    }

    jstring result = NULL;
    if (!ok) {
        jclass iae = (*env)->FindClass(env, "java/lang/IllegalArgumentException");
        (*env)->ThrowNew(env, iae,
            "Unsupported argument type for formatString (expected String, Int, Long, Short, Byte, Float, or Double)");
    } else {
        char* out = (char*)malloc(OUT_BUFFER_SIZE);
        int n = format_string(out, OUT_BUFFER_SIZE, fmt, args, (int)argc);
        if (n < 0) {
            jclass iae = (*env)->FindClass(env, "java/lang/IllegalArgumentException");
            char msg[256];
            snprintf(msg, sizeof(msg), "Format string error: %.200s", fmt);
            (*env)->ThrowNew(env, iae, msg);
        } else {
            result = (*env)->NewStringUTF(env, out);
        }
        free(out);
    }

    for (jsize idx = 0; idx < argc; idx++) {
        if (str_objs[idx] != NULL) {
            (*env)->ReleaseStringUTFChars(env, (jstring)str_objs[idx], utf_refs[idx]);
            (*env)->DeleteLocalRef(env, str_objs[idx]);
        }
    }
    free(args);
    free((void*)utf_refs);
    free(str_objs);

    (*env)->ReleaseStringUTFChars(env, jfmt, fmt);
    return result;
}

static JNINativeMethod methods[] = {
    { (char*)"nativeFormat", (char*)"(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;", (void*)native_format_call }
};

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    (void)reserved;
    JNIEnv* env;
    if ((*vm)->GetEnv(vm, (void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    jclass clazz = (*env)->FindClass(env,
        "com/jabramson/iperf3_network_tester_kmp/utils/NativeFormatterBridge");
    if (clazz == NULL) {
        return JNI_ERR;
    }
    if ((*env)->RegisterNatives(env, clazz, methods, 1) != 0) {
        return JNI_ERR;
    }
    return JNI_VERSION_1_6;
}
