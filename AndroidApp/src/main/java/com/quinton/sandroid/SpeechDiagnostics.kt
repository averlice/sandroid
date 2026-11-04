package com.quinton.sandroid

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Process

/** Device-local report for a remote tester; collecting it does not send anything. */
internal object SpeechDiagnostics {
    fun record(context: Context, operation: String) {
        context.getSharedPreferences("speech-diagnostics", Context.MODE_PRIVATE).edit()
            .putString("last-operation", "${java.util.Date()}: $operation").commit()
    }

    fun collect(context: Context): String = buildString {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        appendLine("Sandroid ${info.versionName}")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Process: ${if (Process.is64Bit()) 64 else 32}-bit")
        appendLine("Device ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine("Native libraries: ${context.applicationInfo.nativeLibraryDir}")
        appendLine("Last operation: " + context.getSharedPreferences("speech-diagnostics", Context.MODE_PRIVATE)
            .getString("last-operation", "None recorded"))
        val manager = context.getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(memory)
        appendLine("Available RAM: ${memory.availMem / 1048576} MiB; low memory: ${memory.lowMemory}")
        if (Build.VERSION.SDK_INT >= 30) {
            runCatching {
                val exits = manager.getHistoricalProcessExitReasons(context.packageName, 0, 3)
                appendLine("Recent process exits: ${exits.size}")
                exits.forEach { exit ->
                    appendLine("Exit at ${java.util.Date(exit.timestamp)}: reason=${exit.reason}, status=${exit.status}")
                    appendLine(exit.description ?: "No description")
                    // Native tombstones are available through this API on Android 12+.
                    runCatching {
                        exit.traceInputStream?.use { trace ->
                            val limit = if (exit.reason == ApplicationExitInfo.REASON_CRASH_NATIVE) 2097152 else 32768
                            val buffer = ByteArray(limit)
                            var used = 0
                            while (used < limit) {
                                val count = trace.read(buffer, used, limit - used)
                                if (count <= 0) break
                                used += count
                            }
                            val data = buffer.copyOf(used)
                            appendLine(if (exit.reason == ApplicationExitInfo.REASON_CRASH_NATIVE)
                                NativeCrashReport.summarize(data) else data.toString(Charsets.UTF_8))
                        }
                    }.onFailure { appendLine("Trace unavailable: ${it.message}") }
                }
            }.onFailure { appendLine("Exit history unavailable: ${it.message}") }
        } else appendLine("Process exit history requires Android 11 or later.")
    }
}
