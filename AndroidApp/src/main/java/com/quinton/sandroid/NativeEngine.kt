package com.quinton.sandroid

import java.io.File

internal object NativeEngine {
    init { System.loadLibrary("sandroid") }

    fun open(dataRoot: File, voice: VoiceDef): Long {
        val folder = when (voice.engine) {
            Engine.SAPI5 -> "VoiceData"
            Engine.SAPI4 -> "SAPI4Voices"
            Engine.ANNA -> "AnnaVoice"
            Engine.ONECORE -> "OneCoreVoice"
            Engine.TRUVOICE -> "TruVoiceData"
        }
        return nativeOpen(voice.engine.ordinal, File(dataRoot, folder).absolutePath,
            voice.dataName, voice.effect, voice.emotion, voice.pitch)
    }
    fun close(handle: Long) = nativeClose(handle)
    fun speak(handle: Long, text: String, rate: Double, pitch: Double = 0.0, singing: Boolean = false, sink: PcmSink): Int =
        nativeSpeak(handle, text, rate, pitch, singing, sink)

    private external fun nativeOpen(engine: Int, root: String, dataName: String, effect: String, emotion: String, pitch: Double): Long
    private external fun nativeClose(handle: Long)
    private external fun nativeSpeak(handle: Long, text: String, rate: Double, pitch: Double, singing: Boolean, sink: PcmSink): Int
}

internal interface PcmSink { fun onNativePcm(samples: ShortArray): Boolean }
