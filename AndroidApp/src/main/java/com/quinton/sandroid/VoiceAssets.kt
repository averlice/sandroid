package com.quinton.sandroid

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.security.DigestInputStream
import java.security.MessageDigest

internal object VoiceAssets {
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

    /** Neural packs can be staged independently, especially the optional Azure packs. */
    fun isAvailable(context: Context, voice: VoiceDef): Boolean {
        if (voice.engine != Engine.NEURAL) return isAvailable(context, voice.engine)
        val folder = when (voice.dataName) {
            "Jane", "Sara" -> "en-US-${voice.dataName}Neural"
            else -> voice.dataName
        }
        return runCatching {
            context.assets.list("NeuralVoices/$folder")?.isNotEmpty() == true
        }.getOrDefault(false)
    }

    @Synchronized
    fun install(context: Context): File {
        val root = File(context.filesDir, "voice-data")
        if (verifiedRoot == root && root.isDirectory) return root
        check(root.isDirectory || root.mkdirs()) { "Cannot create voice-data folder" }
        val entries = context.assets.open("voice-assets.sha256").bufferedReader().use { it.readLines() }
        entries.filter { it.isNotBlank() }.forEach { line ->
            val fields = line.split('\t', limit = 3)
            check(fields.size == 3) { "Invalid voice-data manifest" }
            val size = fields[0].toLong()
            val hash = fields[1]
            val path = fields[2]
            check(!path.startsWith('/') && path.split('/').none { it == ".." }) { "Invalid voice-data path" }
            val target = File(root, path)
            // Verify existing installations too: older builds could leave nonempty partial files.
            val valid = target.isFile && target.length() == size && runCatching {
                val digest = MessageDigest.getInstance("SHA-256")
                DigestInputStream(target.inputStream(), digest).use { input ->
                    val buffer = ByteArray(65536)
                    while (input.read(buffer) != -1) { /* hash without retaining the data */ }
                }
                hex(digest.digest()) == hash
            }.getOrDefault(false)
            if (!valid) installFile(context, path, target, size, hash)
        }
        verifiedRoot = root
        return root
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun installFile(context: Context, path: String, target: File, size: Long, hash: String) {
        val atomic = AtomicFile(target)
        val output = atomic.startWrite()
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val count = context.assets.open(path).use { input ->
                DigestInputStream(input, digest).use { it.copyTo(output) }
            }
            check(count == size && hex(digest.digest()) == hash) { "Incomplete voice data: $path" }
            atomic.finishWrite(output)
        } catch (error: Exception) {
            atomic.failWrite(output)
            throw IllegalStateException("Cannot install voice data: $path (${error.message})", error)
        }
    }
}
