#include <jni.h>
#include <dirent.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#define KEY_LENGTH 128
#define CHUNK_LENGTH 10
#define GROUP_COUNT 8
#define GROUP_SIZE 16
#define EXPECTED_FNV64 0x61074e8225321d6bULL
#define PACKAGE_LENGTH 21
#define BASE_URL_LENGTH 28
#define META_XOR 0x20

static const unsigned char EXPECTED_PACKAGE_OBF[PACKAGE_LENGTH] = {
    0x43, 0x4f, 0x4d, 0x0e, 0x43, 0x53, 0x17, 0x10, 0x12, 0x0e, 0x41,
    0x49, 0x47, 0x45, 0x4e, 0x45, 0x52, 0x41, 0x54, 0x4f, 0x52
};

static const unsigned char BASE_URL_OBF[BASE_URL_LENGTH] = {
    0x48, 0x54, 0x54, 0x50, 0x53, 0x1a, 0x0f, 0x0f, 0x41, 0x49, 0x0e,
    0x45, 0x4c, 0x4C, 0x49, 0x4F, 0x54, 0x54, 0x57, 0x45, 0x4E, 0x0e,
    0x49, 0x4E, 0x46, 0x4F, 0x0F, 0x0F
};

static const char *GROUP_0[16] = {
    "Qdikadadia",
    "Wkakakakaf",
    "Ekadikatuj",
    "Rdaditukas",
    "Tkakakakag",
    "Ykadidatuk",
    "Udatutukad",
    "Ididatutuh",
    "Qtukadikaa",
    "Wtudadituf",
    "Etudidadaj",
    "Rtututukas",
    "Ttukaditug",
    "Ytutukadak",
    "Udadakadad",
    "Idadidadih",
};

static const char *GROUP_1[16] = {
    "Qditutudia",
    "Wtukadadaf",
    "Edatudadaj",
    "Rkatudatus",
    "Tkakakadag",
    "Ykakakakak",
    "Udadikadid",
    "Ikakadadah",
    "Qdadadatua",
    "Wdiditudaf",
    "Edikatudij",
    "Rtudididas",
    "Ttudatukag",
    "Ydiditudik",
    "Uditudikad",
    "Itukadituh",
};

static const char *GROUP_2[16] = {
    "Qdadakatua",
    "Wditudadaf",
    "Ekatudatuj",
    "Rtuditudas",
    "Ttudadidig",
    "Ykatudituk",
    "Ukadididad",
    "Ituditudih",
    "Qdatutudia",
    "Wdadidikaf",
    "Edakatutuj",
    "Rdatukadas",
    "Ttutukatug",
    "Ytudadadak",
    "Uditukadad",
    "Idatukadih",
};

static const char *GROUP_3[16] = {
    "Qdatutudia",
    "Wdakatukaf",
    "Editudituj",
    "Rditudadas",
    "Ttudidatug",
    "Ykadadakak",
    "Ututudatud",
    "Itutukatuh",
    "Qkakadakaa",
    "Wdadadadif",
    "Ekakadidaj",
    "Rkakatudis",
    "Ttututukag",
    "Ydidadidik",
    "Udituditud",
    "Itutudikah",
};

static const char *GROUP_4[16] = {
    "Qkadidadaa",
    "Wdatudatuf",
    "Ekadadatuj",
    "Rkadidadas",
    "Tdidakatug",
    "Yditutudak",
    "Udidadidid",
    "Ididatukah",
    "Qkadiditua",
    "Wtukadituf",
    "Edidadikaj",
    "Rtukatutus",
    "Ttutukadag",
    "Ykadidadik",
    "Ukakadakad",
    "Ikakadatuh",
};

static const char *GROUP_5[16] = {
    "Qdikadadia",
    "Wkadididif",
    "Edadidadaj",
    "Rdidakadis",
    "Tdikakakag",
    "Ydidakatuk",
    "Uditutudad",
    "Idadidituh",
    "Qdidadatua",
    "Wdakadikaf",
    "Ekatudidij",
    "Rtudadikas",
    "Tkadaditug",
    "Ydikadakak",
    "Ututudadid",
    "Ikatututuh",
};

static const char *GROUP_6[16] = {
    "Qtudidadaa",
    "Wdidadatuf",
    "Edidikadaj",
    "Rditudadis",
    "Tdadidatug",
    "Ydatutudak",
    "Ukakadadad",
    "Iditukatuh",
    "Qkadadikaa",
    "Wdadididif",
    "Edididikaj",
    "Rdiditutus",
    "Tdikadadag",
    "Ytutudidak",
    "Udatututud",
    "Iditudadah",
};

static const char *GROUP_7[16] = {
    "Qkatukadaa",
    "Wdakadatuf",
    "Ekakadakaj",
    "Rdatukakas",
    "Tdadakatug",
    "Ydakadadak",
    "Ukakadatud",
    "Ikatutudah",
    "Qtukadidia",
    "Wtukatukaf",
    "Etukatudaj",
    "Rdidatutus",
    "Tkatuditug",
    "Ydakadikak",
    "Udidatutud",
    "Ikakakakah",
};

static const char **ALL_GROUPS[GROUP_COUNT] = {
    GROUP_0, GROUP_1, GROUP_2, GROUP_3, GROUP_4, GROUP_5, GROUP_6, GROUP_7
};

static const unsigned char GROUP_ORDER[GROUP_COUNT] = {5, 1, 7, 0, 6, 3, 4, 2};

static void decode_meta(const unsigned char *src, size_t len, char *dst) {
    for (size_t i = 0; i < len; i++) {
        dst[i] = (char) (src[i] ^ META_XOR);
    }
    dst[len] = '\0';
}

static inline unsigned char ror8(unsigned char value, unsigned char shift) {
    return (unsigned char)((value >> shift) | (value << (8 - shift)));
}

static unsigned char rolling_mask(int index) {
    return (unsigned char)((0x39 + index * 17 + (index % 7) * 13) & 0xFF);
}

static void secure_zero(void *ptr, size_t len) {
    volatile unsigned char *p = (volatile unsigned char *) ptr;
    while (len--) {
        *p++ = 0;
    }
}

static int token_value(const char *token) {
    if (token[0] == 'd' && token[1] == 'i') return 0;
    if (token[0] == 'd' && token[1] == 'a') return 1;
    if (token[0] == 't' && token[1] == 'u') return 2;
    if (token[0] == 'k' && token[1] == 'a') return 3;
    return -1;
}

static int decode_chunk(const char *chunk, int original_index, char *out_char) {
    if (chunk == NULL || out_char == NULL) return 0;
    if ((int) strlen(chunk) != CHUNK_LENGTH) return 0;

    unsigned char value = 0;
    for (int offset = 1; offset < CHUNK_LENGTH - 1; offset += 2) {
        int code = token_value(&chunk[offset]);
        if (code < 0) return 0;
        value = (unsigned char) ((value << 2) | (unsigned char) code);
    }

    unsigned char plain = (unsigned char) (ror8(value, 3) ^ rolling_mask(original_index));
    *out_char = (char) plain;
    return 1;
}

static uint64_t fnv1a64(const char *value) {
    uint64_t hash = 0xcbf29ce484222325ULL;
    for (int i = 0; i < KEY_LENGTH; i++) {
        hash ^= (unsigned char) value[i];
        hash *= 0x100000001b3ULL;
    }
    return hash;
}

static int contains_keyword(const char *haystack) {
    static const char *keywords[] = {
        "frida", "gum-js", "gadget", "linjector", "xposed", "substrate", "zygisk", "magisk"
    };
    if (haystack == NULL) return 0;
    for (size_t i = 0; i < sizeof(keywords) / sizeof(keywords[0]); i++) {
        if (strstr(haystack, keywords[i]) != NULL) return 1;
    }
    return 0;
}

static int suspicious_maps(void) {
    FILE *fp = fopen("/proc/self/maps", "r");
    if (fp == NULL) return 0;

    char line[512];
    int suspicious = 0;
    while (fgets(line, sizeof(line), fp) != NULL) {
        if (contains_keyword(line)) {
            suspicious = 1;
            break;
        }
    }
    fclose(fp);
    return suspicious;
}

static int suspicious_tracer(void) {
    FILE *fp = fopen("/proc/self/status", "r");
    if (fp == NULL) return 0;

    char line[256];
    int suspicious = 0;
    while (fgets(line, sizeof(line), fp) != NULL) {
        if (strncmp(line, "TracerPid:", 10) == 0) {
            char *value = line + 10;
            while (*value == ' ' || *value == '\t') value++;
            suspicious = (*value != '0');
            break;
        }
    }
    fclose(fp);
    return suspicious;
}

static int suspicious_ports(void) {
    FILE *fp = fopen("/proc/net/tcp", "r");
    if (fp == NULL) return 0;

    char line[512];
    int suspicious = 0;
    while (fgets(line, sizeof(line), fp) != NULL) {
        if (strstr(line, ":69A2") || strstr(line, ":69A3") || strstr(line, ":5D8A") ||
            strstr(line, ":5D8B") || strstr(line, ":5D8C") || strstr(line, ":5D8D")) {
            suspicious = 1;
            break;
        }
    }
    fclose(fp);
    return suspicious;
}

static int suspicious_threads(void) {
    DIR *dir = opendir("/proc/self/task");
    if (dir == NULL) return 0;

    struct dirent *entry;
    int suspicious = 0;
    while ((entry = readdir(dir)) != NULL) {
        if (entry->d_name[0] == '.') continue;

        char path[256];
        snprintf(path, sizeof(path), "/proc/self/task/%s/comm", entry->d_name);
        FILE *fp = fopen(path, "r");
        if (fp == NULL) continue;

        char name[128];
        if (fgets(name, sizeof(name), fp) != NULL && contains_keyword(name)) {
            suspicious = 1;
            fclose(fp);
            break;
        }
        fclose(fp);
    }

    closedir(dir);
    return suspicious;
}

static int runtime_safe_impl(void) {
    if (getenv("LD_PRELOAD") != NULL) return 0;
    if (getenv("FRIDA_VERSION") != NULL) return 0;
    if (getenv("FRIDA_PORT") != NULL) return 0;
    if (suspicious_tracer()) return 0;
    if (suspicious_maps()) return 0;
    if (suspicious_ports()) return 0;
    if (suspicious_threads()) return 0;
    return 1;
}

static int validate_key_impl(const char *key) {
    if (key == NULL) return 0;
    if ((int) strlen(key) != KEY_LENGTH) return 0;

    for (int i = 0; i < KEY_LENGTH; i++) {
        char c = key[i];
        if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
            return 0;
        }
    }

    if (key[0] != '4' || key[KEY_LENGTH - 1] != '8') return 0;
    if (!runtime_safe_impl()) return 0;
    return fnv1a64(key) == EXPECTED_FNV64 ? 1 : 0;
}

JNIEXPORT jstring JNICALL
Java_com_cs702_aigenerator_NativeKeyStore_buildNativeKey(JNIEnv *env, jclass clazz) {
    (void) clazz;
    char key[KEY_LENGTH + 1];
    memset(key, 0, sizeof(key));

    for (int group_index = 0; group_index < GROUP_COUNT; group_index++) {
        int original_group = GROUP_ORDER[group_index];
        for (int item_index = 0; item_index < GROUP_SIZE; item_index++) {
            int original_index = original_group * GROUP_SIZE + item_index;
            if (!decode_chunk(ALL_GROUPS[group_index][item_index], original_index, &key[original_index])) {
                secure_zero(key, sizeof(key));
                return (*env)->NewStringUTF(env, "");
            }
        }
    }

    key[KEY_LENGTH] = '\0';
    if (!validate_key_impl(key)) {
        secure_zero(key, sizeof(key));
        return (*env)->NewStringUTF(env, "");
    }

    jstring result = (*env)->NewStringUTF(env, key);
    secure_zero(key, sizeof(key));
    return result;
}

JNIEXPORT jint JNICALL
Java_com_cs702_aigenerator_NativeKeyStore_verifyNative(JNIEnv *env, jclass clazz, jstring key) {
    (void) clazz;
    if (key == NULL) return 0;

    const char *key_str = (*env)->GetStringUTFChars(env, key, NULL);
    if (key_str == NULL) return 0;

    int valid = validate_key_impl(key_str);
    (*env)->ReleaseStringUTFChars(env, key, key_str);
    return valid;
}

JNIEXPORT jint JNICALL
Java_com_cs702_aigenerator_NativeKeyStore_nativeRuntimeSafe(JNIEnv *env, jclass clazz) {
    (void) env;
    (void) clazz;
    return runtime_safe_impl() ? 1 : 0;
}

JNIEXPORT jstring JNICALL
Java_com_cs702_aigenerator_NativeKeyStore_nativeExpectedPackage(JNIEnv *env, jclass clazz) {
    (void) clazz;
    char value[PACKAGE_LENGTH + 1];
    memset(value, 0, sizeof(value));
    decode_meta(EXPECTED_PACKAGE_OBF, PACKAGE_LENGTH, value);
    jstring result = (*env)->NewStringUTF(env, value);
    secure_zero(value, sizeof(value));
    return result;
}

JNIEXPORT jstring JNICALL
Java_com_cs702_aigenerator_NativeKeyStore_nativeBaseUrl(JNIEnv *env, jclass clazz) {
    (void) clazz;
    char value[BASE_URL_LENGTH + 1];
    memset(value, 0, sizeof(value));
    decode_meta(BASE_URL_OBF, BASE_URL_LENGTH, value);
    jstring result = (*env)->NewStringUTF(env, value);
    secure_zero(value, sizeof(value));
    return result;
}
