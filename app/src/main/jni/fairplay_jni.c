#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include <android/log.h>
#include "fairplay.h"

#define TAG "FairPlayJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Global FairPlay instance (one per process)
static fairplay_t *g_fairplay = NULL;

// Dummy logger for FairPlay library (matches typedef in fairplay.h)
struct logger_s {
    int dummy;
};

logger_t* logger_init() {
    return (logger_t*)calloc(1, sizeof(struct logger_s));
}

void logger_destroy(logger_t *logger) {
    free(logger);
}

void logger_log(logger_t *logger, int level, const char *fmt, ...) {
    // We'll just log to Android logcat instead
}

/**
 * Initialize FairPlay
 * Returns 0 on success, -1 on failure
 */
JNIEXPORT jint JNICALL
Java_com_pentagram_airplay_service_FairPlay_nativeInit(JNIEnv *env, jobject thiz) {
    if (g_fairplay != NULL) {
        LOGI("FairPlay already initialized");
        return 0;
    }

    logger_t *logger = logger_init();
    g_fairplay = fairplay_init(logger);

    if (g_fairplay == NULL) {
        LOGE("Failed to initialize FairPlay");
        logger_destroy(logger);
        return -1;
    }

    LOGI("FairPlay initialized successfully");
    return 0;
}

/**
 * FairPlay setup (fp-setup phase 1)
 * Input: 16-byte request
 * Output: 142-byte response
 */
JNIEXPORT jbyteArray JNICALL
Java_com_pentagram_airplay_service_FairPlay_nativeSetup(JNIEnv *env, jobject thiz, jbyteArray request) {
    if (g_fairplay == NULL) {
        LOGE("FairPlay not initialized");
        return NULL;
    }

    jsize req_len = (*env)->GetArrayLength(env, request);
    if (req_len != 16) {
        LOGE("Invalid request length: %d (expected 16)", req_len);
        return NULL;
    }

    unsigned char req_data[16];
    (*env)->GetByteArrayRegion(env, request, 0, 16, (jbyte*)req_data);

    unsigned char res_data[142];
    int ret = fairplay_setup(g_fairplay, req_data, res_data);

    if (ret != 0) {
        LOGE("FairPlay setup failed: %d", ret);
        return NULL;
    }

    LOGI("FairPlay setup successful");

    jbyteArray response = (*env)->NewByteArray(env, 142);
    (*env)->SetByteArrayRegion(env, response, 0, 142, (jbyte*)res_data);

    return response;
}

/**
 * FairPlay handshake (fp-setup phase 2)
 * Input: 164-byte request
 * Output: 32-byte response
 */
JNIEXPORT jbyteArray JNICALL
Java_com_pentagram_airplay_service_FairPlay_nativeHandshake(JNIEnv *env, jobject thiz, jbyteArray request) {
    if (g_fairplay == NULL) {
        LOGE("FairPlay not initialized");
        return NULL;
    }

    jsize req_len = (*env)->GetArrayLength(env, request);
    if (req_len != 164) {
        LOGE("Invalid request length: %d (expected 164)", req_len);
        return NULL;
    }

    unsigned char req_data[164];
    (*env)->GetByteArrayRegion(env, request, 0, 164, (jbyte*)req_data);

    unsigned char res_data[32];
    int ret = fairplay_handshake(g_fairplay, req_data, res_data);

    if (ret != 0) {
        LOGE("FairPlay handshake failed: %d", ret);
        return NULL;
    }

    LOGI("FairPlay handshake successful");

    jbyteArray response = (*env)->NewByteArray(env, 32);
    (*env)->SetByteArrayRegion(env, response, 0, 32, (jbyte*)res_data);

    return response;
}

/**
 * Decrypt ekey (72 bytes) to get AES key (16 bytes)
 * This is the critical function for video decryption!
 */
JNIEXPORT jbyteArray JNICALL
Java_com_pentagram_airplay_service_FairPlay_nativeDecrypt(JNIEnv *env, jobject thiz, jbyteArray encrypted_key) {
    if (g_fairplay == NULL) {
        LOGE("FairPlay not initialized");
        return NULL;
    }

    jsize ekey_len = (*env)->GetArrayLength(env, encrypted_key);
    if (ekey_len != 72) {
        LOGE("Invalid ekey length: %d (expected 72)", ekey_len);
        return NULL;
    }

    unsigned char ekey_data[72];
    (*env)->GetByteArrayRegion(env, encrypted_key, 0, 72, (jbyte*)ekey_data);

    unsigned char aes_key[16];
    int ret = fairplay_decrypt(g_fairplay, ekey_data, aes_key);

    if (ret != 0) {
        LOGE("FairPlay decrypt failed: %d", ret);
        return NULL;
    }

    LOGI("FairPlay decrypt successful - got 16-byte AES key");
    LOGI("AES key: %02X %02X %02X %02X %02X %02X %02X %02X...",
         aes_key[0], aes_key[1], aes_key[2], aes_key[3],
         aes_key[4], aes_key[5], aes_key[6], aes_key[7]);

    jbyteArray result = (*env)->NewByteArray(env, 16);
    (*env)->SetByteArrayRegion(env, result, 0, 16, (jbyte*)aes_key);

    return result;
}

/**
 * Cleanup FairPlay instance
 */
JNIEXPORT void JNICALL
Java_com_pentagram_airplay_service_FairPlay_nativeDestroy(JNIEnv *env, jobject thiz) {
    if (g_fairplay != NULL) {
        fairplay_destroy(g_fairplay);
        g_fairplay = NULL;
        LOGI("FairPlay destroyed");
    }
}
