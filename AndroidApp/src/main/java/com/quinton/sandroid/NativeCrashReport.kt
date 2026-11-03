package com.quinton.sandroid

import java.nio.charset.StandardCharsets

/**
 * Turns the protobuf tombstone behind a native crash into a short readable summary.
 *
 * Android keeps a process exit's debuggerd output as the binary tombstone protobuf, which
 * is useless in a plain text report. Only the fields that identify the fault are read:
 * the signal and fault address, any abort message, the log lines, and the crashing thread's
 * frames. Field numbers are from tombstone/tombstone_proto/tombstone.proto.
 */
internal object NativeCrashReport {
    private class Field(val number: Int, val value: Long, val bytes: ByteArray)

    private class Reader(private val data: ByteArray) {
        var at = 0

        fun varint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                require(at < data.size) { "Incomplete tombstone" }
                val b = data[at++].toInt() and 0xFF
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b < 0x80) return result
                require(shift != 63) { "Invalid tombstone varint" }
                shift += 7
            }
        }

        fun fields(): List<Field> = buildList {
            while (at < data.size) {
                val tag = varint().toInt()
                val number = tag ushr 3
                require(number > 0)
                when (tag and 7) {
                    0 -> add(Field(number, varint(), ByteArray(0)))
                    1 -> {
                        require(data.size - at >= 8)
                        at += 8
                    }
                    2 -> {
                        val size = varint()
                        require(size >= 0 && size <= data.size - at)
                        add(Field(number, 0L, data.copyOfRange(at, at + size.toInt())))
                        at += size.toInt()
                    }
                    5 -> {
                        require(data.size - at >= 4)
                        at += 4
                    }
                    else -> error("Unsupported tombstone wire type")
                }
            }
        }
    }

    private fun fields(data: ByteArray): List<Field> = Reader(data).fields()

    private fun number(fields: List<Field>, id: Int): Long =
        fields.firstOrNull { it.number == id }?.value ?: 0L

    private fun text(fields: List<Field>, id: Int): String =
        fields.firstOrNull { it.number == id }?.let { String(it.bytes, StandardCharsets.UTF_8) } ?: ""

    fun summarize(data: ByteArray): String = buildString {
        val fields = fields(data)
        val tid = number(fields, 6)
        append("Native crash: tid=$tid\n")
        fields.firstOrNull { it.number == 10 }?.let { entry ->
            val signal = fields(entry.bytes)
            append(text(signal, 2) + " " + text(signal, 4) +
                "; fault address=0x" + number(signal, 9).toULong().toString(16) + "\n")
        }
        text(fields, 14).takeIf { it.isNotEmpty() }?.let { append("Abort: $it\n") }
        fields.filter { it.number == 15 }.forEach { append(text(fields(it.bytes), 1)).append('\n') }
        for (entry in fields.filter { it.number == 16 }) {
            val thread = fields(entry.bytes)
            if (number(thread, 1) != tid) continue
            val backtrace = fields(thread.first { it.number == 2 }.bytes)
            append("Thread: " + text(backtrace, 2) + "\n")
            backtrace.filter { it.number == 4 }.take(40).forEachIndexed { index, frame ->
                val it = fields(frame.bytes)
                append(
                    "#$index pc " + number(it, 1).toULong().toString(16) + " " + text(it, 6) +
                        " (" + text(it, 4) + "+" + number(it, 5) + ") BuildId=" + text(it, 8) + "\n"
                )
            }
            break
        }
    }
}
