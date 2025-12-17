/**
 * OpenSSL compatibility layer for Android BoringSSL (API < 29)
 *
 * This header provides OpenSSL types and function declarations
 * that are used by UxPlay crypto.c. The actual BoringSSL library
 * is available in Android, but the headers are not exposed until API 29+.
 *
 * We declare the functions we need and link against libcrypto.so which
 * contains BoringSSL's implementation of these OpenSSL-compatible functions.
 */

#ifndef OPENSSL_COMPAT_H
#define OPENSSL_COMPAT_H

#include <stddef.h>
#include <stdint.h>
#include <stdio.h>

#ifdef __cplusplus
extern "C" {
#endif

// ============================================================================
// EVP (Envelope) API - High-level crypto interface
// ============================================================================

typedef struct evp_cipher_st EVP_CIPHER;
typedef struct evp_cipher_ctx_st EVP_CIPHER_CTX;
typedef struct evp_md_st EVP_MD;
typedef struct evp_md_ctx_st EVP_MD_CTX;
typedef struct evp_pkey_st EVP_PKEY;
typedef struct evp_pkey_ctx_st EVP_PKEY_CTX;
typedef struct bio_st BIO;
typedef struct bio_method_st BIO_METHOD;
typedef struct buf_mem_st BUF_MEM;

// Cipher operations
const EVP_CIPHER *EVP_aes_128_ctr(void);
const EVP_CIPHER *EVP_aes_128_cbc(void);
const EVP_CIPHER *EVP_aes_128_gcm(void);

EVP_CIPHER_CTX *EVP_CIPHER_CTX_new(void);
void EVP_CIPHER_CTX_free(EVP_CIPHER_CTX *ctx);
int EVP_CIPHER_CTX_reset(EVP_CIPHER_CTX *ctx);
int EVP_CIPHER_CTX_set_padding(EVP_CIPHER_CTX *ctx, int padding);
int EVP_CIPHER_CTX_ctrl(EVP_CIPHER_CTX *ctx, int type, int arg, void *ptr);

int EVP_EncryptInit_ex(EVP_CIPHER_CTX *ctx, const EVP_CIPHER *cipher, void *impl, const unsigned char *key, const unsigned char *iv);
int EVP_EncryptUpdate(EVP_CIPHER_CTX *ctx, unsigned char *out, int *outl, const unsigned char *in, int inl);
int EVP_EncryptFinal_ex(EVP_CIPHER_CTX *ctx, unsigned char *out, int *outl);

int EVP_DecryptInit_ex(EVP_CIPHER_CTX *ctx, const EVP_CIPHER *cipher, void *impl, const unsigned char *key, const unsigned char *iv);
int EVP_DecryptUpdate(EVP_CIPHER_CTX *ctx, unsigned char *out, int *outl, const unsigned char *in, int inl);
int EVP_DecryptFinal_ex(EVP_CIPHER_CTX *ctx, unsigned char *out, int *outl);

// GCM control commands
#define EVP_CTRL_GCM_SET_IVLEN 0x9
#define EVP_CTRL_GCM_GET_TAG   0x10
#define EVP_CTRL_GCM_SET_TAG   0x11

// Message digest operations
const EVP_MD *EVP_sha512(void);
const EVP_MD *EVP_md5(void);

EVP_MD_CTX *EVP_MD_CTX_new(void);
void EVP_MD_CTX_free(EVP_MD_CTX *ctx);
int EVP_MD_CTX_reset(EVP_MD_CTX *ctx);

int EVP_DigestInit_ex(EVP_MD_CTX *ctx, const EVP_MD *type, void *impl);
int EVP_DigestUpdate(EVP_MD_CTX *ctx, const void *d, size_t cnt);
int EVP_DigestFinal_ex(EVP_MD_CTX *ctx, unsigned char *md, unsigned int *s);

// Digital signature operations
int EVP_DigestSignInit(EVP_MD_CTX *ctx, EVP_PKEY_CTX **pctx, const EVP_MD *type, void *e, EVP_PKEY *pkey);
int EVP_DigestSign(EVP_MD_CTX *ctx, unsigned char *sigret, size_t *siglen, const unsigned char *tbs, size_t tbslen);
int EVP_DigestVerifyInit(EVP_MD_CTX *ctx, EVP_PKEY_CTX **pctx, const EVP_MD *type, void *e, EVP_PKEY *pkey);
int EVP_DigestVerify(EVP_MD_CTX *ctx, const unsigned char *sigret, size_t siglen, const unsigned char *tbs, size_t tbslen);

// Key types
#define EVP_PKEY_X25519  1034
#define EVP_PKEY_ED25519 1087

// PKEY operations
EVP_PKEY *EVP_PKEY_new_raw_public_key(int type, void *e, const unsigned char *key, size_t keylen);
EVP_PKEY *EVP_PKEY_new_raw_private_key(int type, void *e, const unsigned char *key, size_t keylen);
int EVP_PKEY_get_raw_public_key(const EVP_PKEY *pkey, unsigned char *pub, size_t *len);
void EVP_PKEY_free(EVP_PKEY *pkey);
int EVP_PKEY_up_ref(EVP_PKEY *pkey);

EVP_PKEY_CTX *EVP_PKEY_CTX_new(EVP_PKEY *pkey, void *e);
EVP_PKEY_CTX *EVP_PKEY_CTX_new_id(int id, void *e);
void EVP_PKEY_CTX_free(EVP_PKEY_CTX *ctx);
int EVP_PKEY_keygen_init(EVP_PKEY_CTX *ctx);
int EVP_PKEY_keygen(EVP_PKEY_CTX *ctx, EVP_PKEY **pkey);
int EVP_PKEY_derive_init(EVP_PKEY_CTX *ctx);
int EVP_PKEY_derive_set_peer(EVP_PKEY_CTX *ctx, EVP_PKEY *peer);
int EVP_PKEY_derive(EVP_PKEY_CTX *ctx, unsigned char *key, size_t *keylen);

// ============================================================================
// Error handling
// ============================================================================

unsigned long ERR_get_error(void);
const char *ERR_error_string(unsigned long e, char *buf);

// ============================================================================
// Random number generation
// ============================================================================

int RAND_bytes(unsigned char *buf, int num);

// ============================================================================
// PEM (Privacy Enhanced Mail) format
// ============================================================================

EVP_PKEY *PEM_read_PrivateKey(FILE *fp, EVP_PKEY **x, void *cb, void *u);

// ============================================================================
// BIO (Basic I/O) abstraction
// ============================================================================

struct buf_mem_st {
    size_t length;
    char *data;
    size_t max;
};

BIO *BIO_new(const BIO_METHOD *type);
BIO *BIO_new_fp(FILE *stream, int close_flag);
const BIO_METHOD *BIO_f_base64(void);
const BIO_METHOD *BIO_s_mem(void);
void BIO_free(BIO *a);
void BIO_free_all(BIO *a);
BIO *BIO_push(BIO *b, BIO *append);
int BIO_write(BIO *b, const void *data, int len);
int BIO_flush(BIO *b);
long BIO_ctrl(BIO *bp, int cmd, long larg, void *parg);
int BIO_get_mem_ptr(BIO *b, BUF_MEM **pp);
long BIO_set_close(BIO *b, long flag);
long BIO_set_flags(BIO *b, int flags);

// BIO control commands
#define BIO_C_SET_BUF_MEM_EOF_RETURN 130
#define BIO_get_mem_ptr(b,pp) BIO_ctrl(b,BIO_C_GET_BUF_MEM_PTR,0,(char *)pp)
#define BIO_C_GET_BUF_MEM_PTR 115
#define BIO_set_close(b,c) (int)BIO_ctrl(b,BIO_CTRL_SET_CLOSE,(c),NULL)
#define BIO_CTRL_SET_CLOSE 9

// BIO flags
#define BIO_FLAGS_BASE64_NO_NL 0x100
#define BIO_NOCLOSE 0
#define BIO_CLOSE   1

int PEM_write_bio_PrivateKey(BIO *bp, EVP_PKEY *x, const EVP_CIPHER *enc, unsigned char *kstr, int klen, void *cb, void *u);

// ============================================================================
// SHA-512 constants
// ============================================================================

#define SHA512_DIGEST_LENGTH 64

#ifdef __cplusplus
}
#endif

#endif // OPENSSL_COMPAT_H
