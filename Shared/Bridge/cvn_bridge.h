/* ClassicVoices neural bridge: Jenny, Aria and Guy (en-US), Sonia and Ryan (en-GB), Neerja and Prabhat (en-IN) - the Windows 11 Narrator "natural"
 * voices - plus the Azure embedded packs Jane and Sara (en-US), on Microsoft's own embedded (offline) Speech SDK
 * engine (1.33 on Apple platforms, 1.50 on Android; the three shared libraries in NeuralSDK/, loaded at run time with
 * dlopen, nothing of the SDK linked at build time) - behind the same shape as cv_bridge.h / cvo_bridge.h, so the Apple
 * extension and the Android service drive every engine alike:
 *   - the same text sanitizer (cv_sanitize_alloc), then the text XML-escaped inside SSML <prosody rate pitch> (markup in
 *     the text is spoken, never obeyed);
 *   - rate on the same SAPI scale (-10..18, speed 3^(rate/10)) as the engine's prosody rate, which scales the voice's own
 *     RateAdjustment - the engine generates at that speed, nothing is time-stretched; its limits are 1/3x and 3x (SAPI
 *     rate 10: faster requests stay at 3x);
 *   - pitch in semitones as the engine's prosody pitch (it stops at about +-5 semitones);
 *   - streamed as the engine produces it (start_speaking_ssml + an audio data stream: first audio after the first
 *     chunk, not after the whole text), cancelled with stop_speaking;
 *   - 24 kHz resampled to 22050 Hz (cv_resample.h), then the same streaming silence trim (cv_trimmer).
 * The SDK is loaded once per process; each voice gets one engine configuration and synthesizer, built on its first
 * utterance and kept (the SDK does not give a configuration's memory back, so rebuilding would grow the process by ~3.4
 * MB every time). A voice handle is small. Every call is serialised under one process-wide lock, like the SAPI 4 bridge.
 * Nothing here logs or stores the text it is given, and the SDK is never given a log file; on Android the one exception
 * is the reason a voice failed to open or a synthesis was cancelled, which goes to logcat and nowhere else. */
#ifndef CVN_BRIDGE_H
#define CVN_BRIDGE_H

#include "cv_bridge.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef struct cvn_voice cvn_voice;

/* sdk_dir: folder with libMicrosoft.CognitiveServices.Speech.{core,extension.embedded.tts,extension.onnxruntime}.dylib
 *   (.so on Android)
 *   (the platform's build: iOS or macOS); loaded once per process, the first time any voice opens.
 * data_dir: NeuralVoices/ - one folder per voice (Jenny/, Aria/, Guy/, Sonia/, Ryan/, Neerja/, Prabhat/: the voice's
 *   models, INIs, Tokens.xml), en-GB/ and en-IN/ (those voices' language data) and model.key (the key the voice models are encrypted with).
 *   Azure embedded packs live here too, as en-US-JaneNeural/ and en-US-SaraNeural/ (see nv_def): unencrypted, so each
 *   carries its own SynthModel.license instead of using model.key, and its own copy of the en-US language data.
 * onecore_dir: OneCoreVoice/ - the en-US language data and the domain files both voice families share, byte for byte.
 * work_dir: a writable folder (the extension's Caches): per voice, a folder of links to those files, since the engine reads
 *   a voice from one folder (rebuilt the first time a voice opens in a process: an app update moves the bundle).
 * voice: "Jenny", "Aria", "Guy", "Sonia", "Ryan", "Neerja", "Prabhat", "Jane", "Sara". NULL and err filled on
 *   failure. */
cvn_voice *cvn_voice_open(const char *sdk_dir, const char *data_dir, const char *onecore_dir, const char *work_dir,
                          const char *voice, char *err, size_t errlen);

#if defined(__ANDROID__)
/* Android keeps everything the app ships in one place, so the two data folders are always
 * <data_root>/NeuralVoices and <data_root>/OneCoreVoice, and there is no error buffer to
 * fill: the JNI layer asks for the reason with cvn_last_error instead. The SDK is the same
 * engine as libMicrosoft.CognitiveServices.Speech.*.so, which the app carries in its native
 * library folder (the JNI layer passes that folder as sdk_dir). */
cvn_voice *cvn_voice_open_android(const char *sdk_dir, const char *data_root, const char *work_dir, const char *voice);
const char *cvn_last_error(void);
#endif
void cvn_voice_close(cvn_voice *v);
int cvn_voice_sample_rate(const cvn_voice *v); /* 22050 */

/* Same contract as cv_voice_speak: sapi_rate -10..18 (fractional allowed), semitones -12..12 (the engine's pitch control
 * stops at about +-5), trim. Returns 0 done, 1 stopped by the callback, -1 on error. Not reentrant per voice; voices on
 * other threads wait for the process-wide lock. */
int cvn_voice_speak(cvn_voice *v, const char *utf8, double sapi_rate, double semitones, int trim, cv_pcm_fn fn,
                    void *user);

/* The speed a SAPI rate really gets, relative to the voice's natural speed: 3^(rate/10), 1/3 .. 3 (rates above 10 = 3). */
double cvn_speed_factor(double sapi_rate);

/* Tests only: drop every voice's configuration (the next speak builds and loads it fresh; each rebuild costs the process
 * ~3.4 MB for good). The SDK stays loaded. */
void cvn_release_engine(void);

#ifdef __cplusplus
}
#endif
#endif
