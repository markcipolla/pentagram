/**
 * Pentagram AirPlay Crypto JNI Wrapper
 * Simple test to verify JNI build system works
 * TODO: Add OpenSSL once we get prebuilt libraries integrated
 */

#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include <android/log.h>

#define LOG_TAG "AirPlayCryptoJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/**
 * Test function to verify JNI is working
 */
JNIEXPORT jstring JNICALL
Java_com_pentagram_airplay_service_AirPlayCryptoNative_testJNI(JNIEnv *env, jobject thiz) {
    LOGI("JNI test function called successfully!");
    return (*env)->NewStringUTF(env, "JNI is working! OpenSSL integration pending...");
}

/**
 * Get library version info
 */
JNIEXPORT jstring JNICALL
Java_com_pentagram_airplay_service_AirPlayCryptoNative_getVersion(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "Pentagram Native Crypto v1.0 (OpenSSL pending)");
}
