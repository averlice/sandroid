package com.quinton.sandroid

/** Reads the signal and crashing thread from Android's tombstone protobuf, ignoring other fields.
 * Schema: android.googlesource.com/platform/system/core/+/main/debuggerd/proto/tombstone.proto
 */
internal object NativeCrashReport {
    private data class Field(val number: Int, val value: Long = 0, val bytes: ByteArray = byteArrayOf())

    private fun fields(data: ByteArray): List<Field> {
        var at = 0
        fun varint(): Long {
            var result = 0L
            for (shift in 0..63 step 7) {
                require(at < data.size) { "Incomplete tombstone" }
                val b = data[at++].toInt() and 255
                result = result or ((b and 127).toLong() shl shift)
                if (b < 128) return result
            }
            error("Invalid tombstone varint")
        }
        return buildList {
            while (at < data.size) {
                val tag = varint().toInt()
                val number = tag ushr 3
                require(number > 0)
                when (tag and 7) {
                    0 -> add(Field(number, value = varint()))
                    1 -> { require(data.size - at >= 8); at += 8 }
                    2 -> {
                        val size = varint()
                        require(size >= 0 && size <= data.size - at)
                        add(Field(number, bytes = data.copyOfRange(at, at + size.toInt())))
                        at += size.toInt()
                    }
                    5 -> { require(data.size - at >= 4); at += 4 }
                    else -> error("Unsupported tombstone wire type")
                }
            }
        }
    }

    private fun List<Field>.number(id: Int) = firstOrNull { it.number == id }?.value ?: 0L
    private fun List<Field>.text(id: Int) = firstOrNull { it.number == id }?.bytes?.toString(Charsets.UTF_8).orEmpty()

    fun summarize(data: ByteArray): String = buildString {
        val tombstone = fields(data)
        val tid = tombstone.number(6)
        appendLine("Native crash: tid=$tid")
        tombstone.firstOrNull { it.number == 10 }?.let {
            val signal = fields(it.bytes)
            appendLine("${signal.text(2)} ${signal.text(4)}; fault address=0x${signal.number(9).toULong().toString(16)}")
        }
        tombstone.text(14).takeIf { it.isNotEmpty() }?.let { appendLine("Abort: $it") }
        tombstone.filter { it.number == 15 }.forEach { appendLine(fields(it.bytes).text(1)) }
        tombstone.filter { it.number == 16 }.forEach { entry ->
            val map = fields(entry.bytes)
            if (map.number(1) == tid) {
                val thread = fields(map.first { it.number == 2 }.bytes)
                appendLine("Thread: ${thread.text(2)}")
                thread.filter { it.number == 4 }.take(40).forEachIndexed { index, frame ->
                    val f = fields(frame.bytes)
                    appendLine("#$index pc ${f.number(1).toULong().toString(16)} ${f.text(6)} (${f.text(4)}+${f.number(5)}) BuildId=${f.text(8)}")
                }
            }
        }
    }
}
