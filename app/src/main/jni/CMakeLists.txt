cmake_minimum_required(VERSION 3.18.1)

project("airplay_crypto")

# Add FairPlay PlayFair library (from UxPlay)
add_library(fairplay STATIC
        fairplay_playfair.c
        playfair/playfair.c
        playfair/omg_hax.c
        playfair/modified_md5.c
        playfair/sap_hash.c
        playfair/hand_garble.c)

# Add UxPlay crypto library (mirror_buffer + crypto)
add_library(uxplay_crypto STATIC
        crypto.c
        mirror_buffer.c)

# Import Conscrypt's native library (provides BoringSSL symbols)
# Conscrypt is extracted by Gradle and available in the build intermediates
set(CONSCRYPT_LIB_DIR "${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}")
add_library(conscrypt_jni SHARED IMPORTED)
set_target_properties(conscrypt_jni PROPERTIES
    IMPORTED_LOCATION "${CONSCRYPT_LIB_DIR}/libconscrypt_jni.so")

# Add our JNI library
add_library(airplay_crypto SHARED
        airplay_crypto_jni.c
        fairplay_jni.c
        mirror_buffer_jni.c)

# Ensure 16 KB page alignment (required for Android 15+)
target_link_options(airplay_crypto PRIVATE "LINKER:-z,max-page-size=16384")

# Link libraries
# Link against Conscrypt to get BoringSSL symbols
target_link_libraries(airplay_crypto
        fairplay
        uxplay_crypto
        conscrypt_jni
        android
        log
        m
        dl)

# For Android API 24, BoringSSL crypto functions are in system libraries but not
# exposed as linkable NDK libraries. We declare the functions in openssl_compat.h
# and they will be resolved at runtime from the system's libcrypto.so
if(ANDROID_ALLOW_UNDEFINED_SYMBOLS)
    # Remove strict linker flags and allow undefined symbols
    string(REPLACE "-Wl,--no-undefined" "" CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS}")
    string(REPLACE "-Wl,--fatal-warnings" "" CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS}")
    set_target_properties(airplay_crypto PROPERTIES
        LINK_FLAGS "-Wl,--warn-unresolved-symbols")
endif()
