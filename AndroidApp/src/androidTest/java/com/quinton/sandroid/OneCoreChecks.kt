package com.quinton.sandroid

import android.app.Instrumentation
import android.os.Bundle
import android.os.Process

/** Exercises the native OneCore pipeline in the device's actual app process ABI. */
internal class OneCoreChecks(private val instrumentation: Instrumentation) {
    private fun report(message: String) = instrumentation.sendStatus(0, Bundle().apply {
        putString("stream", "$message\n")
    })

    fun run() {
        checkCrashReport()
        val context = instrumentation.targetContext
        val diagnostics = SpeechDiagnostics.collect(context)
        check(diagnostics.contains("Device ABIs:") && diagnostics.contains("Available RAM:"))
        report(diagnostics)
        // An interrupted old installer left these files nonempty and permanently skipped them.
        // Damage a language container and a model before the first install in this process.
        listOf("OneCoreVoice/MSTTSLocEnUS.dat", "OneCoreVoice/M1033Eva.TDAT").forEach { path ->
            val broken = java.io.File(context.filesDir, "voice-data/$path")
            broken.parentFile!!.mkdirs()
            context.assets.open(path).use { input ->
                if (path.endsWith(".TDAT")) {
                    val bytes = input.readBytes()
                    bytes[0] = (bytes[0].toInt() xor 255).toByte()
                    broken.writeBytes(bytes) // Same length, invalid content: size checks alone are insufficient.
                } else {
                    val prefix = ByteArray(97)
                    check(input.read(prefix) == prefix.size)
                    broken.writeBytes(prefix)
                }
            }
        }
        val root = VoiceAssets.install(context)
        report("Repaired truncated OneCore language data and same-size corrupted Eva model")
        val voices = VoiceCatalog.voices.filter { it.engine == Engine.ONECORE }.distinctBy { it.dataName }
        check(voices.size == 13)
        report("OneCore process: ${if (Process.is64Bit()) 64 else 32}-bit")
        voices.forEach { voice ->
            report("Opening ${voice.name}")
            val handle = NativeEngine.open(context, root, voice)
            check(handle != 0L) { "Cannot open ${voice.name}" }
            try {
                fun speak(cancel: Boolean = false) {
                    var frames = 0
                    var nonzero = false
                    val rc = NativeEngine.speak(handle, "Hello, this is a test of the OneCore voice.", 0.0,
                        sink = object : PcmSink {
                            override fun onNativePcm(samples: ShortArray): Boolean {
                                frames += samples.size
                                nonzero = nonzero || samples.any { it.toInt() != 0 }
                                return !cancel || frames < 4410
                            }
                        })
                    check(rc == if (cancel) 1 else 0) { "${voice.name}: synthesis returned $rc" }
                    check(frames > 0 && nonzero) { "${voice.name}: silent output ($frames frames)" }
                    report("${voice.name}: $frames frames${if (cancel) ", canceled" else ""}")
                }
                speak()
                if (voice.dataName == "Eva") {
                    speak(cancel = true)
                    speak()
                }
            } finally {
                NativeEngine.close(handle)
            }
        }
    }

    private fun checkCrashReport() {
        fun integer(id: Int, value: Long): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            out.write(id shl 3)
            var v = value
            while (v > 127) { out.write((v.toInt() and 127) or 128); v = v ushr 7 }
            out.write(v.toInt())
            return out.toByteArray()
        }
        fun message(id: Int, value: ByteArray): ByteArray {
            val length = integer(1, value.size.toLong()).drop(1).toByteArray()
            val tag = integer(1, ((id shl 3) or 2).toLong()).drop(1).toByteArray()
            return tag + length + value
        }
        fun text(id: Int, value: String) = message(id, value.toByteArray())
        val frame = integer(1, 0xf1234567L) + text(4, "eva_synth") + text(6, "libsandroid.so") + text(8, "test-build-id")
        val thread = integer(1, 123) + text(2, "speech-worker") + message(4, frame)
        val entry = integer(1, 123) + message(2, thread)
        val tombstone = integer(6, 123) + message(10, text(2, "SIGSEGV") + text(4, "SEGV_MAPERR")) + message(16, entry)
        val report = NativeCrashReport.summarize(tombstone)
        check(report.contains("SIGSEGV SEGV_MAPERR") && report.contains("speech-worker") &&
            report.contains("pc f1234567 libsandroid.so (eva_synth+0) BuildId=test-build-id")) { report }
        check(runCatching { NativeCrashReport.summarize(byteArrayOf(0x82.toByte())) }.isFailure)
    }
}
