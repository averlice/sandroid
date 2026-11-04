package com.quinton.sandroid

/** Shared output gain for previews and Android TTS, after synthesis by any backend. */
internal object PcmVolume {
    const val KEY = "volume"
    const val DEFAULT_PERCENT = 100
    const val MAX_PERCENT = 200

    fun apply(samples: ShortArray, percent: Int) {
        val gain = percent.coerceIn(0, MAX_PERCENT)
        if (gain == DEFAULT_PERCENT) return
        for (i in samples.indices) {
            // Saturate before converting back to PCM16 so boosted peaks cannot wrap sign.
            samples[i] = (samples[i].toInt() * gain / 100)
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }
}
