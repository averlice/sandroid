package com.quinton.sandroid

import android.content.Context
import java.io.File

internal object VoiceAssets {
    private val folders = listOf("VoiceData", "SAPI4Voices", "AnnaVoice", "OneCoreVoice", "TruVoiceData")

    fun folderFor(engine: Engine): String = when (engine) {
        Engine.SAPI5 -> "VoiceData"
        Engine.SAPI4 -> "SAPI4Voices"
        Engine.ANNA -> "AnnaVoice"
        Engine.ONECORE -> "OneCoreVoice"
        Engine.TRUVOICE -> "TruVoiceData"
    }

    /**
     * An engine is only worth offering when the APK actually carries its data. The build
     * stages whichever voice folders exist, so this is the same question as "did this
     * voice data get staged?". Reading the assets avoids unpacking them just to answer it.
     */
    fun isAvailable(context: Context, engine: Engine): Boolean = runCatching {
        context.assets.list(folderFor(engine))?.isNotEmpty() == true
    }.getOrDefault(false)

    @Synchronized
    fun install(context: Context): File {
        val root = File(context.filesDir, "voice-data")
        root.mkdirs()
        folders.forEach { folder -> copyAssetTree(context, folder, File(root, folder)) }
        return root
    }

    private fun copyAssetTree(context: Context, assetPath: String, target: File) {
        val entries: Array<String> = try { context.assets.list(assetPath) ?: emptyArray() } catch (_: Exception) { emptyArray() }
        if (entries.isEmpty()) return
        target.mkdirs()
        entries.forEach { entry ->
            val childAsset = "$assetPath/$entry"
            val child = File(target, entry)
            val children: Array<String> = try { context.assets.list(childAsset) ?: emptyArray() } catch (_: Exception) { emptyArray() }
            if (children.isNotEmpty()) copyAssetTree(context, childAsset, child)
            else if (!child.exists() || child.length() == 0L) {
                runCatching { context.assets.open(childAsset).use { input -> child.outputStream().use { output -> input.copyTo(output) } } }
            }
        }
    }
}
