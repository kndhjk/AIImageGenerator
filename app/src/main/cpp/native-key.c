#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#define KEY_LENGTH 128
#define CHUNK_LENGTH 10
#define GROUP_COUNT 8
#define GROUP_SIZE 16
#define EXPECTED_FNV64 0x61074e8225321d6bULL

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
