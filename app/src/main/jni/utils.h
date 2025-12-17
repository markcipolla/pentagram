/**
 * Minimal utils.h stub for Android JNI integration
 *
 * Only used by get_md5() function in crypto.c which is not needed
 * for video decryption functionality.
 */

#ifndef UTILS_H
#define UTILS_H

#include <stdlib.h>
#include <stdio.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Convert binary data to hex string
 * Note: This is only used by get_md5() which is not used by mirror_buffer
 */
static inline char *utils_hex_to_string(const unsigned char *data, int len) {
    char *str = (char *)malloc(len * 2 + 1);
    if (str) {
        for (int i = 0; i < len; i++) {
            sprintf(str + i * 2, "%02x", data[i]);
        }
        str[len * 2] = '\0';
    }
    return str;
}

#ifdef __cplusplus
}
#endif

#endif // UTILS_H
