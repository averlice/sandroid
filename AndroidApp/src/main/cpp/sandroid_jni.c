#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <limits.h>
#include <android/log.h>
#include "cv_bridge.h"
#include "cv4n_bridge.h"
#include "cvt_bridge.h"
#include "cva_bridge.h"
#include "cvo_bridge.h"
#include "cvn_bridge.h"

typedef struct {
    int engine;
    union { cv_voice *sapi5; cv4n_voice *sapi4; cvt_voice *truvoice; cva_voice *anna; cvo_voice *onecore; cvn_voice *neural; } v;
} VoiceHandle;

enum { E_SAPI5, E_SAPI4, E_TRUVOICE, E_ANNA, E_ONECORE, E_NEURAL };

static const char *utf(JNIEnv *env, jstring value) { return value ? (*env)->GetStringUTFChars(env, value, NULL) : ""; }
static void unutf(JNIEnv *env, jstring value, const char *chars) { if (value && chars) (*env)->ReleaseStringUTFChars(env, value, chars); }

JNIEXPORT jlong JNICALL Java_com_quinton_sandroid_NativeEngine_nativeOpen(
    JNIEnv *env, jobject self, jint engine, jstring rootValue, jstring dataValue, jstring effectValue,
    jstring emotionValue, jdouble pitch) {
    (void)self;
    const char *root = utf(env, rootValue), *data = utf(env, dataValue), *effect = utf(env, effectValue), *emotion = utf(env, emotionValue);
    VoiceHandle *h = calloc(1, sizeof(*h));
    char err[256] = {0};
    if (!h) goto done;
    h->engine = engine;
    switch (engine) {
        case E_SAPI5: h->v.sapi5 = cv_voice_open(root, data, effect, pitch, err, sizeof(err)); break;
        case E_SAPI4: h->v.sapi4 = cv4n_voice_open(root, data, err, sizeof(err)); break;
        case E_TRUVOICE: h->v.truvoice = cvt_voice_open(data, err, sizeof(err)); break;
        case E_ANNA: h->v.anna = cva_voice_open(root, err, sizeof(err)); break;
        case E_ONECORE:
            h->v.onecore = cvo_voice_open(root, data, err, sizeof(err));
            if (h->v.onecore && *emotion) cvo_voice_set_emotion(h->v.onecore, emotion);
            break;
        default: free(h); h = NULL; break;
    }
    if (h && !(h->v.sapi5 || h->v.sapi4 || h->v.truvoice || h->v.anna || h->v.onecore)) { free(h); h = NULL; }
done:
    unutf(env, rootValue, root); unutf(env, dataValue, data); unutf(env, effectValue, effect); unutf(env, emotionValue, emotion);
    if (!h) {
        const char *message = *err ? err : "Cannot allocate voice";
        __android_log_print(ANDROID_LOG_ERROR, "Sandroid", "Engine %d open failed: %s", engine, message);
        jclass cls = (*env)->FindClass(env, "java/lang/IllegalStateException");
        if (cls) (*env)->ThrowNew(env, cls, message);
    }
    return (jlong)(intptr_t)h;
}

JNIEXPORT jlong JNICALL Java_com_quinton_sandroid_NativeEngine_nativeOpenNeural(
    JNIEnv *env, jobject self, jstring sdkValue, jstring rootValue, jstring workValue, jstring voiceValue) {
    (void)self;
    const char *sdk = utf(env, sdkValue), *root = utf(env, rootValue);
    const char *work = utf(env, workValue), *voice = utf(env, voiceValue);
    char data[PATH_MAX], onecore[PATH_MAX], err[512] = {0};
    snprintf(data, sizeof data, "%s/NeuralVoices", root);
    snprintf(onecore, sizeof onecore, "%s/OneCoreVoice", root);
    VoiceHandle *h = calloc(1, sizeof(*h));
    if (h) {
        h->engine = E_NEURAL;
        h->v.neural = cvn_voice_open(sdk, data, onecore, work, voice, err, sizeof err);
        if (!h->v.neural) { free(h); h = NULL; }
    }
    unutf(env, sdkValue, sdk); unutf(env, rootValue, root);
    unutf(env, workValue, work); unutf(env, voiceValue, voice);
    if (!h) {
        jclass cls = (*env)->FindClass(env, "java/lang/IllegalStateException");
        if (cls) (*env)->ThrowNew(env, cls, *err ? err : "Cannot allocate natural voice");
    }
    return (jlong)(intptr_t)h;
}

static void close_voice(VoiceHandle *h) {
    if (!h) return;
    switch (h->engine) {
        case E_SAPI5: cv_voice_close(h->v.sapi5); break;
        case E_SAPI4: cv4n_voice_close(h->v.sapi4); break;
        case E_TRUVOICE: cvt_voice_close(h->v.truvoice); break;
        case E_ANNA: cva_voice_close(h->v.anna); break;
        case E_ONECORE: cvo_voice_close(h->v.onecore); break;
        case E_NEURAL: cvn_voice_close(h->v.neural); break;
    }
    free(h);
}

JNIEXPORT void JNICALL Java_com_quinton_sandroid_NativeEngine_nativeClose(JNIEnv *env, jobject self, jlong ptr) {
    (void)env; (void)self; close_voice((VoiceHandle *)(intptr_t)ptr);
}

typedef struct { JNIEnv *env; jobject sink; jmethodID method; } PcmContext;
static int deliver_pcm(const int16_t *pcm, size_t n, void *user) {
    PcmContext *ctx = user;
    jshortArray chunk = (*ctx->env)->NewShortArray(ctx->env, (jsize)n);
    if (!chunk) return 1;
    (*ctx->env)->SetShortArrayRegion(ctx->env, chunk, 0, (jsize)n, (const jshort *)pcm);
    jboolean keep = (*ctx->env)->CallBooleanMethod(ctx->env, ctx->sink, ctx->method, chunk);
    (*ctx->env)->DeleteLocalRef(ctx->env, chunk);
    return (*ctx->env)->ExceptionCheck(ctx->env) || keep != JNI_TRUE;
}

static int speak_sapi5(cv_voice *voice, const char *text, double rate, double pitch, int singing, PcmContext *ctx) {
    if (!singing) return cv_voice_speak(voice, text, rate, pitch, 1, deliver_pcm, ctx);
    cv_score_run runs[64];
    int count = cv_score_find(text, runs, 64);
    if (count <= 0) return cv_voice_speak(voice, text, rate, pitch, 1, deliver_pcm, ctx);
    size_t length = strlen(text), at = 0;
    int result = 0;
    cv_sing_settings cfg = {0, 0, 0};
    for (int i = 0; i <= count; ++i) {
        size_t start = i == count ? length : runs[i].start;
        size_t end = i == count ? length : start + runs[i].len;
        if (start > at) {
            char *piece = strndup(text + at, start - at);
            if (!piece) return -1;
            int rc = cv_voice_speak(voice, piece, rate, pitch, 1, deliver_pcm, ctx);
            free(piece);
            if (rc != 0) return rc;
        }
        if (i < count && end > start) {
            char *score = strndup(text + start, end - start);
            if (!score) return -1;
            int rc = cv_voice_sing(voice, score, rate, pitch, &cfg, 1, 1, deliver_pcm, ctx, NULL);
            free(score);
            if (rc != 0) return rc;
        }
        at = end;
    }
    return result;
}

JNIEXPORT jint JNICALL Java_com_quinton_sandroid_NativeEngine_nativeSpeak(
    JNIEnv *env, jobject self, jlong ptr, jstring textValue, jdouble rate, jdouble pitch,
    jboolean singing, jobject sink) {
    (void)self;
    VoiceHandle *h = (VoiceHandle *)(intptr_t)ptr;
    if (!h || !textValue || !sink) return -1;
    const char *text = utf(env, textValue);
    PcmContext ctx = { env, sink, (*env)->GetMethodID(env, (*env)->GetObjectClass(env, sink), "onNativePcm", "([S)Z") };
    int rc = -1;
    if (!ctx.method) goto done;
    switch (h->engine) {
        case E_SAPI5:
            rc = speak_sapi5(h->v.sapi5, text, rate, pitch, singing, &ctx);
            break;
        case E_SAPI4: rc = cv4n_voice_speak(h->v.sapi4, text, rate, pitch, 1, deliver_pcm, &ctx); break;
        case E_TRUVOICE: rc = cvt_voice_speak(h->v.truvoice, text, rate, pitch, 1, deliver_pcm, &ctx); break;
        case E_ANNA: rc = cva_voice_speak(h->v.anna, text, rate, pitch, 1, deliver_pcm, &ctx); break;
        case E_ONECORE: rc = cvo_voice_speak(h->v.onecore, text, rate, pitch, 1, deliver_pcm, &ctx); break;
        case E_NEURAL: rc = cvn_voice_speak(h->v.neural, text, rate, pitch, 1, deliver_pcm, &ctx); break;
    }
done:
    unutf(env, textValue, text);
    if (rc < 0 && h->engine == E_NEURAL && !(*env)->ExceptionCheck(env)) {
        jclass cls = (*env)->FindClass(env, "java/lang/IllegalStateException");
        const char *error = cvn_last_error();
        if (cls) (*env)->ThrowNew(env, cls, *error ? error : "Natural voice synthesis failed");
    }
    return rc;
}
