package com.kponseel.vocaux

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Modèles de transcription disponibles (fichiers int8 hébergés sur Hugging Face, format sherpa-onnx). */
data class AsrModel(
    val id: String,
    val label: String,
    val description: String,
    val sizeMb: Int,
    val repo: String,
    val encoder: String,
    val decoder: String,
    val tokens: String,
    val extra: List<String> = emptyList(),   // fichiers annexes (ex. poids externes)
    val cohere: Boolean = false,
) {
    val files get() = listOf(encoder, decoder, tokens) + extra
    fun url(file: String) = "https://huggingface.co/$repo/resolve/main/$file"
}

object AsrModels {
    private fun whisper(name: String, label: String, desc: String, mb: Int) = AsrModel(
        id = "whisper-$name", label = label, description = desc, sizeMb = mb,
        repo = "csukuangfj/sherpa-onnx-whisper-$name",
        encoder = "$name-encoder.int8.onnx", decoder = "$name-decoder.int8.onnx", tokens = "$name-tokens.txt",
    )

    val all = listOf(
        whisper("small", "Whisper small", "Bon équilibre précision / vitesse (recommandé)", 360),
        whisper("turbo", "Whisper turbo", "Très précis, plus lent", 990),
        whisper("base", "Whisper base", "Rapide, moins précis", 155),
        whisper("tiny", "Whisper tiny", "Très rapide, approximatif", 100),
        AsrModel(
            id = "cohere", label = "Cohere Transcribe", description = "Le plus précis (14 langues), très lourd",
            sizeMb = 2760, repo = "csukuangfj2/sherpa-onnx-cohere-transcribe-14-lang-int8-2026-04-01",
            encoder = "encoder.int8.onnx", decoder = "decoder.int8.onnx", tokens = "tokens.txt",
            extra = listOf("encoder.int8.onnx.data"), cohere = true,
        ),
    )
    const val DEFAULT = "whisper-small"
    fun byId(id: String) = all.firstOrNull { it.id == id } ?: all.first()
}

/** Téléchargement et stockage des modèles dans la mémoire interne de l'app (jamais effacée par Android). */
class ModelStore(ctx: Context) {
    private val root = File(ctx.filesDir, "models").apply { mkdirs() }

    fun dir(m: AsrModel) = File(root, m.id)
    fun path(m: AsrModel, f: String) = File(dir(m), f).absolutePath
    fun isInstalled(m: AsrModel) = m.files.all { File(dir(m), it).let { f -> f.isFile && f.length() > 0 } }
    fun sizeOnDisk(m: AsrModel) = dir(m).listFiles()?.sumOf { it.length() } ?: 0L
    fun delete(m: AsrModel) { dir(m).deleteRecursively() }

    /** Télécharge les fichiers manquants. [progress] reçoit (octets reçus, octets attendus). */
    fun download(m: AsrModel, progress: (Long, Long) -> Unit, cancelled: () -> Boolean = { false }) {
        val d = dir(m).apply { mkdirs() }
        val sizes = m.files.associateWith { f -> if (File(d, f).length() > 0) File(d, f).length() else contentLength(m.url(f)) }
        val total = sizes.values.sum()
        var done = sizes.filterKeys { File(d, it).length() > 0 }.values.sum()
        for (f in m.files) {
            val target = File(d, f)
            if (target.length() > 0) continue
            val tmp = File(d, "$f.part")
            val conn = open(m.url(f))
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(1 shl 16)
                    while (true) {
                        if (cancelled()) throw InterruptedException("Téléchargement annulé")
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        progress(done, total)
                    }
                }
            }
            if (!tmp.renameTo(target)) throw IllegalStateException("Enregistrement impossible : $f")
        }
    }

    private fun open(url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.instanceFollowRedirects = true
        c.connectTimeout = 20_000; c.readTimeout = 60_000
        if (c.responseCode !in 200..299) throw IllegalStateException("Téléchargement impossible (${c.responseCode})")
        return c
    }

    private fun contentLength(url: String): Long = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "HEAD"; c.instanceFollowRedirects = true
        c.contentLengthLong.coerceAtLeast(0).also { c.disconnect() }
    } catch (_: Exception) { 0 }
}
