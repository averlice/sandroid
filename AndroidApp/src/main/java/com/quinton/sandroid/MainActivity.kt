package com.quinton.sandroid

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.SharedPreferences
import android.graphics.Insets
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
 * The engine's settings screen, opened from Android's own text-to-speech settings and from
 * the launcher. Everything is applied with OK; Cancel leaves the saved settings untouched.
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

    /** Created before the pickers because their listeners report into it, added further down. */
    private lateinit var status: TextView

    private var voiceList: List<VoiceDef> = VoiceCatalog.voices
    private var availableVoices: List<VoiceDef> = emptyList()
    private val voiceSelections = LinkedHashMap<Engine, String>()

    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        availableVoices = VoiceCatalog.voices.filter { VoiceAssets.isAvailable(this, it.engine) }
            .ifEmpty { VoiceCatalog.voices }
        val savedVoice = availableVoices.firstOrNull { it.id == prefs.getString(KEY_VOICE, null) }
            ?: availableVoices.first()
        val engines = Engine.entries.map { it.settingsGroup }.distinct()
            .filter { engine -> availableVoices.any { it.engine.settingsGroup == engine } }
        voiceSelections[savedVoice.engine.settingsGroup] = savedVoice.id
        voiceList = availableVoices.filter { it.engine.settingsGroup == savedVoice.engine.settingsGroup }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        root.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 26f
        })
        root.addView(TextView(this).apply {
            text = getString(R.string.settings_subtitle)
            setPadding(0, 0, 0, dp(16))
        })

        status = TextView(this).apply { setPadding(0, dp(8), 0, dp(8)) }

        root.addView(sectionTitle(getString(R.string.label_engine)))
        enginePicker = Spinner(this).apply {
            prompt = getString(R.string.label_engine)
            contentDescription = getString(R.string.label_engine)
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item,
                engines.map { engineLabel(it) }).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(engines.indexOf(savedVoice.engine.settingsGroup))
        }
        root.addView(enginePicker)

        root.addView(sectionTitle(getString(R.string.label_voice)))
        voicePicker = Spinner(this).apply {
            prompt = getString(R.string.label_voice)
            contentDescription = getString(R.string.label_voice)
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item,
                voiceList.map { it.name }).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(voiceList.indexOfFirst { it.id == prefs.getString(KEY_VOICE, null) }.coerceAtLeast(0))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    stopPreview()
                    val voice = voiceList.getOrNull(position)
                    status.text = voice?.let { getString(R.string.preview_hint, it.name) } ?: ""
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        root.addView(voicePicker)

        // Registered after the voice picker so that showing the initial engine does not
        // immediately refill the voice list and select a different voice.
        enginePicker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectEngine(engines[position])
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        speedLabel = sectionTitle("")
        root.addView(speedLabel)
        speed = SeekBar(this).apply {
            max = SPEED_MAX - SPEED_MIN
            progress = prefs.getInt(KEY_SPEED, DEFAULT_SPEED) - SPEED_MIN
            setOnSeekBarChangeListener(onSliderChange { speedLabel.text = getString(R.string.label_speed, speedPercent()) })
        }
        root.addView(speed)
        speedLabel.text = getString(R.string.label_speed, speedPercent())

        pitchLabel = sectionTitle("")
        root.addView(pitchLabel)
        pitch = SeekBar(this).apply {
            max = PITCH_MAX - PITCH_MIN
            progress = prefs.getInt(KEY_PITCH, 0) - PITCH_MIN
            setOnSeekBarChangeListener(onSliderChange { pitchLabel.text = getString(R.string.label_pitch, pitchSemitones()) })
        }
        root.addView(pitch)
        pitchLabel.text = getString(R.string.label_pitch, pitchSemitones())

        volumeLabel = sectionTitle("")
        root.addView(volumeLabel)
        volume = SeekBar(this).apply {
            contentDescription = getString(R.string.volume_control)
            max = PcmVolume.MAX_PERCENT
            progress = prefs.getInt(PcmVolume.KEY, PcmVolume.DEFAULT_PERCENT).coerceIn(0, max)
            setOnSeekBarChangeListener(onSliderChange { volumeLabel.text = getString(R.string.label_volume, progress) })
        }
        volume.id = View.generateViewId()
        volumeLabel.labelFor = volume.id
        root.addView(volume)
        volumeLabel.text = getString(R.string.label_volume, volume.progress)
        root.addView(TextView(this).apply { text = getString(R.string.volume_hint) })

        singing = Switch(this).apply {
            text = getString(R.string.label_singing)
            isChecked = prefs.getBoolean(KEY_SINGING, false)
        }
        root.addView(singing)

        root.addView(Button(this).apply {
            text = getString(R.string.action_preview)
            setOnClickListener { preview() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(16)
        })

        status.text = getString(R.string.preview_hint, currentVoice().name)
        root.addView(status)

        val copy = Button(this).apply { text = getString(R.string.action_copy_diagnostics) }
        copy.setOnClickListener {
            val selectedVoice = currentVoice().name
            Thread {
                val report = try {
                    "Selected voice: $selectedVoice\n" + SpeechDiagnostics.collect(this)
                } catch (e: Exception) {
                    "Diagnostics failed: ${e.message}"
                }
                runOnUiThread {
                    getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText("Sandroid diagnostics", report))
                    status.text = getString(R.string.diagnostics_copied)
                }
            }.start()
        }
        root.addView(copy)

        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Button(this@MainActivity).apply {
                text = getString(R.string.action_cancel)
                setOnClickListener {
                    stopPreview()
                    finish()
                }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(Button(this@MainActivity).apply {
                text = getString(R.string.action_ok)
                setOnClickListener { saveAndClose() }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        })

        setContentView(ScrollView(this).apply {
            isFillViewport = true
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                        .let { Rect(it.left, it.top, it.right, it.bottom) }
                } else {
                    @Suppress("DEPRECATION")
                    Rect(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                        insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                }
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
            addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
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
            .putInt(KEY_PITCH, pitch.progress - PITCH_MAX)
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

    private fun currentVoice(): VoiceDef {
        val position = voicePicker.selectedItemPosition
        if (position in voiceList.indices) return voiceList[position]
        return voiceList.firstOrNull() ?: VoiceCatalog.voices.first()
    }

    private fun selectEngine(engine: Engine) {
        if (voiceList.firstOrNull()?.engine?.settingsGroup == engine) return
        stopPreview()
        val selected = currentVoice()
        voiceSelections[selected.engine.settingsGroup] = selected.id
        voiceList = availableVoices.filter { it.engine.settingsGroup == engine }
        voicePicker.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, voiceList.map { it.name }).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        voicePicker.setSelection(voiceList.indexOfFirst { it.id == voiceSelections[engine] }.coerceAtLeast(0))
        status.text = getString(R.string.preview_hint, currentVoice().name)
    }

    private fun engineLabel(engine: Engine): String = getString(
        when (engine) {
            Engine.SAPI5, Engine.ANNA -> R.string.engine_sapi5
            Engine.SAPI4 -> R.string.engine_sapi4
            Engine.TRUVOICE -> R.string.engine_truvoice
            Engine.ONECORE -> R.string.engine_onecore
            Engine.NEURAL -> R.string.engine_neural
        }
    )

    private fun speedPercent(): Int = (3.0.pow((speed.progress) / 10.0) * 100).roundToInt()

    private fun pitchSemitones(): Int = pitch.progress - PITCH_MAX

    /** Speaks the sample sentence with the settings shown on screen, without saving them. */
    private fun preview() {
        stopPreview()
        val run = previewGeneration.get()
        val voice = currentVoice()
        val sapiRate = (speed.progress - SPEED_MIN).coerceIn(-10, 18).toDouble()
        val semitones = pitchSemitones().toDouble()
        val sing = singing.isChecked && voice.engine == Engine.SAPI5
        val volumePercent = volume.progress
        status.text = getString(R.string.preview_preparing)
        Thread {
            var output: PreviewAudioOutput? = null
            var handle = 0L
            try {
                val root = VoiceAssets.install(this)
                SpeechDiagnostics.record(this, "Preview opening ${voice.name}")
                handle = NativeEngine.open(this, root, voice)
                if (handle == 0L) {
                    runOnUiThread {
                        if (previewGeneration.get() == run) status.text = getString(R.string.error_open, voice.name)
                    }
                    return@Thread
                }
                val track = PreviewAudioOutput()
                output = track
                synchronized(previewLock) {
                    if (previewGeneration.get() != run) track.cancel() else currentOutput = track
                }
                val text = getString(R.string.preview_text)
                SpeechDiagnostics.record(this, "Synthesizing ${voice.name}")
                val rc = NativeEngine.speak(handle, text, sapiRate, semitones, sing, volumePercent, PcmSink(track::write))
                SpeechDiagnostics.record(this, "${voice.name}: synthesis returned $rc")
                if (rc == 0) track.finish()
                runOnUiThread {
                    if (previewGeneration.get() == run) {
                        status.text = if (rc < 0) getString(R.string.error_speak)
                        else getString(R.string.preview_hint, voice.name)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (previewGeneration.get() == run) status.text = e.message ?: ""
                }
            } finally {
                synchronized(previewLock) {
                    if (currentOutput === output) currentOutput = null
                }
                output?.close()
                if (handle != 0L) NativeEngine.close(handle)
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

    companion object {
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
