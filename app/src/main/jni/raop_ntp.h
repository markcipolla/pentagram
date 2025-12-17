/**
 * Minimal raop_ntp.h stub for Android JNI integration
 *
 * Only needed by raop_rtp.h which we don't actually use in our JNI code.
 * We only need the constants from raop.h and raop_rtp.h.
 */

#ifndef RAOP_NTP_H
#define RAOP_NTP_H

#ifdef __cplusplus
extern "C" {
#endif

// Forward declaration - opaque type
typedef struct raop_ntp_s raop_ntp_t;

#ifdef __cplusplus
}
#endif

#endif // RAOP_NTP_H
