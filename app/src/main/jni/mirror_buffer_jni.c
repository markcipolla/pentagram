#include <jni.h>
#include <android/log.h>
#include <string.h>
#include "mirror_buffer.h"

#define LOG_TAG "MirrorBufferJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Java: native long nativeInit(byte[] aeskey)
JNIEXPORT jlong JNICALL
Java_com_pentagram_airplay_crypto_MirrorBufferDecryptor_nativeInit(JNIEnv *env, jobject thiz, jbyteArray aeskey) {
    jbyte *key_bytes = (*env)->GetByteArrayElements(env, aeskey, NULL);
    jsize key_len = (*env)->GetArrayLength(env, aeskey);

    if (key_len != 16) {
        LOGE("Invalid AES key length: %d (expected 16)", key_len);
        (*env)->ReleaseByteArrayElements(env, aeskey, key_bytes, JNI_ABORT);
        return 0;
    }

    // Initialize mirror_buffer with NULL logger (we'll use Android logging)
    mirror_buffer_t *buffer = mirror_buffer_init(NULL, (const unsigned char*)key_bytes);

    (*env)->ReleaseByteArrayElements(env, aeskey, key_bytes, JNI_ABORT);

    if (buffer == NULL) {
        LOGE("Failed to initialize mirror_buffer");
        return 0;
    }

    LOGI("Mirror buffer initialized successfully");
    return (jlong)buffer;
}

// Java: native void nativeInitAes(long handle, long streamConnectionID)
JNIEXPORT void JNICALL
Java_com_pentagram_airplay_crypto_MirrorBufferDecryptor_nativeInitAes(JNIEnv *env, jobject thiz, jlong handle, jlong stream_connection_id) {
    mirror_buffer_t *buffer = (mirror_buffer_t*)handle;
    if (buffer == NULL) {
        LOGE("Invalid mirror_buffer handle");
        return;
    }

    uint64_t sid = (uint64_t)stream_connection_id;
    mirror_buffer_init_aes(buffer, &sid);

    LOGI("AES initialized for streamConnectionID: %llu", (unsigned long long)sid);
}

// Java: native byte[] nativeDecrypt(long handle, byte[] input)
JNIEXPORT jbyteArray JNICALL
Java_com_pentagram_airplay_crypto_MirrorBufferDecryptor_nativeDecrypt(JNIEnv *env, jobject thiz, jlong handle, jbyteArray input) {
    mirror_buffer_t *buffer = (mirror_buffer_t*)handle;
    if (buffer == NULL) {
        LOGE("Invalid mirror_buffer handle");
        return NULL;
    }

    jbyte *input_bytes = (*env)->GetByteArrayElements(env, input, NULL);
    jsize input_len = (*env)->GetArrayLength(env, input);

    // Create output array (same size as input)
    jbyteArray output = (*env)->NewByteArray(env, input_len);
    if (output == NULL) {
        LOGE("Failed to allocate output array");
        (*env)->ReleaseByteArrayElements(env, input, input_bytes, JNI_ABORT);
        return NULL;
    }

    jbyte *output_bytes = (*env)->GetByteArrayElements(env, output, NULL);

    // Decrypt using UxPlay's mirror_buffer_decrypt
    mirror_buffer_decrypt(buffer, (unsigned char*)input_bytes, (unsigned char*)output_bytes, input_len);

    (*env)->ReleaseByteArrayElements(env, input, input_bytes, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, output, output_bytes, 0);

    return output;
}

// Java: native void nativeDestroy(long handle)
JNIEXPORT void JNICALL
Java_com_pentagram_airplay_crypto_MirrorBufferDecryptor_nativeDestroy(JNIEnv *env, jobject thiz, jlong handle) {
    mirror_buffer_t *buffer = (mirror_buffer_t*)handle;
    if (buffer != NULL) {
        mirror_buffer_destroy(buffer);
        LOGI("Mirror buffer destroyed");
    }
}
