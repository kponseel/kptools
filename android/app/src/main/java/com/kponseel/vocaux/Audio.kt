package com.kponseel.vocaux

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Décode n'importe quel fichier audio/vidéo lisible par Android (opus, ogg, m4a, mp3, amr, 3gp, wav, flac, mp4…)
 *  en PCM mono 16 kHz, le format attendu par Whisper. */
object AudioDecoder {
    const val RATE = 16000

    fun decode(ctx: Context, uri: Uri): FloatArray {
        val ex = MediaExtractor()
        ex.setDataSource(ctx, uri, null)
        try { return decode(ex) } finally { ex.release() }
    }

    fun decode(file: File): FloatArray {
        val ex = MediaExtractor()
        ex.setDataSource(file.absolutePath)
        try { return decode(ex) } finally { ex.release() }
    }

    private fun decode(ex: MediaExtractor): FloatArray {
        val track = (0 until ex.trackCount).firstOrNull {
            ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: throw IllegalArgumentException("Pas de piste audio dans ce fichier")
        ex.selectTrack(track)
        val fmt = ex.getTrackFormat(track)
        val mime = fmt.getString(MediaFormat.KEY_MIME)!!
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(fmt, null, null, 0)
        codec.start()

        var rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var pcmFloat = false
        val out = FloatList()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        try {
            while (!outputDone) {
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val buf = codec.getInputBuffer(i)!!
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0) {
                            codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        pcmFloat = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            f.getInteger(MediaFormat.KEY_PCM_ENCODING) == android.media.AudioFormat.ENCODING_PCM_FLOAT
                    }
                    o >= 0 -> {
                        val buf = codec.getOutputBuffer(o)!!
                        buf.position(info.offset); buf.limit(info.offset + info.size)
                        appendMono(buf.slice().order(ByteOrder.nativeOrder()), channels, pcmFloat, out)
                        codec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
        } finally {
            codec.stop(); codec.release()
        }
        val mono = out.toArray()
        if (mono.isEmpty()) throw IllegalArgumentException("Fichier audio vide")
        return resample(mono, rate, RATE)
    }

    private fun appendMono(b: ByteBuffer, ch: Int, isFloat: Boolean, out: FloatList) {
        if (isFloat) {
            val f = b.asFloatBuffer()
            while (f.remaining() >= ch) { var s = 0f; repeat(ch) { s += f.get() }; out.add(s / ch) }
        } else {
            val s16 = b.asShortBuffer()
            while (s16.remaining() >= ch) { var s = 0f; repeat(ch) { s += s16.get() / 32768f }; out.add(s / ch) }
        }
    }

    /** Rééchantillonnage linéaire (largement suffisant pour de la voix). */
    fun resample(x: FloatArray, from: Int, to: Int): FloatArray {
        if (from == to) return x
        val n = (x.size.toLong() * to / from).toInt()
        val r = from.toDouble() / to
        return FloatArray(n) { i ->
            val p = i * r; val j = p.toInt(); val t = (p - j).toFloat()
            val a = x[j.coerceAtMost(x.size - 1)]; val b = x[(j + 1).coerceAtMost(x.size - 1)]
            a + (b - a) * t
        }
    }

    /** Écrit un WAV mono 16 bits (utilisé pour garder un passage coupé). */
    fun writeWav(samples: FloatArray, file: File, rate: Int = RATE) {
        val bb = ByteBuffer.allocate(44 + samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray()).putInt(36 + samples.size * 2).put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        bb.put("data".toByteArray()).putInt(samples.size * 2)
        for (s in samples) bb.putShort((s.coerceIn(-1f, 1f) * 32767).toInt().toShort())
        file.writeBytes(bb.array())
    }
}

/** Liste de floats extensible, sans « boxing ». */
class FloatList {
    private var a = FloatArray(1 shl 16); private var n = 0
    fun add(v: Float) { if (n == a.size) a = a.copyOf(a.size * 2); a[n++] = v }
    fun toArray() = a.copyOf(n)
}
