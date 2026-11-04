package com.quinton.sandroid

import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.SeekBar
import android.widget.Spinner
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal class SettingsChecks(private val instrumentation: Instrumentation) {
    private val context = instrumentation.targetContext

    fun run() {
        val original = shortArrayOf(-32768, -20000, -1000, 0, 1000, 20000, 32767)
        fun checkGain(percent: Int, expected: ShortArray) {
            val pcm = original.copyOf()
            PcmVolume.apply(pcm, percent)
            check(pcm.contentEquals(expected)) { "Incorrect PCM at $percent%: ${pcm.toList()}" }
        }
        checkGain(100, original)
        checkGain(0, ShortArray(original.size))
        checkGain(50, shortArrayOf(-16384, -10000, -500, 0, 500, 10000, 16383))
        val boosted = shortArrayOf(-32768, -32768, -2000, 0, 2000, 32767, 32767)
        checkGain(200, boosted)
        checkGain(Int.MAX_VALUE, boosted)
        checkGain(Int.MIN_VALUE, ShortArray(original.size))
        val anna = VoiceCatalog.voices.single { it.id == "anna" }
        check(anna.engine == Engine.ANNA && anna.engine.settingsGroup == Engine.SAPI5)

        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val saved = prefs.all
        var activity: MainActivity? = null
        try {
            check(VoiceAssets.isAvailable(context, Engine.ANNA)) { "Stage Anna data for the settings test" }
            check(prefs.edit().clear().putString("voice", "anna").commit())
            fun launch(): MainActivity {
                val opened = instrumentation.startActivitySync(
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
                activity = opened
                instrumentation.waitForIdleSync()
                return opened
            }
            fun views(root: View): List<View> = listOf(root) +
                if (root is ViewGroup) (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()
            fun controls(screen: MainActivity) = views(screen.window.decorView)
            fun button(screen: MainActivity, label: Int) = controls(screen).filterIsInstance<Button>()
                .single { it.text.toString() == context.getString(label) }
            fun volume(screen: MainActivity) = controls(screen).filterIsInstance<SeekBar>()
                .single { it.contentDescription == context.getString(R.string.volume_control) }
            var screen = launch()
            instrumentation.runOnMainSync {
                val pickers = controls(screen).filterIsInstance<Spinner>()
                val engine = pickers.single { it.contentDescription == context.getString(R.string.label_engine) }
                val voice = pickers.single { it.contentDescription == context.getString(R.string.label_voice) }
                check(engine.selectedItem == "SAPI 5" && voice.selectedItem == "Anna")
                check((0 until engine.count).none { engine.getItemAtPosition(it) == "Microsoft Anna" })
                check(volume(screen).progress == 100)
                // Moving to another group and back must restore Anna, not Sam.
                engine.setSelection((0 until engine.count).first { engine.getItemAtPosition(it) != "SAPI 5" })
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val engine = controls(screen).filterIsInstance<Spinner>()
                    .single { it.contentDescription == context.getString(R.string.label_engine) }
                engine.setSelection((0 until engine.count).first { engine.getItemAtPosition(it) == "SAPI 5" })
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                check(controls(screen).filterIsInstance<Spinner>()
                    .single { it.contentDescription == context.getString(R.string.label_voice) }.selectedItem == "Anna")
                volume(screen).progress = 175
                button(screen, R.string.action_ok).performClick()
            }
            instrumentation.waitForIdleSync()
            check(prefs.getString("voice", null) == "anna" && prefs.getInt(PcmVolume.KEY, -1) == 175)
            screen = launch()
            instrumentation.runOnMainSync {
                check(volume(screen).progress == 175)
                volume(screen).progress = 0
                button(screen, R.string.action_cancel).performClick()
            }
            instrumentation.waitForIdleSync()
            check(prefs.getInt(PcmVolume.KEY, -1) == 175) { "Cancel saved volume" }
            checkServiceVolume()
        } finally {
            instrumentation.runOnMainSync { activity?.finish() }
            val editor = prefs.edit().clear()
            saved.forEach { (key, value) ->
                when (value) {
                    is String -> editor.putString(key, value)
                    is Int -> editor.putInt(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
            check(editor.commit())
        }
    }

    private fun checkServiceVolume() {
        val initialized = CountDownLatch(1)
        var initCode = TextToSpeech.ERROR
        val tts = TextToSpeech(context, { initCode = it; initialized.countDown() }, "com.quinton.sandroid")
        val wav = File(context.cacheDir, "volume-service-test.wav")
        try {
            check(initialized.await(30, TimeUnit.SECONDS) && initCode == TextToSpeech.SUCCESS)
            for (percent in listOf(0, 200)) {
                check(context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
                    .putInt(PcmVolume.KEY, percent).commit())
                val done = CountDownLatch(1)
                var failed = false
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) = Unit
                    override fun onDone(id: String?) { done.countDown() }
                    override fun onError(id: String?) { failed = true; done.countDown() }
                })
                check(tts.synthesizeToFile("Hello from Anna.", Bundle(), wav, "volume-$percent") == TextToSpeech.SUCCESS)
                check(done.await(30, TimeUnit.SECONDS) && !failed)
                val bytes = wav.readBytes()
                // Android's synthesizeToFile writes a 44-byte PCM WAV header.
                check(bytes.size > 44 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF")
                val audible = bytes.drop(44).any { it != 0.toByte() }
                check(audible == (percent > 0)) { "System speech did not apply saved volume $percent" }
            }
        } finally {
            tts.shutdown()
            wav.delete()
        }
    }
}
