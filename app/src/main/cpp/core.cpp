#include <jni.h>
#include <android/log.h>
#include <mutex>
#include <vector>
#include "core_interface.h"

#define LOG_TAG "core_jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
    CoreInterface* g_core = nullptr;
    std::mutex g_coreMutex;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_io_github_gbdroid_core_Core_nativeInit(JNIEnv* env, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_coreMutex);
    if (g_core != nullptr) {
        LOGI("nativeInit called but core already exists; reusing");
        return JNI_TRUE;
    }
    g_core = createCore();
    bool ok = g_core->init();
    if (!ok) {
        LOGE("core init failed");
        delete g_core;
        g_core = nullptr;
    }
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_io_github_gbdroid_core_Core_nativeShutdown(JNIEnv* env, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_coreMutex);
    if (g_core != nullptr) {
        g_core->shutdown();
        delete g_core;
        g_core = nullptr;
    }
}

JNIEXPORT jboolean JNICALL
Java_io_github_gbdroid_core_Core_nativeLoadRom(JNIEnv* env, jobject /*thiz*/, jbyteArray romData) {
    std::lock_guard<std::mutex> lock(g_coreMutex);
    if (g_core == nullptr) {
        LOGE("nativeLoadRom called before nativeInit");
        return JNI_FALSE;
    }
    jsize len = env->GetArrayLength(romData);
    std::vector<uint8_t> buffer(static_cast<size_t>(len));
    env->GetByteArrayRegion(romData, 0, len, reinterpret_cast<jbyte*>(buffer.data()));

    bool ok = g_core->loadRom(buffer.data(), buffer.size());
    LOGI("nativeLoadRom: %zu bytes, success=%d", buffer.size(), ok);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_io_github_gbdroid_core_Core_nativeReset(JNIEnv* env, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_coreMutex);
    if (g_core) g_core->reset();
}

JNIEXPORT void JNICALL
Java_io_github_gbdroid_core_Core_nativeRunFrame(JNIEnv* env, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_coreMutex);
    if (g_core) g_core->runFrame();
}

JNIEXPORT void JNICALL
Java_io_github_gbdroid_core_Core_nativeGetVideoBuffer(JNIEnv* env, jobject /*thiz*/, jintArray outPixels) {
    std::lock_guard<std::mutex> lock(g_coreMutex);
    if (!g_core) return;
    const uint32_t* buf = g_core->getVideoBuffer();
    jsize len = env->GetArrayLength(outPixels);
    env->SetIntArrayRegion(outPixels, 0, len, reinterpret_cast<const jint*>(buf));
}

JNIEXPORT jint JNICALL
Java_io_github_gbdroid_core_Core_nativeGetWidth(JNIEnv* env, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_coreMutex);
    return g_core ? g_core->getWidth() : 0;
}

JNIEXPORT jint JNICALL
Java_io_github_gbdroid_core_Core_nativeGetHeight(JNIEnv* env, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_coreMutex);
    return g_core ? g_core->getHeight() : 0;
}

JNIEXPORT void JNICALL
Java_io_github_gbdroid_core_Core_nativeSetKeys(JNIEnv* env, jobject /*thiz*/, jint keyMask) {
    std::lock_guard<std::mutex> lock(g_coreMutex);
    if (g_core) g_core->setKeys(static_cast<uint16_t>(keyMask));
}

JNIEXPORT jint JNICALL
Java_io_github_gbdroid_core_Core_nativeFillAudioBuffer(JNIEnv* env, jobject /*thiz*/, jshortArray outSamples) {
    std::lock_guard<std::mutex> lock(g_coreMutex);
    if (!g_core) return 0;
    jsize len = env->GetArrayLength(outSamples);
    size_t maxFrames = static_cast<size_t>(len) / 2;
    std::vector<int16_t> buffer(maxFrames * 2);
    size_t written = g_core->fillAudioBuffer(buffer.data(), maxFrames);
    env->SetShortArrayRegion(outSamples, 0, static_cast<jsize>(written * 2), buffer.data());
    return static_cast<jint>(written);
}

JNIEXPORT jint JNICALL
Java_io_github_gbdroid_core_Core_nativeGetAudioSampleRate(JNIEnv* env, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_coreMutex);
    return g_core ? g_core->getAudioSampleRate() : 32768;
}

// restores previously persisted cart save bytes into the core, must be called after nativeLoadRom()
JNIEXPORT jboolean JNICALL
Java_io_github_gbdroid_core_Core_nativeLoadSaveData(JNIEnv* env, jobject /*thiz*/, jbyteArray saveData) {
    std::lock_guard<std::mutex> lock(g_coreMutex);
    if (!g_core) return JNI_FALSE;
    jsize len = env->GetArrayLength(saveData);
    std::vector<uint8_t> buffer(static_cast<size_t>(len));
    if (len > 0) {
        env->GetByteArrayRegion(saveData, 0, len, reinterpret_cast<jbyte*>(buffer.data()));
    }
    bool ok = g_core->loadSaveData(buffer.data(), buffer.size());
    LOGI("nativeLoadSaveData: %zu bytes, success=%d", buffer.size(), ok);
    return ok ? JNI_TRUE : JNI_FALSE;
}

// returns the current cart save data as a new Java byte[] for the caller to persist to disk. returns a zero length array if there's no
JNIEXPORT jbyteArray JNICALL
Java_io_github_gbdroid_core_Core_nativeExportSaveData(JNIEnv* env, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_coreMutex);
    std::vector<uint8_t> data = g_core ? g_core->exportSaveData() : std::vector<uint8_t>();
    jbyteArray result = env->NewByteArray(static_cast<jsize>(data.size()));
    if (!data.empty()) {
        env->SetByteArrayRegion(result, 0, static_cast<jsize>(data.size()), reinterpret_cast<const jbyte*>(data.data()));
    }
    return result;
}

JNIEXPORT jstring JNICALL
Java_io_github_gbdroid_core_Core_nativeGetGameTitle(JNIEnv* env, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_coreMutex);
    std::string title = g_core ? g_core->getGameTitle() : "";
    return env->NewStringUTF(title.c_str());
}

JNIEXPORT jstring JNICALL
Java_io_github_gbdroid_core_Core_nativeGetGameCode(JNIEnv* env, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_coreMutex);
    std::string code = g_core ? g_core->getGameCode() : "";
    return env->NewStringUTF(code.c_str());
}

JNIEXPORT jint JNICALL
Java_io_github_gbdroid_core_Core_nativeGetPlatform(JNIEnv* env, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_coreMutex);
    return g_core ? g_core->getPlatform() : -1;
}

} // extern "C"
