/*
 * native-key.c - Native key decoding layer for CS702 Fortify
 *
 * This compiled native library provides an additional security layer:
 * Key transformation constants (XOR seed, shuffle table) are invisible in
 * Java decompilation — only visible in compiled ARM/x86 machine code.
 *
 * Protection layers:
 *   Java:    reversed hex string in bytecode
 *   Native:  .so file holds XOR_SEED + SHUFFLE table (in ARM machine code)
 *             getNativeKey() applies byte-level unshuffle + XOR
 *             verifyNative() checks key format
 *
 * Attackers would need to:
 * (1) Find _rev string in bytecode
 * (2) Know it's reversed (Java layer)
 * (3) Load and reverse-engineer libnative-key.so (ARM disassembly)
 * (4) Extract SHUFFLE table + XOR_SEED from machine code
 * (5) Understand the unshuffle + XOR reconstruction
 */
#include <string.h>
#include <stdlib.h>
#include <jni.h>

/* XOR seed — makes byte transformation non-trivial */
#define XOR_SEED 0x5A

/* Shuffle table: byte written to OUTPUT[SHUFFLE[i]] from INPUT[i] */
/* We invert this: to find original byte at position i, look at SHUFFLE[i] */
static const unsigned char SHUFFLE[64] = {
    63, 31, 59, 15, 55, 47, 51,  7, 43, 27, 39, 11, 35, 23, 19,  3,
    62, 30, 58, 14, 54, 46, 50,  6, 42, 26, 38, 10, 34, 22, 18,  2,
    61, 29, 57, 13, 53, 45, 49,  5, 41, 25, 37,  9, 33, 21, 17,  1,
    60, 28, 56, 12, 52, 44, 48,  4, 40, 24, 36,  8, 32, 20, 16,  0
};

/*
 * Apply native layer transformation to reconstruct the key.
 * The key (128 hex chars = 64 bytes) was encoded as:
 *   encoded[i] = original[SHUFFLE[i]] ^ (XOR_SEED + i)
 *
 * We invert this: for each output position i, find where it came from.
 */
JNIEXPORT jstring JNICALL
Java_com_cs702_aigenerator_NativeKeyStore_getNativeKey(
    JNIEnv *env,
    jobject obj,
    jstring reversed_key)
{
    const char *rev = (*env)->GetStringUTFChars(env, reversed_key, NULL);
    int len = (*env)->GetStringLength(env, reversed_key);

    if (len != 128) {
        (*env)->ReleaseStringUTFChars(env, reversed_key, rev);
        return (*env)->NewStringUTF(env, "");
    }

    /* Process as hex string → byte array → transform → hex string */
    unsigned char original_bytes[64];
    unsigned char encoded_bytes[64];

    /* Parse hex string to bytes (2 chars per byte) */
    for (int i = 0; i < 64; i++) {
        char h1 = rev[i * 2];
        char h2 = rev[i * 2 + 1];
        int val = 0;

        if (h1 >= '0' && h1 <= '9') val = (h1 - '0') * 16;
        else if (h1 >= 'a' && h1 <= 'f') val = (h1 - 'a' + 10) * 16;
        else {
            (*env)->ReleaseStringUTFChars(env, reversed_key, rev);
            return (*env)->NewStringUTF(env, "");
        }

        if (h2 >= '0' && h2 <= '9') val += (h2 - '0');
        else if (h2 >= 'a' && h2 <= 'f') val += (h2 - 'a' + 10);
        else {
            (*env)->ReleaseStringUTFChars(env, reversed_key, rev);
            return (*env)->NewStringUTF(env, "");
        }

        encoded_bytes[i] = (unsigned char)val;
    }

    /* Undo shuffle: OUTPUT[i] = INPUT[SHUFFLE[i]] */
    /* So INPUT[SHUFFLE[i]] = OUTPUT[i] → to get INPUT at i, find where i came from */
    for (int i = 0; i < 64; i++) {
        int src = -1;
        for (int s = 0; s < 64; s++) {
            if (SHUFFLE[s] == i) {
                src = s;
                break;
            }
        }
        if (src >= 0) {
            /* Undo XOR: original = encoded ^ (XOR_SEED + src_pos) */
            unsigned char xor_val = (XOR_SEED + src) & 0xFF;
            original_bytes[i] = encoded_bytes[src] ^ xor_val;
        } else {
            original_bytes[i] = encoded_bytes[i];
        }
    }

    /* Convert back to hex string */
    char *output = (char*)malloc(129);
    if (!output) {
        (*env)->ReleaseStringUTFChars(env, reversed_key, rev);
        return (*env)->NewStringUTF(env, "");
    }

    for (int i = 0; i < 64; i++) {
        unsigned char b = original_bytes[i];
        output[i * 2]     = (b >> 4) + ((b >> 4) < 10 ? '0' : 'a' - 10);
        output[i * 2 + 1] = (b & 0x0F) + ((b & 0x0F) < 10 ? '0' : 'a' - 10);
    }
    output[128] = '\0';

    (*env)->ReleaseStringUTFChars(env, reversed_key, rev);

    jstring result = (*env)->NewStringUTF(env, output);
    free(output);
    return result;
}

/*
 * Verify key format: 128 chars, lowercase hex, starts with 'c'.
 */
JNIEXPORT jint JNICALL
Java_com_cs702_aigenerator_NativeKeyStore_verifyNative(
    JNIEnv *env,
    jobject obj,
    jstring key)
{
    const char *key_str = (*env)->GetStringUTFChars(env, key, NULL);
    int len = (*env)->GetStringLength(env, key);

    if (len != 128) {
        (*env)->ReleaseStringUTFChars(env, key, key_str);
        return 0;
    }

    /* Must start with 'c' */
    if (key_str[0] != 'c') {
        (*env)->ReleaseStringUTFChars(env, key, key_str);
        return 0;
    }

    /* All chars must be lowercase hex */
    for (int i = 0; i < 128; i++) {
        char c = key_str[i];
        if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
            (*env)->ReleaseStringUTFChars(env, key, key_str);
            return 0;
        }
    }

    (*env)->ReleaseStringUTFChars(env, key, key_str);
    return 1;
}