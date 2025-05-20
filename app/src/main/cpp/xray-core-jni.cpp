#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "XrayCoreJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Forward declarations for Xray core functions
// These would be implemented by the actual Xray core library
extern "C" {
    bool startXray(const char* configPath, int fd);
    bool stopXray();
    const char* getXrayVersion();
}

extern "C" {
    // Start Xray with config file and VPN file descriptor
    JNIEXPORT jboolean JNICALL
    Java_com_hiddify_hiddifyng_core_XrayManager_startXrayWithConfig(JNIEnv *env, jobject thiz, jstring configFile, jint fd) {
        const char *config_path = env->GetStringUTFChars(configFile, nullptr);
        bool result = startXray(config_path, fd);
        env->ReleaseStringUTFChars(configFile, config_path);
        return static_cast<jboolean>(result);
    }

    // Stop Xray core
    JNIEXPORT jboolean JNICALL
    Java_com_hiddify_hiddifyng_core_XrayManager_stopXray(JNIEnv *env, jobject thiz) {
        return static_cast<jboolean>(stopXray());
    }

    // Get Xray core version
    JNIEXPORT jstring JNICALL
    Java_com_hiddify_hiddifyng_core_XrayManager_getXrayVersion(JNIEnv *env, jobject thiz) {
        const char* version = getXrayVersion();
        return env->NewStringUTF(version);
    }
}