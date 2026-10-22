package com.quinton.sandroid

import android.app.Activity
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
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
    @Volatile private var currentTrack: AudioTrack? = null
    private lateinit var voicePicker: Spinner
    private lateinit var speed: SeekBar
    private lateinit var pitch: SeekBar
    private lateinit var singing: Switch
    private lateinit var speedLabel: TextView
    private lateinit var pitchLabel: TextView
    private lateinit var status: TextView
    private var voiceList: List<VoiceDef> = VoiceCatalog.voices
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only voices whose data is bundled can speak; the rest would silently fall back.
        voiceList = VoiceCatalog.voices.filter { VoiceAssets.isAvailable(this, it.engine) }
            .ifEmpty { VoiceCatalog.voices }

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

        layout.addView(sectionTitle(getString(R.string.label_voice)))
        voicePicker = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item,
                voiceList.map { it.name }).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            setSelection(voiceList.indexOfFirst { it.id == prefs.getString(KEY_VOICE, null) }.coerceAtLeast(0))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (::status.isInitialized) {
                        status.text = voiceList.getOrNull(position)?.let { getString(R.string.preview_hint, it.name) } ?: ""
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        layout.addView(voicePicker)

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
        layout.addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

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

        setContentView(layout)
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
        status.text = getString(R.string.preview_preparing)
        Thread {
            try {
                val root = VoiceAssets.install(this)
                val handle = NativeEngine.open(root, voice)
                if (handle == 0L) throw IllegalStateException(getString(R.string.error_open, voice.name))
                try {
                    val minBytes = AudioTrack.getMinBufferSize(22050, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                    val track = AudioTrack.Builder()
                        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                        .setAudioFormat(AudioFormat.Builder().setSampleRate(22050).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                        .setBufferSizeInBytes((minBytes.coerceAtLeast(4096)) * 2)
                        .setTransferMode(AudioTrack.MODE_STREAM).build()
                    currentTrack = track
                    track.play()
                    val rc = NativeEngine.speak(handle, getString(R.string.preview_text), sapiRate, semitones, sing,
                        object : PcmSink {
                            override fun onNativePcm(samples: ShortArray): Boolean {
                                if (previewGeneration.get() != run) return false
                                return track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING) >= 0 &&
                                    previewGeneration.get() == run
                            }
                        })
                    track.stop()
                    runOnUiThread {
                        if (previewGeneration.get() == run) {
                            status.text = if (rc < 0) getString(R.string.error_speak)
                            else getString(R.string.preview_hint, voice.name)
                        }
                    }
                    track.release()
                    currentTrack = null
                } finally { NativeEngine.close(handle) }
            } catch (e: Exception) {
                runOnUiThread { if (previewGeneration.get() == run) status.text = e.message ?: "" }
            }
        }.start()
    }

    private fun stopPreview() {
        previewGeneration.incrementAndGet()
        runCatching { currentTrack?.pause(); currentTrack?.flush(); currentTrack?.stop(); currentTrack?.release() }
        currentTrack = null
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
