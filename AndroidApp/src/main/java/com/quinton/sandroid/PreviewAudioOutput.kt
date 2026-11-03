package com.quinton.sandroid

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import java.io.Closeable

/**
 * Plays the preview's samples as the engine produces them, so a long voice keeps speaking
 * while the rest is still being synthesized.
 */
internal class PreviewAudioOutput : Closeable {
    private val lock = Any()
    private val track: AudioTrack
    private var canceled = false
    private var closed = false

    var framesWritten = 0L
        private set

    init {
        val minimum = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minimum > 0) { "Preview audio is unavailable" }
        track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minimum, BLOCK) * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track.play()
    }

    val framesPlayed: Long
        get() = synchronized(lock) { track.playbackHeadPosition.toLong() and 0xFFFFFFFFL }

    /** Blocks until the samples are queued; false when the preview was stopped meanwhile. */
    fun write(samples: ShortArray): Boolean {
        var offset = 0
        var lastProgress = SystemClock.elapsedRealtime()
        while (offset < samples.size) {
            synchronized(lock) {
                if (canceled || closed) return false
                val written = track.write(samples, offset, samples.size - offset, AudioTrack.WRITE_BLOCKING)
                if (written > 0) {
                    framesWritten += written
                    offset += written
                    lastProgress = SystemClock.elapsedRealtime()
                } else {
                    check(written >= 0) { "Preview audio write failed ($written)" }
                    check(SystemClock.elapsedRealtime() - lastProgress < STALL_TIMEOUT_MS) { "Preview audio stalled" }
                    SystemClock.sleep(5)
                }
            }
        }
        return synchronized(lock) { !canceled && !closed }
    }

    /** Queues a buffer of silence and waits until everything has actually been played. */
    fun finish(): Boolean {
        if (!write(ShortArray(track.bufferSizeInFrames))) return false
        var previous = -1L
        var lastProgress = SystemClock.elapsedRealtime()
        while (true) {
            synchronized(lock) {
                if (canceled || closed) return false
                val played = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
                if (played >= framesWritten) return true
                if (played != previous) {
                    previous = played
                    lastProgress = SystemClock.elapsedRealtime()
                }
                check(SystemClock.elapsedRealtime() - lastProgress < STALL_TIMEOUT_MS) { "Preview audio stalled" }
                SystemClock.sleep(10)
            }
        }
    }

    fun cancel() {
        synchronized(lock) {
            if (!closed && !canceled) {
                canceled = true
                track.pause()
                track.flush()
            }
        }
    }

    override fun close() {
        synchronized(lock) {
            if (!closed) {
                closed = true
                try {
                    track.stop()
                } finally {
                    track.release()
                }
            }
        }
    }

    companion object {
        const val RATE = 22050
        const val STALL_TIMEOUT_MS = 5000L
        private const val BLOCK = 8192
    }
}
