package com.quinton.sandroid

import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.speech.tts.Voice
import android.media.AudioFormat
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.log2
import kotlin.math.ln

class SandroidTtsService : TextToSpeechService() {
    private val generation = AtomicInteger()
    @Volatile private var dataRoot: File? = null

    /** Only the voices whose data was staged into the build; the rest would open and fail. */
    private val voices: List<VoiceDef> by lazy {
        VoiceCatalog.voices.filter { VoiceAssets.isAvailable(this, it) }
    }

    override fun onGetLanguage(): Array<String> = arrayOf("eng", "USA", "")

    override fun onIsLanguageAvailable(language: String, country: String, variant: String): Int =
        VoiceCatalog.languageStatus(language, country, voices)

    override fun onLoadLanguage(language: String, country: String, variant: String): Int =
        onIsLanguageAvailable(language, country, variant)

    override fun onGetVoices(): List<Voice> = voices.map(VoiceDef::androidVoice)

    /** The voice picked in the Sandroid app (its settings screen), applied engine-wide. */
    private fun preferredVoice(): VoiceDef? {
        val id = getSharedPreferences("settings", MODE_PRIVATE).getString("voice", null) ?: return null
        val voice = VoiceCatalog.byNameOrLocale(id) ?: return null
        return if (voice in voices) voice else null
    }

    override fun onGetDefaultVoiceNameFor(language: String, country: String, variant: String): String? {
        val preferred = preferredVoice()
        if (preferred != null &&
            VoiceCatalog.languageStatus(language, country, listOf(preferred)) != TextToSpeech.LANG_NOT_SUPPORTED
        ) return preferred.name
        return VoiceCatalog.defaultVoiceFor(language, country, voices)?.name
    }

    /** Voice names are display names, not locale tags, so the default check must be replaced. */
    override fun onIsValidVoiceName(voiceName: String): Int =
        if (VoiceCatalog.byNameOrLocale(voiceName) in voices) TextToSpeech.SUCCESS else TextToSpeech.ERROR

    override fun onLoadVoice(voiceName: String): Int =
        if (VoiceCatalog.byNameOrLocale(voiceName) in voices) TextToSpeech.SUCCESS else TextToSpeech.ERROR

    override fun onStop() { generation.incrementAndGet() }

    override fun onSynthesizeText(request: SynthesisRequest, callback: SynthesisCallback) {
        var callbackFinished = false
        fun fail(notInstalled: Boolean = false) {
            callback.error(if (notInstalled) TextToSpeech.ERROR_NOT_INSTALLED_YET else TextToSpeech.ERROR_SYNTHESIS)
            callback.done()
            callbackFinished = true
        }
        if (callback.start(22050, AudioFormat.ENCODING_PCM_16BIT, 1) != TextToSpeech.SUCCESS) {
            fail()
            return
        }
        // The app is the engine's voice picker, so its choice wins over whatever voice a
        // client cached when it bound; clients otherwise keep speaking the old voice.
        val requested = preferredVoice()?.name ?: request.voiceName?.takeIf { it.isNotBlank() }
        val selected = VoiceCatalog.byId(requested, voices)
        val root = try { dataRoot ?: VoiceAssets.install(this).also { dataRoot = it } }
        catch (_: Exception) { fail(); return }
        val run = generation.get()
        SpeechDiagnostics.record(this, "TTS opening ${selected.name}")
        val handle = try { NativeEngine.open(this, root, selected) }
        catch (_: UnsatisfiedLinkError) { 0L }
        catch (_: Exception) { 0L }
        if (handle == 0L) {
            fail(notInstalled = true)
            return
        }
        try {
            val sink = object : PcmSink {
                override fun onNativePcm(samples: ShortArray): Boolean {
                    if (generation.get() != run) return false
                    val bytes = ByteArray(samples.size * 2)
                    samples.forEachIndexed { i, sample ->
                        bytes[i * 2] = sample.toByte()
                        bytes[i * 2 + 1] = (sample.toInt() ushr 8).toByte()
                    }
                    var offset = 0
                    val maxBytes = callback.maxBufferSize.coerceAtLeast(2).let { it - (it % 2) }
                    while (offset < bytes.size && generation.get() == run) {
                        val count = minOf(maxBytes, bytes.size - offset)
                        if (callback.audioAvailable(bytes, offset, count) != TextToSpeech.SUCCESS) return false
                        offset += count
                    }
                    return generation.get() == run
                }
            }
            val rate = request.speechRate.coerceIn(10, 400) / 100.0
            val sapiRate = (10.0 * ln(rate) / ln(3.0)).coerceIn(-10.0, 18.0)
            val prefPitch = getSharedPreferences("settings", MODE_PRIVATE).getInt("pitch", 0).toDouble()
            val pitch = (12.0 * log2(request.pitch.coerceIn(10, 400) / 100.0) + prefPitch).coerceIn(-12.0, 12.0)
            val singing = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("singing", false)
            val volume = getSharedPreferences("settings", MODE_PRIVATE)
                .getInt(PcmVolume.KEY, PcmVolume.DEFAULT_PERCENT)
            SpeechDiagnostics.record(this, "TTS synthesizing ${selected.name}")
            val rc = NativeEngine.speak(handle, request.charSequenceText.toString(), sapiRate, pitch, singing,
                volumePercent = volume, sink = sink)
            SpeechDiagnostics.record(this, "TTS ${selected.name}: synthesis returned $rc")
            if (generation.get() != run || rc == 1) {
                callback.done()
                callbackFinished = true
            } else if (rc < 0) fail()
            else {
                callback.done()
                callbackFinished = true
            }
        } catch (_: Exception) {
            if (!callbackFinished) fail()
        } finally {
            NativeEngine.close(handle)
        }
    }
}
