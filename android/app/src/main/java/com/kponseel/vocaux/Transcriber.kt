package com.kponseel.vocaux

import com.k2fsa.sherpa.onnx.OfflineCohereTranscribeModelConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import kotlin.math.abs

/** Transcription 100 % sur le téléphone avec sherpa-onnx. */
class Transcriber(private val store: ModelStore) {
    private var recognizer: OfflineRecognizer? = null
    private var loadedKey = ""

    private fun recognizerFor(m: AsrModel, lang: String): OfflineRecognizer {
        val key = "${m.id}|$lang"
        recognizer?.let { if (loadedKey == key) return it }
        recognizer?.release(); recognizer = null
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
        val modelConfig = if (m.cohere) OfflineModelConfig(
            cohereTranscribe = OfflineCohereTranscribeModelConfig(
                encoder = store.path(m, m.encoder), decoder = store.path(m, m.decoder),
                language = lang.ifEmpty { "fr" }, usePunct = true, useItn = true,
            ),
            tokens = store.path(m, m.tokens), numThreads = threads,
        ) else OfflineModelConfig(
            whisper = OfflineWhisperModelConfig(
                encoder = store.path(m, m.encoder), decoder = store.path(m, m.decoder),
                language = lang, task = "transcribe",
            ),
            tokens = store.path(m, m.tokens), numThreads = threads,
        )
        return OfflineRecognizer(config = OfflineRecognizerConfig(modelConfig = modelConfig)).also {
            recognizer = it; loadedKey = key
        }
    }

    /** [lang] : code court ("fr", "en"…) ou "" pour la détection automatique (Whisper uniquement). */
    fun transcribe(m: AsrModel, lang: String, samples: FloatArray, progress: (Float) -> Unit): String {
        val r = recognizerFor(m, lang)
        val parts = mutableListOf<String>()
        val chunks = splitChunks(samples)
        chunks.forEachIndexed { i, (a, b) ->
            val stream = r.createStream()
            if (m.cohere) stream.setOption("language", lang.ifEmpty { "fr" })
            stream.acceptWaveform(samples.copyOfRange(a, b), AudioDecoder.RATE)
            r.decode(stream)
            r.getResult(stream).text.trim().takeIf { it.isNotEmpty() }?.let(parts::add)
            stream.release()
            progress((i + 1f) / chunks.size)
        }
        return parts.joinToString(" ")
    }

    fun release() { recognizer?.release(); recognizer = null; loadedKey = "" }

    /** Découpe en morceaux de 30 s max (limite de Whisper), en coupant de préférence dans un silence. */
    private fun splitChunks(x: FloatArray): List<Pair<Int, Int>> {
        val max = 29 * AudioDecoder.RATE
        val search = 5 * AudioDecoder.RATE
        val win = AudioDecoder.RATE / 20 // 50 ms
        val out = mutableListOf<Pair<Int, Int>>()
        var start = 0
        while (start < x.size) {
            if (x.size - start <= max) { out += start to x.size; break }
            var best = start + max; var bestE = Float.MAX_VALUE
            var p = start + max - search
            while (p + win <= start + max) {
                var e = 0f; for (k in p until p + win) e += abs(x[k])
                if (e < bestE) { bestE = e; best = p + win / 2 }
                p += win
            }
            out += start to best; start = best
        }
        return out
    }
}
