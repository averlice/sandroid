package com.quinton.sandroid

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import java.io.Closeable

/** Owns one preview's track until its final queued frame has played or the user cancels. */
internal class PreviewAudioOutput : Closeable {
    private val lock = Any()
    private var canceled = false
    private var closed = false
    private val track: AudioTrack
    var framesWritten = 0L
        private set

    init {
        val minimum = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minimum > 0) { "Preview audio is unavailable" }
        track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(minimum.coerceAtLeast(4096) * 2)
            .setTransferMode(AudioTrack.MODE_STREAM).build()
        track.play()
    }

    val framesPlayed: Long
        get() = synchronized(lock) { track.playbackHeadPosition.toLong() and 0xffffffffL }

    fun write(samples: ShortArray): Boolean {
        var offset = 0
        var lastProgress = SystemClock.elapsedRealtime()
        while (offset < samples.size) {
            val count = synchronized(lock) {
                if (canceled || closed) return false
                // Nonblocking writes keep cancellation responsive even when the output stalls.
                track.write(samples, offset, samples.size - offset, AudioTrack.WRITE_NON_BLOCKING).also {
                    if (it > 0) framesWritten += it
                }
            }
            check(count >= 0) { "Preview audio write failed ($count)" }
            if (count > 0) {
                offset += count
                lastProgress = SystemClock.elapsedRealtime()
            } else {
                check(SystemClock.elapsedRealtime() - lastProgress < STALL_TIMEOUT_MS) { "Preview audio stalled" }
                SystemClock.sleep(5)
            }
        }
        return synchronized(lock) { !canceled && !closed }
    }

    fun finish(): Boolean {
        // A short utterance may not fill the track's startup threshold. A buffer of silence
        // starts it and keeps the final speech samples ahead of the output pipeline's tail.
        if (!write(ShortArray(track.bufferSizeInFrames))) return false
        var previous = -1L
        var lastProgress = SystemClock.elapsedRealtime()
        while (true) {
            val played = synchronized(lock) {
                if (canceled || closed) return false
                track.playbackHeadPosition.toLong() and 0xffffffffL
            }
            if (played >= framesWritten) return true
            if (played != previous) {
                previous = played
                lastProgress = SystemClock.elapsedRealtime()
            }
            check(SystemClock.elapsedRealtime() - lastProgress < STALL_TIMEOUT_MS) { "Preview audio stalled" }
            SystemClock.sleep(10)
        }
    }

    fun cancel() = synchronized(lock) {
        if (!closed && !canceled) {
            canceled = true
            track.pause()
            track.flush()
        }
    }

    override fun close() = synchronized(lock) {
        if (!closed) {
            closed = true
            try { track.stop() } finally { track.release() }
        }
    }

    private companion object {
        const val RATE = 22050
        const val STALL_TIMEOUT_MS = 5000L
    }
}
