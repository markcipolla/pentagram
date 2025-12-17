/**
 * Minimal raop.h stub for Android JNI integration
 * Only includes the constants and types needed by mirror_buffer.c
 */

#ifndef RAOP_H
#define RAOP_H

#ifdef __cplusplus
extern "C" {
#endif

#define RAOP_AESKEY_LEN 16
#define RAOP_AESIV_LEN  16

// Forward declaration - opaque type used in raop_rtp.h
typedef struct raop_callbacks_s raop_callbacks_t;

#ifdef __cplusplus
}
#endif

#endif // RAOP_H
