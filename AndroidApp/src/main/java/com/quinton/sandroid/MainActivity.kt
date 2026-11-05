package com.quinton.sandroid

import android.app.Activity
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Sandroid's settings screen. Android opens it from the gear next to the Sandroid engine in
 * its text-to-speech settings, and it is also the launcher icon.
 *
 * Nothing changes until OK is pressed; Cancel leaves the engine exactly as it was.
 */
class MainActivity : Activity() {
    private val previewGeneration = AtomicInteger()
    private val previewLock = Any()
    private var currentOutput: PreviewAudioOutput? = null
    private lateinit var enginePicker: Spinner
    private lateinit var voicePicker: Spinner
    private lateinit var speed: SeekBar
    private lateinit var pitch: SeekBar
    private lateinit var volume: SeekBar
    private lateinit var singing: Switch
    private lateinit var speedLabel: TextView
    private lateinit var pitchLabel: TextView
    private lateinit var volumeLabel: TextView
    private lateinit var status: TextView
    private var voiceList: List<VoiceDef> = VoiceCatalog.voices
    private var availableVoices: List<VoiceDef> = emptyList()
    private val voiceSelections = mutableMapOf<Engine, String>()
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only voices whose data is bundled can speak; the rest would silently fall back.
        availableVoices = VoiceCatalog.voices.filter { VoiceAssets.isAvailable(this, it) }
            .ifEmpty { VoiceCatalog.voices }
        val savedVoice = availableVoices.firstOrNull { it.id == prefs.getString(KEY_VOICE, null) }
            ?: availableVoices.first()
        val engines = Engine.entries.map { it.settingsGroup }.distinct()
            .filter { group -> availableVoices.any { it.engine.settingsGroup == group } }
        voiceSelections[savedVoice.engine.settingsGroup] = savedVoice.id
        voiceList = availableVoices.filter { it.engine.settingsGroup == savedVoice.engine.settingsGroup }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }

        layout.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 26f
        })
        layout.addView(TextView(this).apply {
            text = getString(R.string.settings_subtitle)
            setPadding(0, 0, 0, dp(16))
        })

        layout.addView(sectionTitle(getString(R.string.label_engine)))
        // A dialog keeps every row within a scrollable window, even when reopening on
        // the last voice. Anchored dropdowns can be clipped around the selected row.
        enginePicker = Spinner(this, Spinner.MODE_DIALOG).apply {
            prompt = getString(R.string.label_engine)
            contentDescription = getString(R.string.label_engine)
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item,
                engines.map { engineLabel(it) }).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            setSelection(engines.indexOf(savedVoice.engine.settingsGroup))
        }
        layout.addView(enginePicker)

        layout.addView(sectionTitle(getString(R.string.label_voice)))
        voicePicker = Spinner(this, Spinner.MODE_DIALOG).apply {
            prompt = getString(R.string.label_voice)
            contentDescription = getString(R.string.label_voice)
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item,
                voiceList.map { it.name }).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            setSelection(voiceList.indexOfFirst { it.id == prefs.getString(KEY_VOICE, null) }.coerceAtLeast(0))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    stopPreview()
                    if (::status.isInitialized) {
                        status.text = voiceList.getOrNull(position)?.let { getString(R.string.preview_hint, it.name) } ?: ""
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        layout.addView(voicePicker)
        enginePicker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectEngine(engines[position])
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        speedLabel = sectionTitle("")
        layout.addView(speedLabel)
        speed = SeekBar(this).apply {
            max = SPEED_MAX - SPEED_MIN
            progress = prefs.getInt(KEY_SPEED, DEFAULT_SPEED) - SPEED_MIN
            setOnSeekBarChangeListener(onSliderChange { speedLabel.text = getString(R.string.label_speed, speedPercent()) })
        }
        layout.addView(speed)
        speedLabel.text = getString(R.string.label_speed, speedPercent())

        pitchLabel = sectionTitle("")
        layout.addView(pitchLabel)
        pitch = SeekBar(this).apply {
            max = PITCH_MAX - PITCH_MIN
            progress = prefs.getInt(KEY_PITCH, 0) - PITCH_MIN
            setOnSeekBarChangeListener(onSliderChange { pitchLabel.text = getString(R.string.label_pitch, pitchSemitones()) })
        }
        layout.addView(pitch)
        pitchLabel.text = getString(R.string.label_pitch, pitchSemitones())

        volumeLabel = sectionTitle("")
        layout.addView(volumeLabel)
        volume = SeekBar(this).apply {
            contentDescription = getString(R.string.volume_control)
            max = PcmVolume.MAX_PERCENT
            progress = prefs.getInt(PcmVolume.KEY, PcmVolume.DEFAULT_PERCENT).coerceIn(0, max)
            setOnSeekBarChangeListener(onSliderChange {
                volumeLabel.text = getString(R.string.label_volume, volume.progress)
            })
        }
        volumeLabel.labelFor = View.generateViewId().also { volume.id = it }
        layout.addView(volume)
        volumeLabel.text = getString(R.string.label_volume, volume.progress)
        layout.addView(TextView(this).apply { text = getString(R.string.volume_hint) })

        singing = Switch(this).apply {
            text = getString(R.string.label_singing)
            isChecked = prefs.getBoolean(KEY_SINGING, false)
        }
        layout.addView(singing)

        layout.addView(Button(this).apply {
            text = getString(R.string.action_preview)
            setOnClickListener { preview() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(16)
        })

        status = TextView(this).apply {
            setPadding(0, dp(8), 0, dp(8))
            text = getString(R.string.preview_hint, currentVoice().name)
        }
        layout.addView(status)

        layout.addView(Button(this).apply {
            text = getString(R.string.action_copy_diagnostics)
            setOnClickListener {
                val selectedVoice = currentVoice().name
                Thread {
                    val report = runCatching {
                        "Selected voice: $selectedVoice\n" + SpeechDiagnostics.collect(this@MainActivity)
                    }.getOrElse { "Diagnostics failed: ${it.message}" }
                    runOnUiThread {
                        val clipboard = getSystemService(android.content.ClipboardManager::class.java)
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Sandroid diagnostics", report))
                        status.text = getString(R.string.diagnostics_copied)
                    }
                }.start()
            }
        })

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(Button(this).apply {
            text = getString(R.string.action_cancel)
            setOnClickListener {
                stopPreview()
                finish()
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        buttons.addView(Button(this).apply {
            text = getString(R.string.action_ok)
            setOnClickListener { saveAndClose() }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        layout.addView(buttons)

        setContentView(ScrollView(this).apply {
            isFillViewport = true
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = if (Build.VERSION.SDK_INT >= 30) {
                    insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()).let {
                        Rect(it.left, it.top, it.right, it.bottom)
                    }
                } else {
                    @Suppress("DEPRECATION")
                    Rect(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                        insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                }
                view.setPadding(bars.left, bars.top,
                    bars.right, bars.bottom)
                insets
            }
            addView(layout, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT))
        })
    }

    override fun onPause() {
        stopPreview()
        super.onPause()
    }

    override fun onDestroy() {
        stopPreview()
        super.onDestroy()
    }

    private fun saveAndClose() {
        prefs.edit()
            .putString(KEY_VOICE, currentVoice().id)
            .putInt(KEY_SPEED, speed.progress + SPEED_MIN)
            .putInt(KEY_PITCH, pitch.progress + PITCH_MIN)
            .putInt(PcmVolume.KEY, volume.progress)
            .putBoolean(KEY_SINGING, singing.isChecked)
            .apply()
        stopPreview()
        setResult(RESULT_OK)
        finish()
    }

    private fun onSliderChange(update: () -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) = update()
        override fun onStartTrackingTouch(bar: SeekBar?) = Unit
        override fun onStopTrackingTouch(bar: SeekBar?) = Unit
    }

    private fun currentVoice(): VoiceDef =
        voiceList.getOrElse(voicePicker.selectedItemPosition) { voiceList.firstOrNull() ?: VoiceCatalog.voices.first() }

    private fun selectEngine(engine: Engine) {
        if (voiceList.firstOrNull()?.engine?.settingsGroup == engine) return
        stopPreview()
        currentVoice().let { voiceSelections[it.engine.settingsGroup] = it.id }
        voiceList = availableVoices.filter { it.engine.settingsGroup == engine }
        voicePicker.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
            voiceList.map { it.name }).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        voicePicker.setSelection(voiceList.indexOfFirst { it.id == voiceSelections[engine] }.coerceAtLeast(0))
        if (::status.isInitialized) status.text = getString(R.string.preview_hint, currentVoice().name)
    }

    private fun engineLabel(engine: Engine): String = getString(when (engine) {
        Engine.SAPI5 -> R.string.engine_sapi5
        Engine.SAPI4 -> R.string.engine_sapi4
        Engine.TRUVOICE -> R.string.engine_truvoice
        Engine.ANNA -> R.string.engine_sapi5
        Engine.ONECORE -> R.string.engine_onecore
        Engine.NEURAL -> R.string.engine_neural
    })

    /** The slider's progress is the SAPI rate (-10..18); 0 is the voice's own speed. */
    private fun speedPercent(): Int = (3.0.pow(speed.progress / 10.0) * 100).roundToInt()

    private fun pitchSemitones(): Int = pitch.progress + PITCH_MIN

    private fun preview() {
        stopPreview()
        val run = previewGeneration.get()
        val voice = currentVoice()
        val sapiRate = (speed.progress - 10).coerceIn(-10, 18).toDouble()
        val semitones = pitchSemitones().toDouble()
        val sing = singing.isChecked && voice.engine == Engine.SAPI5
        val volumePercent = volume.progress
        status.text = getString(R.string.preview_preparing)
        Thread {
            try {
                val root = VoiceAssets.install(this)
                SpeechDiagnostics.record(this, "Opening ${voice.name}")
                val handle = NativeEngine.open(this, root, voice)
                if (handle == 0L) throw IllegalStateException(getString(R.string.error_open, voice.name))
                try {
                    val output = PreviewAudioOutput()
                    try {
                        synchronized(previewLock) {
                            if (previewGeneration.get() == run) currentOutput = output else output.cancel()
                        }
                        SpeechDiagnostics.record(this, "Synthesizing ${voice.name}")
                        val rc = NativeEngine.speak(handle, getString(R.string.preview_text), sapiRate, semitones, sing,
                            volumePercent = volumePercent, sink =
                            object : PcmSink {
                                override fun onNativePcm(samples: ShortArray): Boolean {
                                    if (previewGeneration.get() != run) return false
                                    return output.write(samples) && previewGeneration.get() == run
                                }
                            })
                        SpeechDiagnostics.record(this, "${voice.name}: synthesis returned $rc")
                        if (rc == 0) output.finish()
                        runOnUiThread {
                            if (previewGeneration.get() == run) {
                                status.text = if (rc < 0) getString(R.string.error_speak)
                                else getString(R.string.preview_hint, voice.name)
                            }
                        }
                    } finally {
                        synchronized(previewLock) {
                            if (currentOutput === output) currentOutput = null
                        }
                        output.close()
                    }
                } finally { NativeEngine.close(handle) }
            } catch (e: Exception) {
                SpeechDiagnostics.record(this, "${voice.name}: ${e.message}")
                runOnUiThread { if (previewGeneration.get() == run) status.text = e.message ?: "" }
            }
        }.start()
    }

    private fun stopPreview() {
        synchronized(previewLock) {
            previewGeneration.incrementAndGet()
            currentOutput?.cancel()
            currentOutput = null
        }
    }

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text
        setPadding(0, dp(12), 0, dp(4))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private companion object {
        const val KEY_VOICE = "voice"
        const val KEY_SPEED = "speed"
        const val KEY_PITCH = "pitch"
        const val KEY_SINGING = "singing"
        const val SPEED_MIN = 10
        const val SPEED_MAX = 38
        const val DEFAULT_SPEED = 10
        const val PITCH_MIN = -12
        const val PITCH_MAX = 12
    }
}
