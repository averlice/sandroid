package com.quinton.sandroid

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** On-device smoke test: real packaged models, SDK, JNI and streamed PCM. No microphone or network. */
class NaturalVoiceInstrumentation : Instrumentation() {
    private var previewOnly = false
    private var settingsOnly = false
    private var scrollingOnly = false
    private var oneCoreOnly = false
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        previewOnly = arguments?.getString("previewOnly") == "true"
        settingsOnly = arguments?.getString("settingsOnly") == "true"
        scrollingOnly = arguments?.getString("scrollingOnly") == "true"
        oneCoreOnly = arguments?.getString("oneCoreOnly") == "true"
        start()
    }

    override fun onStart() {
        val results = Bundle()
        try {
            if (oneCoreOnly) {
                OneCoreChecks(this).run()
                results.putString("stream", "OneCore: all thirteen voices produced PCM; Eva cancellation and recovery passed.\n")
                finish(Activity.RESULT_OK, results)
                return
            }
            if (scrollingOnly) {
                ScrollingChecks(this).run()
                results.putString("stream", "Scrolling: every voice group scrolled from last to first and back; settings top and bottom reachable.\n")
                finish(Activity.RESULT_OK, results)
                return
            }
            if (settingsOnly) {
                SettingsChecks(this).run()
                results.putString("stream", "Settings: Anna grouping, selection restoration, volume save/cancel and PCM gain passed.\n")
                finish(Activity.RESULT_OK, results)
                return
            }
            if (previewOnly) {
                testPreviewPlayback()
                results.putString("stream", "Preview audio: full playback, short utterance and cancellation passed.\n")
                finish(Activity.RESULT_OK, results)
                return
            }
            val root = VoiceAssets.install(targetContext)
            val natural = VoiceCatalog.voices.filter { it.engine == Engine.NEURAL }
            check(natural.size == 7)
            natural.forEach { voice ->
                check(VoiceAssets.isAvailable(targetContext, voice.engine))
                val handle = NativeEngine.open(targetContext, root, voice)
                check(handle != 0L) { "${voice.name}: could not open" }
                try {
                    var samples = 0L
                    var nonzero = false
                    val rc = NativeEngine.speak(handle, "Hello. This is a natural voice speaking offline.", 0.0,
                        sink = object : PcmSink {
                            override fun onNativePcm(chunk: ShortArray): Boolean {
                                samples += chunk.size
                                nonzero = nonzero || chunk.any { it.toInt() != 0 }
                                return true
                            }
                        })
                    check(rc == 0 && samples > 2205 && nonzero) { "${voice.name}: rc=$rc, samples=$samples, nonzero=$nonzero" }
                    sendStatus(0, Bundle().apply { putString("stream", "${voice.name}: $samples PCM samples\n") })
                    // A canceled utterance must stop streaming and allow the next request.
                    val canceled = NativeEngine.speak(handle, "This sentence should be stopped immediately.", 0.0,
                        sink = object : PcmSink { override fun onNativePcm(samples: ShortArray) = false })
                    check(canceled == 1) { "${voice.name}: cancellation returned $canceled" }
                    var resumed = 0
                    val next = NativeEngine.speak(handle, "Ready again.", 2.0, 1.0,
                        sink = object : PcmSink {
                            override fun onNativePcm(samples: ShortArray): Boolean { resumed += samples.size; return true }
                        })
                    check(next == 0 && resumed > 0) { "${voice.name}: could not speak after cancellation" }
                } finally { NativeEngine.close(handle) }
            }
            testSystemService()
            results.putString("stream", "All seven natural voices produced PCM; cancellation, subsequent speech and Android TTS service passed.\n")
            finish(Activity.RESULT_OK, results)
        } catch (error: Throwable) {
            results.putString("stream", "FAILED: ${error.javaClass.simpleName}: ${error.message}\n")
            finish(Activity.RESULT_CANCELED, results)
        }
    }

    private fun testPreviewPlayback() {
        // Shorter than AudioTrack's startup threshold, then larger than its buffer.
        // Verify actual device playback, not merely that write() accepted the samples.
        for (count in listOf(512, 22050)) {
            PreviewAudioOutput().use { output ->
                check(output.write(ShortArray(count) { if (it % 40 < 20) 1000 else -1000 }))
                check(output.framesWritten == count.toLong()) { "Partial write lost PCM" }
                check(output.finish())
                check(output.framesPlayed >= output.framesWritten) { "Track would release with unplayed audio" }
                sendStatus(0, Bundle().apply {
                    putString("stream", "Preview: $count speech frames; all ${output.framesWritten} frames played before release\n")
                })
            }
        }
        val output = PreviewAudioOutput()
        try {
            check(output.write(ShortArray(512)))
            val started = CountDownLatch(1)
            var drained = true
            var failure: Throwable? = null
            val worker = Thread {
                started.countDown()
                try { drained = output.finish() } catch (error: Throwable) { failure = error }
            }
            worker.start()
            check(started.await(1, TimeUnit.SECONDS))
            output.cancel()
            worker.join(1000)
            check(!worker.isAlive && failure == null && !drained) { "Cancel did not interrupt preview drain" }
        } finally { output.close() }
        // Replacing a canceled preview must leave the new track usable.
        PreviewAudioOutput().use { output ->
            check(output.write(ShortArray(512)))
            check(output.finish())
        }
    }

    private fun testSystemService() {
        val initialized = CountDownLatch(1)
        var initCode = TextToSpeech.ERROR
        val tts = TextToSpeech(targetContext, { code -> initCode = code; initialized.countDown() },
            "com.quinton.sandroid")
        val wav = File(targetContext.cacheDir, "natural-service-test.wav")
        try {
            check(initialized.await(30, TimeUnit.SECONDS) && initCode == TextToSpeech.SUCCESS) { "TTS service initialization failed" }
            val voices = tts.voices.filter { it.name.endsWith("(Natural)") }
            check(voices.size == 7 && voices.none { it.isNetworkConnectionRequired }) { "Natural voices not registered as offline" }
            check(tts.setVoice(voices.first { it.name == "Jenny (Natural)" }) == TextToSpeech.SUCCESS)
            val done = CountDownLatch(1)
            var failed = false
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit
                override fun onDone(id: String?) { done.countDown() }
                override fun onError(id: String?) { failed = true; done.countDown() }
            })
            check(tts.synthesizeToFile("Hello from Android's text to speech service.", Bundle(), wav, "natural-smoke") == TextToSpeech.SUCCESS)
            check(done.await(30, TimeUnit.SECONDS) && !failed && wav.length() > 44) { "TTS service did not produce a WAV" }
            check(wav.inputStream().use { String(it.readNBytes(4), Charsets.US_ASCII) } == "RIFF")
            sendStatus(0, Bundle().apply { putString("stream", "Android TTS service: seven offline voices registered; synthesized WAV (${wav.length()} bytes)\n") })
        } finally {
            tts.shutdown()
            wav.delete()
        }
    }
}
