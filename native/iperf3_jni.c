/*
 * iperf3_jni.c — thin JNI adapter over iperf3_bridge.c (see
 * IPERF3_NATIVE_INTEGRATION.md §8.4). All the actual iperf3 test-lifecycle,
 * threading, stall-detection, and stderr-warning-filtering logic lives in
 * iperf3_bridge.c/.h now, with zero JNI types in it — this file's only job
 * is translating between that platform-agnostic `struct IperfCallbacks`
 * interface and real JNI calls (`JNIEnv`, `jmethodID`, `CallVoidMethod`).
 *
 * Shared verbatim between Android (:iperf3native's CMake/NDK build) and
 * desktop JVM (:shared's compileIperf3Jvm Gradle task) — both are ordinary
 * JNI, so the same wrapper works unmodified on either JVM implementation.
 * Both target the same Kotlin bridge:
 * com.jabramson.iperf3_network_tester_kmp.utils.NativeIperfBridge
 *
 * Native methods are bound explicitly via RegisterNatives() in JNI_OnLoad
 * instead of relying on javah-style symbol name mangling, which is fragile
 * for packages containing underscores (this project's package does) — same
 * convention as native/native_format_jni.c.
 */
#include <jni.h>
#include <stdlib.h>
#include <string.h>

#include "iperf3_bridge.h"

/* iperf3_bridge_native_iperf3() calls back into on_output/on_error/on_complete
 * both from the calling (JNI) thread directly (a few synchronous error/
 * status messages) and from background threads it creates itself (the
 * monitor/reader/stderr-reader threads, via on_thread_attach/detach) —
 * see iperf3_bridge.h's header comment for why the split exists. Either
 * way, each thread that can call one of the callbacks needs its own
 * currently-attached JNIEnv*, and JNIEnv* is only ever valid on the thread
 * that owns it — hence thread-local storage rather than a single shared
 * field on the ctx struct. The calling thread's own env is stashed here
 * directly (it's already attached, no AttachCurrentThread needed); each
 * spawned thread's jniThreadAttach fills this in for itself via
 * AttachCurrentThread. */
static _Thread_local JNIEnv *tls_env = NULL;

struct JniRunCtx {
    JavaVM *jvm;
    jobject callback_global;
    jmethodID onOutput;
    jmethodID onError;
    jmethodID onTime;
    jmethodID onComplete;
};

static void jniThreadAttach(void *ctx_ptr) {
    struct JniRunCtx *ctx = (struct JniRunCtx *) ctx_ptr;
    JNIEnv *env;
    (*ctx->jvm)->AttachCurrentThread(ctx->jvm, (void **) &env, NULL);
    tls_env = env;
}

static void jniThreadDetach(void *ctx_ptr) {
    struct JniRunCtx *ctx = (struct JniRunCtx *) ctx_ptr;
    (*ctx->jvm)->DetachCurrentThread(ctx->jvm);
    tls_env = NULL;
}

static void jniOnOutput(void *ctx_ptr, const char *line) {
    struct JniRunCtx *ctx = (struct JniRunCtx *) ctx_ptr;
    JNIEnv *env = tls_env;
    jstring jline = (*env)->NewStringUTF(env, line);
    (*env)->CallVoidMethod(env, ctx->callback_global, ctx->onOutput, jline);
    (*env)->DeleteLocalRef(env, jline);
}

static void jniOnTime(void *ctx_ptr, const char *now) {
    struct JniRunCtx *ctx = (struct JniRunCtx *) ctx_ptr;
    JNIEnv *env = tls_env;
    jstring jline = (*env)->NewStringUTF(env, now);
    (*env)->CallVoidMethod(env, ctx->callback_global, ctx->onTime, jline);
    (*env)->DeleteLocalRef(env, jline);
}

static void jniOnError(void *ctx_ptr, const char *line) {
    struct JniRunCtx *ctx = (struct JniRunCtx *) ctx_ptr;
    JNIEnv *env = tls_env;
    jstring jline = (*env)->NewStringUTF(env, line);
    (*env)->CallVoidMethod(env, ctx->callback_global, ctx->onError, jline);
    (*env)->DeleteLocalRef(env, jline);
}

static void jniOnComplete(void *ctx_ptr) {
    struct JniRunCtx *ctx = (struct JniRunCtx *) ctx_ptr;
    JNIEnv *env = tls_env;
    (*env)->CallVoidMethod(env, ctx->callback_global, ctx->onComplete);
}

static void nativeSetTempDir(JNIEnv *env, jclass clazz, jstring tempDir) {
    (void) clazz;
    if (tempDir == NULL) {
        iperf3_bridge_set_temp_dir(NULL);
        return;
    }
    const char *c_path = (*env)->GetStringUTFChars(env, tempDir, NULL);
    if (c_path == NULL) {
        return; /* OutOfMemoryError already pending */
    }
    iperf3_bridge_set_temp_dir(c_path);
    (*env)->ReleaseStringUTFChars(env, tempDir, c_path);
}

static void nativeSetDebug(JNIEnv *env, jclass clazz, jint debug) {
    (void) env;
    (void) clazz;
    iperf3_bridge_set_debug((int) debug);
}

static void nativeSetStallTimeoutSeconds(JNIEnv *env, jclass clazz, jint seconds) {
    (void) env;
    (void) clazz;
    iperf3_bridge_set_stall_timeout_seconds((int) seconds);
}

/* Context for the single synchronous on_output call forceStop makes — no
 * thread is spawned here, so no attach/detach hooks or thread-local lookup
 * are needed; the JNIEnv passed into this JNI call is used directly. */
struct JniForceStopCtx {
    JNIEnv *env;
    jobject callback;
    jmethodID onOutput;
};

static void jniForceStopOnOutput(void *ctx_ptr, const char *line) {
    struct JniForceStopCtx *ctx = (struct JniForceStopCtx *) ctx_ptr;
    jstring jline = (*ctx->env)->NewStringUTF(ctx->env, line);
    (*ctx->env)->CallVoidMethod(ctx->env, ctx->callback, ctx->onOutput, jline);
    (*ctx->env)->DeleteLocalRef(ctx->env, jline);
}

static void nativeForceStop(JNIEnv *env, jclass clazz, jobject callback) {
    (void) clazz;
    jclass callbackClass = (*env)->GetObjectClass(env, callback);
    jmethodID onOutput = (*env)->GetMethodID(env, callbackClass, "onOutput", "(Ljava/lang/String;)V");

    struct JniForceStopCtx ctx = { env, callback, onOutput };
    struct IperfCallbacks callbacks = {
        .ctx = &ctx,
        .on_thread_attach = NULL,
        .on_thread_detach = NULL,
        .on_output = jniForceStopOnOutput,
        .on_time = NULL,
        .on_error = NULL,
        .on_complete = NULL,
    };
    iperf3_bridge_force_stop(&callbacks);
}

static jint nativeIperf3(JNIEnv *env, jclass clazz, jobjectArray arguments, jobject callback) {
    (void) clazz;
    tls_env = env;

    jclass callbackClass = (*env)->GetObjectClass(env, callback);
    jmethodID onOutput = (*env)->GetMethodID(env, callbackClass, "onOutput", "(Ljava/lang/String;)V");
    jmethodID onError = (*env)->GetMethodID(env, callbackClass, "onError", "(Ljava/lang/String;)V");
    jmethodID onComplete = (*env)->GetMethodID(env, callbackClass, "onComplete", "()V");
    jmethodID onTime = (*env)->GetMethodID(env, callbackClass, "onTime", "(Ljava/lang/String;)V");

    /* ----- Convert Java String[] to native char* argv[] ----- */
    jsize argc = (*env)->GetArrayLength(env, arguments);
    if (argc > 64) argc = 64;

    char *argv[64];
    for (jsize i = 0; i < argc; i++) {
        jstring arg = (jstring) (*env)->GetObjectArrayElement(env, arguments, i);
        const char *arg_str = (*env)->GetStringUTFChars(env, arg, NULL);
        argv[i] = strdup(arg_str);
        (*env)->ReleaseStringUTFChars(env, arg, arg_str);
        (*env)->DeleteLocalRef(env, arg);
    }

    JavaVM *jvm;
    (*env)->GetJavaVM(env, &jvm);
    struct JniRunCtx ctx = {
        .jvm = jvm,
        .callback_global = (*env)->NewGlobalRef(env, callback),
        .onOutput = onOutput,
        .onTime = onTime,
        .onError = onError,
        .onComplete = onComplete,
    };
    struct IperfCallbacks callbacks = {
        .ctx = &ctx,
        .on_thread_attach = jniThreadAttach,
        .on_thread_detach = jniThreadDetach,
        .on_output = jniOnOutput,
        .on_time = jniOnTime,
        .on_error = jniOnError,
        .on_complete = jniOnComplete,
    };

    int result = iperf3_bridge_native_iperf3(argv, (int) argc, &callbacks);

    (*env)->DeleteGlobalRef(env, ctx.callback_global);
    for (jsize i = 0; i < argc; i++) free(argv[i]);
    tls_env = NULL;
    return result;
}

static JNINativeMethod methods[] = {
        {(char *) "nativeSetTempDir", (char *) "(Ljava/lang/String;)V", (void *) nativeSetTempDir},
        {(char *) "nativeSetStallTimeoutSeconds", (char *) "(I)V", (void *) nativeSetStallTimeoutSeconds},
        {(char *) "nativeSetDebug", (char *) "(I)V", (void *) nativeSetDebug},

        {(char *) "nativeForceStop",
                (char *) "(Lcom/jabramson/iperf3_network_tester_kmp/runner/IperfCallback;)V",
                (void *) nativeForceStop},

        {(char *) "nativeIperf3",
                (char *) "([Ljava/lang/String;Lcom/jabramson/iperf3_network_tester_kmp/runner/IperfCallback;)I",
                (void *) nativeIperf3},
};

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void) reserved;
    /* Runs exactly once, when the native library is loaded - before any
     * JNI method below can possibly be called - so this is the one
     * deliberate call site for Android/desktop. See initOrigStderr()'s own
     * doc comment (iperf3_bridge.c) for why the ordering matters. */
    initOrigStderr();

    JNIEnv *env;
    if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    jclass clazz = (*env)->FindClass(env,
        "com/jabramson/iperf3_network_tester_kmp/utils/NativeIperfBridge");
    if (clazz == NULL) {
        return JNI_ERR;
    }
    if ((*env)->RegisterNatives(env, clazz, methods, sizeof(methods) / sizeof(methods[0])) != 0) {
        return JNI_ERR;
    }
    return JNI_VERSION_1_6;
}
