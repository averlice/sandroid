package com.quinton.sandroid

import android.content.Context
import java.io.File

internal fun interface PcmSink {
    /** Returns false to stop the utterance early. */
    fun onNativePcm(samples: ShortArray): Boolean
}

internal object NativeEngine {
    init { System.loadLibrary("sandroid") }

    fun open(context: Context, dataRoot: File, voice: VoiceDef): Long {
        // The neural voices are not sampled data: they run on Microsoft's embedded Speech
        // SDK, which is dlopen'ed from the app's native library folder and reads the voice
        // from a work folder of links built on the first utterance.
        if (voice.engine == Engine.NEURAL) {
            return nativeOpenNeural(
                context.applicationInfo.nativeLibraryDir,
                dataRoot.absolutePath,
                File(context.cacheDir, "neural-work").absolutePath,
                voice.dataName
            )
        }
        return nativeOpen(
            voice.engine.ordinal, File(dataRoot, VoiceAssets.folderFor(voice.engine)).absolutePath,
            voice.dataName, voice.effect, voice.emotion, voice.pitch
        )
    }

    fun close(handle: Long) = nativeClose(handle)

    fun speak(
        handle: Long, text: String, rate: Double, pitch: Double = 0.0, singing: Boolean = false,
        volumePercent: Int = PcmVolume.DEFAULT_PERCENT, sink: PcmSink
    ): Int = nativeSpeak(handle, text, rate, pitch, singing, PcmSink { samples ->
        PcmVolume.apply(samples, volumePercent)
        sink.onNativePcm(samples)
    })

    private external fun nativeOpen(
        engine: Int, root: String, dataName: String, effect: String, emotion: String, pitch: Double
    ): Long

    private external fun nativeOpenNeural(sdkDir: String, dataRoot: String, workDir: String, voice: String): Long

    private external fun nativeClose(handle: Long)

    private external fun nativeSpeak(
        handle: Long, text: String, rate: Double, pitch: Double, singing: Boolean, sink: PcmSink
    ): Int
}

internal object PcmVolume {
    const val KEY = "volume"
    const val DEFAULT_PERCENT = 100
    const val MAX_PERCENT = 200

    /** Scales the engine's 16-bit samples in place; anything above 100% clips. */
    fun apply(samples: ShortArray, percent: Int) {
        val gain = percent.coerceIn(0, MAX_PERCENT)
        if (gain == 100) return
        for (i in samples.indices) {
            samples[i] = ((samples[i] * gain) / 100).coerceIn(-32768, 32767).toShort()
        }
    }
}
