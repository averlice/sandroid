package com.quinton.sandroid

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Process
import java.nio.charset.StandardCharsets
import java.util.Date

/**
 * What "Copy diagnostics" puts on the clipboard: enough about the device, the engine and
 * its last operations for a bug report to be actionable. Nothing here reads the text that
 * was spoken; the recorded operations name voices only.
 */
internal object SpeechDiagnostics {
    private const val PREFS = "speech-diagnostics"
    private const val LAST_OPERATION = "last-operation"

    fun record(context: Context, operation: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(LAST_OPERATION, "${Date()}: $operation").commit()
    }

    fun collect(context: Context): String = buildString {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        append("Sandroid ${info.versionName}").append('\n')
        append("Device: ${Build.MANUFACTURER} ${Build.MODEL}").append('\n')
        append("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})").append('\n')
        append("Process: ${if (Process.is64Bit()) 64 else 32}-bit").append('\n')
        append("Device ABIs: ${Build.SUPPORTED_ABIS.joinToString()}").append('\n')
        append("Native libraries: ${context.applicationInfo.nativeLibraryDir}").append('\n')
        append("Last operation: " + context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(LAST_OPERATION, "None recorded")).append('\n')
        val manager = context.getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(memory)
        append("Available RAM: ${memory.availMem / (1 shl 20)} MiB; low memory: ${memory.lowMemory}").append('\n')
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val exits = manager.getHistoricalProcessExitReasons(context.packageName, 0, 3)
                append("Recent process exits: ${exits.size}").append('\n')
                exits.forEach { exit ->
                    append("Exit at ${Date(exit.timestamp)}: reason=${exit.reason}, status=${exit.status}").append('\n')
                    append(exit.description ?: "No description").append('\n')
                    runCatching {
                        exit.traceInputStream?.use { trace ->
                            val limit = if (exit.reason == ApplicationExitInfo.REASON_CRASH_NATIVE) 2 shl 20 else 32 shl 10
                            val buffer = ByteArray(limit)
                            var used = 0
                            while (used < limit) {
                                val count = trace.read(buffer, used, limit - used)
                                if (count <= 0) break
                                used += count
                            }
                            val data = buffer.copyOf(used)
                            append(
                                if (exit.reason == ApplicationExitInfo.REASON_CRASH_NATIVE) NativeCrashReport.summarize(data)
                                else String(data, StandardCharsets.UTF_8)
                            )
                        }
                    }.onFailure { append("Trace unavailable: ${it.message}").append('\n') }
                }
            }.onFailure { append("Exit history unavailable: ${it.message}").append('\n') }
        } else {
            append("Process exit history requires Android 11 or later.").append('\n')
        }
    }
}
