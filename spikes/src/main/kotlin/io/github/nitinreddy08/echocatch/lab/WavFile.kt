// SPDX-License-Identifier: GPL-3.0-only
package io.github.nitinreddy08.echocatch.lab

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Writes 16-bit mono PCM as a plain WAV file. Used only by the Lab app's consented test clips (ADR-0012). */
object WavFile {
    private const val HEADER_BYTES = 44
    private const val BITS = 16
    private const val CHANNELS = 1

    fun write(file: File, samples: ShortArray, count: Int, sampleRateHz: Int) {
        val dataBytes = count * 2
        val buffer = ByteBuffer.allocate(HEADER_BYTES + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(36 + dataBytes)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(1) // PCM
        buffer.putShort(CHANNELS.toShort())
        buffer.putInt(sampleRateHz)
        buffer.putInt(sampleRateHz * CHANNELS * BITS / 8)
        buffer.putShort((CHANNELS * BITS / 8).toShort())
        buffer.putShort(BITS.toShort())
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataBytes)
        for (i in 0 until count) buffer.putShort(samples[i])
        FileOutputStream(file).use { it.write(buffer.array()) }
    }
}
