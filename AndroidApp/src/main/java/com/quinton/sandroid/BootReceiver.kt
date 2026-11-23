package com.quinton.sandroid

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Android does not start a TTS engine at boot on its own; the engine is bound when a client
 * first asks for speech, which on a fresh boot is the screen reader talking to a locked
 * device. This receiver runs at LOCKED_BOOT_COMPLETED, before the first unlock, and does the
 * slow part there: verifying the voice data and loading the native engine.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        Thread {
            try {
                warmUp(context)
            } catch (error: Throwable) {
                Log.w(TAG, "Engine warm-up failed", error)
                SpeechDiagnostics.record(context, "Boot warm-up failed: ${error.message}")
            } finally {
                pending.finish()
            }
        }.start()
    }

    private fun warmUp(context: Context) {
        val root = VoiceAssets.install(context)
        val preferred = DeviceStorage.prefs(context, "settings").getString("voice", null)
            ?.let { VoiceCatalog.byNameOrLocale(it) }
        val voice = preferred?.takeIf { VoiceAssets.isAvailable(context, it) }
            ?: VoiceCatalog.voices.firstOrNull { VoiceAssets.isAvailable(context, it) }
            ?: return
        val handle = NativeEngine.open(context, root, voice)
        if (handle != 0L) NativeEngine.close(handle)
        Log.i(TAG, "Warm-up: ${voice.name} opened (${if (handle != 0L) "ok" else "failed"})")
        SpeechDiagnostics.record(context, "Boot warm-up: ${voice.name} opened")
    }

    private companion object {
        const val TAG = "SandroidBoot"
    }
}
