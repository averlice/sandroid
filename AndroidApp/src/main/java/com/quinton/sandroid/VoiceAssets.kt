package com.quinton.sandroid

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.security.DigestInputStream
import java.security.MessageDigest

internal object VoiceAssets {
    private val folders = listOf("VoiceData", "SAPI4Voices", "AnnaVoice", "OneCoreVoice", "TruVoiceData", "NeuralVoices")

    /** Set once the whole asset set has been checked against its manifest in this process. */
    private var verifiedRoot: File? = null

    fun folderFor(engine: Engine): String = when (engine) {
        Engine.SAPI5 -> "VoiceData"
        Engine.SAPI4 -> "SAPI4Voices"
        Engine.ANNA -> "AnnaVoice"
        Engine.ONECORE -> "OneCoreVoice"
        Engine.TRUVOICE -> "TruVoiceData"
        Engine.NEURAL -> "NeuralVoices"
    }

    /**
     * An engine is only worth offering when the APK actually carries its data. The build
     * stages whichever voice folders exist, so this is the same question as "did this
     * voice data get staged?". Reading the assets avoids unpacking them just to answer it.
     */
    fun isAvailable(context: Context, engine: Engine): Boolean = runCatching {
        context.assets.list(folderFor(engine))?.isNotEmpty() == true
    }.getOrDefault(false)

    /**
     * Unpacks the voice data into the app's files directory, reusing everything that already
     * matches the build's `voice-assets.sha256` manifest and copying the rest. The neural
     * models alone are 75 MB, so this must not rewrite files that are already correct: the
     * first utterance after an app update only pays for what actually changed.
     */
    @Synchronized
    fun install(context: Context): File {
        val root = File(context.filesDir, "voice-data")
        if (verifiedRoot == root && root.isDirectory) return root
        if (!(root.isDirectory || root.mkdirs())) error("Cannot create voice-data folder")
        val lines = context.assets.open("voice-assets.sha256").bufferedReader().use { it.readLines() }
            .filter { it.isNotBlank() }
        for (line in lines) {
            val parts = line.split('\t', limit = 3)
            check(parts.size == 3) { "Invalid voice-data manifest" }
            val size = parts[0].toLong()
            val hash = parts[1]
            val path = parts[2]
            val safe = !path.startsWith("/") && path.split('/').none { it == ".." }
            check(safe) { "Invalid voice-data path" }
            val target = File(root, path)
            val current = target.isFile && target.length() == size &&
                runCatching { sha256(target) == hash }.getOrDefault(false)
            if (!current) installFile(context, path, target, size, hash)
        }
        verifiedRoot = root
        return root
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        DigestInputStream(file.inputStream(), digest).use { input ->
            val buffer = ByteArray(65536)
            while (input.read(buffer) != -1) Unit
        }
        return hex(digest.digest())
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { String.format("%02x", it) }

    private fun installFile(context: Context, path: String, target: File, size: Long, hash: String) {
        val atomic = AtomicFile(target)
        val output = atomic.startWrite()
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val copied = context.assets.open(path).use { asset ->
                DigestInputStream(asset, digest).use { input -> input.copyTo(output) }
            }
            val complete = copied == size && hex(digest.digest()) == hash
            if (!complete) throw IllegalStateException("Incomplete voice data: $path")
            atomic.finishWrite(output)
        } catch (e: IOException) {
            atomic.failWrite(output)
            throw IllegalStateException("Cannot install voice data: $path (${e.message})", e)
        }
    }
}
