#include "cvt_bridge.h"
#include <stdio.h>

struct cvt_voice { int unused; };

cvt_voice *cvt_voice_open(const char *mode, char *err, size_t errlen) {
    (void)mode;
    if (err && errlen) snprintf(err, errlen, "TruVoice tables are not staged. Run make truvoice-data.");
    return 0;
}
void cvt_voice_close(cvt_voice *voice) { (void)voice; }
int cvt_voice_sample_rate(const cvt_voice *voice) { (void)voice; return 22050; }
int cvt_voice_speak(cvt_voice *voice, const char *text, double rate, double pitch, int trim, cv_pcm_fn fn, void *user) {
    (void)voice; (void)text; (void)rate; (void)pitch; (void)trim; (void)fn; (void)user; return -1;
}
unsigned cvt_voice_wpm_for_rate(const cvt_voice *voice, double rate) { (void)voice; (void)rate; return 150; }
unsigned cvt_engine_wpm(unsigned wpm) { return wpm; }
unsigned cvt_voice_pitch_for_semitones(const cvt_voice *voice, double semitones) { (void)voice; (void)semitones; return 100; }
