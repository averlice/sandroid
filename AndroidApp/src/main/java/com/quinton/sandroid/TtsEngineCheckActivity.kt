package com.quinton.sandroid

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Answers the platform probes that every TTS engine has to implement.
 *
 * Android's text-to-speech settings build their language list solely from the
 * [TextToSpeech.Engine.ACTION_CHECK_TTS_DATA] reply, and read the demo sentence for the
 * Play button from [TextToSpeech.Engine.ACTION_GET_SAMPLE_TEXT]. Without this activity
 * the engine can be selected but shows no voices at all.
 */
class TtsEngineCheckActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent?.action) {
            TextToSpeech.Engine.ACTION_GET_SAMPLE_TEXT -> {
                val sample = Intent().putExtra("sampleText", "Hello from Sandroid on Android.")
                setResult(TextToSpeech.LANG_AVAILABLE, sample)
            }
            else -> {
                val available = ArrayList<String>()
                VoiceCatalog.voices
                    .filter { VoiceAssets.isAvailable(this, it) }
                    .map(VoiceDef::locale).distinct()
                    .forEach { locale -> iso3Tag(locale)?.let(available::add) }
                setResult(
                    TextToSpeech.Engine.CHECK_VOICE_DATA_PASS,
                    Intent()
                        .putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES, available)
                        .putStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES, ArrayList())
                )
            }
        }
        finish()
    }

    /** The platform expects ISO-3 language plus ISO-3 country, e.g. "eng-usa". */
    private fun iso3Tag(locale: Locale): String? = runCatching {
        val country = locale.getISO3Country()
        if (country.isEmpty()) locale.getISO3Language().lowercase()
        else "${locale.getISO3Language()}-$country".lowercase()
    }.getOrNull()
}
